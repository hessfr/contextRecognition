package ch.ethz.wearable.contextrecognition.audio;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;

import org.ejml.data.DenseMatrix64F;

import ch.ethz.wearable.contextrecognition.data.Mfccs;
import ch.ethz.wearable.contextrecognition.data.PredictionResult;
import ch.ethz.wearable.contextrecognition.math.Classifier;
import ch.ethz.wearable.contextrecognition.utils.AppStatus;
import ch.ethz.wearable.contextrecognition.utils.GMM;
import ch.ethz.wearable.contextrecognition.utils.Globals;
import android.app.Activity;
import android.app.IntentService;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

public class AudioWorker extends IntentService {

	private static final String TAG = "AudioWorker";
	
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
	
	public AudioWorker() {
		super("AudioWorker");
	}
	
	@Override
    public void onCreate() {
		
        super.onCreate();
        Log.d(TAG, "AudioWorker created");
    }
	
	@Override
    public void onDestroy() {
		
        super.onDestroy();
        Log.i(TAG, "AudioWorker destroyed");
    }
	
	public void endRec() {
		soundHandler.endRec();
	}

	@Override
	protected void onHandleIntent(Intent arg0) {
		Log.i(TAG, "AudioWorker Service started");
		
		Globals.RECORDING_START_TIME = System.currentTimeMillis();
		Log.d(TAG, "start recording at time " + Globals.RECORDING_START_TIME);
		
	    mfccList = new LinkedList<double[]>();
	    dataBuffer = new LinkedList<double[]>();
		featuresExtractor = new FeaturesExtractor();
		soundHandler = new SoundHandler();
		clf = new Classifier();
		
		Globals.readWriteLock.readLock().lock();
		gmm = new GMM("GMM.json");
		Globals.readWriteLock.readLock().unlock();
		
		// Add the event to the log files:
		appendToLogFiles();
		
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
			protected void handleData(short[] data, boolean[] silenceBuffer) {

				//Log.i(TAG, "Current app status: " + AppStatus.getInstance().get());
				
				
				if (AppStatus.getInstance().get() == AppStatus.MODEL_UPDATED) {
					Globals.readWriteLock.readLock().lock();
					gmm = new GMM("GMM.json");
					Globals.readWriteLock.readLock().unlock();
					AppStatus.getInstance().set(AppStatus.NORMAL_CLASSIFICATION);
					Log.i(TAG, "New classifier loaded after model adaption");
				}
				
				// Only handle new data if we are in init or normal classification status:
				if ((AppStatus.getInstance().get() == AppStatus.NORMAL_CLASSIFICATION) ||
						AppStatus.getInstance().get() == AppStatus.INIT ||
						AppStatus.getInstance().get() == AppStatus.MODEL_ADAPTION) {
					
					//Check if sequence length is 2 seconds (more precisely 63 32ms windows)
					if (data.length != 32256) { //data.length != 64512
						// a single 32ms window has size 1024??
						Log.e(TAG,"data sequence has wrong length, aborting calculation");
						return;
					}
					
					// Call handle data from SoundHandler class
					super.handleData(data, silenceBuffer);

					// Check if loudness of all chunks in the 2s sequence is below silence threshold:
					int silenceCount=0;
					for(int i=0; i<silenceBuffer.length; i++) {
						if(silenceBuffer[i] == true) {
							silenceCount++;
						}
					}
					boolean isSilent = false;
					if (silenceCount == (silenceBuffer.length)) {
						isSilent = true;
					}
					
					// Only call our prediction method if loudness on this 2s interval is above the silence threshold
					if (isSilent == false) {
						//long startTimeFX = System.currentTimeMillis();
						
						// Loop through the audio data and extract our features for each 32ms window
						for(int i=0; i<63; i++) {
							
							// Split the data into 32ms chunks (equals 512 per elements)
							short[] tmpData = new short[512];
							System.arraycopy(data, i*512, tmpData, 0, 512);

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
						
						//long endTimeFX = System.currentTimeMillis();
						//Log.i(TAG, "Feature extraction duration: " + (endTimeFX-startTimeFX)); //~2s
						
						// If we have 2 seconds of data, call our prediction method and clear the list afterwards again 
						if (mfccList.size() == 63) {
							
							//long startTimeClf = System.currentTimeMillis();
							
							// Convert data to DenseMatrix:
							double[][] array = new double[mfccList.size()][12];
							for (int i=0; i<mfccList.size(); i++) {
							    array[i] = mfccList.get(i);
							}
							DenseMatrix64F samples = new DenseMatrix64F(array);
							//Log.i(TAG, samples.toString());

							PredictionResult predictionResult = clf.predict(gmm, samples);
							int currentResult = predictionResult.get_class();
							double currentEntropy = predictionResult.get_entropy();
							//Log.i(TAG, "currentEntropy: " + currentEntropy);
							
							stringRes = gmm.get_class_name(currentResult);
							
							// Set result code to okay and publish the result
							code = Activity.RESULT_OK;
							HashMap<String, Integer> hm = new HashMap<String, Integer>(gmm.get_classesDict());
							publishResult(currentResult, currentEntropy, stringRes, hm, gmm, bufferStatus, dataBuffer, code);
							
							//Log.i(TAG, "Current Prediction: " + stringRes + ": " + intRes);
							
							// Delete all elements of the list afterwards
							mfccList.clear();
							
							// If we were in init status, change to normal classification now:
							if (AppStatus.getInstance().get() == AppStatus.INIT) {
								AppStatus.getInstance().set(AppStatus.NORMAL_CLASSIFICATION);
								Log.i(TAG, "New status: normal classification (after status initializing)");
							}
							
							//long endTimeClf = System.currentTimeMillis();
							//Log.i(TAG, "Classification duration: " + (endTimeClf-startTimeClf)); // ~0.5s
													
						} else {
							Log.e(TAG, "mfccList has wrong size of " + mfccList.size() + "instead of 63");
						}
					} else {
						
						publishResultSilence();
						
					}
				}
				
				if(AppStatus.getInstance().get() == AppStatus.STOP_RECORDING) {
					Log.i(TAG, "AppStatus changed to STOP_RECORDING, stopping the SoundHandler now");
					endRec();
				}
				
			}
		};

		soundHandler.beginRec();	
	}
	
	private void publishResult(int predictionInt, double predictionEntropy,
			String predicationString, HashMap<String, Integer> classesDict,
			GMM gmm, boolean bufferStatus, LinkedList<double[]> buffer,
			int resultCode) {
		
		Intent intent = new Intent(Globals.PREDICTION_INTENT);

		Bundle bundle = new Bundle();

		
		bundle.putInt(Globals.PREDICTION_INT, predictionInt);
		bundle.putDouble(Globals.PREDICTION_ENTROPY, predictionEntropy);
		bundle.putString(Globals.PREDICTION_STRING, predicationString);
		
		//Log.i(TAG, "------------------");
		//Log.i(TAG, gmm.get_string_array()[2]);
		bundle.putStringArray(Globals.CLASS_STRINGS,gmm.get_string_array());
		
		bundle.putBoolean(Globals.BUFFER_STATUS, bufferStatus);
		bundle.putSerializable(Globals.BUFFER, buffer);
		
		bundle.putParcelable(Globals.GMM_OBJECT, gmm); //Needed??
		
		bundle.putSerializable(Globals.CLASSES_DICT, classesDict); //Needed??

		bundle.putInt(Globals.RESULTCODE, code);
		
		bundle.putBoolean(Globals.SILENCE, false);

		intent.putExtras(bundle);
		
		sendBroadcast(intent);

		//Log.d(TAG, "Prediction broadcasted");
	}	
	
	private void publishResultSilence() {
		
		Log.d(TAG, "Loadness below silence threshold for this 2s interval, no prediction is made");
		
		Intent intent = new Intent(Globals.PREDICTION_INTENT);

		Bundle bundle = new Bundle();
		
		bundle.putInt(Globals.RESULTCODE, code);
		
		bundle.putBoolean(Globals.SILENCE, true);
		
		intent.putExtras(bundle);
		sendBroadcast(intent);
	}
	
	/*
	 * Add the time of the when we start and stop recording to the log files
	 */
	private void appendToLogFiles() {
		
		// Check when we made the last change to the raw audio data file and add log it to file:
		File rawAudioFile = new File(Globals.getLogPath(), Globals.AUDIO_FILENAME);
		if (rawAudioFile.exists()) {
			long recordingStopped = rawAudioFile.lastModified();
			
			/*
			 *  First read in when we started the last time, that we can calculate the relative time, when
			 *  we stopped recording:
			 */
			try {
				long recordingLastStarted = 0;
				double recordingStoppedRel = 0.0;
				File startStopFile = new File(Globals.getLogPath(), Globals.START_STOP_LOG_FILENAME);
				
				BufferedReader br = new BufferedReader(new FileReader(startStopFile));
			    String lastLine = null;
			    String tmpLine = null;

			    while ((tmpLine = br.readLine()) != null) {
			    	lastLine = tmpLine;
			    }
			    br.close();
			    
			    if (lastLine.contains("start")) {
			    	recordingLastStarted = Long.valueOf(lastLine.substring(0, lastLine.indexOf("\t")));
			    	
			    	recordingStoppedRel = (recordingStopped - recordingLastStarted)/1000.0;
			    	
			    } else {
			    	Log.e(TAG, "Start-stop log file corrupted, couldn't calulcate stop time");
			    }
			    	
				File predFile = new File(Globals.getLogPath(), Globals.PRED_LOG_FILENAME);
			    if (predFile.exists()) {
					FileWriter f = new FileWriter(predFile, true);
					f.write("\t" + recordingStoppedRel + "\n");
					f.close();
			    }
					
			} catch (IOException e) {
				e.printStackTrace();
				Log.e(TAG, "Relative stop time could not be added to prediction log file");
			}
			
			
			
			
			
			
			try {
				File file = new File(Globals.getLogPath(), Globals.START_STOP_LOG_FILENAME);
				FileWriter f = new FileWriter(file, true);
				f.write(recordingStopped + "\t" + "stop" + "\n");
				f.close();
			} catch (IOException e) {
				Log.e(TAG, "Writing to start log file failed");
				e.printStackTrace();
			}
			
			
		}
	
		double time = (System.currentTimeMillis() - Globals.RECORDING_START_TIME) / 1000.0;
		
		Log.d(TAG, "Appending to start time of the app to log");
		try {
			File file = new File(Globals.getLogPath(), Globals.START_STOP_LOG_FILENAME);
			FileWriter f = new FileWriter(file, true);
			f.write(System.currentTimeMillis() + "\t" + "start" + "\n");
			f.close();
		} catch (IOException e) {
			Log.e(TAG, "Writing to start log file failed");
			e.printStackTrace();
		}
		
		try {
			File file = new File(Globals.getLogPath(), Globals.PRED_LOG_FILENAME);
			FileWriter f = new FileWriter(file, true);
			f.write("RECORDING_STARTED" + "\n");
			f.close();
		} catch (IOException e) {
			Log.e(TAG, "Writing to prediction log file failed");
			e.printStackTrace();
		}
		
		try {
			File file = new File(Globals.getLogPath(), Globals.GT_LOG_FILENAME);
			FileWriter f = new FileWriter(file, true);
			f.write("RECORDING_STARTED" + "\n");
			f.close();
		} catch (IOException e) {
			Log.e(TAG, "Writing to GT log file failed");
			e.printStackTrace();
		}
		
	}
}
