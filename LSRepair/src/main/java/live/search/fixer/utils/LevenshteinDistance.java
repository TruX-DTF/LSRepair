package live.search.fixer.utils;

import java.util.List;

public class LevenshteinDistance {

	public int computeLevenshteinDistance(List<String> s, List<String> t) {
		int sizeS = s.size();
		int sizeT = t.size();

		// Step 1
		if (sizeS == 0) return sizeT;
		if (sizeT == 0) return sizeS;
		
		// Step 2
		int d[][] = new int[sizeS + 1][sizeT + 1];// matrix
		int indexS; // iterates through s
		int indexT; // iterates through t
		for (indexS = 0; indexS <= sizeS; indexS++) d[indexS][0] = indexS;
		for (indexT = 0; indexT <= sizeT; indexT++) d[0][indexT] = indexT;

		// Step 3
		String iCharOfS; // i_th string of s
		String jCharOfT; // j_th string of t
		int cost; // cost
		for (indexS = 1; indexS <= sizeS; indexS++) {
			iCharOfS = s.get(indexS - 1);

			// Step 4
			for (indexT = 1; indexT <= sizeT; indexT++) {
				jCharOfT = t.get(indexT - 1);
				
				// Step 5
				if (iCharOfS == jCharOfT) {
					cost = 0;
				} else {
					cost = 1;
				}

				// Step 6
				d[indexS][indexT] = getMinimumValue(d[indexS - 1][indexT] + 1, 
													d[indexS][indexT - 1] + 1, 
													d[indexS - 1][indexT - 1] + cost);
			}
		}

		// Step 7
		return d[sizeS][sizeT];
	}
	
	private int getMinimumValue(int a, int b, int c) {
		int minimumValue = a;
		if (b < minimumValue)
			minimumValue = b;
		if (c < minimumValue)
			minimumValue = c;
		return minimumValue;
	}
}
