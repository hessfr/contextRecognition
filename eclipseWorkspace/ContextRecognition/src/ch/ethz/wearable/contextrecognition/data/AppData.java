package ch.ethz.wearable.contextrecognition.data;

import java.util.ArrayList;

/*
 * Class containing data of buffers, threshold values, ... to be stored in JSON file
 */
public class AppData {
	
	private ArrayList<Boolean> initThresSet = new ArrayList<Boolean>();
	
	private ArrayList<Boolean> thresSet = new ArrayList<Boolean>();
	
	private ArrayList<Boolean> feedbackReceived = new ArrayList<Boolean>();
	
	private ArrayList<Double> threshold = new ArrayList<Double>();
	
	private ArrayList<ArrayList<Double>> initThresBuffer = new ArrayList<ArrayList<Double>>();
	
	private ArrayList<ArrayList<Double>> thresBuffer = new ArrayList<ArrayList<Double>>();
	
	private ArrayList<Double> thresQueriedInterval = new ArrayList<Double>();
	
	int numQueriesLeft;

	// Constructor:
	public AppData(ArrayList<Boolean> initThresSet, 
			ArrayList<Boolean> thresSet, ArrayList<Boolean> feedbackReceived, 
			ArrayList<Double> threshold, ArrayList<ArrayList<Double>> initThresBuffer,
			ArrayList<ArrayList<Double>> thresBuffer, ArrayList<Double> thresQueriedInterval,
			int numQueriesLeft) {
		
		this.initThresSet = initThresSet;
		this.thresSet = thresSet;
		this.feedbackReceived = feedbackReceived;
		this.threshold = threshold;
		this.initThresBuffer = initThresBuffer;
		this.thresBuffer = thresBuffer;
		this.thresQueriedInterval = thresQueriedInterval;
		this.numQueriesLeft = numQueriesLeft;
		
	}
	
	public ArrayList<Boolean> get_initThresSet() {
		return this.initThresSet;
	}
	
	public ArrayList<Boolean> get_thresSet() {
		return this.thresSet;
	}
	
	public ArrayList<Boolean> get_feedbackReceived() {
		return this.feedbackReceived;
	}
	
	public ArrayList<Double> get_threshold() {
		return this.threshold;
	}

	public ArrayList<ArrayList<Double>> get_initThresBuffer() {
		return this.initThresBuffer;
	}
	
	public ArrayList<Double> get_thresQueriedInterval() {
		return this.thresQueriedInterval;
	}
	
	public ArrayList<ArrayList<Double>> get_thresBuffer() {
		return this.thresBuffer;
	}
	
	public int get_numQueriesLeft() {
		return this.numQueriesLeft;
	}
}






























