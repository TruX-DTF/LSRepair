package method.body.feature.learning.models;

import org.deeplearning4j.text.tokenization.tokenizer.TokenPreProcess;

public class MyTokenPreprocessor implements TokenPreProcess {

	@Override
	public String preProcess(String token) {
		return token;
	}

}
