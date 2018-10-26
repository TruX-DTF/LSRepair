package live.search.fault.localization;

/**
 * Metrics of computing suspicious value of suspicious lines.
 */
public class Metrics {
	
	public Metric generateMetric(String metricStr) {
		Metric metric = null;
		if (metricStr.equals("Ample")) {
			metric = new Ample();
		} else if (metricStr.equals("Anderberg")) {
			metric = new Anderberg();
		} else if (metricStr.equals("Barinel")) {
			metric = new Barinel();
		} else if (metricStr.equals("Dice")) {
			metric = new Dice();
		} else if (metricStr.equals("DStar")) {
			metric = new DStar();
		} else if (metricStr.equals("Fagge")) {
			metric = new Fagge();
		} else if (metricStr.equals("Gp13")) {
			metric = new Gp13();
		} else if (metricStr.equals("Hamann")) {
			metric = new Hamann();
		} else if (metricStr.equals("Hamming")) {
			metric = new Hamming();
		} else if (metricStr.equals("Jaccard")) {
			metric = new Jaccard();
		} else if (metricStr.equals("Kulczynski1")) {
			metric = new Kulczynski1();
		} else if (metricStr.equals("Kulczynski2")) {
			metric = new Kulczynski2();
		} else if (metricStr.equals("M1")) {
			metric = new M1();
		} else if (metricStr.equals("McCon")) {
			metric = new McCon();
		} else if (metricStr.equals("Minus")) {
			metric = new Minus();
		} else if (metricStr.equals("Naish1")) {
			metric = new Naish1();
		} else if (metricStr.equals("Naish2")) {
			metric = new Naish2();
		} else if (metricStr.equals("Ochiai")) {
			metric = new Ochiai();
		} else if (metricStr.equals("Ochiai2")) {
			metric = new Ochiai2();
		} else if (metricStr.equals("Qe")) {
			metric = new Qe();
		} else if (metricStr.equals("RogersTanimoto")) {
			metric = new RogersTanimoto();
		} else if (metricStr.equals("RussellRao")) {
			metric = new RussellRao();
		} else if (metricStr.equals("SimpleMatching")) {
			metric = new SimpleMatching();
		} else if (metricStr.equals("Sokal")) {
			metric = new Sokal();
		} else if (metricStr.equals("Tarantula")) {
			metric = new Tarantula();
		} else if (metricStr.equals("Wong1")) {
			metric = new Wong1();
		} else if (metricStr.equals("Wong2")) {
			metric = new Wong2();
		} else if (metricStr.equals("Wong3")) {
			metric = new Wong3();
		} else if (metricStr.equals("Zoltar")) {
			metric = new Zoltar();
		}
		return metric;
	}

	public interface Metric {
		/**
		 * @param ef: number of executed and failed test cases.
		 * @param ep: number of executed and passed test cases.
		 * @param nf: number of un-executed and failed test cases.
		 * @param np: number of un-executed and passed test cases.
		 * @return
		 */
		double value(int ef, int ep, int nf, int np);
	}
	
	private class Ample implements Metric {
		public double value(int ef, int ep, int nf, int np) {
			return Math.abs(ef / (double) (ef + nf) - ep / (double) (ep + np));
		}
	}
	
	private class Anderberg implements Metric {
		public double value(int ef, int ep, int nf, int np) {
	        return ef / (double) (ef + 2 * (ep + nf));
	    }
	}
	
	private class Barinel implements Metric {
		public double value(int ef, int ep, int nf, int np) {
			return 1 - ep / (double) (ep + ef);
		}
	}
	
	private class Dice implements Metric {//SorensenDice
		public double value(int ef, int ep, int nf, int np) {
	        return 2 * ef / (double) (2 * ef + (ep + nf));
	    }
	}
	
	private class DStar implements Metric {
		public double value(int ef, int ep, int nf, int np) {
			return ef / (double) (ep + nf);
		}
	}

	private class Fagge implements Metric {
	    public double value(int ef, int ep, int nf, int np) {
	    	int _false = ef + nf;
	    	if (_false == 0) {
	    		return 0d;
	    	} else {
	    		return ef / (double) _false;
	    	}
	    }
	}
	
	private class Gp13 implements Metric {
	    public double value(int ef, int ep, int nf, int np) {
	    	return ef * (1 + 1 / (double) (2 * ep + ef));
	    }
	}
	
	private class Hamann implements Metric {
		public double value(int ef, int ep, int nf, int np) {
			return (ef + np - ep - nf) / (double) (ef + ep + nf + np);
		}
	}
	
	private class Hamming implements Metric {
	    public double value(int ef, int ep, int nf, int np) {
	    	return ef + np;
	    }
	}
	
	private class Jaccard implements Metric {
	    public double value(int ef, int ep, int nf, int np) {
	    	return ef / (double) (ef + ep + nf);
	    }
	}
	
	private class Kulczynski1 implements Metric {
	    public double value(int ef, int ep, int nf, int np) {
	    	return ef / (double) (nf + ep);
	    }
	}
	
	private class Kulczynski2 implements Metric {
	    public double value(int ef, int ep, int nf, int np) {
	        return (ef / (double) (nf + ep) + ef / (double) (ef + nf)) / 2;
	    }
	}
	
	private class M1 implements Metric {
	    public double value(int ef, int ep, int nf, int np) {
	        return (ef + np) / (double) (nf + ep);
	    }
	}
	
	private class McCon implements Metric {
		public double value(int ef, int ep, int nf, int np) {
			return (ef * ef - ep * nf) / (double) ((ef + nf) * (ef + ep));
		}
	}
	
	private class Minus implements Metric {
		public double value(int ef, int ep, int nf, int np) {
			return ef / (double) (ef + nf) / (ef / (double) (ef + nf) + ep / (double) (ep + np)) - (1 - ef / (double) (ef + nf) / (2 - ef / (double) (ef + nf) - ep / (double) (ep + np)));
		}
	}
	
	private class Naish1 implements Metric {
		public double value(int ef, int ep, int nf, int np) {
			if (ef == 0)
				return np;
			return -1;
		}
	}
	
	private class Naish2 implements Metric {
	    public double value(int ef, int ep, int nf, int np) {
	    	return ef - ep / (double) (ep + np + 1);
	    }
	}
	
	private class Ochiai implements Metric {
	    public double value(int ef, int ep, int nf, int np) {
	    	return ef / Math.sqrt((double) ((ef + ep) * (ef + nf)));
	    }
	}
	
	private class Ochiai2 implements Metric {
		public double value(int ef, int ep, int nf, int np) {
			return ef * np / Math.sqrt((double) ((ef + ep) * (ef + nf) * (np + ep) * (np + nf)));
		}
	}
	
	private class Qe implements Metric {
	    public double value(int ef, int ep, int nf, int np) {
	    	return  ef / (double) (ef + ep);
	    }
	}
	
	private class RogersTanimoto implements Metric {
	    public double value(int ef, int ep, int nf, int np) {
	    	return (ef + np) / (double) (ef + np + 2 * (nf + ep));
	    }
	}
	
	private class RussellRao implements Metric {
		public double value(int ef, int ep, int nf, int np) {
			return ef / (double) (ef + ep + nf + np);
		}
	}
	
	private class SimpleMatching implements Metric {
	    public double value(int ef, int ep, int nf, int np) {
	    	return (ef + np) / (double) (ef + nf + ep + np);
	    }
	}
	
	private class Sokal implements Metric {
	    public double value(int ef, int ep, int nf, int np) {
	    	return 2 * (ef + np) / (double) ( 2 * (ef + np) + nf + ep);
	    }
	}
	
	private class Tarantula implements Metric {
	    public double value(int ef, int ep, int nf, int np) {
	    	if (ef + nf == 0) {
				return 0;
			}
			return (ef / ((double) (ef + nf))) / ((ef / ((double) (ef + nf))) + (ep / ((double) (ep + np))));
	    }
	}
	
	private class Wong1 implements Metric {
	    public double value(int ef, int ep, int nf, int np) {
	        return  (double) ef;
	    }
	}
	
	private class Wong2 implements Metric {
	    public double value(int ef, int ep, int nf, int np) {
	        return  ef - ep;
	    }
	}
	
	private class Wong3 implements Metric {
	    public double value(int ef, int ep, int nf, int np) {
	    	double h;
	    	if (ep <= 2) {
	    		h = (double) ep;
	    	} else if (ep <= 10) {
	    		h = 2 + 0.1 * (double) (ep - 2);
	    	} else {
	    		h = 2.8 + 0.01 * (double) (ep -10);
	    	}
	    	return (double) ef - h;
	    }
	}
	
	private class Zoltar implements Metric {
		public double value(int ef, int ep, int nf, int np) {
			return ef / (double) (ef + nf + ep + (10000 * nf * ep) / (double) ef);
		}
	}
}
