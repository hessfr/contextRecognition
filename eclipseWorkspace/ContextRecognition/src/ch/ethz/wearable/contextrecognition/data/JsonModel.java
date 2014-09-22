package ch.ethz.wearable.contextrecognition.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
/*
This class is meant to store the data from the JSON file containing the information of the GMM. It represents a Gaussian model for one context class
It should only used initially and should then be converted to a contextClassModel
*/
public class JsonModel {
	
		private Map<String, Integer> classesDict = new HashMap<String, Integer>();		
		private int n_classes;
		private ArrayList<Double> scale_means = new ArrayList<Double>(); //1-dim ArrayList
		private ArrayList<Double> scale_stddevs = new ArrayList<Double>(); //1-dim ArrayList
		
		private int n_components;
		private int n_features;
		private int n_train; // number of training points, needed for model adaption
		
		private ArrayList<ArrayList<Double>> means = new ArrayList<ArrayList<Double>>(); //2-dim ArrayList
		private ArrayList<Double> weights = new ArrayList<Double>(); //1-dim ArrayList
		private ArrayList<ArrayList<ArrayList<Double>>> covars = new ArrayList<ArrayList<ArrayList<Double>>>(); //3-dim ArrayList

		// Methods to access data:
		public int get_n_classes() {
			return n_classes;
		}
		
		public Map<String, Integer> get_classesDict() {
			return classesDict;
		}
		
		public ArrayList<Double> get_scale_means() {
			return scale_means;
		}
		
		public ArrayList<Double> get_scale_stddevs() {
			return scale_stddevs;
		}
		
		public int get_n_components() {
			return n_components;
		}
		
		public int get_n_features() {
			return n_features;
		}
		
		public int get_n_train() {
			return n_train;
		}
		
		public ArrayList<ArrayList<Double>> get_means() {
			return means;
		}
		
		public ArrayList<Double> get_weights() {
			return weights;
		}
		
		public ArrayList<ArrayList<ArrayList<Double>>> get_covars() {
			return covars;
		}
		
		
		// Methods to set data:
		public void set_n_classes(int n_classes_new) {
			n_classes = n_classes_new;
		}
		
		public void set_classesDict(Map<String,Integer> classesDict_new) {
			classesDict = classesDict_new;
		}
		
		public void set_scale_means(ArrayList<Double> scale_means_new) {
			scale_means = scale_means_new;
		}
		
		public void set_scale_stddevs(ArrayList<Double> scale_stddevs_new) {
			scale_stddevs = scale_stddevs_new;
		}
		
		public void set_n_components(int n_components_new) {
			n_components = n_components_new;
		}
		
		public void set_n_features(int n_features_new) {
			n_features = n_features_new;
		}
		
		public void set_n_train(int n_train_new) {
			n_train = n_train_new;
		}
		
		public void set_means(ArrayList<ArrayList<Double>> means_new) {
			means = means_new;
		}
		
		public void set_weights(ArrayList<Double> weights_new) {
			weights = weights_new;
		}
		
		public void set_covars(ArrayList<ArrayList<ArrayList<Double>>> covars_new) {
			covars = covars_new;
		}
	
}
