package method.body.feature.learning;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import moethod.body.feature.learning.utils.FileHelper;

public class ReadMethodBodyCode {

	public static void main(String[] args) throws IOException {
		String inputPath = args[0];//../data/existingMethods_1/
		String outputPath = args[1];// ../data/SyntaticMethods/DL/
		ReadMethodBodyCode reader = new ReadMethodBodyCode();
		reader.exportSelectedMethodBodyCode(inputPath, outputPath);
	}
	
	public void exportSelectedMethodBodyCode(String inputPath, String outputPath) throws IOException {
		File bodyCodeFile = new File(inputPath + "MethodBodyCode.txt");
		File tokenVectorSizesFile = new File(inputPath + "Sizes.csv");
		String symbolStr = "#METHOD_BODY#========================";
		if (!bodyCodeFile.exists() || !tokenVectorSizesFile.exists()) {
			return;
		}
		
		File outputFile = new File(outputPath);
		int maxSize = 0;
		if (outputFile.exists()) {
			File[] files = new File(outputPath).listFiles();
			for (File file : files) {
				String fileName = file.getName();
				if (fileName.startsWith("MethodBodyTokens_MaxSize=") && fileName.endsWith(".txt")) {
					maxSize = Integer.valueOf(fileName.substring(fileName.indexOf("_MaxSize=") + 9, fileName.length() - 4));
				}
			}
		}
		if (maxSize == 0) return;
		
		List<Integer> selectedIndexes = readSizes(tokenVectorSizesFile, maxSize);
		
		
		//Read selected method body code.
		String outputFileName = outputPath + "MethodBodyCode_MaxSize=" + maxSize + ".txt";
		FileHelper.deleteFile(outputFileName);
		
		FileInputStream fis = new FileInputStream(bodyCodeFile);
		Scanner scanner = new Scanner(fis);
		
		StringBuilder fragmentsBuilder = new StringBuilder();
		int index = -1;
		int counter = 0;
		StringBuilder singleCodeFragment = new StringBuilder();
		while (scanner.hasNextLine()) {
			String line = scanner.nextLine();
			if (line.equals(symbolStr)) {
				if (singleCodeFragment.length() > 0) {
					if (selectedIndexes.contains(index)) {
						counter ++;
						fragmentsBuilder.append(singleCodeFragment);
						
						if (counter % 10000 == 0) {
							FileHelper.outputToFile(outputFileName, fragmentsBuilder, true);
							fragmentsBuilder.setLength(0);
						}
					}
				}
				index ++;
				singleCodeFragment.setLength(0);
			}
			singleCodeFragment.append(line).append("\n");
		}
		
		scanner.close();
		fis.close();
		
		if (selectedIndexes.contains(index)) {
			counter ++;
			fragmentsBuilder.append(singleCodeFragment);
		}
		
		if (fragmentsBuilder.length() > 0) {
			FileHelper.outputToFile(outputFileName, fragmentsBuilder, true);
			fragmentsBuilder.setLength(0);
		}
	}

	private List<Integer> readSizes(File tokenVectorSizesFile, int maxSize) throws IOException {
		List<Integer> indexes = new ArrayList<>();
		FileReader fileReader = new FileReader(tokenVectorSizesFile);
		BufferedReader reader = new BufferedReader(fileReader);
		String line = null;
		int index = -1;
		while ((line = reader.readLine()) != null) {
			index ++;
			int size = Integer.valueOf(line);
			if (size <= maxSize) {
				indexes.add(index);
			}
		}
		reader.close();
		fileReader.close();
		return indexes;
	}

}
