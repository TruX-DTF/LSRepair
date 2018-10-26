package data.javaCode.akka.parser;

import java.util.List;

public class WorkerMessage {
	private int workderId;
	private List<JavaFile> javaFiles;
	
	public WorkerMessage(int workderId, List<JavaFile> javaFiles) {
		super();
		this.workderId = workderId;
		this.javaFiles = javaFiles;
	}

	public int getWorkderId() {
		return workderId;
	}

	public List<JavaFile> getJavaFiles() {
		return javaFiles;
	}
	
}
