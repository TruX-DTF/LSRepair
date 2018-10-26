package moethod.body.feature.learning.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import moethod.body.feature.learning.utils.Distribution.MaxSizeType;
import moethod.body.feature.learning.utils.Distribution.MinSizeType;

public class DataSelector {
	
	private File tokenVectorSizesFile;
	private File tokenVectorsFile;
	private File codeFragmentsFile;
	private MaxSizeType maxSizeType = MaxSizeType.UpperWhisker;
	private MinSizeType minSizeType = null;
	private boolean isOddRangeNumber = false;

	private List<Integer> tokenVectorSizes;
	private List<Integer> selectedTokenVectorIndexes;
	public int maxSize = -1;
	private int minSize = -1;

	public String tokenVectorsOutputFileName;
	public String tokenVectorSizesOutputFileName;
	public String fragmentsOutputFileName;
	
	public DataSelector(File tokenVectorSizesFile, File tokenVectorsFile, File codeFragmentsFile, MaxSizeType maxSizeType) {
		this.tokenVectorSizesFile = tokenVectorSizesFile;
		this.tokenVectorsFile = tokenVectorsFile;
		this.codeFragmentsFile = codeFragmentsFile;
		this.maxSizeType = maxSizeType;
	}
	
	public DataSelector(File tokenVectorSizesFile, File tokenVectorsFile, File codeFragmentsFile, MaxSizeType maxSizeType, MinSizeType minSizeType) {
		this(tokenVectorSizesFile, tokenVectorsFile, codeFragmentsFile, maxSizeType);
		this.minSizeType = minSizeType;
	}

	public void setOddRangeNumber(boolean isOddRangeNumber) {
		this.isOddRangeNumber = isOddRangeNumber;
	}

	public void setMaxSize(int maxSize) {
		this.maxSize = maxSize;
	}
	
	public void setMinSze(int minSize) {
		this.minSize = minSize;
	}
	
	public void selectData() throws IOException {
		// Read token vector sizes.
		readTokenVectorSizes();
		
		// Compute the max size of token vectors for selecting data.
		if (maxSize == -1) {
			maxSize = Distribution.computeMaxSize(this.maxSizeType, this.tokenVectorSizes);
		}
		if (minSize == -1) {
			if (minSizeType == null) {
				minSize = 0;
			} else {
				minSize = Distribution.computeMinSize(this.minSizeType, this.tokenVectorSizes);
			}
		}
		if (!isOddRangeNumber) {
			if (maxSize % 2 != 0) maxSize += 1;
			if (minSize % 2 != 0) minSize -= 1;
		}
		
		tokenVectorsOutputFileName += "_MaxSize=" + maxSize + ".txt";
		tokenVectorSizesOutputFileName += "_MaxSize=" + maxSize + ".csv";
		fragmentsOutputFileName += "_MaxSize=" + maxSize + ".txt";
		
		// Select token vectors.
		selectTokenVectors();
	}
	
	public void readSelectedCodeFragments(String symbolStr) throws IOException {
		FileInputStream fis = new FileInputStream(this.codeFragmentsFile);
		Scanner scanner = new Scanner(fis);
		
		StringBuilder fragmentsBuilder = new StringBuilder();
		int index = -1;
		int counter = 0;
		StringBuilder singleCodeFragment = new StringBuilder();
		while (scanner.hasNextLine()) {
			String line = scanner.nextLine();
			if (line.equals(symbolStr)) {
				if (singleCodeFragment.length() > 0) {
					if (this.selectedTokenVectorIndexes.contains(index)) {
						counter ++;
						fragmentsBuilder.append(singleCodeFragment);
						
						if (counter % 10000 == 0) {
							FileHelper.outputToFile(this.fragmentsOutputFileName, fragmentsBuilder, true);
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
		
		if (this.selectedTokenVectorIndexes.contains(index)) {
			counter ++;
			fragmentsBuilder.append(singleCodeFragment);
		}
		
		if (fragmentsBuilder.length() > 0) {
			FileHelper.outputToFile(this.fragmentsOutputFileName, fragmentsBuilder, true);
			fragmentsBuilder.setLength(0);
		}
	}
	
	private void readTokenVectorSizes() throws IOException {
		tokenVectorSizes = new ArrayList<>();
		String sizesContent = FileHelper.readFile(tokenVectorSizesFile);
		BufferedReader reader = new BufferedReader(new StringReader(sizesContent));
		String line = null;
		while ((line = reader.readLine()) != null) {
			tokenVectorSizes.add(Integer.parseInt(line));
		}
		reader.close();
	}

	private void selectTokenVectors() throws IOException {
		selectedTokenVectorIndexes = new ArrayList<>();
		
		FileInputStream fis = new FileInputStream(this.tokenVectorsFile);
		Scanner scanner = new Scanner(fis);
		
		StringBuilder tokenVectorsBuilder = new StringBuilder();
		StringBuilder tokenVectorSizesBuilder = new StringBuilder();
		
		int index = -1;
		int counter = 0;
		while (scanner.hasNextLine()) {
			index ++;
			String line = scanner.nextLine();
			int sizeOfTokenVector = this.tokenVectorSizes.get(index);
			if (this.minSize <= sizeOfTokenVector && sizeOfTokenVector <= this.maxSize) {
				selectedTokenVectorIndexes.add(index);
				tokenVectorsBuilder.append(line).append("\n");
				tokenVectorSizesBuilder.append(sizeOfTokenVector).append("\n");
				counter ++;
				if (counter % 10000 == 0) {
					FileHelper.outputToFile(this.tokenVectorsOutputFileName, tokenVectorsBuilder, true);
					tokenVectorsBuilder.setLength(0);
				}
			}
		}
		
		scanner.close();
		fis.close();
		
		if (tokenVectorsBuilder.length() > 0) {
			FileHelper.outputToFile(this.tokenVectorsOutputFileName, tokenVectorsBuilder, true);
			tokenVectorsBuilder.setLength(0);
		}
		FileHelper.outputToFile(this.tokenVectorSizesOutputFileName, tokenVectorSizesBuilder, false);
		tokenVectorsBuilder.setLength(0);
	}
	
}
