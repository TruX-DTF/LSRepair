package live.search.fault.localization;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gzoltar.core.GZoltar;
import com.gzoltar.core.components.Statement;
import com.gzoltar.core.instr.testing.TestResult;

import live.search.fault.localization.Metrics.Metric;

public class GZoltarFaultLoclaization {
	
	private static Logger logger = LoggerFactory.getLogger(GZoltarFaultLoclaization.class);
	
	public String srcPath;
	
	private int totalFailedTestCases = 0;
	private int totalSuccessfulTestCses = 0;
	
    public Double threshold = 0.01;      // The threshold of suspiciousness.
    public int maxSuspCandidates = 1000; // The number of top-X suspicious candidates.

	private List<TestResult> gzoltarTestResults;
	private List<Statement> suspiciousStatements;
	
	public List<SuspiciousCode> candidates = new ArrayList<SuspiciousCode>();
	
	public void localizeSuspiciousCodeWithGZoltar(final URL[] clazzPaths, Collection<String> packageNames, String... testClasses) {
		ArrayList<String> classPaths = new ArrayList<String>();
        for (URL url : clazzPaths) {
            if ("file".equals(url.getProtocol())) {
                classPaths.add(url.getPath());
            } else {
                classPaths.add(url.toExternalForm());
            }
        }
        
        try {
			GZoltar gzoltar = new GZoltar(System.getProperty("user.dir"));
			
			gzoltar.setClassPaths(classPaths);
	        gzoltar.addPackageNotToInstrument("org.junit");
	        gzoltar.addPackageNotToInstrument("junit.framework");
	        gzoltar.addTestPackageNotToExecute("junit.framework");
	        gzoltar.addTestPackageNotToExecute("org.junit");
	        for (String packageName : packageNames) {
	            gzoltar.addPackageToInstrument(packageName);
	        }
	        for (URL url: clazzPaths){
	            if (url.getPath().endsWith(".jar")){
	                gzoltar.addClassNotToInstrument(url.getPath());
	                gzoltar.addPackageNotToInstrument(url.getPath());
	            }
	        }
	        
	        for (String className : checkNotNull(testClasses)) {
	            gzoltar.addTestToExecute(className);        // we want to execute the test
	            gzoltar.addClassNotToInstrument(className); // we don't want to include the test as root-cause candidate
	        }
	        gzoltar.run();
	        
	        gzoltarTestResults = gzoltar.getTestResults();
	        suspiciousStatements = gzoltar.getSuspiciousStatements();
			Collections.sort(this.suspiciousStatements, new Comparator<Statement>() {
				@Override
				public int compare(Statement o1, Statement o2) {
					if (o2.getSuspiciousness() == o1.getSuspiciousness()) {
						return Integer.compare(o2.getLineNumber(), o1.getLineNumber());
					}
					return Double.compare(o2.getSuspiciousness(), o1.getSuspiciousness());
				}
			});
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
    }
	
	/**
	 * Sort suspicious code with GZoltar results.
	 */
	public void sortSuspiciousCode() {
		parseTestResults();
		
		List<SuspiciousCode> allCandidates = new ArrayList<>();
		for (Statement gzoltarStatement : suspiciousStatements) {
			String className = gzoltarStatement.getMethod().getParent().getLabel();
			if (!isSource(className)) continue;
			
			Double suspiciousness = gzoltarStatement.getSuspiciousness();
			if (suspiciousness.equals(0.0d)) break;
			
			BitSet coverage = gzoltarStatement.getCoverage();
            int nextTest = coverage.nextSetBit(0);
            
            List<String> tests = new ArrayList<>();
            List<String> failedTests = new ArrayList<>();
            while (nextTest != -1) {
            	TestResult testResult = this.gzoltarTestResults.get(nextTest);
                tests.add(testResult.getName());
                if(!testResult.wasSuccessful()) {
                    failedTests.add(testResult.getName());
                }
                nextTest = coverage.nextSetBit(nextTest + 1);
            }
            
            String methodName = gzoltarStatement.getMethod().toString();
			int lineNumber = gzoltarStatement.getLineNumber();
			
            SuspiciousCode suspiciousCode = new SuspiciousCode(className, methodName, lineNumber, suspiciousness, null);
            suspiciousCode.setTests(tests);
            suspiciousCode.setFailedTests(failedTests);
            
        	if (suspiciousness >= threshold) {
                candidates.add(suspiciousCode);
			}
        	allCandidates.add(suspiciousCode);
            
		}
		// If we do not have candidate due to the threshold is too high, we add all as suspicious
		if (candidates.isEmpty()) candidates.addAll(allCandidates);

		// Select the best top-X candidates.
		int size = candidates.size();
		if (maxSuspCandidates < size) {
			candidates = candidates.subList(0, maxSuspCandidates);
		}
		logger.info("Gzoltar found: " + size + " with suspiciousness > " + threshold + ", we consider top-" + candidates.size());
	}

	/**
	 * Sort suspicious code with specific metric.
	 * @param metric
	 */
	public void sortSuspiciousCode(Metric metric) {
		if (metric == null) {
			sortSuspiciousCode();
			return;
		}
		parseTestResults();
		
		List<SuspiciousCode> allCandidates = new ArrayList<>();
		for (Statement statement : suspiciousStatements) {
			String className = statement.getMethod().getParent().getLabel();
			if (!isSource(className)) continue;
			BitSet coverage = statement.getCoverage();
            int executedAndPassedCount = 0;
            int executedAndFailedCount = 0;
            int nextTest = coverage.nextSetBit(0);
            
            List<String> tests = new ArrayList<>();
            List<String> failedTests = new ArrayList<>();
            while (nextTest != -1) {
            	TestResult testResult = this.gzoltarTestResults.get(nextTest);
                tests.add(testResult.getName());
                if(testResult.wasSuccessful()) {
                    executedAndPassedCount++;
                } else {
                    executedAndFailedCount++;
                    failedTests.add(testResult.getName());
                }
                nextTest = coverage.nextSetBit(nextTest + 1);
            }
            
            String methodName = statement.getMethod().toString();
			int lineNumber = statement.getLineNumber();
            SuspiciousCode suspiciousCode = new SuspiciousCode(className, methodName, lineNumber, metric,
            		executedAndFailedCount, executedAndPassedCount, this.totalSuccessfulTestCses - executedAndPassedCount,
            		this.totalFailedTestCases - executedAndFailedCount, statement.getLabel());
            suspiciousCode.setTests(tests);
            suspiciousCode.setFailedTests(failedTests);
            if (suspiciousCode.getSuspiciousValue() >= threshold) {
                candidates.add(suspiciousCode);
			}
            if (suspiciousCode.getSuspiciousValue() > 0) {
                allCandidates.add(suspiciousCode);
			}
		}
		if (candidates.isEmpty()) candidates.addAll(allCandidates);
		
		// Order the suspicious DESC
//		Collections.sort(candidates, (c1, c2) -> Double.compare(c2.getSuspiciousValue(), c1.getSuspiciousValue()));
		Collections.sort(candidates, new Comparator<SuspiciousCode>() {

			@Override
			public int compare(SuspiciousCode o1, SuspiciousCode o2) {
				// reversed parameters because we want a descending order list
                if (o2.getSuspiciousValue() == o1.getSuspiciousValue()){
                	int compareName = o2.getClassName().compareTo(o1.getClassName());
                	if (compareName == 0) {
                		return Integer.compare(o2.getLineNumber(),o1.getLineNumber());
                	}
                    return compareName;
                }
                return Double.compare(o2.getSuspiciousValue(), o1.getSuspiciousValue());
			}
			
		});
		
		// Select the best top-X candidates.
		int size = candidates.size();
		if (maxSuspCandidates < size) {
			candidates = candidates.subList(0, maxSuspCandidates);
		}
		logger.info("Gzoltar found: " + size + " with suspiciousness > " + threshold + ", we consider top-" + candidates.size());
	}
	
	private void parseTestResults() {
        List<String> failingTestCases = new ArrayList<String>();
		for (TestResult tr : this.gzoltarTestResults) {
			String testName = tr.getName().split("#")[0];
			if (testName.startsWith("junit")) {
				continue;
			}
			
			if (tr.wasSuccessful()) {
				totalSuccessfulTestCses ++;
			} else {
				totalFailedTestCases ++;
//				logger.debug("Test failed: " + tr.getName());
				System.err.println("Test failed: " + tr.getName());
				failingTestCases.add(testName.split("\\#")[0]);
			}
		}
        logger.info("Gzoltar Test Result Total: " + (totalSuccessfulTestCses + totalFailedTestCases) + 
        		", fails: " + totalFailedTestCases + ", GZoltar suspicious " + suspiciousStatements.size());
	}
    
    private boolean isSource(String compName) {
    	// compName: org.apache.commons.math.linear.Array2DRowRealMatrix
    	String srcFile = srcPath + compName.replace(".", "/") + ".java";
    	return new File(srcFile).exists();
	}
    
}
