package data.method.syntactic.matcher;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;

public class AkkaMatcher {

	@SuppressWarnings("deprecation")
	public void match(int nEpochs, int topNum, String inputPath, int numberOfBodyFeatureWorkers, String outputPath) {
		ActorSystem system = null;
		ActorRef parsingActor = null;
		try {
			system = ActorSystem.create("Matching-System");
			parsingActor = system.actorOf(MatchActor.props(numberOfBodyFeatureWorkers, inputPath, nEpochs, topNum, outputPath), "match-actor");
			parsingActor.tell("BEGIN", ActorRef.noSender());
		} catch (Exception e) {
			system.shutdown();
			e.printStackTrace();
		}
		
	}

}
