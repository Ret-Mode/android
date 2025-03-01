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
package com.android.tools.profilers.cpu.atrace

import com.android.tools.adtui.model.SeriesData
import com.android.tools.profilers.systemtrace.ProcessModel
import com.android.tools.profilers.systemtrace.SystemTraceModelAdapter
import com.android.tools.profilers.systemtrace.ThreadModel
import com.google.common.base.Preconditions

/**
 * Surfaceflinger is responsible for compositing all the application and system surfaces into a single buffer on Android. This class
 * extracts the Surfaceflinger process from a SystemTraceModelAdapter and exposes various events as data series.
 */
class SystemTraceSurfaceflingerManager(systemTraceModel: SystemTraceModelAdapter) {
  val surfaceflingerEvents: List<SeriesData<SurfaceflingerEvent>>
  val vsyncCounterValues: List<SeriesData<Long>>

  /**
   * Extracts the top level trace events from the main thread and builds a data series for surfaceflinger.
   */
  private fun buildSfEvents(surfaceflingerProcess: ProcessModel): List<SeriesData<SurfaceflingerEvent>> {
    Preconditions.checkArgument(surfaceflingerProcess.name == SURFACEFLINGER_PROCESS_NAME)
    val mainThread = surfaceflingerProcess.getMainThread() ?: return emptyList()
    return buildSfEventsFromThread(mainThread)
  }

  private fun buildSfEventsFromThread(mainThread: ThreadModel): List<SeriesData<SurfaceflingerEvent>> {
    val result = mutableListOf<SeriesData<SurfaceflingerEvent>>()
    var lastEndTime = 0L
    // We only need the granularity at the top level (i.e. onMessageReceived).
    for (event in mainThread.traceEvents) {
      val startTime = event.startTimestampUs
      val endTime = event.endTimestampUs

      // Add an IDLE event as padding between PROCESSING events, needed for UI rendering.
      if (startTime > lastEndTime) {
        result.add(SeriesData(lastEndTime, SurfaceflingerEvent(lastEndTime, startTime, SurfaceflingerEvent.Type.IDLE)))
      }
      lastEndTime = endTime

      // Add the real event.
      result.add(SeriesData(startTime, SurfaceflingerEvent(startTime, endTime, SurfaceflingerEvent.Type.PROCESSING, event.name)))
    }
    // Add one last IDLE event to properly terminate the series.
    result.add(SeriesData(lastEndTime, SurfaceflingerEvent(lastEndTime, Long.MAX_VALUE, SurfaceflingerEvent.Type.IDLE)))
    return result
  }

  /**
   * Extracts the VSYNC-sf counter and builds a data series for [vsyncCounterValues].
   */
  private fun buildVsyncCounter(surfaceflingerProcess: ProcessModel): List<SeriesData<Long>> {
    Preconditions.checkArgument(surfaceflingerProcess.name == SURFACEFLINGER_PROCESS_NAME)
    val counter = surfaceflingerProcess.counterByName[VSYNC_COUNTER_NAME] ?: return emptyList()
    return counter.valuesByTimestampUs
      .map { SeriesData(it.key, it.value.toLong()) }
      .toList()
  }

  companion object {
    private const val SURFACEFLINGER_PROCESS_NAME = "surfaceflinger"
    private const val VSYNC_COUNTER_NAME = "VSYNC-app"
  }

  init {
    val sfProcess = systemTraceModel.getProcesses().find { it.name == SURFACEFLINGER_PROCESS_NAME }
    surfaceflingerEvents = sfProcess?.let { buildSfEvents(it) } ?: listOf()
    vsyncCounterValues = sfProcess?.let { buildVsyncCounter(it) } ?: listOf()
  }
}