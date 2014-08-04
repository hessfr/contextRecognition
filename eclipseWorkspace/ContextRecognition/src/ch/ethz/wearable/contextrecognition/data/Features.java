package ch.ethz.wearable.contextrecognition.data;

import ch.ethz.wearable.contextrecognition.utils.Globals;

/*
 * Data container for the feature values consisting of 12 MFCC values and the 
 * log energy
 */
public class Features {

    private double [] features;

	public Features() {
		features = new double[Globals.NUM_FEATURES];
	}

    public double [] get() {
		return features;
	}
    
    public void set(double [] features) {
		this.features = features;
	}
    
}
