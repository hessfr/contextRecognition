package ch.ethz.wearable.contextrecognition.data;

//TODO: Check if still needed

/*
 * Data container for one MFCC values of one point
 */
public class Mfccs {

    private double [] mfccs;

	public Mfccs() {
		mfccs = new double[12];
	}

    public double [] get() {
		return mfccs;
	}

    public void set(double [] mfccs) {
		this.mfccs = mfccs;
	}

}
