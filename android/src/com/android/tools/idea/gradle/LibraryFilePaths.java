/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle;

import static com.android.ide.gradle.model.artifacts.AdditionalClassifierArtifactsModel.SAMPLE_SOURCE_CLASSIFIER;
import static com.android.tools.idea.gradle.project.sync.idea.SourcesAndJavadocCollectorKt.idToString;
import static com.android.tools.idea.gradle.project.sync.setup.module.dependency.LibraryDependency.NAME_PREFIX;
import static com.intellij.openapi.util.io.FileUtil.getNameWithoutExtension;
import static com.intellij.openapi.util.io.FileUtil.notNullize;

import com.android.ide.gradle.model.artifacts.AdditionalClassifierArtifacts;
import com.android.ide.gradle.model.artifacts.AdditionalClassifierArtifactsModel;
import com.intellij.jarFinder.InternetAttachSourceProvider;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LibraryFilePaths {
  // Key: libraryId, Value: ExtraArtifactsPaths for the library.
  @NotNull private final Map<String, ArtifactPaths> myPathsMap = new HashMap<>();

  // for 2019-05 gradle cache layout
  private static final Pattern gradleCachePattern = Pattern.compile("^[a-f0-9]{30,48}$");

  private static class ArtifactPaths {
    @Nullable final File myJavaDoc;
    @Nullable final File mySources;
    @Nullable final File myPom;
    @Nullable final File mySampleSource;

    private ArtifactPaths(AdditionalClassifierArtifacts artifact) {
      myJavaDoc = artifact.getJavadoc();
      mySources = artifact.getSources();
      myPom = artifact.getMavenPom();
      mySampleSource = artifact.getSampleSources();
    }
  }

  public void populate(@NotNull AdditionalClassifierArtifactsModel artifacts) {
    for (AdditionalClassifierArtifacts artifact : artifacts.getArtifacts()) {
      myPathsMap.computeIfAbsent(idToString(artifact.getId()), k -> new ArtifactPaths(artifact));
    }
  }

  public Collection<String> retrieveCachedLibs() {
    return new HashSet<>(myPathsMap.keySet());
  }

  @NotNull
  public static LibraryFilePaths getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, LibraryFilePaths.class);
  }

  @Nullable
  public File findSourceJarPath(@NotNull String libraryName, @NotNull File libraryPath) {
    String libraryId = getLibraryId(libraryName);
    if (myPathsMap.containsKey(libraryId)) {
      return myPathsMap.get(libraryId).mySources;
    }
    return findArtifactFilePathInRepository(libraryPath, "-sources.jar", true);
  }

  @Nullable
  public File findSampleSourcesJarPath(@NotNull String libraryName, @NotNull File libraryPath) {
    String libraryId = getLibraryId(libraryName);
    if (myPathsMap.containsKey(libraryId)) {
      return myPathsMap.get(libraryId).mySampleSource;
    }
    return findArtifactFilePathInRepository(libraryPath, SAMPLE_SOURCE_CLASSIFIER, true);
  }

  /**
   * libraryName is in the format of "Gradle: junit:junit:4.12@jar", the internal map uses the core part
   * "junit:junit:4.12" as key, this method extracts the map key from libraryName.
   */
  @NotNull
  static String getLibraryId(@NotNull String libraryName) {
    if (libraryName.startsWith(NAME_PREFIX)) {
      libraryName = libraryName.substring(NAME_PREFIX.length());
    }
    if (libraryName.contains("@")) {
      libraryName = libraryName.substring(0, libraryName.indexOf('@'));
    }
    return libraryName.trim();
  }

  @Nullable
  public File findJavadocJarPath(@NotNull String libraryName, @NotNull File libraryPath) {
    String libraryId = getLibraryId(libraryName);
    if (myPathsMap.containsKey(libraryId)) {
      return myPathsMap.get(libraryId).myJavaDoc;
    }
    return findArtifactFilePathInRepository(libraryPath, "-javadoc.jar", true);
  }

  @Nullable
  public File findPomPathForLibrary(@NotNull String libraryName, @NotNull File libraryPath) {
    String libraryId = getLibraryId(libraryName);
    if (myPathsMap.containsKey(libraryId)) {
      return myPathsMap.get(libraryId).myPom;
    }
    return findArtifactFilePathInRepository(libraryPath, ".pom", false);
  }

  @Nullable
  public static File findArtifactFilePathInRepository(@NotNull File libraryPath,
                                                      @NotNull String fileNameSuffix,
                                                      boolean searchInIdeCache) {
    if (!libraryPath.isFile()) {
      // Unlikely to happen. At this point the jar file should exist.
      return null;
    }

    File parentPath = libraryPath.getParentFile();
    String name = getNameWithoutExtension(libraryPath);
    String sourceFileName = name + fileNameSuffix;
    if (parentPath != null) {

      // Try finding sources in the same folder as the jar file. This is the layout of Maven repositories.
      File sourceJar = findChildPath(parentPath, sourceFileName);
      if (sourceJar != null) {
        return sourceJar;
      }

      // Try the parent's parent. This is the layout of the repository cache in .gradle folder.
      parentPath = parentPath.getParentFile();
      if (parentPath != null) {
        for (File child : notNullize(parentPath.listFiles())) {
          if (child.isDirectory()) {
            sourceJar = findChildPath(child, sourceFileName);
            if (sourceJar != null) {
              return sourceJar;
            }
          }
        }
      }

      // Try new (around 2019-05) .gradle cache layout.
      // Example: androidx.appcompat/appcompat/1.0.2/2533a36c928bb27a3cc6843a25f83754b3c3ae/appcompat-1.0.2.aar
      parentPath = libraryPath.getParentFile();
      if (parentPath != null && gradleCachePattern.matcher(parentPath.getName()).matches()) {
        parentPath = parentPath.getParentFile();
        if (parentPath != null && parentPath.getParentFile() != null && libraryPath.getName().startsWith(parentPath.getParentFile().getName())) {
          for (File child : notNullize(parentPath.listFiles())) {
            if (child.isDirectory() && gradleCachePattern.matcher(child.getName()).matches()) {
              sourceJar = findChildPath(child, sourceFileName);
              if (sourceJar != null) {
                return sourceJar;
              }
            }
          }
        }
      }
    }

    if (searchInIdeCache) {
      // Try IDEA's own cache.
      File librarySourceDirPath = InternetAttachSourceProvider.getLibrarySourceDir();
      File sourceJarPath = new File(librarySourceDirPath, sourceFileName);
      if (sourceJarPath.isFile()) {
        return sourceJarPath;
      }
    }
    return null;
  }

  @Nullable
  private static File findChildPath(@NotNull File parentPath, @NotNull String childName) {
    for (File child : notNullize(parentPath.listFiles())) {
      if (childName.equals(child.getName())) {
        return child.isFile() ? child : null;
      }
    }
    return null;
  }
}
