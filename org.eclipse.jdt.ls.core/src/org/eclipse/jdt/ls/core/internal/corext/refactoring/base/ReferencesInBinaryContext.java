/*******************************************************************************
 * Copyright (c) 2008, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Originally copied from org.eclipse.jdt.internal.corext.refactoring.base.ReferencesInBinaryContext
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.corext.refactoring.base;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.ls.core.internal.corext.refactoring.RefactoringCoreMessages;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.RefactoringStatusContext;

public class ReferencesInBinaryContext extends RefactoringStatusContext {

	private List<SearchMatch> fMatches= new ArrayList<>();

	private final String fDescription;

	public ReferencesInBinaryContext(String description) {
		fDescription= description;
	}

	public String getDescription() {
		return fDescription;
	}


	public void add(SearchMatch match) {
		fMatches.add(match);
	}

	public List<SearchMatch> getMatches() {
		return fMatches;
	}

	/*
	 * @see org.eclipse.ltk.core.refactoring.RefactoringStatusContext#getCorrespondingElement()
	 */
	@Override
	public Object getCorrespondingElement() {
		return null;
	}

	public void addErrorIfNecessary(RefactoringStatus status) {
		if (getMatches().size() != 0) {
			status.addError(RefactoringCoreMessages.ReferencesInBinaryContext_binaryRefsNotUpdated, this);
		}
	}

	@Override
	public String toString() {
		return fDescription + " (" + fMatches.size() + " matches)"; //$NON-NLS-1$ //$NON-NLS-2$
	}

}
