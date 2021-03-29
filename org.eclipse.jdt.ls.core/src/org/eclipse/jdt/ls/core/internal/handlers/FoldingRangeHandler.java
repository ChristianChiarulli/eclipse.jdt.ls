/*******************************************************************************
 * Copyright (c) 2019 Microsoft Corporation and others.
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

package org.eclipse.jdt.ls.core.internal.handlers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IImportContainer;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.SourceRange;
import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.compiler.IScanner;
import org.eclipse.jdt.core.compiler.ITerminalSymbols;
import org.eclipse.jdt.core.compiler.InvalidInputException;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.lsp4j.FoldingRange;
import org.eclipse.lsp4j.FoldingRangeKind;
import org.eclipse.lsp4j.FoldingRangeRequestParams;

public class FoldingRangeHandler {

	private static final Pattern REGION_START_PATTERN = Pattern.compile("^//\\s*#?region|^//\\s+<editor-fold.*>");
	private static final Pattern REGION_END_PATTERN = Pattern.compile("^//\\s*#?endregion|^//\\s+</editor-fold>");

	private static IScanner fScanner;

	private static IScanner getScanner() {
		if (fScanner == null) {
			fScanner = ToolFactory.createScanner(true, false, false, true);
		}
		return fScanner;
	}

	public List<FoldingRange> foldingRange(FoldingRangeRequestParams params, IProgressMonitor monitor) {
		List<FoldingRange> $ = new ArrayList<>();
		ITypeRoot unit = null;
		try {
			PreferenceManager preferenceManager = JavaLanguageServerPlugin.getInstance().getPreferencesManager();
			boolean returnCompilationUnit = preferenceManager == null ? false : preferenceManager.isClientSupportsClassFileContent() && (preferenceManager.getPreferences().isIncludeDecompiledSources());
			unit = JDTUtils.resolveTypeRoot(params.getTextDocument().getUri(), returnCompilationUnit, monitor);
			if (unit == null || (monitor != null && monitor.isCanceled())) {
				return $;
			}
			computeFoldingRanges($, unit, monitor);
			return $;
		} finally {
			JDTUtils.discardClassFileWorkingCopy(unit);
		}
	}

	private void computeFoldingRanges(List<FoldingRange> foldingRanges, ITypeRoot unit, IProgressMonitor monitor) {
		try {
			ISourceRange range = unit.getSourceRange();
			if (!SourceRange.isAvailable(range)) {
				return;
			}

			String contents = unit.getSource();
			if (StringUtils.isBlank(contents)) {
				return;
			}

			final int shift = range.getOffset();
			IScanner scanner = getScanner();
			scanner.setSource(contents.toCharArray());
			scanner.resetTo(shift, shift + range.getLength());

			int start = shift;
			int token = 0;
			Stack<Integer> regionStarts = new Stack<>();
			while (token != ITerminalSymbols.TokenNameEOF) {
				start = scanner.getCurrentTokenStartPosition();
				switch (token) {
					case ITerminalSymbols.TokenNameCOMMENT_JAVADOC:
					case ITerminalSymbols.TokenNameCOMMENT_BLOCK:
						int end = scanner.getCurrentTokenEndPosition();
						FoldingRange commentFoldingRange = new FoldingRange(scanner.getLineNumber(start) - 1, scanner.getLineNumber(end) - 1);
						commentFoldingRange.setKind(FoldingRangeKind.Comment);
						foldingRanges.add(commentFoldingRange);
						break;
					case ITerminalSymbols.TokenNameCOMMENT_LINE:
						String currentSource = String.valueOf(scanner.getCurrentTokenSource());
						if (REGION_START_PATTERN.matcher(currentSource).lookingAt()) {
							regionStarts.push(start);
						} else if (REGION_END_PATTERN.matcher(currentSource).lookingAt()) {
							if (regionStarts.size() > 0) {
								FoldingRange regionFolding = new FoldingRange(scanner.getLineNumber(regionStarts.pop()) - 1, scanner.getLineNumber(start) - 1);
								regionFolding.setKind(FoldingRangeKind.Region);
								foldingRanges.add(regionFolding);
							}
						}
						break;
					default:
						break;
				}
				token = getNextToken(scanner);
			}
			computeTypeRootRanges(foldingRanges, unit, scanner);
		} catch (CoreException e) {
			JavaLanguageServerPlugin.logException("Problem with folding range for " + unit.getPath().toPortableString(), e);
			monitor.setCanceled(true);
		}
	}

	private int getNextToken(IScanner scanner) {
		int token = 0;
		while (token == 0) {
			try {
				token = scanner.getNextToken();
			} catch (InvalidInputException e) {
				// ignore
				// JavaLanguageServerPlugin.logException("Problem with folding range", e);
			}
		}
		return token;
	}

	private void computeTypeRootRanges(List<FoldingRange> foldingRanges, ITypeRoot unit, IScanner scanner) throws CoreException {
		if (unit.hasChildren()) {
			for (IJavaElement child : unit.getChildren()) {
				if (child instanceof IImportContainer) {
					ISourceRange importRange = ((IImportContainer) child).getSourceRange();
					FoldingRange importFoldingRange = new FoldingRange(scanner.getLineNumber(importRange.getOffset()) - 1, scanner.getLineNumber(importRange.getOffset() + importRange.getLength()) - 1);
					importFoldingRange.setKind(FoldingRangeKind.Imports);
					foldingRanges.add(importFoldingRange);
				} else if (child instanceof IType) {
					computeTypeRanges(foldingRanges, (IType) child, scanner);
				}
			}
		}
	}

	private void computeTypeRanges(List<FoldingRange> foldingRanges, IType unit, IScanner scanner) throws CoreException {
		ISourceRange typeRange = unit.getSourceRange();
		foldingRanges.add(new FoldingRange(scanner.getLineNumber(unit.getNameRange().getOffset()) - 1, scanner.getLineNumber(typeRange.getOffset() + typeRange.getLength()) - 1));
		IJavaElement[] children = unit.getChildren();
		for (IJavaElement c : children) {
			if (c instanceof IMethod) {
				computeMethodRanges(foldingRanges, (IMethod) c, scanner);
			} else if (c instanceof IType) {
				computeTypeRanges(foldingRanges, (IType) c, scanner);
			}
		}
	}

	private void computeMethodRanges(List<FoldingRange> foldingRanges, IMethod method, IScanner scanner) throws CoreException {
		ISourceRange sourceRange = method.getSourceRange();
		final int shift = sourceRange.getOffset();
		scanner.resetTo(shift, shift + sourceRange.getLength());

		foldingRanges.add(new FoldingRange(scanner.getLineNumber(method.getNameRange().getOffset()) - 1, scanner.getLineNumber(shift + sourceRange.getLength()) - 1));

		int start = shift;
		int token = 0;
		Stack<Integer> leftParens = null;
		int prevCaseLine = -1;
		Map<Integer, Integer> candidates = new HashMap<>();
		while (token != ITerminalSymbols.TokenNameEOF) {
			start = scanner.getCurrentTokenStartPosition();
			switch (token) {
				case ITerminalSymbols.TokenNameLBRACE:
					if (leftParens == null) {
						// Start of method body
						leftParens = new Stack<>();
					} else {
						int startLine = scanner.getLineNumber(start) - 1;
						// Start & end overlap, adjust the previous one for visibility:
						if (candidates.containsKey(startLine)) {
							int originalStartLine = candidates.remove(startLine);
							if (originalStartLine < startLine - 1) {
								candidates.put(startLine - 1, originalStartLine);
							}
						}
						leftParens.push(startLine);
					}
					break;
				case ITerminalSymbols.TokenNameRBRACE:
					int endPos = scanner.getCurrentTokenEndPosition();
					if (leftParens != null && leftParens.size() > 0) {
						int endLine = scanner.getLineNumber(endPos) - 1;
						int startLine = leftParens.pop();
						if (startLine < endLine) {
							candidates.put(endLine, startLine);
						}
						// Assume the last switch case:
						if (startLine < prevCaseLine) {
							if (endLine - 1 > prevCaseLine) {
								candidates.put(endLine - 1, prevCaseLine);
							}
							prevCaseLine = -1;
						}
					}
					break;
				case ITerminalSymbols.TokenNamecase:
				case ITerminalSymbols.TokenNamedefault:
					int currentLine = scanner.getLineNumber(start) - 1;
					if (prevCaseLine != -1 && currentLine - 1 >= prevCaseLine) {
						candidates.put(scanner.getLineNumber(start) - 2, prevCaseLine);
					}
					prevCaseLine = currentLine;
					break;
				default:
					break;
			}
			token = getNextToken(scanner);
		}

		for (Map.Entry<Integer, Integer> entry : candidates.entrySet()) {
			foldingRanges.add(new FoldingRange(entry.getValue(), entry.getKey()));
		}
	}
}
