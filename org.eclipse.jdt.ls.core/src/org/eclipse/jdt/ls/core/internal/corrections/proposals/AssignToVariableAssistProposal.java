/*******************************************************************************
 * Copyright (c) 2000, 2018 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copied from /org.eclipse.jdt.ui/src/org/eclipse/jdt/internal/ui/text/correction/proposals/AssignToVariableAssistProposal.java
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.jdt.ls.core.internal.corrections.proposals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.NamingConventions;
import org.eclipse.jdt.core.compiler.ITerminalSymbols;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.ChildListPropertyDescriptor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.Initializer;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.VariableDeclarationExpression;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite.ImportRewriteContext;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite.TypeLocation;
import org.eclipse.jdt.internal.core.manipulation.StubUtility;
import org.eclipse.jdt.internal.core.manipulation.dom.ASTResolving;
import org.eclipse.jdt.internal.core.manipulation.util.BasicElementLabels;
import org.eclipse.jdt.internal.corext.codemanipulation.ContextSensitiveImportRewriteContext;
import org.eclipse.jdt.internal.corext.dom.ASTNodeFactory;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.dom.Bindings;
import org.eclipse.jdt.internal.corext.dom.LinkedNodeFinder;
import org.eclipse.jdt.internal.corext.dom.TokenScanner;
import org.eclipse.jdt.internal.corext.util.Messages;
import org.eclipse.jdt.ls.core.internal.JavaCodeActionKind;
import org.eclipse.jdt.ls.core.internal.corrections.CorrectionMessages;
import org.eclipse.jdt.ls.core.internal.text.correction.ModifierCorrectionSubProcessor;

/**
 * Proposals for 'Assign to variable' quick assist
 * - Assign an expression from an ExpressionStatement to a local or field
 * - Assign single or all parameter(s) to field(s)
 * */
public class AssignToVariableAssistProposal extends LinkedCorrectionProposal {

	public static final int LOCAL= 1;
	public static final int FIELD= 2;

	private final String KEY_NAME= "name";  //$NON-NLS-1$
	private final String KEY_TYPE= "type";  //$NON-NLS-1$

	private final int  fVariableKind;
	private final List<ASTNode> fNodesToAssign; // ExpressionStatement or SingleVariableDeclaration(s)
	private final ITypeBinding fTypeBinding;

	private VariableDeclarationFragment fExistingFragment;

	public AssignToVariableAssistProposal(ICompilationUnit cu, int variableKind, ExpressionStatement node, ITypeBinding typeBinding, int relevance) {
		super("", JavaCodeActionKind.QUICK_ASSIST, cu, null, relevance); //$NON-NLS-1$

		fVariableKind= variableKind;
		fNodesToAssign= new ArrayList<>();
		fNodesToAssign.add(node);

		fTypeBinding= Bindings.normalizeForDeclarationUse(typeBinding, node.getAST());
		if (variableKind == LOCAL) {
			setDisplayName(CorrectionMessages.AssignToVariableAssistProposal_assigntolocal_description);
		} else {
			setDisplayName(CorrectionMessages.AssignToVariableAssistProposal_assigntofield_description);
		}
		createImportRewrite((CompilationUnit) node.getRoot());
	}

	public AssignToVariableAssistProposal(ICompilationUnit cu, String kind, int variableKind, ExpressionStatement node, ITypeBinding typeBinding, int relevance) {
		super("", kind, cu, null, relevance); //$NON-NLS-1$

		fVariableKind = variableKind;
		fNodesToAssign = new ArrayList<>();
		fNodesToAssign.add(node);

		fTypeBinding = Bindings.normalizeForDeclarationUse(typeBinding, node.getAST());
		if (variableKind == LOCAL) {
			setDisplayName(CorrectionMessages.AssignToVariableAssistProposal_assigntolocal_description);
		} else {
			setDisplayName(CorrectionMessages.AssignToVariableAssistProposal_assigntofield_description);
		}
		createImportRewrite((CompilationUnit) node.getRoot());
	}

	public AssignToVariableAssistProposal(ICompilationUnit cu, SingleVariableDeclaration parameter, VariableDeclarationFragment existingFragment, ITypeBinding typeBinding, int relevance) {
		super("", JavaCodeActionKind.QUICK_ASSIST, cu, null, relevance); //$NON-NLS-1$

		fVariableKind= FIELD;
		fNodesToAssign= new ArrayList<>();
		fNodesToAssign.add(parameter);
		fTypeBinding= typeBinding;
		fExistingFragment= existingFragment;

		if (existingFragment == null) {
			setDisplayName(CorrectionMessages.AssignToVariableAssistProposal_assignparamtofield_description);
		} else {
			setDisplayName(Messages.format(CorrectionMessages.AssignToVariableAssistProposal_assigntoexistingfield_description, BasicElementLabels.getJavaElementName(existingFragment.getName().getIdentifier())));
		}
	}

	public AssignToVariableAssistProposal(ICompilationUnit cu, List<SingleVariableDeclaration> parameters, int relevance) {
		super("", JavaCodeActionKind.QUICK_ASSIST, cu, null, relevance); //$NON-NLS-1$

		fVariableKind= FIELD;
		fNodesToAssign= new ArrayList<>();
		fNodesToAssign.addAll(parameters);
		fTypeBinding= null;

		setDisplayName(CorrectionMessages.AssignToVariableAssistProposal_assignallparamstofields_description);
	}

	@Override
	protected ASTRewrite getRewrite() throws CoreException {
		if (fVariableKind == FIELD) {
			ASTRewrite rewrite= ASTRewrite.create(fNodesToAssign.get(0).getAST());
			if (fNodesToAssign.size() == 1) {
				return doAddField(rewrite, fNodesToAssign.get(0), fTypeBinding, 0);
			} else {
				return doAddAllFields(rewrite);
			}
		} else { // LOCAL
			return doAddLocal();
		}
	}

	private ASTRewrite doAddLocal() {
		ASTNode nodeToAssign= fNodesToAssign.get(0);
		Expression expression= ((ExpressionStatement) nodeToAssign).getExpression();
		AST ast= nodeToAssign.getAST();

		ASTRewrite rewrite= ASTRewrite.create(ast);

		createImportRewrite((CompilationUnit) nodeToAssign.getRoot());

		String[] varNames= suggestLocalVariableNames(fTypeBinding, expression);
		for (int i= 0; i < varNames.length; i++) {
			addLinkedPositionProposal(KEY_NAME, varNames[i]);
		}

		VariableDeclarationFragment newDeclFrag= ast.newVariableDeclarationFragment();
		newDeclFrag.setName(ast.newSimpleName(varNames[0]));
		newDeclFrag.setInitializer((Expression) rewrite.createCopyTarget(expression));

		Type type= evaluateType(ast, nodeToAssign, fTypeBinding, KEY_TYPE, TypeLocation.LOCAL_VARIABLE);

		if (ASTNodes.isControlStatementBody(nodeToAssign.getLocationInParent())) {
			Block block= ast.newBlock();
			block.statements().add(rewrite.createMoveTarget(nodeToAssign));
			rewrite.replace(nodeToAssign, block, null);
		}

		if (needsSemicolon(expression)) {
			VariableDeclarationStatement varStatement= ast.newVariableDeclarationStatement(newDeclFrag);
			varStatement.setType(type);
			rewrite.replace(expression, varStatement, null);
		} else {
			// trick for bug 43248: use an VariableDeclarationExpression and keep the ExpressionStatement
			VariableDeclarationExpression varExpression= ast.newVariableDeclarationExpression(newDeclFrag);
			varExpression.setType(type);
			rewrite.replace(expression, varExpression, null);
		}

		addLinkedPosition(rewrite.track(newDeclFrag.getName()), true, KEY_NAME);
		addLinkedPosition(rewrite.track(type), false, KEY_TYPE);
		setEndPosition(rewrite.track(nodeToAssign)); // set cursor after expression statement

		return rewrite;
	}

	private boolean needsSemicolon(Expression expression) {
		if ((expression.getParent().getFlags() & ASTNode.RECOVERED) != 0) {
			try {
				TokenScanner scanner= new TokenScanner(getCompilationUnit());
				return scanner.readNext(expression.getStartPosition() + expression.getLength(), true) != ITerminalSymbols.TokenNameSEMICOLON;
			} catch (CoreException e) {
				// ignore
			}
		}
		return false;
	}

	private ASTRewrite doAddField(ASTRewrite rewrite, ASTNode nodeToAssign, ITypeBinding typeBinding, int index) {
		boolean isParamToField= nodeToAssign.getNodeType() == ASTNode.SINGLE_VARIABLE_DECLARATION;

		ASTNode newTypeDecl= ASTResolving.findParentType(nodeToAssign);
		if (newTypeDecl == null) {
			return null;
		}

		Expression expression= isParamToField ? ((SingleVariableDeclaration) nodeToAssign).getName() : ((ExpressionStatement) nodeToAssign).getExpression();

		AST ast= newTypeDecl.getAST();

		createImportRewrite((CompilationUnit) nodeToAssign.getRoot());

		BodyDeclaration bodyDecl= ASTResolving.findParentBodyDeclaration(nodeToAssign);
		Block body;
		if (bodyDecl instanceof MethodDeclaration) {
			body= ((MethodDeclaration) bodyDecl).getBody();
		} else if (bodyDecl instanceof Initializer) {
			body= ((Initializer) bodyDecl).getBody();
		} else {
			return null;
		}

		IJavaProject project= getCompilationUnit().getJavaProject();
		boolean isAnonymous= newTypeDecl.getNodeType() == ASTNode.ANONYMOUS_CLASS_DECLARATION;
		boolean isStatic= Modifier.isStatic(bodyDecl.getModifiers()) && !isAnonymous;
		int modifiers= Modifier.PRIVATE;
		if (isStatic) {
			modifiers |= Modifier.STATIC;
		}

		VariableDeclarationFragment newDeclFrag= addFieldDeclaration(rewrite, newTypeDecl, modifiers, expression, nodeToAssign, typeBinding, index);
		String varName= newDeclFrag.getName().getIdentifier();

		Assignment assignment= ast.newAssignment();
		assignment.setRightHandSide((Expression) rewrite.createCopyTarget(expression));

		boolean needsThis= StubUtility.useThisForFieldAccess(project);
		if (isParamToField) {
			needsThis |= varName.equals(((SimpleName) expression).getIdentifier());
		}

		SimpleName accessName= ast.newSimpleName(varName);
		if (needsThis) {
			FieldAccess fieldAccess= ast.newFieldAccess();
			fieldAccess.setName(accessName);
			if (isStatic) {
				String typeName= ((AbstractTypeDeclaration) newTypeDecl).getName().getIdentifier();
				fieldAccess.setExpression(ast.newSimpleName(typeName));
			} else {
				fieldAccess.setExpression(ast.newThisExpression());
			}
			assignment.setLeftHandSide(fieldAccess);
		} else {
			assignment.setLeftHandSide(accessName);
		}

		ASTNode selectionNode;
		if (isParamToField) {
			// assign parameter to field
			ExpressionStatement statement= ast.newExpressionStatement(assignment);
			int insertIdx= findAssignmentInsertIndex(body.statements(), nodeToAssign) + index;
			rewrite.getListRewrite(body, Block.STATEMENTS_PROPERTY).insertAt(statement, insertIdx, null);
			selectionNode= statement;
		} else {
			if (needsSemicolon(expression)) {
				rewrite.replace(expression, ast.newExpressionStatement(assignment), null);
			} else {
				rewrite.replace(expression, assignment, null);
			}
			selectionNode= nodeToAssign;
		}

		addLinkedPosition(rewrite.track(newDeclFrag.getName()), false, KEY_NAME + index);
		if (!isParamToField) {
			FieldDeclaration fieldDeclaration= (FieldDeclaration) newDeclFrag.getParent();
			addLinkedPosition(rewrite.track(fieldDeclaration.getType()), false, KEY_TYPE);
		}
		addLinkedPosition(rewrite.track(accessName), true, KEY_NAME + index);
		IVariableBinding variableBinding= newDeclFrag.resolveBinding();
		if (variableBinding != null) {
			SimpleName[] linkedNodes= LinkedNodeFinder.findByBinding(nodeToAssign.getRoot(), variableBinding);
			for (int i= 0; i < linkedNodes.length; i++) {
				addLinkedPosition(rewrite.track(linkedNodes[i]), false, KEY_NAME + index);
			}
		}
		setEndPosition(rewrite.track(selectionNode));

		return rewrite;
	}

	private ASTRewrite doAddAllFields(ASTRewrite rewrite) {
		for (int i= 0; rewrite != null && i < fNodesToAssign.size(); i++) {
			ASTNode nodeToAssign= fNodesToAssign.get(i);
			ITypeBinding typeBinding= ((SingleVariableDeclaration) nodeToAssign).resolveBinding().getType();
			rewrite= doAddField(rewrite, nodeToAssign, typeBinding, i);
		}
		return rewrite;
	}

	private VariableDeclarationFragment addFieldDeclaration(ASTRewrite rewrite, ASTNode newTypeDecl, int modifiers, Expression expression, ASTNode nodeToAssign, ITypeBinding typeBinding,
			int index) {
		if (fExistingFragment != null) {
			return fExistingFragment;
		}

		ChildListPropertyDescriptor property= ASTNodes.getBodyDeclarationsProperty(newTypeDecl);
		List<BodyDeclaration> decls= ASTNodes.getBodyDeclarations(newTypeDecl);
		AST ast= newTypeDecl.getAST();
		String[] varNames= suggestFieldNames(typeBinding, expression, modifiers, nodeToAssign);
		for (int i= 0; i < varNames.length; i++) {
			addLinkedPositionProposal(KEY_NAME + index, varNames[i]);
		}
		String varName= varNames[0];

		VariableDeclarationFragment newDeclFrag= ast.newVariableDeclarationFragment();
		newDeclFrag.setName(ast.newSimpleName(varName));

		FieldDeclaration newDecl= ast.newFieldDeclaration(newDeclFrag);

		Type type= evaluateType(ast, nodeToAssign, typeBinding, KEY_TYPE + index, TypeLocation.FIELD);
		newDecl.setType(type);
		newDecl.modifiers().addAll(ASTNodeFactory.newModifiers(ast, modifiers));

		ModifierCorrectionSubProcessor.installLinkedVisibilityProposals(getLinkedProposalModel(), rewrite, newDecl.modifiers(), false, ModifierCorrectionSubProcessor.KEY_MODIFIER + index);

		int insertIndex= findFieldInsertIndex(decls, nodeToAssign.getStartPosition()) + index;
		rewrite.getListRewrite(newTypeDecl, property).insertAt(newDecl, insertIndex, null);

		return newDeclFrag;
	}

	private Type evaluateType(AST ast, ASTNode nodeToAssign, ITypeBinding typeBinding, String groupID, TypeLocation location) {
		ITypeBinding[] proposals= ASTResolving.getRelaxingTypes(ast, typeBinding);
		for (int i= 0; i < proposals.length; i++) {
			addLinkedPositionProposal(groupID, proposals[i]);
		}
		ImportRewrite importRewrite= getImportRewrite();
		CompilationUnit cuNode= (CompilationUnit) nodeToAssign.getRoot();
		ImportRewriteContext context= new ContextSensitiveImportRewriteContext(cuNode, nodeToAssign.getStartPosition(), importRewrite);
		return importRewrite.addImport(typeBinding, ast, context, location);
	}

	private String[] suggestLocalVariableNames(ITypeBinding binding, Expression expression) {
		IJavaProject project= getCompilationUnit().getJavaProject();
		return StubUtility.getVariableNameSuggestions(NamingConventions.VK_LOCAL, project, binding, expression, getUsedVariableNames(fNodesToAssign.get(0)));
	}

	private String[] suggestFieldNames(ITypeBinding binding, Expression expression, int modifiers, ASTNode nodeToAssign) {
		IJavaProject project= getCompilationUnit().getJavaProject();
		int varKind= Modifier.isStatic(modifiers) ? NamingConventions.VK_STATIC_FIELD : NamingConventions.VK_INSTANCE_FIELD;
		return StubUtility.getVariableNameSuggestions(varKind, project, binding, expression, getUsedVariableNames(nodeToAssign));
	}

	private Collection<String> getUsedVariableNames(ASTNode nodeToAssign) {
		return Arrays.asList(ASTResolving.getUsedVariableNames(nodeToAssign));
	}

	private int findAssignmentInsertIndex(List<Statement> statements, ASTNode nodeToAssign) {

		HashSet<String> paramsBefore= new HashSet<>();
		List<SingleVariableDeclaration> params = ((MethodDeclaration) nodeToAssign.getParent()).parameters();
		for (int i = 0; i < params.size() && (params.get(i) != nodeToAssign); i++) {
			SingleVariableDeclaration decl= params.get(i);
			paramsBefore.add(decl.getName().getIdentifier());
		}

		int i= 0;
		for (i = 0; i < statements.size(); i++) {
			Statement curr= statements.get(i);
			switch (curr.getNodeType()) {
				case ASTNode.CONSTRUCTOR_INVOCATION:
				case ASTNode.SUPER_CONSTRUCTOR_INVOCATION:
					break;
				case ASTNode.EXPRESSION_STATEMENT:
					Expression expr= ((ExpressionStatement) curr).getExpression();
					if (expr instanceof Assignment) {
						Assignment assignment= (Assignment) expr;
						Expression rightHand = assignment.getRightHandSide();
						if (rightHand instanceof SimpleName && paramsBefore.contains(((SimpleName) rightHand).getIdentifier())) {
							IVariableBinding binding = Bindings.getAssignedVariable(assignment);
							if (binding == null || binding.isField()) {
								break;
							}
						}
					}
					return i;
				default:
					return i;

			}
		}
		return i;

	}

	private int findFieldInsertIndex(List<BodyDeclaration> decls, int currPos) {
		for (int i= decls.size() - 1; i >= 0; i--) {
			ASTNode curr= decls.get(i);
			if (curr instanceof FieldDeclaration && currPos > curr.getStartPosition() + curr.getLength()) {
				return i + 1;
			}
		}
		return 0;
	}

	/**
	 * Returns the variable kind.
	 * @return int
	 */
	public int getVariableKind() {
		return fVariableKind;
	}


}
