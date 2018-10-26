package data.method.syntactic.matcher;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.japi.Creator;
import akka.routing.RoundRobinPool;
import code.parser.utils.FileHelper;

public class MatchActor extends UntypedActor {

	private static Logger logger = LoggerFactory.getLogger(MatchActor.class);

	private ActorRef travelRouter;
	private final int numberOfBodyFeatureWorkers;
	private int counter = 0;
	private String inputPath;
	private int nEpochs;
	private int topNum;
	private String outputPath;
	
	public MatchActor(int numberOfBodyFeatureWorkers, String inputPath, int nEpochs, int topNum, String outputPath) {
		this.numberOfBodyFeatureWorkers = numberOfBodyFeatureWorkers;
		this.inputPath = inputPath;
		this.nEpochs = nEpochs;
		this.topNum = topNum;
		this.outputPath = outputPath;
		this.travelRouter = this.getContext().actorOf(new RoundRobinPool(numberOfBodyFeatureWorkers)
				.props(MatchWorker.props(topNum)), "evaluate-router");
	}

	public static Props props(final int numberOfWorkers, final String inputPath, final int nEpochs, final int topNum, final String outputPath) {
		return Props.create(new Creator<MatchActor>() {

			private static final long serialVersionUID = -4609365408992093978L;

			@Override
			public MatchActor create() throws Exception {
				return new MatchActor(numberOfWorkers, inputPath, nEpochs, topNum, outputPath);
			}
			
		});
	}
	
	@SuppressWarnings("deprecation")
	@Override
	public void onReceive(Object msg) throws Throwable {
		if (msg instanceof String && msg.toString().startsWith("BEGIN")) {
			String bodyFeaturesFilePath = inputPath + "DLoutput_" + nEpochs + "/";
			File[] files = new File(bodyFeaturesFilePath).listFiles();
			File trainingBodyFeaturesFile = null;
			File testingBodyFeaturesFile = null;//TestingFeatures_
			
			for (File file : files) {
				String fileName = file.getName();
				if (fileName.equals(nEpochs + "_CNNoutput.csv")) {
					trainingBodyFeaturesFile = file;
				} else if (fileName.startsWith("TestingFeatures_")) {
					testingBodyFeaturesFile = file;
				}
			}

			// Method body features.
			List<Double[]> trainingBodyFeatures = readFeatures(trainingBodyFeaturesFile);
			List<Double[]> testingBodyFeatures = readFeatures(testingBodyFeaturesFile);
			
			int trainingSize = trainingBodyFeatures.size();
			int trainingAverage = trainingSize / numberOfBodyFeatureWorkers;
			int trainingReminder = trainingSize % numberOfBodyFeatureWorkers;
			int training = 0;

			System.out.println(trainingSize + "-------" + testingBodyFeatures.size());
			for (int i = 0; i < numberOfBodyFeatureWorkers; i ++) {
				int fromIndex = i * trainingAverage + training;
				if (training < trainingReminder) training ++;
				int toIndex = (i + 1) * trainingAverage + training;
				List<Double[]> subTrainingBodyFeatures = trainingBodyFeatures.subList(fromIndex, toIndex);

				final WorkerMessage workerMsg = new WorkerMessage(i + 1, subTrainingBodyFeatures, testingBodyFeatures, fromIndex, toIndex);
				this.travelRouter.tell(workerMsg, getSelf());
				logger.info("Assign a task to worker #" + (i + 1) + "...");
				if (i == numberOfBodyFeatureWorkers - 1) {
					System.out.println(toIndex + "-------");
				}
			}
		} else if (msg instanceof WorkerReturnMessage) {
			counter ++;
			
			WorkerReturnMessage wrMsg = (WorkerReturnMessage) msg;
			mergeData(wrMsg.similarities);
			logger.info(counter + " workers finished their work...");
			
			if (counter >= numberOfBodyFeatureWorkers) {
				outputSyntacticSimilarMethodIndexes();
				logger.info("All workers finished their work...");
				
				this.getContext().stop(travelRouter);
				this.getContext().stop(getSelf());
				this.getContext().system().shutdown();
			}
		} else {
			unhandled(msg);
		}
	}
	
	private void outputSyntacticSimilarMethodIndexes() {
		for (Map.Entry<Integer, List<Similarity>> entity : this.topSimilarBodyMap.entrySet()) {
			Integer testIndex = entity.getKey();
			List<Similarity> similarities = entity.getValue();
			StringBuilder builder = new StringBuilder();
			StringBuilder similarityBuilder = new StringBuilder();
			for (int i = 0, size = similarities.size(); i < size; i ++) {
				builder.append(similarities.get(i).index).append("\n");
				similarityBuilder.append(similarities.get(i).similarityValue).append("\n");
			}
			FileHelper.outputToFile(this.outputPath + "Signature_" + testIndex + "/similarIndexes.txt", builder, false);
			FileHelper.outputToFile(this.outputPath + "Signature_" + testIndex + "/similarities.txt", similarityBuilder, false);
		}
	}

	private void mergeData(Map<Integer, List<Similarity>> similaritiesMap) {
		for (Map.Entry<Integer, List<Similarity>> entity : similaritiesMap.entrySet()) {
			Integer testIndex = entity.getKey();
			List<Similarity> similarities = entity.getValue();
			List<Similarity> s = topSimilarBodyMap.get(testIndex);
			if (s == null) {
				topSimilarBodyMap.put(testIndex, similarities);
			} else {
				s.addAll(similarities);
				Collections.sort(s, (s1, s2) -> Double.compare(s2.similarityValue, s1.similarityValue));
				topSimilarBodyMap.put(testIndex, s.subList(0, this.topNum));
			}
		}
		
	}

	private List<Double[]> readFeatures(File featuresFile) {
		FeatureReader reader = new FeatureReader();
		reader.setFeatureFile(featuresFile);
		reader.readFeatures();
		return reader.getFeatures();
	}

	private Map<Integer, List<Similarity>> topSimilarBodyMap = new HashMap<>();

}
