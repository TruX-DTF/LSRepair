package data.method.syntactic;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import code.parser.utils.FileHelper;
import data.method.syntactic.matcher.AkkaMatcher;

public class SyntacticSearchSpace {
	
	private ArrayList<String> initialSearchSpace;

	public static void main(String[] args) {
		
		// Match similar methods with CNNs-learned body code feature to obtain indexes.
		if (args.length != 5) {
			System.out.println("Arguments: <nEpochs>, <threshold>, <inputPath>, <numOfWorkers>, <outputPath>");
			System.exit(0);
		}
		
		int nEpochs = Integer.valueOf(args[0]);//1, 10, 20
		int topNum = Integer.valueOf(args[1]); //1000
		String inputPath = args[2]; // "../data/Syntactic/"
		int numberOfBodyFeatureWorkers = Integer.valueOf(args[3]);// 1000.
		String outputPath = args[4];// "../data/Syntactic/"
		AkkaMatcher akkaMatcher = new AkkaMatcher();
		akkaMatcher.match(nEpochs, topNum, inputPath, numberOfBodyFeatureWorkers, outputPath);
		
		
		// Create sub search space.
		SyntacticSearchSpace s3 = new SyntacticSearchSpace();
		s3.readInitialSearchSpace();
		
		File[] subSearchSpaces = new File(outputPath).listFiles();
		for (File subSearchSpace : subSearchSpaces) {
			if (subSearchSpace.isDirectory()) {
				String fileName = subSearchSpace.getName();
				if (fileName.startsWith("Signature_")) {
					String indexesFileName = subSearchSpace.getPath() + "/similarIndexes.txt";
					List<Integer> indexes = s3.readIndexes(indexesFileName);
					String subSearchSpaceFileName = subSearchSpace.getPath() + "/SearchSpace.txt";
					s3.createSubSearchSpace(indexes, subSearchSpaceFileName);
				}
			}
		}
	}

	private void createSubSearchSpace(List<Integer> indexes, String subSearchSpaceFileName) {
		StringBuilder builder = new StringBuilder();
		for (int index : indexes) {
			builder.append(this.initialSearchSpace.get(index)).append("\n");
		}
		FileHelper.outputToFile(subSearchSpaceFileName, builder, false);
	}

	@SuppressWarnings("unchecked")
	public void readInitialSearchSpace() {
		String searchSpace = "../data/Syntactic/SearchSpace.txt";
		File searchSpaceFile = new File("../data/Syntactic/SearchSpace.ss");
		try {
			if (searchSpaceFile.exists()) {
	            ObjectInputStream objectInputStream = new ObjectInputStream(new FileInputStream(searchSpaceFile));
	            Object object = objectInputStream.readObject();
	            if (object instanceof ArrayList<?>) {
	            	initialSearchSpace = (ArrayList<String>) object;
	            }
	            objectInputStream.close();
			} else {
				initialSearchSpace = readMethodInfo(searchSpace);
				File parentFile = searchSpaceFile.getParentFile();
				if (!parentFile.exists()) parentFile.mkdirs();
				searchSpaceFile.createNewFile();
				ObjectOutputStream objectOutputStream = new ObjectOutputStream(new FileOutputStream(searchSpaceFile));
				objectOutputStream.writeObject(initialSearchSpace);
				objectOutputStream.close();
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
	}

	private ArrayList<String> readMethodInfo(String fileName) throws IOException {
		ArrayList<String> dataList = new ArrayList<>();
		FileInputStream fis = new FileInputStream(fileName);
		Scanner scanner = new Scanner(fis);
		StringBuilder singleMethod = new StringBuilder();
		while (scanner.hasNextLine()) {
			String line = scanner.nextLine();
			if ("#METHOD_BODY#========================".equals(line)) {
				dataList.add(singleMethod.toString());
				singleMethod.setLength(0);
			}
			singleMethod.append(line).append("\n");
		}
		dataList.add(singleMethod.toString());
		scanner.close();
		fis.close();
		
		return dataList;
	}

	public List<Integer> readIndexes(String indexesFile) {
		List<Integer> indexes = new ArrayList<>();
		try {
			FileReader fileReader = new FileReader(indexesFile);
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
