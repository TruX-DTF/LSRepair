package live.search.fault.localization.utils;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

public class TestUtils {

	public static int getFailTestNumInProject(String projectName, String defects4jPath, List<String> failedTests){
        String testResult = getDefects4jResult(projectName, defects4jPath, "test");
        if (testResult.equals("")){//error occurs in run
            return Integer.MAX_VALUE;
        }
        if (!testResult.contains("Failing tests:")){
            return Integer.MAX_VALUE;
        }
        int errorNum = 0;
        String[] lines = testResult.trim().split("\n");
        for (String lineString: lines){
            if (lineString.contains("Failing tests:")){
                errorNum =  Integer.valueOf(lineString.split(":")[1].trim());
            } else {
            	failedTests.add(lineString);
            }
        }
        return errorNum;
	}
	
	public static int compileProjectWithDefects4j(String projectName, String defects4jPath) {
		String compileResults = getDefects4jResult(projectName, defects4jPath, "compile").trim();
		String[] lines = compileResults.trim().split("\n");
		if (lines.length != 2) return 1;
        for (String lineString: lines){
        	if (!lineString.endsWith("OK")) return 1;
        }
		return 0;
	}

	private static String getDefects4jResult(String projectName, String defects4jPath, String cmdType) {
		try {
            String result = ShellUtils.shellRun(Arrays.asList("cd " + projectName + "\n", defects4jPath + "framework/bin/defects4j " + cmdType));
            return result;
        } catch (IOException e){
            return "";
        }
	}

	public static String recoverWithGitCmd(String projectName) {
		try {
            ShellUtils.shellRun(Arrays.asList("cd " + projectName + "\n", "git checkout -- ."));
            return "";
        } catch (IOException e){
            return "Failed to recover.";
        }
	}
	
	public static String buildClasspath(List<String> additionalPath, String classPath, String testClassPath){
        String path = "\"";
        path += classPath;
        path += System.getProperty("path.separator");
        path += testClassPath;
        path += System.getProperty("path.separator");
        path += JunitRunner.class.getProtectionDomain().getCodeSource().getLocation().getFile();
        path += System.getProperty("path.separator");
        path += StringUtils.join(additionalPath,System.getProperty("path.separator"));
        path += "\"";
        return path;
    }

}
