package live.search.fault.localization;

import java.io.File;
import java.io.IOException;
import java.util.List;

import code.parser.utils.FileHelper;
import live.search.fault.localization.dataprepare.DataPreparer;

public class Main {

	public static void main(String[] args) {
		String buggyProjectsPath = "../Defects4jBugs/";
		String outputPath = "../SuspiciousCodePositions/";
		File[] buggyProjects = new File(buggyProjectsPath).listFiles();
		
		for (File buggyProject : buggyProjects) {
			if (!buggyProject.isDirectory()) continue;
			FaultLocalizer faultLocalizer = new FaultLocalizer();
			String metricStr = "null";//Zoltar
			// Prepare data.
			DataPreparer dp = new DataPreparer(buggyProjectsPath);
			dp.prepareData(buggyProject.getName());
			if (!dp.validPaths) continue;
			try {
				List<SuspiciousCode> suspiciousCandidates = faultLocalizer.localizeSuspiciousCode(buggyProjectsPath, buggyProject.getName(), dp, metricStr);

				StringBuilder builder = new StringBuilder();
				if (suspiciousCandidates != null && !suspiciousCandidates.isEmpty()) {
					for (SuspiciousCode suspCode : suspiciousCandidates) {
						String suspiciousClassName = suspCode.getClassName();
						int lineNumber = suspCode.getLineNumber();
						
						String suspiciousJavaFile = suspiciousClassName.replace(".", "/") + ".java";
						builder.append(dp.srcPath).append("@").append(suspiciousJavaFile).append("@").append(lineNumber).append("\n");
					}
				}
				FileHelper.outputToFile(outputPath + buggyProject.getName() + ".txt", builder, false);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

}
