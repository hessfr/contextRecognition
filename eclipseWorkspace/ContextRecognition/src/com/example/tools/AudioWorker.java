package com.example.tools;

import java.util.HashMap;
import java.util.LinkedList;

import org.ejml.data.DenseMatrix64F;

import android.app.Activity;
import android.app.IntentService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;

import com.example.contextrecognition.MainActivity;

public class AudioWorker extends IntentService {

	private static final String TAG = "IntentService";
	
	private GMM gmm;
	private Classifier clf;
	private SoundHandler soundHandler;
	private FeaturesExtractor featuresExtractor;
	private LinkedList<double[]> mfccList;
	private String stringRes;
	
	public static final String PREDICTION = "resultRequest";
	public static final String PREDICTION_INT = "predictionInt";
	public static final String PREDICTION_STRING = "predictionString";
	public static final String CLASSES_DICT = "classesDict";
	public static final String RESULTCODE = "resultcode";
	public static int code = Activity.RESULT_CANCELED;

	private BroadcastReceiver receiver;
	
	public AudioWorker() {
		super("AudioWorker");
	}
	
	@Override
    public void onCreate() {
        // TODO Auto-generated method stub
        super.onCreate();
        
        // Register the broadcast receiver and intent filters:
        IntentFilter filter = new IntentFilter();
        filter.addAction(MainActivity.STOP_RECORDING);
        filter.addAction(MainActivity.REQ_CLASSNAMES);
        
    	this.receiver = new BroadcastReceiver() {
    		   
    		   @Override
    		   public void onReceive(Context context, Intent intent) {
    			   
    			   Log.d(TAG, "Broadcast received xxxxxxxxxxxxxxxxx");
    			   
    			   Bundle bundle = intent.getExtras();
    			   
    			   Log.i(TAG, "xxxxxxxxxxxxxxxxxx" + intent.getAction());
    			   
    			   if (bundle != null) {
    				   
    				   if (intent.getAction().equals(MainActivity.STOP_RECORDING)) {
    					   Log.i(TAG,"xxxxxxxxxxxxxxxxxxxxx stop recording");
    					   endRec();
    					   
    					   Log.i(TAG,"Recording stopped");
    				   } else if(intent.getAction().equals(MainActivity.REQ_CLASSNAMES)) {
    					   Log.i(TAG,"xxxxxxxxxxxxxxxxxxxxx request class names");
    			       }
    			        //int resultCode = bundle.getInt(MainActivity.STOP_RECORDING);
    				   	
    			   }
    		   }
    		};
        
        
        this.registerReceiver(receiver, filter);
        
        Log.d(TAG, "Broadcast receiver registered");
        Log.d(TAG, "AudioWorker created");
    }
	
	@Override
    public void onDestroy() {
        // TODO Auto-generated method stub
        super.onDestroy();
        unregisterReceiver(receiver);
        Log.d(TAG, "AudioWorker destroyed");
    }

	@Override
	protected void onHandleIntent(Intent arg0) {
		Log.i(TAG, "AudioWorker Service started");
		
	    mfccList = new LinkedList<double[]>();
		featuresExtractor = new FeaturesExtractor();
		soundHandler = new SoundHandler();
		clf = new Classifier();
		gmm = new GMM("jsonGMM.json"); //TODO
		
		//Initialize the data handling
		initializeDataHandling();
		
	}
	
	/*
	 * This method overrides the data handling method of the SoundHandler to
	 * (i) extract features of 32ms window size from the 2s sound data
	 * (ii) run the classification algorithm
	 * 
	 * and start the actual recording
	 */
	public void initializeDataHandling() {

		soundHandler = new SoundHandler() {

			@Override
			protected void handleData(short[] data, int length, int frameLength) {
				
				//length of data has to be 1024 to match our 32ms windows, 64512 to match 63 32ms windows
				if (data.length != 64512) {
					Log.e(TAG,"data sequence has wrong length, aborting calculation");
					return;
				}
				
				// Call handle data from SoundHandler class
				super.handleData(data, length, frameLength);

				// Loop through the audio data and extract our features for each 32ms window
				
				for(int i=0; i<63; i++) {
					
					// Split the data into 32ms chunks (equals 1024 elements)
					short[] tmpData = new short[1024];
					System.arraycopy(data, i*1024, tmpData, 0, 1024);
					
					Mfccs currentMFCCs = featuresExtractor.extractFeatures(tmpData);
					mfccList.add(currentMFCCs.get());
				}
				
				// If we have 2 seconds of data, call our prediction method and clear the List afterwards again 
				if (mfccList.size() == 63) {
					// Convert data to DenseMatrix:
					double[][] array = new double[mfccList.size()][12]; // TODO: n_features instead of 12
					for (int i=0; i<mfccList.size(); i++) {
					    array[i] = mfccList.get(i);
					}
					DenseMatrix64F samples = new DenseMatrix64F(array);

					DenseMatrix64F res = clf.predict(gmm, samples);
					
					int intRes = (int) res.get(10); // TODO: implement this properly! As this array is exactly the length of one majority vote window, all elements in it are the same 
					
					stringRes = gmm.get_class_name(intRes);
					
					Log.v(TAG, "Current Context: " + stringRes);
					
					HashMap<String, Integer> hm = new HashMap<String, Integer>(gmm.get_classesDict());
					
					// Set result code to okay and publish the result
					code = Activity.RESULT_OK;
					publish(intRes, stringRes, code, hm);
					
					// Delete all elements of the list afterwards
					mfccList.clear();
				}

			}

		};
		
		soundHandler.beginRec();
	}
	
	private void endRec() {
		soundHandler.endRec();
	}
	
//	private BroadcastReceiver receiver = new BroadcastReceiver() {
//	   @Override
//	   public void onReceive(Context context, Intent intent) {
//		   
//		   Log.d(TAG, "Broadcast received");
//		   
//		   Bundle bundle = intent.getExtras();
//		   
//		   //Log.i(TAG, "xxxxx" + intent.getAction());
//		   
//		   if (bundle != null) {
//			   
//			   if (intent.getAction().equals(MainActivity.STOP_RECORDING)) {
//				   Log.i(TAG,"xxxxxxxxxxxxxxxxxxxxx stop recording");
//				   endRec();
//				   
//				   Log.i(TAG,"Recording stopped");
//			   } else if(intent.getAction().equals(MainActivity.REQ_CLASSNAMES)) {
//				   Log.i(TAG,"xxxxxxxxxxxxxxxxxxxxx request class names");
//		       }
//		        //int resultCode = bundle.getInt(MainActivity.STOP_RECORDING);
//			   	
//		   }
//	   }
//	};
	
	private void publish(int predictionInt, String predicationString, int resultCode, HashMap<String, Integer> classesDict) {
		Intent intent = new Intent(PREDICTION);
		
		Bundle bundle = new Bundle();
		
		bundle.putSerializable(CLASSES_DICT,classesDict);
		
		intent.putExtras(bundle);
		
		intent.putExtra(PREDICTION_INT, predictionInt);
		intent.putExtra(PREDICTION_STRING, predicationString);

		intent.putExtra(RESULTCODE, code);
		sendBroadcast(intent);
	}
	
}
