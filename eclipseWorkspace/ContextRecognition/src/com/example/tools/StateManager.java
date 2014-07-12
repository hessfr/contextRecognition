package com.example.tools;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutionException;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.math3.stat.descriptive.moment.Mean;
import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import com.example.communication.CheckClassFeasibility;
import com.example.communication.GetNewModel;
import com.example.communication.IncorporateNewClass;
import com.example.contextrecognition.ContextSelection;
import com.example.contextrecognition.Globals;
import com.example.contextrecognition.R;
import com.example.tools.ModelAdaptor.onModelAdaptionCompleted;
import com.google.gson.Gson;

/*
 * Handles all broadcasts and holds all prediction var	iables like current context, buffers, class names, ...
 * 
 * AL Queries are also sent from here...
 */
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
	public static Map<String, Integer> classesDict = new HashMap<String, Integer>(); // Needed??
	private static String[] classNameArray;
	private static boolean bufferStatus;
	private static GMM gmm; // Needed??
	
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
	
	private static boolean testBool = false; // for testing only
	
	private static boolean classNamesRequested = false; 
	
	private static long startTime;
	private static long endTime;
	
	SharedPreferences mPrefs;
	
	public static final int NOTIFICATION_ID = 1;
	
	@Override
	public void onReceive(Context context, Intent intent) {
		
		Bundle bundle = intent.getExtras();

		if (bundle != null) {

			if (intent.getAction().equals(Globals.PREDICTION_INTENT)) {

				int resultCode = bundle.getInt(Globals.RESULTCODE);

				if (resultCode == Activity.RESULT_OK) {

					// The following lines have to be in exactly the same order as the were put on the bundle (in the AudioWorker):

					currentPrediction = bundle.getInt(Globals.PREDICTION_INT);
					currentEntropy = bundle.getDouble(Globals.PREDICTION_ENTROPY);
					predictionString = bundle.getString(Globals.PREDICTION_STRING);
					
					classNameArray = bundle.getStringArray(Globals.CLASS_STRINGS);
//					Log.i(TAG, classNameArray[0]);
					
					bufferStatus = bundle
							.getBoolean(Globals.BUFFER_STATUS);
//					Log.i(TAG, String.valueOf(bufferStatus));
					
					Serializable s1 = bundle.getSerializable(Globals.BUFFER);
					if (waitingForFeedback == false) {
						buffer = (ArrayList<double[]>) s1;
					}
//					Log.i(TAG, String.valueOf(buffer.get(0)[0]));

					gmm = bundle.getParcelable(Globals.GMM_OBJECT); // Needed??

					Serializable s2 = new HashMap<String, Integer>();
					s2 = bundle.getSerializable(Globals.CLASSES_DICT);
//					classesDict = (HashMap<String, Integer>) s2;
					
					// For testing only:
					if (testBool == false) {
						testBool = true;
						
						//requestNewClassFromServer("Restaurant");
						
						CheckClassFeasibility feasReq = new CheckClassFeasibility();
						
						try {
							Boolean result = feasReq.execute("Restaurant").get();
							
							Log.w(TAG, "Result: " + result);
							
						} catch (InterruptedException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						} catch (ExecutionException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						
						
					}
					
					Log.i(TAG, "Current Prediction: " + predictionString + ": " + currentPrediction);

					//=================================================================================
					//============ Handle sending of query, threshold calculations, ... ===============
					//=================================================================================
					
					startTime = System.currentTimeMillis();
					
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

						} else {
							/*
							 *  If it doesn't exists (i.e. at the very first start) initialize variables empty:
							 *  (same goes for the buffer containing the MFCC values)
							 */
							
							Log.i(TAG, "Initializing empty app data, because no JSON file was created yet");
							
							initThresSet = new ArrayList<Boolean>();
							thresSet = new ArrayList<Boolean>();
							feedbackReceived = new ArrayList<Boolean>();
							threshold = new ArrayList<Double>();
							initThresBuffer = new ArrayList<ArrayList<Double>>();
							thresBuffer = new ArrayList<ArrayList<Double>>();
							thresQueriedInterval = new ArrayList<Double>();
							
							for(int i=0; i<gmm.get_n_classes(); i++) {
								initThresSet.add(false);
								thresSet.add(false);
								feedbackReceived.add(false);
								threshold.add(-1.0);
								initThresBuffer.add(new ArrayList<Double>());
								thresBuffer.add(new ArrayList<Double>());
								thresQueriedInterval.add(-1.0);
							}
							
							resetQueriesLeft(context);

							Intent i = new Intent(Globals.CLASS_NAMES_SET);
							context.sendBroadcast(i);
							
						}						
						
						// These buffers doesn't need to be taken from the last run:
						queryBuffer = new ArrayList<Double>();
						predBuffer = new ArrayList<Integer>();
						
						// Save String array of the context classes to preferences:
						Globals.setStringArrayPref(context, Globals.CONTEXT_CLASSES, gmm.get_string_array());

						if (classNamesRequested == true) {
							Intent i = new Intent(Globals.CLASS_NAMES_SET);
							context.sendBroadcast(i);
							classNamesRequested = false;
						}
						
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
							Log.i(TAG, "Initial threshold for class " + gmm.get_class_name(currentPrediction) +
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
								
								Log.i(TAG, "Threshold for class " + gmm.get_class_name(currentPrediction) +
										" updated to " + newThreshold);
								
								thresSet.set(currentPrediction, true);
							}
							
						}

						Log.i(TAG, "thresBuffer length: " + thresBuffer.get(currentPrediction).size());

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

							if ((queryCrit > threshold.get(currentPrediction)) && (waitingForFeedback == false) && (numQueriesLeft > 0) &&
									((System.currentTimeMillis() - prevTime) > Globals.minBreak)) {
								
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
								
					long sincePrevEndTime = System.currentTimeMillis() - endTime;
					
					endTime = System.currentTimeMillis();
					
					long diff = endTime-startTime;
					
					//Log.w(TAG, "Time: " + startTime);
					//Log.w(TAG, "Time: " + endTime);
					//Log.w(TAG, "Time for one computation: " + diff);
					//Log.w(TAG, "Time since last cycle: " + sincePrevEndTime);
					
					//=================================================================================
					//=================================================================================
					//=================================================================================

					// Save to log file and send broadcast to change text, if prediction has changed
					if (!predictionString.equals(prevPredictionString)) {
						
						appendToPredLog(predictionString);
						
						Intent i = new Intent(Globals.PREDICTION_CHANGED_INTENT);
						Bundle b = new Bundle();
						b.putString(Globals.NEW_PREDICTION_STRING, predictionString);
						i.putExtras(b);
						context.sendBroadcast(i);					
				
						prevPredictionString = predictionString;
					}
					
					//setText(predictionString); //TODO
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
						Toast.LENGTH_SHORT).show();

			} else if (intent.getAction().equals(Globals.MODEL_ADAPTION_NEW_INTENT)) {
				
				String newClassName = bundle.getString(Globals.NEW_CLASS_NAME);

				requestNewClassFromServer(context, newClassName);
				
			} else if (intent.getAction().equals(Globals.CALL_CONTEXT_SELECTION_INTENT)) {
				
				callContextSelectionActivity(context);
				
			}

		}
		
		if (intent.getAction().equals(Globals.DISMISS_NOTIFICATION)) {
			
			dismissNotifitcation(context);
			
			Log.i(TAG, "Query dismissed");
			
		}
		
		if (intent.getAction().equals(Globals.REGISTER_RECURRING_TASKS)) {
			
			Calendar updateTime = Calendar.getInstance();
		    updateTime.setTimeZone(TimeZone.getTimeZone("GMT"));
		    updateTime.set(Calendar.HOUR_OF_DAY, 23);
		    updateTime.set(Calendar.MINUTE, 59);			
		    
		    Intent resetIntent = new Intent(Globals.RESET_MAX_QUERY_NUMBER);
	        PendingIntent pendingResetIntent = PendingIntent.getBroadcast(context, 0, resetIntent, 0);
	        
	        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
	        alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, updateTime.getTimeInMillis(), AlarmManager.INTERVAL_DAY, pendingResetIntent);
		    
	        Log.d(TAG, "AlarmManager registered, to reset the maximum number of queries at the end of the day");
	        
	        Intent persistIntent = new Intent(Globals.PERSIST_DATA);
	        PendingIntent pendingPersistIntent = PendingIntent.getBroadcast(context, 0, persistIntent, 0);

	        Calendar currentCal = Calendar.getInstance();
	        alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, currentCal.getTimeInMillis(), Globals.PERSIST_PERIOD, pendingPersistIntent); //change to 10min or so
		    
	        Log.d(TAG, "AlarmManager registered, to reset the maximum number of queries at the end of the day");

		} 
		
		if (intent.getAction().equals(Globals.RESET_MAX_QUERY_NUMBER)) {

			resetQueriesLeft(context);
			
		}
		
		if (intent.getAction().equals(Globals.PERSIST_DATA)) {

			if (variablesInitialized == true) {
				persistData();
			}

		}
		
		if (intent.getAction().equals(Globals.MAX_QUERY_NUMBER_CHANGED)) {

			maxQueryNumberChanged(context);
			
		}
		
		if (intent.getAction().equals(Globals.REQUEST_CLASS_NAMES)) {

			classNamesRequested = true;
			
		}
		
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
		
		public void onModelAdaptionCompleted(GMM newGMM) {
			
			Log.i(TAG, "Model adation completed");

//			Toast.makeText(context,
//					(String) "Model adaption completed", Toast.LENGTH_SHORT)
//					.show(); //TODO
		}
	};
	
	private void callModelAdaption(Context context, int label) {

		// Log the AL feedback:
		if (waitingForFeedback == true) {
			appendToALLog(true, label, currentPrediction, false, false);
		} else {
			appendToALLog(true, label, currentPrediction, false, true);
		}
		
		// Find index of the conversation class, as we do not want to adapt our model for these:
		int conversationIdx = -1;
		for(int i=0; i<gmm.get_n_classes(); i++) {
			if (gmm.get_class_name(i).equals("Conversation")) {
				conversationIdx = i;
			}
		}
		
		if (label != conversationIdx) {
			
			Log.i(TAG, "Model adaption called for class " + String.valueOf(label));
			
			waitingForFeedback = false;
			
			new ModelAdaptor(buffer, label, listener).execute();
			
			// Clear all buffer values etc.
				
			feedbackReceived.set(label, true);
			
			// Calculate first part of the new threshold
			thresQueriedInterval.set(label, tmpQueryCrit);
			
			// Flush the buffers
			queryBuffer.clear();
			predBuffer.clear();
			for(int i=0; i<gmm.get_n_classes(); i++) {
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
					Toast.LENGTH_SHORT).show();
			
		} else {
			Log.i(TAG, "Conversation class will not be incorporated into our model");
		}
	}
	
	/*
	 * Go to the context selection activity
	 */
	public void callContextSelectionActivity(Context context) {		
		
		Intent i=null;
		
		if (gmm != null) {
			if (gmm.get_string_array().length > 0) {
				i = new Intent(context, ContextSelection.class);
				Bundle b = new Bundle();
				b.putStringArray(Globals.CLASS_NAMES, gmm.get_string_array());
				i.putExtras(b);
				i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); // start activity from outside activity
				context.startActivity(i);
			}
			
		} else {
			// We cannot start ContextSelection activitiy if class names not yet available
			i = null;

			Log.w(TAG,
					"Not changing to ContextSelection activity, as class names not available yet.");
		}
	}
	
	/*
	 * Only return intent to the context selection activity (for pending intent of  the notification)
	 */
	public Intent getContextSelectionActivity(Context context) {		
		
		Intent i=null;
		
		if (gmm != null) {
			if (gmm.get_string_array().length > 0) {
				i = new Intent(context, ContextSelection.class);
				Bundle b = new Bundle();
				b.putStringArray(Globals.CLASS_NAMES, gmm.get_string_array());
				i.putExtras(b);
				i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); // start activity from outside activity
			}
			
		} else {
			// We cannot start ContextSelection activity if class names not yet available
			i = null;

			Log.w(TAG,
					"Not changing to ContextSelection activity, as class names not available yet.");
		}
		
		return i;
	}
	
	private void requestNewClassFromServer(Context context, String newClassName) {
	
		Log.i(TAG, "Requesting new context class " + newClassName
				+ " from server");
		
		waitingForFeedback = false;

		IncorporateNewClass postReq = new IncorporateNewClass();
		//String filenameOnServer = null;
		
		try {

			String filenameOnServer = postReq.execute(newClassName).get();

			// Now check periodically if the computation on server is finished
			TimerTaskGet task = new TimerTaskGet(context, filenameOnServer) {
				
				private int counter;
				
				public void run() {
		        	
		        	GetNewModel getReq = new GetNewModel();
					Boolean res = false;
					
					try {
						
						res = getReq.execute(filenameOnServer).get();
						
					} catch (InterruptedException e) {
						e.printStackTrace();
					} catch (ExecutionException e) {
						e.printStackTrace();
					}
					
					if (res == true) {
						
						// Model received from the server:
						Log.i(TAG, "New classifier received from server");
						onNewClassIncorporated(context);
						this.cancel();

					}
					
					if(++counter == Globals.MAX_RETRY_NEW_CLASS) {
						Log.e(TAG, "Server timeout, model adaption failed, as server didn't provide new classifier"
								+ "with the specified time limit");
						this.cancel();
					}


					Log.i(TAG, "Waiting for new classifier from server");
		        }
			};
			
		    Timer timer = new Timer();
	        timer.schedule(task, 0, Globals.POLLING_INTERVAL_NEW_CLASS);
		  

		} catch (InterruptedException e) {

			e.printStackTrace();
		} catch (ExecutionException e) {

			e.printStackTrace();
		}
		
		/*
		 *  If the feedback is a response to a query the system sent out, clear all buffer values
		 *  immediately (even before we actually incorporate the new class into the model):
		 */
		// Flush the buffers...
		queryBuffer.clear();
		predBuffer.clear();
		for (int i = 0; i < gmm.get_n_classes(); i++) {
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
	}
	
	private void sendQuery(Context context) {

		long[] vibratePattern = {0, 500}; // Start with 0 delay and vibrate for 500ms
		
		// To cancel the query		
		Intent dismissIntent = new Intent(Globals.DISMISS_NOTIFICATION);
		PendingIntent dismiss = PendingIntent.getBroadcast(context, 0, dismissIntent, 0);
		
		NotificationCompat.Builder builder = new NotificationCompat.Builder(
				context).setSmallIcon(R.drawable.ic_launcher)
				.setContentTitle("What's your current context?")
				.setAutoCancel(true)
				.setWhen(System.currentTimeMillis())
				.setTicker("What's your current context?")
				.setVibrate(vibratePattern)
				.addAction(R.drawable.ic_stat_dismiss, "Dismiss", dismiss);
		
		Intent notificationIntent = getContextSelectionActivity(context);
		if (notificationIntent == null) {
			Log.e(TAG, "Notification intent could not be initialized, as callContextSelectionActivity returned null");
			return;
		}

		
		PendingIntent contentIntent = PendingIntent.getActivity(context, 0,
				notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
		builder.setContentIntent(contentIntent);


		NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
		manager.notify(NOTIFICATION_ID, builder.build());
		
		waitingForFeedback = true;
		timeQuerySent = System.currentTimeMillis();
		
		// Start TimerTask:
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
		notificationManager.cancel(NOTIFICATION_ID);
	}
	
	private void appendToALLog(boolean feedbackReceived, int contextClassGT, 
			int contextClassPredicted, boolean isNewClass, boolean voluntaryFeedback) {
		
		Log.i(TAG, "Appending to AL log");
		
		String classNameGT = null;
		String classNamePredicted = gmm.get_class_name(contextClassPredicted);
		String timePassed = null;
		String isNewClassString = null;
		String volFeedbackString = null;
		
		if (feedbackReceived == true) {
			classNameGT = gmm.get_class_name(contextClassGT);
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

		try {
			FileWriter f = new FileWriter(Globals.AL_LOG_FILE, true);
			f.write(System.currentTimeMillis() + "\t" + classNamePredicted + "\t" +  
			classNameGT + "\t" + timePassed + "\t" + isNewClassString + "\t" + volFeedbackString + "\n");
			f.close();
		} catch (IOException e) {
			Log.e(TAG, "Writing to AL log file failed");
			e.printStackTrace();
		}
	}
	
	private void appendToPredLog(String className) {
		
		Log.d(TAG, "Appending to prediction log file");
		
		try {
			FileWriter f = new FileWriter(Globals.PRED_LOG_FILE, true);
			f.write(System.currentTimeMillis() + "\t" + className + "\n");
			f.close();
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
	 * When a new context class was added to the model, we have to incorporate the new class into
	 * the threshold buffers, class names, ...
	 */
	private void onNewClassIncorporated(Context context) {
		
		// Create entry for the new class at all lists used for the threshold computation:
		initThresSet.add(false);
		thresSet.add(false);
		feedbackReceived.add(false);
		threshold.add(-1.0);
		initThresBuffer.add(new ArrayList<Double>());
		thresBuffer.add(new ArrayList<Double>());
		thresQueriedInterval.add(-1.0);
		
		// Save String array of the context classes to preferences:
		Globals.setStringArrayPref(context, Globals.CONTEXT_CLASSES, gmm.get_string_array());
		
		Intent i = new Intent(Globals.CLASS_NAMES_SET);
		context.sendBroadcast(i);		
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
	private void persistData() {
		
		AppData appData = new AppData(initThresSet, thresSet, feedbackReceived, 
				threshold, initThresBuffer, thresBuffer, thresQueriedInterval, numQueriesLeft);
		
		String str = new Gson().toJson(appData);
		
		if(!str.equals("{}")) { //Only write to file, if it is not empty
			FileOutputStream f = null;
			try {
				f = new FileOutputStream(Globals.APP_DATA_FILE);
			} catch (FileNotFoundException e) {
				Log.e(TAG, "App data file to store JSON not found");
				e.printStackTrace();
			}
			try {
				Log.d(TAG, "App data persited to JSON file");
				f.write(str.getBytes());
				f.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}	
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
	
}
