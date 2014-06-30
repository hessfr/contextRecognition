package com.example.tools;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
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
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import com.google.gson.Gson;
/*
Contains parameters for the Gaussian mixture of all context classes
*/
public class GMM implements Parcelable {

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
	
	// Copy Constructor:
	public GMM(GMM otherGMM) {
		this.clfs = otherGMM.clfs;
		this.classesDict = otherGMM.classesDict;
		this.n_classes = otherGMM.n_classes;
		this.n_features = otherGMM.n_features;
		this.scale_means = otherGMM.scale_means;
		this.scale_stddevs = otherGMM.scale_stddevs;
	}
	
	// Parcelable Constructor:
	public GMM(Parcel in) {
		this.clfs = in.readArrayList(null); //in.readArrayList(ContextClassModel.class.getClassLoader())
		in.readMap(this.classesDict, Map.class.getClassLoader());
		this.n_classes = in.readInt();
		this.n_features = in.readInt();
		this.scale_means = (DenseMatrix64F) in.readSerializable();		//does this work??????????
		this.scale_stddevs = (DenseMatrix64F) in.readSerializable(); 	//does this work??????????
	}
	
    public static final Parcelable.Creator<GMM> CREATOR = new Parcelable.Creator<GMM>() {
        public GMM createFromParcel(Parcel in) {
            return new GMM(in);
        }

        public GMM[] newArray(int size) {
            return new GMM[size];
        }
    };
	
	@Override
	public int describeContents() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void writeToParcel(Parcel out, int flags) {
		out.writeList(clfs);
		out.writeMap(classesDict);
		out.writeInt(n_classes);
		out.writeInt(n_features);
		out.writeSerializable(scale_means);
		out.writeSerializable(scale_stddevs);
	}

	
	
	public List<JsonModel> parseGSON(String filename) {
		
		Gson gson = new Gson();
		
		List<JsonModel> jsonGMM;

		File dir = Environment.getExternalStorageDirectory();
		
		File file = new File(dir,filename);

		if(file.exists()) {
			
			Log.v(TAG,"JSON file found");
			
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

	// Dump a GMM object into a JSON file
	public void dumpJSON() {

		List<JsonModel> jm = convertGMMtoJSON();
		
		String str = new Gson().toJson(jm);

		File sdcard = Environment.getExternalStorageDirectory();

		File dir = new File(sdcard.getAbsolutePath());

		File file = new File(dir, "GMM.json");
		
		FileOutputStream f = null;
		try {
			f = new FileOutputStream(file);
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		try {
			f.write(str.getBytes());
			f.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}

	// Convert GMM to ArrayList of JsonModels:
	public List<JsonModel> convertGMMtoJSON() {

		List<JsonModel> jsonModelList = new ArrayList<JsonModel>();
		
		for(int i=0; i<get_n_classes(); i++) {
			JsonModel j = new JsonModel();
			
			j.set_n_classes(get_n_classes());
			j.set_classesDict(get_classesDict());
			j.set_n_features(get_n_features());
			j.set_scale_means(convertToArrayList_1D_row(get_scale_means()));
			j.set_scale_stddevs(convertToArrayList_1D_row(get_scale_stddevs()));
			
			j.set_n_components(clf(i).get_n_components());
			j.set_n_features(clf(i).get_n_features());
			j.set_n_train(clf(i).get_n_train());
			
			j.set_weights(convertToArrayList_1D_row(clf(i).get_weights()));
			j.set_means(convertToArrayList_2D(clf(i).get_means()));
			j.set_covars(convertToArrayList_3D(clf(i).get_covars()));
			
			jsonModelList.add(j);			
		}
			
			return jsonModelList;
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
	
	// Converts an ArrayList into a (1 x n) EJML DenseMatrix64F. If a (n x 1) matrix is needed, transpose the result
	private ArrayList<Double> convertToArrayList_1D_row(DenseMatrix64F in) {
		
		ArrayList<Double> out = new ArrayList<Double>();
		
		int len = in.numCols;
		
		for(int i=0; i<len; i++) {
			out.add(in.get(0, i));
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

	// Converts an ArrayList into a 2D EJML DenseMatrix64F.
	private ArrayList<ArrayList<Double>> convertToArrayList_2D(DenseMatrix64F in) {
		
		ArrayList<ArrayList<Double>> out = new ArrayList<ArrayList<Double>>();
		
		int nRows = in.numRows;
		int nCols = in.numCols;
		
		for(int r=0; r<nRows; r++) {
			
			ArrayList<Double> tmpRow = new ArrayList<Double>();
			
			for(int c=0; c<nCols; c++) {
				
				tmpRow.add(in.get(r, c));
				
			}
			out.add(tmpRow);
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

	// Converts an array of 2D EJML DenseMatrix64Fs into a 3D ArrayList.
	private ArrayList<ArrayList<ArrayList<Double>>> convertToArrayList_3D(DenseMatrix64F[] in) {
		
		ArrayList<ArrayList<ArrayList<Double>>> out = new ArrayList<ArrayList<ArrayList<Double>>>();
		
		int nMatrices = in.length;
		int nRows = in[0].numRows;
		int nCols = in[0].numCols;
		
		for(int m=0; m<nMatrices; m++) {
			
			ArrayList<ArrayList<Double>> tmpMatrix = new ArrayList<ArrayList<Double>>();
			
			for(int r=0; r<nRows; r++) {
				
				ArrayList<Double> tmpRow = new ArrayList<Double>();
				
				for(int c=0; c<nCols; c++) {
					
					tmpRow.add(in[m].get(r, c));
					
				}
				tmpMatrix.add(tmpRow);
			}
			
			out.add(tmpMatrix);
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
	
	// Return array of strings for all classes
	public String[] get_string_array() {
		
		String[] result = new String[n_classes];
		
		for(int i=0; i<n_classes; i++) {
			result[i] = get_class_name(i);
		}
		
		return result;
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
