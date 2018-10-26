package data.method.semantic;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.dom.CompilationUnit;

import code.parser.AST.ASTGenerator;
import code.parser.AST.ASTGenerator.TokenType;
import code.parser.JavaFileParser.JavaFileParser;
import code.parser.JavaFileParser.JavaFileParser.MethodBodyTreeReader;
import code.parser.JavaFileParser.SimpleTree;
import code.parser.entity.Pair;
import code.parser.jdt.tree.ITree;
import code.parser.method.Method;
import code.parser.utils.FileHelper;

public class FacoyResultsParser {
	
	private List<Pair<Integer, Integer>> positions = new ArrayList<>();
	File javaFile;
	private CompilationUnit unit = null;
	public String packageName;
	public String className;
	public List<Method> methods = new ArrayList<>();
	public List<Method> constructors = new ArrayList<>();
	
	public void parserSimilarMethods(File javaFile, File positionsFile) {
		parserPositions(positionsFile);
		if (this.positions.size() == 0) return;
		
		this.javaFile = javaFile;
		unit = new JavaFileParser().new MyUnit().createCompilationUnit(javaFile);
		try {
			packageName = unit.getPackage().getName().toString();
		} catch (Exception e) {
			/*
			 * 1_Promasi-Multiplayer||
			 * org.promasi.desktop_swing||Libs||jfreechart-1.0.14||source||org||jfree||chart||renderer||xy||AbstractXYItemRenderer.java
			 */
			packageName = javaFile.getName().replace("||", ".");
		}
		
		ITree rootTree = new ASTGenerator().generateTreeForJavaFile(javaFile, TokenType.EXP_JDT);
		identifyMethod(rootTree, "");
	}
	
	private void parserPositions(File positionsFile) {
		String content = FileHelper.readFile(positionsFile).trim();
		if ("[]".equals(content)) return;
		content = content.substring(2, content.length() - 2);
		String[] positionsGroup = content.split("\\], \\[");
		for (String positionGroup : positionsGroup) {
			String[] lines = positionGroup.split(", ");
			int startLine = Integer.parseInt(lines[0].trim());
			int endLine = Integer.parseInt(lines[lines.length - 1].trim());
			Pair<Integer, Integer> pair = new Pair<>(startLine, endLine);
			positions.add(pair);
		}
	}
	
	private void identifyMethod(ITree tree, String className) {
		List<ITree> children = tree.getChildren();
		
		for (ITree child : children) {
			int astNodeType = child.getType();
			
			if (astNodeType == 31) { // MethodDeclaration.
				readMethodInfo(child, className);
			} else {
				String currentClassName = "";
				if (astNodeType == 55) { // TypdeDeclaration.
					String classNameLabel = readClassNameLabel(child);
					currentClassName = classNameLabel.substring(10);
				}
				if ("".equals(className)) {
					identifyMethod(child, currentClassName);
					this.className = currentClassName;
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
		int methodNamePosition = readMethodNamePosition(methodBodyTree);
		if (methodNamePosition == 0) return;
		int startLine = unit.getLineNumber(methodNamePosition);
		int endLine = unit.getLineNumber(endPosition);
		
		if (!isContained(startLine, endLine)) return;
		
		String methodBodySourceCode = getMethodSourceCode(startPosition, endPosition);//getMethodSourceCode(methodBodyTree, startLine, endLine);
		
		MethodBodyTreeReader reader = new JavaFileParser().new MethodBodyTreeReader();
		reader.readToken(methodBodyTree);
		String arguments = reader.argus;
		SimpleTree methodBodySimpleTree = reader.methodBodyStatementTrees;
		if (methodBodySimpleTree.getChildren().size() == 0) return;// empty method.
		
		Method method = new Method("", packageName, className, returnType, methodName, methodBodySourceCode, isConstructor, arguments);
		methods.add(method);
	}
	
	private int readMethodNamePosition(ITree methodBodyTree) {
		ITree methodName = null;
		List<ITree> children = methodBodyTree.getChildren();
		for (ITree child : children) {
			if (child.getType() == 42) {
				methodName = child;
				break;
			}
		}
		return methodName == null ? 0 : methodName.getPos();
	}

	private boolean isContained(int startLine, int endLine) {
		for (Pair<Integer, Integer> position : this.positions) {
			if (position.firstElement <= startLine && endLine <= position.secondElement) return true;
		}
		return false;
	}

	private String getMethodSourceCode(int startPos, int endPos) {
		String javaCode = FileHelper.readFile(this.javaFile);
		return javaCode.substring(startPos, endPos);
	}
	
}
