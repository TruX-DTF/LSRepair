package data.javaCode.akka.parser;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;

/**
 * Read java files from an existing java-file names file.
 */
public class MultipleThreadsJavaFileParser2 {
	
	private WorkerMessage projects;
	private int numberOfWorkers;
	
	public MultipleThreadsJavaFileParser2(String javaFilesName, int numberOfWorkers) {
		List<JavaFile> javaFiles = listJavaFilesInProjects(javaFilesName);
		this.projects = new WorkerMessage(0, javaFiles);
		this.numberOfWorkers = numberOfWorkers;
	}

	private List<JavaFile> listJavaFilesInProjects(String javaFilesName) {
		List<JavaFile> javaFiles = new ArrayList<>();
		try {
			FileInputStream fis = new FileInputStream(javaFilesName);
			Scanner scanner = new Scanner(fis);
			while (scanner.hasNextLine()) {
				String line = scanner.nextLine();
				String[] info = line.split("==@@@@@@==");
				String projectName = info[0];
				File file = new File(info[1]);
				JavaFile javaFile = new JavaFile(projectName, file);
				javaFiles.add(javaFile);
			}
			scanner.close();
			fis.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return javaFiles;
	}
	
	@SuppressWarnings("deprecation")
	public void parseMethods(String outputPath) {
		ActorSystem system = null;
		ActorRef parsingActor = null;
		
		try {
			system = ActorSystem.create("Parsing-Method-System");
			parsingActor = system.actorOf(JavaFileParseActor.props(numberOfWorkers, outputPath), "parse-method-actor");
			parsingActor.tell(projects, ActorRef.noSender());
		} catch (Exception e) {
			system.shutdown();
			e.printStackTrace();
		}
		
	}
}
