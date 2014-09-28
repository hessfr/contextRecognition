package ch.ethz.wearable.contextrecognition.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;

/*
 * Data container class to persist the predictions (i.e. the class names and the numbers how often 
 * they were predicted) as a JSON file to the storage using GSON.
 * 
 * This persisting will be done once at the end of the day.
 */
public class HistoricPredictions {

	// Each of these ArrayLists has to have exactly the same size:
	private ArrayList<ArrayList<Integer>> predictionList = new ArrayList<ArrayList<Integer>>();
	private ArrayList<Integer> silenceList = new ArrayList<Integer>();
	private ArrayList<ArrayList<String>> contextClassesList = new ArrayList<ArrayList<String>>(); 
	private ArrayList<Date> dateList = new ArrayList<Date>();
	
	// Constructor (only called if the JSON file does not exist yet):
	public HistoricPredictions(ArrayList<Integer> pred, int silenceCount,
			String[] classNamesArray, Date d) {
		
		this.predictionList.add(pred);
		this.silenceList.add(silenceCount);
		this.contextClassesList.add(new ArrayList<String>(Arrays.asList(classNamesArray)));
		this.dateList.add(d);
		
	}
	
	public ArrayList<ArrayList<Integer>> get_prediction_list() {
		return this.predictionList;
	}
	
	public ArrayList<Integer> get_silence_list() {
		return this.silenceList;
	}
	
	public ArrayList<ArrayList<String>> get_context_class_list() {
		return this.contextClassesList;
	}
	
	public ArrayList<Date> get_date_list() {
		return this.dateList;
	}
	
	public int get_size() {
		return this.predictionList.size();
	}
	
	public void set_prediction_list(ArrayList<ArrayList<Integer>> p) {
		this.predictionList = p;
	}
	
	public void set_silence_list(ArrayList<Integer> s) {
		this.silenceList = s;
	}
	
	public void set_context_class_list(ArrayList<ArrayList<String>> c) {
		this.contextClassesList = c;
	}
	
	public void set_date_list(ArrayList<Date> d) {
		this.dateList = d;
	}
	
	public void append_to_prediction_list(ArrayList<Integer> p) {
		this.predictionList.add(p);
	}
	
	public void append_to_silence_list(Integer s) {
		this.silenceList.add(s);
	}
	
	public void append_to_context_class_list(ArrayList<String> c) {
		this.contextClassesList.add(c);
	}
	
	public void append_to_context_class_list(String[] c) {
		this.contextClassesList.add(new ArrayList<String>(Arrays.asList(c)));
	}
	
	public void append_to_date_list(Date d) {
		this.dateList.add(d);
	}
}
