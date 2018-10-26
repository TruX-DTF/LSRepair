package live.search.space;

import java.io.Serializable;
import java.util.List;

public class MethodCandidate implements Serializable {

	private static final long serialVersionUID = 20180330L;
	
	public String packageName = "";
	public String methodName = null;
	public String signature;
	public List<String> rawTokens;
	public String info;
	public String bodyCode;
	public Integer levenshteinDistance;
}
