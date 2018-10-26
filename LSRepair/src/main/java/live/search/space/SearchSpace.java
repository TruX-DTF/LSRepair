package live.search.space;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * Search space of methods with similar signature.
 */
public class SearchSpace implements Serializable {

	private static final long serialVersionUID = 20180331L;
	
	private String searchPath;
	public Map<String, List<MethodCandidate>> searchSpace = new HashMap<>();
	
	public SearchSpace(String searchPath) throws IOException {
		this.searchPath = searchPath;
		build();
	}
	
//	public SearchSpace(String searchPath, String type) throws IOException {
//		this.searchPath = searchPath;
//		build(type);
//	}

	private void build() throws IOException {
		File[] returnTypeFiles = new File(this.searchPath).listFiles();
		for (File returnTypeFile : returnTypeFiles) {
			if (returnTypeFile.isDirectory()) {
				File signaturesFile = new File(returnTypeFile.getPath() + "/MethodSignature.txt");
				File rawTokensFile = new File(returnTypeFile.getPath() + "/MethodBodyRawTokens.txt");
				File bodyCodeFile = new File(returnTypeFile.getPath() + "/MethodBodyCode.txt");
				if (signaturesFile.exists()) {
					List<String> methodNamesList = new ArrayList<>();
					List<String> signaturesList = readSingleLineData(signaturesFile, methodNamesList);
					List<String> rawTokensList = readSingleLineData(rawTokensFile, null);
					List<String> methodInfoList = new ArrayList<>();
					List<String> methodBodiesList = readBodyCode(bodyCodeFile, methodInfoList);
					
					int size = signaturesList.size();
					
					List<MethodCandidate> methods = new ArrayList<>();
					for (int index = 0; index < size; index ++) {
						MethodCandidate method = new MethodCandidate();
						method.methodName = methodNamesList.get(index);
						method.signature = signaturesList.get(index);
						method.rawTokens = Arrays.asList(rawTokensList.get(index).split(" "));
						method.info = methodInfoList.get(index);
						method.bodyCode = methodBodiesList.get(index);
						methods.add(method);
					}
					this.searchSpace.put(returnTypeFile.getName(), methods);
				}
			}
		}
	}

//	private void build(String type) throws IOException {
//		File[] suspiciousMethods = new File(this.searchPath + type).listFiles();
//		for (File suspiciousMethod : suspiciousMethods) {
//			if (suspiciousMethod.isDirectory()) {
//				File bodyCodeFile = new File(suspiciousMethod.getPath() + "/MethodBodyCode.txt");
//				if (bodyCodeFile.exists()) {
//					List<String> methodNamesList = new ArrayList<>();
//					List<String> methodInfoList = new ArrayList<>();
//					List<String> methodBodiesList = readBodyCode(bodyCodeFile, methodInfoList);
//					
//					int size = methodBodiesList.size();
//					
//					List<MethodCandidate> methods = new ArrayList<>();
//					for (int index = 0; index < size; index ++) {
//						MethodCandidate method = new MethodCandidate();
//						method.methodName = methodNamesList.get(index);
//						method.info = methodInfoList.get(index);
//						method.bodyCode = methodBodiesList.get(index);
//						methods.add(method);
//					}
//					this.searchSpace.put(suspiciousMethod.getName(), methods);
//				}
//			}
//		}
//	}

	private List<String> readSingleLineData(File file, List<String> methodNamesList) throws IOException {
		List<String> dataList = new ArrayList<>();
		FileReader fileReader = new FileReader(file);
		BufferedReader buffReader = new BufferedReader(fileReader);
		String line = null;
		if (methodNamesList == null) {
			while ((line = buffReader.readLine()) != null) {
				dataList.add(line);
			}
		} else {
			while ((line = buffReader.readLine()) != null) {
				int hashtagIndex = line.indexOf("#");
				String methodName = line.substring(0, hashtagIndex);
				methodNamesList.add(methodName);
				dataList.add(line.substring(hashtagIndex + 1));
			}
		}
		buffReader.close();
		fileReader.close();
		return dataList;
	}
	
	private List<String> readBodyCode(File dataFile, List<String> methodInfoList) throws IOException {
		List<String> methodBodiesList = new ArrayList<>();
		FileInputStream fis = new FileInputStream(dataFile);
		Scanner scanner = new Scanner(fis);
		StringBuilder singleMethod = new StringBuilder();
		boolean isMethodBody = false;
		
		while (scanner.hasNextLine()) {
			String line = scanner.nextLine();
			if ("#METHOD_BODY#========================".equals(line)) {
				if (isMethodBody) methodBodiesList.add(singleMethod.toString());
				singleMethod.setLength(0);
				isMethodBody = false;
			} else {
				if (isMethodBody) {
					singleMethod.append(line).append("\n");
				}
				else {
					isMethodBody = true;
					methodInfoList.add(line);
				}
			}
		}
		methodBodiesList.add(singleMethod.toString());
		scanner.close();
		fis.close();
		return methodBodiesList;
	}
	
}
