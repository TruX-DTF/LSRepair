package live.search.fault.localization;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import live.search.config.Configuration;
import live.search.fault.localization.Metrics.Metric;
import live.search.fault.localization.dataprepare.DataPreparer;
import live.search.fault.localization.utils.FileUtils;
import live.search.fault.localization.utils.PathUtils;

public class FaultLocalizer {
	
	private List<SuspiciousCode> suspiciousCandidates = null;

	private static Logger log = LoggerFactory.getLogger(FaultLocalizer.class);
	
	public List<SuspiciousCode> localizeSuspiciousCode(String path, String buggyProject, DataPreparer dp, String metricStr) throws IOException {
		Metric metric = null; // null: GZoltar default metric.
		if (!metricStr.equals("null")) {
			metric = new Metrics().generateMetric(metricStr);
			if (metric == null) {
				log.error("Incorrect Fault-Localization-Metric name: " + metricStr);
				return suspiciousCandidates;
			}
		}
		
		File suspiciousFile = new File(Configuration.LOCALIZATION_RESULT_CACHE + FileUtils.getMD5(StringUtils.join(dp.testCases, "")
				+ dp.classPath + dp.testClassPath + dp.srcPath + dp.testSrcPath + (metricStr.equals("All") ? "All" : metric)) + ".sps");
		if (suspiciousFile.exists()) {
			log.info("Suspicious file: " + suspiciousFile);
			readSuspiciousCodeFromFile(suspiciousFile);
			return suspiciousCandidates;
		}
		
		GZoltarFaultLoclaization gzfl = new GZoltarFaultLoclaization();
		gzfl.threshold = 0.01;
		gzfl.maxSuspCandidates = 1000;
		gzfl.srcPath = path + buggyProject + PathUtils.getSrcPath(buggyProject).get(2);
		gzfl.localizeSuspiciousCodeWithGZoltar(dp.classPaths, checkNotNull(Arrays.asList("")), dp.testCases);
		
		gzfl.sortSuspiciousCode(metric);
		suspiciousCandidates = new ArrayList<SuspiciousCode>(gzfl.candidates.size());
		suspiciousCandidates.addAll(gzfl.candidates);
		
		File parentFile = suspiciousFile.getParentFile();
		if (!parentFile.exists()) parentFile.mkdirs();
		suspiciousFile.createNewFile();
		ObjectOutputStream objectOutputStream = new ObjectOutputStream(new FileOutputStream(suspiciousFile));
        objectOutputStream.writeObject(suspiciousCandidates);
        objectOutputStream.close();
        
        return suspiciousCandidates;
	}
	
	private void readSuspiciousCodeFromFile(File suspiciousFile) {
		List<?> results = null;
		try {
			this.suspiciousCandidates = new ArrayList<SuspiciousCode>();
            ObjectInputStream objectInputStream = new ObjectInputStream(new FileInputStream(suspiciousFile));
            Object object = objectInputStream.readObject();
            if (object instanceof List<?>) {
            	results = (List<?>) object;
            }
            if (results != null) {
            	for (Object result : results) {
                	if (result instanceof SuspiciousCode) {
                		suspiciousCandidates.add((SuspiciousCode) result);
                	}
                }
            }
            objectInputStream.close();
        }catch (Exception e){
            log.error("Please Reload Fault Localization Result...");
        }
		
	}
}
