package data.java.code.akka.parser;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Scanner;

import org.eclipse.jdt.core.dom.CompilationUnit;

import code.parser.AST.ASTGenerator;
import code.parser.AST.ASTGenerator.TokenType;
import code.parser.JavaFileParser.JavaFileParser;
import code.parser.jdt.tree.ITree;

public class LOC {

	public static void main(String[] args) {
		long loc = 0;
		String javaFilesName = args[0];
		try {
			FileInputStream fis = new FileInputStream(javaFilesName);
			Scanner scanner = new Scanner(fis);
			while (scanner.hasNextLine()) {
				String line = scanner.nextLine();
				String[] info = line.split("==@@@@@@==");
				File file = new File(info[1]);
				CompilationUnit unit = new JavaFileParser().new MyUnit().createCompilationUnit(file);
				ITree rootTree = new ASTGenerator().generateTreeForJavaFile(file, TokenType.EXP_JDT);
				long l = Long.valueOf(unit.getLineNumber(rootTree.getPos() + rootTree.getLength() - 1));
				System.out.println(l);
				loc += l;
				System.out.println("LOC: " + loc);
			}
			scanner.close();
			fis.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.out.println(loc);
	}

}
