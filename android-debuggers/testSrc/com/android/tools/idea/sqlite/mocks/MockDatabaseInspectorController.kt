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
package com.android.tools.idea.sqlite.mocks

import com.android.tools.idea.appinspection.inspector.ide.AppInspectionIdeServices
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.sqlite.DatabaseInspectorClientCommandsChannel
import com.android.tools.idea.sqlite.controllers.DatabaseInspectorController
import com.android.tools.idea.sqlite.controllers.DatabaseInspectorController.SavedUiState
import com.android.tools.idea.sqlite.model.DatabaseInspectorModel
import com.android.tools.idea.sqlite.model.SqliteDatabaseId
import com.android.tools.idea.sqlite.model.SqliteSchema
import com.android.tools.idea.sqlite.model.SqliteStatement
import com.android.tools.idea.sqlite.repository.DatabaseRepositoryImpl
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.withContext
import javax.naming.OperationNotSupportedException
import javax.swing.JComponent

open class MockDatabaseInspectorController(private val repository: DatabaseRepositoryImpl, val model: DatabaseInspectorModel) : DatabaseInspectorController {

  override val component: JComponent
    get() = throw OperationNotSupportedException()

  override fun setUp() { }

  override suspend fun addSqliteDatabase(deferredDatabaseId: Deferred<SqliteDatabaseId>) = withContext(uiThread) {
    addSqliteDatabase(deferredDatabaseId.await())
  }

  override suspend fun addSqliteDatabase(databaseId: SqliteDatabaseId) = withContext(uiThread) {
    model.addDatabaseSchema(databaseId, SqliteSchema(emptyList()))
  }

  override suspend fun runSqlStatement(databaseId: SqliteDatabaseId, sqliteStatement: SqliteStatement) { }

  override suspend fun closeDatabase(databaseId: SqliteDatabaseId): Unit = withContext(uiThread) {
    repository.closeDatabase(databaseId)
    model.removeDatabaseSchema(databaseId)
  }

  override suspend fun databasePossiblyChanged() { }

  override fun showError(message: String, throwable: Throwable?) { }

  override fun restoreSavedState(previousState: SavedUiState?) {
  }

  override fun saveState(): SavedUiState {
    return object: SavedUiState {}
  }

  override fun setDatabaseInspectorClientCommandsChannel(databaseInspectorClientCommandsChannel: DatabaseInspectorClientCommandsChannel?) {}

  override fun setAppInspectionServices(appInspectionIdeServices: AppInspectionIdeServices?) { }

  override fun dispose() { }
}