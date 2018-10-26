package data.suspicious.methods;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
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

/**
 * Prepare suspicious methods for searching syntactic- and semantic- similar methods.
 * syntactic- : method signature and method body tokens.
 * semantic_  : method signature and method body code.
 */
public class SuspiciousMethod {

	private static Logger log = LoggerFactory.getLogger(SuspiciousMethod.class);
	
	private String positionsFile = "../SuspiciousCodePositions/";
	private String outputPath1 = "../data/Syntactic/";
	private String outputPath2 = "../data/Semantic/";
	
	public static void main(String[] args) {
		SuspiciousMethod suspMethod = new SuspiciousMethod();
		suspMethod.read();
	}

	public void read() {
		StringBuilder methodSignatures = new StringBuilder();
		StringBuilder methodCodeTokens = new StringBuilder();
		File[] positionFiles = new File(positionsFile).listFiles();
		for (File positionFile : positionFiles) {
			String buggyProject = positionFile.getName();
			buggyProject = buggyProject.substring(0, buggyProject.length() - 4);
			Map<String, List<Method>> parsedSuspiciousMethods = new HashMap<>();
			try {
				FileReader fileReader = new FileReader(positionFile);
				BufferedReader bufReader = new BufferedReader(fileReader);
				String position = null;
				
				while ((position = bufReader.readLine()) != null) {
					String[] elements = position.split("@");
					String path = elements[0];
					String suspFile = elements[1];
					int suspLine = Integer.parseInt(elements[2]);
					
					List<Method> parsedMethods = parsedSuspiciousMethods.get(suspFile);
					if (parsedMethods != null) {
						boolean parsed = false;
						for (Method parsedMethod : parsedMethods) {
							if (suspLine < parsedMethod.getStartLine()) {
								break;
							} else if (suspLine <= parsedMethod.getEndLine()) {
								parsed = true;
								break;
							}
						}
						if (parsed) continue;
					}
					
					JavaFileParser jfp = new JavaFileParser();
					jfp.parseSuspiciousJavaFile(buggyProject, new File(path + suspFile), suspLine);
					List<Method> suspiciousMethods = jfp.getMethods();
					List<Method> suspiciousConstructors = jfp.getConstructors();
					if (suspiciousMethods.isEmpty() && suspiciousConstructors.isEmpty()) {
						log.error("Failed to read the buggy method of " + buggyProject + " " + suspFile + "  " + suspLine);
						continue;
					}
					Method method = suspiciousMethods.isEmpty() ? suspiciousConstructors.get(0) : suspiciousMethods.get(0);
					String returnType = method.getReturnTypeString();
					String arguments = method.getArgumentsStr();
					String suspiciousMethodName = method.getName();
					returnType = TypeReader.canonicalType(returnType);
					String argumentTypes = TypeReader.readArgumentTypes(arguments);
					String keySignature = returnType + "#" + argumentTypes;
					String suspiciousMethodSignature = buggyProject + "#" + suspFile.replace("/", "#") + "#" + suspiciousMethodName + "#" + keySignature;
					
					// Add this method to the set of tried methods.
					if (parsedMethods != null) {
						parsedMethods.add(method);
						Collections.sort(parsedMethods, new Comparator<Method>() {
							@Override
							public int compare(Method m1, Method m2) {
								return m1.getStartLine() < m2.getStartLine() ? -1 : 1;
							}
						});
					} else {
						parsedMethods = new ArrayList<>();
						parsedMethods.add(method);
					}
					parsedSuspiciousMethods.put(suspFile, parsedMethods);
					
					methodSignatures.append(suspiciousMethodSignature).append("\n");
					methodCodeTokens.append(method.getBodyCodeTokens()).append("\n");
					FileHelper.outputToFile(outputPath2 + buggyProject + "/" + suspiciousMethodSignature + ".txt", method.getBody(), false);
				}
				bufReader.close();
				fileReader.close();
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		FileHelper.outputToFile(outputPath1 + "SuspiciousMethodSignatures.txt", methodSignatures, false);
		FileHelper.outputToFile(outputPath1 + "SuspiciousMethodCodeTokens.txt", methodCodeTokens, false);
	}
}
