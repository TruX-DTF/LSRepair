package live.search.fault.localization.utils;

import java.io.File;
import java.security.MessageDigest;

import live.search.config.Configuration;

public class FileUtils {

	public static String getMD5(String s) {
		char hexDigits[] = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F' };
		try {
			byte[] btInput = s.getBytes("utf-8");
			MessageDigest mdInst = MessageDigest.getInstance("MD5");
			mdInst.update(btInput);
			byte[] md = mdInst.digest();
			int j = md.length;
			char str[] = new char[j * 2];
			int k = 0;
			for (int i = 0; i < j; i++) {
				byte byte0 = md[i];
				str[k++] = hexDigits[byte0 >>> 4 & 0xf];
				str[k++] = hexDigits[byte0 & 0xf];
			}
			return new String(str);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	public static String getFileAddressOfJava(String srcPath, String className) {
		if (className.contains("<") && className.contains(">")) {
			className = className.substring(0, className.indexOf("<"));
		}
		return srcPath.trim() + System.getProperty("file.separator")
				+ className.trim().replace('.', System.getProperty("file.separator").charAt(0)) + ".java";
	}

	public static String getFileAddressOfClass(String classPath, String className) {
		if (className.contains("<") && className.contains(">")) {
			className = className.substring(0, className.indexOf("<"));
		}
		return classPath.trim() + System.getProperty("file.separator")
				+ className.trim().replace('.', System.getProperty("file.separator").charAt(0)) + ".class";
	}

	public static String tempJavaPath(String classname, String identifier) {
		new File(Configuration.TEMP_FILES_PATH + identifier).mkdirs();
		return Configuration.TEMP_FILES_PATH + identifier + "/" + classname.substring(classname.lastIndexOf(".") + 1) + ".java";
	}

	public static String tempClassPath(String classname, String identifier) {
		new File(Configuration.TEMP_FILES_PATH + identifier).mkdirs();
		return Configuration.TEMP_FILES_PATH + identifier + "/" + classname.substring(classname.lastIndexOf(".") + 1) + ".class";
	}

}
