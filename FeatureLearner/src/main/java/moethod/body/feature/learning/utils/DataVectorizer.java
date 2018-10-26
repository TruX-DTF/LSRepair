package moethod.body.feature.learning.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Convert data into two-dimensional numeric vectors which further can be fed to deep learning algorithms.
 */
public class DataVectorizer {
	
	private static Logger log = LoggerFactory.getLogger(DataVectorizer.class);
	
	private int sizeOfEmbededVector = 300;
	private Map<String, String> embeddedTokens = new HashMap<>();
	
	public DataVectorizer(int sizeOfEmbededVector) {
		this.sizeOfEmbededVector = sizeOfEmbededVector;
	}
	
	/**
	 * Convert the textual training data into two-dimensional numeric vectors.
	 * 
	 * @param embeddedTokensFile, output of Word2Vec, e.g., parentPath + "embedding/embeddedTokens.txt";
	 * @param trainingDataDirectorytrainingDataDirectory, e.g, inputPath + "TrainingData/"  File Name: Tokens_MaxSize=#.txt
	 * @throws IOException
	 */
	public void vectorizeData(File embeddedTokensFile, String trainingDataDirectory, String symbolStr) throws IOException {
		readEmbeddedTokens(embeddedTokensFile);
		vectorizeTrainingData(trainingDataDirectory, symbolStr);
	}
	
	/**
	 * Convert the textual training data into two-dimensional numeric vectors.
	 * 
	 * @param embeddedTokensFile, output of Word2Vec, e.g., parentPath + "embedding/embeddedTokens.txt";
	 * @param trainingDataFile, e.g, inputPath + "TrainingData/Tokens_MaxSize=#.txt"
	 * @param maxSize, the max length of textual vectors of training data.
	 * @throws IOException
	 */
	public void vectorizeData(File embeddedTokensFile, File tokenVectorsFile, int maxSize, File outputFile) throws IOException {
		readEmbeddedTokens(embeddedTokensFile);
		vectorizeTokenVectors(tokenVectorsFile, maxSize, outputFile);
	}
	
	/**
	 * Read numeric vector representation of each word or token.
	 * 
	 * @param embeddedTokensFile, output of Word2Vec, e.g., parentPath + "embedding/embeddedTokens.txt";
	 * @throws IOException
	 */
	private void readEmbeddedTokens(File embeddedTokensFile) throws IOException {
		FileInputStream fis = new FileInputStream(embeddedTokensFile);;
		Scanner scanner = new Scanner(fis);
		while (scanner.hasNextLine()) {
			String line = scanner.nextLine();
			int firstBlankIndex = line.indexOf(" ");
			String token = line.substring(0, firstBlankIndex);
			String value = line.substring(firstBlankIndex + 1).replaceAll(" ", ",");
			embeddedTokens.put(token, value);
		}
		scanner.close();
		fis.close();
	}
	
	/**
	 * Convert the textual training data into two-dimensional numeric vectors.
	 * 
	 * @param trainingDataDirectory, e.g, inputPath + "TrainingData/", File Name: Tokens_MaxSize=#.txt"
	 * @throws IOException
	 */
	private void vectorizeTrainingData(String trainingDataDirectory, String symbolStr) throws IOException {
		File[] files = new File(trainingDataDirectory).listFiles();
		File trainingDataFile = null;
		int maxSize = 0;
		for (File file : files) {
			String fileName = file.getName();
			if (fileName.startsWith(symbolStr)) {
				maxSize = Integer.parseInt(fileName.substring(symbolStr.length(), fileName.lastIndexOf(".txt")));
				trainingDataFile = file;
			}
		}
		
		String outputFileName = trainingDataFile.getPath().replace(".txt", ".csv");
		FileHelper.deleteFile(outputFileName);
		vectorizeTokenVectors(trainingDataFile, maxSize, new File(outputFileName));
	}
	
	/**
	 * Convert the textual training data into two-dimensional numeric vectors.
	 * 
	 * @param tokenVectorsFile
	 * @param maxSize, the max length of textual vectors of training data.
	 * @throws IOException
	 */
	private void vectorizeTokenVectors(File tokenVectorsFile, int maxSize, File outputFile) throws IOException {
		StringBuilder zeroVector = new StringBuilder();
		int size = sizeOfEmbededVector - 1;
		for (int i = 0; i < size; i ++) {
			zeroVector.append("0,");
		}
		zeroVector.append("0");
		log.info("Max size of textual vectors of training data: " + maxSize);
		
		vectorizeTokenVector(tokenVectorsFile, maxSize, zeroVector, outputFile);
	}
	
	private void vectorizeTokenVector(File tokenVectorsFile, int maxSize, StringBuilder zeroVector, File outputFile) throws IOException {
		FileInputStream fis = new FileInputStream(tokenVectorsFile);
		Scanner scanner = new Scanner(fis);
		int vectorSize = 0;
		StringBuilder builder = new StringBuilder();
		int counter = 0;
		while (scanner.hasNextLine()) {
			String line = scanner.nextLine();
			List<String> methodBodyTokens = Arrays.asList(line.split(" "));
			SingleVectorizer vecBody = new SingleVectorizer();
			vecBody.vectorize(methodBodyTokens, embeddedTokens, maxSize, zeroVector);
			StringBuilder vectorizedTokenVector = vecBody.numericVector;
			int length = vectorizedTokenVector.toString().trim().split(",").length;
			if (length != vectorSize) {
				System.err.println(length);
				vectorSize = length;
			}
			builder.append(vectorizedTokenVector).append("\n");
			counter ++;
			if (counter % 1000 == 0) {
				FileHelper.outputToFile(outputFile, builder, true);
				builder.setLength(0);
			}
		}
		scanner.close();
		fis.close();
		
		FileHelper.outputToFile(outputFile, builder, true);
		builder.setLength(0);
	}
	
	/**
	 * Single vectorizer of a single token vector.
	 */
	private class SingleVectorizer {
		private StringBuilder numericVector = new StringBuilder();
		
		/**
		 * Append symbol "," in each iteration.
		 * 
		 * @param tokenVector
		 * @param embeddedTokens
		 * @param maxSize
		 * @param zeroVector
		 */
		public void vectorize(List<String> tokenVector, Map<String, String> embeddedTokens, int maxSize, StringBuilder zeroVector) {
			int i = 0;
			for (int size = tokenVector.size(); i < size; i ++) {
				String numericVectorOfSingleToken = embeddedTokens.get(tokenVector.get(i));
				if (numericVectorOfSingleToken == null) {
					numericVectorOfSingleToken = zeroVector.toString();
				}
				numericVector.append(numericVectorOfSingleToken);
				if (i < maxSize - 1) {
					numericVector.append(",");
				}
			}
			for (; i < maxSize; i ++) {
				numericVector.append(zeroVector);
				if (i < maxSize - 1) {
					numericVector.append(",");
				}
			}
		}
	}
}
