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
package com.android.tools.idea.appinspection.test

import com.android.tools.app.inspection.AppInspection
import com.android.tools.idea.appinspection.api.AppInspectionDiscoveryHost
import com.android.tools.idea.appinspection.api.AppInspectionJarCopier
import com.android.tools.idea.appinspection.api.JarCopierCreator
import com.android.tools.idea.appinspection.api.process.ProcessDescriptor
import com.android.tools.idea.appinspection.inspector.api.AppInspectorJar
import com.android.tools.idea.protobuf.ByteString
import com.android.tools.idea.transport.TransportClient
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.idea.transport.manager.TransportStreamManager
import com.android.tools.profiler.proto.Common
import com.google.wireless.android.sdk.stats.AppInspectionEvent
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

/**
 * The amount of time tests will wait for async calls and events to trigger. It is used primarily in asserting latches and futures.
 *
 * These calls normally take way less than the allotted time below, on the order of tens of milliseconds. The timeout we chose is extremely
 * generous and was only chosen to fail tests faster if something goes wrong. If you hit this timeout, please don't just increase it but
 * investigate the root cause.
 */
const val ASYNC_TIMEOUT_MS: Long = 10000

const val INSPECTOR_ID = "test.inspector.1"
const val INSPECTOR_ID_2 = "test.inspector.2"

val TEST_JAR = AppInspectorJar("test")

/**
 * A collection of utility functions for inspection tests.
 */
object AppInspectionTestUtils {

  /**
   * Creates a successful service response proto.
   */
  fun createSuccessfulServiceResponse(commandId: Int): AppInspection.AppInspectionResponse =
    AppInspection.AppInspectionResponse.newBuilder()
      .setCommandId(commandId)
      .setStatus(AppInspection.AppInspectionResponse.Status.SUCCESS)
      .setServiceResponse(
        AppInspection.ServiceResponse.newBuilder()
          .build()
      )
      .build()

  /**
   * Creates an [AppInspectionEvent] with the provided [data] and inspector [name].
   */
  fun createRawAppInspectionEvent(
    data: ByteArray,
    name: String = INSPECTOR_ID
  ): AppInspection.AppInspectionEvent = AppInspection.AppInspectionEvent.newBuilder()
    .setInspectorId(name)
    .setRawEvent(
      AppInspection.RawEvent.newBuilder()
        .setContent(ByteString.copyFrom(data))
        .build()
    )
    .build()

  fun createDiscoveryHost(
    executor: ExecutorService,
    client: TransportClient,
    jarCopierCreator: JarCopierCreator = { TestTransportJarCopier }
  ): AppInspectionDiscoveryHost {
    return AppInspectionDiscoveryHost(
      executor, client, TransportStreamManager.createManager(
        client.transportStub,
        TimeUnit.MILLISECONDS.toNanos(250)
      ), jarCopierCreator
    )
  }

  fun createFakeProcessDescriptor(
    device: Common.Device = FakeTransportService.FAKE_DEVICE,
    process: Common.Process = FakeTransportService.FAKE_PROCESS
  ): ProcessDescriptor = ProcessDescriptor(Common.Stream.newBuilder().setDevice(device).setStreamId(device.deviceId).build(), process)

  /**
   * Keeps track of the copied jar so tests could verify the operation happened.
   */
  object TestTransportJarCopier : AppInspectionJarCopier {
    private const val deviceBasePath = "/test/"
    lateinit var copiedJar: AppInspectorJar

    override fun copyFileToDevice(jar: AppInspectorJar): List<String> {
      copiedJar = jar
      return listOf(deviceBasePath + jar.name)
    }
  }
}