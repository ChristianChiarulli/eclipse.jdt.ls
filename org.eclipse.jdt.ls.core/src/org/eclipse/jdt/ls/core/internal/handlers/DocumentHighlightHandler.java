/*******************************************************************************
 * Copyright (c) 2016-2017 Red Hat Inc. and others.
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
package org.eclipse.jdt.ls.core.internal.handlers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.manipulation.CoreASTProvider;
import org.eclipse.jdt.internal.core.manipulation.search.IOccurrencesFinder;
import org.eclipse.jdt.internal.core.manipulation.search.IOccurrencesFinder.OccurrenceLocation;
import org.eclipse.jdt.internal.core.manipulation.search.OccurrencesFinder;
import org.eclipse.jdt.ls.core.internal.JDTUtils;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.lsp4j.DocumentHighlight;
import org.eclipse.lsp4j.DocumentHighlightKind;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentPositionParams;

@SuppressWarnings("restriction")
public class DocumentHighlightHandler{

	private List<DocumentHighlight> computeOccurrences(ITypeRoot unit, int line, int column, IProgressMonitor monitor) {
		if (unit != null) {
			try {
				int offset = JsonRpcHelpers.toOffset(unit.getBuffer(), line, column);
				OccurrencesFinder finder = new OccurrencesFinder();
				CompilationUnit ast = CoreASTProvider.getInstance().getAST(unit, CoreASTProvider.WAIT_YES, monitor);
				if (ast != null) {
					String error = finder.initialize(ast, offset, 0);
					if (error == null){
						List<DocumentHighlight> result = new ArrayList<>();
						OccurrenceLocation[] occurrences = finder.getOccurrences();
						if (occurrences != null) {
							for (OccurrenceLocation loc : occurrences) {
								if (monitor.isCanceled()) {
									return Collections.emptyList();
								}
								result.add(convertToHighlight(unit, loc));
							}
						}
						return result;
					}
				}
			} catch (JavaModelException e) {
				JavaLanguageServerPlugin.logException("Problem with compute occurrences for" + unit.getElementName(), e);
			}
		}
		return Collections.emptyList();
	}

	private DocumentHighlight convertToHighlight(ITypeRoot unit, OccurrenceLocation occurrence)
			throws JavaModelException {
		DocumentHighlight h = new DocumentHighlight();
		if ((occurrence.getFlags() | IOccurrencesFinder.F_WRITE_OCCURRENCE) == IOccurrencesFinder.F_WRITE_OCCURRENCE) {
			h.setKind(DocumentHighlightKind.Write);
		} else if ((occurrence.getFlags()
				| IOccurrencesFinder.F_READ_OCCURRENCE) == IOccurrencesFinder.F_READ_OCCURRENCE) {
			h.setKind(DocumentHighlightKind.Read);
		}
		int[] loc = JsonRpcHelpers.toLine(unit.getBuffer(), occurrence.getOffset());
		int[] endLoc = JsonRpcHelpers.toLine(unit.getBuffer(), occurrence.getOffset() + occurrence.getLength());

		h.setRange(new Range(
				new Position(loc[0], loc[1]),
				new Position(endLoc[0],endLoc[1])
				));
		return h;
	}

	public List<? extends DocumentHighlight> documentHighlight(TextDocumentPositionParams position, IProgressMonitor monitor) {
		ITypeRoot type = JDTUtils.resolveTypeRoot(position.getTextDocument().getUri());
		return computeOccurrences(type, position.getPosition().getLine(),
				position.getPosition().getCharacter(), monitor);
	}

}
