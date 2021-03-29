/*******************************************************************************
 * Copyright (c) 2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.managers;

import static java.nio.file.FileVisitResult.CONTINUE;
import static java.nio.file.FileVisitResult.SKIP_SUBTREE;
import static java.nio.file.FileVisitResult.TERMINATE;

import java.io.IOException;
import java.nio.file.FileSystemLoopException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.StatusFactory;

/**
 * Searches recursively for all the directories containing a given filename.
 *
 * @author Fred Bricon
 */
public class BasicFileDetector {

	private static final String METADATA_FOLDER = "**/.metadata";
	private static final Set<FileVisitOption> FOLLOW_LINKS_OPTION = EnumSet.of(FileVisitOption.FOLLOW_LINKS);
	private List<Path> directories;
	private Path rootDir;
	private List<String> fileNames;
	private int maxDepth = 5;
	private boolean includeNested = true;
	private Set<String> exclusions = new LinkedHashSet<>(1);

	/**
	 * Constructs a new BasicFileDetector for the given root directory, searching
	 * for fileNames. By default, the search depth is limited to 5. Sub-directories
	 * of a found directory will be walked through. The ".metadata" folder is
	 * excluded.
	 *
	 * @param rootDir
	 *            the root directory to search for files
	 * @param fileNames
	 *            the names of the file to search
	 */
	public BasicFileDetector(Path rootDir, String... fileNames) {
		this.rootDir = rootDir;
		this.fileNames = fileNames == null ? new ArrayList<>() : Arrays.asList(fileNames);
		directories = new ArrayList<>();
		addExclusions(METADATA_FOLDER);
		List<String> javaImportExclusions = JavaLanguageServerPlugin.getPreferencesManager().getPreferences().getJavaImportExclusions();
		if (javaImportExclusions != null) {
			for (String pattern : javaImportExclusions) {
				addExclusions(pattern);
			}
		}
	}

	/**
	 * Adds the names of directories to exclude from the search. All its sub-directories will be skipped.
	 *
	 * @param excludes directory name(s) to exclude from the search
	 * @return a reference to this object.
	 */
	public BasicFileDetector addExclusions(String...excludes) {
		if (excludes != null) {
			exclusions.addAll(Arrays.asList(excludes));
		}
		return this;
	}

	/**
	 * Whether or not scan sub-directories of a previously found directory.
	 *
	 * @param exclude a directory name to exclude from the search
	 * @return a reference to this object.
	 */
	public BasicFileDetector includeNested(boolean includeNested) {
		this.includeNested = includeNested;
		return this;
	}

	/**
	 * Sets the maximum depth of the search
	 * @param maxDepth the maximum depth of the search. Must be > 0.
	 * @return a reference to this object.
	 */
	public BasicFileDetector maxDepth(int maxDepth) {
		Assert.isTrue(maxDepth > 0, "maxDepth must be > 0");
		this.maxDepth = maxDepth;
		return this;
	}

	/**
	 * Returns the directories found to be containing the sought-after file.
	 * @return an unmodifiable collection of {@link Path}s.
	 */
	public Collection<Path> getDirectories() {
		return Collections.unmodifiableList(directories);
	}

	/**
	 * Scan the  the directories found to be containing the sought-after file.
	 * @param monitor the {@link IProgressMonitor} used to handle scan interruption.
	 * @return an unmodifiable collection of {@link Path}s.
	 * @throws CoreException if an error is encountered during the scan
	 */
	public Collection<Path> scan(IProgressMonitor monitor) throws CoreException {
		try {
			scanDir(rootDir, (monitor == null? new NullProgressMonitor(): monitor));
		} catch (IOException e) {
			throw new CoreException(StatusFactory.newErrorStatus("Failed to scan "+rootDir, e));
		}
		return getDirectories();
	}

	private void scanDir(Path dir, final IProgressMonitor monitor) throws IOException {
		FileVisitor<Path> visitor = new SimpleFileVisitor<>() {
			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
				if (monitor.isCanceled()) {
					return TERMINATE;
				}
				Objects.requireNonNull(dir);
				if (isExcluded(dir)) {
					return SKIP_SUBTREE;
				}
				if (hasTargetFile(dir)) {
					directories.add(dir);
					return includeNested?CONTINUE:SKIP_SUBTREE;
				}
				return CONTINUE;
			}

			@Override
			public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
				Objects.requireNonNull(file);
    			if (exc instanceof FileSystemLoopException) {
        			return CONTINUE;
    			} else {
        			throw exc;
    			}
			}

		};
		Files.walkFileTree(dir, FOLLOW_LINKS_OPTION, maxDepth, visitor);
	}

	private boolean isExcluded(Path dir) {
		if (dir.getFileName() == null) {
			return true;
		}
		boolean excluded = false;
		for (String pattern : exclusions) {
			boolean includePattern = false;
			if (pattern.startsWith("!")) {
				includePattern = true;
				pattern = pattern.substring(1);
			}
			PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
			if (matcher.matches(dir)) {
				excluded = includePattern ? false : true;
			}
		}
		return excluded;
	}

	private boolean hasTargetFile(Path dir) {
		for (String fileName : fileNames) {
			if (Files.isRegularFile(dir.resolve(fileName))) {
				return true;
			}
		}
		return false;
	}

}
