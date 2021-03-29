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
package org.eclipse.jdt.ls.core.internal;

import java.io.File;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;

public interface IProjectImporter {

	void initialize(File rootFolder);

	boolean applies(IProgressMonitor monitor) throws OperationCanceledException, CoreException;

	default boolean isResolved(File folder) throws OperationCanceledException, CoreException {
		return false;
	};

	void importToWorkspace(IProgressMonitor monitor) throws OperationCanceledException, CoreException;

	void reset();
}
