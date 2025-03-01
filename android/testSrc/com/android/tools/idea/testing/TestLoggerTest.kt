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
package com.android.tools.idea.testing

import com.intellij.openapi.application.PathManager
import com.intellij.testFramework.UsefulTestCase
import org.apache.log4j.LogManager
import org.junit.Test
import java.io.File

class TestLoggerTest : UsefulTestCase() {

  @Test
  fun testLogger() {
    val logXmlFile = File(PathManager.getHomePath(), "test-log.xml") // From TestLoggerFactory.
    assertTrue("Could not find test-log.xml", logXmlFile.exists())
    assertTrue("No log4j appenders found", LogManager.getRootLogger().allAppenders.hasMoreElements())
  }
}
