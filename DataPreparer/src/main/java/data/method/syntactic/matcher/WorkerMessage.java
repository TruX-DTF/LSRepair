package data.method.syntactic.matcher;

import java.util.List;

public class WorkerMessage {

	public int workerId;
	public List<Double[]> subTrainingBodyFeatures;
	public List<Double[]> testingBodyFeatures;
	public int fromIndex;
	public int toIndex;
	
	public WorkerMessage(int workerId, List<Double[]> subTrainingBodyFeatures, List<Double[]> testingBodyFeatures,
			int fromIndex, int toIndex) {
		this.workerId = workerId;
		this.subTrainingBodyFeatures = subTrainingBodyFeatures;
		this.testingBodyFeatures = testingBodyFeatures;
		this.fromIndex = fromIndex;
		this.toIndex = toIndex;
	}

}
