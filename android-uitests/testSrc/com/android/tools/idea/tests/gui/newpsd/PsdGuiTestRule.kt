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
package com.android.tools.idea.tests.gui.newpsd

import com.android.tools.idea.testing.AndroidGradleTests
import com.android.tools.idea.tests.gui.framework.GuiTestRule
import java.io.File

class PsdGuiTestRule : GuiTestRule() {
  override fun updateGradleVersions(projectPath: File) {
    val testRepositoryPath = getMasterProjectDirPath("psdSampleRepo")
    AndroidGradleTests.updateToolingVersionsAndPaths(projectPath, null, null, testRepositoryPath)
  }
}
