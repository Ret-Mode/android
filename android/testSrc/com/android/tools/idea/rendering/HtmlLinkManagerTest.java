/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.rendering;

import static com.google.common.truth.Truth.assertThat;

import com.android.ide.common.repository.GradleCoordinate;
import com.android.tools.idea.projectsystem.TestProjectSystem;
import com.android.tools.idea.projectsystem.TestRepositories;
import com.google.common.collect.ImmutableList;
import com.intellij.testFramework.LightPlatformTestCase;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class HtmlLinkManagerTest extends LightPlatformTestCase {
  public void testRunnable() {
    HtmlLinkManager manager = new HtmlLinkManager();
    final AtomicBoolean result1 = new AtomicBoolean(false);
    final AtomicBoolean result2 = new AtomicBoolean(false);
    Runnable runnable1 = new Runnable() {
      @Override
      public void run() {
        result1.set(true);
      }
    };
    Runnable runnable2 = new Runnable() {
      @Override
      public void run() {
        result2.set(true);
      }
    };
    String url1 = manager.createRunnableLink(runnable1);
    String url2 = manager.createRunnableLink(runnable2);
    assertFalse(result1.get());
    manager.handleUrl(url1, null, null, null);
    assertTrue(result1.get());
    assertFalse(result2.get());
    result1.set(false);
    manager.handleUrl(url2, null, null, null);
    assertFalse(result1.get());
    assertTrue(result2.get());
  }

  public void testHandleAddDependency() {
    List<GradleCoordinate> accessibleDependencies = new ImmutableList.Builder<GradleCoordinate>()
      .addAll(TestRepositories.GOOGLE_PLAY_SERVICES)
      .addAll(TestRepositories.NON_PLATFORM_SUPPORT_LAYOUT_LIBS)
      .addAll(TestRepositories.PLATFORM_SUPPORT_LIBS)
      .build();
    TestProjectSystem testProjectSystem = new TestProjectSystem(getProject(), accessibleDependencies);
    testProjectSystem.useInTests();

    // try multiple invalid links
    HtmlLinkManager.handleAddDependency("addDependency:", getModule());
    HtmlLinkManager.handleAddDependency("addDependency:com.android.support", getModule());
    HtmlLinkManager.handleAddDependency("addDependency:com.android.support:", getModule());
    HtmlLinkManager.handleAddDependency("addDependency:com.google.android.gms:palette-v7", getModule());
    HtmlLinkManager.handleAddDependency("addDependency:com.android.support:palette-v7-broken", getModule());
    assertThat(testProjectSystem.getAddedDependencies(getModule())).isEmpty();

    HtmlLinkManager.handleAddDependency("addDependency:com.android.support:palette-v7", getModule());
    HtmlLinkManager.handleAddDependency("addDependency:com.google.android.gms:play-services", getModule());
    HtmlLinkManager.handleAddDependency("addDependency:com.android.support.constraint:constraint-layout", getModule());
    assertThat(
      testProjectSystem.getAddedDependencies(getModule()).stream()
                       .map(artifact -> artifact.getGroupId() + ":" + artifact.getArtifactId())
                       .collect(Collectors.toList()))
      .containsExactly("com.android.support:palette-v7",
                       "com.google.android.gms:play-services",
                       "com.android.support.constraint:constraint-layout");
  }
}
