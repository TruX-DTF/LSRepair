package live.search.fault.localization;

import java.io.Serializable;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import live.search.fault.localization.Metrics.Metric;

public class SuspiciousCode implements Serializable{

	private static final long serialVersionUID = -7698475119588343389L;
	
	/**
	 * Suspicious class.
	 */
	String className;
	/**
	 * Suspicious method.
	 */
	String methodName;
	/**
	 * Suspicious line number
	 */
	public int lineNumber;
	/**
	 * Suspicious value of the line
	 */
	double suspiciousValue;
	
	/**
	 * Key is the test identifier, value Numbers of time executed by that test.
	 */
	private Map<Integer,Integer> coverage = null;
	
	private String label;
    private float suspiciousWeight = 1;
	private int num_ef; // number of executed and failed test cases.
	private int num_ep; // number of executed and passed test cases.
	private int num_pt; // total number of passed test cases.
	private int num_ft; // total number of failed test cases.
	
    private List<String> tests = new ArrayList<>();
    private List<String> failedTests = new ArrayList<>();

	public SuspiciousCode() {
	}

	public SuspiciousCode(String className, String methodName, int lineNumber, double suspiciousValue, Map<Integer, Integer> frequency) {
		super();
		this.className = className;
		this.methodName = methodName;
		this.lineNumber = lineNumber;
		this.suspiciousValue = suspiciousValue;
		this.coverage = frequency;
	}

	public SuspiciousCode(String className, String methodName, double susp) {
		super();
		this.className = className;
		this.methodName = methodName;
		this.suspiciousValue = susp;
	}
	
	public SuspiciousCode(String className, String methodName, int lineNumber, Metric metric,
			int efn, int epn, int npn, int nfn, String label) {
		super();
		this.className = className;
		this.methodName = methodName;
		this.lineNumber = lineNumber;
		this.num_ef = efn;
		this.num_ep = epn;
		this.num_pt = npn;
		this.num_ft = nfn;
		this.label = label;
		
		if (this.label.contains("getOffsetFromLocal")){
            resetSuspiciousness(metric);
            this.suspiciousValue *= 10;
        } else resetSuspiciousness(metric);
	}
    
	private void resetSuspiciousness(Metric metric) {
		if (label.contains("(") && label.contains(")")) {
			if (StringUtils.isNumeric(label.substring(label.lastIndexOf("(") + 1, label.lastIndexOf(")")))) {
				this.suspiciousValue = metric.value(num_ef, num_ep, num_ft, num_pt) / 4 * suspiciousWeight;
				return;
			}
		}
		this.suspiciousValue = metric.value(num_ef, num_ep, num_ft, num_pt) * suspiciousWeight;
	}

	public int getLineNumber() {
		return lineNumber;
	}

	public void setLineNumber(int lineNumber) {
		this.lineNumber = lineNumber;
	}

	public double getSuspiciousValue() {
		return suspiciousValue;
	}

	private DecimalFormat df = new DecimalFormat("#.####");

	public String getSuspiciousValueString() {
		return df.format(this.suspiciousValue);
	}

	public void setSusp(double susp) {
		this.suspiciousValue = susp;
	}

	public String getClassName() {
		int i = className.indexOf("$");
		if (i != -1) {
			return className.substring(0, i);
		}

		return className;
	}

	public void setClassName(String className) {
		this.className = className;
	}

	public String getMethodName() {
		return methodName;
	}

	public void setMethodName(String methodName) {
		this.methodName = methodName;
	}

	@Override
	public String toString() {
		return "Candidate [className=" + className + ", methodName=" + methodName + ", lineNumber=" + lineNumber
				+ ", susp=" + suspiciousValue + "]";
	}

	public Map<Integer, Integer> getCoverage() {
		return coverage;
	}

	public void setCoverage(Map<Integer, Integer> coverage) {
		this.coverage = coverage;
	}

    public void setFailedTests(List<String> failedTests){
        this.failedTests = failedTests;
    }
    
    public void setTests(List<String> tests){
        this.tests = tests;
    }

    public List<String> getTests(){
        return tests;
    }
    
    public List<String> getFailedTests(){
        return failedTests;
    }

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof SuspiciousCode) {
			SuspiciousCode s = (SuspiciousCode) obj;
			if (s.className.equals(this.className) && s.methodName.equals(this.methodName) && s.lineNumber == this.lineNumber) {
				return true;
			}
		}
		return false;
	}

}
