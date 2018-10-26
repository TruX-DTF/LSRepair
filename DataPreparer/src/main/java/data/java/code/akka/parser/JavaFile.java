package data.java.code.akka.parser;

import java.io.File;

public class JavaFile {
	private String projectName;
	private File javaFile;
	
	public JavaFile(String projectName, File javaFile) {
		super();
		this.projectName = projectName;
		this.javaFile = javaFile;
	}

	public String getProjectName() {
		return projectName;
	}

	public File getJavaFile() {
		return javaFile;
	}
	
	
}
