package live.search.sig.sim.fixer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import code.parser.JavaFileParser.JavaFileParser;
import code.parser.JavaFileParser.TypeReader;
import code.parser.method.Method;
import code.parser.utils.FileHelper;
import live.search.fault.localization.FaultLocalizer;
import live.search.fault.localization.SuspiciousCode;
import live.search.fault.localization.dataprepare.DataPreparer;
import live.search.fault.localization.utils.FileUtils;
import live.search.fault.localization.utils.PathUtils;
import live.search.fault.localization.utils.ShellUtils;
import live.search.fault.localization.utils.TestUtils;
import live.search.fixer.utils.LevenshteinDistance;
import live.search.fixer.utils.Patch;
import live.search.fixer.utils.PatchGenerator;
import live.search.fixer.utils.TimeLine;
import live.search.space.MethodCandidate;
import live.search.space.SearchSpace;
import live.search.space.SearchSpaceBuilder;
import live.search.space.SecondHandCandidates;

/**
 * Try to fix bugs with signature-similar methods.
 */
public class SigSimFixer {
	
	private static Logger log = LoggerFactory.getLogger(SigSimFixer.class);
	
	private TimeLine timeLine;
	
	public SearchSpace searchSpace = null;
	private List<SuspiciousCode> suspiciousCandidates;
	private String defects4jPath;
	private String buggyProjectsPath;
	private String buggyProject;
	private List<String> failedTestStrList = new ArrayList<>();
	public int minErrorTest = 0;
	private int minErrorTestAfterFix = 0;
	public boolean isOneByOne = true; // Fine one similar method, then test this one.
	public boolean withoutPriority = false;// Without the priority of similar method candidates.
	private Map<String, List<Method>> triedSuspiciousMethods = new HashMap<>();
	
	public static void main(String[] args) {
		String buggyProjectsPath = args[0];//"../Defects4jBugs/";
		String defects4jPath = args[1];// "../defects4j/";
		String buggyProject = args[2]; // Chart_1
		String searchPath = args[3];   // "../data/existingMethods/";
		String metricStr = args[4];    // Zoltar
		boolean readSearchSpace = Boolean.valueOf(args[8]);
		
		SigSimFixer main = new SigSimFixer();
		main.isOneByOne = Boolean.valueOf(args[5]);
		main.withoutPriority = Boolean.valueOf(args[6]);
		int expire = Integer.valueOf(args[7]);
		main.fixProcess(buggyProjectsPath, defects4jPath, buggyProject, searchPath, metricStr, expire, readSearchSpace);
	}

	public void fixProcess(String buggyProjectsPath, String defects4jPath, String buggyProject, String searchPath, String metricStr, int expire, boolean doesReadSearchSpace) {
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
		
		// Build search space of methods similar to suspicious methods.
		if (doesReadSearchSpace) {
			this.searchSpace = new SearchSpaceBuilder().build(doesReadSearchSpace, searchPath);
		}
		if (this.searchSpace == null) {
			log.error("Failed to build the search space!!!");
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
		List<String> triedSuspiciousMethods = new ArrayList<>();
		List<SecondHandCandidates> scCandidates = new ArrayList<>();// Similar method candidates: Same return type and parameter types, different method name.
		boolean fixed = false;
		for (int index = 0; index < size; index ++) {
			if (timeLine.isTimeout()) break;
			SuspiciousCode suspiciousCode = this.suspiciousCandidates.get(index);
			String suspiciousClassName = suspiciousCode.getClassName();
			int lineNumber = suspiciousCode.getLineNumber();
			
			String suspiciousJavaFile = suspiciousClassName.replace(".", "/") + ".java";
			String filePath = dp.srcPath + suspiciousJavaFile;
			
			// Check whether this method has been tried or not?
			List<Method> similarTriedMethods = this.triedSuspiciousMethods.get(suspiciousJavaFile);
			if (similarTriedMethods != null) {
				boolean tried = false;
				for (Method triedMethod : similarTriedMethods) {
					if (lineNumber < triedMethod.getStartLine()) {
						break;
					} else if (lineNumber <= triedMethod.getEndLine()) {
						tried = true;
						break;
					}
				}
				if (tried) continue;
			}
			
			// Read the information of suspicious method that contains the suspicious code.
			JavaFileParser jfp = new JavaFileParser();
			jfp.parseSuspiciousJavaFile(buggyProject, new File(filePath), lineNumber);
			List<Method> suspiciousMethods = jfp.getMethods();
			List<Method> suspiciousConstructors = jfp.getConstructors();
			if (suspiciousMethods.isEmpty() && suspiciousConstructors.isEmpty()) {
				log.error("Failed to read the buggy method of " + buggyProject + " " + suspiciousClassName + "  " + lineNumber);
				continue;
			}
			Method method = suspiciousMethods.isEmpty() ? suspiciousConstructors.get(0) : suspiciousMethods.get(0);
			String returnType = method.getReturnTypeString();
			String arguments = method.getArgumentsStr();
			String suspiciousMethodName = method.getName();
			returnType = TypeReader.canonicalType(returnType);//FIXME using exact the same return type and argument types to further reduce the search space.
			String argumentTypes = TypeReader.readArgumentTypes(arguments);
			String keySignature = returnType + "#" + argumentTypes;
			String suspiciousMethodSignature = buggyProject + "#" + suspiciousJavaFile + "#" + suspiciousMethodName + "#" + keySignature;
			if (triedSuspiciousMethods.contains(suspiciousMethodSignature)) continue;
			triedSuspiciousMethods.add(suspiciousMethodSignature);
			method.setSignature(suspiciousMethodSignature);
			
			// Add this method to the set of tried methods.
			if (similarTriedMethods != null) {
				similarTriedMethods.add(method);
				Collections.sort(similarTriedMethods, new Comparator<Method>() {
					@Override
					public int compare(Method m1, Method m2) {
						return m1.getStartLine() < m2.getStartLine() ? -1 : 1;
					}
				});
			} else {
				similarTriedMethods = new ArrayList<>();
				similarTriedMethods.add(method);
			}
			this.triedSuspiciousMethods.put(suspiciousJavaFile, similarTriedMethods);
			
			// Search similar methods.
			String packageName = suspiciousClassName;
			List<String> buggyBodyCodeRawTokens = Arrays.asList(method.getBodyCodeRawTokens().split(" "));
			List<MethodCandidate> subSearchSpace = this.searchSpace.searchSpace.get(returnType);
			if (subSearchSpace == null) continue;

			File targetJavaFile = new File(FileUtils.getFileAddressOfJava(dp.srcPath, suspiciousClassName));
	        File targetClassFile = new File(FileUtils.getFileAddressOfClass(dp.classPath, suspiciousClassName));
	        File javaBackup = new File(FileUtils.tempJavaPath(suspiciousClassName, "Fixer"));
	        File classBackup = new File(FileUtils.tempClassPath(suspiciousClassName, "Fixer"));
	        FileHelper.outputToFile(javaBackup, FileHelper.readFile(targetJavaFile), false);
	        if (targetClassFile.exists()) {
	        	FileHelper.outputToFile(classBackup, FileHelper.readFile(targetClassFile), false);
	        }
			String suspiciousMethodBodyCode = method.getBody();
	        int startPos = method.getStartPosition();
	        int endPos = method.getEndPosition();
			
	        List<MethodCandidate> similarMethodsEMS = new ArrayList<>();
	        List<MethodCandidate> similarMethodsEMSWN = new ArrayList<>();
	        
			for (int i = 0, s = subSearchSpace.size(); i < s; i ++) {
				MethodCandidate ssMethod = subSearchSpace.get(i);
				if (keySignature.equals(ssMethod.signature)) {
					if (suspiciousMethodName.equals(ssMethod.methodName)) {// SameSignature: the same method signature.
						String existingMethodInfo = ssMethod.info;
						String[] elements = existingMethodInfo.split(":");
						String packageName_ = elements[1] + "." + elements[2];
						if (packageName.equals(packageName_)) continue;
						
						int levenshteinDistance = new LevenshteinDistance().computeLevenshteinDistance(buggyBodyCodeRawTokens, ssMethod.rawTokens);
//						if (levenshteinDistance == 0) continue;
						ssMethod.levenshteinDistance = levenshteinDistance;
						
						// Fix suspicious method with this similar method.
						if (isOneByOne) {
							fixed = fixSuspiciousMethod(suspiciousMethodBodyCode, ssMethod, targetJavaFile,
									javaBackup, suspiciousClassName, startPos, endPos, targetClassFile, dp, 
									"SameSignature", suspiciousMethodName, suspiciousMethodSignature, ssMethod.info, i);
							if (fixed) {
								break;
							}
						} else {
							similarMethodsEMS.add(ssMethod);
						}
					} else { // Similar Signature: Same return type and parameter types, different method name.
						int levenshteinDistance = new LevenshteinDistance().computeLevenshteinDistance(buggyBodyCodeRawTokens, ssMethod.rawTokens);
//						if (levenshteinDistance == 0) continue;
						ssMethod.levenshteinDistance = levenshteinDistance;
						
						if (isOneByOne && withoutPriority) {
							fixed = fixSuspiciousMethod(suspiciousMethodBodyCode, ssMethod, targetJavaFile,
									javaBackup, suspiciousClassName, startPos, endPos, targetClassFile, dp, 
									"SimilarSignature", suspiciousMethodName, suspiciousMethodSignature, ssMethod.info, i);
							if (fixed) break;
						} else {
							similarMethodsEMSWN.add(ssMethod);
						}
					}
				}
			}
			
			if (!isOneByOne) {
				if (similarMethodsEMS.isEmpty()) continue;
				Collections.sort(similarMethodsEMS, new Comparator<MethodCandidate>() {
					@Override
					public int compare(MethodCandidate m1, MethodCandidate m2) {
						return Integer.compare(m1.levenshteinDistance, m2.levenshteinDistance);
					}
				});
				for (int i = 0, s = similarMethodsEMS.size(); i < s; i ++) {
					if (timeLine.isTimeout()) break;
						MethodCandidate similarMethod = similarMethodsEMS.get(i);
					fixed = fixSuspiciousMethod(suspiciousMethodBodyCode, similarMethod, targetJavaFile,
							javaBackup, suspiciousClassName, startPos, endPos, targetClassFile, dp, 
							"SameSignature", suspiciousMethodName, suspiciousMethodSignature, similarMethod.info, i);
					if (fixed) break;
				}
			}
			
			FileHelper.outputToFile(targetJavaFile, FileHelper.readFile(javaBackup), false);
			if (classBackup.exists()) {
				FileHelper.outputToFile(targetClassFile, FileHelper.readFile(classBackup), false);
			}
			if (fixed) break;
		    
			if (!similarMethodsEMSWN.isEmpty()) {
				SecondHandCandidates scc = new SecondHandCandidates();
				scc.suspiciousCode = suspiciousCode;
				scc.suspiciousMethod = method;
				scc.methodCandidates = similarMethodsEMSWN;
				scCandidates.add(scc);
			}
		}
		
		if (!fixed && !withoutPriority) {//method candidates with SimilarSignature
			for (SecondHandCandidates scc : scCandidates) {
				if (timeLine.isTimeout()) break;
				SuspiciousCode sc = scc.suspiciousCode;
				Method suspiciousMethod = scc.suspiciousMethod;
				List<MethodCandidate> searchSpaceMethods = scc.methodCandidates;
				if (searchSpaceMethods == null || searchSpaceMethods.isEmpty()) continue;
				Collections.sort(searchSpaceMethods, new Comparator<MethodCandidate>() {
					@Override
					public int compare(MethodCandidate m1, MethodCandidate m2) {
						return Integer.compare(m1.levenshteinDistance, m2.levenshteinDistance);
					}
				});
				
				String suspiciousClassName = sc.getClassName();
				
				File targetJavaFile = new File(FileUtils.getFileAddressOfJava(dp.srcPath, suspiciousClassName));
		        File targetClassFile = new File(FileUtils.getFileAddressOfClass(dp.classPath, suspiciousClassName));
		        File javaBackup = new File(FileUtils.tempJavaPath(suspiciousClassName, "Fixer"));
		        File classBackup = new File(FileUtils.tempClassPath(suspiciousClassName, "Fixer"));
		        FileHelper.outputToFile(javaBackup, FileHelper.readFile(targetJavaFile), false);
		        if (targetClassFile.exists()) {
		        	FileHelper.outputToFile(classBackup, FileHelper.readFile(targetClassFile), false);
		        }
				String suspiciousMethodBodyCode = suspiciousMethod.getBody();
		        int startPos = suspiciousMethod.getStartPosition();
		        int endPos = suspiciousMethod.getEndPosition();
				
				for (int i = 0, s = searchSpaceMethods.size(); i < s; i ++) {
					if (timeLine.isTimeout()) break;
					MethodCandidate ssMethod = searchSpaceMethods.get(i);
					fixed = fixSuspiciousMethod(suspiciousMethodBodyCode, ssMethod, targetJavaFile,
							javaBackup, suspiciousClassName, startPos, endPos, targetClassFile, dp, 
							"SimilarSignature", suspiciousMethod.getName(), suspiciousMethod.getSignature(), ssMethod.info, i);
					if (fixed) break;
				}
				
				FileHelper.outputToFile(targetJavaFile, FileHelper.readFile(javaBackup), false);
				if (classBackup.exists()) {
					FileHelper.outputToFile(targetClassFile, FileHelper.readFile(classBackup), false);
				}
				
				if (fixed) break;
			}
		}
	}

	/**
	 * Testing the patched method.
	 * 
	 * @param suspiciousMethodBodyCode
	 * @param similarMethod
	 * @param targetJavaFile
	 * @param javaBackup
	 * @param suspiciousClassName
	 * @param startPos
	 * @param endPos
	 * @param targetClassFile
	 * @param dp
	 * @param type
	 * @param methodName
	 * @param suspiciousMethodInfo
	 * @param similarMethodInfo
	 * @param index
	 * @return
	 */
	private boolean fixSuspiciousMethod(String suspiciousMethodBodyCode, MethodCandidate similarMethod, File targetJavaFile,
			File javaBackup, String suspiciousClassName, int startPos, int endPos, File targetClassFile, DataPreparer dp, String type,
			String methodName, String suspiciousMethodInfo, String similarMethodInfo, int index) {
		
		if (timeLine.isTimeout()) return false;
		
		FileHelper.outputToFile(targetJavaFile, FileHelper.readFile(javaBackup), false);
		Patch patch = new PatchGenerator().generatePatch(suspiciousMethodBodyCode, suspiciousClassName, similarMethod.bodyCode);
        addCodeToFile(targetJavaFile, patch.patchMethodCode, startPos, endPos);// Insert the patch.
        
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
        log.info("Finish of compiling");
        if (!targetClassFile.exists()) { // fail to compile
            System.err.println(buggyProject + " ---Fixer: fix fail because of compile fail! ");
            return false;
        }
        
        List<String> failedTestsAfterFix = new ArrayList<>();
        log.info("======Begin to test patch======");
		int errorTestAfterFix = TestUtils.getFailTestNumInProject(this.buggyProjectsPath + this.buggyProject, this.defects4jPath, failedTestsAfterFix);
        log.info("======Finilize testing patch======");
		if (errorTestAfterFix < minErrorTest) {
			if (errorTestAfterFix == 0) {
				log.info("Succeeded to fix the bug " + buggyProject + "====================" + type);
				FileHelper.outputToFile(type + "/FixedBugs/" + buggyProject + "/buggyMethod_" + methodName + "_" + startPos + "_" + index + ".txt", 
						"//**********************************************************\n//  " + suspiciousMethodInfo + 
						"\n//**********************************************************\n" + suspiciousMethodBodyCode, false);
				FileHelper.outputToFile(type + "/FixedBugs/" + buggyProject + "/patchMethod_" + methodName + "_" + startPos + "_" + index + ".txt",
						"//**********************************************************\n//  " + similarMethodInfo + 
						"\n//**********************************************************\n" + patch.patchMethodCode, false);
				return true;
			} else {
				failedTestsAfterFix.removeAll(this.failedTestStrList);
				if (failedTestsAfterFix.size() > 0) {
					System.err.println(buggyProject + " ---Generated " + failedTestsAfterFix.size() + " new bugs.");
					return false;
				}
				if (minErrorTestAfterFix == 0 || errorTestAfterFix < minErrorTestAfterFix) {
					minErrorTestAfterFix = errorTestAfterFix;
					log.info("Partially Succeeded to fix the bug " + buggyProject + "====================" + type);
					FileHelper.outputToFile(type + "/PartialFixedBugs/" + buggyProject + "/buggyMethod_" + methodName + "_" + startPos + "_" + index + ".txt", 
							"//**********************************************************\n//  " + suspiciousMethodInfo + 
							"\n//**********************************************************\n" + suspiciousMethodBodyCode, false);
					FileHelper.outputToFile(type + "/PartialFixedBugs/" + buggyProject + "/patchMethod_" + methodName + "_" + startPos + "_" + index + ".txt",
							"//**********************************************************\n//  " + similarMethodInfo + 
							"\n//**********************************************************\n" + patch.patchMethodCode, false);
				}
			}
		}
		return false;
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

}
