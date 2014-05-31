package com.example.contextrecognition;

import android.util.Log;

public class ModelAdaptor {

	private static final String TAG = "AdaptModel";
	
	//Constructor:
	public ModelAdaptor() {
		Log.i(TAG,"constructor");
	}
	
	public void changeExistingClass(String contextClass) {
		//TODO: this is only a placeholder
	    Log.i(TAG, "changeExistingClass: " + contextClass);
	}
	
	public void incorporateNewClass(String contextClass) {
		//TODO: this is only a placeholder
		Log.i(TAG, "incorporateNewClass: " + contextClass);
	}
}
