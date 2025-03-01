/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.rendering;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_CLASS;
import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.ATTR_LAYOUT;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.CLASS_ATTRIBUTE_SET;
import static com.android.SdkConstants.CLASS_CONTEXT;
import static com.android.SdkConstants.CLASS_FRAGMENT;
import static com.android.SdkConstants.CLASS_V4_FRAGMENT;
import static com.android.SdkConstants.CLASS_VIEW;
import static com.android.SdkConstants.LAYOUT_RESOURCE_PREFIX;
import static com.android.SdkConstants.TOOLS_URI;
import static com.android.SdkConstants.VALUE_FALSE;
import static com.android.SdkConstants.VIEW_FRAGMENT;
import static com.android.tools.idea.layoutlib.LayoutLibrary.LAYOUTLIB_NATIVE_PLUGIN;
import static com.android.tools.idea.layoutlib.LayoutLibrary.LAYOUTLIB_STANDARD_PLUGIN;

import com.android.ide.common.repository.GradleCoordinate;
import com.android.resources.ResourceType;
import com.android.tools.analytics.UsageTracker;
import com.android.tools.idea.projectsystem.GoogleMavenArtifactId;
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.idea.res.IdeResourcesUtil;
import com.android.tools.idea.ui.designer.EditorDesignSurface;
import com.android.tools.idea.ui.resourcechooser.util.ResourceChooserHelperKt;
import com.android.tools.idea.ui.resourcemanager.ResourcePickerDialog;
import com.android.tools.idea.util.DependencyManagementUtil;
import com.android.tools.lint.detector.api.Lint;
import com.android.utils.SdkUtils;
import com.android.utils.SparseArray;
import com.google.common.annotations.VisibleForTesting;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.LayoutEditorEvent;
import com.intellij.codeInsight.daemon.impl.quickfix.CreateClassKind;
import com.intellij.codeInsight.intention.impl.CreateClassDialog;
import com.intellij.ide.browsers.BrowserLauncher;
import com.intellij.ide.plugins.PluginManagerConfigurable;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiImportList;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.AlarmFactory;
import com.intellij.util.PsiNavigateUtil;
import java.io.File;
import java.net.MalformedURLException;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.android.dom.manifest.AndroidManifestUtils;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.uipreview.ChooseClassDialog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class HtmlLinkManager {
  private static final String URL_EDIT_CLASSPATH = "action:classpath";
  private static final String URL_BUILD = "action:build";
  private static final String URL_SYNC = "action:sync";
  private static final String URL_SHOW_XML = "action:showXml";
  private static final String URL_ACTION_IGNORE_FRAGMENTS = "action:ignoreFragment";
  private static final String URL_RUNNABLE = "runnable:";
  private static final String URL_COMMAND = "command:";
  private static final String URL_REPLACE_TAGS = "replaceTags:";
  private static final String URL_SHOW_TAG = "showTag:";
  private static final String URL_OPEN = "open:";
  private static final String URL_CREATE_CLASS = "createClass:";
  private static final String URL_OPEN_CLASS = "openClass:";
  private static final String URL_ASSIGN_FRAGMENT_URL = "assignFragmentUrl:";
  private static final String URL_ASSIGN_LAYOUT_URL = "assignLayoutUrl:";
  private static final String URL_EDIT_ATTRIBUTE = "editAttribute:";
  private static final String URL_REPLACE_ATTRIBUTE_VALUE = "replaceAttributeValue:";
  private static final String URL_DISABLE_SANDBOX = "disableSandbox:";
  private static final String URL_REFRESH_RENDER = "refreshRender";
  private static final String URL_ADD_DEPENDENCY = "addDependency:";
  private static final String URL_CLEAR_CACHE_AND_NOTIFY = "clearCacheAndNotify";
  private static final String URL_ENABLE_LAYOUTLIB_NATIVE = "enableLayoutlibNative";
  private static final String URL_DISABLE_LAYOUTLIB_NATIVE = "disableLayoutlibNative";

  private SparseArray<Runnable> myLinkRunnables;
  private SparseArray<CommandLink> myLinkCommands;
  private int myNextLinkId = 0;

  public HtmlLinkManager() {
  }

  /**
   * {@link NotificationGroup} used to let the user now that the click on a link did something. This is meant to be used
   * in those actions that do not trigger any UI updates (like Copy stack trace to clipboard).
   */
  private static final NotificationGroup NOTIFICATIONS_GROUP = new NotificationGroup(
    "Render error panel notifications", NotificationDisplayType.BALLOON, false, null, null, null, PluginId.getId("org.jetbrains.android"));

  public static void showNotification(@NotNull String content) {
    Notification notification = NOTIFICATIONS_GROUP.createNotification(content, NotificationType.INFORMATION);
    Notifications.Bus.notify(notification);

    AlarmFactory.getInstance().create().addRequest(
      notification::expire,
      TimeUnit.SECONDS.toMillis(2)
    );
  }

  public void handleUrl(@NotNull String url, @Nullable Module module, @Nullable PsiFile file, @Nullable DataContext dataContext,
                        @Nullable RenderResult result, @Nullable EditorDesignSurface surface) {
    if (url.startsWith("http:") || url.startsWith("https:")) {
      BrowserLauncher.getInstance().browse(url, null, module == null ? null : module.getProject());
    }
    else if (url.startsWith("file:")) {
      assert module != null;
      handleFileUrl(url, module);
    }
    else if (url.startsWith(URL_REPLACE_TAGS)) {
      assert module != null;
      assert file != null;
      handleReplaceTagsUrl(url, module, file);
    }
    else if (url.equals(URL_BUILD)) {
      assert dataContext != null;
      assert module != null;
      handleBuildProjectUrl(url, module.getProject());
    }
    else if (url.equals(URL_SYNC)) {
      assert dataContext != null;
      assert module != null;
      handleSyncProjectUrl(url, module.getProject());
    }
    else if (url.equals(URL_EDIT_CLASSPATH)) {
      assert module != null;
      handleEditClassPathUrl(url, module);
    }
    else if (url.startsWith(URL_CREATE_CLASS)) {
      assert module != null && file != null;
      handleNewClassUrl(url, module);
    }
    else if (url.startsWith(URL_OPEN)) {
      assert module != null;
      handleOpenStackUrl(url, module);
    }
    else if (url.startsWith(URL_OPEN_CLASS)) {
      assert module != null;
      handleOpenClassUrl(url, module);
    }
    else if (url.equals(URL_SHOW_XML)) {
      assert module != null && file != null;
      handleShowXmlUrl(url, module, file);
    }
    else if (url.startsWith(URL_SHOW_TAG)) {
      assert module != null && file != null;
      handleShowTagUrl(url, module, file);
    }
    else if (url.startsWith(URL_ASSIGN_FRAGMENT_URL)) {
      assert module != null && file != null;
      handleAssignFragmentUrl(url, module, file);
    }
    else if (url.startsWith(URL_ASSIGN_LAYOUT_URL)) {
      assert module != null && file != null;
      handleAssignLayoutUrl(url, module, file);
    }
    else if (url.equals(URL_ACTION_IGNORE_FRAGMENTS)) {
      assert result != null;
      handleIgnoreFragments(url, surface);
    }
    else if (url.startsWith(URL_EDIT_ATTRIBUTE)) {
      assert result != null;
      if (module != null && file != null) {
        handleEditAttribute(url, module, file);
      }
    }
    else if (url.startsWith(URL_REPLACE_ATTRIBUTE_VALUE)) {
      assert result != null;
      if (module != null && file != null) {
        handleReplaceAttributeValue(url, module, file);
      }
    }
    else if (url.startsWith(URL_DISABLE_SANDBOX)) {
      assert module != null;
      handleDisableSandboxUrl(module, surface);
    }
    else if (url.startsWith(URL_RUNNABLE)) {
      Runnable linkRunnable = getLinkRunnable(url);
      if (linkRunnable != null) {
        linkRunnable.run();
      }
    }
    else if (url.startsWith(URL_ADD_DEPENDENCY) && module != null) {
      assert module.getModuleFile() != null;
      handleAddDependency(url, module);
      ProjectSystemUtil.getSyncManager(module.getProject())
        .syncProject(ProjectSystemSyncManager.SyncReason.PROJECT_MODIFIED);
    }
    else if (url.startsWith(URL_COMMAND)) {
      if (myLinkCommands != null && url.startsWith(URL_COMMAND)) {
        String idString = url.substring(URL_COMMAND.length());
        int id = Integer.decode(idString);
        CommandLink command = myLinkCommands.get(id);
        if (command != null) {
          command.executeCommand();
        }
      }
    }
    else if (url.startsWith(URL_REFRESH_RENDER)) {
      handleRefreshRenderUrl(surface);
    }
    else if (url.startsWith(URL_CLEAR_CACHE_AND_NOTIFY)) {
      // This does the same as URL_REFRESH_RENDERER with the only difference of displaying a notification afterwards. The reason to have
      // handler is that we have different entry points for the action, one of which is "Clear cache". The user probably expects a result
      // of clicking that link that has something to do with the cache being cleared.
      handleRefreshRenderUrl(surface);
      showNotification("Cache cleared");
    }
    else if (url.startsWith(URL_ENABLE_LAYOUTLIB_NATIVE)) {
      handleEnableLayoutlibNative(true);
    }
    else if (url.startsWith(URL_DISABLE_LAYOUTLIB_NATIVE)) {
      handleEnableLayoutlibNative(false);
    }
    else {
      assert false : "Unexpected URL: " + url;
    }
  }

  /**
   * Creates a file url for the given file and line position
   *
   * @param file   the file
   * @param line   the line, or -1 if not known
   * @param column the column, or 0 if not known
   * @return a URL which points to a given position in a file
   */
  @Nullable
  public static String createFilePositionUrl(@NotNull File file, int line, int column) {
    try {
      String fileUrl = SdkUtils.fileToUrlString(file);
      if (line != -1) {
        if (column > 0) {
          return fileUrl + ':' + line + ':' + column;
        }
        else {
          return fileUrl + ':' + line;
        }
      }
      return fileUrl;
    }
    catch (MalformedURLException e) {
      // Ignore
      Logger.getInstance(HtmlLinkManager.class).error(e);
      return null;
    }
  }

  private static void handleFileUrl(@NotNull String url, @NotNull Module module) {
    Project project = module.getProject();
    try {
      // Allow line numbers and column numbers to be tacked on at the end of
      // the file URL:
      //   file:<path>:<line>:<column>
      //   file:<path>:<line>
      int line = -1;
      int column = 0;
      Pattern pattern = Pattern.compile(".*:(\\d+)(:(\\d+))");
      Matcher matcher = pattern.matcher(url);
      if (matcher.matches()) {
        line = Integer.parseInt(matcher.group(1));
        column = Integer.parseInt(matcher.group(3));
        url = url.substring(0, matcher.start(1) - 1);
      }
      else {
        matcher = Pattern.compile(".*:(\\d+)").matcher(url);
        if (matcher.matches()) {
          line = Integer.parseInt(matcher.group(1));
          url = url.substring(0, matcher.start(1) - 1);
        }
      }
      File ioFile = SdkUtils.urlToFile(url);
      VirtualFile file = LocalFileSystem.getInstance().findFileByIoFile(ioFile);
      if (file != null) {
        openEditor(project, file, line, column);
      }
    }
    catch (MalformedURLException e) {
      // Ignore
    }
  }

  static abstract class CommandLink implements Runnable {
    private final String myCommandName;
    private final PsiFile myFile;

    CommandLink(@NotNull String commandName, @NotNull PsiFile file) {
      myCommandName = commandName;
      myFile = file;
    }
    void executeCommand() {
      WriteCommandAction.writeCommandAction(myFile.getProject(), myFile).withName(myCommandName).run(() -> run());
    }
  }

  String createCommandLink(@NotNull CommandLink command) {
    String url = URL_COMMAND + myNextLinkId;
    if (myLinkCommands == null) {
      myLinkCommands = new SparseArray<CommandLink>(5);
    }
    myLinkCommands.put(myNextLinkId, command);
    myNextLinkId++;
    return url;
  }

  public String createRunnableLink(@NotNull Runnable runnable) {
    String url = URL_RUNNABLE + myNextLinkId;
    if (myLinkRunnables == null) {
      myLinkRunnables = new SparseArray<>(5);
    }
    myLinkRunnables.put(myNextLinkId, runnable);
    myNextLinkId++;

    return url;
  }

  @Nullable
  private Runnable getLinkRunnable(String url) {
    if (myLinkRunnables != null && url.startsWith(URL_RUNNABLE)) {
      String idString = url.substring(URL_RUNNABLE.length());
      int id = Integer.decode(idString);
      return myLinkRunnables.get(id);
    }
    return null;
  }

  public String createReplaceTagsUrl(String from, String to) {
    return URL_REPLACE_TAGS + from + '/' + to;
  }

  private static void handleReplaceTagsUrl(@NotNull String url, @NotNull Module module, @NotNull PsiFile file) {
    assert url.startsWith(URL_REPLACE_TAGS) : url;
    int start = URL_REPLACE_TAGS.length();
    int delimiterPos = url.indexOf('/', start);
    if (delimiterPos != -1) {
      String wrongTag = url.substring(start, delimiterPos);
      String rightTag = url.substring(delimiterPos + 1);
      new ReplaceTagFix((XmlFile)file, wrongTag, rightTag).run();
    }
  }

  public String createBuildProjectUrl() {
    return URL_BUILD;
  }

  private static void handleBuildProjectUrl(@NotNull String url, @NotNull Project project) {
    assert url.equals(URL_BUILD) : url;
    ProjectSystemUtil.getProjectSystem(project).buildProject();
  }

  public String createSyncProjectUrl() {
    return URL_SYNC;
  }

  private static void handleSyncProjectUrl(@NotNull String url, @NotNull Project project) {
    assert url.equals(URL_SYNC) : url;

    ProjectSystemSyncManager.SyncReason reason = project.isInitialized() ? ProjectSystemSyncManager.SyncReason.PROJECT_MODIFIED : ProjectSystemSyncManager.SyncReason.PROJECT_LOADED;
    ProjectSystemUtil.getProjectSystem(project).getSyncManager().syncProject(reason);
  }

  public String createEditClassPathUrl() {
    return URL_EDIT_CLASSPATH;
  }

  private static void handleEditClassPathUrl(@NotNull String url, @NotNull Module module) {
    assert url.equals(URL_EDIT_CLASSPATH) : url;
    ProjectSettingsService.getInstance(module.getProject()).openModuleSettings(module);
  }

  public String createOpenClassUrl(@NotNull String className) {
    return URL_OPEN_CLASS + className;
  }

  private static void handleOpenClassUrl(@NotNull String url, @NotNull Module module) {
    assert url.startsWith(URL_OPEN_CLASS) : url;
    String className = url.substring(URL_OPEN_CLASS.length());
    Project project = module.getProject();
    PsiClass clz = JavaPsiFacade.getInstance(project).findClass(className, GlobalSearchScope.allScope(project));
    if (clz != null) {
      PsiFile containingFile = clz.getContainingFile();
      if (containingFile != null) {
        openEditor(project, containingFile, clz.getTextOffset());
      }
    }
  }

  private static void handleShowXmlUrl(@NotNull String url, @NotNull Module module, @NotNull PsiFile file) {
    assert url.equals(URL_SHOW_XML) : url;
    openEditor(module.getProject(), file, 0, -1);
  }

  public String createShowTagUrl(String tag) {
    return URL_SHOW_TAG + tag;
  }

  private static void handleShowTagUrl(@NotNull String url, @NotNull Module module, @NotNull final PsiFile file) {
    assert url.startsWith(URL_SHOW_TAG) : url;
    final String tagName = url.substring(URL_SHOW_TAG.length());

    XmlTag first = ApplicationManager.getApplication().runReadAction((Computable<XmlTag>)() -> {
      Collection<XmlTag> xmlTags = PsiTreeUtil.findChildrenOfType(file, XmlTag.class);
      for (XmlTag tag : xmlTags) {
        if (tagName.equals(tag.getName())) {
          return tag;
        }
      }

      return null;
    });

    if (first != null) {
      PsiNavigateUtil.navigate(first);
    }
    else {
      // Fall back to just opening the editor
      openEditor(module.getProject(), file, 0, -1);
    }
  }

  public String createNewClassUrl(String className) {
    return URL_CREATE_CLASS + className;
  }

  private static void handleNewClassUrl(@NotNull String url, @NotNull Module module) {
    assert url.startsWith(URL_CREATE_CLASS) : url;
    String s = url.substring(URL_CREATE_CLASS.length());

    final Project project = module.getProject();
    String title = "Create Custom View";

    final String className;
    final String packageName;
    int index = s.lastIndexOf('.');
    if (index == -1) {
      className = s;
      packageName = AndroidManifestUtils.getPackageName(module);
      if (packageName == null) {
        return;
      }
    }
    else {
      packageName = s.substring(0, index);
      className = s.substring(index + 1);
    }
    CreateClassDialog dialog = new CreateClassDialog(project, title, className, packageName, CreateClassKind.CLASS, true, module) {
      @Override
      protected boolean reportBaseInSourceSelectionInTest() {
        return true;
      }
    };
    dialog.show();
    if (dialog.getExitCode() == DialogWrapper.OK_EXIT_CODE) {
      final PsiDirectory targetDirectory = dialog.getTargetDirectory();
      if (targetDirectory != null) {
        PsiClass newClass = WriteCommandAction.writeCommandAction(project).withName("Create Class").compute(()-> {
            PsiClass targetClass = JavaDirectoryService.getInstance().createClass(targetDirectory, className);
            PsiManager manager = PsiManager.getInstance(project);
            final JavaPsiFacade facade = JavaPsiFacade.getInstance(manager.getProject());
            final PsiElementFactory factory = facade.getElementFactory();

            // Extend android.view.View
            PsiJavaCodeReferenceElement superclassReference =
              factory.createReferenceElementByFQClassName(CLASS_VIEW, targetClass.getResolveScope());
            PsiReferenceList extendsList = targetClass.getExtendsList();
            if (extendsList != null) {
              extendsList.add(superclassReference);
            }

            // Add constructor
            GlobalSearchScope scope = GlobalSearchScope.allScope(project);
            PsiJavaFile javaFile = (PsiJavaFile)targetClass.getContainingFile();
            PsiImportList importList = javaFile.getImportList();
            if (importList != null) {
              PsiClass contextClass = JavaPsiFacade.getInstance(project).findClass(CLASS_CONTEXT, scope);
              if (contextClass != null) {
                importList.add(factory.createImportStatement(contextClass));
              }
              PsiClass attributeSetClass = JavaPsiFacade.getInstance(project).findClass(CLASS_ATTRIBUTE_SET, scope);
              if (attributeSetClass != null) {
                importList.add(factory.createImportStatement(attributeSetClass));
              }
            }

            PsiMethod constructor1arg = factory.createMethodFromText(
              "public " + className + "(Context context) {\n" +
              "  this(context, null);\n" +
              "}\n", targetClass);
            targetClass.add(constructor1arg);

            PsiMethod constructor2args = factory.createMethodFromText(
              "public " + className + "(Context context, AttributeSet attrs) {\n" +
              "  this(context, attrs, 0);\n" +
              "}\n", targetClass);
            targetClass.add(constructor2args);

            PsiMethod constructor3args = factory.createMethodFromText(
              "public " + className + "(Context context, AttributeSet attrs, int defStyle) {\n" +
              "  super(context, attrs, defStyle);\n" +
              "}\n", targetClass);
            targetClass.add(constructor3args);

            // Format class
            CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(manager.getProject());
            PsiFile containingFile = targetClass.getContainingFile();
            if (containingFile != null) {
              codeStyleManager.reformat(javaFile);
            }

            return targetClass;
          }
        );

        if (newClass != null) {
          PsiFile file = newClass.getContainingFile();
          if (file != null) {
            openEditor(project, file, newClass.getTextOffset());
          }
        }
      }
    }
  }

  public String createOpenStackUrl(@NotNull String className, @NotNull String methodName, @NotNull String fileName, int lineNumber) {
    return URL_OPEN + className + '#' + methodName + ';' + fileName + ':' + lineNumber;
  }

  private static void handleOpenStackUrl(@NotNull String url, @NotNull Module module) {
    assert url.startsWith(URL_OPEN) : url;
    // Syntax: URL_OPEN + className + '#' + methodName + ';' + fileName + ':' + lineNumber;
    int start = URL_OPEN.length();
    int semi = url.indexOf(';', start);
    String className;
    String fileName;
    int line;
    if (semi != -1) {
      className = url.substring(start, semi);
      int colon = url.indexOf(':', semi + 1);
      if (colon != -1) {
        fileName = url.substring(semi + 1, colon);
        line = Integer.decode(url.substring(colon + 1));
      }
      else {
        fileName = url.substring(semi + 1);
        line = -1;
      }
      // Attempt to open file
    }
    else {
      className = url.substring(start);
      fileName = null;
      line = -1;
    }
    String method = null;
    int hash = className.indexOf('#');
    if (hash != -1) {
      method = className.substring(hash + 1);
      className = className.substring(0, hash);
    }

    Project project = module.getProject();
    PsiClass clz = JavaPsiFacade.getInstance(project).findClass(className, GlobalSearchScope.allScope(project));
    if (clz != null) {
      PsiFile containingFile = clz.getContainingFile();
      if (fileName != null && containingFile != null && line != -1) {
        VirtualFile virtualFile = containingFile.getVirtualFile();
        if (virtualFile != null) {
          String name = virtualFile.getName();
          if (fileName.equals(name)) {
            // Use the line number rather than the methodName
            openEditor(project, containingFile, line - 1, -1);
            return;
          }
        }
      }

      if (method != null) {
        PsiMethod[] methodsByName = clz.findMethodsByName(method, true);
        for (PsiMethod m : methodsByName) {
          PsiFile psiFile = m.getContainingFile();
          if (psiFile != null) {
            VirtualFile virtualFile = psiFile.getVirtualFile();
            if (virtualFile != null) {
              OpenFileDescriptor descriptor = new OpenFileDescriptor(project, virtualFile, m.getTextOffset());
              FileEditorManager.getInstance(project).openEditor(descriptor, true);
              return;
            }
          }
        }
      }

      if (fileName != null) {
        PsiFile[] files = FilenameIndex.getFilesByName(project, fileName, GlobalSearchScope.allScope(project));
        for (PsiFile psiFile : files) {
          if (openEditor(project, psiFile, line != -1 ? line - 1 : -1, -1)) {
            break;
          }
        }
      }
    }
  }

  private static boolean openEditor(@NotNull Project project, @NotNull PsiFile psiFile, int line, int column) {
    VirtualFile file = psiFile.getVirtualFile();
    if (file != null) {
      return openEditor(project, file, line, column);
    }

    return false;
  }

  private static boolean openEditor(@NotNull Project project, @NotNull VirtualFile file, int line, int column) {
    OpenFileDescriptor descriptor = new OpenFileDescriptor(project, file, line, column);
    FileEditorManager manager = FileEditorManager.getInstance(project);

    // Attempt to prefer text editor if it's available for this file
    if (manager.openTextEditor(descriptor, true) != null) {
      return true;
    }

    return !manager.openEditor(descriptor, true).isEmpty();
  }

  private static boolean openEditor(@NotNull Project project, @NotNull PsiFile psiFile, int offset) {
    VirtualFile file = psiFile.getVirtualFile();
    if (file != null) {
      return openEditor(project, file, offset);
    }

    return false;
  }

  private static boolean openEditor(@NotNull Project project, @NotNull VirtualFile file, int offset) {
    OpenFileDescriptor descriptor = new OpenFileDescriptor(project, file, offset);
    return !FileEditorManager.getInstance(project).openEditor(descriptor, true).isEmpty();
  }

  public String createAssignFragmentUrl(@Nullable String id) {
    return URL_ASSIGN_FRAGMENT_URL + (id != null ? id : "");
  }

  /**
   * Converts a (possibly ambiguous) class name like A.B.C.D into an Android-style class name
   * like A.B$C$D where package names are separated by dots and inner classes are separated by dollar
   * signs (similar to how the internal JVM format uses dollar signs for inner classes but slashes
   * as package separators)
   */
  @NotNull
  private static String getFragmentClass(@NotNull final Module module, @NotNull final String fqcn) {
    return ApplicationManager.getApplication().runReadAction((Computable<String>)() -> {
      Project project = module.getProject();
      JavaPsiFacade finder = JavaPsiFacade.getInstance(project);
      PsiClass psiClass = finder.findClass(fqcn, module.getModuleScope());
      if (psiClass == null) {
        psiClass = finder.findClass(fqcn, GlobalSearchScope.allScope(project));
      }

      if (psiClass != null) {
        String jvmClassName = ClassUtil.getJVMClassName(psiClass);
        if (jvmClassName != null) {
          return jvmClassName.replace('/', '.');
        }
      }

      return fqcn;
    });
  }

  private static void handleAssignFragmentUrl(@NotNull String url, @NotNull Module module, @NotNull final PsiFile file) {
    assert url.startsWith(URL_ASSIGN_FRAGMENT_URL) : url;

    Predicate<PsiClass> psiFilter = ChooseClassDialog.getUserDefinedPublicAndUnrestrictedFilter();
    String className = ChooseClassDialog
      .openDialog(module, "Fragments", null, psiFilter, CLASS_FRAGMENT, CLASS_V4_FRAGMENT.oldName(), CLASS_V4_FRAGMENT.newName());
    if (className == null) {
      return;
    }
    final String fragmentClass = getFragmentClass(module, className);

    int start = URL_ASSIGN_FRAGMENT_URL.length();
    final String id;
    if (start == url.length()) {
      // No specific fragment identified; use the first one
      id = null;
    }
    else {
      id = Lint.stripIdPrefix(url.substring(start));
    }

    WriteCommandAction.writeCommandAction(module.getProject(), file).withName("Assign Fragment").run(()-> {
        Collection<XmlTag> tags = PsiTreeUtil.findChildrenOfType(file, XmlTag.class);
        for (XmlTag tag : tags) {
          if (!tag.getName().equals(VIEW_FRAGMENT)) {
            continue;
          }

          if (id != null) {
            String tagId = tag.getAttributeValue(ATTR_ID, ANDROID_URI);
            if (tagId == null || !tagId.endsWith(id) || !id.equals(Lint.stripIdPrefix(tagId))) {
              continue;
            }
          }

          if (tag.getAttribute(ATTR_NAME, ANDROID_URI) == null && tag.getAttribute(ATTR_CLASS) == null) {
            tag.setAttribute(ATTR_NAME, ANDROID_URI, fragmentClass);
            return;
          }
        }

        if (id == null) {
          for (XmlTag tag : tags) {
            if (!tag.getName().equals(VIEW_FRAGMENT)) {
              continue;
            }

            tag.setAttribute(ATTR_NAME, ANDROID_URI, fragmentClass);
            break;
          }
        }
      });
  }

  public String createPickLayoutUrl(@NotNull String activityName) {
    return URL_ASSIGN_LAYOUT_URL + activityName;
  }

  public String createAssignLayoutUrl(@NotNull String activityName, @NotNull String layout) {
    return URL_ASSIGN_LAYOUT_URL + activityName + ':' + layout;
  }

  private static void handleAssignLayoutUrl(@NotNull String url, @NotNull final Module module, @NotNull final PsiFile file) {
    assert url.startsWith(URL_ASSIGN_LAYOUT_URL) : url;
    int start = URL_ASSIGN_LAYOUT_URL.length();
    int layoutStart = url.indexOf(':', start + 1);
    Project project = module.getProject();
    XmlFile xmlFile = (XmlFile)file;
    if (layoutStart == -1) {
      // Only specified activity; pick it
      String activityName = url.substring(start);
      pickLayout(module, xmlFile, activityName);
    }
    else {
      // Set directory to specified layoutName
      final String activityName = url.substring(start, layoutStart);
      final String layoutName = url.substring(layoutStart + 1);
      final String layout = LAYOUT_RESOURCE_PREFIX + layoutName;
      assignLayout(project, xmlFile, activityName, layout);
    }
  }

  private static void pickLayout(
    @NotNull final Module module,
    @NotNull final XmlFile file,
    @NotNull final String activityName) {

    AndroidFacet facet = AndroidFacet.getInstance(module);
    assert facet != null;

    ResourcePickerDialog dialog = ResourceChooserHelperKt.createResourcePickerDialog(
      "Choose a Layout",
      null,
      facet,
      EnumSet.of(ResourceType.LAYOUT),
      null,
      true,
      false,
      file.getVirtualFile()
    );

    if (dialog.showAndGet()) {
      String layout = dialog.getResourceName();
      if (!layout.equals(LAYOUT_RESOURCE_PREFIX + file.getName())) {
        assignLayout(module.getProject(), file, activityName, layout);
      }
    }
  }

  private static void assignLayout(
    @NotNull final Project project,
    @NotNull final XmlFile file,
    @NotNull final String activityName,
    @NotNull final String layout) {

    WriteCommandAction.writeCommandAction(project, file).withName("Assign Preview Layout").run(() -> {
      IdeResourcesUtil.ensureNamespaceImported(file, TOOLS_URI, null);
      Collection<XmlTag> xmlTags = PsiTreeUtil.findChildrenOfType(file, XmlTag.class);
      for (XmlTag tag : xmlTags) {
        if (tag.getName().equals(VIEW_FRAGMENT)) {
          String name = tag.getAttributeValue(ATTR_CLASS);
          if (name == null || name.isEmpty()) {
            name = tag.getAttributeValue(ATTR_NAME, ANDROID_URI);
          }
          if (activityName.equals(name)) {
            tag.setAttribute(ATTR_LAYOUT, TOOLS_URI, layout);
          }
        }
      }
    });
  }

  public String createIgnoreFragmentsUrl() {
    return URL_ACTION_IGNORE_FRAGMENTS;
  }

  private static void handleIgnoreFragments(@NotNull String url, @Nullable EditorDesignSurface surface) {
    assert url.equals(URL_ACTION_IGNORE_FRAGMENTS);
    RenderLogger.ignoreFragments();
    requestRender(surface);
  }

  public String createEditAttributeUrl(String attribute, String value) {
    return URL_EDIT_ATTRIBUTE + attribute + '/' + value;
  }

  private static void handleEditAttribute(@NotNull String url, @NotNull Module module, @NotNull final PsiFile file) {
    assert url.startsWith(URL_EDIT_ATTRIBUTE);
    int attributeStart = URL_EDIT_ATTRIBUTE.length();
    int valueStart = url.indexOf('/');
    final String attributeName = url.substring(attributeStart, valueStart);
    final String value = url.substring(valueStart + 1);

    XmlAttribute first = ApplicationManager.getApplication().runReadAction((Computable<XmlAttribute>)() -> {
      Collection<XmlAttribute> attributes = PsiTreeUtil.findChildrenOfType(file, XmlAttribute.class);
      for (XmlAttribute attribute : attributes) {
        if (attributeName.equals(attribute.getLocalName()) && value.equals(attribute.getValue())) {
          return attribute;
        }
      }

      return null;
    });

    if (first != null) {
      PsiNavigateUtil.navigate(first.getValueElement());
    }
    else {
      // Fall back to just opening the editor
      openEditor(module.getProject(), file, 0, -1);
    }
  }

  public String createReplaceAttributeValueUrl(String attribute, String oldValue, String newValue) {
    return URL_REPLACE_ATTRIBUTE_VALUE + attribute + '/' + oldValue + '/' + newValue;
  }

  private static void handleReplaceAttributeValue(@NotNull String url, @NotNull Module module, @NotNull final PsiFile file) {
    assert url.startsWith(URL_REPLACE_ATTRIBUTE_VALUE);
    int attributeStart = URL_REPLACE_ATTRIBUTE_VALUE.length();
    int valueStart = url.indexOf('/');
    int newValueStart = url.indexOf('/', valueStart + 1);
    final String attributeName = url.substring(attributeStart, valueStart);
    final String oldValue = url.substring(valueStart + 1, newValueStart);
    final String newValue = url.substring(newValueStart + 1);

    WriteCommandAction.writeCommandAction(module.getProject(), file).withName("Set Attribute Value").run(() -> {
      Collection<XmlAttribute> attributes = PsiTreeUtil.findChildrenOfType(file, XmlAttribute.class);
      int oldValueLen = oldValue.length();
      for (XmlAttribute attribute : attributes) {
        if (attributeName.equals(attribute.getLocalName())) {
          String attributeValue = attribute.getValue();
          if (attributeValue == null) {
            continue;
          }
          if (oldValue.equals(attributeValue)) {
            attribute.setValue(newValue);
          }
          else {
            int index = attributeValue.indexOf(oldValue);
            if (index != -1) {
              if ((index == 0 || attributeValue.charAt(index - 1) == '|') &&
                  (index + oldValueLen == attributeValue.length() || attributeValue.charAt(index + oldValueLen) == '|')) {
                attributeValue = attributeValue.substring(0, index) + newValue + attributeValue.substring(index + oldValueLen);
                attribute.setValue(attributeValue);
              }
            }
          }
        }
      }
    });
  }

  public String createDisableSandboxUrl() {
    return URL_DISABLE_SANDBOX;
  }

  private static void handleDisableSandboxUrl(@NotNull Module module, @Nullable EditorDesignSurface surface) {
    RenderSecurityManager.sEnabled = false;
    requestRender(surface);

    Messages.showInfoMessage(module.getProject(),
                             "The custom view rendering sandbox was disabled for this session.\n\n" +
                             "You can turn it off permanently by adding\n" +
                             RenderSecurityManager.ENABLED_PROPERTY + "=" + VALUE_FALSE + "\n" +
                             "to {install}/bin/idea.properties.",
                             "Disabled Rendering Sandbox");
  }

  public String createRefreshRenderUrl() {
    return URL_REFRESH_RENDER;
  }

  public String createClearCacheUrl() {
    return URL_CLEAR_CACHE_AND_NOTIFY;
  }

  private static void handleRefreshRenderUrl(@Nullable EditorDesignSurface surface) {
      if (surface != null) {
        RenderUtils.clearCache(surface.getConfigurations());
      }
  }

  private static void requestRender(@Nullable EditorDesignSurface surface) {
      if (surface != null) {
        surface.forceUserRequestedRefresh();
      }
  }

  public String createAddDependencyUrl(GoogleMavenArtifactId artifactId) {
    return URL_ADD_DEPENDENCY + artifactId;
  }

  @VisibleForTesting
  static void handleAddDependency(@NotNull String url, @NotNull final Module module) {
    assert url.startsWith(URL_ADD_DEPENDENCY) : url;
    String coordinateStr = url.substring(URL_ADD_DEPENDENCY.length());
    GradleCoordinate coordinate = GradleCoordinate.parseCoordinateString(coordinateStr + ":+");
    if (coordinate == null) {
      Logger.getInstance(HtmlLinkManager.class).warn("Invalid coordinate " + coordinateStr);
      return;
    }
    if (DependencyManagementUtil.addDependencies(module, Collections.singletonList(coordinate), false, false)
                                .isEmpty()) {
      return;
    }
    Logger.getInstance(HtmlLinkManager.class).warn("Could not add dependency " + coordinate);
  }

  @NotNull
  public String createEnableLayoutlibNativeUrl() {
    return URL_ENABLE_LAYOUTLIB_NATIVE;
  }

  @NotNull
  public String createDisableLayoutlibNativeUrl() {
    return URL_DISABLE_LAYOUTLIB_NATIVE;
  }

  private static void handleEnableLayoutlibNative(boolean enable) {
    LayoutEditorEvent.Builder eventBuilder = LayoutEditorEvent.newBuilder();
    if (enable) {
      eventBuilder.setType(LayoutEditorEvent.LayoutEditorEventType.ENABLE_LAYOUTLIB_NATIVE);
      PluginManagerCore.enablePlugin(LAYOUTLIB_NATIVE_PLUGIN);
    }
    else {
      eventBuilder.setType(LayoutEditorEvent.LayoutEditorEventType.DISABLE_LAYOUTLIB_NATIVE);
      PluginManagerCore.enablePlugin(LAYOUTLIB_STANDARD_PLUGIN);
      PluginManagerCore.disablePlugin(LAYOUTLIB_NATIVE_PLUGIN);
    }

    AndroidStudioEvent.Builder studioEvent = AndroidStudioEvent.newBuilder()
      .setCategory(AndroidStudioEvent.EventCategory.LAYOUT_EDITOR)
      .setKind(AndroidStudioEvent.EventKind.LAYOUT_EDITOR_EVENT)
      .setLayoutEditorEvent(eventBuilder.build());
    UsageTracker.log(studioEvent);

    PluginManagerConfigurable.shutdownOrRestartApp();
  }
}
