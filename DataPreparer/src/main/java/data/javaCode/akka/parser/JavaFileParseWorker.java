package data.javaCode.akka.parser;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.japi.Creator;
import code.parser.JavaFileParser.JavaFileParser;
import code.parser.JavaFileParser.TypeReader;
import code.parser.method.Method;
import code.parser.utils.FileHelper;

public class JavaFileParseWorker extends UntypedActor {
	
	private static Logger log = LoggerFactory.getLogger(JavaFileParseWorker.class);
	
	private int numberOfWorkers;
	private String outputPath;
	
	public JavaFileParseWorker(String outputPath) {
		this.outputPath = outputPath;
	}
	
	public JavaFileParseWorker(String outputPath, int numberOfWorkers) {
		this.outputPath = outputPath;
		this.numberOfWorkers = numberOfWorkers;
	}

	public static Props props(final String outputPath) {
		return Props.create(new Creator<JavaFileParseWorker>() {

			private static final long serialVersionUID = -7615153844097275009L;

			@Override
			public JavaFileParseWorker create() throws Exception {
				return new JavaFileParseWorker(outputPath);
			}
			
		});
	}

	public static Props props(final String outputPath, final int numberOfWorkers) {
		return Props.create(new Creator<JavaFileParseWorker>() {

			private static final long serialVersionUID = -7615153844097275009L;

			@Override
			public JavaFileParseWorker create() throws Exception {
				return new JavaFileParseWorker(outputPath, numberOfWorkers);
			}
			
		});
	}

	@Override
	public void onReceive(Object message) throws Exception {
		if (message instanceof WorkerMessage) {
			WorkerMessage msg = (WorkerMessage) message;
			List<JavaFile> javaFiles = msg.getJavaFiles();
			int workerId = msg.getWorkderId();
			int numberOfMethods = 0;
			List<Method> allMethods = new ArrayList<>();
			List<Method> allConstructorMethods = new ArrayList<>();
			
			for (JavaFile javaFile : javaFiles) {
				String projectName = javaFile.getProjectName();
				File file = javaFile.getJavaFile();

				JavaFileParser parser = new JavaFileParser();
				parser.setIgnoreGetterAndSetterMethods(true);
				parser.parseJavaFile(projectName, file);
				List<Method> methods = parser.getMethods();
				if (!methods.isEmpty()) {
					allMethods.addAll(methods);
					if (allMethods.size() >= 1000) {
						numberOfMethods += allMethods.size();
						exportParsedMethods(allMethods, workerId);
						allMethods.clear();
					}
				}
				List<Method> constructorMethods = parser.getConstructors();
				if (!constructorMethods.isEmpty()) {
					allConstructorMethods.addAll(constructorMethods);
				}
			}
			
			if (allMethods.size() > 0) {
				numberOfMethods += allMethods.size();
				exportParsedMethods(allMethods, workerId);
				allMethods.clear();
			}
			if (!allConstructorMethods.isEmpty()) {
				exportParsedConstructorMethods(allConstructorMethods, workerId);
			}
			
			log.info("Worker #" + workerId +" Finish of parsing " + numberOfMethods + " methods of " + javaFiles.size() + " java files...");
			this.getSender().tell("SHUT_DOWN", getSelf());
		} else if (message instanceof List<?>) {
			List<?> msgList = (List<?>) message;
			for (Object obj : msgList) {
				if (obj instanceof File) {
					File returnTypeFile = (File) obj;
					if (returnTypeFile.getName().equals("void") && msgList.size() > 1) continue;
					if (returnTypeFile.isDirectory()) {
						mergeData(returnTypeFile);
					}
				}
			}
			
			this.getSender().tell("SHUT_DOWN", getSelf());
		} else {
			unhandled(message);
		}
	}

	/**
	 * Export parsed methods.
	 * 
	 * @param parsedMethods
	 * @param id
	 * @return
	 */
	private void exportParsedMethods(List<Method> parsedMethods, int id) {
//		String methodInfoFile = "/MethodInfo/MethodInfo_" + id + ".txt";
		String methodSignatureFile = "/MethodSignature/MethodSignature_" + id + ".txt";
		String methodBodyCodeFile = "/MethodBodyCode/MethodBodyCode_" + id + ".txt";
		String methodBodyCodeTokensFile = "/MethodBodyTokens/MethodBodyTokens_" + id + ".txt";
		String methodBodyCodeRawTokensFile = "/MethodBodyRawTokens/MethodBodyRawTokens_" + id + ".txt";
		
		Map<String, StringBuilder> signatureBuilders = new HashMap<>();
//		Map<String, StringBuilder> infoBuilders = new HashMap<>();
		Map<String, StringBuilder> bodyCodeBuilders = new HashMap<>();
		Map<String, StringBuilder> bodyCodeTokensBuilders = new HashMap<>();
		Map<String, StringBuilder> bodyCodeRawTokensBuilders = new HashMap<>();
		for (Method method : parsedMethods) {
			String methodKey = method.getKey();// projectName:packageName:className:methodName:parameters:returnType.
			String methodName = method.getName();
			String returnType = method.getReturnTypeString();
			returnType = TypeReader.canonicalType(returnType);
			String arguments = method.getArgumentsStr();
			String argumentTypes = TypeReader.readArgumentTypes(arguments); 
			
			StringBuilder signatureBuilder = signatureBuilders.get(returnType);
//			StringBuilder infoBuilder = infoBuilders.get(returnType);
			StringBuilder bodyCodeBuilder = bodyCodeBuilders.get(returnType);
			StringBuilder bodyCodeTokensBuilder = bodyCodeTokensBuilders.get(returnType);
			StringBuilder bodyCodeRawTokensBuilder = bodyCodeRawTokensBuilders.get(returnType);
			if (signatureBuilder == null) {
				signatureBuilder = new StringBuilder();
//				infoBuilder = new StringBuilder();
				bodyCodeBuilder = new StringBuilder();
				bodyCodeTokensBuilder = new StringBuilder();
				bodyCodeRawTokensBuilder = new StringBuilder();
				signatureBuilders.put(returnType, signatureBuilder);
//				infoBuilders.put(returnType, infoBuilder);
				bodyCodeBuilders.put(returnType, bodyCodeBuilder);
				bodyCodeTokensBuilders.put(returnType, bodyCodeTokensBuilder);
				bodyCodeRawTokensBuilders.put(returnType, bodyCodeRawTokensBuilder);
			}
			
//			infoBuilder.append(methodKey).append("\n");
			signatureBuilder.append(methodName).append("#").append(returnType).append("#").append(argumentTypes).append("\n");
			bodyCodeBuilder.append("#METHOD_BODY#========================\n")
				.append(methodKey).append("\n").append(method.getBody()).append("\n");
			if (methodKey.startsWith("Unidata_IDV:ucar.unidata.idv.control.chart:MyXYPlot:getDataRange")) {
				System.out.println(id + "\n"+ methodKey + "\n" + method.getBody());
			}
			bodyCodeTokensBuilder.append(method.getBodyCodeTokens()).append("\n");
			bodyCodeRawTokensBuilder.append(method.getBodyCodeRawTokens()).append("\n");
		}
		
		for (Map.Entry<String, StringBuilder> entry : signatureBuilders.entrySet()) {
			String returnType = entry.getKey();
			StringBuilder signatureBuilder = entry.getValue();
//			StringBuilder infoBuilder = infoBuilders.get(returnType);
			StringBuilder bodyCodeBuilder = bodyCodeBuilders.get(returnType);
			StringBuilder bodyCodeTokensBuilder = bodyCodeTokensBuilders.get(returnType);
			StringBuilder bodyCodeRawTokensBuilder = bodyCodeRawTokensBuilders.get(returnType);
//			FileHelper.outputToFile(outputPath + returnType + methodInfoFile, infoBuilder, true);
			FileHelper.outputToFile(outputPath + returnType + methodSignatureFile, signatureBuilder, true);
			FileHelper.outputToFile(outputPath + returnType + methodBodyCodeFile, bodyCodeBuilder, true);
			FileHelper.outputToFile(outputPath + returnType + methodBodyCodeTokensFile, bodyCodeTokensBuilder, true);
			FileHelper.outputToFile(outputPath + returnType + methodBodyCodeRawTokensFile, bodyCodeRawTokensBuilder, true);
		}
	}
	
	private void exportParsedConstructorMethods(List<Method> allConstructorMethods, int id) {
//		String methodInfoFile = "CONSTRUCTOR/MethodInfo/MethodInfo_" + id + ".txt";
		String methodSignatureFile = "CONSTRUCTOR/MethodSignature/MethodSignature_" + id + ".txt";
		String methodBodyCodeFile = "CONSTRUCTOR/MethodBodyCode/MethodBodyCode_" + id + ".txt";
		String methodBodyCodeTokensFile = "CONSTRUCTOR/MethodBodyTokens/MethodBodyTokens_" + id + ".txt";
		String methodBodyCodeRawTokensFile = "CONSTRUCTOR/MethodBodyRawTokens/MethodBodyRawTokens_" + id + ".txt";
		
		StringBuilder signatureBuilder = new StringBuilder();
//		StringBuilder infoBuilder = new StringBuilder();
		StringBuilder bodyCodeBuilder = new StringBuilder();
		StringBuilder bodyCodeTokensBuilder = new StringBuilder();
		StringBuilder bodyCodeRawTokensBuilder = new StringBuilder();
		for (Method method : allConstructorMethods) {
			String methodKey = method.getKey();// projectName:packageName:className:methodName:parameters:returnType.
			String methodName = method.getName();
			String arguments = method.getArgumentsStr();
			String argumentTypes = TypeReader.readArgumentTypes(arguments); 
			
//			infoBuilder.append(methodKey).append("\n");
			signatureBuilder.append(methodName).append("#=CONSTRUCTOR=#").append(argumentTypes).append("\n");
			bodyCodeBuilder.append("#METHOD_BODY#========================\n")
				.append(methodKey).append("\n").append(method.getBody()).append("\n");
			bodyCodeTokensBuilder.append(method.getBodyCodeTokens()).append("\n");
			bodyCodeRawTokensBuilder.append(method.getBodyCodeRawTokens()).append("\n");
		}
//		FileHelper.outputToFile(outputPath + methodInfoFile, infoBuilder, false);
		FileHelper.outputToFile(outputPath + methodSignatureFile, signatureBuilder, false);
		FileHelper.outputToFile(outputPath + methodBodyCodeFile, bodyCodeBuilder, false);
		FileHelper.outputToFile(outputPath + methodBodyCodeTokensFile, bodyCodeTokensBuilder, false);
		FileHelper.outputToFile(outputPath + methodBodyCodeRawTokensFile, bodyCodeRawTokensBuilder, false);
	}

	private void mergeData(File returnTypeFile) throws IOException {
		String path = returnTypeFile.getPath();
//		String methodInfoFileName = path + "/MethodInfo.txt";
		String methodSignatureFileName = path + "/MethodSignature.txt";
		String methodBodyCodeFileName = path + "/MethodBodyCode.txt";
		String methodBodyTokensFileName = path + "/MethodBodyTokens.txt";
		String methodBodyRawTokensFileName = path + "/MethodBodyRawTokens.txt";
		String methodBodyTokenVectorSizesFileName = path + "/Sizes.csv";
//		FileHelper.deleteFile(methodInfoFileName);
		FileHelper.deleteFile(methodSignatureFileName);
		FileHelper.deleteFile(methodBodyCodeFileName);
		FileHelper.deleteFile(methodBodyTokensFileName);
		FileHelper.deleteFile(methodBodyRawTokensFileName);
		FileHelper.deleteFile(methodBodyTokenVectorSizesFileName);
		
		for (int i = 1; i <= numberOfWorkers; i ++) {
			File tokensFile = new File(path + "/MethodBodyTokens/MethodBodyTokens_" + i + ".txt");
			if (tokensFile.exists()) {
				StringBuilder sizesBuilder = new StringBuilder();
				FileInputStream fis = new FileInputStream(tokensFile);
				Scanner scanner = new Scanner(fis);
				while (scanner.hasNextLine()) {
					String line = scanner.nextLine();
					int size = line.split(" ").length;
					sizesBuilder.append(size).append("\n");
				}
				scanner.close();
				fis.close();
				
				FileHelper.outputToFile(methodBodyTokensFileName, FileHelper.readFile(tokensFile), true);
				FileHelper.outputToFile(methodBodyTokenVectorSizesFileName, sizesBuilder, true);
//				FileHelper.outputToFile(methodInfoFileName, FileHelper.readFile(path + "/MethodInfo/MethodInfo_" + i + ".txt"), true);
				FileHelper.outputToFile(methodSignatureFileName, FileHelper.readFile(path + "/MethodSignature/MethodSignature_" + i + ".txt"), true);
				FileHelper.outputToFile(methodBodyRawTokensFileName, FileHelper.readFile(path + "/MethodBodyRawTokens/MethodBodyRawTokens_" + i + ".txt"), true);
				FileHelper.outputToFile(methodBodyCodeFileName, FileHelper.readFile(path + "/MethodBodyCode/MethodBodyCode_" + i + ".txt"), true);
			}
		}
//		FileHelper.deleteDirectory(dataPath + "/");
	}

}
