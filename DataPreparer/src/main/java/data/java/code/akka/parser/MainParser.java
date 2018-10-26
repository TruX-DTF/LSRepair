package data.java.code.akka.parser;

import code.parser.utils.FileHelper;

/**
 * Parse methods from existing java projects.
 */
public class MainParser {

	public static void main(String[] args) {
		String projectsPath = args[0];//"../data/GitHubJavaProjects/" or ../data/GitHubJavaFiles/JavaFiles.txt.
		String outputPath = args[1];//"../data/existingMethods/".
		FileHelper.deleteDirectory(outputPath);
		int numberOfWorkers = Integer.parseInt(args[2]);
		int number = Integer.parseInt(args[3]);
		if (number == 2) {
			MultipleThreadsJavaFileParser2 parser = new MultipleThreadsJavaFileParser2(projectsPath, numberOfWorkers);
			parser.parseMethods(outputPath);
		} else {
			MultipleThreadsJavaFileParser parser = new MultipleThreadsJavaFileParser(projectsPath, numberOfWorkers);
			parser.parseMethods(outputPath);
		}
	}

}
