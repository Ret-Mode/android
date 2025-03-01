/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.deploy;

import static com.android.sdklib.AndroidVersion.VersionCodes.O;
import static com.google.common.truth.Truth.assertThat;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.android.fakeadbserver.DeviceState;
import com.android.fakeadbserver.FakeAdbServer;
import com.android.fakeadbserver.devicecommandhandlers.JdwpCommandHandler;
import com.android.testutils.TestUtils;
import com.android.tools.deployer.DeployerTestUtils;
import com.android.tools.deployer.devices.DeviceId;
import com.android.tools.deployer.devices.FakeDevice;
import com.android.tools.deployer.devices.FakeDeviceHandler;
import com.android.tools.deployer.devices.FakeDeviceLibrary;
import com.android.tools.idea.adb.AdbService;
import com.android.tools.idea.run.AndroidProcessHandler;
import com.android.tools.idea.run.deployable.DeviceBinder;
import com.android.tools.idea.run.deployable.SwappableProcessHandler;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.run.deployment.DeviceSelectorFixture;
import com.android.tools.idea.tests.util.ddmlib.AndroidDebugBridgeUtils;
import com.google.common.io.Files;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.ui.RunContentManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.fest.swing.timing.Wait;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class DeploymentTest {
  // Project name for studio deployment dashboards
  private static final String PROJECT_NAME = "simple";
  private static final String APKS_LOCATION = "tools/base/deploy/deployer/src/test/resource/apks";
  private static final String DEPLOY_APK_NAME = "simple.apk";
  private static final String PACKAGE_NAME = "com.example.simpleapp";
  private static final int WAIT_TIME = 30;

  private enum APK {
    NONE(""),
    BASE("simple.apk"),
    CODE("simple+code.apk"),
    RESOURCE("simple+res.apk"),
    CODE_RESOURCE("simple+code+res.apk"),
    VERSION("simple+ver.apk");

    @NotNull public final String myFileName;

    APK(@NotNull String fileName) {
      myFileName = fileName;
    }
  }

  @Rule public final GuiTestRule myGuiTest = new GuiTestRule();
  private final FakeDeviceHandler myHandler = new FakeDeviceHandler();
  private FakeAdbServer myAdbServer;
  private AndroidDebugBridge myBridge;
  private Project myProject;

  @Before
  public void before() throws Exception {
    DeployerTestUtils.prepareStudioInstaller();

    // Build the server and configure it to use the default ADB command handlers.
    myAdbServer = new FakeAdbServer.Builder()
      .installDefaultCommandHandlers()
      .addDeviceHandler(myHandler)
      .addDeviceHandler(new JdwpCommandHandler())
      .build();

    // Start server execution.
    myAdbServer.start();

    // Start ADB with fake server and its port.
    AndroidDebugBridgeUtils.enableFakeAdbServerMode(myAdbServer.getPort());

    myProject = myGuiTest.openProjectAndWaitForIndexingToFinish(PROJECT_NAME);

    // Get the bridge synchronously, since we're in test mode.
    myBridge = AdbService.getInstance().getDebugBridge(AndroidSdkUtils.getAdb(myProject)).get();

    // Wait for ADB.
    Wait.seconds(WAIT_TIME).expecting("Android Debug Bridge to connect").until(() -> myBridge.isConnected());
    Wait.seconds(WAIT_TIME).expecting("Initial device list is available").until(() -> myBridge.hasInitialDeviceList());
  }

  @After
  public void after() throws Exception {
    myGuiTest.ideFrame().closeProject();
    Wait.seconds(WAIT_TIME)
      .expecting("Project to close")
      .until(() -> ProjectManagerEx.getInstanceEx().getOpenProjects().length == 0);
    myProject = null;

    if (myAdbServer != null) {
      boolean status = myAdbServer.awaitServerTermination(WAIT_TIME, TimeUnit.SECONDS);
      assertThat(status).isTrue();
    }

    Disposer.dispose(AdbService.getInstance());
    AndroidDebugBridge.disableFakeAdbServerMode();

    DeployerTestUtils.removeStudioInstaller();
  }

  @Test
  public void runOnDevices() throws Exception {
    List<FakeDevice> devices = connectDevices();
    IdeFrameFixture ideFrameFixture = myGuiTest.ideFrame();
    List<DeviceState> deviceStates = myAdbServer.getDeviceListCopy().get();
    List<DeviceBinder> deviceBinders = new ArrayList<>(deviceStates.size());
    for (DeviceState state : deviceStates) {
      deviceBinders.add(new DeviceBinder(state));
    }

    DeviceSelectorFixture deviceSelector = new DeviceSelectorFixture(myGuiTest.robot(), ideFrameFixture);
    Map<DeviceBinder, AndroidProcessHandler> binderHandlerMap = new HashMap<>();

    // Run the app on all devices.
    setActiveApk(myProject, APK.BASE);
    for (DeviceBinder deviceBinder : deviceBinders) {
      deviceSelector.selectDevice(deviceBinder.getIDevice());
      ideFrameFixture.updateToolbars();

      ideFrameFixture.findApplyCodeChangesButton(false);
      ideFrameFixture.findApplyChangesButton(false);

      // Run the app and wait for it to be picked up by the AndroidProcessHandler.
      ideFrameFixture.findRunApplicationButton().click();
      binderHandlerMap.put(deviceBinder, waitForClient(deviceBinder.getIDevice()));
    }

    for (DeviceBinder deviceBinder : deviceBinders) {
      deviceSelector.selectDevice(deviceBinder.getIDevice());
      IDevice device = deviceBinder.getIDevice();
      waitForClient(device);

      // Ensure that the buttons are enabled if on Android version Oreo or above and disabled otherwise.
      boolean shouldBeEnabled = Integer.parseInt(deviceBinder.getState().getBuildVersionSdk()) >= O;
      ideFrameFixture.findApplyCodeChangesButton(shouldBeEnabled);
      ideFrameFixture.findApplyChangesButton(shouldBeEnabled);

      // Stop the app and wait for the AndroidProcessHandler termination.
      ideFrameFixture.findStopButton().click();
      awaitTermination(binderHandlerMap.remove(deviceBinder), device);

      myAdbServer.disconnectDevice(deviceBinder.getState().getDeviceId());
    }

    for (FakeDevice device : devices) {
      assertThat(device.getProcesses()).isEmpty();
      device.shutdown();
    }
  }

  @NotNull
  private List<FakeDevice> connectDevices() throws Exception {
    List<FakeDevice> devices = new ArrayList<>();
    for (DeviceId id : DeviceId.values()) {
      FakeDevice device = new FakeDeviceLibrary().build(id);
      devices.add(device);
      myHandler.connect(device, myAdbServer);
    }

    Wait.seconds(WAIT_TIME)
      .expecting("device to show up in ddmlib")
      .until(() -> {
        try {
          return myAdbServer.getDeviceListCopy().get().size() == DeviceId.values().length;
        }
        catch (InterruptedException | ExecutionException e) {
          return false;
        }
      });

    return devices;
  }

  /**
   * A utility class to capture {@link AndroidProcessHandler} for a given target {@link IDevice}.
   */
  static class AndroidProcessHandlerCaptor implements Wait.Objective {

    private final Project myProject;
    private final IDevice myTargetDevice;
    private Optional<AndroidProcessHandler> capturedAndroidDeviceHandler = Optional.empty();

    AndroidProcessHandlerCaptor(Project project, IDevice targetDevice) {
      myProject = project;
      myTargetDevice = targetDevice;
    }

    @Override
    public boolean isMet() {
      capturedAndroidDeviceHandler = RunContentManager.getInstance(myProject).getAllDescriptors().stream()
        .filter(descriptor -> descriptor.getProcessHandler() instanceof AndroidProcessHandler)
        .map(descriptor -> (AndroidProcessHandler)descriptor.getProcessHandler())
        .filter(handler -> handler.getCopyableUserData(SwappableProcessHandler.EXTENSION_KEY).getExecutor() ==
                           DefaultRunExecutor.getRunExecutorInstance())
        .filter(handler -> handler.isAssociated(myTargetDevice))
        .filter(handler -> handler.getClient(myTargetDevice) != null)
        .findAny();
      return capturedAndroidDeviceHandler.isPresent();
    }

    public Optional<AndroidProcessHandler> getCapturedAndroidDeviceHandler() {
      return capturedAndroidDeviceHandler;
    }
  }

  @NotNull
  private AndroidProcessHandler waitForClient(@NotNull IDevice iDevice) {
    Wait.seconds(WAIT_TIME)
      .expecting("launched client to appear")
      .until(() -> Arrays.stream(iDevice.getClients()).anyMatch(
        c ->
          PACKAGE_NAME.equals(c.getClientData().getClientDescription()) ||
          PACKAGE_NAME.equals(c.getClientData().getPackageName())));

    AndroidProcessHandlerCaptor captor = new AndroidProcessHandlerCaptor(myProject, iDevice);

    Wait.seconds(WAIT_TIME)
      .expecting("launched client to appear")
      .until(captor);

    return captor.getCapturedAndroidDeviceHandler().orElseThrow(() -> new RuntimeException("launched client did not appear"));
  }

  private static void awaitTermination(@NotNull AndroidProcessHandler androidProcessHandler, @NotNull IDevice iDevice) {
    Wait.seconds(WAIT_TIME)
      .expecting("process handler to stop")
      .until(() -> androidProcessHandler.isProcessTerminated());

    Wait.seconds(WAIT_TIME)
      .expecting("launched app to stop")
      .until(() -> Arrays.stream(iDevice.getClients()).noneMatch(
        c ->
          PACKAGE_NAME.equals(c.getClientData().getClientDescription()) ||
          PACKAGE_NAME.equals(c.getClientData().getPackageName())));
  }

  private void setActiveApk(@NotNull Project project, @NotNull APK apk) {
    try {
      VirtualFile baseDir = VfsUtil.findFileByIoFile(new File(project.getBasePath()), true);
      assertThat(baseDir.isDirectory()).isTrue();

      ApplicationManager.getApplication().invokeAndWait(() -> {
        try {
          WriteAction.run(() -> {
            VirtualFile apkFileToReplace = VfsUtil.refreshAndFindChild(baseDir, DEPLOY_APK_NAME);
            if (apkFileToReplace != null && apkFileToReplace.exists()) {
              apkFileToReplace.delete(this);
              assertThat(apkFileToReplace.exists()).isFalse();
            }

            if (apk == APK.NONE) {
              return;
            }

            VirtualFile apkFileToCopy = VfsUtil.findFileByIoFile(TestUtils.getWorkspaceFile(new File(APKS_LOCATION, apk.myFileName).getPath()), true);
            if (apkFileToReplace == null || !apkFileToReplace.exists()) {
              VirtualFile targetApkCopy = VfsUtilCore.copyFile(this, apkFileToCopy, baseDir, DEPLOY_APK_NAME);
              assertThat(targetApkCopy.isValid()).isTrue();
            }
            else {
              Files.copy(new File(apkFileToCopy.getPath()), new File(apkFileToReplace.getPath()));
              apkFileToReplace.refresh(false, false);
              assertThat(apkFileToReplace.isValid()).isTrue();
            }
            VirtualFileManager.getInstance().syncRefresh();
          });
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      });
    }
    finally {
      // We need to refresh the VFS because we're modifying files here and some listeners may fire
      // at "inappropriate" times if we don't do it now.
      GuiTests.refreshFiles();
      GuiTests.waitForProjectIndexingToFinish(project);
    }
  }
}
