package data.java.file.getter;

import java.io.File;
import java.util.List;

public class WorkerMessage {
	private int workderId;
	private List<File> javaProjects;
	
	public WorkerMessage(int workderId, List<File> javaProjects) {
		super();
		this.workderId = workderId;
		this.javaProjects = javaProjects;
	}

	public int getWorkderId() {
		return workderId;
	}

	public List<File> getJavaProjects() {
		return javaProjects;
	}
	
}
