package ch.ethz.wearable.contextrecognition.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Serializable;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.math3.stat.descriptive.moment.Mean;
import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;

import ch.ethz.wearable.contextrecognition.R;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;
import ch.ethz.wearable.contextrecognition.communication.CheckClassFeasibility;
import ch.ethz.wearable.contextrecognition.communication.CompressAndSendData;
import ch.ethz.wearable.contextrecognition.communication.IncorporateNewClass;
import ch.ethz.wearable.contextrecognition.communication.IncorporateNewClassGet;
import ch.ethz.wearable.contextrecognition.communication.InitModelGet;
import ch.ethz.wearable.contextrecognition.communication.ManageClassesGet;
import ch.ethz.wearable.contextrecognition.data.AppData;
import ch.ethz.wearable.contextrecognition.data.HistoricPredictions;
import ch.ethz.wearable.contextrecognition.gui.ContextSelection;
import ch.ethz.wearable.contextrecognition.gui.MainActivity;
import ch.ethz.wearable.contextrecognition.gui.QueryPopup;
import ch.ethz.wearable.contextrecognition.gui.UploadActivity;
import ch.ethz.wearable.contextrecognition.math.ModelAdaptor;
import ch.ethz.wearable.contextrecognition.math.ModelAdaptor.onModelAdaptionCompleted;

import com.google.gson.Gson;

/*
 * Handles all broadcasts and holds all prediction variables like current context, 
 * buffers, class names, etc. The AL query criteria are also checked and send from here
 */
@SuppressLint("SimpleDateFormat")
public class StateManager extends BroadcastReceiver {

	private static final String TAG = "StateManager";
	
	/*
	 * Variables of the current prediction: (we only have one instance of StateManager at once, so static is ok...)
	 * 
	 * Remember that these variables are only initialized after the first prediction (after ~2sec)
	 */
	private static boolean variablesInitialized = false;
	
	private static int currentPrediction;
	private static double currentEntropy;
	private static String predictionString;
	private static String prevPredictionString = "";
	private static String[] classNameArray;
	public static Map<String, Integer> classesDict = new HashMap<String, Integer>();
	private static ArrayList<Integer> totalCount; // contains number of total predictions for each class
	private static int totalSilenceCount; // contains number silences predictions
	
	// ----- Variables needed to calculate the queryCriteria: -----
	private static long prevTime = -1000000;
	private static ArrayList<Boolean> initThresSet;
	private static ArrayList<Boolean> thresSet;
	private static ArrayList<Boolean> feedbackReceived;
	private static ArrayList<Double> threshold;
	
	// To calculate maj vote on the last minute of  data and only incorporate those points matching the majority vote:
	private static ArrayList<Integer> predBuffer;
	
	// Mean entropy values (on 2sec window) of the last min:
	private static ArrayList<Double> queryBuffer;
	
	// Store entropy values to set threshold for the first time. Separate for different classes:
	private static ArrayList<ArrayList<Double>> initThresBuffer;
	
	// Store entropy values to set threshold after the init model adaption is done. Separate for different classes:
	private static ArrayList<ArrayList<Double>> thresBuffer;
	
	// Value computed on the points where query was sent. Used to calculate the new query criteria:
	private static ArrayList<Double> thresQueriedInterval;

	/*
	 * This is the buffer of feature points be use to adapt the model. Called "updatePoints" in Python.
	 * The update of this buffer is completely done in the AudioWorker
	 */
	private static ArrayList<double[]> buffer;

	// Apache Commons methods to calculate means and standard deviations:
	StandardDeviation stdCalc = new StandardDeviation();
	Mean meanCalc = new Mean();
	
	// When we wait for user feedback, don't change the buffer:
	private static boolean waitingForFeedback = false;
	
	// Value contributing to the query criteria that is computed on the interval where the query was sent.
	private static double tmpQueryCrit;
	
	// Time when query was sent, used to calculate the response time of the user
	private static long timeQuerySent;
	
	// Number of queries left for that day.
	private static int numQueriesLeft;
	
	// -------------------------------------------------------------
	
	private static boolean classNamesRequested = false;
	
	private static String[] prevClassnames;
	
	//private static long startTime;
	//private static long endTime;
	
	SharedPreferences mPrefs;
	
	@SuppressWarnings({ "unchecked", "static-access" })
	@Override
	public void onReceive(Context context, Intent intent) {
		
		Bundle bundle = intent.getExtras();

		if (bundle != null) {

			if (intent.getAction().equals(Globals.PREDICTION_INTENT)) {

				int resultCode = bundle.getInt(Globals.RESULTCODE);
				boolean silence = bundle.getBoolean(Globals.SILENCE);

				if (resultCode == Activity.RESULT_OK) {
					
					if (silence == false) {
						
						// The following lines have to be in exactly the same order as the were put on the bundle (in the AudioWorker):

						currentPrediction = bundle.getInt(Globals.PREDICTION_INT);
						currentEntropy = bundle.getDouble(Globals.PREDICTION_ENTROPY);
						predictionString = bundle.getString(Globals.PREDICTION_STRING);
						
						classNameArray = bundle.getStringArray(Globals.CLASS_STRINGS);

						Serializable s1 = bundle.getSerializable(Globals.BUFFER);
						if (waitingForFeedback == false) {
							buffer = (ArrayList<double[]>) s1;
							//Log.i(TAG, "Buffer size: " + buffer.size()/31.5 + " seconds");
						}

						Serializable s2 = new HashMap<String, Integer>();
						s2 = bundle.getSerializable(Globals.CLASSES_DICT);
						classesDict = (HashMap<String, Integer>) s2;
		
						boolean firstPredAfterModelChange = bundle.getBoolean(Globals.FIRST_PRED_AFTER_MODEL_CHANGE);
						
						//=================================================================================
						//============ Handle sending of query, threshold calculations, ... ===============
						//=================================================================================
						
						//startTime = System.currentTimeMillis();
						
						if (firstPredAfterModelChange == true) {
							// Save String array of the context classes to preferences:
							Globals.setStringArrayPref(context, Globals.CONTEXT_CLASSES, classNameArray);
							
							Log.i(TAG, "Number of context classes: " + classNameArray.length);
							
							changeBuffersAfterModelChange(context, classNameArray, prevClassnames);
						}
						
						// Initialize the variable when receiving the first set of data:
						if(variablesInitialized == false) {
							
							// Load data from JSON file from external storage if it already exists:
							if (Globals.APP_DATA_FILE.exists()) {
								Log.i(TAG, "Loading app data from JSON file");
								
								AppData appData = readAppData();
								initThresSet = appData.get_initThresSet();
								thresSet = appData.get_thresSet();
								feedbackReceived = appData.get_feedbackReceived();
								threshold = appData.get_threshold();
								initThresBuffer = appData.get_initThresBuffer();
								thresBuffer = appData.get_thresBuffer();
								thresQueriedInterval = appData.get_thresQueriedInterval();
								numQueriesLeft = appData.get_numQueriesLeft();
								
								totalCount = Globals.getIntListPref(context, Globals.CLASS_COUNTS);
								SharedPreferences mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
								totalSilenceCount = mPrefs.getInt(Globals.SILENCE_COUNTS, 0);

							} else {
								/*
								 *  If it doesn't exists (i.e. at the very first start) initialize variables empty:
								 *  (same goes for the buffer containing the MFCC values)
								 */
								
								Log.i(TAG, "Initializing empty buffers and thresholds, because no JSON file was created yet");
								
								initThresSet = new ArrayList<Boolean>();
								thresSet = new ArrayList<Boolean>();
								feedbackReceived = new ArrayList<Boolean>();
								threshold = new ArrayList<Double>();
								initThresBuffer = new ArrayList<ArrayList<Double>>();
								thresBuffer = new ArrayList<ArrayList<Double>>();
								thresQueriedInterval = new ArrayList<Double>();
								
								for(int i=0; i<classNameArray.length; i++) {
									initThresSet.add(false);
									thresSet.add(false);
									feedbackReceived.add(false);
									threshold.add(-1.0);
									initThresBuffer.add(new ArrayList<Double>());
									thresBuffer.add(new ArrayList<Double>());
									thresQueriedInterval.add(-1.0);
								}
								
								totalCount = new ArrayList<Integer>();
								for(int i=0; i<classNameArray.length; i++) {
									totalCount.add(0);
								}
								
								totalSilenceCount = 0;

								resetQueriesLeft(context);

								Intent i = new Intent(Globals.CLASS_NAMES_SET);
								context.sendBroadcast(i);
								
							}						
							
							// These buffers doesn't need to be taken from the last run:
							queryBuffer = new ArrayList<Double>();
							predBuffer = new ArrayList<Integer>();
							
							// Save String array of the context classes to preferences:
							Globals.setStringArrayPref(context, Globals.CONTEXT_CLASSES, classNameArray);

							if (classNamesRequested == true) {
								Intent i = new Intent(Globals.CLASS_NAMES_SET);
								context.sendBroadcast(i);
								classNamesRequested = false;
							}

							Log.i(TAG, "Number of context classes: " + classNameArray.length);
							
							variablesInitialized = true;
						}					
						
						// For each class buffer the last (30) entropy values
						if (queryBuffer.size() < Globals.QUERY_BUFFER_SIZE) {
							
							queryBuffer.add(currentEntropy);
							predBuffer.add(currentPrediction);
							
						} else {
							
							queryBuffer.add(currentEntropy);
							queryBuffer.remove(0);
							predBuffer.add(currentPrediction);
							predBuffer.remove(0);
						}
						
						// ----- Set initial threshold -----
						if (initThresSet.get(currentPrediction) == false) {
							if (initThresBuffer.get(currentPrediction).size() < Globals.INIT_THRES_BUFFER_SIZE) {
								// Fill the buffer for the predicted class first:
								ArrayList<Double> tmpList = initThresBuffer.get(currentPrediction);							
								tmpList.add(currentEntropy);
								initThresBuffer.set(currentPrediction, tmpList);
								//Log.i(TAG, "initThresBuffer length: " + initThresBuffer.get(currentPrediction).size());
							} else {
								// As soon as the buffer is full, set the initial threshold for this class:
								Double[] ds = initThresBuffer.get(currentPrediction).toArray(new Double[initThresBuffer.get(currentPrediction).size()]);							
								double[] d = ArrayUtils.toPrimitive(ds);
								double mean = meanCalc.evaluate(d);
								double std = stdCalc.evaluate(d);
								
								threshold.set(currentPrediction, initMetric(mean, std));
								Log.i(TAG, "Initial threshold for class " + get_class_name(currentPrediction, classesDict) +
										" set to " + initMetric(mean, std));
								
								thresSet.set(currentPrediction, true);
								initThresSet.set(currentPrediction, true);	
							}
						}
						
						// ----- Set threshold (not initial one...) -----
						if ((thresSet.get(currentPrediction) == false) && 
								(feedbackReceived.get(currentPrediction) == true)) {
							
							if (thresBuffer.get(currentPrediction).size() < Globals.THRES_BUFFER_SIZE) {
								// Fill the threshold buffer for the predicted class first:
								ArrayList<Double> tmpList = thresBuffer.get(currentPrediction);
								tmpList.add(currentEntropy);
								thresBuffer.set(currentPrediction, tmpList);
							} else {
								/*
								 * Threshold buffer full for the predicted class -> set new threshold
								 * for this class:
								 */
								if (initThresSet.get(currentPrediction) == true) {
									Double[] ds = thresBuffer.get(currentPrediction).toArray(new Double[thresBuffer.get(currentPrediction).size()]);							
									double[] d = ArrayUtils.toPrimitive(ds);
									double mean = meanCalc.evaluate(d);
									double std = stdCalc.evaluate(d);
									double newThreshold = (metricAfterFeedback(mean, std) + thresQueriedInterval.get(currentPrediction)) / 2.0;
									
									threshold.set(currentPrediction, newThreshold);
									
									
									
									Log.i(TAG, "Threshold for class " + get_class_name(currentPrediction, classesDict) +
											" updated to " + newThreshold);
									
									thresSet.set(currentPrediction, true);
								}
								
							}

						}
						
						// ----- Check if we want to query -----
						if ((thresSet.get(currentPrediction) == true) && 
								(queryBuffer.size() == Globals.QUERY_BUFFER_SIZE)) {
							
							Integer[] ds = predBuffer.toArray(new Integer[predBuffer.size()]);							
							int[] d = ArrayUtils.toPrimitive(ds);
							int mostFreq = getMostFrequent(d);
							
							/*
							 * Make a majority vote on the last minute and only consider elements
							 * (2sec windows) equal to the majority in this minute.
							 * Regardless of this, all feature points will be used to adapt the model
							 * later...
							 */
							ArrayList<Double> majElements = new ArrayList<Double>();
							for (int i=0; i<Globals.PRED_BUFFER_SIZE; i++) {
								if (predBuffer.get(i) == mostFreq) {
									majElements.add(queryBuffer.get(i));
								}
							}
							
							// Use mean entropy value of the last last 30 2sec windows as query criteria
							Double[] ms = majElements.toArray(new Double[majElements.size()]);							
							double[] m = ArrayUtils.toPrimitive(ms);
							double queryCrit = meanCalc.evaluate(m);
							double std = stdCalc.evaluate(m);
							
							//Log.i(TAG,"Time since last feedback: " + (System.currentTimeMillis() - prevTime));

								if ((queryCrit > threshold.get(currentPrediction)) && 
										(waitingForFeedback == false) && 
										(numQueriesLeft > 0) &&
										((System.currentTimeMillis() - prevTime) > Globals.minBreak) && 
										(AppStatus.getInstance().get() != AppStatus.getInstance().MODEL_ADAPTION)) {
									
									Log.i(TAG, "Threshold exceeded, user queried for current context");

									sendQuery(context);

									prevTime = System.currentTimeMillis();
									
									/*
									 * Contributing to the query criteria that is computed on the interval where the query was sent.
									 * We can only assign this value to the correct class, once we received the ground truth from
									 * the user:
									 */
									tmpQueryCrit = metricBeforeFeedback(queryCrit, std); // queryCrit is just value of the mean

									/*
									 * The model adaption is handled in the callModelAdaption, that is always being called
									 * from the ContextSelection Activity
									 */
								}						
						}
									
						//long sincePrevEndTime = System.currentTimeMillis() - endTime;
						
						//endTime = System.currentTimeMillis();
						
						//long diff = endTime-startTime;
						
						//Log.w(TAG, "Time: " + startTime);
						//Log.w(TAG, "Time: " + endTime);
						//Log.w(TAG, "Time for one computation: " + diff);
						//Log.w(TAG, "Time since last cycle: " + sincePrevEndTime);
						
						//=================================================================================
						//=================================================================================
						//=================================================================================

						
						/*
						 * Increase the total number of predictions per class to 
						 * for the predicted one and save to preferences (for plotting):
						 */
						totalCount.set(currentPrediction, (totalCount.get(currentPrediction) + 1));
						Globals.setIntListPref(context, Globals.CLASS_COUNTS, totalCount);
						
						// Put the current prediction string to the preferences (workaround!):
						mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
						SharedPreferences.Editor editor = mPrefs.edit();
						editor.putString(Globals.CURRENT_CONTEXT, predictionString);
						editor.commit();
						
						// When day changes, create the new log files and reset the recording start time:
						dayChangeTasks(context);
						
						/*
						 *  Call method to persist the prediction data (it will only persist, if a day is
						 *  completely finished):
						 */
						persistPredictionData(context);
						
						// Save to log file and send broadcast to change text, if prediction has changed
						if (!predictionString.equals(prevPredictionString)) {
							
							appendToPredLog(context, predictionString);
							
							Intent i = new Intent(Globals.PREDICTION_CHANGED_INTENT);
							Bundle b = new Bundle();
							b.putString(Globals.NEW_PREDICTION_STRING, predictionString);
							i.putExtras(b);
							context.sendBroadcast(i);
					
							prevPredictionString = predictionString;
						}
						
						// Broadcast the current entropy value (for debugging only)
						Intent i = new Intent(Globals.PREDICTION_ENTROPY_INTENT);
						Bundle b = new Bundle();
						b.putDouble(Globals.PREDICTION_ENTROPY_VALUE, currentEntropy);
						i.putExtras(b);
						context.sendBroadcast(i);
						
					} else {
						
						predictionString = Globals.SILENCE; 
						
						// Increase the number of sample predicted as silent (for plotting)
						SharedPreferences mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
						totalSilenceCount = mPrefs.getInt(Globals.SILENCE_COUNTS, 0);
						totalSilenceCount++;
						SharedPreferences.Editor editor = mPrefs.edit();
						editor.putInt(Globals.SILENCE_COUNTS, totalSilenceCount);
						editor.commit();
						
						// When day changes, create the new log files and reset the recording start time:
						dayChangeTasks(context);
						
						// Save to log file and send broadcast to change text, if prediction has changed
						if (!predictionString.equals(prevPredictionString)) {
							
							appendToPredLog(context, predictionString);
							
							Intent i = new Intent(Globals.PREDICTION_CHANGED_INTENT);
							Bundle b = new Bundle();
							b.putString(Globals.NEW_PREDICTION_STRING, predictionString);
							i.putExtras(b);
							context.sendBroadcast(i);
					
							prevPredictionString = Globals.SILENCE;
						}
					}
					
				} else {
					Log.i(TAG,
							"Received prediction result not okay, result code "
									+ resultCode);
				}

			}
		
			else if (intent.getAction().equals(Globals.MODEL_ADAPTION_EXISTING_INTENT)) {
				
				int label = bundle.getInt(Globals.LABEL);
				callModelAdaption(context, label);

			} else if (intent.getAction().equals(
					Globals.MODEL_ADAPTION_FINISHED_INTENT)) {

				Toast.makeText(context,
						(String) "Model adaptation finished",
						Toast.LENGTH_LONG).show();

			} else if (intent.getAction().equals(Globals.MODEL_ADAPTION_NEW_INTENT)) {
				
				String newClassName = bundle.getString(Globals.NEW_CLASS_NAME);

				requestNewClassFromServer(context, newClassName);
				
			} 
		}
		
		if (intent.getAction().equals(Globals.DISMISS_NOTIFICATION)) {
			
			dismissNotifitcation(context);
			
			Log.i(TAG, "Query dismissed");
			
		}
		
		if (intent.getAction().equals(Globals.REGISTER_RECURRING_TASKS)) {
			/* 
			 * Reset of max number of queries at the end of the day. The alarm manager wakes
			 * up the device and calls the pending intent, even if the app itself is not opened.
			 */
			Calendar updateTime = Calendar.getInstance();
			Calendar now = Calendar.getInstance();
		    updateTime.set(Calendar.HOUR_OF_DAY, 0);
		    updateTime.set(Calendar.MINUTE, 5);
		    
		    // If App started after 00:05, the alarm would go off, so we have to add one day in that case
		    long actualUpdateTime = 0;
		    if(updateTime.getTimeInMillis() <= now.getTimeInMillis()) {
		    	actualUpdateTime = updateTime.getTimeInMillis() + (AlarmManager.INTERVAL_DAY+1);
		    } else {
		    	actualUpdateTime = updateTime.getTimeInMillis();
		    }
		    
		    Intent endOfDayIntent = new Intent(Globals.END_OF_DAY_TASKS);
	        PendingIntent pendingEndOfDayIntent = PendingIntent.getBroadcast(context, 0, endOfDayIntent, 0);
	        
	        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
	        alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, actualUpdateTime, AlarmManager.INTERVAL_DAY, pendingEndOfDayIntent);
		    
	        Log.d(TAG, "AlarmManager registered, to reset the maximum number of queries at the end of the day");
	        
	        // Persist data (thresholds, buffers, ...) continuously:
	        Intent persistIntent = new Intent(Globals.PERSIST_DATA);
	        PendingIntent pendingPersistIntent = PendingIntent.getBroadcast(context, 0, persistIntent, 0);

	        Calendar currentCal = Calendar.getInstance();
	        alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, currentCal.getTimeInMillis(), Globals.PERSIST_PERIOD, pendingPersistIntent);
		    
	        Log.d(TAG, "AlarmManager registered, to continuously persist data");
	        
		} 
		
		if (intent.getAction().equals(Globals.END_OF_DAY_TASKS)) {
			
	        // Copy the classifier into the Log folder at the end of the day (for later evaluation):
			Calendar cal = Calendar.getInstance();
			cal.add(Calendar.DATE, -1);
			Date currentLocalTime = cal.getTime();
			DateFormat date = new SimpleDateFormat("yyyMMdd");
			String dateString = date.format(currentLocalTime);
			File destFile = new File(Globals.getLogPathYesterday(), "GMM_" + dateString);
			
			try {
				Globals.readWriteLock.readLock().lock();
				Globals.copyFile(new File(Globals.APP_PATH, "GMM.json"), destFile);
				Globals.readWriteLock.readLock().unlock();
			} catch (IOException e) {
				Log.e(TAG, "Failed to copy GMM into log folder");
				e.printStackTrace();
			}
					
			// reset the max number of queries:
			resetQueriesLeft(context);
			

			
			// initiate the transfer of the raw audio data to the server:
			Intent i = new Intent(context, CompressAndSendData.class);
			context.startService(i);
		}
		
		if (intent.getAction().equals(Globals.PERSIST_DATA)) {

			if (variablesInitialized == true) {
				persistAppData();
			}

		}
		
		if (intent.getAction().equals(Globals.MAX_QUERY_NUMBER_CHANGED)) {

			maxQueryNumberChanged(context);
			
		}
		
		if (intent.getAction().equals(Globals.REQUEST_CLASS_NAMES)) {

			classNamesRequested = true;
			
		}
		
		if (intent.getAction().equals(Globals.CONN_MANAGE_CLASSES_RECEIVE)) {

			String[] invalidClasses = intent.getStringArrayExtra(Globals.CONN_MANAGE_CLASSES_INVALIDS);
			String waitOrNoWait = intent.getStringExtra(Globals.CONN_MANAGE_CLASSES_WAIT);
			String filenameOnServer = intent.getStringExtra(Globals.CONN_MANAGE_CLASSES_FILENAME);
			
			if (filenameOnServer != null) {
				 
				Log.i(TAG, "filenameOnServer: " + filenameOnServer);
				 
				// If some classes could not be trained, send a notification to the user
				if (invalidClasses.length != 0) {
					
					String contentText = "";
					
					if (invalidClasses.length == 1) {
						contentText = contentText + "Class " + invalidClasses[0] + " could not be added";
					} else {
						contentText = contentText + "Classes ";
						for(int i=0; i<invalidClasses.length; i++) {
							contentText = contentText + invalidClasses[i] + ", ";
						}
						contentText = contentText.substring(0, (contentText.length()-2)); //remove the comma
						contentText = contentText + " could not be added";
					}

					
					NotificationCompat.Builder builder = new NotificationCompat.Builder(
							context).setSmallIcon(R.drawable.ic_action_warning)
							.setContentTitle("Classes could not be added")
							.setContentText(contentText)
							.setAutoCancel(true)
							.setWhen(System.currentTimeMillis())
							.setTicker("Classes could not be added");

					Intent i = new Intent(context, MainActivity.class);
					i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
					
					Intent notificationIntent = i;
					
					PendingIntent contentIntent = PendingIntent.getActivity(context, 0,
							notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
					builder.setContentIntent(contentIntent);
					
					NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
					manager.notify(Globals.NOTIFICATION_ID_CLASSES_NOT_ADDED, builder.build());	
				}
				
				// Start timer to periodically poll the server for the response:
				Intent i = new Intent(context, ManageClassesGet.class);
				i.putExtra(Globals.CONN_MANAGE_CLASSES_GET_FILENAME, filenameOnServer);
				i.putExtra(Globals.CONN_MANAGE_CLASSES_GET_WAIT, waitOrNoWait);
				context.startService(i);
	
				
			 } else {
				 
				 Log.e(TAG, "Unexpected server response: filename is null. Aborting request");
				 
			 }	
		}
		
		if (intent.getAction().equals(Globals.CONN_MANAGE_CLASSES_FINISH)) {
			
			prevClassnames = intent.getStringArrayExtra(Globals.CONN_MANAGE_CLASSES_PREV_CLASSNAMES);
			
			// Set status to updated, so that the AudioWorker can load the new classifier
			AppStatus.getInstance().set(AppStatus.MODEL_UPDATED);
			Log.i(TAG, "New status: model updated");
			
		}
		
		if (intent.getAction().equals(Globals.CONN_SEND_RAW_AUDIO_RECEIVE)) {
			
			Boolean result = intent.getBooleanExtra(Globals.CONN_SEND_RAW_AUDIO_RESULT, false);
		
			Log.i(TAG, "Sending of raw audio data finsished, result: " + result);
			if (result == true) {
				Log.i(TAG, "Transferring of raw audio data to server was successful");
			} else {
				
				Log.w(TAG, "Transferring or raw audio data failed. Sending notification to user");
				
				NotificationCompat.Builder builder = new NotificationCompat.Builder(
						context).setSmallIcon(R.drawable.ic_action_warning)
						.setContentTitle("Transferring audio data not successful")
						.setAutoCancel(true)
						.setWhen(System.currentTimeMillis())
						.setTicker("Audio data could not be transfered");
				
				Intent goToUploadActivity = new Intent(context, UploadActivity.class);
				PendingIntent uploadIntent = PendingIntent.getActivity(context, 0, goToUploadActivity, 0);
				builder.setContentIntent(uploadIntent);

				NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
				manager.notify(Globals.NOTIFICATION_ID_FILE_TRANSFER, builder.build());
				
			}
		}
		
		if (intent.getAction().equals(Globals.CONN_INIT_MODEL_RECEIVE)) {
			
			String[] invalidClasses = intent.getStringArrayExtra(Globals.CONN_INIT_MODEL_INVALIDS);
			String filenameOnServer = intent.getStringExtra(Globals.CONN_INIT_MODEL_RESULT_FILENAME);
			String waitOrNoWait = intent.getStringExtra(Globals.CONN_INIT_MODEL_RESULT_WAIT);

			if (filenameOnServer != null) {
				 
				Log.i(TAG, "filenameOnServer: " + filenameOnServer);
				 
				// If some classes could not be trained, send a notification to the user
				if (invalidClasses.length != 0) {
					
					String contentText = "";
					
					if (invalidClasses.length == 1) {
						contentText = contentText + "Class " + invalidClasses[0] + " could not be added";
					} else {
						contentText = contentText + "Classes ";
						for(int i=0; i<invalidClasses.length; i++) {
							contentText = contentText + invalidClasses[i] + ", ";
						}
						contentText = contentText.substring(0, (contentText.length()-2)); //remove the comma
						contentText = contentText + " could not be added";
					}
					
					NotificationCompat.Builder builder = new NotificationCompat.Builder(
							context).setSmallIcon(R.drawable.ic_action_warning)
							.setContentTitle("Classes could not be added")
							.setContentText(contentText)
							.setAutoCancel(true)
							.setWhen(System.currentTimeMillis())
							.setTicker("Classes could not be added");

					Intent i = new Intent(context, MainActivity.class);
					i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
					
					Intent notificationIntent = i;
					
					PendingIntent contentIntent = PendingIntent.getActivity(context, 0,
							notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
					builder.setContentIntent(contentIntent);
					
					NotificationManager manager = (NotificationManager) context
							.getSystemService(Context.NOTIFICATION_SERVICE);
					manager.notify(Globals.NOTIFICATION_ID_CLASSES_NOT_ADDED, 
							builder.build());	
				}
				
				// Display Toast if we have to wait for the server:
				Toast.makeText(context, "Model will be created on server until then "
						+ "default classes will be used", Toast.LENGTH_LONG).show();
				
				// Start timer to periodically poll the server for the response:
				Intent i = new Intent(context, InitModelGet.class);
				i.putExtra(Globals.CONN_INIT_MODEL_GET_FILENAME, filenameOnServer);
				i.putExtra(Globals.CONN_INIT_MODEL_GET_WAIT, waitOrNoWait);
				context.startService(i);
	
				
			 } else {
				 
				// Display Toast if we have to wait for the server:
					Toast.makeText(context, "Server not responding, please try again later "
							+ "by using the Manage Classes option", Toast.LENGTH_LONG).show();
				 Log.e(TAG, "Unexpected server response: filename is null. Aborting request");
				 
			 }
		}
		
		if (intent.getAction().equals(Globals.CONN_INIT_MODEL_FINISH)) {
			
			prevClassnames = intent.getStringArrayExtra(Globals.CONN_INIT_MODEL_PREV_CLASSNAMES);
			
			// Set status to updated, so that the AudioWorker can load the new classifier
			AppStatus.getInstance().set(AppStatus.MODEL_UPDATED);
			Log.i(TAG, "New status: model updated");
			
		}
		
		if (intent.getAction().equals(Globals.EVENT_START_TIME_LUNCH)) {
			
			// Get the number of counts for the restaurant class:
			ArrayList<Integer> classCounts = Globals.getIntListPref(context, Globals.CLASS_COUNTS);
			String[] classNames = Globals.getStringArrayPref(context, Globals.CONTEXT_CLASSES);
			int restCount;
			if (Arrays.asList(classNames).contains("Restaurant")) {
				restCount = classCounts.get(Arrays.asList(classNames).indexOf("Restaurant"));
			} else {
				restCount = 0;
			}
			
			SharedPreferences mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
			SharedPreferences.Editor editor = mPrefs.edit();
			editor.putInt(Globals.LUNCH_BREAK_START_COUNT,restCount);
			editor.commit();
			
			Log.i(TAG, "----- EVENT_START_TIME_LUNCH broadcast received -----");
			
			
		}
		
		if (intent.getAction().equals(Globals.EVENT_START_TIME_OVERTIME)) {
			
			// Get the number of counts for the restaurant class:
			ArrayList<Integer> classCounts = Globals.getIntListPref(context, Globals.CLASS_COUNTS);
			String[] classNames = Globals.getStringArrayPref(context, Globals.CONTEXT_CLASSES);
			int restCount;
			if (Arrays.asList(classNames).contains("Office")) {
				restCount = classCounts.get(Arrays.asList(classNames).indexOf("Office"));
			} else {
				restCount = 0;
			}
			
			SharedPreferences mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
			SharedPreferences.Editor editor = mPrefs.edit();
			editor.putInt(Globals.WORKING_OVERTIME_START_COUNT,restCount);
			editor.commit();
			
			Log.i(TAG, "----- EVENT_START_TIME_OVERTIME broadcast received -----");
			
			
		}
		
		// =====================================================================
		// ============= Incorporate new class from server =====================
		// =====================================================================
		
		/*
		 * First get the result of the feasibility request from the server:
		 */
		if (intent.getAction().equals(Globals.CONN_CHECK_FEASIBILITY_RECEIVE)) {
			
			String feasibilityString = intent.getStringExtra(Globals.CONN_CHECK_FEASIBILITY_RESULT);
			String newClassName = intent.getStringExtra(Globals.CONN_CHECK_FEASIBILITY_CLASS_NAME);

			Log.i(TAG, "Received server response from feasibility request. Result: " + feasibilityString);

			if (feasibilityString != null) {
				if (feasibilityString.equals(Globals.FEASIBILITY_NOT_FEASIBLE)) {
					
					String msg = "It is not feasible to train the class " + newClassName
							+ " as not enough sound files available on freesound";
					Log.i(TAG, msg);
					
					Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
					
					// Don't send request to server to add the class:
					return;
					
				} else if (feasibilityString.equals(Globals.FEASIBILITY_FEASIBLE)) {
					
					String msg = "Including the class " + newClassName
							+ " will take longer as we need to download new files to our server";
					Log.i(TAG, msg);
					Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
					
					// Send the the request to actually train a new class on the server:
					Intent ii = new Intent(context, IncorporateNewClass.class);
					ii.putExtra(Globals.CONN_INCORPORATE_NEW_CLASS_NAME, newClassName);
					ii.putExtra(Globals.CONN_CHECK_FEASIBILITY_RESULT, Globals.FEASIBILITY_FEASIBLE);
					context.startService(ii);

				} else if (feasibilityString.equals(Globals.FEASIBILITY_DOWNLOADED)) {
					
					String msg = "The class " + newClassName + " will be available in some minutes";
					Log.i(TAG, msg);
					Toast.makeText(context, msg , Toast.LENGTH_LONG).show();
					
					// Send the the request to actually train a new class on the server:
					Intent ii = new Intent(context, IncorporateNewClass.class);
					ii.putExtra(Globals.CONN_INCORPORATE_NEW_CLASS_NAME, newClassName);
					ii.putExtra(Globals.CONN_CHECK_FEASIBILITY_RESULT, Globals.FEASIBILITY_DOWNLOADED);
					context.startService(ii);
					
				} else {
					
					Log.e(TAG, "Wrong result received after feasibility check");
				}
			} else {
				
				Log.w(TAG, "Could not reach server");

				NotificationCompat.Builder builder = new NotificationCompat.Builder(
						context).setSmallIcon(R.drawable.ic_action_warning)
						.setContentTitle("Server problems: new context class could not be added, "
								+ "please try again later")
						.setAutoCancel(true)
						.setWhen(System.currentTimeMillis())
						.setTicker("Server problems: new context class could not be added");

				Intent i = new Intent(context, MainActivity.class);
				i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				
				Intent notificationIntent = i;
				
				PendingIntent contentIntent = PendingIntent.getActivity(context, 0,
						notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
				builder.setContentIntent(contentIntent);
				
				NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
				manager.notify(Globals.NOTIFICATION_ID_STANDARD, builder.build());				
				
				
			}
			
			waitingForFeedback = false;			
		}
		
		/*
		 * Then receive the result of the request to incorporate a new class (i.e. the filename 
		 * where the classifier can be downloaded when the training is done):
		 */		
		if (intent.getAction().equals(Globals.CONN_INCORPORATE_NEW_CLASS_RECEIVE)) {
			
			String filenameOnServer = intent.getStringExtra(Globals.CONN_INCORPORATE_NEW_CLASS_FILENAME);
			String feasibilityString = intent.getStringExtra(Globals.CONN_CHECK_FEASIBILITY_RESULT);
			
			if (filenameOnServer != null) {
				
				Intent i = new Intent(context, IncorporateNewClassGet.class);
				i.putExtra(Globals.CONN_UPDATED_MODEL_FILENAME, filenameOnServer);
				i.putExtra(Globals.CONN_CHECK_FEASIBILITY_RESULT, feasibilityString);
				context.startService(i);
			} else {
				
				Log.w(TAG, "Could not reach server");

				NotificationCompat.Builder builder = new NotificationCompat.Builder(
						context).setSmallIcon(R.drawable.ic_action_warning)
						.setContentTitle("Server problems: new context class could not be added, "
								+ "please try again later")
						.setAutoCancel(true)
						.setWhen(System.currentTimeMillis())
						.setTicker("Server problems: new context class could not be added");

				Intent i = new Intent(context, MainActivity.class);
				i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				
				Intent notificationIntent = i;
				
				PendingIntent contentIntent = PendingIntent.getActivity(context, 0,
						notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
				builder.setContentIntent(contentIntent);
				
				NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
				manager.notify(Globals.NOTIFICATION_ID_STANDARD, builder.build());
				
			}
		}
		/*
		 * Finally, after receiving the new classifier, update all buffers etc. to incorporate
		 * the new class
		 */
		if (intent.getAction().equals(Globals.CONN_UPDATED_MODEL_RECEIVE)) {
			
			Boolean successful = intent.getBooleanExtra(Globals.CONN_UPDATED_MODEL_RESULT, false);
			prevClassnames = intent.getStringArrayExtra(Globals.CONN_UPDATED_MODEL_PREV_CLASSNAMES);
			
			if (successful == true) {
				
				// Set status to updated, so that the AudioWorker can load the new classifier
				AppStatus.getInstance().set(AppStatus.MODEL_UPDATED);
				Log.i(TAG, "New status: model updated");
				
			} else {
				
				Log.e(TAG, "");

			}
		}
		//=================================================================================
		//=================================================================================
		//=================================================================================
		
	}
	
	// ------------------ methods used for the threshold computation: ------------------
	
	/*
	 * Metric used to set the initial threshold. This value should be slightly lower than
	 * the threshold used after the the first feedback to make sure that we don't miss asking
	 * queries at all due to a too high initial threshold
	 */
	private double initMetric(double mean, double std) {
		return (mean + std);
	}
	
	/*
	 * Part of the threshold calculation accounting for only for values after the model adaption
	 * (calculated with the new model)
	 */
	private double metricAfterFeedback(double mean, double std) {
		return (mean + 3 * std);
	}
	
	/*
	 * Part of the threshold calculation accounting for only for values on the interval that triggered
	 * the query (calculated with the old model)
	 */
	private double metricBeforeFeedback(double mean, double std) {
		return (mean + 2 * std);
	}
	
	public int getMostFrequent(int[] a) {
		
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
	
	// ------------------------------------------------------------------------------------
	
	private onModelAdaptionCompleted listener = new onModelAdaptionCompleted() {

		@Override
		public void onModelAdaptionCompleted(Context context, GMM newGMM) {
			
			Log.i(TAG, "Model adation completed");
			
			Toast.makeText(context,
					(String) "Model adaption completed", Toast.LENGTH_LONG)
					.show();
			
			
		}
	};
	
	@SuppressWarnings("static-access")
	private void callModelAdaption(Context context, int label) {

		if (AppStatus.getInstance().get() != AppStatus.getInstance().MODEL_ADAPTION) {
			
			AppStatus.getInstance().set(AppStatus.getInstance().MODEL_ADAPTION);
			Log.i(TAG, "New status: model adaption");
			
			// Find index of the conversation class, as we do not want to adapt our model for these:
//			int conversationIdx = -1;
//			for(int i=0; i<gmm.get_n_classes(); i++) {
//				if (gmm.get_class_name(i).equals("Conversation")) {
//					conversationIdx = i;
//				}
//			}
			
//			if (label != conversationIdx) {
			
				// Log the AL feedback:
				if (waitingForFeedback == true) {
					appendToALLog(true, label, currentPrediction, false, false);
				} else {
					appendToALLog(true, label, currentPrediction, false, true);
				}
				
				Log.i(TAG, "Model adaption called for class " + String.valueOf(get_class_name(label, classesDict)));
				
				waitingForFeedback = false;
				
				
				executeModelAdaption(context, buffer, label, listener);
				
				// Clear all buffer values etc.
					
				feedbackReceived.set(label, true);
				
				// Calculate first part of the new threshold
				thresQueriedInterval.set(label, tmpQueryCrit);
				
				// Flush the buffers
				queryBuffer.clear();
				predBuffer.clear();
				for(int i=0; i<classNameArray.length; i++) {
					ArrayList<Double> tmp = thresBuffer.get(i);
					tmp.clear();
					thresBuffer.set(i, tmp);
					
					thresSet.set(i, false);
					
					if (feedbackReceived.get(i) == false) {
						ArrayList<Double> tmp2 = initThresBuffer.get(i);
						tmp2.clear();
						initThresBuffer.set(i, tmp2);
					}
				}

				Toast.makeText(context, (String) "Model is being adapted",
						Toast.LENGTH_LONG).show();
				
//			} else {
//				Log.i(TAG, "Conversation class will not be incorporated into our model");
//			}
		} else {
			Toast.makeText(context, (String) "Please wait until previous model adaption finished",
					Toast.LENGTH_LONG).show();
			
			Log.i(TAG, "Model adaption request ignored, as previous model adaption still in progress");
		}
		

	}

	/*
	 * Initiate request of the new class by classing the feasibility check. Is this check is passed,
	 * we tell the server to (download &) train the new model and finally transmit it to the client. (all
	 * this is done sequentially when handling the intents in this BroadcastReceiver)
	 */
	private void requestNewClassFromServer(Context context, String newClassName) {
		
		Intent i = new Intent(context, CheckClassFeasibility.class);
		i.putExtra(Globals.CONN_CHECK_FEASIBILITY_CLASS_NAME, newClassName);
		context.startService(i);
	}
	
	private void sendQuery(final Context context) {

		long[] vibratePattern = {0, Globals.VIBRATE_TIME};
		
		// To cancel the query		
		Intent dismissIntent = new Intent(Globals.DISMISS_NOTIFICATION);
		PendingIntent dismiss = PendingIntent.getBroadcast(context, 0, dismissIntent, 0);
		
		NotificationCompat.Builder builder = new NotificationCompat.Builder(
				context).setSmallIcon(R.drawable.ic_action_help)
				.setContentTitle("What's your current context?")
				.setAutoCancel(true)
				.setWhen(System.currentTimeMillis())
				.setTicker("What's your current context?")
				.setVibrate(vibratePattern)
				.addAction(R.drawable.ic_stat_dismiss, "Dismiss", dismiss);
		
		Intent i = new Intent(context, ContextSelection.class);
		i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		
		Intent notificationIntent = i;
		
		PendingIntent contentIntent = PendingIntent.getActivity(context, 0,
				notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
		builder.setContentIntent(contentIntent);

		NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
		manager.notify(Globals.NOTIFICATION_ID_QUERY, builder.build());
		
		
		// Also display a pop-up dialog:
		Intent popupIntent= new Intent(context, QueryPopup.class);
		popupIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//		popupIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		context.startActivity(popupIntent);
		
		
		
		
		waitingForFeedback = true;
		timeQuerySent = System.currentTimeMillis();
		
		// Start TimerTask to cancel the query:
		CancelQueryTask cancelQueryTask = new CancelQueryTask(context);
        Timer cancelTimer = new Timer();
        cancelTimer.schedule(cancelQueryTask, Globals.CANCEL_QUERY_TIME);
        
        // Decrement the number of queries left (for today):
        numQueriesLeft--;
        Log.d(TAG, numQueriesLeft + " queries left for today");

	}
	
	private void dismissNotifitcation(Context context) {

		NotificationManager notificationManager = (NotificationManager) context
	            .getSystemService(Context.NOTIFICATION_SERVICE);
		notificationManager.cancel(Globals.NOTIFICATION_ID_QUERY);
	}
	
	private void appendToALLog(boolean feedbackReceived, int contextClassGT, 
			int contextClassPredicted, boolean isNewClass, boolean voluntaryFeedback) {
		
		Log.i(TAG, "Appending to AL log");
		
		String classNameGT = null;
		String classNamePredicted = get_class_name(contextClassPredicted, classesDict);
		String timePassed = null;
		String isNewClassString = null;
		String volFeedbackString = null;
		
		if (feedbackReceived == true) {
			classNameGT = get_class_name(contextClassGT, classesDict);
			timePassed = String.valueOf(System.currentTimeMillis()-timeQuerySent);
			if (isNewClass == true) {
				isNewClassString = "new_class";
			} else {
				isNewClassString = "known_class";
			}
			if (voluntaryFeedback == true) {
				volFeedbackString = "voluntarily";
				timePassed = String.valueOf(-1);
			} else {
				volFeedbackString = "query_response";
			}
		} else {
			classNameGT = String.valueOf(-1);
			timePassed = String.valueOf(-1);
			isNewClassString = String.valueOf(-1);
			volFeedbackString = String.valueOf(-1);
		}

		double timeSinceStartRec = (System.currentTimeMillis() - Globals.RECORDING_START_TIME) / 1000.0;
		
		try {
			File file = new File(Globals.getLogPath(), Globals.AL_LOG_FILENAME);
			FileWriter f = new FileWriter(file, true);
			f.write(timeSinceStartRec + "\t" + classNamePredicted + "\t" +  
			classNameGT + "\t" + timePassed + "\t" + isNewClassString + "\t" + volFeedbackString + "\n");
			f.close();
		} catch (IOException e) {
			Log.e(TAG, "Writing to AL log file failed");
			e.printStackTrace();
		}
	}
	
	/*
	 * If we are recording of the transition from one day to another, we have to take the following
	 * actions (don't do anything if not):
	 * 
	 * - Reset the recording time
	 * - Finish the log files from the previous day
	 * - Create the start-Stop-, prediction- and ground truth log files and add the 
	 * 	starting time
	 * 
	 * Make sure that this method is called before the archiving of the log folder (i.e.
	 * between 00:00 and 00:05)
	 * 
	 */
	private void dayChangeTasks(Context context) {
		
		File predLogFile = new File(Globals.getLogPath(), Globals.PRED_LOG_FILENAME); // new log file
		
		// Check if just after midnight:
		Calendar cal = Calendar.getInstance();
		long now = cal.getTime().getTime();
		cal.set(Calendar.HOUR_OF_DAY, 0);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MILLISECOND, 0);
		long midnight = cal.getTime().getTime();
		cal.set(Calendar.HOUR_OF_DAY, 0);
		cal.set(Calendar.MINUTE, 5);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MILLISECOND, 0);
		long justAfterMidnight = cal.getTime().getTime();

		if ((!predLogFile.exists()) && (now >= midnight)
				&& (now <= justAfterMidnight)) {

			Globals.RECORDING_START_TIME = System.currentTimeMillis();

			/*
			 * First finished the log files in the folder from yesterday:
			 */
			SharedPreferences mPrefs = PreferenceManager
					.getDefaultSharedPreferences(context);
			long recordingStopped = mPrefs.getLong(
					Globals.LASTEST_RECORDING_TIMESTAMP, -1);
			try {

				long recordingLastStarted = 0;
				double recordingStoppedRel = 0.0;
				File startStopFileYesterday = new File(
						Globals.getLogPathYesterday(),
						Globals.START_STOP_LOG_FILENAME);

				BufferedReader br = new BufferedReader(new FileReader(
						startStopFileYesterday));
				String lastLine = null;
				String tmpLine = null;

				while ((tmpLine = br.readLine()) != null) {
					lastLine = tmpLine;
				}
				br.close();

				if (lastLine.contains("start")) {
					recordingLastStarted = Long.valueOf(lastLine.substring(0,
							lastLine.indexOf("\t")));

					recordingStoppedRel = (recordingStopped - recordingLastStarted) / 1000.0;

				} else {
					Log.e(TAG,
							"Start-stop log file corrupted, couldn't calulcate stop time");
				}

				File predFileYesterday = new File(
						Globals.getLogPathYesterday(),
						Globals.PRED_LOG_FILENAME);
				if (predFileYesterday.exists()) {
					FileWriter f = new FileWriter(predFileYesterday, true);
					f.write("\t" + recordingStoppedRel + "\n");
					f.close();
				}

			} catch (IOException e) {
				e.printStackTrace();
				Log.w(TAG,
						"Relative stop time could not be added to prediction log file");
			}

			/*
			 * Then create the new log files and add the starting time:
			 */
			Log.d(TAG, "Appending to start time of the app to log");
			try {
				File startStopFile = new File(Globals.getLogPath(),
						Globals.START_STOP_LOG_FILENAME);
				FileWriter f = new FileWriter(startStopFile, true);
				f.write(System.currentTimeMillis() + "\t" + "start" + "\n");
				f.close();
			} catch (IOException e) {
				Log.e(TAG, "Writing to start log file failed");
				e.printStackTrace();
			}

			try {
				FileWriter f = new FileWriter(predLogFile, true);
				f.write("RECORDING_STARTED" + "\n");
				f.close();
			} catch (IOException e) {
				Log.e(TAG, "Writing to prediction log file failed");
				e.printStackTrace();
			}

			try {
				File gtLogFile = new File(Globals.getLogPath(),
						Globals.GT_LOG_FILENAME);
				FileWriter f = new FileWriter(gtLogFile, true);
				f.write("RECORDING_STARTED" + "\n");
				f.close();
			} catch (IOException e) {
				Log.e(TAG, "Writing to GT log file failed");
				e.printStackTrace();
			}
			
		}
		
	}
	
	private void appendToPredLog(Context context, String className) {
		
		Log.d(TAG, "Appending to prediction log file");

		File predLogFile = new File(Globals.getLogPath(), Globals.PRED_LOG_FILENAME); 
		
		try {
			
			BufferedReader br = new BufferedReader(new FileReader(predLogFile));
		    String lastLine = null;
		    String tmpLine = null;

		    while ((tmpLine = br.readLine()) != null) {
		    	lastLine = tmpLine;
		    }
		    br.close();
		    
		    double timeSinceStartRec = (System.currentTimeMillis() - Globals.RECORDING_START_TIME) / 1000.0;
		    
		    if(lastLine.equals("RECORDING_STARTED")) {
		    	
				FileWriter f = new FileWriter(predLogFile, true);
				f.write(className + "\t" + timeSinceStartRec + "\t");
				f.close();
				
		    } else {
		    	
				FileWriter f = new FileWriter(predLogFile, true);
				f.write(timeSinceStartRec + "\n" + className + "\t" + timeSinceStartRec + "\t");
				f.close();
				
		    }
			

		} catch (IOException e) {
			Log.e(TAG, "Writing to prediction log file failed");
			e.printStackTrace();
		}
	}
	
	private void resetQueriesLeft(Context context) {
		
		mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
		
		int tmp = mPrefs.getInt(Globals.MAX_NUM_QUERIES, -1);
		if (tmp != -1) {
			numQueriesLeft = tmp;
			Log.i(TAG, "Maximum number of queries resetted to " + tmp);
		} else {
			Log.e(TAG, "Got invalid value from preference, could not reset reset max number of queries");
		}

	}
	
	/* 
	 * Called if the maximum number of queries is changed in the settings activity and updates numQueriesLeft
	 * accordingly
	 */
	private void maxQueryNumberChanged(Context context) {
		
		mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
		
		int newValue = mPrefs.getInt(Globals.MAX_NUM_QUERIES, -1);
		int prevValue = mPrefs.getInt(Globals.PREV_MAX_NUM_QUERIES, -1);
		if ((newValue != -1) && (prevValue != -1)) {
			
			int diff = newValue - prevValue;

			numQueriesLeft = numQueriesLeft + diff;
			
			if (numQueriesLeft < 0) {
				numQueriesLeft = 0;
			}
			
			Log.i(TAG, "Maximum number of queries changed to " + numQueriesLeft);
		} else {
			Log.e(TAG, "Got invalid value from preference, could not reset reset max number of queries");
		}

	}
	
	/*
	 * Save buffers, threshold values etc periodically to external storage
	 */
	private void persistAppData() {
		
		AppData appData = new AppData(initThresSet, thresSet, feedbackReceived, 
				threshold, initThresBuffer, thresBuffer, thresQueriedInterval, numQueriesLeft);
		
		
		String str = new Gson().toJson(appData);
		
		if(!str.equals("{}")) { //Only write to file, if string is not empty
			FileOutputStream f = null;
			try {
				f = new FileOutputStream(Globals.APP_DATA_FILE);
			} catch (FileNotFoundException e) {
				Log.e(TAG, "App data file to store JSON not found");
				e.printStackTrace();
			}
			try {
				f.write(str.getBytes());
				f.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		} else {
			Log.e(TAG, "Data could not be persisted, as generated JSON string was empty");
		}
	}
	
	/*
	 * Save the prediction results (total numbers) from the last day, so that we can plot them. 
	 * After that reset the values, so that we start counting at zero on the new day.
	 * 
	 * Call this method whenever a new prediction arrives
	 */
	private void persistPredictionData(Context context) {
		
		boolean persistData = false;
		
		Calendar cal = Calendar.getInstance();
		Date today = cal.getTime();
		
		SharedPreferences mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
		totalSilenceCount = mPrefs.getInt(Globals.SILENCE_COUNTS, 0);
		
		long tmpFirstStarted = mPrefs.getLong(Globals.DATE_FIRST_STARTED, -1);
		long tmpLastRecorded = mPrefs.getLong(Globals.DATE_LAST_RECORDED, -1);
		Date lastRecorded = new Date(tmpLastRecorded);
		
		if (tmpFirstStarted != -1) {
			
			Date firstStarted = new Date(tmpFirstStarted);
			
			/*
			 *  lastPersisted refers to the date on which the data was recorded, not the day when we write the
			 *  data to the storage (this is always later!) 
			 */
			long tmpLastPersisted = mPrefs.getLong(Globals.DATE_LAST_PERSISTED, -1);
			if (tmpLastPersisted != -1) {
				
				/*
				 *  Data has already been persisted before. So we only want to persist, if 
				 *  lastPersisted != lastRecorded != today
				 */
				Calendar calToday = Calendar.getInstance();
				calToday.setTime(today);
				Date lastPersisted = new Date(tmpLastPersisted);
				Calendar calLastPersisted = Calendar.getInstance();
				calLastPersisted.setTime(lastPersisted);
				
				Calendar calLastRecorded = Calendar.getInstance();
				calLastRecorded.setTime(lastRecorded);
				
				/*
				 *  Only persist, if TODAY, DATE_LAST_PERSISTED and DATE_LAST_RECORDED are
				 *  each on days, so we make sure to only persist when the very first prediction
				 *  of that date arrives:
				 */
				if ((calLastPersisted.get(Calendar.DAY_OF_YEAR) != calLastRecorded.get(Calendar.DAY_OF_YEAR))
						&& (calToday.get(Calendar.DAY_OF_YEAR) != calLastRecorded.get(Calendar.DAY_OF_YEAR))
						&& (calToday.get(Calendar.DAY_OF_YEAR) != calLastPersisted.get(Calendar.DAY_OF_YEAR))) {
					
					Log.i(TAG, "First if: ");
					Log.i(TAG, "calLastPersisted: " + calLastPersisted.get(Calendar.DAY_OF_YEAR));
					Log.i(TAG, "calLastRecorded: " + calLastRecorded.get(Calendar.DAY_OF_YEAR));
					Log.i(TAG, "calToday: " + calToday.get(Calendar.DAY_OF_YEAR));
					
					persistData = true;
					
				}
				
				
			} else {
				/*
				 *  Data hasn't been persisted before, so we want to persist if we finished recording
				 *  a whole day, i.e. if today is NOT the DATE_FIRST_STARTED
				 */
				Calendar calToday = Calendar.getInstance();
				calToday.setTime(today);
				Calendar calFirstStarted = Calendar.getInstance();
				calFirstStarted.setTime(firstStarted);
				
				if (calToday.get(Calendar.DAY_OF_YEAR) != calFirstStarted.get(Calendar.DAY_OF_YEAR)) {
					
					Log.i(TAG, "Second if: ");
					Log.i(TAG, "calToday: " + calToday.get(Calendar.DAY_OF_YEAR));
					Log.i(TAG, "calFirstStarted: " + calFirstStarted.get(Calendar.DAY_OF_YEAR));
					
					persistData = true;
					
				}
			}
			
		} else {
			
			/*
			 *  First date hasn't been set yet, so initialize it to today. We don't have to stored
			 *  data here, as we just started using the app
			 */
			SharedPreferences.Editor editor = mPrefs.edit();
			editor.putLong(Globals.DATE_FIRST_STARTED, today.getTime());
			editor.commit();
			
			Log.i(TAG, "Date first started set");
			
		}
		
		if (persistData == true) {
			if(!Globals.HISTORIC_PREDICTIONS_FILE.exists()) {
				
				// If the file doesn't exists yet, create a completely new one:
				HistoricPredictions historicPredictions = new HistoricPredictions(totalCount, 
						totalSilenceCount, classNameArray, lastRecorded);
				
				String str = new Gson().toJson(historicPredictions);
				
				if(!str.equals("{}")) { //Only write to file, if string is not empty
					FileOutputStream f = null;
					try {
						f = new FileOutputStream(Globals.HISTORIC_PREDICTIONS_FILE);
						
					} catch (FileNotFoundException e) {
						e.printStackTrace();
					}
					try {
						
						f.write(str.getBytes());
						f.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				} else {
					
					Log.e(TAG, "Historic predictions could not be persisted, "
							+ "as generated JSON string was empty");
				}
			} else {
				
				// If the file already exists, we need to read it in and just append the newest values:
				Gson gson = new Gson();
				HistoricPredictions histRead;

				if(Globals.HISTORIC_PREDICTIONS_FILE.exists()) {
					
					Log.d(TAG,"JSON file found");
					
					try {
						BufferedReader br = new BufferedReader(new FileReader(Globals.HISTORIC_PREDICTIONS_FILE));
					
						histRead = gson.fromJson(br, HistoricPredictions.class);
						
					} catch (IOException e) {
						histRead = null;
						Log.e(TAG,"Couldn't open JSON file");
						e.printStackTrace();
					}
				} else {
					histRead = null;
					Log.e(TAG, "File does not exist: " + Globals.APP_DATA_FILE.toString());
		        }
				
				if (histRead != null) {
					// Append elements to the HistoricPredictions object:
					histRead.append_to_prediction_list(totalCount);
					histRead.append_to_silence_list(totalSilenceCount);
					histRead.append_to_context_class_list(classNameArray);
					histRead.append_to_date_list(lastRecorded);
					
					String str = new Gson().toJson(histRead);
					
					if(!str.equals("{}")) { // Only write to file, if string is not empty
					FileOutputStream f = null;
					try {
						f = new FileOutputStream(Globals.HISTORIC_PREDICTIONS_FILE);
						
					} catch (FileNotFoundException e) {
						
						Log.e(TAG, "App data file to store JSON not found");
						e.printStackTrace();
					}
					try {
						
						f.write(str.getBytes());
						f.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				} else {
					Log.i(TAG, "histRead is null");
					} 
				}
			
			}
			
			// Reset values to zero when a new day started:
			for(int i=0; i<totalCount.size(); i++) {
				totalCount.set(i, 0);
			}
			Globals.setIntListPref(context, Globals.CLASS_COUNTS, totalCount);
			totalSilenceCount = 0;
			SharedPreferences.Editor editor = mPrefs.edit();
			
			editor.putInt(Globals.SILENCE_COUNTS, totalSilenceCount);
			
			editor.putLong(Globals.DATE_LAST_PERSISTED, tmpLastRecorded);
			
			// Also reset the number of variable for the event detection:
			editor.putInt(Globals.LUNCH_BREAK_START_COUNT, 0);
			editor.putInt(Globals.WORKING_OVERTIME_START_COUNT, 0);
			// Also reset the boolean indicating if an event was detected on a day:
			editor.putBoolean(Globals.LUNCH_BREAK_DETECTED, false);
			editor.putBoolean(Globals.WEEKEND_WORK_DETECTED, false);
			editor.putBoolean(Globals.WORKING_OVERTIME_DETECTED, false);
			editor.putBoolean(Globals.FEW_CONVERSATIONS_DETECTED, false);
			editor.putBoolean(Globals.MAKING_TRIP_DETECTED, false);
			editor.putBoolean(Globals.LITTLE_SILENCE_DETECTED, false);
			
			editor.commit();
			
			Log.i(TAG, "Data persisted");
			
		} else {
			// Do nothing
		}
		
		/*
		 * By always saving the date when the last data was recorded, we know to which day
		 * we have to assign the data when we detect a day change
		 */
		SharedPreferences.Editor editor = mPrefs.edit();
		editor.putLong(Globals.DATE_LAST_RECORDED, today.getTime());
		editor.commit();
	}
	
	
	/*
	 * Read in buffers, thresholds, ... is called when the application is started, to read in values
	 * from the last time
	 */
	private AppData readAppData() {
		
		Gson gson = new Gson();
		
		AppData appData;

		if(Globals.APP_DATA_FILE.exists()) {
			
			Log.v(TAG,"JSON file found");
			
			try {
				BufferedReader br = new BufferedReader(new FileReader(Globals.APP_DATA_FILE));
			
				appData = gson.fromJson(br, AppData.class);
				
			} catch (IOException e) {
				appData = null;
				Log.e(TAG,"Couldn't open JSON file");
				e.printStackTrace();
			}
		} else {
			appData = null;
			Log.e(TAG, "File does not exist: " + Globals.APP_DATA_FILE.toString());
        }
		
		return appData;
	}
	
	
	class CancelQueryTask extends TimerTask {
		
		Context context;
		
		public CancelQueryTask(Context c) {
			this.context = c;
		}
		public void run() {

			Log.d(TAG, "CancelQueryTask started");
			
				waitingForFeedback = false;

				appendToALLog(false, -1, currentPrediction, false, false);
				
				dismissNotifitcation(context);
		}
	}
	
	class RemindTask extends TimerTask {
		
		Context context;
		
		public RemindTask(Context c) {
			this.context = c;
		}
		public void run() {
			
			Log.d(TAG, "VibrateTask started");
			
			if (waitingForFeedback == true) {
				
				// Dismiss the old query:
				dismissNotifitcation(context);
				
				// And create a new one:
				Intent dismissIntent = new Intent(Globals.DISMISS_NOTIFICATION);
				PendingIntent dismiss = PendingIntent.getBroadcast(context, 0, dismissIntent, 0);
				
				long[] vibratePattern = {0, Globals.VIBRATE_TIME};
				
				NotificationCompat.Builder builder = new NotificationCompat.Builder(
						context).setSmallIcon(R.drawable.ic_action_help)
						.setContentTitle("What's your current context?")
						.setAutoCancel(true)
						.setWhen(System.currentTimeMillis())
						.setTicker("What's your current context?")
						.setVibrate(vibratePattern)
						.addAction(R.drawable.ic_stat_dismiss, "Dismiss", dismiss);
				
				
				
				Intent i = new Intent(context, ContextSelection.class);
				i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				
				Intent notificationIntent = i;
				
				PendingIntent contentIntent = PendingIntent.getActivity(context, 0,
						notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
				builder.setContentIntent(contentIntent);

				NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
				manager.notify(Globals.NOTIFICATION_ID_QUERY, builder.build());
				
				Vibrator vibrator = (Vibrator) this.context.getSystemService(Context.VIBRATOR_SERVICE);
				vibrator.vibrate(Globals.VIBRATE_TIME);
				
				this.cancel();
			}
		}
	}
	
	/*
	 * After we changed the number of classes, we have to change the size of the buffers etc. to calculate the
	 * threshold values. This function is only called, after we received the first prediction with the
	 * new classifier.
	 */
	@SuppressWarnings("unchecked")
	private void changeBuffersAfterModelChange(Context context, String[] newClassnames, String[] prevClassnames) {
	
		Log.i(TAG, "changeBuffersAfterModelChange called");
		
		/*
		 *  Check first if the buffers... are already initialized at all (if not, it
		 *  will be done automatically later...
		 */
		if (variablesInitialized == true) {
					
			// Create temporary copies of all ArrayLists:
			ArrayList<Boolean> initThresSetTmp = new ArrayList<Boolean>(initThresSet);
			ArrayList<Boolean> thresSetTmp = new ArrayList<Boolean>(thresSet);
			ArrayList<Boolean> feedbackReceivedTmp = new ArrayList<Boolean>(feedbackReceived);
			ArrayList<Double> thresholdTmp = new ArrayList<Double>(threshold);
			ArrayList<ArrayList<Double>> initThresBufferTmp = new ArrayList<ArrayList<Double>>(initThresBuffer);
			ArrayList<ArrayList<Double>> thresBufferTmp = new ArrayList<ArrayList<Double>>(thresBuffer);
			ArrayList<Double> thresQueriedIntervalTmp = new ArrayList<Double>(thresQueriedInterval);
			ArrayList<Integer> totalCountTmp = new ArrayList<Integer>(totalCount);
			
			// Clear all ArrayLists:
			initThresSet = new ArrayList<Boolean>();
			thresSet = new ArrayList<Boolean>();
			feedbackReceived = new ArrayList<Boolean>();
			threshold = new ArrayList<Double>();
			initThresBuffer = new ArrayList<ArrayList<Double>>();
			thresBuffer = new ArrayList<ArrayList<Double>>();
			thresQueriedInterval = new ArrayList<Double>();
			totalCount = new ArrayList<Integer>();
			
			// And fill them again in the correct order:
			for(int i=0; i<newClassnames.length; i++) {
				
				/*
				 *  Index of this element in the String Array of the previous classes:
				 *  
				 *  i -> index in the new classes
				 *  
				 *  idx -> index in the old classes (= -1 if it doesn't exist)
				 */
				int idx = ArrayUtils.indexOf(prevClassnames, newClassnames[i]);
				
				if (idx == -1) {
					// If the class is completely new, initialize empty ArrayLists for this class:
					initThresSet.add(false);
					thresSet.add(false);
					feedbackReceived.add(false);
					threshold.add(-1.0);
					initThresBuffer.add(new ArrayList<Double>());
					thresBuffer.add(new ArrayList<Double>());
					thresQueriedInterval.add(-1.0);
					totalCount.add(0);
					
					Log.i(TAG, "Class " + newClassnames[i] + " was not found in old array");
					
				} else {
					/*
					 * If the class was already in the previous model, assign the already existing buffers
					 * and thresholds to the class:
					 */
					initThresSet.add(initThresSetTmp.get(idx));
					thresSet.add(thresSetTmp.get(idx));
					feedbackReceived.add(feedbackReceivedTmp.get(idx));
					threshold.add(thresholdTmp.get(idx));
					initThresBuffer.add((ArrayList<Double>) initThresBufferTmp.get(idx).clone());
					thresBuffer.add((ArrayList<Double>) thresBufferTmp.get(idx).clone());
					thresQueriedInterval.add(thresQueriedIntervalTmp.get(idx));
					totalCount.add(totalCountTmp.get(idx));				
				}
			
				// Clear the buffers to calculate the thresholds for all classes:
				ArrayList<Double> tmp = thresBuffer.get(i);
				tmp.clear();
				thresBuffer.set(i, tmp);

				thresSet.set(i, false);

				if (feedbackReceived.get(i) == false) {
					ArrayList<Double> tmp2 = initThresBuffer.get(i);
					tmp2.clear();
					initThresBuffer.set(i, tmp2);
				}
			
			}

			// Flush the query and the prediction buffers for all classes:	
			queryBuffer.clear();
			predBuffer.clear();			
			
		
		} else { // Initialize the buffers etc. if they are not initialized yet:
			
			// Load data from JSON file from external storage if it already exists:
			if (Globals.APP_DATA_FILE.exists()) {
				Log.i(TAG, "Loading app data from JSON file");
				
				AppData appData = readAppData();
				initThresSet = appData.get_initThresSet();
				thresSet = appData.get_thresSet();
				feedbackReceived = appData.get_feedbackReceived();
				threshold = appData.get_threshold();
				initThresBuffer = appData.get_initThresBuffer();
				thresBuffer = appData.get_thresBuffer();
				thresQueriedInterval = appData.get_thresQueriedInterval();
				numQueriesLeft = appData.get_numQueriesLeft();
				
				totalCount = Globals.getIntListPref(context, Globals.CLASS_COUNTS);
				
				SharedPreferences mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
				totalSilenceCount = mPrefs.getInt(Globals.SILENCE_COUNTS, 0);

			} else {
				/*
				 *  If it doesn't exists (i.e. at the very first start) initialize variables empty:
				 *  (same goes for the buffer containing the MFCC values)
				 */
				Log.i(TAG, "Initializing empty buffers and thresholds, because no JSON file was created yet");
				
				initThresSet = new ArrayList<Boolean>();
				thresSet = new ArrayList<Boolean>();
				feedbackReceived = new ArrayList<Boolean>();
				threshold = new ArrayList<Double>();
				initThresBuffer = new ArrayList<ArrayList<Double>>();
				thresBuffer = new ArrayList<ArrayList<Double>>();
				thresQueriedInterval = new ArrayList<Double>();
				
				for(int i=0; i<newClassnames.length; i++) {
					initThresSet.add(false);
					thresSet.add(false);
					feedbackReceived.add(false);
					threshold.add(-1.0);
					initThresBuffer.add(new ArrayList<Double>());
					thresBuffer.add(new ArrayList<Double>());
					thresQueriedInterval.add(-1.0);
				}
				
				totalCount = new ArrayList<Integer>();
				for(int i=0; i<newClassnames.length; i++) {
					totalCount.add(0);
				}
				
				SharedPreferences mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
				totalSilenceCount = mPrefs.getInt(Globals.SILENCE_COUNTS, 0);

				resetQueriesLeft(context);
				
			}						
			
			// These buffers doesn't need to be taken from the last run:
			queryBuffer = new ArrayList<Double>();
			predBuffer = new ArrayList<Integer>();
			
			// Save String array of the context classes to preferences: //already done before
			//Globals.setStringArrayPref(context, Globals.CONTEXT_CLASSES, newClassnames);

			if (classNamesRequested == true) {
				classNamesRequested = false;
			}

			Log.i(TAG, "Number of context classes: " + newClassnames);
			
			variablesInitialized = true;
			
		}
		
		AppStatus.getInstance().set(AppStatus.NORMAL_CLASSIFICATION);
		
		Globals.setStringArrayPref(context, Globals.CLASSES_BEING_ADDED, null);
		Globals.setStringArrayPref(context, Globals.CLASSES_BEING_REMOVED, null);
		
		// Broadcast this message, that other activities can rebuild their views:
		Intent i2 = new Intent(Globals.CLASS_NAMES_SET);
		context.sendBroadcast(i2);
		
		// Display notification:
		NotificationCompat.Builder builder = new NotificationCompat.Builder(
				context).setSmallIcon(R.drawable.ic_stat_confirm)
				.setContentTitle("Changed model successfully")
				.setAutoCancel(true)
				.setWhen(System.currentTimeMillis())
				.setTicker("Changed model successfully");

		Intent i = new Intent(context, MainActivity.class);
		i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		
		Intent notificationIntent = i;
		
		PendingIntent contentIntent = PendingIntent.getActivity(context, 0,
				notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
		builder.setContentIntent(contentIntent);
		
		NotificationManager manager = (NotificationManager) context
				.getSystemService(Context.NOTIFICATION_SERVICE);
		manager.notify(Globals.NOTIFICATION_ID_STANDARD,
				builder.build());	
	}
	
	/*
	 * Allow parallel execution for the AsyncTask
	 * 
	 * Code similar to http://stackoverflow.com/questions/4068984/running-multiple-asynctasks-at-the-same-time-not-possible/13800208#13800208
	 */
	@TargetApi(Build.VERSION_CODES.HONEYCOMB) // API 11
	private static <T> void executeModelAdaption(Context context, ArrayList<double[]> buffer, int label,
			onModelAdaptionCompleted listener) {
		
		prevClassnames = new String[classNameArray.length];
		// Fill the prevClassnames array with the current class names:
		for(int i=0; i<classNameArray.length; i++) {

			prevClassnames[i] = classNameArray[i];
			
		}
		
		ModelAdaptor modelAdaptor = new ModelAdaptor(buffer, label, listener);
	    if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
	    	modelAdaptor.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, context);
	    else
	    	modelAdaptor.execute(context);
	}
	
	// Return name of the class for a given integer
	private String get_class_name(int i, Map<String, Integer> cl) {
		String res = new String();
		for (Entry<String, Integer> entry : cl.entrySet()) {
            if (entry.getValue() == i) {
            	res = entry.getKey();
            }
        }
		return res;	
	}
	
}
