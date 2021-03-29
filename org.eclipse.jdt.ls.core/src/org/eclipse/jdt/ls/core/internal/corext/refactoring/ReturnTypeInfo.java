/*******************************************************************************
 * Copyright (c) 2000, 2011 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Originally copied from org.eclipse.jdt.internal.corext.refactoring.ReturnTypeInfo
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.corext.refactoring;

import org.eclipse.core.runtime.Assert;
import org.eclipse.jdt.core.dom.ITypeBinding;


public class ReturnTypeInfo {

	private final String fOldTypeName;
	private String fNewTypeName;
	private ITypeBinding fNewTypeBinding;

	public ReturnTypeInfo(String returnType) {
		fOldTypeName= returnType;
		fNewTypeName= returnType;
	}

	public String getOldTypeName() {
		return fOldTypeName;
	}

	public String getNewTypeName() {
		return fNewTypeName;
	}

	public void setNewTypeName(String type){
		Assert.isNotNull(type);
		fNewTypeName= type;
	}

	public ITypeBinding getNewTypeBinding() {
		return fNewTypeBinding;
	}

	public void setNewTypeBinding(ITypeBinding typeBinding){
		fNewTypeBinding= typeBinding;
	}

	public boolean isTypeNameChanged() {
		return !fOldTypeName.equals(fNewTypeName);
	}

	@Override
	public String toString() {
		return fOldTypeName + " -> " + fNewTypeName; //$NON-NLS-1$
	}
}
