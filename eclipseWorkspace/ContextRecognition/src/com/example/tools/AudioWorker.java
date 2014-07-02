package com.example.tools;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import org.ejml.data.DenseMatrix64F;

import android.app.Activity;
import android.app.IntentService;
import android.content.Intent;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.util.Log;

public class AudioWorker extends IntentService {

	private static final String TAG = "IntentService";
	
	private GMM gmm;
	private Classifier clf;
	private SoundHandler soundHandler;
	private FeaturesExtractor featuresExtractor;
	private LinkedList<double[]> mfccList;
	private LinkedList<double[]> dataBuffer; // buffer the last 1 minute of data for the model adaption
	private static int DATA_BUFFER_SIZE = 1875; // equals ~1min for 0.032ms window length
	private boolean bufferStatus = false;
	private String stringRes;

	public static int code = Activity.RESULT_CANCELED;	
			
	private int testInt = 0;
	
	public AudioWorker() {
		super("AudioWorker");
	}
	
	@Override
    public void onCreate() {
        // TODO Auto-generated method stub
        super.onCreate();
        Log.d(TAG, "AudioWorker created");
    }
	
	@Override
    public void onDestroy() {
        // TODO Auto-generated method stub
        super.onDestroy();
        Log.d(TAG, "AudioWorker destroyed");
    }

	@Override
	protected void onHandleIntent(Intent arg0) {
		Log.i(TAG, "AudioWorker Service started");
		
	    mfccList = new LinkedList<double[]>();
	    dataBuffer = new LinkedList<double[]>();
		featuresExtractor = new FeaturesExtractor();
		soundHandler = new SoundHandler();
		clf = new Classifier();
		gmm = new GMM("GMM.json"); //TODO
		
		// Initialize the data handling
		initializeDataHandling();
        
	}
	
	/*
	 * This method overrides the data handling method of the SoundHandler to
	 * (i) extract features of 32ms window size from the 2s sound data
	 * (ii) run the classification algorithm
	 * (iii) start the next recording sequence
	 */
	public void initializeDataHandling() {

		soundHandler = new SoundHandler() {
			
			@Override
			protected void handleData(short[] data, int length, int frameLength) {
				
				// Only handle new data if we are in init or normal classification status:
				if ((appStatus.getInstance().get() == appStatus.NORMAL_CLASSIFICATION) ||
						appStatus.getInstance().get() == appStatus.INIT) {
					
					//Check if sequence length is 2 seconds (more precisely 63 32ms windows)
					if (data.length != 64512) { //data.length != 64512
						// a single 32ms window has size 1024??
						Log.e(TAG,"data sequence has wrong length, aborting calculation");
						return;
					}
					
					// Call handle data from SoundHandler class
					super.handleData(data, length, frameLength);

					// Loop through the audio data and extract our features for each 32ms window
					for(int i=0; i<63; i++) {
						
						// Split the data into 32ms chunks (equals 1024 (??) elements)
						short[] tmpData = new short[1024];
						System.arraycopy(data, i*1024, tmpData, 0, 1024);
						
//						short[] tmpData = new short[512];
//						System.arraycopy(data, i*512, tmpData, 0, 512);
						
						Mfccs currentMFCCs = featuresExtractor.extractFeatures(tmpData);
						mfccList.add(currentMFCCs.get());
						
						// Also add the MFCCs to the 1min data buffer:
						if (dataBuffer.size() < DATA_BUFFER_SIZE) {
							// Add new feature point:
							dataBuffer.add(currentMFCCs.get());
							
							//Update buffer status:
							if (bufferStatus != false) {
								bufferStatus = false;
							}

						} else {
							// Add new feature point and remove the oldest one:
							dataBuffer.add(currentMFCCs.get());
							dataBuffer.remove(0);
							
							//Update buffer status:
							if (bufferStatus != true) {
								bufferStatus = true;
							}
						}
						
					}
					
					// If we have 2 seconds of data, call our prediction method and clear the list afterwards again 
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
						
						// Set result code to okay and publish the result
						code = Activity.RESULT_OK;
						publishResult(intRes, stringRes, code);
						
						//Log.i(TAG, "Current Prediction: " + stringRes + ": " + intRes);
						
						// Delete all elements of the list afterwards
						mfccList.clear();
						
						// If we were in init status, change to normal classification now:
						if (appStatus.getInstance().get() == appStatus.INIT) {
							appStatus.getInstance().set(appStatus.NORMAL_CLASSIFICATION);
							Log.i(TAG, "New status: normal classification");
						}
												
					}
				}
				
				HashMap<String, Integer> hm = new HashMap<String, Integer>(gmm.get_classesDict());
				publishStatus(hm, gmm, bufferStatus, dataBuffer, code);

			}
		};
		
		//Log.i(TAG,String.valueOf(dataBuffer.size()));

		soundHandler.beginRec();	
		
	
	}
	
	private void publishResult(int predictionInt, String predicationString, int resultCode) {
		Intent intent = new Intent(StateManager.PREDICTION_INTENT);
		
		Bundle bundle = new Bundle();
		
		intent.putExtras(bundle);
		intent.putExtra(StateManager.PREDICTION_INT, predictionInt);
		intent.putExtra(StateManager.PREDICTION_STRING, predicationString);
		intent.putExtra(StateManager.RESULTCODE, code);
		
		sendBroadcast(intent);
		
		//Log.d(TAG, "Prediction broadcasted");
	}
	
	private void publishStatus(HashMap<String, Integer> classesDict, GMM gmm, boolean bufferStatus, LinkedList<double[]> buffer, int resultCode) {
		Intent intent = new Intent(StateManager.STATUS_INTENT);
		
		Bundle bundle = new Bundle();

		bundle.putStringArray(StateManager.CLASS_STRINGS,gmm.get_string_array());
		bundle.putSerializable(StateManager.CLASSES_DICT, classesDict); //Needed??
		bundle.putParcelable(StateManager.GMM_OBJECT, gmm); //Needed??
		bundle.putBoolean(StateManager.BUFFER_STATUS, bufferStatus);
		bundle.putSerializable(StateManager.BUFFER, buffer);
		bundle.putInt(StateManager.RESULTCODE, code);

		intent.putExtras(bundle);
		
		sendBroadcast(intent);
	}
	
	/*
	 *  Converts an LinkedList of double arrays into a EJML DenseMatrix64F
	 */
	private DenseMatrix64F convertToEJML(LinkedList<double[]> in) {
		
		int nRows = in.size();
		int nCols = in.get(0).length;
		
		DenseMatrix64F out = new DenseMatrix64F(nRows, nCols);
		
		for(int r=0; r<nRows; r++) {
			for(int c=0; c<nCols; c++) {
				out.set(r, c, in.get(r)[c]);
			}
			
		}
		
		return out;
		
	}
	
}
