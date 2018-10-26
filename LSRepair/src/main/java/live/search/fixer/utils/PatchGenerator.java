package live.search.fixer.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import code.parser.AST.ASTGenerator;
import code.parser.AST.ASTGenerator.TokenType;
import code.parser.JavaFileParser.TypeReader;
import code.parser.jdt.tree.ITree;
import code.parser.utils.Checker;

public class PatchGenerator {

	public Patch generatePatch(String suspiciousMethodBodyCode, String suspiciousClassName, String similarMethod) {
		Patch patch = new Patch();
		patch.patchMethodCode = generatePatchMethodBody("public class T {\n" + suspiciousMethodBodyCode + "}\n", "public class T {\n" + similarMethod + "}\n");
		patch.className = suspiciousClassName;
		return patch;
	}

	public Patch generatePatch(String suspiciousStmtCode, String suspiciousClassName, String similarMethod, ITree suspStmtTree, String returnType, String exceptionStr) {
		Patch patch = new Patch();
		patch.patchStatementCode = generatePatchStmt(suspiciousStmtCode, "public class T {\n" + similarMethod + "}\n", suspStmtTree, returnType, exceptionStr);
		patch.className = suspiciousClassName;
		return patch;
	}
	
	public Patch generatePatch(String suspiciousMethodBodyCode, String suspiciousStmtCode, int startLine, String suspiciousClassName, String similarMethod, ITree suspStmtTree, String returnType, String argus, String exceptionStr) {
		Patch patch = new Patch();
		patch.patchStatementCode = generatePatchString(suspiciousMethodBodyCode, suspiciousStmtCode, "public class T {\n" + similarMethod + "}\n", suspStmtTree, returnType, argus, exceptionStr, startLine, patch);
		patch.className = suspiciousClassName;
		return patch;
	}
	
	private List<String> generatePatchString(String suspiciousMethodBodyCode, String suspiciousStmtCode, String newMethod, ITree suspStmtTree,
			String returnType, String argus, String exceptionStr, int startLine, Patch patch) {
		ITree newClassTree = new ASTGenerator().generateTreeForJavaFileContent(newMethod, TokenType.EXP_JDT);
		List<ITree> classChildren = newClassTree.getChildren().get(0).getChildren();
		ITree methodTree = classChildren.get(classChildren.size() - 1);
		String methodNameInfo = methodTree.getLabel();
		int indexOfMethodName = methodNameInfo.indexOf("MethodName:");
		String newReturnType = methodNameInfo.substring(methodNameInfo.indexOf("@@") + 2, indexOfMethodName - 2);
		int index = newReturnType.indexOf("@@tp:");
		if (index > 0) {
			newReturnType = newReturnType.substring(0, index - 2);
		}
		newReturnType = TypeReader.canonicalType(newReturnType);
		if (newReturnType.equals(returnType)) {
			// Arguments
			String newArgus = "";
			if (methodNameInfo.contains("@@Argus:null")) {
				newArgus = "null";
			} else {
				newArgus = methodNameInfo.substring(methodNameInfo.indexOf("@@Argus:") + 8, methodNameInfo.length() - 1).replace(" ", "").replace("+", "#");
				int expIndex = newArgus.indexOf("@@Exp:");
				if (expIndex > 0) {
					newArgus = newArgus.substring(0, expIndex - 1);
				}
			}
			newArgus = TypeReader.readArgumentTypes(newArgus);
			if (newArgus.equals(argus)) {
				// Same signature: replace the whole method body.
				int endPositionOfNewMethodName = readEndPositionOfMethodName(methodTree);
				if (endPositionOfNewMethodName == 0) {
					System.err.println("Failed to read the name of the new method!");
					System.err.println(newMethod);
				} else {
					String buggyMethod = "public class T {\n" + suspiciousMethodBodyCode + "}\n";
					ITree buggyMethodTree = new ASTGenerator().generateTreeForJavaFileContent(buggyMethod, TokenType.EXP_JDT);
					int endPositionOfBuggyMethodName = readEndPositionOfMethodName(buggyMethodTree);
					if (endPositionOfBuggyMethodName == 0) {
						System.err.println("Failed to read the name of the buggy method!");
					} else {
						String patchCode = newMethod.substring(endPositionOfNewMethodName);
						
						buggyMethod = buggyMethod.substring(0, endPositionOfBuggyMethodName) + patchCode;
						int endPosition = buggyMethod.length();
						buggyMethod = buggyMethod.substring(17, endPosition - 2);
						patch.patchMethodCode = buggyMethod;
					}
				}
				
			}
		} else if (isNumericType(returnType) && isNumericType(newReturnType)) {
			//if(!"void".equals(returnType) && !"void".equals(newReturnType) && !"=CONSTRUCTOR=".equals(returnType) && !"=CONSTRUCTOR=".equals(newReturnType)) {
			// Change return type of method declaration.
			String buggyMethod = "public class T {\n" + suspiciousMethodBodyCode + "}\n";
			ITree buggyClassTree = new ASTGenerator().generateTreeForJavaFileContent(buggyMethod, TokenType.EXP_JDT);
			List<ITree> buggyClassChildren = buggyClassTree.getChildren().get(0).getChildren();
			ITree buggyMethodTree = buggyClassChildren.get(buggyClassChildren.size() - 1);
			ITree returnTypeNode = readReturnType(buggyMethodTree);
			if (returnTypeNode == null) {
				System.err.println("Failed to read the return type of the buggy method!");
			} else {
				int startPos = returnTypeNode.getPos();
				String patchCode = buggyMethod.substring(0, startPos) + newReturnType;
				
				buggyMethod = patchCode + buggyMethod.substring(startPos + returnTypeNode.getLength());
				int endPosition = buggyMethod.length();
				buggyMethod = buggyMethod.substring(17, endPosition - 2);
				patch.patchMethodCode = buggyMethod;
			}
		}
		
		if (suspStmtTree == null) return new ArrayList<String>(0);
		int startPositionOfNewMethodName = methodTree.getPos();
		int endPositionOfNewMethodName = methodTree.getLength() + startPositionOfNewMethodName;
		if (endPositionOfNewMethodName == 0) {
			System.err.println("Failed to read the name of the new method!");
			System.err.println(newMethod);
			return new ArrayList<String>(0);
		}
		int suspType = suspStmtTree.getType();
		List<ITree> methodChildren = methodTree.getChildren();
		List<String> arguments = new ArrayList<>();
		List<ITree> stmtCandidates = new ArrayList<>();
		stmtCandidates.addAll(identifySameTypeStmts(methodChildren, suspType, suspStmtTree, arguments, true));
		
		if (stmtCandidates.isEmpty()) {
//			System.err.println("Failed to find statements with the same type!");
			return new ArrayList<String>(0);
		}
		List<String> patchList = null;
		if (suspType == 60) { // VariableDeclarationStatement.
			patchList = fixSuspiciousVariableDeclarationStatement(stmtCandidates, suspStmtTree, newMethod, suspiciousStmtCode, returnType, exceptionStr);
		} else if (suspType == 25) {//IfStatement
			patchList = fixSuspiciousIfStatement(stmtCandidates, suspStmtTree, newMethod, suspiciousStmtCode);
		} else if (suspType == 21) {//ExpressionStatement
			patchList = fixSuspiciousExpStatementstmt(stmtCandidates, suspStmtTree, newMethod, suspiciousStmtCode, returnType, exceptionStr);
		} else if (suspType == 41) {//ReturnStatement
			patchList = fixSuspiciousReturnStatement(stmtCandidates, suspStmtTree, newMethod, suspiciousStmtCode, returnType, exceptionStr, newReturnType);
		} else if (suspType == 24) {// ForStatement
			patchList = fixSuspiciousForStatement(stmtCandidates, suspStmtTree, newMethod, suspiciousStmtCode);
		} else {
			patchList = new ArrayList<String>(); //TODO Other statements
		}
		return patchList;
	}

	private boolean isNumericType(String returnType) {
		if ("int".equals(returnType)) return true;
		if ("Integer".equals(returnType)) return true;
//		if (returnType.equals("char")) return true;
//		if (returnType.equals("Character")) return true;
		if ("byte".equalsIgnoreCase(returnType)) return true;
		if ("short".equalsIgnoreCase(returnType)) return true;
		if ("long".equalsIgnoreCase(returnType)) return true;
		if ("double".equalsIgnoreCase(returnType)) return true;
		if ("float".equalsIgnoreCase(returnType)) return true;
		return false;
	}

	private ITree readReturnType(ITree buggyMethodTree) {
		List<ITree> children = buggyMethodTree.getChildren();
		for (ITree child : children) {
			int type = child.getType();
			if (type != 83) { // Non-Modifier
				if (type == 42) return null;
				else return child;
			}
		}
		return null;
	}

	private String generatePatchMethodBody(String suspiciousMethod, String candidateMethod) {
		ITree newMethodTree = new ASTGenerator().generateTreeForJavaFileContent(candidateMethod, TokenType.EXP_JDT);
		int endPositionOfNewMethodName = readEndPositionOfMethodName(newMethodTree);
		if (endPositionOfNewMethodName == 0) {
			System.err.println("Failed to read the name of the new method!");
			System.err.println(candidateMethod);
			return "";
		}
		ITree buggyMethodTree = new ASTGenerator().generateTreeForJavaFileContent(suspiciousMethod, TokenType.EXP_JDT);
		int endPositionOfBuggyMethodName = readEndPositionOfMethodName(buggyMethodTree);
		if (endPositionOfBuggyMethodName == 0) {
			System.err.println("Failed to read the name of the buggy method!");
			return "";
		}
		String patchCode = candidateMethod.substring(endPositionOfNewMethodName);
		
		suspiciousMethod = suspiciousMethod.substring(0, endPositionOfBuggyMethodName) + patchCode;
		int endPosition = suspiciousMethod.length();
		suspiciousMethod = suspiciousMethod.substring(17, endPosition - 2);
		
		return suspiciousMethod;
	}

	private List<String> generatePatchStmt(String suspiciousStmtCode, String newMethod, ITree suspStmtTree, String returnType, String exceptionStr) {
		ITree newClassTree = new ASTGenerator().generateTreeForJavaFileContent(newMethod, TokenType.EXP_JDT);
		int endPositionOfNewMethodName = readEndPositionOfMethodName(newClassTree);
		if (endPositionOfNewMethodName == 0) {
			System.err.println("Failed to read the name of the new method!");
			System.err.println(newMethod);
			return new ArrayList<String>(0);
		}
		
		List<ITree> classChildren = newClassTree.getChildren().get(0).getChildren();
		ITree methodTree = classChildren.get(classChildren.size() - 1);
		int suspType = suspStmtTree.getType();
		List<ITree> methodChildren = methodTree.getChildren();
		List<String> arguments = new ArrayList<>();
		List<ITree> stmtCandidates = new ArrayList<>();
		stmtCandidates.addAll(identifySameTypeStmts(methodChildren, suspType, suspStmtTree, arguments, true));
		
		if (stmtCandidates.isEmpty()) {
//			System.err.println("Failed to find statements with the same type!");
			return new ArrayList<String>(0);
		}
		List<String> patchList = null;
		if (suspType == 60) { // VariableDeclarationStatement.
			patchList = fixSuspiciousVariableDeclarationStatement(stmtCandidates, suspStmtTree, newMethod, suspiciousStmtCode, returnType, exceptionStr);
		} else if (suspType == 25) {//IfStatement
			patchList = fixSuspiciousIfStatement(stmtCandidates, suspStmtTree, newMethod, suspiciousStmtCode);
		} else if (suspType == 21) {//ExpressionStatement
			patchList = fixSuspiciousExpStatementstmt(stmtCandidates, suspStmtTree, newMethod, suspiciousStmtCode, returnType, exceptionStr);
		} else if (suspType == 41) {//ReturnStatement
			String newReturnType = TypeReader.canonicalType(identifyReturnType(methodTree));
			patchList = fixSuspiciousReturnStatement(stmtCandidates, suspStmtTree, newMethod, suspiciousStmtCode, returnType, exceptionStr, newReturnType);
		} else if (suspType == 24) {// ForStatement
			patchList = fixSuspiciousForStatement(stmtCandidates, suspStmtTree, newMethod, suspiciousStmtCode);
		} else {
			patchList = new ArrayList<String>(); //TODO Other statements.
		}
		return patchList;
	}

	private String identifyReturnType(ITree methodTree) {
		List<ITree> children = methodTree.getChildren();
		for (int index = 0, size = children.size(); index < size; index ++) {
			if (children.get(index).getType() == 42) {
				if (index == 0) {
					return "";
				} else {
					ITree tree = children.get(index - 1);
					if (tree.getType() == 83) {// modifier
						return "";
					} else {
						return tree.getLabel();
					}
				}
			}
		}
		return "";
	}

	private List<String> fixSuspiciousVariableDeclarationStatement(List<ITree> stmtCandidates, ITree suspStmtTree, String methodCode, String suspStmtCode, String returnType, String exceptionStr) {
		List<String> patchList = new ArrayList<>();
		for (ITree stmt : stmtCandidates) {
			ITree stmtParent = stmt.getParent();
			if (stmtParent.getType() == 25) {// if check status for stmt.
				patchList.addAll(addIfParentStatement(stmt, stmtParent, suspStmtTree, suspStmtCode, methodCode));
			}
			
			// Insert if check before stmt.
			ITree previousStmt = identifyPreviousStmt(stmt);
			if (previousStmt != null) {
				if (previousStmt.getType() == 25) {
					patchList.addAll(addIfCheck(stmt, previousStmt, suspStmtTree, suspStmtCode, methodCode, returnType, exceptionStr));
				} else {
					previousStmt = identifyPreviousStmt(previousStmt);
					if (previousStmt != null && previousStmt.getType() == 25) {
						patchList.addAll(addIfCheck(stmt, previousStmt, suspStmtTree, suspStmtCode, methodCode, returnType, exceptionStr));
					}
				}
			}
			
			if (stmt.getType() == 21) {// Expression statement . Assignment.
				// Same data type, mutate expression.
				int buggyExpPosition = identifyExpPostion(suspStmtTree);
				ITree newExpTree = stmt.getChildren().get(0).getChildren().get(2);//the rightExp of Assignment
				int newExpPosition = newExpTree.getPos();
				
				String newExp = methodCode.substring(newExpPosition, stmt.getPos() + stmt.getLength());
				if (buggyExpPosition == 0) {
					patchList.add(suspStmtCode.substring(0, suspStmtCode.indexOf(";")) + " = " + newExp);
				} else { 
					List<String> suspVars = readVariables(suspStmtTree, null);
					if (suspVars.size() <= 1) {
						patchList.add(suspStmtCode.substring(0, buggyExpPosition - suspStmtTree.getPos()) + newExp);
						continue;
					}
					List<String> variables = readVariables(newExpTree, null);
					if (variables.size() == 1 && suspVars.size() == 2) {
						String suspVar = suspVars.get(1);
						String var = variables.get(0);
						
						if (!suspVar.equals(var)) {
							List<Integer> varPositions = identifyPos(newExpTree, var);
							int varLength = var.length();
							String patchExp = "";
							int s = varPositions.size();
							for (int i = 0; i < s; i ++) {
								int prevPos = i == 0 ? newExpPosition : (varPositions.get(i - 1)  + varLength);
								int pos = varPositions.get(i);
								patchExp += newExp.substring(prevPos - newExpPosition, pos - newExpPosition) + suspVar;
							}
							patchExp += newExp.substring(varPositions.get(s - 1) + varLength - newExpPosition);
							patchList.add(suspStmtCode.substring(0, buggyExpPosition - suspStmtTree.getPos()) + patchExp);
						} else {
							patchList.add(suspStmtCode.substring(0, buggyExpPosition - suspStmtTree.getPos()) + newExp);
						}
					} else {
						// FIXME rename more variables.
						patchList.add(suspStmtCode.substring(0, buggyExpPosition - suspStmtTree.getPos()) + newExp);
//						if (variables.size() == 0) {
//							patchList.add(suspStmtCode.substring(0, buggyExpPosition - suspStmtTree.getPos()) + newExp);
//						} else {
//							suspVars = suspVars.subList(1, suspVars.size());
//							if (suspVars.size() >= variables.size()) {
//								for (String suspVar : suspVars) {
//									for (String var : variables) {
//									}
//								}
//							} else {
//							}
//						}
					}
				}
			} else {
				// Buggy data type or Buggy expression?
				ITree dataType = getDataType(suspStmtTree);
				String dataTypeStr = dataType.getLabel();
				String dataTypeStrCandidate = getDataType(stmt).getLabel();
				boolean isType1 = isPrimitiveInteger(dataTypeStr) || "float".equalsIgnoreCase(dataTypeStr) || "double".equalsIgnoreCase(dataTypeStr);
				boolean isType2 = isPrimitiveInteger(dataTypeStrCandidate) || "float".equalsIgnoreCase(dataTypeStrCandidate) || "double".equalsIgnoreCase(dataTypeStrCandidate);
				if (isType1 && isType2 && !sameType(dataTypeStr, dataTypeStrCandidate)) {
					// Mutate data type.
					int pos = dataType.getPos() - suspStmtTree.getPos();
					patchList.add(dataTypeStrCandidate + suspStmtCode.substring(pos + dataTypeStr.length()) + "=" + dataTypeStrCandidate + "=" + dataTypeStr + "=TYPE=");
				} else if (dataTypeStr.replace(" ", "").equals(dataTypeStrCandidate.replace(" ", ""))) {
					// Same data type, mutate expression.
					int buggyExpPosition = identifyExpPostion(suspStmtTree);
					int newExpPosition = identifyExpPostion(stmt);
					
					if (newExpPosition == 0) continue;
					String newExp = methodCode.substring(newExpPosition, stmt.getPos() + stmt.getLength());
					if (buggyExpPosition == 0) {
						patchList.add(suspStmtCode.substring(0, suspStmtCode.indexOf(";")) + " = " + newExp);
					} else {
						patchList.add(suspStmtCode.substring(0, buggyExpPosition - suspStmtTree.getPos()) + newExp);
						List<String> suspVars = readVariables(suspStmtTree, null);
						List<String> variables = readVariables(stmt, null);
						if (variables.size() == 2 && suspVars.size() == 2) {
							String suspVar = suspVars.get(1);
							String var = variables.get(1);
							
							if (!suspVar.equals(var)) {
								List<Integer> varPositions = identifyPos(stmt, var);
								int varLength = var.length();
								String patchExp = "";
								int s = varPositions.size();
								for (int i = 0; i < s; i ++) {
									int prevPos = i == 0 ? newExpPosition : (varPositions.get(i - 1)  + varLength);
									int pos = varPositions.get(i);
									patchExp += newExp.substring(prevPos - newExpPosition, pos - newExpPosition) + suspVar;
								}
								patchExp += newExp.substring(varPositions.get(s - 1) + varLength - newExpPosition);
								patchList.add(suspStmtCode.substring(0, buggyExpPosition - suspStmtTree.getPos()) + patchExp);
							} else {
								patchList.add(suspStmtCode.substring(0, buggyExpPosition - suspStmtTree.getPos()) + newExp);
							}
						} else {
							// FIXME rename more variables.
							patchList.add(suspStmtCode.substring(0, buggyExpPosition - suspStmtTree.getPos()) + newExp);
//							if (variables.size() == 0) {
//								patchList.add(suspStmtCode.substring(0, buggyExpPosition - suspStmtTree.getPos()) + newExp);
//							} else {
//								suspVars = suspVars.subList(1, suspVars.size());
//								if (suspVars.size() >= variables.size()) {
//									for (String suspVar : suspVars) {
//										for (String var : variables) {
//										}
//									}
//								} else {
//								}
//							}
						}
					}
				}
			}
		}
		
		return patchList;
	}

	private int identifyExpPostion(ITree stmtTree) {
		List<ITree> children = stmtTree.getChildren();
		for (ITree child : children) {
			if (child.getType() == 59) {
				List<ITree> subChildren = child.getChildren();
				if (subChildren.size() == 1) return 0;
				return subChildren.get(1).getPos();
			}
		}
		return 0;
	}

	private ITree getDataType(ITree suspStmtTree) {
		List<ITree> children = suspStmtTree.getChildren();
		for (ITree child : children) {
			if (child.getType() != 83) {// non-modifier
				return child;
			}
		}
		return null;
	}

	private List<String> addIfCheck(ITree stmt, ITree previousStmt, ITree suspStmtTree, String suspStmtCode, String methodCode, String returnType, String exceptionStr) {
		List<String> patchList = new ArrayList<>();
		List<ITree> expressions = new ArrayList<>();
		List<String> variables = new ArrayList<>();
		if (isCorelatedIfCheck(stmt, previousStmt, expressions, variables)  && expressions.size() == 1 && variables.size() == 1) {
			// if check status for stmt before it. return or throw.
			List<String> suspVars = readVariables(suspStmtTree, null);
			if (suspVars.size() <= 1) {
				return patchList;
			} else {
				suspVars = suspVars.subList(1, suspVars.size());
				ITree expression = expressions.get(0);
				int expPos = expression.getPos();
				String expCode = methodCode.substring(expPos, expPos + expression.getLength());
				String variable = variables.get(0);
				List<Integer> varPositions = identifyPos(expression, variable);
				int varLength = variable.length();
				List<String> patchExpList = new ArrayList<>();
				for (String suspVar : suspVars) {
					String patchExp = "";
					int s = varPositions.size();
					for (int i = 0; i < s; i ++) {// FIXME: changes of multiple variables.
						int prevPos = i == 0 ? expPos : (varPositions.get(i - 1)  + varLength);
						int pos = varPositions.get(i);
						patchExp += expCode.substring(prevPos - expPos, pos - expPos) + suspVar;
					}
					patchExp += expCode.substring(varPositions.get(s - 1) + varLength - expPos);
					patchExpList.add(patchExp);
				}

				List<ITree> childrenOfPreStmt = previousStmt.getChildren();
				ITree lastChild = childrenOfPreStmt.get(childrenOfPreStmt.size() - 1);
				int type = lastChild.getType();// the SubStatement in the block of IfStatement.
				List<String> patchStmtStrList = new ArrayList<>();
				if (type == 21) {// ExpressionStatement
					int stmtPos = stmt.getPos();
					String patchStmtStr1 = methodCode.substring(stmtPos, stmtPos + stmt.getLength());
					List<Integer> varPositions2 = identifyPos(stmt, variable);
					for (String suspVar : suspVars) {
						String patchStmt = "";
						int s = varPositions2.size();
						for (int i = 0; i < s; i ++) {// FIXME: changes of multiple variables.
							int prevPos = i == 0 ? stmtPos : (varPositions2.get(i - 1)  + varLength);
							int pos = varPositions2.get(i);
							patchStmt += patchStmtStr1.substring(prevPos - stmtPos, pos - stmtPos) + suspVar;
						}
						patchStmt += patchStmtStr1.substring(varPositions2.get(s - 1) + varLength - stmtPos);
						patchStmtStrList.add(patchStmt);
					}
				} else if (type == 41) {// ReturnStatement
					patchStmtStrList.add(generateReturnStmt(returnType));
				} else if (type == 53) {// ThrowStatement
					if ("".equals(exceptionStr)) {
						patchStmtStrList.add(generateReturnStmt(returnType));
					} else {
						patchStmtStrList.add(" throw new " + exceptionStr + "(\""+ exceptionStr + "!\")");
					}
				} else {
					patchStmtStrList.add(generateReturnStmt(returnType));
				}
				if (!patchStmtStrList.isEmpty()) {
					for (String patchExp : patchExpList) {
						for (String patchStmtStr : patchStmtStrList) {
							patchList.add("if (" + patchExp + ") {\n" + patchStmtStr + "\n}\n" + suspStmtCode);
						}
					}
				}
			}
		}
		return patchList;
	}

	private List<String> addIfParentStatement(ITree stmt, ITree stmtParent, ITree suspStmtTree, String suspStmtCode, String methodCode) {
		List<String> patchList = new ArrayList<>();
		List<ITree> expressions = new ArrayList<>();
		List<String> variables = new ArrayList<>();
		if (isCorelatedIfCheck(stmt, stmtParent, expressions, variables) && expressions.size() == 1 && variables.size() == 1) {
			// extract actions in ifStmt: rename the variable with variables in suspStmtTree.
			ITree expression = expressions.get(0);
			if (expression.getType() == 62) { // instanceof exp.
				Map<String, String> castExps = identifyCastExpressions(suspStmtTree);
				if (!castExps.isEmpty()) {
					for (Map.Entry<String, String> entity : castExps.entrySet()) {
						patchList.add("if (" + entity.getKey() + " instanceof " + entity.getValue() + ") {\n"
								+ suspStmtCode + "\n } else {\n throw new IllegalArgumentException(\"Illegal argument \" + "
								+ entity.getKey() + "); \n}\n");
					}
				}
			} else {
				List<String> suspVars = readVariables(suspStmtTree, null);
				if (suspVars.size() == 1) {//The variable at the left of operator.
					return patchList;
				} else {
					suspVars = suspVars.subList(1, suspVars.size());
				}
				int expPos = expression.getPos();
				String expCode = methodCode.substring(expPos, expPos + expression.getLength());
				String variable = variables.get(0);
				List<Integer> varPositions = identifyPos(expression, variable);
				int varLength = variable.length();
				
				for (String suspVar : suspVars) {// Change one variable each iteration. FIXME: multiple variables changing.
					String patchExp = "";
					int s = varPositions.size();
					for (int i = 0; i < s; i ++) {
						int prevPos = i == 0 ? expPos : (varPositions.get(i - 1)  + varLength);
						int pos = varPositions.get(i);
						patchExp += expCode.substring(prevPos - expPos, pos - expPos) + suspVar;
					}
					patchExp += expCode.substring(varPositions.get(s - 1) + varLength - expPos);
					patchList.add("if (" + patchExp + ") {"); //  + suspStmtCode + "\n}\n"
				}
			}
		}
		return patchList;
	}

	private List<String> fixSuspiciousIfStatement(List<ITree> stmtCandidates, ITree suspStmtTree, String newMethod, String suspiciousStmtCode) {
		List<String> patchesList = new ArrayList<>();
		ITree buggyExp = suspStmtTree.getChildren().get(0);
		int buggyCodeStartPos = suspStmtTree.getPos();
		int buggyType = buggyExp.getType();
		for (ITree stmt : stmtCandidates) {
			ITree expCand = stmt.getChildren().get(0);
			int expType = expCand.getType();
			if (buggyType == 27 && expType == 27) {// InfixExpression
				ITree buggyOp = buggyExp.getChildren().get(1);
				ITree opCand = expCand.getChildren().get(1);
				String buggyOpStr = buggyOp.getLabel();
				String opCandStr = opCand.getLabel();
				if (!buggyOpStr.equals(opCandStr) && isSameTypeOp(opCandStr, buggyOpStr)) {
					int startPos = buggyOp.getPos() - buggyCodeStartPos;
					for (int a = startPos, l = buggyExp.getPos() + buggyExp.getLength() - buggyCodeStartPos; a < l; a ++) {
						if (suspiciousStmtCode.charAt(a) != ' ') {
							startPos = a;
							break;
						}
					}
					patchesList.add(suspiciousStmtCode.substring(0, startPos) + opCandStr + suspiciousStmtCode.substring(startPos + buggyOp.getLength()));
				}
				
				// Remove subInfixExpressions.
				if (isLogicalOp(buggyOpStr) && !isLogicalOp(opCandStr)) {
					List<ITree> children = buggyExp.getChildren();
					for (ITree child : children) {
						if (child.getType() != -1) {// Expression.
							int startPos = child.getPos() - buggyCodeStartPos;
							int endPos = startPos + child.getLength();
							patchesList.add("if (" + suspiciousStmtCode.substring(startPos, endPos) + suspiciousStmtCode.substring(buggyExp.getPos()  - buggyCodeStartPos + buggyExp.getLength()));
						}
					}
				}
				// FIXME Insert subInfixExpressions.
//				if (isLogicalOp(buggyOpStr)) {
//				}
			} else {
				if (buggyType == 38 && expType != 38) {// PrefixExpression
					int startPos = buggyExp.getPos() - buggyCodeStartPos;
					patchesList.add(suspiciousStmtCode.substring(0, startPos) + suspiciousStmtCode.substring(startPos + 1));
				} else if (expType == 38) {
					int startPos = buggyExp.getPos() - buggyCodeStartPos;
					int endPos = startPos + buggyExp.getLength();
					patchesList.add(suspiciousStmtCode.substring(0, startPos) + "!(" + suspiciousStmtCode.substring(startPos, endPos) + ")" + suspiciousStmtCode.substring(endPos));
				} else {
					if (buggyType == 27) {
						ITree buggyOp = buggyExp.getChildren().get(1);
						if (isLogicalOp(buggyOp.getLabel())) {
							List<ITree> children = buggyExp.getChildren();
							for (ITree child : children) {
								if (child.getType() != -1) {// Expression. Remove other conditional expressions.
									int startPos2 = child.getPos() - buggyCodeStartPos;
									int endPos2 = startPos2 + child.getLength();
									int s = buggyExp.getPos() - buggyCodeStartPos + buggyExp.getLength();
									patchesList.add("if (" + suspiciousStmtCode.substring(startPos2, endPos2) + suspiciousStmtCode.substring(s));
								}
							}
						}
					}
				}
				
				int startPos = buggyExp.getPos() - buggyCodeStartPos;
				int endPos = startPos + buggyExp.getLength();
				int sPos = expCand.getPos();
				int ePos = sPos + expCand.getLength();
				String expCodeCandidate = newMethod.substring(sPos, ePos);
				
				//FIXME rename variables.
				List<String> suspVars = readVariables(suspStmtTree, null);
				List<String> candVars = readVariables(stmt, null);
				if (suspVars.size() == 0 || candVars.size() == 0) {
					patchesList.add(suspiciousStmtCode.substring(0, startPos) + expCodeCandidate + suspiciousStmtCode.substring(endPos));
					continue;
				}
				if (candVars.size() == 1 && suspVars.size() == 1) {
					String suspVar = suspVars.get(0);
					String var = candVars.get(0);
					if (!suspVar.equals(var)) {
						List<Integer> varPositions = identifyPos(stmt, var);
						int varLength = var.length();
						String patchExp = "";
						int s = varPositions.size();
						for (int i = 0; i < s; i ++) {
							int prevPos = i == 0 ? sPos : (varPositions.get(i - 1)  + varLength);
							int pos = varPositions.get(i);
							patchExp += expCodeCandidate.substring(prevPos - sPos, pos - sPos) + suspVar;
						}
						patchExp += expCodeCandidate.substring(varPositions.get(s - 1) + varLength - sPos);
						patchesList.add(suspiciousStmtCode.substring(0, startPos) + patchExp + suspiciousStmtCode.substring(endPos));
					} else {
						patchesList.add(suspiciousStmtCode.substring(0, startPos) + expCodeCandidate + suspiciousStmtCode.substring(endPos));
					}
				} else {
					// FIXME rename more variables.
					patchesList.add(suspiciousStmtCode.substring(0, startPos) + expCodeCandidate + suspiciousStmtCode.substring(endPos));
//					if (variables.size() == 0) {
//						patchList.add(suspStmtCode.substring(0, buggyExpPosition - suspStmtTree.getPos()) + newExp);
//					} else {
//						suspVars = suspVars.subList(1, suspVars.size());
//						if (suspVars.size() >= variables.size()) {
//							for (String suspVar : suspVars) {
//								for (String var : variables) {
//								}
//							}
//						} else {
//						}
//					}
				}
			}
		}
		return patchesList;
	}
	
	private boolean isSameTypeOp(String opCandStr, String buggyOpStr) {
		if (isLogicalOp(opCandStr) && isLogicalOp(buggyOpStr)) return true;
		if (isRelationalOp(opCandStr) && isRelationalOp(buggyOpStr)) return true;
		return false;
	}

	private boolean isRelationalOp(String opCandStr) {
		if (">".equals(opCandStr) || ">=".equals(opCandStr)
				|| "<".equals(opCandStr) || "<=".equals(opCandStr)
				|| "==".equals(opCandStr) || "!=".equals(opCandStr)) return true;
		return false;
	}

	private boolean isLogicalOp(String opCandStr) {
		if ("&&".equals(opCandStr) || "||".equals(opCandStr)) return true;
		return false;
	}

	private List<String> fixSuspiciousExpStatementstmt(List<ITree> stmtCandidates, ITree suspStmtTree, String methodCode, String suspStmtCode, String returnType, String exceptionStr) {
		List<String> patchList = new ArrayList<>();
		for (ITree stmt : stmtCandidates) {
			ITree stmtParent = stmt.getParent();
			if (stmtParent.getType() == 25) {// if check status for stmt.
				patchList.addAll(addIfParentStatement(stmt, stmtParent, suspStmtTree, suspStmtCode, methodCode));
			}
			
			ITree previousStmt = identifyPreviousStmt(stmt);
			if (previousStmt != null && previousStmt.getType() == 25) {
				patchList.addAll(addIfCheck(stmt, previousStmt, suspStmtTree, suspStmtCode, methodCode, returnType, exceptionStr));
			}
			
			if (stmt.getType() == 60) {// VariableDeclarationStatement.
				ITree buggyExp = suspStmtTree.getChildren().get(0);
				int buggyExpType = buggyExp.getType();
				if (buggyExpType == 7) {
					// Same data type, mutate expression.
					int newExpPosition = identifyExpPostion(stmt);
					
					if (newExpPosition == 0) continue;
					String newExp = methodCode.substring(newExpPosition, stmt.getPos() + stmt.getLength());
					
					List<String> suspVars = readVariables(suspStmtTree, null);
					List<String> variables = readVariables(stmt, null);
					if (variables.size() == 2 && suspVars.size() == 1) {
						String suspVar = suspVars.get(0);
						String var = variables.get(1);
						
						if (!suspVar.equals(var)) {
							List<Integer> varPositions = identifyPos(stmt, var);
							int varLength = var.length();
							String patchExp = "";
							int s = varPositions.size();
							if (s > 1) {
								for (int i = 1; i < s; i ++) {
									int prevPos = varPositions.get(i - 1)  + varLength;
									int pos = varPositions.get(i);
									patchExp += newExp.substring(prevPos - newExpPosition, pos - newExpPosition) + suspVar;
								}
								patchExp += newExp.substring(varPositions.get(s - 1) + varLength - newExpPosition);
								patchList.add(patchExp);
							} else {
								patchList.add(newExp);
							}
						} else {
							patchList.add(newExp);
						}
					} else {
						// FIXME rename more variables.
						patchList.add(newExp);
//						if (variables.size() == 0) {
//							patchList.add(suspStmtCode.substring(0, buggyExpPosition - suspStmtTree.getPos()) + newExp);
//						} else {
//							suspVars = suspVars.subList(1, suspVars.size());
//							if (suspVars.size() >= variables.size()) {
//								for (String suspVar : suspVars) {
//									for (String var : variables) {
//									}
//								}
//							} else {
//							}
//						}
					}
				}
			} else {
				// Buggy operator or Buggy expression?
				ITree buggyExp = suspStmtTree.getChildren().get(0);
				ITree candExp = stmt.getChildren().get(0);
				int buggyExpType = buggyExp.getType();
				int candExpType = candExp.getType();
				if (buggyExpType == 7 && candExpType == 7) { // Assignment
					ITree buggyOp = buggyExp.getChildren().get(1);
					ITree opCand = candExp.getChildren().get(1);
					String buggyOpStr = buggyOp.getLabel();
					String opCandStr = opCand.getLabel();
					if (!buggyOpStr.equals(opCandStr)) {
						int buggyCodeStartPos = suspStmtTree.getPos();
						int startPos = buggyOp.getPos() - buggyCodeStartPos;
						for (int a = startPos, l = buggyExp.getPos() + buggyExp.getLength() - buggyCodeStartPos; a < l; a ++) {
							if (suspStmtCode.charAt(a) != ' ') {
								startPos = a;
								break;
							}
						}
						patchList.add(suspStmtCode.substring(0, startPos) + opCandStr + suspStmtCode.substring(startPos + buggyOp.getLength()));
					}
					
					// Replace the rightExp of the buggyAssignment with the rightExp of newAssignment by replacing variables.
					ITree buggyRightExp = buggyExp.getChildren().get(2);
					ITree candRightExp = candExp.getChildren().get(2);
					int candRightExpPos = candRightExp.getPos();
					String candRightExpStr = methodCode.substring(candRightExpPos, candRightExpPos + candRightExp.getLength());
					List<String> suspVars = readVariables(buggyRightExp, null);
					List<String> candVars = readVariables(candRightExp, null);
					int suspStmtPos = suspStmtTree.getPos();
					if (suspVars.size() == 0 || candVars.size() == 0) {
						patchList.add(suspStmtCode.substring(0, buggyRightExp.getPos() - suspStmtPos) + candRightExpStr + ";");
						continue;
					}
					if (candVars.size() == 1 && suspVars.size() == 1) {
						String suspVar = suspVars.get(0);
						String var = candVars.get(0);
						
						if (!suspVar.equals(var)) {
							List<Integer> varPositions = identifyPos(candRightExp, var);
							int varLength = var.length();
							String patchExp = "";
							int s = varPositions.size();
							for (int i = 0; i < s; i ++) {
								int prevPos = i == 0 ? candRightExpPos : (varPositions.get(i - 1)  + varLength);
								int pos = varPositions.get(i);
								patchExp += candRightExpStr.substring(prevPos - candRightExpPos, pos - candRightExpPos) + suspVar;
							}
							patchExp += candRightExpStr.substring(varPositions.get(s - 1) + varLength - candRightExpPos);
							patchList.add(suspStmtCode.substring(0,buggyRightExp.getPos() - suspStmtPos) + patchExp + ";");
						} else {
							patchList.add(suspStmtCode.substring(0, buggyRightExp.getPos() - suspStmtPos) + candRightExpStr + ";");
						}
					} else {
						// FIXME rename more variables.
						patchList.add(suspStmtCode.substring(0, buggyRightExp.getPos() - suspStmtPos) + candRightExpStr + ";");
//						if (variables.size() == 0) {
//							patchList.add(suspStmtCode.substring(0, buggyExpPosition - suspStmtTree.getPos()) + newExp);
//						} else {
//							suspVars = suspVars.subList(1, suspVars.size());
//							if (suspVars.size() >= variables.size()) {
//								for (String suspVar : suspVars) {
//									for (String var : variables) {
//									}
//								}
//							} else {
//							}
//						}
					}
				} else if ((buggyExpType == 7 && candExpType != 7) || (buggyExpType != 7 && candExpType == 7)) {
					continue;
				} else {
					int startPos = stmt.getPos();
					int endPos = startPos + stmt.getLength();
					
					List<String> suspVars = readVariables(buggyExp, null);
					List<String> candVars = readVariables(candExp, null);
					if (suspVars.size() == 0 || candVars.size() == 0) {
						patchList.add(methodCode.substring(startPos, endPos));
						continue;
					}
					if (candVars.size() == 1 && suspVars.size() == 1) {
						String suspVar = suspVars.get(0);
						String var = candVars.get(0);
						if (!suspVar.equals(var)) {
							String candExpStr = methodCode.substring(startPos, endPos);
							List<Integer> varPositions = identifyPos(stmt, var);
							int varLength = var.length();
							String patchExp = "";
							int s = varPositions.size();
							for (int i = 0; i < s; i ++) {
								int prevPos = i == 0 ? startPos : (varPositions.get(i - 1)  + varLength);
								int pos = varPositions.get(i);
								patchExp += candExpStr.substring(prevPos - startPos, pos - startPos) + suspVar;
							}
							patchExp += candExpStr.substring(varPositions.get(s - 1) + varLength - startPos);
							patchList.add(patchExp + ";");
						} else {
							patchList.add(methodCode.substring(startPos, endPos));
						}
					} else {
						// FIXME rename more variables.
						// TODO MethodInvocation.
						patchList.add(methodCode.substring(startPos, endPos));
//						if (variables.size() == 0) {
//							patchList.add(suspStmtCode.substring(0, buggyExpPosition - suspStmtTree.getPos()) + newExp);
//						} else {
//							suspVars = suspVars.subList(1, suspVars.size());
//							if (suspVars.size() >= variables.size()) {
//								for (String suspVar : suspVars) {
//									for (String var : variables) {
//									}
//								}
//							} else {
//							}
//						}
					}
				}
			}
		}
		return patchList;
	}
	
	private List<String> fixSuspiciousReturnStatement(List<ITree> stmtCandidates, ITree suspStmtTree, String methodCode, String suspStmtCode, String buggyReturnType, String exceptionStr, String newReturnType) {
		// Return statement, insert if or upd exp.
		List<String> patchList = new ArrayList<>();
		for (ITree stmt : stmtCandidates) {
			ITree stmtParent = stmt.getParent();
			if (stmtParent.getType() == 25) {// if check status for stmt.
				patchList.addAll(addIfParentStatement(stmt, stmtParent, suspStmtTree, suspStmtCode, methodCode));
			}
			
			ITree previousStmt = identifyPreviousStmt(stmt);
			if (previousStmt != null && previousStmt.getType() == 25) {
				patchList.addAll(addIfCheck(stmt, previousStmt, suspStmtTree, suspStmtCode, methodCode, buggyReturnType, exceptionStr));
			}
			
			// Buggy expression.
			if (newReturnType.equals(buggyReturnType)) {
				int startPos = stmt.getPos();
				int endPos = startPos + stmt.getLength();
				
				List<String> suspVars = readVariables(suspStmtTree, null);
				List<String> candVars = readVariables(stmt, null);
				if (suspVars.size() == 0 || candVars.size() == 0) {
					patchList.add(methodCode.substring(startPos, endPos));
					continue;
				}
				if (candVars.size() == 1 && suspVars.size() == 1) {
					String suspVar = suspVars.get(0);
					String var = candVars.get(0);
					if (!suspVar.equals(var)) {
						String candExpStr = methodCode.substring(startPos, endPos);
						List<Integer> varPositions = identifyPos(stmt, var);
						int varLength = var.length();
						String patchExp = "";
						int s = varPositions.size();
						for (int i = 0; i < s; i ++) {
							int prevPos = i == 0 ? startPos : (varPositions.get(i - 1)  + varLength);
							int pos = varPositions.get(i);
							patchExp += candExpStr.substring(prevPos - startPos, pos - startPos) + suspVar;
						}
						patchExp += candExpStr.substring(varPositions.get(s - 1) + varLength - startPos);
						patchList.add(patchExp + ";");
					} else {
						patchList.add(methodCode.substring(startPos, endPos));
					}
				} else {
					// FIXME rename more variables.
					// TODO MethodInvocation.
					patchList.add(methodCode.substring(startPos, endPos));
//					if (variables.size() == 0) {
//						patchList.add(suspStmtCode.substring(0, buggyExpPosition - suspStmtTree.getPos()) + newExp);
//					} else {
//						suspVars = suspVars.subList(1, suspVars.size());
//						if (suspVars.size() >= variables.size()) {
//							for (String suspVar : suspVars) {
//								for (String var : variables) {
//								}
//							}
//						} else {
//						}
//					}
				}
			}
		}
		return patchList;
	}

	private List<String> fixSuspiciousForStatement(List<ITree> stmtCandidates, ITree suspStmtTree, String newMethod,
			String suspiciousStmtCode) {
		// TODO Auto-generated method stub
		// Modify the conditional expression part.
		return null;
	}

	private boolean sameType(String dataTypeStr, String dataTypeStrCandidate) {
		if (!dataTypeStr.equalsIgnoreCase(dataTypeStrCandidate)) {
			if ("int".equals(dataTypeStr) && "Integer".equals(dataTypeStrCandidate)) {
				return true;
			}
			if ("Integer".equals(dataTypeStr) && "int".equals(dataTypeStrCandidate)) {
				return true;
			}
			return false;
		}
		return true;
	}

	private String generateReturnStmt(String returnType) {
		String patchStmtStr;
		if ("boolean".equalsIgnoreCase(returnType)) {
			patchStmtStr = "return false;";
		} else if (isPrimitiveInteger(returnType)) {
			patchStmtStr = "return 0;";
		} else if ("char".equals(returnType) || "Character".equals(returnType)) {
			patchStmtStr = "return '';";
		} else if ("float".equalsIgnoreCase(returnType)) {
			patchStmtStr = "return 0f;";
		} else if ("double".equalsIgnoreCase(returnType)) {
			patchStmtStr = "return 0d;";
		} else if ("void".equals(returnType)) {
			patchStmtStr = "return;";
		} else if ("String".equals(returnType)) {
			patchStmtStr = "return \"\";";
		} else {
			patchStmtStr = "return null;";
		}
		return patchStmtStr;
	}

	private boolean isPrimitiveInteger(String returnType) {
		if ("int".equals(returnType)) return true;
		if ("Integer".equals(returnType)) return true;
//		if ("char".equals(returnType)) return true;
//		if ("Character".equals(returnType)) return true;
		if ("byte".equalsIgnoreCase(returnType)) return true;
		if ("short".equalsIgnoreCase(returnType)) return true;
		if ("long".equalsIgnoreCase(returnType)) return true;
//		if ("double".equalsIgnoreCase(returnType)) return true;
//		if ("float".equalsIgnoreCase(returnType)) return true;
		return false;
	}

	private Map<String, String> identifyCastExpressions(ITree statement) {
		Map<String, String> castExps = new HashMap<>();
		List<ITree> children = statement.getChildren();
		for (ITree child : children) {
			if (child.getType() == 11) {// CastExpression
				List<ITree> childrenExps = child.getChildren();
				ITree castedExp = childrenExps.get(1);
				if (castedExp.getType() == 42) {
					String var = castedExp.getLabel();
					String type = childrenExps.get(0).getLabel();
					castExps.put(var, type);
				}
			} else if (Checker.isComplexExpression(child.getType()) || (child.getType() == 42 && child.getLabel().startsWith("MethodName:"))) {
				castExps.putAll(identifyCastExpressions(child));
			}
		}
		return castExps;
	}

	private List<Integer> identifyPos(ITree expression, String variable) {
		List<Integer> positions = new ArrayList<>();
		List<ITree> children = expression.getChildren();
		for (ITree child : children) {
			if (child.getType() == 42) {
				if (child.getLabel().equals(variable) || child.getLabel().equals("Name:" + variable)) {
					positions.add(child.getPos());
				}
			} else if (Checker.isComplexExpression(child.getType())) {
				positions.addAll(identifyPos(child, variable));
			}
		}
		return positions;
	}

	private boolean isCorelatedIfCheck(ITree stmt, ITree stmtParent, List<ITree> expressions, List<String> varialbes) {
		List<String> variables1 = readVariables(stmt, null);
		List<String> variables2 = readVariables(stmtParent, expressions);
		variables1.retainAll(variables2);
		if (variables1.size() == 0) {
			expressions.clear();
			return false;
		}
		List<ITree> expList = new ArrayList<>();
		for (ITree exp : expressions) {
			String expLabel = exp.getLabel();
			for (String var : variables1) {
				if (expLabel.contains(var) && containVar(exp, var)) {
					expList.add(exp);
					varialbes.add(var);
					break;
				}
			}
		}
		expressions.clear();
		expressions.addAll(expList);
		return true;
	}

	private boolean containVar(ITree exp, String var) {
		if (exp.getLabel().equals(var)) return true;
		List<ITree> children = exp.getChildren();
		if (children == null || children.isEmpty()) return false;
		for (ITree child : children) {
			if (child.getType() == 42) {// SimpleName
				if (child.getLabel().equals(var)) return true;
			} else if (Checker.isComplexExpression(child.getType())) {
				return containVar(child, var);
			}
		}
		return false;
	}

	private List<String> readVariables(ITree statement, List<ITree> expressions) {
		List<String> variables = new ArrayList<>();
		List<ITree> children = statement.getChildren();
		if (children != null && children.size() > 0) {
			for (ITree child : children) {
				if (Checker.isStatement2(child.getType())) break;
				if (child.getType() == 42) {// SimpleName
					String variable = child.getLabel();
					if (variable.contains(":")) {
						if (variable.contains("MethodName:")) {
							variables.addAll(readVariables(child, null));
						} else {
							variables.add(variable.substring(5));
						}
					} else if (!variable.contains(":")) {
						variables.add(variable);
					}
				} else if (Checker.isComplexExpression(child.getType())) {
					variables.addAll(readVariables(child, null));
					if (expressions != null) {
						expressions.add(child);
					}
				}
			}
		}
		return variables;
	}

	private ITree identifyPreviousStmt(ITree stmt) {
		List<ITree> children = stmt.getParent().getChildren();
		if (children.size() == 1) {
			return null;
		}
		for (int index = 0, size = children.size(); index < size; index ++) {
			ITree child = children.get(index);
			if (child.equals(stmt)) {
				if (index == 0) return null;
				return children.get(index - 1);
			}
		}
		return null;
	}

	private List<ITree> identifySameTypeStmts(List<ITree> methodChildren, int suspType, ITree suspStmtTree, List<String> arguments, boolean isMethod) {
		List<ITree> stmtCandidates = new ArrayList<>();
		for (int index = 0, size = methodChildren.size(); index < size; index ++) {
			ITree child = methodChildren.get(index);
			int type = child.getType();
			if (isMethod && type == 44) { // SingleVariableDeclaration, Identify arguments.
				List<ITree> agrumentList = child.getChildren();
				arguments.add(agrumentList.get(agrumentList.size() - 1).getLabel());
			} else if (type == suspType) {// Same type statement.
				stmtCandidates.add(child);
			} else if ((suspType == 60 && type == 21)// && child.getChild(0).getType() == 7)
					|| (type == 60 && suspType == 21)) {// && suspStmtTree.getChild(0).getType() == 7)) {// VariableDeclarationStatement <==> ExpressionStatement.Assignment
				stmtCandidates.add(child);
			}
			if (Checker.isComplexExpression(type) || Checker.isStatement(type)) {
				stmtCandidates.addAll(identifySameTypeStmts(child.getChildren(), suspType, suspStmtTree, arguments, false));
			}
		}
		return stmtCandidates;
	}

	private int readEndPositionOfMethodName(ITree methodTree) {
		List<ITree> children = methodTree.getChildren();
		for (ITree child : children) {
			if (child.getType() == 42 && child.getLabel().contains("MethodName:")) {// SimpleName
				return child.getPos() + child.getLength();
			} else {
				int pos = readEndPositionOfMethodName(child);
				if (pos != 0) return pos;
			}
		}
		return 0;
	}

}
