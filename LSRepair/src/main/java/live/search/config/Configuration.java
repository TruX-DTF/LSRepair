package live.search.config;

public class Configuration {
	public static final String[] METRICS = {"Ample", "Anderberg", "Dice", "Fagge", "Gp13", "Hamming", "Jaccard", "Kulczynski1", "M1",
			"Naish1", "Naish2", "Ochiai", "Qe", "RogersTanimoto", "SimpleMatching", "Sokal", "SorensenDice", "Tarantula", "Wong1", "Zoltar", "null"};
	
    //---------------Timeout Config------------------
    public static int TOTAL_RUN_TIMEOUT = 10800;

    public static final int SHELL_RUN_TIMEOUT = 10800;
    public static final int GZOLTAR_RUN_TIMEOUT = 600;
    public static final int SEARCH_BOUNDARY_TIMEOUT = TOTAL_RUN_TIMEOUT/5;

    //--------------Result Path Config------------------
    public static final String RESULT_PATH = "resultMessage";
    public static final String PATCH_PATH = RESULT_PATH + "/patch";
    public static final String PATCH_SOURCE_PATH = RESULT_PATH + "/patchSource";
    public static final String LOCALIZATION_PATH = RESULT_PATH + "/localization";
    public static final String RUNTIMEMESSAGE_PATH = RESULT_PATH + "/runtimeMessage";
    public static final String PREDICATE_MESSAGE_PATH = RESULT_PATH + "/predicateMessage";
    public static final String FIX_RESULT_FILE_PATH = RESULT_PATH + "/fixResult.log";


    //--------------Runtime Path Config--------------------
    public static final String TEMP_FILES_PATH = ".temp/";
    public static final String LOCALIZATION_RESULT_CACHE = ".suspicious/";

    //--------------Search Space File Config--------------------
	public static final String SEARCH_SPACE_FILE = ".SearchSpace/searchSpace.ss";
}
