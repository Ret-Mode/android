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
package com.android.tools.idea.gradle.project.build.output

import com.intellij.execution.filters.ConsoleFilterProvider
import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.OpenFileHyperlinkInfo
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem

enum class ParsingState {
  NORMAL, PATH, CANCELED_PATH
}

/**
 *  Filter that highlights absolute path as hyperlinks in console output. This filter is effective in all console output, including build
 *  output, sync output, run test output, built-in terminal emulator, etc. Therefore, we manually parse the string instead of using regex
 *  for maximum performance.
 */
class GenericFileFilter(private val project: Project, private val localFileSystem: LocalFileSystem) : Filter {
  companion object {
    /**
     *  Max filename considered during parsing. Do not confuse with file path, which may contain several file names separated by '/' or '\'.
     *
     * Modern popular FS do not allow file names longer than 255.*/
    const val FILENAME_MAX = 255

    /**
     * Min path length to be considered.
     *
     * E.g. lonely slashes should be ignored.
     */
    const val PATH_MIN = 2
  }

  override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
    val indexOffset = entireLength - line.length
    val items = mutableListOf<Filter.ResultItem>()
    var state = ParsingState.NORMAL
    var pathStartIndex = -1
    var lastPathSegmentStart = -1
    var i = 0

    fun startPathMode(){
      state = ParsingState.PATH
      pathStartIndex = i
      lastPathSegmentStart = i
    }

    while (i < line.length) {
      when (state) {
        ParsingState.NORMAL -> {
          when {
            line[i] == '/' -> {
              // Start parsing a Linux path
              startPathMode()
            }
            line[i] in 'A'..'Z' && (line.startsWith(":\\", startIndex = i + 1) || line.startsWith(":/", startIndex = i + 1) ) -> {
              // Start parsing a Windows path
              startPathMode()
              i += 2
            }
          }
        }
        ParsingState.PATH -> {
          fun addItem(pathEndIndex: Int, lineNumber: Int, columnNumber: Int): Boolean {
            state = ParsingState.NORMAL
            if (pathEndIndex - lastPathSegmentStart > FILENAME_MAX) return false
            if (pathEndIndex - pathStartIndex < PATH_MIN) return false

            val path = line.substring(pathStartIndex, pathEndIndex)
            if (path == "/") {
              // Ignore single slashes, as these are probably referring to something
              // other than the file system root (e.g. progress indicators like "[10 / 1,000]").
              return false
            }
            val file = try {
              localFileSystem.findFileByPathIfCached(path)
            } catch (t: Throwable) {
              // We interpret any exception to mean the file is not found.
              null
            }
            if (file != null) {
              items += Filter.ResultItem(
                indexOffset + pathStartIndex,
                indexOffset + i,
                OpenFileHyperlinkInfo(project, file, lineNumber, columnNumber))
              return true
            }
            return false
          }
          when {
            line[i] == '\\' || line[i] == '/' -> {
              lastPathSegmentStart = i + 1
              if (i - pathStartIndex > 1){
                val path = line.substring(pathStartIndex, i)
                val file = localFileSystem.findFileByPathIfCached(path)
                if (file == null){
                  // skip remaining. If a prent does not exist, children will not exist either.
                  state = ParsingState.CANCELED_PATH
                }
              }
            }
            line[i] == ':' -> {
              val pathEndIndex = i
              var lineNumber = 1
              var columnNumber = 1
              // Try to parse as "path: (line, column):"
              if (line.startsWith(" (", i + 1)) {
                i += 3
                lineNumber = line.takeWhileFromIndex(i) { it.isDigit() }?.also { i += it.length }.safeToIntOrDefault(1)
                if (line.startsWith(", ", i)) {
                  i += 2
                  columnNumber = line.takeWhileFromIndex(i) { it.isDigit() }?.also { i += it.length }.safeToIntOrDefault(1)
                  if (line.startsWith("):", i)) {
                    i += 2
                  }
                }
              }
              else {
                // Try to parse as path:line:column:
                lineNumber = line.takeWhileFromIndex(i + 1) { it.isDigit() }?.also { i += it.length }.safeToIntOrDefault(1)
                columnNumber =
                  if (line.getOrNull(++i) == ':')
                    line.takeWhileFromIndex(++i) { it.isDigit() }?.also { i += it.length }.safeToIntOrDefault(1)
                  else
                    1
              }
              addItem(pathEndIndex, lineNumber - 1, columnNumber - 1)
            }
            // Paths can have withe spaces and links get cut early (https://issuetracker.google.com/issues/136242040)
            line[i].isWhitespace() -> if (!addItem(i, 0, 0) && (i - lastPathSegmentStart) < FILENAME_MAX) {
              // Do not break path current if it does not belong to a valid file, and filename does not exceed max allowed len
              state = ParsingState.PATH
            }
          }
        }
        ParsingState.CANCELED_PATH -> if (line[i].isWhitespace()) state = ParsingState.NORMAL
      }
      i++
    }
    if (items.isEmpty()) return null
    return Filter.Result(items)
  }
}

private fun String?.safeToIntOrDefault(default: Int): Int = try {
  this?.toInt() ?: default
}
catch (e: NumberFormatException) {
  default
}

private fun String.takeWhileFromIndex(index: Int, predicate: (Char) -> Boolean): String? {
  for (i in index until length) {
    if (!predicate(get(i))) {
      return if (i == index) null else substring(index, i)
    }
  }
  return null
}

class GenericFileFilterProvider : ConsoleFilterProvider {
  override fun getDefaultFilters(project: Project) = arrayOf(GenericFileFilter(project, LocalFileSystem.getInstance()))
}

