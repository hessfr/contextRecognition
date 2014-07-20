package ch.ethz.wearable.contextrecognition.tools;

import org.ejml.data.DenseMatrix64F;

/*
 * This class is used as a container for returned values in the methods mergeComponents(), addHistoricalComponent(),
 * and addNovelComponent() in adaptGMM. It is used for a single component only.
 */
public class ComponentObject {
	private DenseMatrix64F means; // (1 x n_features)
	private double weight; 
	private DenseMatrix64F covars; // (n_features x n_features)
	
	
	public DenseMatrix64F get_means() {
		return means;
	}
	
	public double get_weight() {
		return weight;
	}
	
	public DenseMatrix64F get_covars() {
		return covars;
	}
	
	public void set_means(DenseMatrix64F means_new) {
		means = means_new;
	}
	
	public void set_weight(double weight_new) {
		weight = weight_new;
	}
	
	public void set_covars(DenseMatrix64F covars_new) {
		covars = covars_new;
	}
	
}
