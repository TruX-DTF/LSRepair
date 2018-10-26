package data.java.file.getter;

/**
 * Prepare Java files for search space creating and method body feature learning.
 */
public class MainProcess {

	/**
	 * arg1: Path of All Java projects.
	 * arg2: Path of output data.
	 * arg3: Number of akka workers.
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		String projectsPath = args[0]; // ../data/GitHubJavaProjects/
		String outputPath = args[1];   // ../data/GitHubJavaFiles/JavaFiles.txt
		int numberOfWorkers = Integer.parseInt(args[2]); // 1000
		MultipleThreadsJavaFileGetter getter = new MultipleThreadsJavaFileGetter(projectsPath, numberOfWorkers);
		getter.getJavaFiles(outputPath);
	}

}
