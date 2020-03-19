/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.errors

import com.android.tools.idea.gradle.project.sync.errors.SyncErrorHandler.updateUsageTracker
import com.android.tools.idea.gradle.project.sync.quickFixes.SyncProjectRefreshingDependenciesQuickFix
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncFailure
import com.intellij.build.issue.BuildIssue
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.gradle.issue.GradleIssueChecker
import org.jetbrains.plugins.gradle.issue.GradleIssueData

class ErrorOpeningZipFileIssueChecker: GradleIssueChecker {
  override fun check(issueData: GradleIssueData): BuildIssue? {
    val message = issueData.error.message ?: return null
    if (!message.contains("error in opening zip file")) return null

    // Log metrics.
    invokeLater {
      updateUsageTracker(issueData.projectPath, GradleSyncFailure.CANNOT_OPEN_ZIP_FILE)
    }
    val syncProjectQuickFix = SyncProjectRefreshingDependenciesQuickFix()
    return object : BuildIssue {
      override val title = "Failed to open zip file."
      override val description = buildString {
        appendln("Failed to open zip file.")
        appendln("Gradle's dependency cache may be corrupt (this sometimes occurs after a network connection timeout.)")
        appendln("<a href=\"${syncProjectQuickFix.id}\">${syncProjectQuickFix.linkText}</a>")
      }
      override val quickFixes = listOf(syncProjectQuickFix)
      override fun getNavigatable(project: Project) = null
    }
  }
}