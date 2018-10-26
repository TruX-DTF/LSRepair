package data.javaFile.getter;

import java.io.File;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.japi.Creator;
import code.parser.utils.FileHelper;

public class JavaFileGetWorker extends UntypedActor {
	
	private static Logger log = LoggerFactory.getLogger(JavaFileGetWorker.class);
	
	private String outputPath;
	
	public JavaFileGetWorker(String outputPath) {
		this.outputPath = outputPath;
	}

	public static Props props(final String outputPath) {
		return Props.create(new Creator<JavaFileGetWorker>() {

			private static final long serialVersionUID = -7615153844097275009L;

			@Override
			public JavaFileGetWorker create() throws Exception {
				return new JavaFileGetWorker(outputPath);
			}
			
		});
	}

	@Override
	public void onReceive(Object message) throws Exception {
		if (message instanceof WorkerMessage) {
			WorkerMessage msg = (WorkerMessage) message;
			List<File> javaProjects = msg.getJavaProjects();
			int workerId = msg.getWorkderId();
			
			StringBuilder builder = new StringBuilder();
			for (File javaProject : javaProjects) {
				if (javaProject.isDirectory()) {
					readAllJavaFiles(javaProject, ".java", javaProject.getName(), builder);
				}
				if (builder.length() > 0) {
					exportJavaFileNames(builder, workerId);
					builder.setLength(0);
				}
			}
			
			log.info("Worker #" + workerId +" Finish of reading java files...");
			this.getSender().tell("SHUT_DOWN", getSelf());
		} else {
			unhandled(message);
		}
	}
	
	/**
	 * Recursively list all files in the file.
	 * 
	 * @param filePath
	 * @return
	 */
	private void readAllJavaFiles(File filePath, String type, String projectName, StringBuilder builder) {
		if (!filePath.exists()) {
			return;
		}
		
		File[] files = filePath.listFiles();
		
		for (File file : files) {
			if (file.getPath().toLowerCase(Locale.ENGLISH).contains("test")) continue;
			if (file.isFile()) {
				if (file.getName().endsWith(type)) {
					builder.append(projectName).append("==@@@@@@==").append(file.getAbsolutePath()).append("\n");
				}
			} else {
				readAllJavaFiles(file, type, projectName, builder);
			}
		}
	}

	private void exportJavaFileNames(StringBuilder builder, int id) {
		String methodBodyCodeTokensFile = outputPath + "JavaFiles/JavaFiles_" + id + ".txt";
		FileHelper.outputToFile(methodBodyCodeTokensFile, builder, true);
	}
	
}
