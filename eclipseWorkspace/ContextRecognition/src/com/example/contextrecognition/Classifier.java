package com.example.contextrecognition;

import android.util.Log;
import org.ejml.data.DenseMatrix64F;
import org.ejml.ops.CommonOps;

public class Classifier {
	private static final String TAG = "Classifier";
	public static final double MAJORITY_WINDOW = 2.0; // in seconds
	public static final double WINDOW_LENGTH = 0.032; // in seconds
	
	//Constructor:
	public Classifier() {
		Log.i(TAG,"constructor");
		
		//TODO initialize GMM objects here
	}
	
	public void testGMM() {
		Log.d(TAG,"testGMM");
		
	}
	
	public void logProb(DenseMatrix64F X, DenseMatrix64F weights, DenseMatrix64F means, DenseMatrix64F covars) {
		Log.d(TAG,"logProb");
		
		
		
	}
	
	public DenseMatrix64F majorityVote(DenseMatrix64F y_in) {
		Log.d(TAG,"majorityVote");
		int frameLength = (int) Math.ceil(MAJORITY_WINDOW/WINDOW_LENGTH);
		
		int n_frames = (int) Math.ceil(( (double) y_in.numCols) / ((double) frameLength) ); // TODO: check if this is correct for 1D array! 
		
		DenseMatrix64F resArray = new DenseMatrix64F(y_in.numRows);
		
		for(int i=0; i<n_frames; i++) { //TODO: Check if loop conditions correct
			if (((i+1) * frameLength) < y_in.numCols) {
				// All except the very last one:
				
			}
			
			else {
				// The last sequence most likely not exactly 2.0 seconds long:
			}
		}
		
		return resArray;
	}
}































