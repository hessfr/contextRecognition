package com.example.contextrecognition;

import java.util.Arrays;

import org.ejml.data.DenseMatrix64F;

import android.util.Log;

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
	
	public int[] majorityVote(int[] y_in) {
		Log.d(TAG,"majorityVote");
		int frameLength = (int) Math.ceil(MAJORITY_WINDOW/WINDOW_LENGTH);
		
		int n_frames = (int) Math.ceil(( (double) y_in.length) / ((double) frameLength) );
		
		int[] resArray = new int[y_in.length];
		
		for(int i=0; i<n_frames; i++) {
			if (((i+1) * frameLength) < y_in.length) {
				// All except the very last one:
				
				// Create temporary array for the current window:
				int len = ((i+1) * frameLength) - (i * frameLength);
				int tmpArray[] = new int[len];
			    System.arraycopy(y_in, (i * frameLength), tmpArray, 0, len); // arraycopy(Object src, int srcPos, Object dest, int destPos, int length)
				
				// Find most frequent number in array:
			    int mostFrequent = getMostFrequent(tmpArray);
				
				// Fill with most frequent element:
				Arrays.fill(tmpArray, mostFrequent);
				
				// Write into result array:
				System.arraycopy(tmpArray, 0, resArray, (i * frameLength), len);
				
			}
			
			else {
				// The last sequence most likely not exactly 2.0 seconds long:
				
				// Create temporary array for the current window:
				int len = y_in.length - (i * frameLength);
				int tmpArray[] = new int[len];
			    System.arraycopy(y_in, (i * frameLength), tmpArray, 0, len); // arraycopy(Object src, int srcPos, Object dest, int destPos, int length)
				
			    // Find most frequent number in array:
				int mostFrequent = getMostFrequent(tmpArray);
				
				// Fill with most frequent element:
				Arrays.fill(tmpArray, mostFrequent);
				
				// Write into result array:
				System.arraycopy(tmpArray, 0, resArray, (i * frameLength), len);
			}
		}
		
		return resArray;
	}
	
	public int getMostFrequent(int[] a)
	{
	  int count = 1, tempCount;
	  int popular = a[0];
	  int temp = 0;
	  for (int i = 0; i < (a.length - 1); i++)
	  {
	    temp = a[i];
	    tempCount = 0;
	    for (int j = 1; j < a.length; j++)
	    {
	      if (temp == a[j])
		tempCount++;
	    }
	    if (tempCount > count)
	    {
	      popular = temp;
	      count = tempCount;
	    }
	  }
	  return popular;
	}
}































