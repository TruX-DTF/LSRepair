package method.body.feature.learning.models;

import java.io.File;
import java.io.IOException;

import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
import org.deeplearning4j.models.word2vec.Word2Vec;
import org.deeplearning4j.text.sentenceiterator.BasicLineIterator;
import org.deeplearning4j.text.sentenceiterator.SentenceIterator;
import org.deeplearning4j.text.tokenization.tokenizerfactory.DefaultTokenizerFactory;
import org.deeplearning4j.text.tokenization.tokenizerfactory.TokenizerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import moethod.body.feature.learning.utils.FileHelper;

public class Word2VecEncoder {
	private static Logger log = LoggerFactory.getLogger(Word2VecEncoder.class);
	
	private int windowSize = 4;
	
	public void setWindowSize(int windowSize) {
		this.windowSize = windowSize;
	}

	@SuppressWarnings("deprecation")
	public void embedTokens(File inputFile, int minWordFrequency, int layerSize, String inputFilePath, String outputFilePath) throws IOException {
		String fileName = inputFile.getPath();

        log.info("Load & Vectorize Sentences....");
        // Strip white space before and after for each line
        SentenceIterator iter = new BasicLineIterator(inputFile);
        // Split on white spaces in the line to get words
        TokenizerFactory t = new DefaultTokenizerFactory();

        /*
            CommonPreprocessor will apply the following regex to each token: [\d\.:,"'\(\)\[\]|/?!;]+
            So, effectively all numbers, punctuation symbols and some special symbols are stripped off.
            Additionally it forces lower case for all tokens.
         */
        t.setTokenPreProcessor(new MyTokenPreprocessor());

        log.info("****************Building model****************");
        Word2Vec vec = new Word2Vec.Builder()
        		.epochs(1)
//        		.batchSize(100)
//        		.useAdaGrad(reallyUse)
                .iterations(1)
                .learningRate(.01)
                .seed(50)
                .windowSize(windowSize)
                .minWordFrequency(minWordFrequency)
                .layerSize(layerSize)
                .iterate(iter)
                .tokenizerFactory(t)
                .build();

        log.info("****************Fitting Word2Vec model****************");
        vec.fit();

        log.info("****************Writing word vectors to text file****************");
        // Write word vectors to file
        fileName = fileName.replace(inputFilePath, outputFilePath + minWordFrequency + "/");
        FileHelper.makeDirectory(fileName);
//        WordVectorSerializer.writeWord2VecModel(vec, new File(fileName)); // output model to a file
        WordVectorSerializer.writeWordVectors(vec, fileName);
        log.info("****************Finish off embedding****************\n");
	}
	
	@SuppressWarnings("deprecation")
	public void embedTokens(File inputFile, int minWordFrequency, int layerSize, String outputFileName) throws IOException {
		log.info("Load & Vectorize Sentences....");
        // Strip white space before and after for each line
        SentenceIterator iter = new BasicLineIterator(inputFile);
        // Split on white spaces in the line to get words
        TokenizerFactory t = new DefaultTokenizerFactory();

        /*
            CommonPreprocessor will apply the following regex to each token: [\d\.:,"'\(\)\[\]|/?!;]+
            So, effectively all numbers, punctuation symbols and some special symbols are stripped off.
            Additionally it forces lower case for all tokens.
         */
        t.setTokenPreProcessor(new MyTokenPreprocessor());

        log.info("****************Building model****************");
        Word2Vec vec = new Word2Vec.Builder()
        		.epochs(1)
//        		.batchSize(100)
//        		.useAdaGrad(reallyUse)
                .iterations(1)
                .learningRate(.01)
                .seed(50)
                .windowSize(windowSize)
                .minWordFrequency(minWordFrequency)
                .layerSize(layerSize)
                .iterate(iter)
                .tokenizerFactory(t)
                .build();

        log.info("****************Fitting Word2Vec model****************");
        vec.fit();

        log.info("****************Writing word vectors to text file****************");
        // Write word vectors to file
        FileHelper.makeDirectory(outputFileName);
//        WordVectorSerializer.writeWord2VecModel(vec, new File(fileName)); // output model to a file
        WordVectorSerializer.writeWordVectors(vec, outputFileName);
        log.info("****************Finish off embedding****************\n");
        // Evaluation
	}
}
