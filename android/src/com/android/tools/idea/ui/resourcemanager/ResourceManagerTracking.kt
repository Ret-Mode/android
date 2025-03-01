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
package com.android.tools.idea.ui.resourcemanager

import com.android.resources.ResourceType
import com.android.tools.analytics.UsageTracker
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.ResourceManagerEvent
import com.google.wireless.android.sdk.stats.ResourceManagerEvent.Kind
import com.google.wireless.android.sdk.stats.ResourceManagerEvent.ResourceType as EventResourceType


/**
 * Utility class to log usage related to the Resource Manager
 *
 * [Metrics Docs](http://go/android-devtools-metrics)
 */
object ResourceManagerTracking {

  /**
   * Called when the Resource Manager tool window is opened
   */
  fun logPanelOpens() = log(Kind.TOOL_WINDOW_OPEN)

  /**
   * Called when the Resource Picker Dialog is opened
   */
  fun logDialogOpens() = log(Kind.RESOURCE_PICKER_DIALOG_OPEN)

  /**
   * Called when the Resource Manager tool window is closed
   */
  fun logPanelCloses() = Unit // Missing Proto value

  /**
   * Called when users click the "Import Drawable" option
   */
  fun logAssetAddedViaButton() = log(Kind.ASSET_ADDED_VIA_BUTTON)

  /**
   * Called when users drop files onto the Resource Manager
   */
  fun logAssetAddedViaDnd() = log(Kind.ASSET_ADDED_VIA_DND)

  /**
   * Called the view displaying all versions of a resource is displayed
   */
  fun logDetailViewOpened(type: ResourceType?) = log(Kind.DETAIL_VIEW_OPENED, type)

  /**
   * Called when a resource file is opened via the resource manager.
   */
  fun logAssetOpened(type: ResourceType) = log(Kind.ASSET_OPENED, type)

  /**
   * Called when the resource manager is switched to grid mode
   */
  fun logSwitchToGridMode() = log(Kind.LIST_MODE_CHANGED)

  /**
   * Called when the resource manager is switched to list mode
   */
  fun logSwitchToListMode() = Unit // Missing proto value

  /**
   * Called when refreshing an asset in the resource manager through the right click action
   */
  fun logRefreshAsset(type: ResourceType) = log(Kind.REFRESH_RES_PREVIEW, type)

  /**
   * Called when the user toggles the 'local dependencies' filter
   */
  fun logShowLocalDependenciesToggle(state: Boolean) = log(if (state) Kind.DEPENDENT_MODULES_SHOWN else Kind.DEPENDENT_MODULES_HIDDEN)

  /**
   * Called when the user toggles the 'library' filter
   */
  fun logShowLibrariesToggle(state: Boolean) = log(if (state) Kind.LIBRARIES_SHOWN else Kind.LIBRARIES_HIDDEN)

  /**
   * Called when the user toggles the 'android resources' filter
   */
  fun logShowFrameworkToggle(state: Boolean) = log(if (state) Kind.FRAMEWORK_SHOWN else Kind.FRAMEWORK_HIDDEN)

  /**
   * Called when the user toggles the 'theme attributes' filter
   */
  fun logShowThemeAttributesToggle(state: Boolean) = log(if (state) Kind.THEME_ATTR_SHOWN else Kind.THEME_ATTR_HIDDEN)

  /**
   * Called when the user enables a Type filter. Eg: Enable the 'vector' drawable filter
   */
  fun logTypeFilterEnabled(type: ResourceType) = log(Kind.ENABLE_FILTER_BY_TYPE, type)

  /**
   * Called when users drop or paste a resource from the Resource Manager onto a blank area
   * in an XML file.
   */
  fun logPasteOnBlank(type: ResourceType) = log(Kind.DROP_ON_XML_BLANK_SPACE, type)

  /**
   * Called when users drop or paste a resource from the Resource Manager onto an XML tag (not an attribute)
   */
  fun logPasteOnXmlTag(type: ResourceType) = log(Kind.DROP_ON_XML_TAG, type)

  /**
   * Called when users drop or paste a resource from thr Resource Manager onto an XML attribute or value.
   */
  fun logPasteOnXmlAttribute(type: ResourceType) = log(Kind.DROP_ON_XML_ATTRIBUTE, type)

  /**
   * Called when users drop or paste a resource on a text area that does not recognize the Resource Url flavor.
   */
  fun logPasteUrlText(type: ResourceType?) = log(Kind.DROP_AS_TEXT, type)

  /**
   * (Not in use yet) Called when a resource is drop onto a view (to change the background of a view for instance)
   */
  fun logDragOnView(type: ResourceType?) = log(Kind.DROP_ON_LAYOUT_VIEW, type)

  /**
   * Called when a user drag a resource on a view group. This is happens if the view itself does not handle resource url drop.
   */
  fun logDragOnViewGroup(type: ResourceType?) = log(Kind.DROP_ON_LAYOUT_VIEWGROUP, type)

  /**
   * Called when density qualifier has been inferred from file path.
   */
  fun logDensityInferred() = log(Kind.DENSITY_INFERED)


  /**
   * Creates a log event for the resource manager.
   */
  private fun createEvent(kind: ResourceManagerEvent.Kind,
                          type: ResourceManagerEvent.ResourceType
  ): AndroidStudioEvent.Builder = AndroidStudioEvent.newBuilder()
    .setKind(AndroidStudioEvent.EventKind.RESOURCE_MANAGER)
    .setResourceManagerEvent(
      ResourceManagerEvent.newBuilder()
        .setKind(kind)
        .setResourceType(type))

  /**
   * Utility method to log a resource manager event
   */
  private fun log(kind: ResourceManagerEvent.Kind) =
    UsageTracker.log(createEvent(kind, EventResourceType.UNKNOWN))

  /**
   * Utility method to log a resource manager event with a resource type
   */
  private fun log(kind: ResourceManagerEvent.Kind, type: ResourceType?) = UsageTracker.log(createEvent(kind, type.toEventType()))

  /**
   * Map a [ResourceType] to an [EventResourceType] which represents a resource in the proto.
   */
  private fun ResourceType?.toEventType() = when (this) {
    ResourceType.DRAWABLE,
    ResourceType.MIPMAP -> EventResourceType.DRAWABLE
    ResourceType.FONT -> EventResourceType.FONT
    ResourceType.COLOR -> EventResourceType.COLOR
    ResourceType.LAYOUT -> EventResourceType.LAYOUT
    ResourceType.STRING -> EventResourceType.STRING
    ResourceType.NAVIGATION -> EventResourceType.NAVIGATION
    ResourceType.MENU -> EventResourceType.MENU
    ResourceType.STYLE -> EventResourceType.STYLE
    ResourceType.XML -> EventResourceType.XML
    ResourceType.ANIM,
    ResourceType.ANIMATOR,
    ResourceType.INTERPOLATOR,
    ResourceType.TRANSITION -> EventResourceType.ANIMATION
    ResourceType.ARRAY,
    ResourceType.BOOL,
    ResourceType.DIMEN,
    ResourceType.FRACTION,
    ResourceType.INTEGER,
    ResourceType.PLURALS -> EventResourceType.VALUE
    else -> EventResourceType.UNKNOWN
  }
}
