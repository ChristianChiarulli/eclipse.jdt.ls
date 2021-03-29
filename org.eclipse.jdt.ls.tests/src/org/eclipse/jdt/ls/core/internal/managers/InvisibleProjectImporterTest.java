/*******************************************************************************
 * Copyright (c) 2018-2021 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.managers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jdt.ls.core.internal.JavaProjectHelper;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.TestVMType;
import org.eclipse.jdt.ls.core.internal.preferences.ClientPreferences;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences;
import org.eclipse.lsp4j.FileSystemWatcher;
import org.junit.Test;

public class InvisibleProjectImporterTest extends AbstractInvisibleProjectBasedTest {

	@Test
	public void importIncompleteFolder() throws Exception {
		IProject invisibleProject = copyAndImportFolder("maven/salut/src/main/java/org/sample", "Bar.java");
		assertFalse(invisibleProject.exists());
	}

	@Test
	public void importCompleteFolder() throws Exception {
		IProject invisibleProject = copyAndImportFolder("singlefile/lesson1", "src/org/samples/HelloWorld.java");
		assertTrue(invisibleProject.exists());
		IPath sourcePath = invisibleProject.getFolder(new Path(ProjectUtils.WORKSPACE_LINK).append("src")).getFullPath();
		assertTrue(ProjectUtils.isOnSourcePath(sourcePath, JavaCore.create(invisibleProject)));
	}

	@Test
	public void importCompleteFolderWithoutTriggerFile() throws Exception {
		IProject invisibleProject = copyAndImportFolder("singlefile/lesson1", null);
		assertFalse(invisibleProject.exists());
	}

	@Test
	public void importPartialMavenFolder() throws Exception {
		File projectFolder = copyFiles("maven/salut-java11", true);
		IPath projectFullPath = Path.fromOSString(projectFolder.getAbsolutePath());
		IPath rootPath = projectFullPath.append("src");
		IProject invisibleProject = importRootFolder(rootPath, "main/java/org/sample/Bar.java");
		assertFalse(invisibleProject.exists());
	}

	@Test
	public void importPartialGradleFolder() throws Exception {
		File projectFolder = copyFiles("gradle/gradle-11", true);
		IPath projectFullPath = Path.fromOSString(projectFolder.getAbsolutePath());
		IPath rootPath = projectFullPath.append("src");
		IProject invisibleProject = importRootFolder(rootPath, "main/java/foo/bar/Foo.java");
		assertFalse(invisibleProject.exists());
	}

	@Test
	public void automaticJarDetectionLibUnderSource() throws Exception {
		ClientPreferences mockCapabilies = mock(ClientPreferences.class);
		when(mockCapabilies.isWorkspaceChangeWatchedFilesDynamicRegistered()).thenReturn(Boolean.TRUE);
		when(preferenceManager.getClientPreferences()).thenReturn(mockCapabilies);

		File projectFolder = createSourceFolderWithLibs("automaticJarDetectionLibUnderSource");

		IProject invisibleProject = importRootFolder(projectFolder, "Test.java");
		assertNoErrors(invisibleProject);

		IJavaProject javaProject = JavaCore.create(invisibleProject);
		IClasspathEntry[] classpath = javaProject.getRawClasspath();
		assertEquals("Unexpected classpath:\n" + JavaProjectHelper.toString(classpath), 3, classpath.length);
		assertEquals("foo.jar", classpath[2].getPath().lastSegment());
		assertEquals("foo-sources.jar", classpath[2].getSourceAttachmentPath().lastSegment());

		List<FileSystemWatcher> watchers = projectsManager.registerWatchers();
		//watchers.sort((a, b) -> a.getGlobPattern().compareTo(b.getGlobPattern()));
		assertEquals(10, watchers.size()); // basic(8) + project(1) + library(1)
		String srcGlobPattern = watchers.stream().filter(w -> "**/src/**".equals(w.getGlobPattern())).findFirst().get().getGlobPattern();
		assertTrue("Unexpected source glob pattern: " + srcGlobPattern, srcGlobPattern.equals("**/src/**"));
		String projGlobPattern = watchers.stream().filter(w -> w.getGlobPattern().endsWith(projectFolder.getName() + "/**")).findFirst().get().getGlobPattern();
		assertTrue("Unexpected project glob pattern: " + projGlobPattern, projGlobPattern.endsWith(projectFolder.getName() + "/**"));
		String libGlobPattern = watchers.stream().filter(w -> w.getGlobPattern().endsWith(projectFolder.getName() + "/lib/**")).findFirst().get().getGlobPattern();
		assertTrue("Unexpected library glob pattern: " + libGlobPattern, libGlobPattern.endsWith(projectFolder.getName() + "/lib/**"));
	}

	public void automaticJarDetection() throws Exception {
		ClientPreferences mockCapabilies = mock(ClientPreferences.class);
		when(mockCapabilies.isWorkspaceChangeWatchedFilesDynamicRegistered()).thenReturn(Boolean.TRUE);
		when(preferenceManager.getClientPreferences()).thenReturn(mockCapabilies);

		File projectFolder = createSourceFolderWithLibs("automaticJarDetection", "src", true);

		IProject invisibleProject = importRootFolder(projectFolder, "Test.java");
		assertNoErrors(invisibleProject);

		IJavaProject javaProject = JavaCore.create(invisibleProject);
		IClasspathEntry[] classpath = javaProject.getRawClasspath();
		assertEquals("Unexpected classpath:\n" + JavaProjectHelper.toString(classpath), 3, classpath.length);
		assertEquals("foo.jar", classpath[2].getPath().lastSegment());
		assertEquals("foo-sources.jar", classpath[2].getSourceAttachmentPath().lastSegment());

		List<FileSystemWatcher> watchers = projectsManager.registerWatchers();
		watchers.sort((a, b) -> a.getGlobPattern().compareTo(b.getGlobPattern()));
		assertEquals(10, watchers.size());
		String srcGlobPattern = watchers.get(7).getGlobPattern();
		assertTrue("Unexpected source glob pattern: " + srcGlobPattern, srcGlobPattern.equals("**/src/**"));
		String libGlobPattern = watchers.get(9).getGlobPattern();
		assertTrue("Unexpected lib glob pattern: " + libGlobPattern, libGlobPattern.endsWith(projectFolder.getName() + "/lib/**"));
	}

	@Test
	public void getPackageNameFromRelativePathOfEmptyFile() throws Exception {
		File projectFolder = copyFiles("singlefile", true);
		IProject invisibleProject = importRootFolder(projectFolder, "lesson1/Test.java");
		assertTrue(invisibleProject.exists());

		IPath workspaceRoot = Path.fromOSString(projectFolder.getAbsolutePath());
		IPath javaFile = workspaceRoot.append("lesson1/Test.java");
		String packageName = InvisibleProjectImporter.getPackageName(javaFile, workspaceRoot, JavaCore.create(invisibleProject));
		assertEquals("lesson1", packageName);
	}

	@Test
	public void getPackageNameFromNearbyNonEmptyFile() throws Exception {
		File projectFolder = copyFiles("singlefile", true);
		IProject invisibleProject = importRootFolder(projectFolder, "lesson1/samples/Empty.java");
		assertTrue(invisibleProject.exists());

		IPath workspaceRoot = Path.fromOSString(projectFolder.getAbsolutePath());
		IPath javaFile = workspaceRoot.append("lesson1/samples/Empty.java");
		String packageName = InvisibleProjectImporter.getPackageName(javaFile, workspaceRoot, JavaCore.create(invisibleProject));
		assertEquals("samples", packageName);
	}

	@Test
	public void getPackageNameInSrcEmptyFile() throws Exception {
		File projectFolder = copyFiles("singlefile", true);
		IProject invisibleProject = importRootFolder(projectFolder, "lesson1/src/main/java/demosamples/Empty1.java");
		assertTrue(invisibleProject.exists());

		IPath workspaceRoot = Path.fromOSString(projectFolder.getAbsolutePath());
		IPath javaFile = workspaceRoot.append("lesson1/src/main/java/demosamples/Empty1.java");
		String packageName = InvisibleProjectImporter.getPackageName(javaFile, workspaceRoot, JavaCore.create(invisibleProject));
		assertEquals("main.java.demosamples", packageName);
	}

	@Test
	public void getPackageName() throws Exception {
		File projectFolder = copyFiles("singlefile", true);
		IProject invisibleProject = importRootFolder(projectFolder, "Single.java");
		assertTrue(invisibleProject.exists());

		IPath workspaceRoot = Path.fromOSString(projectFolder.getAbsolutePath());
		IPath javaFile = workspaceRoot.append("Single.java");
		String packageName = InvisibleProjectImporter.getPackageName(javaFile, workspaceRoot, JavaCore.create(invisibleProject));
		assertEquals("", packageName);
	}

	@Test
	public void testPreviewFeaturesEnabledByDefault() throws Exception {
		String defaultJVM = JavaRuntime.getDefaultVMInstall().getId();
		try {
			TestVMType.setTestJREAsDefault("15");
			IProject invisibleProject = copyAndImportFolder("singlefile/java14", "foo/bar/Foo.java");
			assertTrue(invisibleProject.exists());
			assertNoErrors(invisibleProject);
			IJavaProject javaProject = JavaCore.create(invisibleProject);
			assertEquals(JavaCore.ENABLED, javaProject.getOption(JavaCore.COMPILER_PB_ENABLE_PREVIEW_FEATURES, false));
			assertEquals(JavaCore.IGNORE, javaProject.getOption(JavaCore.COMPILER_PB_REPORT_PREVIEW_FEATURES, false));
		} finally {
			TestVMType.setTestJREAsDefault(defaultJVM);
		}
	}

	@Test
	public void testPreviewFeaturesDisabledForNotLatestJDK() throws Exception {
		String defaultJVM = JavaRuntime.getDefaultVMInstall().getId();
		try {
			String secondToLastJDK = JavaCore.getAllVersions().get(JavaCore.getAllVersions().size() - 2);
			TestVMType.setTestJREAsDefault(secondToLastJDK);
			IProject invisibleProject = copyAndImportFolder("singlefile/lesson1", "src/org/samples/HelloWorld.java");
			assertTrue(invisibleProject.exists());
			assertNoErrors(invisibleProject);
			IJavaProject javaProject = JavaCore.create(invisibleProject);
			assertEquals(JavaCore.DISABLED, javaProject.getOption(JavaCore.COMPILER_PB_ENABLE_PREVIEW_FEATURES, false));
		} finally {
			TestVMType.setTestJREAsDefault(defaultJVM);
		}
	}

	@Test
	public void testSpecifyingOutputPath() throws Exception {
		Preferences preferences = preferenceManager.getPreferences();
		preferences.setInvisibleProjectOutputPath("output");
		IProject invisibleProject = copyAndImportFolder("singlefile/java14", "foo/bar/Foo.java");
		waitForBackgroundJobs();
		IJavaProject javaProject = JavaCore.create(invisibleProject);
		assertEquals(String.join("/", "", javaProject.getElementName(), ProjectUtils.WORKSPACE_LINK, "output"), javaProject.getOutputLocation().toString());
	}

	@Test
	public void testSpecifyingOutputPathInsideSourcePath() throws Exception {
		Preferences preferences = preferenceManager.getPreferences();
		preferences.setInvisibleProjectOutputPath("output");
		IProject invisibleProject = copyAndImportFolder("singlefile/java14", "foo/bar/Foo.java");
		waitForBackgroundJobs();
		IJavaProject javaProject = JavaCore.create(invisibleProject);
		boolean isOutputExcluded = false;
		for (IClasspathEntry entry : javaProject.getRawClasspath()) {
			if (entry.getEntryKind() != IClasspathEntry.CPE_SOURCE) {
				continue;
			}
			for (IPath excludePath : entry.getExclusionPatterns()) {
				if (excludePath.toString().equals("output/")) {
					isOutputExcluded = true;
					break;
				}
			}
		}
		assertTrue("Output path should be excluded from source path", isOutputExcluded);
	}

	@Test(expected = CoreException.class)
	public void testSpecifyingOutputPathEqualToSourcePath() throws Exception {
		Preferences preferences = preferenceManager.getPreferences();
		preferences.setInvisibleProjectOutputPath("src");
		copyAndImportFolder("singlefile/simple", "src/App.java");
		waitForBackgroundJobs();
	}

	@Test(expected = CoreException.class)
	public void testSpecifyingAbsoluteOutputPath() throws Exception {
		Preferences preferences = preferenceManager.getPreferences();
		preferences.setInvisibleProjectOutputPath(new File("projects").getAbsolutePath());
		copyAndImportFolder("singlefile/simple", "src/App.java");
		waitForBackgroundJobs();
	}

	@Test
	public void testSpecifyingEmptyOutputPath() throws Exception {
		Preferences preferences = preferenceManager.getPreferences();
		preferences.setInvisibleProjectOutputPath("");
		IProject invisibleProject = copyAndImportFolder("singlefile/simple", "src/App.java");
		waitForBackgroundJobs();
		IJavaProject javaProject = JavaCore.create(invisibleProject);
		assertEquals(String.join("/", "", javaProject.getElementName(), "bin"), javaProject.getOutputLocation().toString());
	}

	@Test
	public void testSpecifyingSourcePaths() throws Exception {
		Preferences preferences = preferenceManager.getPreferences();
		preferences.setInvisibleProjectSourcePaths(Arrays.asList("foo", "bar"));
		IProject invisibleProject = copyAndImportFolder("singlefile/java14", "foo/bar/Foo.java");
		waitForBackgroundJobs();
		IJavaProject javaProject = JavaCore.create(invisibleProject);
		IFolder linkFolder = invisibleProject.getFolder(ProjectUtils.WORKSPACE_LINK);

		List<String> sourcePaths = new ArrayList<>();
		for (IClasspathEntry entry : javaProject.getRawClasspath()) {
			if (entry.getEntryKind() == IClasspathEntry.CPE_SOURCE) {
				sourcePaths.add(entry.getPath().makeRelativeTo(linkFolder.getFullPath()).toString());
			}
		}
		assertEquals(1, sourcePaths.size());
		assertTrue(sourcePaths.contains("foo"));
	}

	@Test
	public void testSpecifyingEmptySourcePaths() throws Exception {
		Preferences preferences = preferenceManager.getPreferences();
		preferences.setInvisibleProjectSourcePaths(Collections.emptyList());
		IProject invisibleProject = copyAndImportFolder("singlefile/java14", "foo/bar/Foo.java");
		waitForBackgroundJobs();
		IJavaProject javaProject = JavaCore.create(invisibleProject);
		IFolder linkFolder = invisibleProject.getFolder(ProjectUtils.WORKSPACE_LINK);

		List<String> sourcePaths = new ArrayList<>();
		for (IClasspathEntry entry : javaProject.getRawClasspath()) {
			if (entry.getEntryKind() == IClasspathEntry.CPE_SOURCE) {
				sourcePaths.add(entry.getPath().makeRelativeTo(linkFolder.getFullPath()).toString());
			}
		}
		assertEquals(0, sourcePaths.size());
	}

	@Test
	public void testSpecifyingNestedSourcePaths() throws Exception {
		Preferences preferences = preferenceManager.getPreferences();
		preferences.setInvisibleProjectSourcePaths(Arrays.asList("foo", "foo/bar"));
		IProject invisibleProject = copyAndImportFolder("singlefile/java14", "foo/bar/Foo.java");
		waitForBackgroundJobs();
		IJavaProject javaProject = JavaCore.create(invisibleProject);
		IFolder linkFolder = invisibleProject.getFolder(ProjectUtils.WORKSPACE_LINK);

		List<String> sourcePaths = new ArrayList<>();
		for (IClasspathEntry entry : javaProject.getRawClasspath()) {
			if (entry.getEntryKind() == IClasspathEntry.CPE_SOURCE) {
				sourcePaths.add(entry.getPath().makeRelativeTo(linkFolder.getFullPath()).toString());
			}
		}
		assertEquals(2, sourcePaths.size());
		assertTrue(sourcePaths.contains("foo"));
		assertTrue(sourcePaths.contains("foo/bar"));
	}

	@Test
	public void testSpecifyingDuplicatedSourcePaths() throws Exception {
		Preferences preferences = preferenceManager.getPreferences();
		preferences.setInvisibleProjectSourcePaths(Arrays.asList("foo", "foo"));
		IProject invisibleProject = copyAndImportFolder("singlefile/java14", "foo/bar/Foo.java");
		waitForBackgroundJobs();
		IJavaProject javaProject = JavaCore.create(invisibleProject);
		IFolder linkFolder = invisibleProject.getFolder(ProjectUtils.WORKSPACE_LINK);

		List<String> sourcePaths = new ArrayList<>();
		for (IClasspathEntry entry : javaProject.getRawClasspath()) {
			if (entry.getEntryKind() == IClasspathEntry.CPE_SOURCE) {
				sourcePaths.add(entry.getPath().makeRelativeTo(linkFolder.getFullPath()).toString());
			}
		}
		assertEquals(1, sourcePaths.size());
		assertTrue(sourcePaths.contains("foo"));
	}

	@Test
	public void testSpecifyingRootAsSourcePaths() throws Exception {
		Preferences preferences = preferenceManager.getPreferences();
		preferences.setInvisibleProjectSourcePaths(Arrays.asList(""));
		IProject invisibleProject = copyAndImportFolder("singlefile/java14", "foo/bar/Foo.java");
		waitForBackgroundJobs();
		IJavaProject javaProject = JavaCore.create(invisibleProject);
		IFolder linkFolder = invisibleProject.getFolder(ProjectUtils.WORKSPACE_LINK);

		List<String> sourcePaths = new ArrayList<>();
		for (IClasspathEntry entry : javaProject.getRawClasspath()) {
			if (entry.getEntryKind() == IClasspathEntry.CPE_SOURCE) {
				sourcePaths.add(entry.getPath().makeRelativeTo(linkFolder.getFullPath()).toString());
			}
		}
		assertEquals(1, sourcePaths.size());
		assertTrue(sourcePaths.contains(""));
	}

	@Test(expected = CoreException.class)
	public void testSpecifyingAbsoluteSourcePath() throws Exception {
		Preferences preferences = preferenceManager.getPreferences();
		preferences.setInvisibleProjectSourcePaths(Arrays.asList(new File("projects").getAbsolutePath()));
		copyAndImportFolder("singlefile/simple", "src/App.java");
		waitForBackgroundJobs();
	}

	@Test
	public void testSpecifyingSourcePathsContainingOutputPath() throws Exception {
		Preferences preferences = preferenceManager.getPreferences();
		preferences.setInvisibleProjectSourcePaths(Arrays.asList(""));
		preferences.setInvisibleProjectOutputPath("bin");
		IProject invisibleProject = copyAndImportFolder("singlefile/java14", "foo/bar/Foo.java");
		waitForBackgroundJobs();
		IJavaProject javaProject = JavaCore.create(invisibleProject);

		for (IClasspathEntry entry : javaProject.getRawClasspath()) {
			if (entry.getEntryKind() == IClasspathEntry.CPE_SOURCE) {
				assertEquals("bin/", entry.getExclusionPatterns()[0].toString());
			}
		}
	}
}
