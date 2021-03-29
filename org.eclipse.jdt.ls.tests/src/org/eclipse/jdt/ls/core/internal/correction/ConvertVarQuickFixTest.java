/*******************************************************************************
 * Copyright (c) 2018 Red Hat Inc. and others.
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
package org.eclipse.jdt.ls.core.internal.correction;

import static org.junit.Assert.assertNotNull;

import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.ls.core.internal.CodeActionUtil;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.Before;
import org.junit.Test;

public class ConvertVarQuickFixTest extends AbstractQuickFixTest {

	private IJavaProject javaProject;
	private IPackageFragmentRoot sourceFolder;

	@Before
	public void setup() throws Exception {
		importProjects("eclipse/java10");
		IProject project = WorkspaceHelper.getProject("java10");
		javaProject = JavaCore.create(project);
		sourceFolder = javaProject.getPackageFragmentRoot(javaProject.getProject().getFolder("src/main/java"));
	}

	@Test
	public void testConvertVarTypeToResolvedType() throws Exception {
		IPackageFragment pack1 = sourceFolder.createPackageFragment("foo.bar", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package foo.bar;\n");
		buf.append("public class Test {\n");
		buf.append("    public void test() {\n");
		buf.append("        var name = \"test\";\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("Test.java", buf.toString(), false, null);
		List<Either<Command, CodeAction>> codeActions = evaluateCodeActions(cu);
		Either<Command, CodeAction> codeAction = codeActions.stream().filter(c -> getTitle(c).matches("Change type of 'name' to 'String'")).findFirst().orElse(null);
		assertNotNull(codeAction);
	}

	@Test
	public void testConvertVarTypeToResolvedType2() throws Exception {
		IPackageFragment pack1 = sourceFolder.createPackageFragment("foo.bar", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package foo.bar;\n");
		buf.append("public class Test {\n");
		buf.append("    public void test() {\n");
		buf.append("        var/*cursor*/ name = \"test\";\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("Test.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package foo.bar;\n");
		buf.append("public class Test {\n");
		buf.append("    public void test() {\n");
		buf.append("        String name = \"test\";\n");
		buf.append("    }\n");
		buf.append("}\n");

		Expected expected = new Expected("Change type of 'name' to 'String'", buf.toString(), CodeActionKind.Refactor);
		Range range = CodeActionUtil.getRange(cu, "/*cursor*/", 0);
		assertCodeActions(cu, range, expected);
	}

	@Test
	public void testConvertResolvedTypeToVar() throws Exception {
		IPackageFragment pack1 = sourceFolder.createPackageFragment("foo.bar", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package foo.bar;\n");
		buf.append("public class Test {\n");
		buf.append("    public void test() {\n");
		buf.append("        String name = \"test\";\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("Test.java", buf.toString(), false, null);
		List<Either<Command, CodeAction>> commands = evaluateCodeActions(cu);
		Either<Command, CodeAction> codeAction = commands.stream().filter(c -> getTitle(c).matches("Change type of 'name' to 'var'")).findFirst().orElse(null);
		assertNotNull(codeAction);
	}

	@Test
	public void testConvertResolvedTypeToVar2() throws Exception {
		IPackageFragment pack1 = sourceFolder.createPackageFragment("foo.bar", false, null);
		StringBuilder buf = new StringBuilder();
		buf.append("package foo.bar;\n");
		buf.append("public class Test {\n");
		buf.append("    public void test() {\n");
		buf.append("        String/*cursor*/ name = \"test\";\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu = pack1.createCompilationUnit("Test.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package foo.bar;\n");
		buf.append("public class Test {\n");
		buf.append("    public void test() {\n");
		buf.append("        var name = \"test\";\n");
		buf.append("    }\n");
		buf.append("}\n");

		Expected expected = new Expected("Change type of 'name' to 'var'", buf.toString(), CodeActionKind.Refactor);
		Range range = CodeActionUtil.getRange(cu, "/*cursor*/", 0);
		assertCodeActions(cu, range, expected);
	}

}
