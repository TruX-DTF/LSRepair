package data.method.semantic;

import java.io.File;
import java.util.List;

import code.parser.method.Method;
import code.parser.utils.FileHelper;

/**
 * Create search space of semantic-similar methods for suspicious methods.
 */
public class SemanticSearchSpace {
	
	private static String DATA_PATH = "../data/Semantic/";
	
	public static void main(String[] args) {
		String outputPath = args[0];
		SemanticSearchSpace s3 = new SemanticSearchSpace();
		s3.create(outputPath);
	}

	public void create(String facoyOutputPath) {
		File[] bugs = new File(facoyOutputPath).listFiles();
		for (File bug : bugs) {//Chart_1
			if (!bug.isDirectory()) continue;
			
			File[] semanticMethodFiles = bug.listFiles();
			for (File semanticMethodFile : semanticMethodFiles) {//SuspiciousMethods
				if (!semanticMethodFile.isDirectory()) continue;
				List<File> semanticMethodFilesList = FileHelper.getAllFilesInCurrentDiectory(semanticMethodFile, ".java");
				for (File semanticMethodsF : semanticMethodFilesList) {
					String fileName = semanticMethodsF.getName();
					int priority = Integer.parseInt(fileName.substring(0, fileName.indexOf("_")));
					if (priority > 10) continue;
					File positionsFile = new File(semanticMethodsF.getPath() + "_");
					FacoyResultsParser parser = new FacoyResultsParser();
					parser.parserSimilarMethods(semanticMethodsF, positionsFile);
					List<Method> methods = parser.methods;
					StringBuilder builder = new StringBuilder();
					for (int i = 0, size = methods.size(); i < size; i ++) {
						Method method = methods.get(i);
						builder.append("#METHOD_BODY#========================\n//****** ")
							   .append(method.getKey())
							   .append(" ******//\n")
							   .append(method.getBody()).append("\n");
					}
					FileHelper.outputToFile(DATA_PATH + bug.getName() + "/" + semanticMethodFile.getName() + "/method_" + priority + ".txt", builder, false);
				}
			}
		}
	}

}
