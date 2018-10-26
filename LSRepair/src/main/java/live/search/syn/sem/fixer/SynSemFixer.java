package live.search.syn.sem.fixer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import code.parser.JavaFileParser.JavaFileParser;
import code.parser.JavaFileParser.TypeReader;
import code.parser.jdt.tree.ITree;
import code.parser.method.Method;
import code.parser.utils.Checker;
import code.parser.utils.FileHelper;
import code.parser.utils.ListSorter;
import live.search.fault.localization.FaultLocalizer;
import live.search.fault.localization.SuspiciousCode;
import live.search.fault.localization.dataprepare.DataPreparer;
import live.search.fault.localization.utils.FileUtils;
import live.search.fault.localization.utils.PathUtils;
import live.search.fault.localization.utils.ShellUtils;
import live.search.fault.localization.utils.TestUtils;
import live.search.fixer.utils.Patch;
import live.search.fixer.utils.PatchGenerator;
import live.search.fixer.utils.TimeLine;
import live.search.sig.sim.fixer.SigSimFixer;
import live.search.space.MethodCandidate;

/**
 * Try to fix bugs with syntactic- and semantic- similar methods.
 */
public class SynSemFixer {

private static Logger log = LoggerFactory.getLogger(SigSimFixer.class);
	
	private TimeLine timeLine;
	
	private List<SuspiciousCode> suspiciousCandidates;
	private String defects4jPath;
	private String buggyProjectsPath;
	private String buggyProject;
	private List<String> failedTestStrList = new ArrayList<>();
	public int minErrorTest = 0;
	private int minErrorTestAfterFix = 0;
	public boolean isOneByOne = true; // Fine one similar method, then test this one.
	public boolean withoutPriority = false;// Without the priority of similar method candidates.
	public String dataType;//Syntactic or Semantic
	public String searchPath;

	public static void main(String[] args) {
		String buggyProjectsPath = args[0];//"/....../Defects4JData/";
		String defects4jPath = args[1];// "....../defects4j/";
		String buggyProject = args[2]; // Chart_1
		String searchPath = args[3];   // "/.../data/" + "Syntactic or Semantic".
		String metricStr = args[4];    // Zoltar
		
		SynSemFixer fixer = new SynSemFixer();
		fixer.isOneByOne = Boolean.valueOf(args[5]);
		fixer.withoutPriority = Boolean.valueOf(args[6]);
		int expire = Integer.valueOf(args[7]);
		fixer.dataType  = args[8];//Syntactic or Semantic
		fixer.searchPath = searchPath;
		fixer.fixProcess(buggyProjectsPath, defects4jPath, buggyProject, metricStr, expire);
	}

	public void fixProcess(String buggyProjectsPath, String defects4jPath, String buggyProject, String metricStr, int expire) {
		if (!new File(buggyProjectsPath).exists()) {
			log.error("Wrong buggy project parent path!!!");
			return;
		}
		if (!new File(defects4jPath).exists()) {
			log.error("Wrong Defects4J path!!!");
			return;
		}
		if (!new File(buggyProjectsPath + buggyProject).exists()) {
			log.error("Wrong buggy project path!!!");
			return;
		}
		if (!new File(searchPath).exists()) {
			log.error("Wrong search space path!!!");
			return;
		}
		
		this.defects4jPath = defects4jPath;
		this.buggyProjectsPath = buggyProjectsPath;
		this.buggyProject = buggyProject;
		// Prepare data.
		DataPreparer dp = new DataPreparer(buggyProjectsPath);
		dp.prepareData(buggyProject);
		if (!dp.validPaths) return;
		
		// Localize suspicious code.
		try {
			this.suspiciousCandidates = new FaultLocalizer().localizeSuspiciousCode(buggyProjectsPath, buggyProject, dp, metricStr);
		} catch (IOException e) {
			e.printStackTrace();
			log.error("Failed to localize suspicious code!!!");
			return;
		}
		if (this.suspiciousCandidates == null || this.suspiciousCandidates.isEmpty()) {
			log.error("Failed to localize suspicious code!!!");
			return;
		}
		
		// Test the buggy project to return the number of failed tests.
		minErrorTest = TestUtils.getFailTestNumInProject(buggyProjectsPath + buggyProject, defects4jPath, failedTestStrList);
		int a = 0;
		while (minErrorTest == 2147483647 && a < 10) {
			minErrorTest = TestUtils.getFailTestNumInProject(buggyProjectsPath + buggyProject, defects4jPath, failedTestStrList);
			a ++;
		}
		if (minErrorTest == 2147483647) {
			log.error("Failed to use ``defects4j compile`` to compile bug " + buggyProject);
			return;
		}
		log.info(buggyProject + " Failed Tests: " + this.minErrorTest);
		
		timeLine = new TimeLine(expire);
		// Try to fix the bug by modifying suspicious code.
		fixSuspiciousCode(dp, buggyProject);
		
	}

	private void fixSuspiciousCode(DataPreparer dp, String buggyProject) {
		int size = this.suspiciousCandidates.size();
		boolean fixed = false;
		for (int index = 0; index < size; index ++) {
			if (timeLine.isTimeout()) break;
			SuspiciousCode suspiciousCode = this.suspiciousCandidates.get(index);
			String suspiciousClassName = suspiciousCode.getClassName();
			int lineNumber = suspiciousCode.getLineNumber();
			
			String suspiciousJavaFile = suspiciousClassName.replace(".", "/") + ".java";
			String filePath = dp.srcPath + suspiciousJavaFile;
			
			Method suspiciousMethod = null;
			ITree suspStmtTree = null;
			// Read the information of suspicious method that contains the suspicious code.
			JavaFileParser jfp = new JavaFileParser();
			jfp.parseSuspiciousJavaFile(buggyProject, new File(filePath), lineNumber);
			List<Method> suspiciousMethods = jfp.getMethods();
			List<Method> suspiciousConstructors = jfp.getConstructors();
			if (suspiciousMethods.isEmpty() && suspiciousConstructors.isEmpty()) {
				log.error("Failed to read the buggy method of " + buggyProject + " " + suspiciousClassName + "  " + lineNumber);
				continue;
			}
			suspiciousMethod = suspiciousMethods.isEmpty() ? suspiciousConstructors.get(0) : suspiciousMethods.get(0);
			suspStmtTree = jfp.getSuspiciousStmt();
			
			String exceptionStr = jfp.getMethodTree().getLabel();
			int indexExp = exceptionStr.indexOf("@@Exp:");
			if (indexExp > 0) {
				exceptionStr = exceptionStr.substring(indexExp + 6);
				if (exceptionStr.contains("+")) exceptionStr = "";
			} else {
				exceptionStr = "";
			}
			suspiciousMethod.setExceptionStr(exceptionStr);
			
			
			String returnType = suspiciousMethod.getReturnTypeString();
			String arguments = suspiciousMethod.getArgumentsStr();
			String suspiciousMethodName = suspiciousMethod.getName();
			
			returnType = TypeReader.canonicalType(returnType);//FIXME using exact the same return type and argument types to further reduce the search space.
			String argumentTypes = TypeReader.readArgumentTypes(arguments);
			String keySignature = returnType + "#" + argumentTypes;
			String suspiciousMethodSignature = buggyProject + "#" + suspiciousJavaFile.replace("/", "#")+ "#" + suspiciousMethodName + "#" + keySignature;
			suspiciousMethod.setSignature(suspiciousMethodSignature);
			
			if (suspStmtTree != null) {
				int suspType = suspStmtTree.getType();
				if (suspType != 60 && suspType != 21 && suspType != 41 && suspType != 25 && suspType != 24) {
					// VariableDeclarationStatement, ExpressionStatement, ReturnStatement, IfStatement, ForStatement
					continue;
				}
			}
			
			// Search similar methods.
			String packageName = suspiciousClassName;
			List<MethodCandidate> subSearchSpace = readSearchSpace(buggyProject, suspiciousMethodSignature);
			if (subSearchSpace == null) continue;

			int startLine = lineNumber - suspiciousMethod.getStartLine();
			int methodStartPos = suspiciousMethod.getStartPosition();
			int methodEndPos = suspiciousMethod.getEndPosition();
			int startPos = suspStmtTree == null ? -1 : suspStmtTree.getPos();
			int endPos = suspStmtTree == null ? -1 : (startPos + suspStmtTree.getLength());
			String suspiciousStmtCode = suspStmtTree == null ? "" :FileHelper.readFile(filePath).substring(startPos, endPos);
			String suspiciousMethodCode = suspiciousMethod.getBody();
			
			File targetJavaFile = new File(FileUtils.getFileAddressOfJava(dp.srcPath, suspiciousClassName));
	        File targetClassFile = new File(FileUtils.getFileAddressOfClass(dp.classPath, suspiciousClassName));
	        File javaBackup = new File(FileUtils.tempJavaPath(suspiciousClassName, "Fixer"));
	        File classBackup = new File(FileUtils.tempClassPath(suspiciousClassName, "Fixer"));
	        FileHelper.outputToFile(javaBackup, FileHelper.readFile(targetJavaFile), false);
	        if (targetClassFile.exists()) {
	        	FileHelper.outputToFile(classBackup, FileHelper.readFile(targetClassFile), false);
	        }
			
			for (int i = 0, s = subSearchSpace.size(); i < s; i ++) {
				MethodCandidate ssMethod = subSearchSpace.get(i);
				if ("Syntactic".equals(this.dataType)) {
					if (keySignature.equals(ssMethod.signature)) {
						if (suspiciousMethodName.equals(ssMethod.methodName)
								&& packageName.equals(ssMethod.packageName)) {// SameSignature: the same method signature.
							continue;
						}
					}
				}
				// Fix suspicious method with this similar method.
				String similarMethod = ssMethod.bodyCode;
				String similarMethodInfo = ssMethod.info;
				boolean isFixed = fixWithMatchedSimilarMethods(similarMethod, similarMethodInfo, suspiciousMethodCode, suspiciousStmtCode, suspiciousClassName, javaBackup, classBackup, targetJavaFile,
						targetClassFile, startLine, methodStartPos, methodEndPos, startPos, endPos, dp,
						suspiciousMethod.getName(), dataType, suspiciousMethodSignature, suspStmtTree, returnType, argumentTypes, exceptionStr);
				
				FileHelper.outputToFile(targetJavaFile, FileHelper.readFile(javaBackup), false);
				if (classBackup.exists()) {
					FileHelper.outputToFile(targetClassFile, FileHelper.readFile(classBackup), false);
				}
				if (isFixed) break;
			}

			if (fixed) break;
			FileHelper.outputToFile(targetJavaFile, FileHelper.readFile(javaBackup), false);
			if (classBackup.exists()) {
				FileHelper.outputToFile(targetClassFile, FileHelper.readFile(classBackup), false);
			}
		}
	}

	private List<MethodCandidate> readSearchSpace(String buggyProject, String suspiciousMethodSignature) {
		List<MethodCandidate> candidates = new ArrayList<>();
		String path = this.searchPath + this.dataType + "/";
		if ("Syntactic".equals(this.dataType)) {
			List<String> signatures = readSignatures(path + "SuspiciousMethodSignatures.txt");
			int index = signatures.indexOf(suspiciousMethodSignature);
			if (index != -1) {
				String methodsFile = path + "Signature_" + index + "/SearchSpace.txt";
				
				if (new File(methodsFile).exists()) {
					try {
						FileInputStream fis = new FileInputStream(methodsFile);
						Scanner scanner = new Scanner(fis);
						StringBuilder singleMethod = new StringBuilder();
						boolean isMethodBody = false;
						String methodInfo = "";
						
						while (scanner.hasNextLine()) {
							String line = scanner.nextLine();
							if ("#METHOD_BODY#========================".equals(line)) {
								if (isMethodBody) {
									MethodCandidate candidate = new MethodCandidate();
									String[] elements = methodInfo.split(":");
									//e.g., riotfamily_riot:org.riotfamily.pages.model:Site:getSuffixSchema:null:PageSuffixSchema
									candidate.methodName = elements[3];
									candidate.signature = elements[5] + "#" + elements[4];
									candidate.info = methodInfo;
									candidate.bodyCode = singleMethod.toString();
									candidate.packageName = elements[1] + "." + elements[2];
									candidates.add(candidate);
								}
								singleMethod.setLength(0);
								isMethodBody = false;
								methodInfo = "";
							} else {
								if (isMethodBody) {
									singleMethod.append(line).append("\n");
								}
								else {
									isMethodBody = true;
									methodInfo = line;
								}
							}
						}
						if (singleMethod.length() > 0) {
							MethodCandidate candidate = new MethodCandidate();
							String[] elements = methodInfo.split(":");
							candidate.methodName = elements[3];
							candidate.signature = elements[5] + "#" + elements[4];
							candidate.info = methodInfo;
							candidate.bodyCode = singleMethod.toString();
							candidate.packageName = elements[1] + "." + elements[2];
							candidates.add(candidate);
						}
						scanner.close();
						fis.close();
					} catch (FileNotFoundException e) {
						e.printStackTrace();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		} else {//Semantic
			path += buggyProject + "/" + suspiciousMethodSignature;
			int priority = 20;
			for (int i = 0; i <= priority; i ++) {
				if (new File(path + "/method_" + i + ".txt").exists()) {
					try {
						FileInputStream fis = new FileInputStream(path + "/method_" + i + ".txt");
						Scanner scanner = new Scanner(fis);
						StringBuilder singleMethod = new StringBuilder();
						boolean isMethodBody = false;
						String methodInfo = "";
						
						while (scanner.hasNextLine()) {
							String line = scanner.nextLine();
							if ("#METHOD_BODY#========================".equals(line)) {
								if (isMethodBody) {
									MethodCandidate candidate = new MethodCandidate();
									candidate.info = methodInfo;
									candidate.bodyCode = singleMethod.toString();
									candidates.add(candidate);
								}
								singleMethod.setLength(0);
								isMethodBody = false;
								methodInfo = "";
							} else {
								if (isMethodBody) {
									singleMethod.append(line).append("\n");
								}
								else {
									isMethodBody = true;
									methodInfo = line;
								}
							}
						}
						if (singleMethod.length() > 0) {
							MethodCandidate candidate = new MethodCandidate();
							candidate.info = methodInfo;
							candidate.bodyCode = singleMethod.toString();
							candidates.add(candidate);
						}
						scanner.close();
						fis.close();
					} catch (FileNotFoundException e) {
						e.printStackTrace();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		}
		return candidates;
	}

	private List<String> readSignatures(String fileName) {
		List<String> signatures = new ArrayList<>();
		try {
			FileReader fileReader = new FileReader(fileName);
			BufferedReader bufReader = new BufferedReader(fileReader);
			String line = null;
			while ((line = bufReader.readLine()) != null) {
				signatures.add(line);
			}
			bufReader.close();
			fileReader.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return signatures;
	}

	private boolean fixWithMatchedSimilarMethods(String similarMethod, String similarMethodInfo, String suspiciousMethodCode, String suspiciousStmtCode, 
			String suspiciousClassName, File javaBackup, File classBackup, File targetJavaFile, File targetClassFile, int startLine, int methodStartPos, 
			int methodEndPos, int startPos, int endPos, DataPreparer dp, String methodName, String type, String signature, ITree suspStmtTree, 
			String returnType, String argumentTypes, String exceptionStr) {

		Patch patch = new PatchGenerator().generatePatch(suspiciousMethodCode, suspiciousStmtCode, startLine, suspiciousClassName, similarMethod, suspStmtTree, returnType, argumentTypes, exceptionStr);
		String patchMethodCode = patch.patchMethodCode;
		if (patchMethodCode != null) {
			FileHelper.outputToFile(targetJavaFile, FileHelper.readFile(javaBackup), false);
			addCodeToFile(targetJavaFile, patchMethodCode, methodStartPos, methodEndPos);// Insert the patch.
            
			if (compilePatchedProgram(dp, targetJavaFile, targetClassFile)) {// Compile the patched program.
				List<String> failedTestsAfterFix = new ArrayList<>();
				int errorTestAfterFix = TestUtils.getFailTestNumInProject(this.buggyProjectsPath + this.buggyProject, this.defects4jPath, failedTestsAfterFix);
				if (errorTestAfterFix < minErrorTest) {
					failedTestsAfterFix.removeAll(this.failedTestStrList);
					if (failedTestsAfterFix.size() > 0) {
						System.err.println(buggyProject + " ---Generated new bugs: " + failedTestsAfterFix.size());
						return false;
					}
					if (errorTestAfterFix == 0) {
						log.info("Succeeded to fix the bug " + buggyProject + "====================");
						outputPatch(errorTestAfterFix, type, methodName, methodStartPos, signature, suspiciousMethodCode,
								similarMethodInfo, patchMethodCode, "FixedBugs");
						return true;
					} else {
						if (minErrorTestAfterFix == 0 || errorTestAfterFix < minErrorTestAfterFix) {
							log.info("Partially Succeeded to fix the bug " + buggyProject + "====================");
							outputPatch(errorTestAfterFix, type, methodName, methodStartPos, signature, suspiciousMethodCode,
									similarMethodInfo, patchMethodCode, "PartialFixedBugs");
						}
					}
				} else {
					System.err.println("Failed Tests after fixing: " + errorTestAfterFix + " " + buggyProject + " ");
				}
			}
		}
		
		List<String> patchStmts = patch.patchStatementCode;
		if (patchStmts == null) return false;
		for (int index = 0, size = patchStmts.size(); index < size; index ++) {
			String patchStmt = patchStmts.get(index);
			FileHelper.outputToFile(targetJavaFile, FileHelper.readFile(javaBackup), false);
			String patchCode = addCodeToFile(targetJavaFile, patchStmt, startPos, endPos, suspiciousMethodCode, suspStmtTree);// Insert the patch.

            if (!compilePatchedProgram(dp, targetJavaFile, targetClassFile)) continue;
            
            List<String> failedTestsAfterFix = new ArrayList<>();
			int errorTestAfterFix = TestUtils.getFailTestNumInProject(this.buggyProjectsPath + this.buggyProject, this.defects4jPath, failedTestsAfterFix);
			if (errorTestAfterFix < minErrorTest) {
				failedTestsAfterFix.removeAll(this.failedTestStrList);
				if (failedTestsAfterFix.size() > 0) {
					System.err.println(buggyProject + " ---Generated new bugs: " + failedTestsAfterFix.size());
					continue;
				}
				if (errorTestAfterFix == 0) {
					minErrorTest = errorTestAfterFix;
					log.info("Succeeded to fix the bug " + buggyProject + "====================");
					FileHelper.outputToFile(type + "/FixedBugs/" + buggyProject + "/Patch_" + methodName + "_" + startPos + "_" + index + ".txt", 
							"//**********************************************************\n//  " + signature + 
							"\n//**********************************************************\n===Buggy Code===\n" + suspiciousStmtCode + 
							"\n===Patch Code===\n" + similarMethodInfo + "\n" + patchCode, false);
					return true;
				} else {
					if (minErrorTestAfterFix == 0 || errorTestAfterFix <= minErrorTestAfterFix) {
						minErrorTestAfterFix = errorTestAfterFix;
						log.info("Partially Succeeded to fix the bug " + buggyProject + "====================");
						FileHelper.outputToFile( type + "/PartialFixedBugs/" + buggyProject + "/Patch_" + methodName + "_" + startPos + "_" + index + ".txt", 
								"//**********************************************************\n//  " + signature + 
								"\n//**********************************************************\n===Buggy Code===\n" + suspiciousStmtCode + 
								"\n===Patch Code===\n" + similarMethodInfo + "\n" + patchCode, false);
					}
				}
			} else {
				System.err.println("Failed Tests after fixing: " + errorTestAfterFix + " " + buggyProject);
			}
		}
		FileHelper.outputToFile(targetJavaFile, FileHelper.readFile(javaBackup), false);
	    FileHelper.outputToFile(targetClassFile, FileHelper.readFile(classBackup), false);
	    return false;
	}
	
	private void outputPatch(int errorTestAfterFix, String type, String methodName, int methodStartPos, String signature, String suspiciousMethodCode,
			String similarMethodInfo, String patchCode, String fixedType) {
		minErrorTest = errorTestAfterFix;
		FileHelper.outputToFile(type + "/" + fixedType + "/" + buggyProject + "/buggyMethod_" + methodName + "_" + methodStartPos + ".txt", 
				"//**********************************************************\n//  " + signature + 
				"\n//**********************************************************\n" + suspiciousMethodCode, false);
		FileHelper.outputToFile( type + "/" + fixedType + "/" + buggyProject + "/patchMethod_" + methodName + "_" + methodStartPos + ".txt",
				"//**********************************************************\n//  " + similarMethodInfo + 
				"\n//**********************************************************\n" + patchCode, false);
	}

	private boolean compilePatchedProgram(DataPreparer dp, File targetJavaFile, File targetClassFile) {
		targetClassFile.delete();
        log.info("Compiling");
        int compileResult = TestUtils.compileProjectWithDefects4j(this.buggyProjectsPath + this.buggyProject, this.defects4jPath);
        if (compileResult == 1) {
          try {
				ShellUtils.shellRun(Arrays.asList("javac -Xlint:unchecked -source 1.8 -target 1.8 -cp "
						+ TestUtils.buildClasspath(Arrays.asList(PathUtils.getJunitPath()), dp.classPath, dp.testClassPath) + " -d " + dp.classPath + " "
						+ targetJavaFile.getAbsolutePath())); // Compile patched file.
          } catch (IOException e){
              System.err.println(buggyProject + " ---Fixer: fix fail because of javac exception! ");
              return false;
          }
        }
        if (!targetClassFile.exists()) { // fail to compile
            System.err.println(buggyProject + " ---Fixer: fix fail because of compile fail! ");
            return false;
        }
        log.info("Finalize compiling the patched program.");
        return true;
	}

	private void addCodeToFile(File file, String patchMethodCode, int startPos, int endPos) {
		File newFile = new File(file.getAbsolutePath() + ".temp");
        String javaCode = FileHelper.readFile(file);
        String patchCode = javaCode.substring(0, startPos) + patchMethodCode + javaCode.substring(endPos);
        FileHelper.outputToFile(newFile, patchCode, false);
        if (file.delete()){
            newFile.renameTo(file);
        }
	}
	
	private String addCodeToFile(File file, String patchStatementCode, int startPos, int endPos, String suspiciousStmtCode, ITree suspStmtTree) {
		String patch = "";
		File newFile = new File(file.getAbsolutePath() + ".temp");
        String content = FileHelper.readFile(file);
        
        if (patchStatementCode.endsWith("{")) {
        	if (suspStmtTree.getType() == 60) {// VariableDeclarationStatement.
        		List<String> variables = new ArrayList<>();
        		boolean isFollowingStmt = false;
        		List<ITree> stmts = suspStmtTree.getParent().getChildren();
        		ITree lastStmt = null;
        		for (ITree stmt : stmts) {
        			if (isFollowingStmt) {
        				if (isCorelatedStmt(stmt, variables, stmt.getType(), null, null, null)) {
        					lastStmt = stmt;
        				} else {
        					break;
        				}
        			} else {
        				if (stmt == suspStmtTree) {
        					isFollowingStmt = true;
        					for (ITree childExp : suspStmtTree.getChildren()) {
        						if (childExp.getType() == 42) {
        							variables.add(childExp.getLabel());
        							break;
        						}
        					}
        				}
        			}
        		}
        		if (lastStmt != null) {
        			endPos = lastStmt.getPos() + lastStmt.getLength();
        		}
        	} else {
        		patch = patchStatementCode + "\n" + FileHelper.readFile(file).substring(startPos, endPos) + "}";
        	}
        } else if (patchStatementCode.endsWith("=TYPE=")) { // VariableDeclarationStatement.
        	patch = patchStatementCode.substring(0, patchStatementCode.length() - 6);
        	int index = patch.lastIndexOf("=");
        	String oldType = patch.substring(index + 1);
        	patch = patch.substring(0, index) + "\n";
        	index = patch.lastIndexOf("=");
        	String newType = patch.substring(index + 1);
        	patch = patch.substring(0, index) + "\n";
        	List<String> variables = new ArrayList<>();
        	for (ITree child : suspStmtTree.getChildren()) {
        		if (child.getType() == 59) {//VariableDeclarationFragment
        			variables.add(child.getChildren().get(0).getLabel());
        			break;
        		}
        	}
    		List<ITree> stmts = suspStmtTree.getParent().getChildren();
    		ITree lastStmt = null;
    		boolean isFollowingStmt = false;
    		List<Integer> positionsList = new ArrayList<>();
    		for (int i = 0, size = stmts.size(); i < size; i ++) {
    			ITree stmt = stmts.get(i);
    			if (isFollowingStmt) {
    				List<Integer> posList = new ArrayList<>();
    				identifySameTypes(stmt, oldType, variables, (i == size - 1 ? null : stmts.subList(i + 1, size)), posList);
        			if (posList.size() > 0) {
        				lastStmt = stmt;
        				positionsList.addAll(posList);
        			}
    			} else if (stmt == suspStmtTree) {
    				isFollowingStmt = true;
    			}
    			
    		}
    		if (lastStmt != null) {
    			positionsList = positionsList.stream().distinct().collect(Collectors.toList());
    			ListSorter<Integer> sorter = new ListSorter<Integer>(positionsList);
    			positionsList = sorter.sortAscending();
    			int s = positionsList.size();
    			for (int i = 0; i < s; i ++) {
    				int prevPos = i == 0 ? endPos : (positionsList.get(i - 1) + oldType.length());
    				int currPos = positionsList.get(i);
    				patch += content.substring(prevPos, currPos) + newType;
				}
    			int prevPos = positionsList.get(s - 1) + oldType.length();
    			endPos = lastStmt.getPos() + lastStmt.getLength();
    			patch += content.substring(prevPos, endPos);
    		}
        } else {
        	patch = patchStatementCode;
        }
        
        String patchFile = content.substring(0, startPos) + patch + content.substring(endPos);
        FileHelper.outputToFile(newFile, patchFile, false);
        if (file.delete()){
            newFile.renameTo(file);
        }
        return patch;
	}

	private void identifySameTypes(ITree stmt, String oldType, List<String> variables, List<ITree> peerStmts, List<Integer> posList) {
		if (stmt.getType() == 60) {
			ITree dataType = null;
			String variable = null;
			List<ITree> children = stmt.getChildren();
			for (ITree child : children) {
				if (child.getType() != 83) {// non-modifier
					if (child.getType() == 59) {//VariableDeclarationFragment
						variable = child.getChildren().get(0).getLabel();
						break;
					} else {
						dataType = child;
					}
				}
			}
			if (dataType != null && dataType.getLabel().equals(oldType)) {
				if (isCorelatedStmt(stmt, variables, 60, peerStmts, posList, oldType)) {
					posList.add(dataType.getPos());
					variables.add(variable);
				}
			}
		} else if (Checker.withBlockStatement(stmt.getType())) {
			List<ITree> children = stmt.getChildren();
			for (int index = 0, size = children.size(); index < size; index ++) {
				ITree child = children.get(index);
				if (Checker.isStatement(child.getType())) {
//					posList.addAll(identifySameTypes(child, oldType, variables, (index == size - 1 ? null : children.subList(index + 1, size)), posList));
					identifySameTypes(child, oldType, variables, (index == size - 1 ? null : children.subList(index + 1, size)), posList);
				}
			}
		}
//		return posList;
	}

	private boolean isCorelatedStmt(ITree stmt, List<String> variables, int stmtType, List<ITree> peerStmts, List<Integer> posList, String oldType) {
		List<ITree> children = stmt.getChildren();
		boolean isCorelatedStmt = false;
		for (int index = 0, size = children.size(); index < size; index ++) {
			ITree child = children.get(index);
			// variables in stmt are int variable list.
			int type = child.getType();
			if (type == 42) {
				String variable = child.getLabel();
				if (variables.contains(variable)) {
					isCorelatedStmt = true;
				} else if (stmtType == 60) {// VariableDeclarationStatement
					variables.add(variable);
				}
			} else if (Checker.isComplexExpression(type)) {
				isCorelatedStmt = isCorelatedStmt(child, variables, stmtType, null, posList, oldType);
				if (isCorelatedStmt) return isCorelatedStmt;
			} else if (Checker.isStatement(child.getType())) {
//				posList.addAll(identifySameTypes(child, oldType, variables, (index == size - 1 ? null : children.subList(index + 1, size)), posList));
				identifySameTypes(child, oldType, variables, (index == size - 1 ? null : children.subList(index + 1, size)), posList);
			}
		}
		if (peerStmts != null) {
			for (ITree peerStmt : peerStmts) {
				isCorelatedStmt = isCorelatedStmt(peerStmt, variables, stmtType, null, posList, oldType);
				if (isCorelatedStmt) return isCorelatedStmt;
			}
		}
		return isCorelatedStmt;
	}

}
