package live.search.fault.localization;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import code.parser.utils.FileHelper;
import live.search.config.Configuration;
import live.search.fault.localization.Metrics.Metric;
import live.search.fault.localization.dataprepare.DataPreparer;
import live.search.fault.localization.utils.FileUtils;
import live.search.fault.localization.utils.PathUtils;
import live.search.sig.sim.fixer.SigSimFixer;

public class FLTemplate {
	
	private static Logger log = LoggerFactory.getLogger(SigSimFixer.class);
	
	public static void main(String[] args) throws IOException {
		String outputPath = "suspPositions/";
		FileHelper.deleteDirectory(outputPath);
		String path = "/Defects4J_Buggy_Projects_Path/";
		File[] projects = new File(path).listFiles();
		for (File project : projects) {
			if (project.isDirectory()) {
				String projectName = project.getName();
				FLTemplate test = new FLTemplate();
				test.locateSuspiciousCode(path, projectName, outputPath);
			}
		}
	}

	public void locateSuspiciousCode(String path, String buggyProject, String outputPath) throws IOException {
		
		if (!buggyProject.contains("_")) {
			System.out.println("Main: cannot recognize project name \"" + buggyProject + "\"");
			return;
		}

		String[] elements = buggyProject.split("_");
		try {
			Integer.valueOf(elements[1]);
		} catch (NumberFormatException e) {
			System.out.println("Main: cannot recognize project name \"" + buggyProject + "\"");
			return;
		}

		System.out.println(buggyProject);
		
		DataPreparer dp = new DataPreparer(path);
		dp.prepareData(buggyProject);
		if (!dp.validPaths) return;

		GZoltarFaultLoclaization gzfl = new GZoltarFaultLoclaization();
		gzfl.threshold = 0.01;
		gzfl.maxSuspCandidates = 1000;
		gzfl.srcPath = path + buggyProject + PathUtils.getSrcPath(buggyProject).get(2);
		gzfl.localizeSuspiciousCodeWithGZoltar(dp.classPaths, checkNotNull(Arrays.asList("")), dp.testCases);
		
		for (String metricStr : Configuration.METRICS) {
			Metric metric = null;
			if (!metricStr.equals("null")) {
				metric = new Metrics().generateMetric(metricStr);
				if (metric == null) {
					log.error("Incorrect Fault-Localization-Metric name: " + metricStr);
					return;
				}
			}
			System.out.println(metricStr);
			gzfl.sortSuspiciousCode(metric);
			List<SuspiciousCode> candidates = new ArrayList<SuspiciousCode>(gzfl.candidates.size());
			candidates.addAll(gzfl.candidates);
			
			File suspiciousFile = new File(Configuration.LOCALIZATION_RESULT_CACHE + FileUtils.getMD5(StringUtils.join(dp.testCases, "")
					+ dp.classPath + dp.testClassPath + dp.srcPath + dp.testSrcPath + metric) + ".sps");
			File parentFile = suspiciousFile.getParentFile();
			if (!parentFile.exists()) parentFile.mkdirs();
			suspiciousFile.createNewFile();
			ObjectOutputStream objectOutputStream = new ObjectOutputStream(new FileOutputStream(suspiciousFile));
            objectOutputStream.writeObject(candidates);
            objectOutputStream.close();
            
			StringBuilder builder = new StringBuilder();
			for (int index = 0, size = candidates.size(); index < size; index ++) {
				SuspiciousCode candidate = candidates.get(index);
				String className = candidate.getClassName();
				int lineNumber = candidate.lineNumber;
				builder.append(className).append("@@@").append(lineNumber).append("\n");
			}
			FileHelper.outputToFile(outputPath + buggyProject + "/" + metricStr + ".txt", builder, false);
			gzfl.candidates = new ArrayList<SuspiciousCode>();
		}
	}

}
