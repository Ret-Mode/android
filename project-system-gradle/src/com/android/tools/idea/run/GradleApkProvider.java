/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.run;

import static com.android.AndroidProjectTypes.PROJECT_TYPE_APP;
import static com.android.AndroidProjectTypes.PROJECT_TYPE_DYNAMIC_FEATURE;
import static com.android.AndroidProjectTypes.PROJECT_TYPE_INSTANTAPP;
import static com.android.AndroidProjectTypes.PROJECT_TYPE_TEST;
import static com.android.tools.idea.gradle.util.GradleBuildOutputUtil.getOutputListingFile;
import static com.android.tools.idea.gradle.util.GradleUtil.findModuleByGradlePath;

import com.android.build.OutputFile;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidArtifactOutput;
import com.android.builder.model.InstantAppProjectBuildOutput;
import com.android.builder.model.InstantAppVariantBuildOutput;
import com.android.builder.model.ProjectBuildOutput;
import com.android.builder.model.TestVariantBuildOutput;
import com.android.builder.model.TestedTargetVariant;
import com.android.builder.model.VariantBuildOutput;
import com.android.ddmlib.IDevice;
import com.android.ide.common.build.GenericBuiltArtifacts;
import com.android.ide.common.build.GenericBuiltArtifactsLoader;
import com.android.ide.common.gradle.model.IdeAndroidArtifact;
import com.android.ide.common.gradle.model.IdeAndroidProject;
import com.android.ide.common.gradle.model.IdeVariant;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.apk.analyzer.AaptInvoker;
import com.android.tools.apk.analyzer.AndroidApplicationInfo;
import com.android.tools.apk.analyzer.ArchiveContext;
import com.android.tools.apk.analyzer.Archives;
import com.android.tools.idea.apk.viewer.ApkParser;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.run.PostBuildModel;
import com.android.tools.idea.gradle.run.PostBuildModelProvider;
import com.android.tools.idea.gradle.util.DynamicAppUtils;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.gradle.util.OutputType;
import com.android.tools.idea.log.LogWrapper;
import com.android.tools.idea.projectsystem.AndroidProjectSettingsService;
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.idea.sdk.AndroidSdks;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/**
 * Provides the information on APKs to install for run configurations in Gradle projects.
 */
public class GradleApkProvider implements ApkProvider {
  @NotNull private final AndroidFacet myFacet;
  @NotNull private final ApplicationIdProvider myApplicationIdProvider;
  @NotNull private final PostBuildModelProvider myOutputModelProvider;
  @NotNull private final BestOutputFinder myBestOutputFinder;
  private final boolean myTest;
  private Function<AndroidVersion, OutputKind> myOutputKindProvider;

  public static final Key<PostBuildModel> POST_BUILD_MODEL = Key.create("com.android.tools.idea.post_build_model");

  /**
   * Specify where to look for build output APKs
   */
  public enum OutputKind {
    /**
     * Default behavior: Look for build output in the regular Gradle Model output
     */
    Default,
    /**
     * Bundle behavior: Look for build output in the Bundle task output model
     */
    AppBundleOutputModel,
  }

  public GradleApkProvider(@NotNull AndroidFacet facet,
                           @NotNull ApplicationIdProvider applicationIdProvider,
                           @NotNull PostBuildModelProvider outputModelProvider,
                           boolean test,
                           @NotNull Function<AndroidVersion, OutputKind> outputKindProvider) {
    this(facet, applicationIdProvider, outputModelProvider, new BestOutputFinder(), test, outputKindProvider);
  }

  @VisibleForTesting
  public GradleApkProvider(@NotNull AndroidFacet facet,
                           @NotNull ApplicationIdProvider applicationIdProvider,
                           boolean test) {
    this(facet, applicationIdProvider, () -> null, test, targetDeviceVersion -> OutputKind.Default);
  }

  @VisibleForTesting
  public GradleApkProvider(@NotNull AndroidFacet facet,
                           @NotNull ApplicationIdProvider applicationIdProvider,
                           @NotNull PostBuildModelProvider outputModelProvider,
                           boolean test) {
    this(facet, applicationIdProvider, outputModelProvider, new BestOutputFinder(), test, targetDeviceVersion -> OutputKind.Default);
  }

  @VisibleForTesting
  GradleApkProvider(@NotNull AndroidFacet facet,
                    @NotNull ApplicationIdProvider applicationIdProvider,
                    @NotNull PostBuildModelProvider outputModelProvider,
                    @NotNull BestOutputFinder bestOutputFinder,
                    boolean test,
                    @NotNull Function<AndroidVersion, OutputKind> outputKindProvider) {
    myFacet = facet;
    myApplicationIdProvider = applicationIdProvider;
    myOutputModelProvider = outputModelProvider;
    myBestOutputFinder = bestOutputFinder;
    myTest = test;
    myOutputKindProvider = outputKindProvider;
  }

  @TestOnly
  OutputKind getOutputKind(@Nullable AndroidVersion targedDeviceVersion) { return myOutputKindProvider.apply(targedDeviceVersion); }

  @TestOnly
  boolean isTest() { return myTest; }

  @Override
  @NotNull
  public Collection<ApkInfo> getApks(@NotNull IDevice device) throws ApkProvisionException {
    AndroidModuleModel androidModel = AndroidModuleModel.get(myFacet);
    if (androidModel == null) {
      getLogger().warn("Android model is null. Sync might have failed");
      return Collections.emptyList();
    }
    IdeVariant selectedVariant = androidModel.getSelectedVariant();

    List<ApkInfo> apkList = new ArrayList<>();

    int projectType = androidModel.getAndroidProject().getProjectType();
    if (projectType == PROJECT_TYPE_APP ||
        projectType == PROJECT_TYPE_INSTANTAPP ||
        projectType == PROJECT_TYPE_TEST ||
        projectType == PROJECT_TYPE_DYNAMIC_FEATURE) {
      String pkgName = projectType == PROJECT_TYPE_TEST
                       ? myApplicationIdProvider.getTestPackageName()
                       : myApplicationIdProvider.getPackageName();
      if (pkgName == null) {
        getLogger().warn("Package name is null. Sync might have failed");
        return Collections.emptyList();
      }

      switch (myOutputKindProvider.apply(device.getVersion())) {
        case Default:
          // Collect the base (or single) APK file, then collect the dependent dynamic features for dynamic
          // apps (assuming the androidModel is the base split).
          //
          // Note: For instant apps, "getApk" currently returns a ZIP to be provisioned on the device instead of
          //       a .apk file, the "collectDependentFeaturesApks" is a no-op for instant apps.
          List<ApkFileUnit> apkFileList = new ArrayList<>();
          apkFileList.add(new ApkFileUnit(androidModel.getModuleName(), getApk(selectedVariant, device, myFacet, false)));
          apkFileList.addAll(collectDependentFeaturesApks(androidModel, device));
          apkList.add(new ApkInfo(apkFileList, pkgName));
          break;

        case AppBundleOutputModel:
          Module baseAppModule = myFacet.getModule();
          if (projectType == PROJECT_TYPE_DYNAMIC_FEATURE) {
            // If it's instrumented test for dynamic feature module, the base-app module is retrieved,
            // and then Apks from bundle are able to be extracted.
            baseAppModule = DynamicAppUtils.getBaseFeature(myFacet.getModule());
          }
          if (baseAppModule != null) {
            ApkInfo apkInfo = DynamicAppUtils.collectAppBundleOutput(baseAppModule, myOutputModelProvider, pkgName);
            if (apkInfo != null) {
              apkList.add(apkInfo);
            }
          }
          break;
      }
    }

    apkList.addAll(getAdditionalApks(selectedVariant.getMainArtifact()));

    if (myTest) {
      if (projectType == PROJECT_TYPE_TEST) {
        if (androidModel.getFeatures().isTestedTargetVariantsSupported()) {
          apkList.addAll(0, getTargetedApks(selectedVariant, device));
        }
      }
      else {
        IdeAndroidArtifact testArtifactInfo = androidModel.getSelectedVariant().getAndroidTestArtifact();
        if (testArtifactInfo != null) {
          File testApk = getApk(androidModel.getSelectedVariant(), device, myFacet, true);
          String testPackageName = myApplicationIdProvider.getTestPackageName();
          assert testPackageName != null; // Cannot be null if initialized.
          apkList.add(new ApkInfo(testApk, testPackageName));
          apkList.addAll(getAdditionalApks(testArtifactInfo));
        }
      }
    }
    return apkList;
  }

  @NotNull
  private List<ApkFileUnit> collectDependentFeaturesApks(@NotNull AndroidModuleModel androidModel,
                                                         @NotNull IDevice device) {
    IdeAndroidProject project = androidModel.getAndroidProject();
    return DynamicAppUtils.getDependentFeatureModulesForBase(myFacet.getModule().getProject(), project)
      .stream()
      .map(module -> {
        // Find the output APK of the module
        AndroidFacet featureFacet = AndroidFacet.getInstance(module);
        if (featureFacet == null) {
          return null;
        }
        AndroidModuleModel androidFeatureModel = AndroidModuleModel.get(featureFacet);
        if (androidFeatureModel == null) {
          return null;
        }
        IdeVariant selectedVariant = androidFeatureModel.getSelectedVariant();
        try {
          File apk = getApk(selectedVariant, device, featureFacet, false);
          return new ApkFileUnit(androidFeatureModel.getModuleName(), apk);
        }
        catch (ApkProvisionException e) {
          //TODO: Is this the right thing to do?
          return null;
        }
      })
      .filter(Objects::nonNull)
      .collect(Collectors.toList());
  }

  /**
   * Returns ApkInfo objects for all runtime apks.
   * <p>
   * For each of the additional runtime apks it finds its package id and creates ApkInfo object it.
   * Thus it returns information for all runtime apks.
   *
   * @param testArtifactInfo test android artifact
   * @return a list of ApkInfo objects for each additional runtime Apk
   */
  @NotNull
  private static List<ApkInfo> getAdditionalApks(@NotNull AndroidArtifact testArtifactInfo) {
    List<ApkInfo> result = new ArrayList<>();
    for (File fileApk : testArtifactInfo.getAdditionalRuntimeApks()) {
      try {
        String packageId = getPackageId(fileApk);
        result.add(new ApkInfo(fileApk, packageId));
      }
      catch (ApkProvisionException e) {
        getLogger().error(
          "Failed to get the package name from the given file. Therefore, we are not be able to install it. Please install it manually: " +
          fileApk.getName() +
          " error: " +
          e.getMessage(), e);
      }
    }
    return result;
  }

  private static Path getPathToAapt() {
    AndroidSdkHandler handler = AndroidSdks.getInstance().tryToChooseSdkHandler();
    return AaptInvoker.getPathToAapt(handler, new LogWrapper(GradleApkProvider.class));
  }

  private static String getPackageId(@NotNull File fileApk) throws ApkProvisionException {
    try (ArchiveContext archiveContext = Archives.open(fileApk.toPath())) {
      AndroidApplicationInfo applicationInfo = ApkParser.getAppInfo(getPathToAapt(), archiveContext.getArchive());
      if (applicationInfo == AndroidApplicationInfo.UNKNOWN) {
        throw new ApkProvisionException("Could not determine manifest package for apk: " + fileApk.getName());
      }
      else {
        return applicationInfo.packageId;
      }
    }
    catch (IOException e) {
      throw new ApkProvisionException("Could not determine manifest package for apk: " + fileApk.getName(), e.getCause());
    }
  }

  @VisibleForTesting
  @NotNull
  File getApk(@NotNull IdeVariant variant,
              @NotNull IDevice device,
              @NotNull AndroidFacet facet,
              boolean fromTestArtifact) throws ApkProvisionException {
    AndroidModuleModel androidModel = AndroidModuleModel.get(facet);
    assert androidModel != null;
    if (androidModel.getFeatures().isBuildOutputFileSupported()) {
      return getApkFromBuildOutputFile(androidModel, device, fromTestArtifact);
    }
    if (androidModel.getFeatures().isPostBuildSyncSupported()) {
      return getApkFromPostBuildSync(variant, device, facet, fromTestArtifact);
    }
    return getApkFromPreBuildSync(variant, device, fromTestArtifact);
  }

  @NotNull
  File getApkFromBuildOutputFile(@NotNull AndroidModuleModel androidModel,
                                 @NotNull IDevice device,
                                 boolean fromTestArtifact) throws ApkProvisionException {
    IdeVariant variant = androidModel.getSelectedVariant();
    String outputFile = getOutputListingFile(androidModel, variant.getName(), OutputType.Apk, fromTestArtifact);
    if (outputFile == null) {
      throw new ApkProvisionException("Cannot get output listing file name from the build model");
    }
    GenericBuiltArtifacts builtArtifacts = GenericBuiltArtifactsLoader.loadFromFile(new File(outputFile), new LogWrapper(getLogger()));
    if (builtArtifacts == null) {
      throw new ApkProvisionException(String.format("Error loading build artifacts from: %s", outputFile));
    }
    return myBestOutputFinder.findBestOutput(variant, device, builtArtifacts);
  }

  @NotNull
  @VisibleForTesting
  File getApkFromPreBuildSync(@NotNull IdeVariant variant,
                              @NotNull IDevice device,
                              boolean fromTestArtifact) throws ApkProvisionException {
    IdeAndroidArtifact artifact = fromTestArtifact ? variant.getAndroidTestArtifact() : variant.getMainArtifact();
    assert artifact != null;
    @SuppressWarnings("deprecation") List<AndroidArtifactOutput> outputs = new ArrayList<>(artifact.getOutputs());
    return myBestOutputFinder.findBestOutput(variant, device, outputs);
  }

  @NotNull
  @VisibleForTesting
  File getApkFromPostBuildSync(@NotNull IdeVariant variant,
                               @NotNull IDevice device,
                               @NotNull AndroidFacet facet,
                               boolean fromTestArtifact) throws ApkProvisionException {
    List<OutputFile> outputs = new ArrayList<>();

    PostBuildModel outputModels = myOutputModelProvider.getPostBuildModel();
    if (outputModels == null) {
      return getApkFromPreBuildSync(variant, device, fromTestArtifact);
    }

    if (facet.getConfiguration().getProjectType() == PROJECT_TYPE_INSTANTAPP) {
      InstantAppProjectBuildOutput outputModel = outputModels.findInstantAppProjectBuildOutput(facet);
      if (outputModel == null) {
        throw new ApkProvisionException(
          "Couldn't get post build model for Instant Apps. Please, make sure to use plugin 3.0.0-alpha10 or later.");
      }

      for (InstantAppVariantBuildOutput instantAppVariantBuildOutput : outputModel.getInstantAppVariantsBuildOutput()) {
        if (instantAppVariantBuildOutput.getName().equals(variant.getName())) {
          outputs.add(instantAppVariantBuildOutput.getOutput());
        }
      }
    }
    else {
      ProjectBuildOutput outputModel = outputModels.findProjectBuildOutput(facet);
      if (outputModel == null) {
        return getApkFromPreBuildSync(variant, device, fromTestArtifact);
      }

      // Loop through the variants in the model and get the one that matches
      for (VariantBuildOutput variantBuildOutput : outputModel.getVariantsBuildOutput()) {
        if (variantBuildOutput.getName().equals(variant.getName())) {

          if (fromTestArtifact) {
            // Get the output from the test artifact
            for (TestVariantBuildOutput testVariantBuildOutput : variantBuildOutput.getTestingVariants()) {
              if (testVariantBuildOutput.getType().equals(TestVariantBuildOutput.ANDROID_TEST)) {
                int apiWithSplitApk = AndroidVersion.ALLOW_SPLIT_APK_INSTALLATION.getApiLevel();
                if (facet.getConfiguration().getProjectType() == PROJECT_TYPE_DYNAMIC_FEATURE &&
                    !device.getVersion().isGreaterOrEqualThan(apiWithSplitApk)) {
                  // b/119663247
                  throw new ApkProvisionException(
                    "Running Instrumented Tests for Dynamic Features is currently not supported on API < 21.");
                }
                outputs.addAll(testVariantBuildOutput.getOutputs());
              }
            }
          }
          else {
            // Get the output from the main artifact
            outputs.addAll(variantBuildOutput.getOutputs());
          }
        }
      }
    }

    // If empty, it means that either ProjectBuildOut has not been filled correctly or the variant was not found.
    // In this case we try to get an APK known at sync time, if any.
    return outputs.isEmpty()
           ? getApkFromPreBuildSync(variant, device, fromTestArtifact)
           : myBestOutputFinder.findBestOutput(variant, device, outputs);
  }

  /**
   * Gets the list of targeted apks for the specified variant.
   *
   * <p>This is used for test-only modules when specifying the tested apk
   * using the targetProjectPath and targetVariant properties in the build file.
   */
  @NotNull
  private List<ApkInfo> getTargetedApks(@NotNull IdeVariant selectedVariant,
                                        @NotNull IDevice device) throws ApkProvisionException {
    List<ApkInfo> targetedApks = new ArrayList<>();

    for (TestedTargetVariant testedVariant : selectedVariant.getTestedTargetVariants()) {
      String targetGradlePath = testedVariant.getTargetProjectPath();
      Module targetModule = ApplicationManager.getApplication().runReadAction(
        (Computable<Module>)() -> {
          Project project = myFacet.getModule().getProject();
          return findModuleByGradlePath(project, targetGradlePath);
        });

      if (targetModule == null) {
        getLogger().warn(String.format("Module not found for gradle path %s. Please install tested apk manually.", targetGradlePath));
        continue;
      }

      AndroidFacet targetFacet = AndroidFacet.getInstance(targetModule);
      if (targetFacet == null) {
        getLogger().warn("Android facet for tested module is null. Please install tested apk manually.");
        continue;
      }

      AndroidModuleModel targetAndroidModel = AndroidModuleModel.get(targetModule);
      if (targetAndroidModel == null) {
        getLogger().warn("Android model for tested module is null. Sync might have failed.");
        continue;
      }

      IdeVariant targetVariant = (IdeVariant)targetAndroidModel.findVariantByName(testedVariant.getTargetVariant());
      if (targetVariant == null) {
        getLogger().warn("Tested variant not found. Sync might have failed.");
        continue;
      }

      File targetApk = getApk(targetVariant, device, targetFacet, false);

      // TODO: use the applicationIdProvider to get the applicationId (we might not know it by sync time for Instant Apps)
      String applicationId = targetVariant.getMergedFlavor().getApplicationId();
      if (applicationId == null) {
        // If can't find applicationId in model, get it directly from manifest
        applicationId = targetAndroidModel.getApplicationId();
      }
      targetedApks.add(new ApkInfo(targetApk, applicationId));
    }

    return targetedApks;
  }

  @NotNull
  @Override
  public List<ValidationError> validate() {
    AndroidModuleModel androidModuleModel = AndroidModuleModel.get(myFacet);
    if (androidModuleModel == null) {
      Runnable requestProjectSync =
        () -> ProjectSystemUtil.getSyncManager(myFacet.getModule().getProject())
          .syncProject(ProjectSystemSyncManager.SyncReason.USER_REQUEST);
      return ImmutableList.of(ValidationError.fatal("The project has not yet been synced with Gradle configuration", requestProjectSync));
    }
    // Note: Instant apps and app bundles outputs are assumed to be signed
    if (androidModuleModel.getAndroidProject().getProjectType() == PROJECT_TYPE_INSTANTAPP ||
        myOutputKindProvider.apply(null) == OutputKind.AppBundleOutputModel ||
        androidModuleModel.getMainArtifact().isSigned()) {
      return ImmutableList.of();
    }

    File outputFile = GradleUtil.getOutputFile(androidModuleModel);
    String outputFileName = outputFile == null ? "Unknown output" : outputFile.getName();
    final String message =
      AndroidBundle.message("run.error.apk.not.signed", outputFileName, androidModuleModel.getSelectedVariant().getDisplayName());

    Runnable quickFix = () -> {
      Module module = myFacet.getModule();
      ProjectSettingsService service = ProjectSettingsService.getInstance(module.getProject());
      if (service instanceof AndroidProjectSettingsService) {
        ((AndroidProjectSettingsService)service).openSigningConfiguration(module);
      }
      else {
        service.openModuleSettings(module);
      }
    };
    return ImmutableList.of(ValidationError.fatal(message, quickFix));
  }

  @NotNull
  private static Logger getLogger() {
    return Logger.getInstance(GradleApkProvider.class);
  }
}
