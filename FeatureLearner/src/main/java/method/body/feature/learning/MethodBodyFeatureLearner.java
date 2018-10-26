package method.body.feature.learning;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import method.body.feature.learning.models.CNNFeatureExtractor;
import method.body.feature.learning.models.Word2VecEncoder;
import moethod.body.feature.learning.utils.DataSelector;
import moethod.body.feature.learning.utils.DataVectorizer;
import moethod.body.feature.learning.utils.Distribution.MaxSizeType;
import moethod.body.feature.learning.utils.FileHelper;

/**
 * Embed code tokens of method bodies with Word2Vec.
 * Convert method bodies into two-dimensional numeric vectors.
 * Learn features of method bodies with CNN.
 */
public class MethodBodyFeatureLearner {
	
	private static Logger log = LoggerFactory.getLogger(MethodBodyFeatureLearner.class);
	
	int maxSize = 0;

	public void learnFeature(String inputPath, String outputPath, String dlInputPath, int batchSize, int sizeOfEmbeddedVector, int nEpochs, String testingTokensFile) {
		//Select data.
		File inputFile = new File(inputPath);
		if (!inputFile.exists() || !inputFile.isDirectory()) {
			return;
		}
		File tokenVectorSizesFile = new File(inputPath + "Sizes.csv");
		File tokenVectorsFile = new File(inputPath + "MethodBodyTokens.txt");
		File bodyCodeFile = new File(inputPath + "MethodBodyCode.txt");
		@SuppressWarnings("unused")
		String symbolStr = "#METHOD_BODY#========================";
		if (!tokenVectorSizesFile.exists() || !tokenVectorsFile.exists() || !bodyCodeFile.exists()) {
			return;
		}
		
		DataSelector dataSelector = new DataSelector(tokenVectorSizesFile, tokenVectorsFile, bodyCodeFile, MaxSizeType.UpperWhisker);
		dataSelector.tokenVectorsOutputFileName = outputPath + "MethodBodyTokens";
		dataSelector.tokenVectorSizesOutputFileName = outputPath + "Sizes";
		dataSelector.fragmentsOutputFileName = outputPath + "MethodBodyCode";
		dataSelector.setMinSze(0);    //TODO
//		dataSelector.setMaxSize(100); //TODO
		dataSelector.setOddRangeNumber(false);
		
		File selectedTokenVectorsFile = null;
		File outputFile = new File(outputPath);
		boolean dataExisting = false;
		if (outputFile.exists()) {
			File[] files = new File(outputPath).listFiles();
			for (File file : files) {
				String fileName = file.getName();
				if (fileName.startsWith("MethodBodyTokens_MaxSize=") && fileName.endsWith(".txt")) {
					dataExisting = true;
					maxSize = Integer.valueOf(fileName.substring(fileName.indexOf("_MaxSize=") + 9, fileName.length() - 4));
					dataSelector.setMaxSize(maxSize);
					selectedTokenVectorsFile = file;
					break;
				}
			}
		}
		
		if (!dataExisting) {
			try {
				log.info("====Data Selection====");
				dataSelector.selectData();
//				dataSelector.readSelectedCodeFragments(symbolStr);
				selectedTokenVectorsFile = new File(outputPath + "MethodBodyTokens_MaxSize=" + dataSelector.maxSize + ".txt");
				log.info("====Data Selection Done====");
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		
		// Embed tokens.
		String embeddedTokensFile = outputPath + "DLinput/EmbeddedTokens.txt";
		if (!(new File(embeddedTokensFile).exists())) {
			log.info("====Token Embedding====");
			Word2VecEncoder encoder = new Word2VecEncoder();
			int windowSize = 4;
			encoder.setWindowSize(windowSize);
			try {
				int minWordFrequency = 1;
				
				encoder.embedTokens(selectedTokenVectorsFile, minWordFrequency, sizeOfEmbeddedVector, embeddedTokensFile);
			} catch (IOException e) {
				e.printStackTrace();
			}
			log.info("====Token Embedding Done====");
		}
		
		
		// Vectorize tokens.
		DataVectorizer vectorizer = new DataVectorizer(sizeOfEmbeddedVector);
		int maxSize = dataSelector.maxSize;
		File dlInputFile = new File(dlInputPath + "DLinput/DLinput.csv");
		if (!dlInputFile.exists()) {
			try {
				log.info("====Token Vectorization====");
				vectorizer.vectorizeData(new File(embeddedTokensFile), selectedTokenVectorsFile, maxSize, dlInputFile);
				log.info("====Token Vectorization Done====");
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		
		// Learn features.
		int sizeOfFeatureVector = 300;
		int sizeOfTokensVector = maxSize;
		String dlOutputPath = outputPath + "/DLoutput_" + nEpochs + "/";
		if (!(new File(dlOutputPath + "CNNoutput.zip").exists())) {
			FileHelper.deleteDirectory(dlOutputPath);
			log.info("====Feature Training====");
			CNNFeatureExtractor learner = new CNNFeatureExtractor(dlInputFile, sizeOfTokensVector, sizeOfEmbeddedVector, batchSize, sizeOfFeatureVector);
			learner.setNumberOfEpochs(nEpochs);
			learner.setSeed(123);
			learner.setNumOfOutOfLayer1(20);
			learner.setNumOfOutOfLayer2(50);
			learner.setOutputPath(dlOutputPath);
			try {
				learner.extracteFeaturesWithCNN();
				dlInputFile.delete();
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			log.info("====Feature Training Done====");
		}
		
		// Learning features with existing CNN model.
		try {
			File selectedTokenVectorsFile2 = selectedTestingTokens(maxSize, testingTokensFile, outputPath + "/DLoutput_" + nEpochs + "/");//
			File dlInputFile2 = new File(outputPath + "/DLoutput_" + nEpochs + "/Testinginput.csv");
			if (!dlInputFile2.exists()) {
				vectorizer.vectorizeData(new File(embeddedTokensFile), selectedTokenVectorsFile2, maxSize, dlInputFile2);
			}

			log.info("====Feature Learning====");
			CNNFeatureExtractor learner = new CNNFeatureExtractor(null, sizeOfTokensVector, sizeOfEmbeddedVector, batchSize, sizeOfFeatureVector);
			learner.setNumberOfEpochs(nEpochs);
			learner.setSeed(123);
			learner.setNumOfOutOfLayer1(20);
			learner.setNumOfOutOfLayer2(50);
			learner.setOutputPath(dlOutputPath);
			learner.setModelFile(new File(dlOutputPath + "CNNoutput.zip"));
			learner.setTestingData(dlInputFile2);
		
			learner.extracteFeaturesWithCNNByLoadingModel();
			log.info("====Feature Learning Done====");
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	private File selectedTestingTokens(int maxSize, String testingTokensFile, String path) throws IOException {
		FileReader fileReader = new FileReader(testingTokensFile);
		BufferedReader reader = new BufferedReader(fileReader);
		String line = null;
		int index = -1;
		StringBuilder tokens = new StringBuilder();
		StringBuilder indexes = new StringBuilder();
		while ((line = reader.readLine()) != null) {
			index ++;
			int length = line.split(" ").length;
			if (length <= maxSize) {
				tokens.append(line).append("\n");
				indexes.append(index).append("\n");
			}
		}
		reader.close();
		reader.close();
		
		File tokenFile = new File(path + "TestingTokens.txt");
		FileHelper.outputToFile(tokenFile, tokens, false);
		FileHelper.outputToFile(path + "TestingIndexes.txt", indexes, false);
		return tokenFile;
	}
	
}
