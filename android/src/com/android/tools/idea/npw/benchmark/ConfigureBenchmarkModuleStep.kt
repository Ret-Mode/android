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
package com.android.tools.idea.npw.benchmark

import com.android.tools.adtui.validation.ValidatorPanel
import com.android.tools.idea.device.FormFactor.MOBILE
import com.android.tools.idea.npw.labelFor
import com.android.tools.idea.npw.model.NewProjectModel.Companion.getSuggestedProjectPackage
import com.android.tools.idea.npw.module.ConfigureModuleStep
import com.android.tools.idea.ui.wizard.StudioWizardStepPanel
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.layout.panel
import org.jetbrains.android.util.AndroidBundle.message

class ConfigureBenchmarkModuleStep(
  model: NewBenchmarkModuleModel, title: String, minSdkLevel: Int
) : ConfigureModuleStep<NewBenchmarkModuleModel>(
  model, MOBILE, minSdkLevel, getSuggestedProjectPackage(), title
) {
  private val panel: DialogPanel = panel {
    row {
      cell {
        labelFor("Module name:", moduleName, message("android.wizard.module.help.name"))
      }
      moduleName()
    }

    row {
      labelFor("Package name:", packageName)
      packageName()
    }

    row {
      labelFor("Language:", languageCombo)
      languageCombo()
    }

    row {
      labelFor("Minimum SDK:", apiLevelCombo)
      apiLevelCombo()
    }
  }

  override val validatorPanel = ValidatorPanel(this, StudioWizardStepPanel.wrappedWithVScroll(panel))

  override fun getPreferredFocusComponent() = moduleName
}