package live.search.main;

import live.search.sig.sim.fixer.SigSimFixer;
import live.search.space.SearchSpace;

/**
 * 1. Localize bug.
 * 2. Search similar methods.
 * 3. Fix the bug with similar methods.
 */
public class MainProcess {
	
	/*
	 
Code Transform: the ingredients.

Host code snippet.
Donor code snippet.  (How to select it?)

Some context information?

Dependency?

	 */
	
	public static void main(String[] args) {
		String buggyProjectsPath = args[0];//"../Defects4jBugs/";
		String defects4jPath = args[1];// "../defects4j/";
		String buggyProjects = args[2]; // Chart_1,Chart_2,...
		String searchPath = args[3];   // "../data/existingMethods/";
		String metricStr = args[4];    // Zoltar
		boolean readSearchSpace = Boolean.valueOf(args[8]);
		
		String[] buggyProjectsArray = buggyProjects.split(",");
		SearchSpace searchSpace = null;
		for (String buggyProject : buggyProjectsArray) {
			SigSimFixer fixer = new SigSimFixer();
			fixer.isOneByOne = Boolean.valueOf(args[5]);
			fixer.withoutPriority = Boolean.valueOf(args[6]);
			int expire = Integer.valueOf(args[7]);
			if (searchSpace != null) {
				fixer.searchSpace = searchSpace;
			}
			fixer.fixProcess(buggyProjectsPath, defects4jPath, buggyProject, searchPath, metricStr, expire, readSearchSpace);
			if (searchSpace == null) {
				searchSpace = fixer.searchSpace;
			}
		}
		
	}

	
}
