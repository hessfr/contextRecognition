package com.example.tools;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import com.example.contextrecognition.ContextSelection;
import com.example.contextrecognition.MainActivity;
import com.example.contextrecognition.R;
import com.example.tools.ModelAdaptor.onModelAdaptionCompleted;

import android.app.Activity;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

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
	private static int currentPrediction;
	private static double currentEntropy;
	private static String predictionString;
	private static String prevPredictionString = "";
	public static Map<String, Integer> classesDict = new HashMap<String, Integer>(); // Needed??
	private static String[] classNameArray;
	private static boolean bufferStatus;
	private static ArrayList<double[]> buffer;
	private static GMM gmm; // Needed??
	
	
	private static boolean querySendTemp = false; // for testing only
	
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
					buffer = (ArrayList<double[]>) s1;
//					Log.i(TAG, String.valueOf(buffer.get(0)[0]));

					gmm = bundle.getParcelable(GMM_OBJECT); // Needed??
//					Log.i(TAG, gmm.get_class_name(0));

					Serializable s2 = new HashMap<String, Integer>();
					s2 = bundle.getSerializable(CLASSES_DICT);
//					classesDict = (HashMap<String, Integer>) s2;
					
					if (querySendTemp == false) {
						sendQuery(context);
						querySendTemp = true;
					}
					
					Log.i(TAG, "Current Prediction: " + predictionString + ": " + currentPrediction);
					
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
		
		new ModelAdaptor(buffer, label, listener).execute(); // used to be .execute(this)
				
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
