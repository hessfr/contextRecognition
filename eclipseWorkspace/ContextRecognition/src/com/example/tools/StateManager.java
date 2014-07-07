package com.example.tools;

import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.math3.stat.descriptive.moment.Mean;
import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

import android.app.Activity;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.example.contextrecognition.ContextSelection;
import com.example.contextrecognition.R;
import com.example.tools.ModelAdaptor.onModelAdaptionCompleted;

/*
 * Handles all broadcasts and holds all prediction varaiables like current context, buffers, class names, ...
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
	private static int PRED_BUFFER_SIZE = 30;
	
	// Mean entropy values (on 2sec window) of the last min:
	private static ArrayList<Double> queryBuffer;
	private static int QUERY_BUFFER_SIZE = 30;
	
	// Store entropy values to set threshold for the first time. Separate for different classes:
	private static ArrayList<ArrayList<Double>> initThresBuffer;
	private static int INIT_THRES_BUFFER_SIZE = 30; //TODO: change back to 90 xxxxxxxxxxxxxxxxxxxxxx
	
	// Store entropy values to set threshold after the init model adaption is done. Separate for different classes:
	private static ArrayList<ArrayList<Double>> thresBuffer;
	private static int THRES_BUFFER_SIZE = 30; //TODO: change back to 300 xxxxxxxxxxxxxxxx
	
	// Value computed on the points where query was sent. Used to calculate the new query criteria:
	private static ArrayList<Double> thresQueriedInterval;

	/*
	 * This is the buffer of feature points be use to adapt the model. Called "updatePoints" in Python.
	 * The update of this buffer is completely done in the AudioWorker
	 */
	private static ArrayList<double[]> buffer;
	
	// Count the number of queries for each context class
	private static ArrayList<Integer> numQueriesAnswered;
	
	// Count the number of queries for each context class
	private static ArrayList<Integer> numQueriesIgnored; //TODO
	
	// Count the number of of voluntary feedback for each context class. For evaluation only
	private static ArrayList<Integer> volFeedback;
	
	// Minimum time we has to wait between two queries:
	private static long minBreak = 100000; //TODO: change back to 600000
	
	// Apache Commons methods to calculate means and standard deviations:
	StandardDeviation stdCalc = new StandardDeviation();
	Mean meanCalc = new Mean();
	
	// When we wait for user feedback, don't change the buffer:
	private static boolean waitingForFeedback = false;
	
	// Value contributing to the query criteria that is computed on the interval where the query was sent.
	private static double tmpQueryCrit;
	
	// -------------------------------------------------------------
	
	private static boolean testBool = false; // for testing only
	
	// Send from AudioWorker:
	public static final String PREDICTION_INTENT = "action.prediction";
	public static final String PREDICTION_INT = "predictionInt";
	public static final String PREDICTION_ENTROPY = "predictionEntropy";
	public static final String PREDICTION_STRING = "predictionString";
	public static final String CLASSES_DICT = "classesDict";
	public static final String RESULTCODE = "resultcode";
	
	public static final String STATUS_INTENT = "action.status";
	public static final String CLASS_STRINGS = "classesStrings";
	public static final String GMM_OBJECT = "gmmObject";
	public static final String BUFFER_STATUS = "bufferStatus";
	public static final String BUFFER = "buffer";
	

	// Received by the state manager:
	public static final String MODEL_ADAPTION_EXISTING_INTENT = "action.modelAdaptionExisting";
	public static final String LABEL = "label";
	
	public static final String CLASS_NAMES = "classNames";
	
	public static final String MODEL_ADAPTION_NEW_INTENT = "action.modelAdaptionNew";
	public static final String NEW_CLASS_NAME = "newClassName";
	
	public static final String MODEL_ADAPTION_FINISHED_INTENT = "action.modelAdaptionFinished";
	public static final String CALL_CONTEXT_SELECTION_INTENT = "action.callContextSelection";
	
	public static final String DISMISS_NOTIFICATION = "action.dismissNotification";
	
	
	// Send by the StateManager:
	public static final String PREDICTION_CHANGED_INTENT = "predictionChangedIntent";
	public static final String NEW_PREDICTION_STRING = "newPredictionString";
	
	public static final int NOTIFICATION_ID = 1;
	
	// Constructor:
//	public StateManager() {
//		Log.i(TAG, "constructor");
//	}
	
	@Override
	public void onReceive(Context context, Intent intent) {
		
		Bundle bundle = intent.getExtras();

		if (bundle != null) {

			if (intent.getAction().equals(PREDICTION_INTENT)) {

				int resultCode = bundle.getInt(RESULTCODE);

				if (resultCode == Activity.RESULT_OK) {
					
					// The following lines have to be in exactly the same order as the were put on the bundle (in the AudioWorker):

					currentPrediction = bundle.getInt(PREDICTION_INT);
					currentEntropy = bundle.getDouble(PREDICTION_ENTROPY);
					predictionString = bundle.getString(PREDICTION_STRING);
					
					classNameArray = bundle.getStringArray(CLASS_STRINGS);
//					Log.i(TAG, classNameArray[0]);
					
					bufferStatus = bundle
							.getBoolean(BUFFER_STATUS);
//					Log.i(TAG, String.valueOf(bufferStatus));
					
					Serializable s1 = bundle.getSerializable(BUFFER);
					if (waitingForFeedback == false) {
						buffer = (ArrayList<double[]>) s1;
					}
						
//					Log.i(TAG, String.valueOf(buffer.get(0)[0]));

					gmm = bundle.getParcelable(GMM_OBJECT); // Needed??
//					Log.i(TAG, gmm.get_class_name(0));

					Serializable s2 = new HashMap<String, Integer>();
					s2 = bundle.getSerializable(CLASSES_DICT);
//					classesDict = (HashMap<String, Integer>) s2;
					
					if (testBool == false) {
						testBool = true;
						requestNewClassFromServer("Restaurant");
					}
					
					Log.i(TAG, "Current Prediction: " + predictionString + ": " + currentPrediction);
					
					
					//=================================================================================
					//============ Handle sending of query, threshold calculations, ... ===============
					//=================================================================================
					
					// Initialize the variable when receiving the first set of data:
					if(variablesInitialized == false) {
						initThresSet = new ArrayList<Boolean>();
						thresSet = new ArrayList<Boolean>();
						feedbackReceived = new ArrayList<Boolean>();
						threshold = new ArrayList<Double>();
						numQueriesAnswered = new ArrayList<Integer>();
						numQueriesIgnored = new ArrayList<Integer>();
						volFeedback = new ArrayList<Integer>();
						
						initThresBuffer = new ArrayList<ArrayList<Double>>();
						thresBuffer = new ArrayList<ArrayList<Double>>();
						
						thresQueriedInterval = new ArrayList<Double>();
						
						queryBuffer = new ArrayList<Double>();
						predBuffer = new ArrayList<Integer>();
	
						for(int i=0; i<gmm.get_n_classes(); i++) {
							
							initThresSet.add(false);
							thresSet.add(false);
							feedbackReceived.add(false);
							threshold.add(-1.0);
							numQueriesAnswered.add(0);
							numQueriesIgnored.add(0);
							volFeedback.add(0);
							
							initThresBuffer.add(new ArrayList<Double>());
							thresBuffer.add(new ArrayList<Double>());
							thresQueriedInterval.add(-1.0);
						}
						
						variablesInitialized = true;
					}

					// For each class buffer the last (30) entropy values
					if (queryBuffer.size() < QUERY_BUFFER_SIZE) {
						
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
						if (initThresBuffer.get(currentPrediction).size() < INIT_THRES_BUFFER_SIZE) {
							// Fill the buffer for the predicted class first:
							ArrayList<Double> tmpList = initThresBuffer.get(currentPrediction);
							tmpList.add(currentEntropy);
							initThresBuffer.set(currentPrediction, tmpList);
							Log.i(TAG, "initThresBuffer length: " + initThresBuffer.get(currentPrediction).size());
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
						
						if (thresBuffer.get(currentPrediction).size() < THRES_BUFFER_SIZE) {
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
							(queryBuffer.size() == QUERY_BUFFER_SIZE)) {
						
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
						for (int i=0; i<PRED_BUFFER_SIZE; i++) {
							if (predBuffer.get(i) == mostFreq) {
								majElements.add(queryBuffer.get(i));
							}
						}
						
						// Use mean entropy value of the last last 30 2sec windows as query criteria
						Double[] ms = majElements.toArray(new Double[majElements.size()]);							
						double[] m = ArrayUtils.toPrimitive(ms);
						double queryCrit = meanCalc.evaluate(m);
						double std = stdCalc.evaluate(m);
						
						Log.i(TAG,"Time since last feedback: " + (System.currentTimeMillis() - prevTime));
						Log.i(TAG,"thresSet: " + thresSet.get(currentPrediction) + " - feedback received: " + feedbackReceived.get(currentPrediction));
						
						if ((System.currentTimeMillis() - prevTime) > minBreak) {
							
							//if (queryCrit > threshold.get(currentPrediction)) { //TODO: xxxxxxxxxx
								
							if (queryCrit > 0 && waitingForFeedback == false) {
								
								Log.i(TAG, "Threshold exceeded, user queried for current context");

								sendQuery(context);

								prevTime = System.currentTimeMillis();
								
								/*
								 * Contributing to the query criteria that is computed on the interval where the query was sent.
								 * We can only assign this value to the correct class, once we received the ground truth from
								 * the user:
								 */
								tmpQueryCrit = metricBeforeFeedback(queryCrit, std); // queryCrit is just the mean
								
								waitingForFeedback = true;

								/*
								 * The model adaption is handled in the callModelAdaption, that is always being called
								 * from the ContextSelection Activity
								 */
								
							}

						}
						
					}
					
					//=================================================================================
					//=================================================================================
					//=================================================================================

					
					// Send broadcast to change text, if prediction has changed
					if (!predictionString.equals(prevPredictionString)) {
						
						Intent i = new Intent(StateManager.PREDICTION_CHANGED_INTENT);
						Bundle b = new Bundle();
						b.putString(NEW_PREDICTION_STRING, predictionString);
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
		
			else if (intent.getAction().equals(MODEL_ADAPTION_EXISTING_INTENT)) {
				
				int label = bundle.getInt(LABEL);
				callModelAdaption(label);

			} else if (intent.getAction().equals(
					MODEL_ADAPTION_FINISHED_INTENT)) {

//				Toast.makeText(getBaseContext(),
//						(String) "Model adaptation finished",
//						Toast.LENGTH_SHORT).show();

			} else if (intent.getAction().equals(MODEL_ADAPTION_NEW_INTENT)) {
				
				String newClassName = bundle.getString(NEW_CLASS_NAME);

				requestNewClassFromServer(newClassName);
				
			} else if (intent.getAction().equals(CALL_CONTEXT_SELECTION_INTENT)) {
				
				callContextSelectionActivity(context);
				
			}

		}
		
		if (intent.getAction().equals(DISMISS_NOTIFICATION)) {
			
			dismissNotifitcation(context);
			
			Log.i(TAG, "Query dismissed");
			
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

//			Toast.makeText(getBaseContext(),
//					(String) "Model adaption completed", Toast.LENGTH_SHORT)
//					.show(); //TODO
			
		}
	};
	
	private void callModelAdaption(int label) {

		Log.i(TAG, "Model adaption called for class " + String.valueOf(label));	

		new ModelAdaptor(buffer, label, listener).execute();
		
		// If the feedback is a response to a query the system sent out, clear all buffer values etc.
		if (waitingForFeedback == true) {
			
			// Update the number of queries (for evaluation only)
			numQueriesAnswered.set(label, (numQueriesAnswered.get(label)+1));
			
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
			
			waitingForFeedback = false;
			
		} else {
			
			//TODO: do we have to flush all buffers as well here ???
			
			// If the feedback was given voluntarily (without a query from the system):
			volFeedback.set(label, (volFeedback.get(label)+1));
			
		}
		
		
				
//		Toast.makeText(getBaseContext(), (String) "Model is being adapted",
//				Toast.LENGTH_SHORT).show();

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
				b.putStringArray(CLASS_NAMES, gmm.get_string_array());
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
				b.putStringArray(CLASS_NAMES, gmm.get_string_array());
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
	
	private void requestNewClassFromServer(String newClassName) {

		Log.i(TAG, "Requesting new context class " + newClassName
				+ " from server");

		PostRequest postReq = new PostRequest();
		//String filenameOnServer = null;
		
		try {

			String filenameOnServer = postReq.execute(newClassName).get();

//			// Remove quotation marks:
//			filenameOnServer = filenameOnServer.substring(1, filenameOnServer.length()-1);
			Log.i(TAG, "xxxxxxxxxxxxxx Filename on server: " + filenameOnServer);

			GetRequest getReq = new GetRequest();
			String tmp = getReq.execute(filenameOnServer).get();			
	        
			// Now check periodically if the computation on server is finished
//		    Timer t = new Timer();
//		    t.schedule(new TimerTask() {
//
//		        public void run() {
//
//
//		            //Your code will be here 
//
//		        }
//		      }, 1000);

		} catch (InterruptedException e) {

			e.printStackTrace();
		} catch (ExecutionException e) {

			e.printStackTrace();
		}
		
		// If the feedback is a response to a query the system sent out, clear all buffer values etc. first
		if (waitingForFeedback == true) {

			// Flush the buffers
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
			
			
			//TODO: xxxxxxxxxx
			
			waitingForFeedback = false;

		} else {
			
			//TODO: xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
			
			// Update the number of queries (for evaluation only)
//			numQueries.set(label, (numQueries.get(label) + 1));
//
//			feedbackReceived.set(label, true);
//
//			// Calculate first part of the new threshold
//			thresQueriedInterval.set(label, tmpQueryCrit);

			
		}
		
		
		
		
		//TODO: increase all the ArrayLists etc. in this class!!!
		
		
		
		
		
		
		

	}
	
	private void sendQuery(Context context) {

		long[] vibratePattern = {0, 500}; // Start with 0 delay and vibrate for 500ms
		
		// To cancel the query		
		Intent dismissIntent = new Intent(DISMISS_NOTIFICATION);
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

	}
	
	private void dismissNotifitcation (Context context) {

		NotificationManager notificationManager = (NotificationManager) context
	            .getSystemService(Context.NOTIFICATION_SERVICE);
		notificationManager.cancel(NOTIFICATION_ID);
	}
	
}
