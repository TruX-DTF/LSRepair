package data.javaFile.getter;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.japi.Creator;
import akka.routing.RoundRobinPool;
import code.parser.utils.FileHelper;

public class JavaFileGetActor extends UntypedActor {
	
	private static Logger logger = LoggerFactory.getLogger(JavaFileGetActor.class);

	private ActorRef travelRouter;
	private final int numberOfWorkers;
	private int counter = 0;
	private String outputPath;
	
	public JavaFileGetActor(int numberOfWorkers, String outputPath) {
		this.numberOfWorkers = numberOfWorkers;
		this.outputPath = outputPath;
		this.travelRouter = this.getContext().actorOf(new RoundRobinPool(numberOfWorkers)
				.props(JavaFileGetWorker.props(outputPath)), "parse-method-router");
	}

	public static Props props(final int numberOfWorkers, final String outputPath) {
		
		return Props.create(new Creator<JavaFileGetActor>() {

			private static final long serialVersionUID = 9207427376110704705L;

			@Override
			public JavaFileGetActor create() throws Exception {
				return new JavaFileGetActor(numberOfWorkers, outputPath);
			}
			
		});
	}
	
	@SuppressWarnings("deprecation")
	@Override
	public void onReceive(Object message) throws Exception {
		if (message instanceof String && !message.toString().equals("SHUT_DOWN")) {
			String msg = message.toString();
			List<File> projects = new ArrayList<>();
			projects.addAll(Arrays.asList(new File(msg).listFiles()));
			System.out.println(projects.size());
			
			int size = projects.size();
			int average = size / numberOfWorkers;
			int reminder = size % numberOfWorkers;
			int index = 0;
			
			for (int i = 0; i < numberOfWorkers; i ++) {
				int fromIndex = i * average + index;
				if (index < reminder) index ++;
				int toIndex = (i + 1) * average + index;
				
				
				List<File> javaProjectsOfWoker = new ArrayList<>();
				javaProjectsOfWoker.addAll(projects.subList(fromIndex, toIndex));
				final WorkerMessage pro = new WorkerMessage(i + 1, javaProjectsOfWoker);
				travelRouter.tell(pro, getSelf());
				logger.info("Assign a task to worker #" + (i + 1) + "...");
			}
		} else if (message.toString().equals("SHUT_DOWN")) {
			counter ++;
			logger.info(counter + " workers finished their work...");
			if (counter >= numberOfWorkers) {
				mergeData();// merge data.
				logger.info("All workers finished their work...");
				
				this.getContext().stop(travelRouter);
				this.getContext().stop(getSelf());
				this.getContext().system().shutdown();
			}
		} else {
			unhandled(message);
		}
	}
	
	public void mergeData() {
		mergeData("JavaFiles");
	}

	private void mergeData(String type) {
		String fileType = ".txt";
		String outputFileName = outputPath + type + fileType;
		FileHelper.deleteFile(outputFileName);
		
		String dataPath = outputPath + type;
		if (new File(dataPath).exists()) {
			for (int i = 1; i <= numberOfWorkers; i ++) {
				FileHelper.outputToFile(outputFileName, FileHelper.readFile(dataPath + "/" + type + "_" + i + fileType), true);
			}
		}
		FileHelper.deleteDirectory(dataPath + "/");
	}

}
