package data.method.syntactic.matcher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.japi.Creator;
import data.method.syntactic.matcher.DistanceCalculator.DistanceFunction;

public class MatchWorker extends UntypedActor {
	
	private static Logger log = LoggerFactory.getLogger(MatchWorker.class);
	
	private int topNum;
	
	public MatchWorker(int topNum) {
		this.topNum = topNum;
	}

	public static Props props(final int topNum) {
		return Props.create(new Creator<MatchWorker>() {

			private static final long serialVersionUID = 3368635349264029140L;

			@Override
			public MatchWorker create() throws Exception {
				return new MatchWorker(topNum);
			}
			
		});
	}
	
	@Override
	public void onReceive(Object msg) throws Throwable {
		if (msg instanceof WorkerMessage) {
			WorkerMessage workerMsg = (WorkerMessage) msg;
			int workerId = workerMsg.workerId;
			List<Double[]> testingBodyFeatures = workerMsg.testingBodyFeatures;
			List<Double[]> subTrainingBodyFeatures = workerMsg.subTrainingBodyFeatures;
			int fromIndex = workerMsg.fromIndex;
			int toIndex = workerMsg.toIndex;
			int trainSize = toIndex - fromIndex;
			
			WorkerReturnMessage wrMsg = new WorkerReturnMessage();
			for (int testIndex = 0, testSize = testingBodyFeatures.size(); testIndex < testSize; testIndex ++) {
				Double[] testingBodyFeatre = testingBodyFeatures.get(testIndex);
				List<Similarity> similarities = new ArrayList<>();
				for (int trainIndex = 0; trainIndex < trainSize; trainIndex ++) {
					Double similarityValue = new DistanceCalculator().calculateDistance(DistanceFunction.COSINESIMILARITY, testingBodyFeatre, subTrainingBodyFeatures.get(trainIndex));
					if (similarityValue.equals(Double.NaN)) {
						continue;
					}
					Similarity similarity = new Similarity(trainIndex + fromIndex, similarityValue);
					similarities.add(similarity);
				}
				if (similarities.size() > this.topNum) {
					Collections.sort(similarities, (s1, s2) -> Double.compare(s2.similarityValue, s1.similarityValue));
					similarities = similarities.subList(0, this.topNum);
				}
				
				wrMsg.similarities.put(testIndex, similarities);
			}
			
			log.info("Worker #" + workerId +" Finish of its work...");
			
			this.getSender().tell(wrMsg, getSelf());
		} else {
			unhandled(msg);
		}
	}

}
