package live.search.fixer.utils;

import java.util.List;

public class Patch {
	public String className;     // e.g., org.jfree.chart.plot.XYPlot
	public String testClassName; // e.g., org.jfree.chart.plot.junit.XYPlotTests
	public String testMethodName;// e.e., testRemoveRangeMarker
	public String patchMethodCode = null;
	public List<String> patchStatementCode;

}
