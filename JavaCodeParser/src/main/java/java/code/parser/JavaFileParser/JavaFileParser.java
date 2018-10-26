package java.code.parser.JavaFileParser;

import java.code.parser.AST.ASTGenerator;
import java.code.parser.AST.ASTGenerator.TokenType;
import java.code.parser.jdt.tree.ITree;
import java.code.parser.method.Method;
import java.code.parser.utils.Checker;
import java.code.parser.utils.FileHelper;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;

public class JavaFileParser {
	
	private String projectName;
	private String packageName;
	private CompilationUnit unit = null;
	private File javaFile;
	private List<Method> methods = new ArrayList<>();
	private List<Method> constructors = new ArrayList<>();
	private ITree methodTree = null;
	private ITree suspiciousStmt = null;
	private boolean ignoreGetterAndSetterMethods = false;

	public List<Method> getMethods() {
		return methods;
	}
	
	public List<Method> getConstructors() {
		return constructors;
	}

	public ITree getMethodTree() {
		return methodTree;
	}

	public ITree getSuspiciousStmt() {
		return this.suspiciousStmt;
	}
	
	public void setIgnoreGetterAndSetterMethods(boolean ignoreGetterAndSetterMethods) {
		this.ignoreGetterAndSetterMethods = ignoreGetterAndSetterMethods;
	}

	public void parseJavaFile(String projectName, File file) {
		this.javaFile = file;
		this.projectName = projectName;
		unit = new MyUnit().createCompilationUnit(file);
		try {
			packageName = unit.getPackage().getName().toString();
		} catch (Exception e) {
			packageName = file.getPath();
			System.err.println("Failed to get package name: " + packageName);
			int fromIndex = packageName.indexOf(projectName);
			if (fromIndex < 0) {
				fromIndex = packageName.indexOf("dataset") + 8;
			}
			packageName = packageName.substring(fromIndex, packageName.lastIndexOf("/")).replaceAll("/", ".");
		}
		
		ITree rootTree = new ASTGenerator().generateTreeForJavaFile(file, TokenType.EXP_JDT);
		identifyMethod(rootTree, "");
	}
	
	public void parseSuspiciousJavaFile(String projectName, File file, int lineNumber) {
		this.javaFile = file;
		this.projectName = projectName;
		unit = new MyUnit().createCompilationUnit(file);
		try {
			packageName = unit.getPackage().getName().toString();
		} catch (Exception e) {
			packageName = file.getPath();
			System.err.println("Failed to get package name: " + packageName);
			int fromIndex = packageName.indexOf(projectName);
			if (fromIndex < 0) {
				fromIndex = packageName.indexOf("dataset") + 8;
			}
			packageName = packageName.substring(fromIndex, packageName.lastIndexOf("/")).replaceAll("/", ".");
		}
		
		ITree rootTree = new ASTGenerator().generateTreeForJavaFile(file, TokenType.EXP_JDT);
		identifySuspiciousMethod(rootTree, "", lineNumber);
	}

	private void identifyMethod(ITree tree, String className) {
		List<ITree> children = tree.getChildren();
		
		for (ITree child : children) {
			int astNodeType = child.getType();
			
			if (astNodeType == 31) { // MethodDeclaration.
				identifyMethod(child, className);
				readMethodInfo(child, className);
			} else {
				String currentClassName = "";
				if (astNodeType == 55) { // TypdeDeclaration.
					String classNameLabel = readClassNameLabel(child);
					currentClassName = classNameLabel.substring(10);
				}
				if ("".equals(className)) {
					identifyMethod(child, currentClassName);
				} else {
					if ("".equals(currentClassName)) {
						identifyMethod(child, className);
					} else {
						identifyMethod(child, className + "$" + currentClassName);
					}
				}
			}
 		}
	}
	
	private void identifySuspiciousMethod(ITree tree, String className, int lineNumber) {
		List<ITree> children = tree.getChildren();
		
		for (ITree child : children) {
			int astNodeType = child.getType();
			
			if (astNodeType == 31) { // MethodDeclaration.
				int startPosition = child.getPos();
				int endPosition = startPosition + child.getLength();
				int startLine = this.unit.getLineNumber(startPosition);
				int endLine = this.unit.getLineNumber(endPosition);
				if (startLine <= lineNumber && lineNumber <= endLine) {
					identifySuspiciousMethod(child, className, lineNumber);
					if (methods.isEmpty()) {
						readSuspiciousMethodInfo(child, className, lineNumber);
					}
					break;
				}
			} else {
				String currentClassName = "";
				if (astNodeType == 55) { // TypdeDeclaration.
					String classNameLabel = readClassNameLabel(child);
					currentClassName = classNameLabel.substring(10);
				}
				if ("".equals(className)) {
					identifySuspiciousMethod(child, currentClassName, lineNumber);
				} else {
					if ("".equals(currentClassName)) {
						identifySuspiciousMethod(child, className, lineNumber);
					} else {
						identifySuspiciousMethod(child, className + "$" + currentClassName, lineNumber);
					}
				}
			}
 		}
	}

	private String readClassNameLabel(ITree classNameTree) {
		String classNameLabel = "";
		List<ITree> children = classNameTree.getChildren();
		for (ITree child : children) {
			if (child.getType() == 42) { // SimpleName
				classNameLabel = child.getLabel();
				break;
			}
		}
		return classNameLabel;
	}

	private void readMethodInfo(ITree methodBodyTree, String className) {
		String methodNameInfo = methodBodyTree.getLabel();
		int indexOfMethodName = methodNameInfo.indexOf("MethodName:");
		String methodName = methodNameInfo.substring(indexOfMethodName);
		methodName = methodName.substring(11, methodName.indexOf(", "));
		
		if ("main".equals(methodName)) return;
		
		boolean isConstructor = false;
		String returnType = methodNameInfo.substring(methodNameInfo.indexOf("@@") + 2, indexOfMethodName - 2);
		int index = returnType.indexOf("@@tp:");
		if (index > 0) {
			returnType = returnType.substring(0, index - 2);
		}
		if ("=CONSTRUCTOR=".equals(returnType)) {// Constructor.
			isConstructor = true;
		}
		
		int startPosition = methodBodyTree.getPos();
		int endPosition = startPosition + methodBodyTree.getLength();
		
		String methodBodySourceCode = getMethodSourceCode(startPosition, endPosition);//getMethodSourceCode(methodBodyTree, startLine, endLine);
		
		MethodBodyTreeReader reader = new MethodBodyTreeReader();
		reader.readToken(methodBodyTree);
		String arguments = reader.argus;
		SimpleTree methodBodySimpleTree = reader.methodBodyStatementTrees;
		if (methodBodySimpleTree.getChildren().size() == 0) return;// empty method.
		String tokens = new Tokenizer().getAbstractTokensDeepFirst(methodBodySimpleTree).toString();
		List<ITree> children = methodBodyTree.getChildren();
		List<ITree> childStmts = new ArrayList<>();
		boolean isStatement = false;
		for (ITree child : children) {
			if (isStatement) {
				childStmts.add(child);
			} else {
				if (Checker.isStatement(child.getType())) {
					childStmts.add(child);
					isStatement = true;
				}
			}
		}
		//Remove getter and setter methods.
		if (ignoreGetterAndSetterMethods) {
			if (methodName.startsWith("get") || methodName.startsWith("set") || methodName.startsWith("is")) {
				if (childStmts.size() == 1) return;
			}
		}
		methodBodyTree.setChildren(childStmts);
		SimpleTree simpleTree = new SimplifyTree().simplifyTree(methodBodyTree, null);
		String rawTokens = new Tokenizer().getRawTokens(simpleTree).toString();
//		if (tokens.equals("Block Block")) return;
		
		Method method = new Method(projectName, packageName, className, returnType, methodName, methodBodySourceCode, isConstructor, arguments);
		method.setBodyCodeTokens(tokens);
		method.setBodyCodeRawTokens(rawTokens);
		if (isConstructor) {// Constructor.
			constructors.add(method);
		} else {
			methods.add(method);
		}
	}
	
	private void readSuspiciousMethodInfo(ITree methodBodyTree, String className, int lineNumber) {
		String methodNameInfo = methodBodyTree.getLabel();
		int indexOfMethodName = methodNameInfo.indexOf("MethodName:");
		String methodName = methodNameInfo.substring(indexOfMethodName);
		methodName = methodName.substring(11, methodName.indexOf(", "));
		
		if ("main".equals(methodName)) return;
		
		boolean isConstructor = false;
		String returnType = methodNameInfo.substring(methodNameInfo.indexOf("@@") + 2, indexOfMethodName - 2);
		int index = returnType.indexOf("@@tp:");
		if (index > 0) {
			returnType = returnType.substring(0, index - 2);
		}
		if ("=CONSTRUCTOR=".equals(returnType)) {// Constructor.
			isConstructor = true;
		}
		
		int startPosition = methodBodyTree.getPos();
		int endPosition = startPosition + methodBodyTree.getLength();
		int startLine = unit.getLineNumber(startPosition);
		int endLine = unit.getLineNumber(endPosition);
		
		String methodBodySourceCode = getMethodSourceCode(startPosition, endPosition);//getMethodSourceCode(methodBodyTree, startLine, endLine);
		
		MethodBodyTreeReader reader = new MethodBodyTreeReader();
		reader.readToken(methodBodyTree);
		String arguments = reader.argus;
		SimpleTree methodBodySimpleTree = reader.methodBodyStatementTrees;
		if (methodBodySimpleTree.getChildren().size() == 0) return;// empty method.
		String tokens = new Tokenizer().getAbstractTokensDeepFirst(methodBodySimpleTree).toString();
		List<ITree> children = methodBodyTree.getChildren();
		List<ITree> newChildren = new ArrayList<>();
		boolean isStatement = false;
		ITree suspStmt = null; // Suspicious buggy statement.
		for (ITree child : children) {
			if (isStatement) {
				newChildren.add(child);
				if (suspStmt == null) suspStmt = identifyStatement(child, lineNumber);
			} else {
				if (Checker.isStatement(child.getType())) {
					newChildren.add(child);
					isStatement = true;
					suspStmt = identifyStatement(child, lineNumber);
				}
			}
		}
		this.suspiciousStmt = suspStmt;
		methodBodyTree.setChildren(newChildren);
		SimpleTree simpleTree = new SimplifyTree().simplifyTree(methodBodyTree, null);
		String rawTokens = new Tokenizer().getRawTokens(simpleTree).toString();
//		if (tokens.equals("Block Block")) return;
		
		Method method = new Method(projectName, packageName, className, returnType, methodName, methodBodySourceCode, isConstructor, arguments);
		method.setBodyCodeTokens(tokens);
		method.setBodyCodeRawTokens(rawTokens);
		method.setStartLine(startLine);
		method.setEndLine(endLine);
		method.setStartPosition(startPosition);
		method.setEndPosition(endPosition);
		if (isConstructor) {// Constructor.
			constructors.add(method);
		} else {
			methods.add(method);
			this.methodTree = methodBodyTree;
		}
	}

	private ITree identifyStatement(ITree stmtTree, int lineNumber) {
		int startPos = stmtTree.getPos();
		int endPos = startPos + stmtTree.getLength();
		int startLine = this.unit.getLineNumber(startPos);
		int endLine = this.unit.getLineNumber(endPos);
		ITree stmt = null;
		if (startLine <= lineNumber && lineNumber <= endLine) {
			if (startLine == lineNumber || lineNumber == endLine) {
				stmt = stmtTree;
			} else {
				List<ITree> children = stmtTree.getChildren();
				for (ITree child : children) {
					int type = child.getType();
					if (Checker.isValidExpression(type) || Checker.isStatement(type)) {
						stmt = identifyStatement(child, lineNumber);
						if (stmt != null) break;
					}
				}
				if (stmt == null) stmt = stmtTree;
			}
		}
		if (stmt != null) {
			while (true) {
				if (Checker.isStatement(stmt.getType())) {
					break;
				}
				stmt = stmt.getParent();
			}
		}
		return stmt;
	}

	/**
	 * Read method body code.
	 * @param methodBodyTree
	 * @return
	 */
	private String getMethodSourceCode(int startPos, int endPos) {
		String javaCode = FileHelper.readFile(this.javaFile);
		return javaCode.substring(startPos, endPos);
	}

	public class MyUnit {
		
		public CompilationUnit createCompilationUnit(File javaFile) {
			char[] javaCode = readFileToCharArray(javaFile);
			ASTParser parser = createASTParser(javaCode);
			parser.setKind(ASTParser.K_COMPILATION_UNIT);
			CompilationUnit unit = (CompilationUnit) parser.createAST(null);
			
			return unit;
		}

		private ASTParser createASTParser(char[] javaCode) {
			ASTParser parser = ASTParser.newParser(AST.JLS8);
			parser.setSource(javaCode);

			return parser;
		}
		
		private char[] readFileToCharArray(File javaFile) {
			StringBuilder fileData = new StringBuilder();
			BufferedReader br = null;
			
			char[] buf = new char[10];
			int numRead = 0;
			try {
				FileReader fileReader = new FileReader(javaFile);
				br = new BufferedReader(fileReader);
				while ((numRead = br.read(buf)) != -1) {
					String readData = String.valueOf(buf, 0, numRead);
					fileData.append(readData);
					buf = new char[1024];
				}
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				try {
					if (br != null) {
						br.close();
						br = null;
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}

			if (fileData.length() > 0)
				return fileData.toString().toCharArray();
			else return new char[0];
		}
	}

	/**
	 * Read Parameters of each method, and simple tree of method body.
	 */
	public class MethodBodyTreeReader {
		public SimpleTree methodBodyStatementTrees = new SimpleTree();
		public String argus = "";
		
		public void readToken(ITree methodBodyTree) {
			String methodDeclarationLabel = methodBodyTree.getLabel();
			if (methodDeclarationLabel.endsWith("@@Argus:null")) {
				argus = "null";
			} else {
				argus = methodDeclarationLabel.substring(methodDeclarationLabel.indexOf("@@Argus:") + 8, methodDeclarationLabel.length() - 1).replace(" ", "").replace("+", "#");
				int expIndex = argus.indexOf("@@Exp:");
				if (expIndex > 0) {
					argus = argus.substring(0, expIndex - 1);
				}
			}
			
			simplifyTree(methodBodyTree);
		}

		public void simplifyTree(ITree methodBodyTree) {
			methodBodyStatementTrees.setNodeType("Block");
			methodBodyStatementTrees.setLabel("Block");
			methodBodyStatementTrees.setParent(null);
			SimpleTree methodBodySimpleTree = new SimplifyTree().canonicalizeSourceCodeTree(methodBodyTree, null);
			
			List<SimpleTree> stmts = new ArrayList<>();
			List<SimpleTree> children = methodBodySimpleTree.getChildren();
			boolean isStatement = false;
			for (SimpleTree child : children) {
				if (isStatement) {
					stmts.add(child);
				} else {
					String astNodeType = child.getNodeType();
					if (astNodeType.endsWith("Statement") || astNodeType.endsWith("ConstructorInvocation")) {
						stmts.add(child);
						isStatement = true;
					}
				}
			}
			
			methodBodyStatementTrees.setChildren(stmts);
		}
	}
	
}
