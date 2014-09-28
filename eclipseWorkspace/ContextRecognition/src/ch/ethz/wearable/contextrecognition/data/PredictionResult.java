package ch.ethz.wearable.contextrecognition.data;

/*
 * Class containing the prediction result and the mean entropy that is return by
 * the Classifier class.
 */
public class PredictionResult {
	private int predictedClass;
	private double meanEntropy;
	
	//Constructor:
	public PredictionResult(int pred, double ent) {
		this.predictedClass = pred;
		this.meanEntropy = ent;
	}
	
	public int get_class() {
		return this.predictedClass;
	}
	
	public double get_entropy() {
		return this.meanEntropy;
	}
	
	public void set_class(int c) {
		this.predictedClass = c;
	}
	
	public void set_entropy(int e) {
		this.predictedClass = e;
	}
	
}
