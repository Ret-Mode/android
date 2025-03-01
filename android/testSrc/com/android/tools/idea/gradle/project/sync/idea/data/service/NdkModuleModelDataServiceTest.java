/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.idea.data.service;

import static com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.NDK_MODEL;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.android.tools.idea.gradle.project.model.NdkModuleModel;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.project.sync.ModuleSetupContext;
import com.android.tools.idea.gradle.project.sync.setup.module.NdkModuleSetup;
import com.android.tools.idea.gradle.project.sync.setup.module.ndk.NdkModuleCleanupStep;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProviderImpl;
import com.intellij.openapi.module.Module;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.ServiceContainerUtil;
import java.util.Collection;
import java.util.Collections;
import org.mockito.Mock;

/**
 * Tests for {@link NdkModuleModelDataService}.
 */
public class NdkModuleModelDataServiceTest extends HeavyPlatformTestCase {
  @Mock private NdkModuleSetup myModuleSetup;
  @Mock private GradleSyncState mySyncState;
  @Mock private ModuleSetupContext.Factory myModuleSetupContextFactory;
  @Mock private ModuleSetupContext myModuleSetupContext;

  private IdeModifiableModelsProvider myModelsProvider;
  private NdkModuleModelDataService myService;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    ServiceContainerUtil
      .replaceService(getProject(), GradleSyncState.class, mySyncState, getTestRootDisposable());
    myModelsProvider = new IdeModifiableModelsProviderImpl(getProject());
    myService = new NdkModuleModelDataService(myModuleSetupContextFactory, myModuleSetup);
  }

  public void testGetTargetDataKey() {
    assertSame(NDK_MODEL, myService.getTargetDataKey());
  }

  public void testImportData() {
    String appModuleName = "app";
    Module appModule = createModule(appModuleName);

    NdkModuleModel model = mock(NdkModuleModel.class);
    when(model.getModuleName()).thenReturn(appModuleName);

    DataNode<NdkModuleModel> dataNode = new DataNode<>(NDK_MODEL, model, null);
    Collection<DataNode<NdkModuleModel>> dataNodes = Collections.singleton(dataNode);

    when(myModuleSetupContextFactory.create(appModule, myModelsProvider)).thenReturn(myModuleSetupContext);
    myService.importData(dataNodes, null, getProject(), myModelsProvider);

    verify(myModuleSetup).setUpModule(myModuleSetupContext, model);
  }
}
