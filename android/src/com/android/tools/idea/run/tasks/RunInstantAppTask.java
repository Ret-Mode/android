/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.run.tasks;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.android.tools.idea.gradle.util.DynamicAppUtils;
import com.android.tools.idea.instantapp.InstantAppSdks;
import com.android.tools.idea.run.ApkFileUnit;
import com.android.tools.idea.run.ApkInfo;
import com.android.tools.idea.run.ConsolePrinter;
import com.android.tools.idea.run.util.LaunchStatus;
import com.google.android.instantapps.sdk.api.HandlerResult;
import com.google.android.instantapps.sdk.api.ProgressIndicator;
import com.google.android.instantapps.sdk.api.ResultStream;
import com.google.android.instantapps.sdk.api.StatusCode;
import com.google.common.collect.ImmutableList;
import com.intellij.execution.Executor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.google.android.instantapps.sdk.api.ExtendedSdk;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.google.android.instantapps.sdk.api.ExtendedSdk;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.List;
import org.jetbrains.annotations.TestOnly;

import static com.android.tools.idea.run.tasks.LaunchTaskDurations.DEPLOY_INSTANT_APP;

public class RunInstantAppTask implements LaunchTask {
  private static final String ID = "RUN_INSTANT_APP";

  @NotNull private final Collection<ApkInfo> myPackages;
  @Nullable private final String myDeepLink;
  @NotNull private final InstantAppSdks mySdk;
  @NotNull private final List<String> myDisabledFeatures;

  public RunInstantAppTask(@NotNull Collection<ApkInfo> packages, @Nullable String link, @NotNull List<String> disabledFeatures) {
    myPackages = packages;
    myDeepLink = link;
    mySdk = InstantAppSdks.getInstance();
    myDisabledFeatures = disabledFeatures;
  }

  public RunInstantAppTask(@NotNull Collection<ApkInfo> packages, @Nullable String link) {
    this(packages, link, ImmutableList.of());
  }

  @NotNull
  @Override
  public String getDescription() {
    return "Uploading and launching Instant App";
  }

  @Override
  public int getDuration() {
    return DEPLOY_INSTANT_APP;
  }

  @Override
  public LaunchResult run(
    @NotNull Executor executor, @NotNull IDevice device, @NotNull LaunchStatus launchStatus, @NotNull ConsolePrinter printer) {
    if (launchStatus.isLaunchTerminated()) {
      return LaunchResult.error("", getDescription());
    }

    // We expect exactly one zip file per Instant App that will contain the apk-splits for the
    // Instant App
    if (myPackages.size() != 1) {
      printer.stderr("Package not found or not unique.");
      return LaunchResult.error("", getDescription());
    }

    URL url = null;
    if (myDeepLink != null && !myDeepLink.isEmpty()) {
      try {
        url = new URL(myDeepLink);
      } catch (MalformedURLException e) {
        printer.stderr("Invalid launch URL: " + myDeepLink);
        return LaunchResult.error("", getDescription());
      }
    }

    ResultStream resultStream = new ResultStream() {
      @Override
      public void write(HandlerResult result) {
        if (result.isError()) {
          ApplicationManager.getApplication().invokeLater(
            () -> Messages.showWarningDialog(result.getDetail(), "Instant App Deployment Failed"));
          printer.stderr(result.toString());
          getLogger().warn(new RunInstantAppException(result.getMessage()));
        }
        else {
          printer.stdout(result.toString());
        }
      }
    };

    try {
      ExtendedSdk aiaSdk = mySdk.loadLibrary();

      ApkInfo apkInfo = myPackages.iterator().next();
      List<ApkFileUnit> artifactFiles = apkInfo.getFiles();

      StatusCode status;
      if (isSingleZipFile(artifactFiles)) {
        // This is a ZIP built by the feature plugin, containing all the app splits
        status = aiaSdk.getRunHandler().runZip(
          artifactFiles.get(0).getApkFile(),
          url,
          AndroidDebugBridge.getSocketAddress(),
          device.getSerialNumber(),
          null,
          resultStream,
          new NullProgressIndicator());
      } else {
        // This is a set of individual APKs, such as might be built from a bundle
        status = aiaSdk.getRunHandler().runApks(
          artifactFiles.stream()
                       // Remove disabled APKs
                       .filter((apkFileUnit) -> (DynamicAppUtils.isFeatureEnabled(myDisabledFeatures, apkFileUnit)))
                       .map(ApkFileUnit::getApkFile)
                       .collect(ImmutableList.toImmutableList()),
          url,
          AndroidDebugBridge.getSocketAddress(),
          device.getSerialNumber(),
          null,
          resultStream,
          new NullProgressIndicator());
      }

      return status == StatusCode.SUCCESS ? LaunchResult.success() : LaunchResult.error("", getDescription());
    } catch (Exception e) {
      printer.stderr(e.toString());
      getLogger().error(new RunInstantAppException(e));
      return LaunchResult.error("", getDescription());
    }
  }

  @NotNull
  @Override
  public String getId() {
    return ID;
  }

  public static class RunInstantAppException extends Exception {

    private RunInstantAppException(@NotNull String message) {
      super(message);
    }

    private RunInstantAppException(@NotNull Throwable t) {
      super(t);
    }
  }

  @NotNull
  private static Logger getLogger() {
    return Logger.getInstance(RunInstantAppTask.class);
  }

  private static boolean isSingleZipFile(List<ApkFileUnit> artifactFiles) {
    return artifactFiles.size() == 1 && StringUtil.toLowerCase(artifactFiles.get(0).getApkFile().getName()).endsWith(".zip");
  }

  private static class NullProgressIndicator implements ProgressIndicator {
    @Override
    public void setProgress(double v) {
    }
  }

  @TestOnly
  @NotNull
  public Collection<ApkInfo> getPackages() {
    return myPackages;
  }
}
