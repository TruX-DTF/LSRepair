package method.body.feature.learning;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import method.body.feature.learning.models.CNNFeatureExtractor;
import moethod.body.feature.learning.utils.FileHelper;

public class Main {

	public static void main(String[] args) {
		String inputPath = args[0];    // ../data/existingMethods/
		String outputPath = args[1];   // ../data/Syntactic/DL/
		String dlInputPath = args[2];  // ../data/Syntactic/DL/
		int batchSize = Integer.valueOf(args[3]);// 1024.
		int sizeOfEmbeddedVector = Integer.valueOf(args[4]);// 100.
		int nEpochs = Integer.valueOf(args[5]); // 1
		String testingTokensFile = args[6]; // "../data/Syntactic/SuspiciousMethodCodeTokens.txt"; 
		
		String dlOutputPath = outputPath + "/DLoutput_" + nEpochs + "/";
		int maxSize = 0;
		if (!(new File(dlOutputPath + "CNNoutput.zip").exists())) {
			MethodBodyFeatureLearner learner = new MethodBodyFeatureLearner();
			learner.learnFeature(inputPath, outputPath, dlInputPath, batchSize, sizeOfEmbeddedVector, nEpochs, testingTokensFile);
			maxSize = learner.maxSize;
		} else {
			try {
				File dlInputFile2 = new File(dlOutputPath + "Testinginput.csv");
				if (dlInputFile2.exists()) {
					System.out.println(dlInputFile2);
					CNNFeatureExtractor learner = new CNNFeatureExtractor(null, 160, sizeOfEmbeddedVector, batchSize, 300);
					learner.setNumberOfEpochs(nEpochs);
					learner.setSeed(123);
					learner.setNumOfOutOfLayer1(20);
					learner.setNumOfOutOfLayer2(50);
					learner.setOutputPath(dlOutputPath);
					learner.setModelFile(new File(dlOutputPath + "CNNoutput.zip"));
					learner.setTestingData(dlInputFile2);
				
					learner.extracteFeaturesWithCNNByLoadingModel();
				}
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		
		// Read the selected suspicious methods.
		String selectedIndexesFile = outputPath + "/DLoutput_" + nEpochs + "/" + "TestingIndexes.txt";
		String suspiciousMethodSignatures = args[7]; // "../data/Syntactic/SuspiciousMethodSignatures.txt"; 
		List<Integer> indexes1 = readIndexes(selectedIndexesFile);
		try {
			FileReader fileReader = new FileReader(suspiciousMethodSignatures);
			BufferedReader bufReader = new BufferedReader(fileReader);
			String line = null;
			StringBuilder builder = new StringBuilder();
			int index = -1;
			while ((line = bufReader.readLine()) != null) {
				index ++;
				if (indexes1.contains(index)) {
					builder.append(line).append("\n");
				}
			}
			bufReader.close();
			fileReader.close();
			FileHelper.outputToFile(outputPath + "/SelectedSuspMethods/SuspiciousMethodSignatures.txt", builder, false);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		// Create the search space with the selected training methods.
		if (maxSize != 0) {
			List<Integer> indexes2 = readIndexes(inputPath + "Sizes.csv", maxSize);
			String outputFileName = "../data/Syntactic/SearchSpace.txt";
			String path1 = "../data/existingMethods/MethodBodyCode.txt";
			FileHelper.deleteFile(outputFileName);
			File searchSpaceFile = new File("../data/Syntactic/SearchSpace.ss");
			try {
				ArrayList<String> methodBodyCodelist = readMethodInfo(path1, indexes2, outputFileName);
				File parentFile = searchSpaceFile.getParentFile();
				if (!parentFile.exists()) parentFile.mkdirs();
				searchSpaceFile.createNewFile();
				ObjectOutputStream objectOutputStream = new ObjectOutputStream(new FileOutputStream(searchSpaceFile));
				objectOutputStream.writeObject(methodBodyCodelist);
				objectOutputStream.close();
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private static ArrayList<String> readMethodInfo(String fileName, List<Integer> indexes, String outputFileName) throws IOException {
		ArrayList<String> dataList = new ArrayList<>();
		FileInputStream fis = new FileInputStream(fileName);
		Scanner scanner = new Scanner(fis);
		StringBuilder singleMethod = new StringBuilder();
		StringBuilder builder = new StringBuilder();
		int index = -1;
		while (scanner.hasNextLine()) {
			String line = scanner.nextLine();
			if ("#METHOD_BODY#========================".equals(line)) {
				if (indexes.get(0) == index) {
					indexes.remove(0);
					dataList.add(singleMethod.toString());
					builder.append(singleMethod);
					if (index % 100000 == 0) {
						FileHelper.outputToFile(outputFileName, builder, true);
						builder.setLength(0);
						System.out.println(index);
					}
				}
				index ++;
				singleMethod.setLength(0);
			}
			singleMethod.append(line).append("\n");
		}
		dataList.add(singleMethod.toString());
		scanner.close();
		fis.close();
		
		if (indexes.contains(index)) {
			builder.append(singleMethod);
		}
		if (builder.length() > 0) {
			FileHelper.outputToFile(outputFileName, builder, true);
		}
		return dataList;
	}

	private static List<Integer> readIndexes(String fileName, int maxSize) {
		List<Integer> indexes = new ArrayList<>();
		try {
			FileReader fileReader = new FileReader(fileName);
			BufferedReader reader = new BufferedReader(fileReader);
			String line = null;
			int index = -1;
			while ((line = reader.readLine()) != null) {
				int size = Integer.valueOf(line);
				index ++;
				if (size <= maxSize) {
					indexes.add(index);
				}
			}
			System.out.println(index + 1);
			reader.close();
			fileReader.close();
		} catch (NumberFormatException | IOException e) {
			e.printStackTrace();
		}
		return indexes;
	}

	private static List<Integer> readIndexes(String selectedIndexesFile) {
		List<Integer> indexes = new ArrayList<>();
		try {
			FileReader fileReader = new FileReader(selectedIndexesFile);
			BufferedReader bufReader = new BufferedReader(fileReader);
			String line = null;
			while ((line = bufReader.readLine()) != null) {
				indexes.add(Integer.parseInt(line));
			}
			bufReader.close();
			fileReader.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return indexes;
	}

}
