/*******************************************************************************
 * Copyright (c) 2016-2017 Red Hat, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * 	Contributors:
 * 		 Red Hat Inc. - initial API and implementation and/or initial documentation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal;

import static org.eclipse.core.resources.IResource.DEPTH_ONE;
import static org.eclipse.jdt.ls.core.internal.hover.JavaElementLabels.ALL_DEFAULT;
import static org.eclipse.jdt.ls.core.internal.hover.JavaElementLabels.M_APP_RETURNTYPE;
import static org.eclipse.jdt.ls.core.internal.hover.JavaElementLabels.ROOT_VARIABLE;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import org.eclipse.core.internal.utils.FileUtil;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.URIUtil;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.jdt.core.CompletionProposal;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IAnnotatable;
import org.eclipse.jdt.core.IAnnotation;
import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.ILocalVariable;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMemberValuePair;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IOpenable;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.ISourceReference;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeParameter;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.SourceRange;
import org.eclipse.jdt.core.WorkingCopyOwner;
import org.eclipse.jdt.core.compiler.CharOperation;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IAnnotationBinding;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMemberValuePairBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.PackageDeclaration;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.manipulation.SharedASTProviderCore;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.internal.codeassist.InternalCompletionProposal;
import org.eclipse.jdt.internal.codeassist.impl.Engine;
import org.eclipse.jdt.internal.compiler.lookup.Binding;
import org.eclipse.jdt.internal.compiler.lookup.MethodBinding;
import org.eclipse.jdt.internal.core.manipulation.search.IOccurrencesFinder.OccurrenceLocation;
import org.eclipse.jdt.internal.core.manipulation.search.OccurrencesFinder;
import org.eclipse.jdt.internal.core.util.Util;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.dom.IASTSharedValues;
import org.eclipse.jdt.internal.corext.template.java.SignatureUtil;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.JdtFlags;
import org.eclipse.jdt.launching.IVMInstall;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jdt.launching.environments.IExecutionEnvironment;
import org.eclipse.jdt.launching.environments.IExecutionEnvironmentsManager;
import org.eclipse.jdt.ls.core.internal.handlers.JsonRpcHelpers;
import org.eclipse.jdt.ls.core.internal.hover.JavaElementLabelComposer;
import org.eclipse.jdt.ls.core.internal.hover.JavaElementLabels;
import org.eclipse.jdt.ls.core.internal.javadoc.JavaElementLinks;
import org.eclipse.jdt.ls.core.internal.managers.ContentProviderManager;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

/**
 * General utilities for working with JDT APIs
 * @author Gorkem Ercan
 *
 */
public final class JDTUtils {

	public static final String PATH_SEPARATOR = "/";
	public static final String PERIOD = ".";
	public static final String SRC = "src";
	private static final String JDT_SCHEME = "jdt";
	//Code generators known to cause problems
	private static Set<String> SILENCED_CODEGENS = Collections.singleton("lombok");

	private JDTUtils() {
		//No public instantiation
	}

	/**
	 * Given the uri returns a {@link ICompilationUnit}.
	 * May return null if it can not associate the uri with a Java
	 * file.
	 *
	 * @param uriString
	 * @return compilation unit
	 */
	public static ICompilationUnit resolveCompilationUnit(String uriString) {
		return resolveCompilationUnit(toURI(uriString));
	}

	/**
	 * Given the uri returns a {@link ICompilationUnit}.
	 * May return null if it can not associate the uri with a Java
	 * file.
	 *
	 * @param uriString
	 * @return compilation unit
	 */
	public static ICompilationUnit resolveCompilationUnit(URI uri) {
		if (uri == null || JDT_SCHEME.equals(uri.getScheme()) || !uri.isAbsolute()){
			return null;
		}

		IFile resource = (IFile) findResource(uri, ResourcesPlugin.getWorkspace().getRoot()::findFilesForLocationURI);
		if(resource != null) {
			return resolveCompilationUnit(resource);
		} else {
			return getFakeCompilationUnit(uri, new NullProgressMonitor());
		}
	}

	public static ICompilationUnit resolveCompilationUnit(IFile resource) {
		if(resource != null){
			if(!ProjectUtils.isJavaProject(resource.getProject())){
				return null;
			}
			if (resource.getFileExtension() != null) {
				String name = resource.getName();
				if (org.eclipse.jdt.internal.core.util.Util.isJavaLikeFileName(name)) {
					return JavaCore.createCompilationUnitFrom(resource);
				}
			}
		}

		return null;
	}

	/**
	 * Given the uri string returns a {@link IPackageFragement}. May return null if
	 * it can not associate the uri with a package fragment.
	 *
	 * @param uriString
	 * @return package fragment
	 */
	public static IPackageFragment resolvePackage(String uriString) {
		return resolvePackage(toURI(uriString));
	}

	/**
	 * Given the uri returns a {@link IPackageFragment}. May return null if it can
	 * not associate the uri with a package fragment.
	 *
	 * @param uriString
	 * @return package fragment
	 */
	public static IPackageFragment resolvePackage(URI uri) {
		if (uri == null || JDT_SCHEME.equals(uri.getScheme()) || !uri.isAbsolute()) {
			return null;
		}

		IFolder resource = (IFolder) findResource(uri, ResourcesPlugin.getWorkspace().getRoot()::findContainersForLocationURI);
		if (resource != null) {
			if (!ProjectUtils.isJavaProject(resource.getProject())) {
				return null;
			}
			IJavaElement element = JavaCore.create(resource);
			if (element instanceof IPackageFragment) {
				return (IPackageFragment) element;
			}
		}
		return null;
	}

	public static ICompilationUnit getFakeCompilationUnit(String uri) {
		return getFakeCompilationUnit(toURI(uri), new NullProgressMonitor());
	}

	static ICompilationUnit getFakeCompilationUnit(URI uri, IProgressMonitor monitor) {
		if (uri == null || !"file".equals(uri.getScheme()) || !uri.getPath().endsWith(".java")) {
			return null;
		}
		java.nio.file.Path path = Paths.get(uri);
		//Only support existing standalone java files
		if (!java.nio.file.Files.isReadable(path)) {
			return null;
		}

		IProject project = JavaLanguageServerPlugin.getProjectsManager().getDefaultProject();
		if (project == null || !project.isAccessible()) {
			return null;
		}
		IJavaProject javaProject = JavaCore.create(project);

		String packageName = getPackageName(javaProject, uri);
		String fileName = path.getName(path.getNameCount() - 1).toString();
		String packagePath = packageName.replace(PERIOD, PATH_SEPARATOR);

		IPath filePath = new Path(SRC).append(packagePath).append(fileName);
		final IFile file = project.getFile(filePath);
		if (!file.isLinked()) {
			try {
				createFolders(file.getParent(), monitor);
				file.createLink(uri, IResource.REPLACE, monitor);
			} catch (CoreException e) {
				String errMsg = "Failed to create linked resource from " + uri + " to " + project.getName();
				JavaLanguageServerPlugin.logException(errMsg, e);
			}
		}
		if (file.isLinked()) {
			return (ICompilationUnit) JavaCore.create(file, javaProject);
		}
		return null;
	}

	public static void createFolders(IContainer folder, IProgressMonitor monitor) throws CoreException {
		if (!folder.exists() && folder instanceof IFolder) {
			IContainer parent = folder.getParent();
			createFolders(parent, monitor);
			folder.refreshLocal(IResource.DEPTH_ZERO, monitor);
			if (!folder.exists()) {
				((IFolder)folder).create(true, true, monitor);
			}
		}
	}

	public static String getPackageName(IJavaProject javaProject, URI uri) {
		try {
			File file = ResourceUtils.toFile(uri);
			//FIXME need to determine actual charset from file
			String content = Files.toString(file, Charsets.UTF_8);
			if (content.isEmpty() && javaProject != null && ProjectsManager.DEFAULT_PROJECT_NAME.equals(javaProject.getProject().getName())) {
				java.nio.file.Path path = Paths.get(uri);
				java.nio.file.Path parent = path;
				while (parent.getParent() != null && parent.getParent().getNameCount() > 0) {
					parent = parent.getParent();
					String name = parent.getName(parent.getNameCount() - 1).toString();
					if (SRC.equals(name)) {
						String pathStr = path.getParent().toString();
						if (pathStr.length() > parent.toString().length()) {
							pathStr = pathStr.substring(parent.toString().length() + 1);
							pathStr = pathStr.replace(PATH_SEPARATOR, PERIOD);
							return pathStr;
						}
					}
				}
			} else {
				return getPackageName(javaProject, content);
			}
		} catch (IOException e) {
			JavaLanguageServerPlugin.logException("Failed to read package name from "+uri, e);
		}
		return "";
	}

	public static String getPackageName(IJavaProject javaProject, String fileContent) {
		if (fileContent == null) {
			return "";
		}
		//TODO probably not the most efficient way to get the package name as this reads the whole file;
		char[] source = fileContent.toCharArray();
		ASTParser parser = ASTParser.newParser(IASTSharedValues.SHARED_AST_LEVEL);
		parser.setProject(javaProject);
		parser.setIgnoreMethodBodies(true);
		parser.setSource(source);
		CompilationUnit ast = (CompilationUnit) parser.createAST(null);
		PackageDeclaration pkg = ast.getPackage();
		return (pkg == null || pkg.getName() == null)?"":pkg.getName().getFullyQualifiedName();
	}

	/**
	 * Returns with the human readable name of the element. For types with type
	 * arguments, it is {@code Comparable<T>} instead of {@code Comparable}. First,
	 * this method tries to retrieve the
	 * {@link JavaElementLabels#getElementLabel(IJavaElement, long) label} of the
	 * element, then falls back to {@link IJavaElement#getElementName() element
	 * name}. Returns {@code null} if the argument does not have a name.
	 */
	public static String getName(IJavaElement element) {
		Assert.isNotNull(element, "element");
		String name = JavaElementLabels.getElementLabel(element, ALL_DEFAULT | M_APP_RETURNTYPE | ROOT_VARIABLE);
		return name == null ? element.getElementName() : name;
	}

	/**
	 * {@code true} if the element is deprecated. Otherwise, {@code false}.
	 */
	public static boolean isDeprecated(IJavaElement element) throws JavaModelException {
		Assert.isNotNull(element, "element");
		if (element instanceof ITypeRoot) {
			return Flags.isDeprecated(((ITypeRoot) element).findPrimaryType().getFlags());
		} else if (element instanceof IMember) {
			return Flags.isDeprecated(((IMember) element).getFlags());
		}
		return false;
	}

	/**
	 * Given the uri returns a {@link IClassFile}. May return null if it can not
	 * resolve the uri to a library.
	 *
	 * @see #toLocation(IClassFile, int, int)
	 * @param uri
	 *            with 'jdt' scheme
	 * @return class file
	 */
	public static IClassFile resolveClassFile(String uriString){
		return resolveClassFile(toURI(uriString));
	}

	/**
	 * Given the uri returns a {@link IClassFile}.
	 * May return null if it can not resolve the uri to a
	 * library.
	 *
	 * @see #toLocation(IClassFile, int, int)
	 * @param uri with 'jdt' scheme
	 * @return class file
	 */
	public static IClassFile resolveClassFile(URI uri){
		if (uri != null && JDT_SCHEME.equals(uri.getScheme()) && "contents".equals(uri.getAuthority())) {
			String handleId = uri.getQuery();
			IJavaElement element = JavaCore.create(handleId);
			IClassFile cf = (IClassFile) element.getAncestor(IJavaElement.CLASS_FILE);
			return cf;
		}
		return null;
	}

	/**
	 * Convenience method that combines {@link #resolveClassFile(String)} and
	 * {@link #resolveCompilationUnit(String)}.
	 *
	 * @param uri
	 * @return either a class file or compilation unit
	 */
	public static ITypeRoot resolveTypeRoot(String uriString) {
		return resolveTypeRoot(uriString, false, null);
	}

	/**
	 * Convenience method that combines {@link #resolveClassFile(String)} and
	 * {@link #resolveCompilationUnit(String)}.
	 *
	 * @param uri
	 * @param returnCompilationUnit
	 * @param monitor
	 * @return either a class file or compilation unit
	 */
	public static ITypeRoot resolveTypeRoot(String uriString, boolean returnCompilationUnit, IProgressMonitor monitor) {
		URI uri = toURI(uriString);
		if (uri == null) {
			return null;
		}
		if (JDT_SCHEME.equals(uri.getScheme())) {
			IClassFile classFile = resolveClassFile(uri);
			try {
				if (returnCompilationUnit && classFile != null && classFile.getSourceRange() == null) {
					ContentProviderManager contentProvider = JavaLanguageServerPlugin.getContentProviderManager();
					if (monitor == null) {
						monitor = new NullProgressMonitor();
					}
					String contents;
					try {
						contents = contentProvider.getSource(classFile, monitor);
					} catch (Exception e) {
						JavaLanguageServerPlugin.logException(e.getMessage(), e);
						return classFile;
					}
					if (contents != null && !contents.isBlank()) {
						return getWorkingCopy(classFile, contents, monitor);
					}
				}
			} catch (JavaModelException e) {
				JavaLanguageServerPlugin.logException(e.getMessage(), e);
			}
			return classFile;
		}
		return resolveCompilationUnit(uri);
	}

	public static void discardClassFileWorkingCopy(ITypeRoot unit) {
		try {
			if (getClassFile(unit) != null) {
				((ICompilationUnit) unit).discardWorkingCopy();
			}
		} catch (JavaModelException e) {
			// do nothing
		}
	}

	public static IClassFile getClassFile(ITypeRoot unit) throws JavaModelException {
		if (unit instanceof ICompilationUnit && unit.getResource() != null && !unit.getResource().exists()) {
			IType primaryType = unit.findPrimaryType();
			if (primaryType != null) {
				String fqn = primaryType.getFullyQualifiedName();
				IType type = unit.getJavaProject().findType(fqn);
				return type.getClassFile();
			}
		}
		return null;
	}

	private static final class ClassFileVisitor extends ASTVisitor {
		private final IJavaElement element;
		private final IProgressMonitor monitor;
		private final ASTNode[] nodes;

		private ClassFileVisitor(IJavaElement element, ASTNode[] nodes, IProgressMonitor monitor) {
			this.element = element;
			this.monitor = monitor;
			this.nodes = nodes;
		}

		@Override
		public boolean visit(SimpleName node) {
			if (element.getElementName().equals(node.getIdentifier())) {
				if (find(element, nodes, node)) {
					return false;
				}
			}
			if (monitor != null && monitor.isCanceled()) {
				return false;
			}
			return super.visit(node);
		}

		@Override
		public boolean visit(MethodInvocation node) {
			if (element.getElementName().equals(node.getName().getIdentifier())) {
				if (element instanceof IMethod) {
					IMethod method = (IMethod) element;
					String[] parameters = method.getParameterTypes();
					List astParameters = node.typeArguments();
					if (parameters.length == astParameters.size()) {
						int size = astParameters.size();
						String[] astParameterTypes = new String[size];
						Iterator iterator = astParameters.iterator();
						for (int i = 0; i < size; i++) {
							Type parameter = (Type) iterator.next();
							astParameterTypes[i] = getSignature(parameter);
						}
						if (equals(parameters, astParameterTypes)) {
							nodes[0] = node;
							return false;
						}
					}
				}

			}
			if (monitor != null && monitor.isCanceled()) {
				return false;
			}
			return super.visit(node);
		}

		@Override
		public boolean visit(MethodDeclaration node) {
			if (element.getElementName().equals(node.getName().getIdentifier())) {
				if (element instanceof IMethod) {
					IMethod method = (IMethod) element;
					String[] parameters = method.getParameterTypes();
					IMethodBinding binding = node.resolveBinding();
					if (binding != null) {
						ITypeBinding[] types = binding.getParameterTypes();
						if (types.length != parameters.length) {
							return false;
						}
						String[] astParameterTypes = new String[types.length];
						for (int i = 0; i < types.length; i++) {
							ITypeBinding type = types[i];
							String fullName = type.getQualifiedName();
							astParameterTypes[i] = Signature.createTypeSignature(fullName, true);
						}
						if (equals(parameters, astParameterTypes)) {
							nodes[0] = node;
							return false;
						}
					}
					List astParameters = node.parameters();
					if (parameters.length == astParameters.size()) {
						int size = astParameters.size();
						String[] astParameterTypes = new String[size];
						Iterator iterator = astParameters.iterator();
						for (int i = 0; i < size; i++) {
							SingleVariableDeclaration parameter = (SingleVariableDeclaration) iterator.next();
							String typeSig = getSignature(parameter.getType());
							int extraDimensions = parameter.getExtraDimensions();
							if (node.isVarargs() && i == size - 1) {
								extraDimensions++;
							}
							astParameterTypes[i] = Signature.createArraySignature(typeSig, extraDimensions);
						}
						if (equals(parameters, astParameterTypes)) {
							nodes[0] = node;
							return false;
						}
					}
				}
			}
			if (monitor != null && monitor.isCanceled()) {
				return false;
			}
			return super.visit(node);
		}

		private boolean equals(String[] parameters, String[] parameterTypes) {
			boolean isSame = true;
			for (int i = 0; i < parameters.length; i++) {
				if (!parameters[i].equals(parameterTypes[i])) {
					isSame = false;
					break;
				}
			}
			return isSame;
		}

		private String getSignature(Type type) {
			String signature = Util.getSignature(type);
			final String packageName = Signature.getSignatureQualifier(signature);
			final String typeName = Signature.getSignatureSimpleName(signature);
			final String fullName = "".equals(packageName) ? typeName : packageName + "." + typeName;
			signature = Signature.createTypeSignature(fullName, true);
			return signature;
		}

		private boolean find(IJavaElement element, final ASTNode[] nodes, SimpleName node) {
			ASTNode parent = node.getParent();
			boolean found = false;
			switch (parent.getNodeType()) {
				case ASTNode.ANNOTATION_TYPE_DECLARATION:
				case ASTNode.ANNOTATION_TYPE_MEMBER_DECLARATION:
					found = element.getElementType() == IJavaElement.ANNOTATION;
					break;
				case ASTNode.FIELD_DECLARATION:
				case ASTNode.ENUM_CONSTANT_DECLARATION:
				case ASTNode.ENUM_DECLARATION:
				case ASTNode.FIELD_ACCESS:
					found = element.getElementType() == IJavaElement.FIELD || element.getElementType() == IJavaElement.TYPE;
					break;
				case ASTNode.EXPRESSION_METHOD_REFERENCE:
					found = element.getElementType() == IJavaElement.METHOD;
					break;
				case ASTNode.TYPE_DECLARATION:
				case ASTNode.VARIABLE_DECLARATION_FRAGMENT:
				case ASTNode.VARIABLE_DECLARATION_STATEMENT:
				case ASTNode.VARIABLE_DECLARATION_EXPRESSION:
					found = element.getElementType() == IJavaElement.TYPE || element.getElementType() == IJavaElement.FIELD || element.getElementType() == IJavaElement.METHOD;
					break;
				default:
					break;
			}
			if (found) {
				nodes[0] = node;
			}
			return found;
		}
	}

	/**
	 * Enumeration for determining the location of a Java element. Either returns
	 * with the name range only, or the extended source range around the name of the
	 * element.
	 */
	public static enum LocationType {
		/**
		 * This is range encapsulating only the name of the Java element.
		 */
		NAME_RANGE {

			@Override
			ISourceRange getRange(IJavaElement element) throws JavaModelException {
				return getNameRange(element);
			}

		},
		/**
		 * The range enclosing this element not including leading/trailing whitespace
		 * but everything else like comments. This information is typically used to
		 * determine if the client's cursor is inside the element.
		 */
		FULL_RANGE {

			@Override
			ISourceRange getRange(IJavaElement element) throws JavaModelException {
				return getSourceRange(element);
			}

		};

		/* default */ abstract ISourceRange getRange(IJavaElement element) throws JavaModelException;

		/**
		 * Sugar for {@link JDTUtils#toLocation(IJavaElement, LocationType)}.
		 */
		public Location toLocation(IJavaElement element) throws JavaModelException {
			return JDTUtils.toLocation(element, this);
		}
	}

	/**
	 * Creates a location for a given java element.
	 * Element can be a {@link ICompilationUnit} or {@link IClassFile}
	 *
	 * @param element
	 * @return location or null
	 * @throws JavaModelException
	 */
	public static Location toLocation(IJavaElement element) throws JavaModelException {
		return toLocation(element, LocationType.NAME_RANGE);
	}

	/**
	 * Creates a location for a given java element. Unlike {@link #toLocation} this
	 * method can be called to return with a range that contains surrounding
	 * comments (method body), not just the name of the Java element. Element can be
	 * a {@link ICompilationUnit} or {@link IClassFile}
	 *
	 * @param element
	 * @param type the range type. The {@link LocationType#NAME_RANGE name} or {@link LocationType#FULL_RANGE full} range.
	 * @return location or null
	 * @throws JavaModelException
	 */
	public static Location toLocation(IJavaElement element, LocationType type) throws JavaModelException {
		ICompilationUnit unit = (ICompilationUnit) element.getAncestor(IJavaElement.COMPILATION_UNIT);
		IClassFile cf = (IClassFile) element.getAncestor(IJavaElement.CLASS_FILE);
		if (unit == null && cf == null) {
			return null;
		}
		if (element instanceof ISourceReference) {
			ISourceRange nameRange = type.getRange(element);
			if (SourceRange.isAvailable(nameRange)) {
				if (cf == null) {
					return toLocation(unit, nameRange.getOffset(), nameRange.getLength());
				} else {
					return toLocation(cf, nameRange.getOffset(), nameRange.getLength());
				}
			}
		}
		return null;
	}

	public static ISourceRange getNameRange(IJavaElement element) throws JavaModelException {
		ISourceRange nameRange = null;
		if (element instanceof IMember) {
			IMember member = (IMember) element;
			nameRange = member.getNameRange();
			if ((!SourceRange.isAvailable(nameRange))) {
				nameRange = member.getSourceRange();
			}
		} else if (element instanceof ITypeParameter || element instanceof ILocalVariable) {
			nameRange = ((ISourceReference) element).getNameRange();
		} else if (element instanceof ISourceReference) {
			nameRange = ((ISourceReference) element).getSourceRange();
		}
		if (!SourceRange.isAvailable(nameRange) && element.getParent() != null) {
			nameRange = getNameRange(element.getParent());
		}
		return nameRange;
	}

	private static ISourceRange getSourceRange(IJavaElement element) throws JavaModelException {
		ISourceRange sourceRange = null;
		if (element instanceof IMember) {
			IMember member = (IMember) element;
			sourceRange = member.getSourceRange();
		} else if (element instanceof ITypeParameter || element instanceof ILocalVariable) {
			sourceRange = ((ISourceReference) element).getSourceRange();
		} else if (element instanceof ISourceReference) {
			sourceRange = ((ISourceReference) element).getSourceRange();
		}
		if (!SourceRange.isAvailable(sourceRange) && element.getParent() != null) {
			sourceRange = getSourceRange(element.getParent());
		}
		return sourceRange;
	}

	/**
	 * Creates location to the given offset and length for the compilation unit
	 *
	 * @param unit
	 * @param offset
	 * @param length
	 * @return location or null
	 * @throws JavaModelException
	 */
	public static Location toLocation(ICompilationUnit unit, int offset, int length) throws JavaModelException {
		return new Location(ResourceUtils.toClientUri(toURI(unit)), toRange(unit, offset, length));
	}

	/**
	 * Creates a default location for the class file.
	 *
	 * @param classFile
	 * @return location
	 * @throws JavaModelException
	 */
	public static Location toLocation(IClassFile classFile) throws JavaModelException{
		return toLocation(classFile, 0, 0);
	}

	/**
	 * Creates a default location for the uri.
	 *
	 * @param classFile
	 * @return location
	 * @throws JavaModelException
	 */
	public static Location toLocation(String uri) {
		return new Location(ResourceUtils.toClientUri(uri), newRange());
	}

	/**
	 * Creates location to the given offset and length for the class file.
	 *
	 * @param unit
	 * @param offset
	 * @param length
	 * @return location
	 * @throws JavaModelException
	 */
	public static Location toLocation(IClassFile classFile, int offset, int length) throws JavaModelException {
		String uriString = toUri(classFile);
		if (uriString != null) {
			Range range = toRange(classFile, offset, length);
			return new Location(uriString, range);
		}
		return null;
	}

	public static String toUri(IClassFile classFile) {
		if (JavaLanguageServerPlugin.getPreferencesManager() != null && !JavaLanguageServerPlugin.getPreferencesManager().isClientSupportsClassFileContent()) {
			return null;
		}

		String packageName = classFile.getParent().getElementName();
		String jarName = classFile.getParent().getParent().getElementName();
		String uriString = null;
		try {
			uriString = new URI(JDT_SCHEME, "contents", PATH_SEPARATOR + jarName + PATH_SEPARATOR + packageName + PATH_SEPARATOR + classFile.getElementName(), classFile.getHandleIdentifier(), null).toASCIIString();
		} catch (URISyntaxException e) {
			JavaLanguageServerPlugin.logException("Error generating URI for class ", e);
		}
		return uriString;
	}

	public static String replaceUriFragment(String uriString, String fragment) {
		if (uriString != null) {
			URI uri = toURI(uriString);
			if (uri != null && Objects.equals(JDT_SCHEME, uri.getScheme())) {
				try {
					return new URI(JDT_SCHEME, uri.getAuthority(), uri.getPath(), uri.getQuery(), fragment).toASCIIString();
				} catch (URISyntaxException e) {
					// do nothing
				}
			}
		}

		return uriString;
	}

	public static String toUri(ITypeRoot typeRoot) {
		if (typeRoot instanceof ICompilationUnit) {
			return toURI((ICompilationUnit) typeRoot);
		}
		if (typeRoot instanceof IClassFile) {
			return toUri((IClassFile) typeRoot);
		}
		return null;
	}

	/**
	 * Creates a range for the given offset and length for an {@link IOpenable}
	 *
	 * @param openable
	 * @param offset
	 * @param length
	 * @return
	 * @throws JavaModelException
	 */
	public static Range toRange(IOpenable openable, int offset, int length) throws JavaModelException{
		Range range = newRange();
		if (offset > 0 || length > 0) {
			int[] loc = null;
			int[] endLoc = null;
			IBuffer buffer = openable.getBuffer();
			if (buffer != null) {
				loc = JsonRpcHelpers.toLine(buffer, offset);
				endLoc = JsonRpcHelpers.toLine(buffer, offset + length);
			}
			if (loc == null) {
				loc = new int[2];
			}
			if (endLoc == null) {
				endLoc = new int[2];
			}
			setPosition(range.getStart(), loc);
			setPosition(range.getEnd(), endLoc);
		}
		return range;
	}

	/**
	 * Creates a new {@link Range} with its start and end {@link Position}s set to line=0, character=0
	 *
	 * @return a new {@link Range};
	 */
	public static Range newRange() {
		return new Range(new Position(), new Position());
	}

	/**
	 * Creates a new {@link Range} with its start and end {@link Position}s set to
	 * the given line
	 *
	 * @return a new {@link Range};
	 */
	public static Range newLineRange(int line, int start, int end) {
		return new Range(new Position(line, start), new Position(line, end));
	}

	private static void setPosition(Position position, int[] coords) {
		assert coords.length == 2;
		position.setLine(coords[0]);
		position.setCharacter(coords[1]);
	}

	/**
	 * Returns uri for a compilation unit
	 * @param cu
	 * @return
	 */
	@Deprecated
	public static String getFileURI(ICompilationUnit cu) {
		return toURI(cu);
	}

	/**
	 * Returns uri for a compilation unit
	 *
	 * @param cu
	 * @return
	 */
	public static String toURI(ICompilationUnit cu) {
		return getFileURI(cu.getResource());
	}

	/**
	 * Returns uri for a resource
	 * @param resource
	 * @return
	 */
	public static String getFileURI(IResource resource) {
		URI uri = resource.getRawLocationURI();
		return ResourceUtils.fixURI(uri == null ? resource.getLocationURI() : uri);
	}

	public static IJavaElement findElementAtSelection(ITypeRoot unit, int line, int column, PreferenceManager preferenceManager, IProgressMonitor monitor) throws JavaModelException {
		IJavaElement[] elements = findElementsAtSelection(unit, line, column, preferenceManager, monitor);
		if (monitor.isCanceled()) {
			return null;
		}
		if (elements != null && elements.length == 1) {
			return elements[0];
		}
		return null;
	}

	public static IJavaElement[] findElementsAtSelection(ITypeRoot unit, int line, int column, PreferenceManager preferenceManager, IProgressMonitor monitor) throws JavaModelException {
		if (unit == null || monitor.isCanceled()) {
			return null;
		}
		int offset = JsonRpcHelpers.toOffset(unit.getBuffer(), line, column);
		if (monitor != null && monitor.isCanceled()) {
			return null;
		}
		if (offset > -1) {
			return unit.codeSelect(offset, 0);
		}
		return null;
	}

	public static boolean isSameParameters(IMethod method1, IMethod method2) {
		if (method1 == null || method2 == null) {
			return false;
		}
		String[] params1 = method1.getParameterTypes();
		String[] params2 = method2.getParameterTypes();
		if (params2.length == params1.length) {
			for (int i = 0; i < params2.length; i++) {
				String t1 = Signature.getSimpleName(Signature.toString(params2[i]));
				String t2 = Signature.getSimpleName(Signature.toString(params1[i]));
				if (!t1.equals(t2)) {
					return false;
				}
			}
			return true;
		}
		return false;
	}

	public static boolean isFolder(String uriString) {
		IFile fakeFile = findFile(uriString); // This may return IFile even when uriString really describes a IContainer
		IContainer parent = fakeFile == null ? null : fakeFile.getParent();
		if (parent == null) {
			return false;
		}
		if (!parent.isSynchronized(DEPTH_ONE)) {
			try {
				parent.refreshLocal(DEPTH_ONE, null);
			} catch (CoreException e) {
				// Ignore
			}
		}
		return (parent.findMember(fakeFile.getName()) instanceof IFolder);
	}

	public static IFile findFile(String uriString) {
		return (IFile) findResource(toURI(uriString), ResourcesPlugin.getWorkspace().getRoot()::findFilesForLocationURI);
	}

	public static ISchedulingRule getRule(String uri) {
		IResource resource = JDTUtils.findFile(uri);
		if (resource != null) {
			return ResourcesPlugin.getWorkspace().getRuleFactory().createRule(resource);
		}
		return null;
	}

	public static IContainer findFolder(String uriString) {
		return (IContainer) findResource(toURI(uriString), ResourcesPlugin.getWorkspace().getRoot()::findContainersForLocationURI);
	}

	public static IResource findResource(URI uri, Function<URI, IResource[]> resourceFinder) {
		if (uri == null || !"file".equals(uri.getScheme())) {
			return null;
		}
		IResource[] resources = resourceFinder.apply(uri);
		if (resources.length == 0) {
			//On Mac, Linked resources are referenced via the "real" URI, i.e file://USERS/username/...
			//instead of file://Users/username/..., so we check against that real URI.
			URI realUri = FileUtil.realURI(uri);
			if (!uri.equals(realUri)) {
				uri = realUri;
				resources = resourceFinder.apply(uri);
			}
		}
		if (resources.length == 0 && Platform.OS_WIN32.equals(Platform.getOS()) && uri.toString().startsWith(ResourceUtils.FILE_UNC_PREFIX)) {
			String uriString = uri.toString();
			int index = uriString.indexOf(PATH_SEPARATOR, ResourceUtils.FILE_UNC_PREFIX.length());
			if (index > 0) {
				String server = uriString.substring(ResourceUtils.FILE_UNC_PREFIX.length(), index);
				uriString = uriString.replace(server, server.toUpperCase());
				try {
					uri = new URI(uriString);
				} catch (URISyntaxException e) {
					JavaLanguageServerPlugin.logException(e.getMessage(), e);
				}
				resources = resourceFinder.apply(uri);
			}
		}
		switch(resources.length) {
		case 0:
			return null;
		case 1:
			return resources[0];
		default://several candidates if a linked resource was created before the real project was configured
				IResource resource = null;
				for (IResource f : resources) {
				//delete linked resource
				if (JavaLanguageServerPlugin.getProjectsManager().getDefaultProject().equals(f.getProject())) {
					try {
						f.delete(true, null);
					} catch (CoreException e) {
							JavaLanguageServerPlugin.logException(e.getMessage(), e);
					}
				}
				//find closest project containing that file, in case of nested projects
					if (resource == null || f.getProjectRelativePath().segmentCount() < resource.getProjectRelativePath().segmentCount()) {
						resource = f;
				}
			}
				return resource;
		}
	}

	public static URI toURI(String uriString) {
		if (uriString == null || uriString.isEmpty()) {
			return null;
		}
		try {
			URI uri = new URI(uriString);
			if (Platform.OS_WIN32.equals(Platform.getOS()) && URIUtil.isFileURI(uri)) {
				uri = URIUtil.toFile(uri).toURI();
			}
			return uri;
		} catch (URISyntaxException e) {
			JavaLanguageServerPlugin.logException("Failed to resolve "+uriString, e);
			return null;
		}
	}

	public static boolean isHiddenGeneratedElement(IJavaElement element) {
		// generated elements are annotated with @Generated and they need to be filtered out
		if (element instanceof IAnnotatable) {
			try {
				IAnnotation[] annotations = ((IAnnotatable) element).getAnnotations();
				if (annotations.length != 0) {
					for (IAnnotation annotation : annotations) {
						if (isSilencedGeneratedAnnotation(annotation)) {
							return true;
						}
					}
				}
			} catch (JavaModelException e) {
				//ignore
			}
		}
		return false;
	}

	private static boolean isSilencedGeneratedAnnotation(IAnnotation annotation) throws JavaModelException {
		if ("javax.annotation.Generated".equals(annotation.getElementName()) || "javax.annotation.processing.Generated".equals(annotation.getElementName())) {
			IMemberValuePair[] memberValuePairs = annotation.getMemberValuePairs();
			for (IMemberValuePair m : memberValuePairs) {
				if ("value".equals(m.getMemberName())
						&& IMemberValuePair.K_STRING == m.getValueKind()) {
					if (m.getValue() instanceof String) {
						return SILENCED_CODEGENS.contains(m.getValue());
					} else if (m.getValue() instanceof Object[]) {
						for (Object val : (Object[])m.getValue()) {
							if(SILENCED_CODEGENS.contains(val)) {
								return true;
							}
						}
					}
				}
			}
		}
		return false;
	}

	public static IJavaSearchScope createSearchScope(IJavaProject project, PreferenceManager preferenceManager) {
		IJavaProject[] elements;
		if (project == null) {//workspace search
			elements = ProjectUtils.getJavaProjects();
		} else {
			elements = new IJavaProject[] { project };
		}
		int scope = IJavaSearchScope.SOURCES;
		if (preferenceManager != null && preferenceManager.isClientSupportsClassFileContent()) {
			scope |= IJavaSearchScope.APPLICATION_LIBRARIES | IJavaSearchScope.SYSTEM_LIBRARIES;
		}
		return SearchEngine.createJavaSearchScope(elements, scope);
	}

	public static boolean isOnClassPath(ICompilationUnit unit) {
		if (unit != null && unit.getJavaProject() != null && !unit.getJavaProject().getProject().equals(JavaLanguageServerPlugin.getProjectsManager().getDefaultProject())) {
			return unit.getJavaProject().isOnClasspath(unit);
		}
		return false;
	}

	public static boolean isDefaultProject(ICompilationUnit unit) {
		return unit != null && unit.getResource() != null && unit.getResource().getProject().equals(JavaLanguageServerPlugin.getProjectsManager().getDefaultProject());
	}

	public static void setCompatibleVMs(String id) {
		// update all environments compatible to use the test JRE
		IExecutionEnvironmentsManager manager = JavaRuntime.getExecutionEnvironmentsManager();
		IExecutionEnvironment[] environments = manager.getExecutionEnvironments();
		for (IExecutionEnvironment environment : environments) {
			IVMInstall[] compatibleVMs = environment.getCompatibleVMs();
			for (IVMInstall compatibleVM : compatibleVMs) {
				if (id.equals(compatibleVM.getVMInstallType().getId()) && compatibleVM.getVMInstallType().findVMInstall(compatibleVM.getId()) != null && !compatibleVM.equals(environment.getDefaultVM())
				// Fugly way to ensure the lowest VM version is set:
						&& (environment.getDefaultVM() == null || compatibleVM.getId().compareTo(environment.getDefaultVM().getId()) < 0)) {
					environment.setDefaultVM(compatibleVM);
				}
			}
		}
	}

	public static IResource getFileOrFolder(String uriString) {
		IFile file = findFile(uriString); // This may return IFile even when uriString really describes a IContainer
		IContainer parent = file == null ? null : file.getParent();
		if (parent == null) {
			return file;
		}
		try {
			parent.refreshLocal(DEPTH_ONE, null);
		} catch (CoreException e) {
			// Ignore
		}
		if (parent.findMember(file.getName()) instanceof IFolder) {
			return findFolder(uriString);
		}
		return file;
	}

	/* adapted from org.eclipse.jdt.internal.ui.text.java.hover.JavadocHover */

	/**
	 * Returns the constant value for the given field.
	 *
	 * @param field
	 *            the field
	 * @param typeRoot
	 *            the editor input element
	 * @param region
	 *            the hover region in the editor
	 * @return the constant value for the given field or <code>null</code> if none
	 *
	 */
	public static String getConstantValue(IField field, ITypeRoot typeRoot, IRegion region) {
		if (field == null || !isStaticFinal(field)) {
			return null;
		}

		Object constantValue;
		ASTNode node = getHoveredASTNode(typeRoot, region);
		if (node != null) {
			constantValue = getVariableBindingConstValue(node, field);
		} else {
			constantValue = computeFieldConstantFromTypeAST(field, null);
		}
		if (constantValue == null) {
			return null;
		}

		if (constantValue instanceof String) {
			return ASTNodes.getEscapedStringLiteral((String) constantValue);
		} else if (constantValue instanceof Character) {
			return '\'' + constantValue.toString() + '\'';
		} else {
			return constantValue.toString(); // getHexConstantValue(constantValue);
		}
	}

	/**
	 * Tells whether the given field is static final.
	 *
	 * @param field
	 *            the member to test
	 * @return <code>true</code> if static final
	 *
	 */
	public static boolean isStaticFinal(IField field) {
		try {
			return JdtFlags.isFinal(field) && JdtFlags.isStatic(field);
		} catch (JavaModelException e) {
			JavaLanguageServerPlugin.log(e);
			return false;
		}
	}

	private static ASTNode getHoveredASTNode(ITypeRoot typeRoot, IRegion region) {
		if (typeRoot == null || region == null) {
			return null;
		}

		CompilationUnit unit = SharedASTProviderCore.getAST(typeRoot, SharedASTProviderCore.WAIT_ACTIVE_ONLY, null);
		if (unit == null) {
			return null;
		}

		return NodeFinder.perform(unit, region.getOffset(), region.getLength());
	}

	private static Object getVariableBindingConstValue(ASTNode node, IField field) {
		if (node != null && node.getNodeType() == ASTNode.SIMPLE_NAME) {
			IBinding binding = ((SimpleName) node).resolveBinding();
			if (binding != null && binding.getKind() == IBinding.VARIABLE) {
				IVariableBinding variableBinding = (IVariableBinding) binding;
				if (field.equals(variableBinding.getJavaElement())) {
					return variableBinding.getConstantValue();
				}
			}
		}
		return null;
	}

	/**
	 * Retrieve a constant initializer value of a field by (AST) parsing field's
	 * type.
	 *
	 * @param constantField
	 *            the constant field
	 * @param monitor
	 *            the progress monitor or null
	 * @return the constant value of the field, or <code>null</code> if it could not
	 *         be computed (or if the progress was cancelled).
	 *
	 */
	public static Object computeFieldConstantFromTypeAST(IField constantField, IProgressMonitor monitor) {
		if (monitor != null && monitor.isCanceled()) {
			return null;
		}

		CompilationUnit ast = SharedASTProviderCore.getAST(constantField.getTypeRoot(), SharedASTProviderCore.WAIT_NO, monitor);
		if (ast != null) {
			try {
				if (constantField.isEnumConstant()) {
					return null;
				}

				VariableDeclarationFragment fieldDecl = org.eclipse.jdt.ls.core.internal.corext.refactoring.structure.ASTNodeSearchUtil.getFieldDeclarationFragmentNode(constantField, ast);
				if (fieldDecl == null) {
					return null;
				}
				Expression initializer = fieldDecl.getInitializer();
				if (initializer == null) {
					return null;
				}
				return initializer.resolveConstantExpressionValue();
			} catch (JavaModelException e) {
				// ignore the exception and try the next method
			}
		}

		if (monitor != null && monitor.isCanceled()) {
			return null;
		}

		ASTParser p = ASTParser.newParser(IASTSharedValues.SHARED_AST_LEVEL);
		p.setProject(constantField.getJavaProject());
		IBinding[] createBindings;
		try {
			createBindings = p.createBindings(new IJavaElement[] { constantField }, monitor);
		} catch (OperationCanceledException e) {
			return null;
		}

		IVariableBinding variableBinding = (IVariableBinding) createBindings[0];
		if (variableBinding != null) {
			return variableBinding.getConstantValue();
		}

		return null;
	}

	/**
	 * Returns the default value of the given annotation type method.
	 *
	 * @param method
	 *            the method
	 * @param typeRoot
	 *            the editor input element
	 * @param hoverRegion
	 *            the hover region in the editor
	 * @return the default value of the given annotation type method or
	 *         <code>null</code> if none
	 * @throws JavaModelException
	 *             if an exception occurs while accessing its default value
	 */
	public static String getAnnotationMemberDefaultValue(IMethod method, ITypeRoot typeRoot, IRegion hoverRegion) throws JavaModelException {
		IMemberValuePair memberValuePair = method.getDefaultValue();
		if (memberValuePair == null) {
			return null;
		}

		Object defaultValue = memberValuePair.getValue();
		boolean isEmptyArray = defaultValue instanceof Object[] && ((Object[]) defaultValue).length == 0;
		int valueKind = memberValuePair.getValueKind();

		if (valueKind == IMemberValuePair.K_UNKNOWN && !isEmptyArray) {
			IBinding binding = getHoveredNodeBinding(method, typeRoot, hoverRegion);
			if (binding instanceof IMethodBinding) {
				Object value = ((IMethodBinding) binding).getDefaultValue();
				StringBuilder buf = new StringBuilder();
				try {
					addValue(buf, value, false);
				} catch (URISyntaxException e) {
					// should not happen as links are not added
				}
				return buf.toString();
			}

		} else if (defaultValue != null) {
			IAnnotation parentAnnotation = (IAnnotation) method.getAncestor(IJavaElement.ANNOTATION);
			StringBuilder buf = new StringBuilder();
			new JavaElementLabelComposer(buf).appendAnnotationValue(parentAnnotation, defaultValue, valueKind, JavaElementLabels.LABEL_FLAGS);
			return buf.toString();
		}

		return null;
	}

	private static void addValue(StringBuilder buf, Object value, boolean addLinks) throws URISyntaxException {
		// Note: To be bug-compatible with Javadoc from Java 5/6/7, we currently don't escape HTML tags in String-valued annotations.
		if (value instanceof ITypeBinding) {
			ITypeBinding typeBinding = (ITypeBinding) value;
			IJavaElement type = typeBinding.getJavaElement();
			if (type == null || !addLinks) {
				buf.append(typeBinding.getName());
			} else {
				String uri = JavaElementLinks.createURI(JavaElementLinks.JAVADOC_SCHEME, type);
				String name = type.getElementName();
				addLink(buf, uri, name);
			}
			buf.append(".class"); //$NON-NLS-1$

		} else if (value instanceof IVariableBinding) { // only enum constants
			IVariableBinding variableBinding = (IVariableBinding) value;
			IJavaElement variable = variableBinding.getJavaElement();
			if (variable == null || !addLinks) {
				buf.append(variableBinding.getName());
			} else {
				String uri = JavaElementLinks.createURI(JavaElementLinks.JAVADOC_SCHEME, variable);
				String name = variable.getElementName();
				addLink(buf, uri, name);
			}

		} else if (value instanceof IAnnotationBinding) {
			IAnnotationBinding annotationBinding = (IAnnotationBinding) value;
			addAnnotation(buf, annotationBinding, addLinks);

		} else if (value instanceof String) {
			buf.append(ASTNodes.getEscapedStringLiteral((String) value));

		} else if (value instanceof Character) {
			buf.append(ASTNodes.getEscapedCharacterLiteral(((Character) value).charValue()));

		} else if (value instanceof Object[]) {
			Object[] values = (Object[]) value;
			buf.append('{');
			for (int i = 0; i < values.length; i++) {
				if (i > 0) {
					buf.append(JavaElementLabels.COMMA_STRING);
				}
				addValue(buf, values[i], addLinks);
			}
			buf.append('}');

		} else { // primitive types (except char) or null
			buf.append(String.valueOf(value));
		}
	}

	private static StringBuilder addLink(StringBuilder buf, String uri, String label) {
		return buf.append(JavaElementLinks.createLink(uri, label));
	}

	private static void addAnnotation(StringBuilder buf, IAnnotationBinding annotation, boolean addLinks) throws URISyntaxException {
		IJavaElement javaElement = annotation.getAnnotationType().getJavaElement();
		buf.append('@');
		if (javaElement == null || !addLinks) {
			buf.append(annotation.getName());
		} else {
			String uri = JavaElementLinks.createURI(JavaElementLinks.JAVADOC_SCHEME, javaElement);
			addLink(buf, uri, annotation.getName());
		}

		IMemberValuePairBinding[] mvPairs = annotation.getDeclaredMemberValuePairs();
		if (mvPairs.length > 0) {
			buf.append('(');
			for (int j = 0; j < mvPairs.length; j++) {
				if (j > 0) {
					buf.append(JavaElementLabels.COMMA_STRING);
				}
				IMemberValuePairBinding mvPair = mvPairs[j];
				if (addLinks) {
					String memberURI = JavaElementLinks.createURI(JavaElementLinks.JAVADOC_SCHEME, mvPair.getMethodBinding().getJavaElement());
					addLink(buf, memberURI, mvPair.getName());
				} else {
					buf.append(mvPair.getName());
				}
				buf.append('=');
				addValue(buf, mvPair.getValue(), addLinks);
			}
			buf.append(')');
		}
	}

	private static IBinding getHoveredNodeBinding(IJavaElement element, ITypeRoot typeRoot, IRegion region) {
		if (typeRoot == null || region == null) {
			return null;
		}
		IBinding binding;
		ASTNode node = getHoveredASTNode(typeRoot, region);
		if (node == null) {
			ASTParser p = ASTParser.newParser(IASTSharedValues.SHARED_AST_LEVEL);
			p.setProject(element.getJavaProject());
			p.setBindingsRecovery(true);
			try {
				binding = p.createBindings(new IJavaElement[] { element }, null)[0];
			} catch (OperationCanceledException e) {
				return null;
			}
		} else {
			binding = resolveBinding(node);
		}
		return binding;
	}

	private static IBinding resolveBinding(ASTNode node) {
		if (node instanceof SimpleName) {
			SimpleName simpleName = (SimpleName) node;
			// workaround for https://bugs.eclipse.org/62605 (constructor name resolves to type, not method)
			ASTNode normalized = ASTNodes.getNormalizedNode(simpleName);
			if (normalized.getLocationInParent() == ClassInstanceCreation.TYPE_PROPERTY) {
				ClassInstanceCreation cic = (ClassInstanceCreation) normalized.getParent();
				IMethodBinding constructorBinding = cic.resolveConstructorBinding();
				if (constructorBinding == null) {
					return null;
				}
				ITypeBinding declaringClass = constructorBinding.getDeclaringClass();
				if (!declaringClass.isAnonymous()) {
					return constructorBinding;
				}
				ITypeBinding superTypeDeclaration = declaringClass.getSuperclass().getTypeDeclaration();
				return resolveSuperclassConstructor(superTypeDeclaration, constructorBinding);
			}
			return simpleName.resolveBinding();

		} else if (node instanceof SuperConstructorInvocation) {
			return ((SuperConstructorInvocation) node).resolveConstructorBinding();
		} else if (node instanceof ConstructorInvocation) {
			return ((ConstructorInvocation) node).resolveConstructorBinding();
		} else if (node instanceof LambdaExpression) {
			return ((LambdaExpression) node).resolveMethodBinding();
		} else {
			return null;
		}
	}

	private static IBinding resolveSuperclassConstructor(ITypeBinding superClassDeclaration, IMethodBinding constructor) {
		IMethodBinding[] methods = superClassDeclaration.getDeclaredMethods();
		for (int i = 0; i < methods.length; i++) {
			IMethodBinding method = methods[i];
			if (method.isConstructor() && constructor.isSubsignature(method)) {
				return method;
			}
		}
		return null;
	}

	/* adapted from org.eclipse.jdt.internal.ui.text.java.FieldProposalInfo.resolveMember */
	/**
	 * Resolves the field described by the receiver and returns it if found. Returns
	 * <code>null</code> if no corresponding member can be found.
	 *
	 * @param proposal
	 *            - completion proposal
	 * @param javaProject
	 *            - Java project
	 *
	 * @return the resolved field or <code>null</code> if none is found
	 * @throws JavaModelException
	 *             if accessing the java model fails
	 */
	public static IField resolveField(CompletionProposal proposal, IJavaProject javaProject) throws JavaModelException {
		char[] declarationSignature = proposal.getDeclarationSignature();
		// for synthetic fields on arrays, declaration signatures may be null
		// TODO remove when https://bugs.eclipse.org/bugs/show_bug.cgi?id=84690 gets fixed
		if (declarationSignature == null) {
			return null;
		}
		String typeName = SignatureUtil.stripSignatureToFQN(String.valueOf(declarationSignature));
		IType type = javaProject.findType(typeName);
		if (type != null) {
			String name = String.valueOf(proposal.getName());
			IField field = type.getField(name);
			if (field.exists()) {
				return field;
			}
		}

		return null;
	}

	/**
	 * Resolves the method described by the receiver and returns it if found.
	 * Returns <code>null</code> if no corresponding member can be found.
	 *
	 * @param proposal
	 *            - completion proposal
	 * @param javaProject
	 *            - Java project
	 *
	 * @return the resolved method or <code>null</code> if none is found
	 * @throws JavaModelException
	 *             if accessing the java model fails
	 */

	public static IMethod resolveMethod(CompletionProposal proposal, IJavaProject javaProject) throws JavaModelException {
		char[] declarationSignature = proposal.getDeclarationSignature();
		String typeName = SignatureUtil.stripSignatureToFQN(String.valueOf(declarationSignature));
		IType type = javaProject.findType(typeName);
		if (type != null) {
			String name = String.valueOf(proposal.getName());
			if (proposal.getKind() == CompletionProposal.ANNOTATION_ATTRIBUTE_REF) {
				IMethod method = type.getMethod(name, CharOperation.NO_STRINGS);
				if (method.exists()) {
					return method;
				} else {
					return null;
				}
			}
			char[] signature = proposal.getSignature();
			if (proposal instanceof InternalCompletionProposal) {
				Binding binding = ((InternalCompletionProposal) proposal).getBinding();
				if (binding instanceof MethodBinding) {
					MethodBinding methodBinding = (MethodBinding) binding;
					MethodBinding original = methodBinding.original();
					if (original != binding) {
						signature = Engine.getSignature(original);
					}
				}
			}
			String[] parameters = Signature.getParameterTypes(String.valueOf(SignatureUtil.fix83600(signature)));
			for (int i = 0; i < parameters.length; i++) {
				parameters[i] = SignatureUtil.getLowerBound(parameters[i]);
			}
			boolean isConstructor = proposal.isConstructor();

			return JavaModelUtil.findMethod(name, parameters, isConstructor, type);
		}

		return null;
	}

	public static List<Location> searchDecompiledSources(IJavaElement element, IClassFile classFile, boolean ignoreMethodBody, boolean declaration, IProgressMonitor monitor) throws JavaModelException {
		PreferenceManager preferencesManager = JavaLanguageServerPlugin.getPreferencesManager();
		if (preferencesManager == null || !preferencesManager.isClientSupportsClassFileContent() || !preferencesManager.getPreferences().isIncludeDecompiledSources()) {
			return Collections.emptyList();
		}
		ContentProviderManager contentProvider = JavaLanguageServerPlugin.getContentProviderManager();
		String contents;
		try {
			contents = contentProvider.getSource(classFile, new NullProgressMonitor());
		} catch (Exception e) {
			JavaLanguageServerPlugin.logException(e.getMessage(), e);
			return Collections.emptyList();
		}
		if (monitor != null && monitor.isCanceled()) {
			return Collections.emptyList();
		}
		List<Location> locations = new ArrayList<>();
		if (contents != null && !contents.isBlank()) {
			ICompilationUnit workingCopy = workingCopy = getWorkingCopy(classFile, contents, monitor);
			try {
				final ASTParser parser = ASTParser.newParser(IASTSharedValues.SHARED_AST_LEVEL);
				parser.setResolveBindings(true);
				parser.setKind(ASTParser.K_COMPILATION_UNIT);
				parser.setStatementsRecovery(false);
				parser.setBindingsRecovery(false);
				parser.setSource(workingCopy);
				parser.setIgnoreMethodBodies(ignoreMethodBody);
				CompilationUnit unit = (CompilationUnit) parser.createAST(monitor);
				final ASTNode[] nodes = new ASTNode[1];
				if (monitor != null && monitor.isCanceled()) {
					return Collections.emptyList();
				}
				unit.accept(new ClassFileVisitor(element, nodes, monitor));
				ASTNode node = nodes[0];
				if (monitor != null && monitor.isCanceled()) {
					return Collections.emptyList();
				}
				Location location;
				if (node != null) {
					String uriString = JDTUtils.toUri(classFile);
					IDocument document = new Document(contents);
					if (declaration) {
						int offset = node.getStartPosition();
						int length = node.getLength();
						Range range;
						if (offset >= 0 && length > 0 && offset + length <= contents.length()) {
							int[] start = JsonRpcHelpers.toLine(document, offset);
							int[] end = JsonRpcHelpers.toLine(document, offset + length);
							range = new Range(new Position(start[0], start[1]), new Position(end[0], end[1]));
						} else {
							range = new Range();
						}
						location = new Location(uriString, range);
						locations.add(location);
						return locations;
					}
					OccurrencesFinder finder = new OccurrencesFinder();
					if (node instanceof MethodDeclaration) {
						SimpleName name = ((MethodDeclaration) node).getName();
						finder.initialize(unit, name);
					} else if (node instanceof Name) {
						finder.initialize(unit, node);
					} else if (node instanceof MethodInvocation) {
						SimpleName name = ((MethodInvocation) node).getName();
					} else {
						return locations;
					}
					OccurrenceLocation[] occurrences = finder.getOccurrences();
					for (OccurrenceLocation occurrence : occurrences) {
						int offset = occurrence.getOffset();
						int length = occurrence.getLength();
						Range range;
						if (offset >= 0 && length > 0 && offset + length <= contents.length()) {
							int[] start = JsonRpcHelpers.toLine(document, offset);
							int[] end = JsonRpcHelpers.toLine(document, offset + length);
							range = new Range(new Position(start[0], start[1]), new Position(end[0], end[1]));
						} else {
							range = new Range();
						}
						location = new Location(uriString, range);
						locations.add(location);
					}
				} else {
					location = JDTUtils.toLocation(classFile, 0, 0);
					locations.add(location);
				}
			} finally {
				if (workingCopy != null) {
					workingCopy.discardWorkingCopy();
				}
			}
		}
		return locations;
	}

	public static ICompilationUnit getWorkingCopy(IClassFile classFile, String contents, IProgressMonitor monitor) throws JavaModelException {
		String name = classFile.getElementName().replace(".class", ".java");
		IPackageFragment fragment = (IPackageFragment) classFile.getAncestor(IJavaElement.PACKAGE_FRAGMENT);
		if (!fragment.getElementName().isEmpty()) {
			name = fragment.getElementName().replace(".", "/") + "/" + name;
		}
		IClasspathEntry[] existingEntries = classFile.getJavaProject().getRawClasspath();
		IPath path = new Path(name);
		for (IClasspathEntry entry : existingEntries) {
			if (entry.getEntryKind() == IClasspathEntry.CPE_SOURCE) {
				IPath srcPath = entry.getPath();
				path = srcPath.append(name).makeRelative().removeFirstSegments(1);
				break;
			}
		}
		IFile file = classFile.getJavaProject().getProject().getFile(path);
		ICompilationUnit sourceUnit = JavaCore.createCompilationUnitFrom(file);
		ICompilationUnit workingCopy = sourceUnit.getWorkingCopy(new WorkingCopyOwner() {
		}, monitor);
		workingCopy.getBuffer().setContents(contents);
		workingCopy.becomeWorkingCopy(monitor);
		workingCopy.reconcile(ICompilationUnit.NO_AST, false, null, monitor);
		if (monitor.isCanceled()) {
			workingCopy.discardWorkingCopy();
			return null;
		}
		return workingCopy;
	}

}
