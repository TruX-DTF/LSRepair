package data.javaCode.akka.parser;

import code.parser.utils.FileHelper;

public class DeleteFiles {

	public static void main(String[] args) {
		FileHelper.deleteDirectory(args[0]);
	}

}
