package data.javaCode.akka.parser;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;

/**
 * Read java files from java projects.
 */
public class MultipleThreadsJavaFileParser {
	
	private WorkerMessage projects;
	private int numberOfWorkers;
	
	public MultipleThreadsJavaFileParser(String projectsPath, int numberOfWorkers) {
		List<JavaFile> javaFiles = listJavaFilesInProjects(projectsPath);
		this.projects = new WorkerMessage(0, javaFiles);
		this.numberOfWorkers = numberOfWorkers;
	}

	private List<JavaFile> listJavaFilesInProjects(String projectsPath) {
		List<JavaFile> javaFiles = new ArrayList<>();
		File[] projects = new File(projectsPath).listFiles();
		for (File project : projects) {
			if (project.isDirectory()) {
				List<JavaFile> files = listAllFiles(project, ".java", project.getName());
				javaFiles.addAll(files);
			}
		}
		return javaFiles;
	}
	
	/**
	 * Recursively list all files in the file.
	 * 
	 * @param filePath
	 * @return
	 */
	private List<JavaFile> listAllFiles(File filePath, String type, String projectName) {
		List<JavaFile> fileList = new ArrayList<>();
		
		if (!filePath.exists()) {
			return null;
		}
		
		File[] files = filePath.listFiles();
		
		for (File file : files) {
			if (file.getPath().toLowerCase(Locale.ENGLISH).contains("test")) continue;
			if (file.isFile()) {
				if (file.getName().endsWith(type)) {
					JavaFile javaFile = new JavaFile(projectName, file);
					fileList.add(javaFile);
				}
			} else {
				List<JavaFile> fl = listAllFiles(file, type, projectName);
				if (fl != null && fl.size() > 0) {
					fileList.addAll(fl);
				}
			}
		}
		
		return fileList;
	}

	@SuppressWarnings("deprecation")
	public void parseMethods(String outptuPath) {
		ActorSystem system = null;
		ActorRef parsingActor = null;
		
		try {
			system = ActorSystem.create("Parsing-Method-System");
			parsingActor = system.actorOf(JavaFileParseActor.props(numberOfWorkers, outptuPath), "parse-method-actor");
			parsingActor.tell(projects, ActorRef.noSender());
		} catch (Exception e) {
			system.shutdown();
			e.printStackTrace();
		}
		
	}
}
