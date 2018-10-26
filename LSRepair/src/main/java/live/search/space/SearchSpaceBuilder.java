package live.search.space;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import live.search.config.Configuration;

public class SearchSpaceBuilder {
	
	private static Logger log = LoggerFactory.getLogger(SearchSpaceBuilder.class);

	public SearchSpace searchSpace = null;

	public SearchSpace build(boolean doesReadSearchSpace, String searchSpacePath) {
		try {
			log.info("Begin to read search space...");
			File searchSpaceFile = new File(Configuration.SEARCH_SPACE_FILE);
			if (searchSpaceFile.exists() && doesReadSearchSpace) {
				ObjectInputStream objectInputStream = new ObjectInputStream(new FileInputStream(searchSpaceFile));
	            Object object = objectInputStream.readObject();
	            if (object instanceof SearchSpace) {
	            	searchSpace = (SearchSpace) object;
	            }
	            objectInputStream.close();
			} else {
				if (doesReadSearchSpace) {
					searchSpace = new SearchSpace(searchSpacePath);
					searchSpaceFile.getParentFile().mkdirs();
					searchSpaceFile.createNewFile();
					ObjectOutputStream objectOutputStream = new ObjectOutputStream(new FileOutputStream(searchSpaceFile));
					objectOutputStream.writeObject(searchSpace);
					objectOutputStream.close();
				}
			}
			log.info("Finalize reading search space...");
		} catch (IOException | ClassNotFoundException e) {
			e.printStackTrace();
			log.error("Failed to build the search space!!!");
		}
		return searchSpace;
	}
	
}
