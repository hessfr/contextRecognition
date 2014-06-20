package com.example.tools;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.ejml.data.DenseMatrix64F;

import android.os.Environment;
import android.util.Log;

import com.google.gson.Gson;

/*
Contains parameters for the Gaussian mixture of all context classes
*/
public class GMM {
	
	private static String TAG = "GMM";
	
	private ArrayList<ContextClassModel> clfs = new ArrayList<ContextClassModel>();
	
	private Map<String, Integer> classesDict = new HashMap<String, Integer>();
	private int n_classes;
	private int n_features;
	private DenseMatrix64F scale_means = new DenseMatrix64F(1, n_features);
	private DenseMatrix64F scale_stddevs = new DenseMatrix64F(1, n_features);
	
	
	// Constructor:
	public GMM(String filename) {
		
		List<JsonModel> jsonGMM = parseGSON(filename);
		
		convertJSONtoGMM(jsonGMM);
	}
	
	public List<JsonModel> parseGSON(String filename) {
		
		Gson gson = new Gson();
		
		List<JsonModel> jsonGMM;

		File dir = Environment.getExternalStorageDirectory();
		
		File file = new File(dir,filename);

		if(file.exists()) {
			
			Log.i(TAG,"JSON file found");
			
			try {
				BufferedReader br = new BufferedReader(new FileReader(file));
			
				jsonGMM = Arrays.asList(gson.fromJson(br, JsonModel[].class));
				
			} catch (IOException e) {
				jsonGMM = null;
				Log.e(TAG,"Couldn't open JSON file");
				e.printStackTrace();
			}
		} else {
			jsonGMM = null;
			Log.e(TAG, "File does not exist: " + file.toString());
        }
		
		return jsonGMM;
	}
	
	// Fill data according to the provided jsonModel
	private void convertJSONtoGMM(List<JsonModel> jsonGMM) {
		
		if (jsonGMM.size() != 0) {
			
			n_classes = jsonGMM.size();
			classesDict = jsonGMM.get(0).get_classesDict();
			n_features = jsonGMM.get(0).get_n_features();
			scale_means = convertToEJML_1D_row(jsonGMM.get(0).get_scale_means());
			scale_stddevs = convertToEJML_1D_row(jsonGMM.get(0).get_scale_stddevs());
			
			for(int i=0; i<n_classes; i++) {
				ContextClassModel newContextClass = new ContextClassModel();
				
				newContextClass.set_n_components(jsonGMM.get(i).get_n_components());
				newContextClass.set_n_features(jsonGMM.get(i).get_n_features());
				newContextClass.set_n_train(jsonGMM.get(i).get_n_train());
				
				ArrayList<Double> tmpWeights = jsonGMM.get(i).get_weights();
				newContextClass.set_weights(convertToEJML_1D_row(tmpWeights));
				
				ArrayList<ArrayList<Double>> tmpMeans = jsonGMM.get(i).get_means();
				newContextClass.set_means(convertToEJML_2D(tmpMeans));
				
				ArrayList<ArrayList<ArrayList<Double>>> tmpCovars = jsonGMM.get(i).get_covars();
				newContextClass.set_covars(convertToEJML_3D(tmpCovars));
				
				clfs.add(newContextClass);
			}

		} else {
			//TODO:
		}
	}
	
	// Converts an ArrayList into a (1 x n) EJML DenseMatrix64F. If a (n x 1) matrix is needed, transpose the result
	private DenseMatrix64F convertToEJML_1D_row(ArrayList<Double> in) {
		
		int len = in.size();
		DenseMatrix64F out = new DenseMatrix64F(1, len);
		
		for(int i=0; i<len; i++) {
			out.set(0, i, in.get(i));
		}
		
		return out;
		
	}
	
	// Converts a 2 dimensional ArrayList into a EJML DenseMatrix64F
	private DenseMatrix64F convertToEJML_2D(ArrayList<ArrayList<Double>> in) {
		
		int nRows = in.size();
		int nCols = in.get(0).size();		
		
		DenseMatrix64F out = new DenseMatrix64F(nRows, nCols);
		
		for(int r=0; r<nRows; r++) {
			for(int c=0; c<nCols; c++) {
				out.set(r, c, in.get(r).get(c));
			}
			
		}
		
		return out;
		
	}
	
	// Converts a 3 dimensional ArrayList into an array of EJML DenseMatrix64F
	private DenseMatrix64F[] convertToEJML_3D(ArrayList<ArrayList<ArrayList<Double>>> in) {
		
		int nMatrices = in.size(); // number of 2D arrays
		int nRows = in.get(0).size();
		int nCols = in.get(0).get(0).size();		
		
		DenseMatrix64F[] out = new DenseMatrix64F[nMatrices];

		for(int i=0; i<nMatrices; i++) {
			out[i] = new DenseMatrix64F(nRows,nCols);
		}
		
		for(int m=0; m<nMatrices; m++) {
			for(int r=0; r<nRows; r++) {
				for(int c=0; c<nCols; c++) {
					out[m].set(r, c, in.get(m).get(r).get(c));
				}
				
			}
		}

		
		return out;
		
	}

	// Methods to access data:
	public ContextClassModel clf(int i) {
		return clfs.get(i);
	}
	
	public Map<String, Integer> get_classesDict() {
		return classesDict;
	}
	
	public int get_n_features() {
		return n_features;
	}
	
	public int get_n_classes() {
		return n_classes;
	}
	
	public DenseMatrix64F get_scale_means() {
		return scale_means;
	}
	
	public DenseMatrix64F get_scale_stddevs() {
		return scale_stddevs;
	}
	
	// Return name of the class for a given integer
	public String get_class_name(int i) {
		String res = new String();
		for (Entry<String, Integer> entry : classesDict.entrySet()) {
            if (entry.getValue() == i) {
            	res = entry.getKey();
            }
        }
		return res;	
	}

	
	// Methods to set data:
	public void set_clf(ContextClassModel mod, int i) {
		clfs.set(i, mod);
	}
	
	public void set_classesDict(Map<String, Integer> classesDict_new) {
		classesDict = classesDict_new;
	}
	
	public void set_n_features(int n_features_new) {
		n_features = n_features_new;
	}
	
	public void set_n_classes(int n_classes_new) {
		n_classes = n_classes_new;
	}
	
	public void set_scale_means(DenseMatrix64F scale_means_new) {
		scale_means = scale_means_new;
	}
	
	public void set_scale_stddevs(DenseMatrix64F scale_stddevs_new) {
		scale_stddevs = scale_stddevs_new;
	}
	
}
