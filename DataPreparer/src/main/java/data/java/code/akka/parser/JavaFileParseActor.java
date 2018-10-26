package data.java.code.akka.parser;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.japi.Creator;
import akka.routing.RoundRobinPool;
import code.parser.utils.FileHelper;

public class JavaFileParseActor extends UntypedActor {
	
	private static Logger logger = LoggerFactory.getLogger(JavaFileParseActor.class);

	private ActorRef travelRouter;
	private final int numberOfWorkers;
	private int counter = 0;
	private String outputPath;
	
	public JavaFileParseActor(int numberOfWorkers, String outputPath) {
		this.numberOfWorkers = numberOfWorkers;
		this.outputPath = outputPath;
		this.travelRouter = this.getContext().actorOf(new RoundRobinPool(numberOfWorkers)
				.props(JavaFileParseWorker.props(outputPath, numberOfWorkers)), "parse-method-router");
	}

	public static Props props(final int numberOfWorkers, final String outputPath) {
		
		return Props.create(new Creator<JavaFileParseActor>() {

			private static final long serialVersionUID = 9207427376110704705L;

			@Override
			public JavaFileParseActor create() throws Exception {
				return new JavaFileParseActor(numberOfWorkers, outputPath);
			}
			
		});
	}
	
	@SuppressWarnings("deprecation")
	@Override
	public void onReceive(Object message) throws Exception {
		if (message instanceof WorkerMessage) {
			WorkerMessage msg = (WorkerMessage) message;
			List<JavaFile> javaFiles = msg.getJavaFiles();
			System.out.println(javaFiles.size());
			
			int size = javaFiles.size();
			int average = size / numberOfWorkers;
			int reminder = size % numberOfWorkers;
			int index = 0;
			
			int a = 0;
			for (int i = 0; i < numberOfWorkers; i ++) {
				int fromIndex = i * average + index;
				if (index < reminder) index ++;
				int toIndex = (i + 1) * average + index;
				
				List<JavaFile> javaFilesOfWoker = new ArrayList<>();
				javaFilesOfWoker.addAll(javaFiles.subList(fromIndex, toIndex));
				final WorkerMessage pro = new WorkerMessage(i + 1, javaFilesOfWoker);
				travelRouter.tell(pro, getSelf());
				logger.info("Assign a task to worker #" + (i + 1) + "..." + fromIndex + " -- " + toIndex + ": " + javaFilesOfWoker.size());
				a += javaFilesOfWoker.size();
			}
			System.out.println(a);
		} else if (message.toString().equals("MERGE_DATA")) {
			List<File> rtFiles = new ArrayList<>();
			rtFiles.add(new File(outputPath + "void/"));
			travelRouter.tell(rtFiles, getSelf());
			logger.info("Assign a task to worker #" + numberOfWorkers + "...");
			
			List<File> returnTypeFiles = Arrays.asList(new File(outputPath).listFiles());
			int size = returnTypeFiles.size();
			int average = size / (numberOfWorkers - 1);
			int reminder = size % (numberOfWorkers - 1);
			int index = 0;
			for (int i = 0; i < (numberOfWorkers - 1); i ++) {
				int fromIndex = i * average + index;
				if (index < reminder) index ++;
				int toIndex = (i + 1) * average + index;
				
				List<File> returnTypeFilesOfWorker = new ArrayList<>();
				returnTypeFilesOfWorker.addAll(returnTypeFiles.subList(fromIndex, toIndex));
				travelRouter.tell(returnTypeFilesOfWorker, getSelf());
				logger.info("Assign a task to worker #" + (i + 1) + "...");
			}
		} else if (message.toString().equals("SHUT_DOWN")) {
			counter ++;
			logger.info(counter + " workers finished their work...");
			if (counter >= numberOfWorkers) {
				mergeData();// merge data.
				logger.info("ReturnType: " + new File(outputPath).listFiles().length);
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
		File[] returnTypeFiles = new File(outputPath).listFiles();
		String tokensFile = outputPath + "/MethodBodyTokens.txt";
		String codesFile = outputPath + "/MethodBodyCode.txt";
		String rawTokensFile = outputPath + "/MethodBodyRawTokens.txt";
		String signaturesFile = outputPath + "/MethodSignature.txt";
		String sizesFile = outputPath + "/Sizes.csv";
		FileHelper.deleteFile(tokensFile);
		FileHelper.deleteFile(codesFile);
		FileHelper.deleteFile(rawTokensFile);
		FileHelper.deleteFile(signaturesFile);
		FileHelper.deleteFile(sizesFile);
		for (File returnTypeFile : returnTypeFiles) {
			if (returnTypeFile.isDirectory()) {
				if (returnTypeFile.getName().equals("void")) {
					String tokenfile = returnTypeFile.getPath() + "/MethodBodyTokens.txt";
					mergeData(tokensFile, tokenfile, 10000);
					String rawTokenFile = returnTypeFile.getPath() + "/MethodBodyRawTokens.txt";
					mergeData(rawTokensFile, rawTokenFile, 10000);
					String signatureFile = returnTypeFile.getPath() + "/MethodSignature.txt";
					mergeData(signaturesFile, signatureFile, 10000);
					String codefile = returnTypeFile.getPath() + "/MethodBodyCode.txt";
					mergeData(codesFile, codefile, 100000);
				} else {
					String tokenfile = returnTypeFile.getPath() + "/MethodBodyTokens.txt";
					FileHelper.outputToFile(tokensFile, FileHelper.readFile(tokenfile), true);
					String codefile = returnTypeFile.getPath() + "/MethodBodyCode.txt";
					FileHelper.outputToFile(codesFile, FileHelper.readFile(codefile), true);
					String rawTokenFile = returnTypeFile.getPath() + "/MethodBodyRawTokens.txt";
					FileHelper.outputToFile(rawTokensFile, FileHelper.readFile(rawTokenFile), true);
					String signatureFile = returnTypeFile.getPath() + "/MethodSignature.txt";
					FileHelper.outputToFile(signaturesFile, FileHelper.readFile(signatureFile), true);
				}
				String sizefile = returnTypeFile.getPath() + "/Sizes.csv";
				FileHelper.outputToFile(sizesFile, FileHelper.readFile(sizefile), true);
			}
		}
	}

	private void mergeData(String tokensFile, String tokenFile, int num) {
		try {
			StringBuilder builder = new StringBuilder();
			FileInputStream fis = new FileInputStream(tokenFile);
			Scanner scanner = new Scanner(fis);
			int counter = 0;
			while (scanner.hasNextLine()) {
				String line = scanner.nextLine();
				counter ++;
				builder.append(line).append("\n");
				if (counter % num == 0) {
					FileHelper.outputToFile(tokensFile, builder, true);
					builder.setLength(0);
				}
			}
			scanner.close();
			fis.close();
			if (builder.length() > 0) {
				FileHelper.outputToFile(tokensFile, builder, true);
				builder.setLength(0);
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
