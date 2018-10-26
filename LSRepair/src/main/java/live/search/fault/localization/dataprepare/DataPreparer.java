package live.search.fault.localization.dataprepare;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import code.parser.utils.FileHelper;
import live.search.fault.localization.utils.JavaLibrary;
import live.search.fault.localization.utils.PathUtils;

/**
 * Prepare data for fault localization.
 *
 */
public class DataPreparer {

    private String buggyProjectParentPath;
    
    public String classPath;
    public String srcPath;
    public String testClassPath;
    public String testSrcPath;
    public List<String> libPaths = new ArrayList<>();
    public boolean validPaths = true;
    public String[] testCases;
    public URL[] classPaths;
    
    public DataPreparer(String path){
        if (!path.endsWith("/")){
            path += "/";
        }
        buggyProjectParentPath = path;
    }
    
    public void prepareData(String buggyProject){
//		libPath.add(FromString.class.getProtectionDomain().getCodeSource().getLocation().getFile());
//		libPath.add(EasyMock.class.getProtectionDomain().getCodeSource().getLocation().getFile());
//		libPath.add(IOUtils.class.getProtectionDomain().getCodeSource().getLocation().getFile());
		
		loadPaths(buggyProject);
		
		if (!checkProjectDirectories()){
			validPaths = false;
			return;
		}
		
		loadTestCases();
		
		loadClassPaths();
    }

	private void loadPaths(String buggyProject) {
		String projectDir = buggyProjectParentPath;
		List<String> paths = PathUtils.getSrcPath(buggyProject);
		classPath = projectDir + buggyProject + paths.get(0);
		testClassPath = projectDir + buggyProject + paths.get(1);
		srcPath = projectDir + buggyProject + paths.get(2);
		testSrcPath = projectDir + buggyProject + paths.get(3);

//		File libPkg = new File(projectDir + project + "/lib/");
//		if (libPkg.exists() && libPkg.list() != null) {
//			for (String p : libPkg.list()) {
//				if (p.endsWith(".jar")) {
//					libPaths.add(libPkg.getAbsolutePath() + "/" + p);
//				}
//			}
//		}
//		libPkg = new File(projectDir + project + "/build/lib/");
//		if (libPkg.exists() && libPkg.list() != null) {
//			for (String p : libPkg.list()) {
//				if (p.endsWith(".jar")) {
//					libPaths.add(libPkg.getAbsolutePath() + "/" + p);
//				}
//			}
//		}
		List<File> libPackages = new ArrayList<>();
		if (new File(projectDir + buggyProject + "/lib/").exists()) {
			libPackages.addAll(FileHelper.getAllFiles(projectDir + buggyProject + "/lib/", ".jar"));
		}
		if (new File(projectDir + buggyProject + "/build/lib/").exists()) {
			libPackages.addAll(FileHelper.getAllFiles(projectDir + buggyProject + "/build/lib/", ".jar"));
		}
		for (File libPackage : libPackages) {
			libPaths.add(libPackage.getAbsolutePath());
		}
	}
	
	private boolean checkProjectDirectories() {
		if (!new File(classPath).exists()) {
			System.err.println("Class path: " + classPath + " does not exist!");
			return false;
		}
		if (!new File(srcPath).exists()) {
			System.err.println("Source code path: " + srcPath + " does not exist!");
			return false;
		}
		if (!new File(testClassPath).exists()) {
			System.err.println("Test class path: " + testClassPath + " does not exist!");
			return false;
		}
		if (!new File(testSrcPath).exists()) {
			System.err.println("Test source path: " + testSrcPath + " does not exist!");
			return false;
		}
		return true;
	}

	private void loadTestCases() {
		testCases = new TestClassesFinder().findIn(JavaLibrary.classpathFrom(testClassPath), false);
		Arrays.sort(testCases);
	}

	private void loadClassPaths() {
		classPaths = JavaLibrary.classpathFrom(testClassPath);
		classPaths = JavaLibrary.extendClasspathWith(classPath, classPaths);
		if (libPaths != null) {
			for (String lpath : libPaths) {
				classPaths = JavaLibrary.extendClasspathWith(lpath, classPaths);
			}
		}
	}
    
}
