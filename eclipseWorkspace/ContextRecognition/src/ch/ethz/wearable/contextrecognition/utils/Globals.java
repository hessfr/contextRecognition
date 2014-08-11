package ch.ethz.wearable.contextrecognition.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.json.JSONArray;
import org.json.JSONException;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.preference.PreferenceManager;

public class Globals {
	
	public static final String APP_FOLDER = "ContextRecognition";
	public static final File APP_PATH = new File(Environment.getExternalStorageDirectory().getAbsolutePath()
			+ "/" + APP_FOLDER);
	
	// Get the file to the current (today's) log folder
	@SuppressLint("SimpleDateFormat")
	public static File getLogPath() {
		Calendar cal = Calendar.getInstance();
		Date currentLocalTime = cal.getTime();
		DateFormat date = new SimpleDateFormat("yyyMMdd");
		String dateString = date.format(currentLocalTime);
		File f = new File(APP_PATH, "/Logs_" + dateString);
		
		if (!f.exists()) {
		    f.mkdir();
		}
		
		return f;
	}
	
	// Get the file to yesterday's log folder
	@SuppressLint("SimpleDateFormat")
	public static File getLogPathYesterday() {
		Calendar cal = Calendar.getInstance();
		cal.add(Calendar.DATE, -1);
		Date currentLocalTime = cal.getTime();
		DateFormat date = new SimpleDateFormat("yyyMMdd");
		String dateString = date.format(currentLocalTime);
		File f = new File(APP_PATH, "/Logs_" + dateString);
		
		return f;
	}
	
	public static final String START_STOP_LOG_FILENAME = "START_STOP_Log.txt";
	
	public static final String AL_LOG_FILENAME = "AL_Log.txt";
	
	public static final String GT_LOG_FILENAME = "GT_Log.txt";
	
	public static final String PRED_LOG_FILENAME = "PRED_Log.txt";
	
	public static final String MAX_QUERY_LOG_FILENAME = "MAX_QUERY_Log.txt";
	
	public static final String AUDIO_FILENAME = "rawAudio";
	
	public static final File APP_DATA_FILE = new File(APP_PATH, "AppData.json");
	
	public static final File HISTORIC_PREDICTIONS_FILE = new File(APP_PATH, "HistoricPredictions.json");
	
	public static long RECORDING_START_TIME = 0;
	
	public static int NUM_FEATURES = 13; // 12 features if only MFCCs, 13 if MFCCs & log energy
	
	/*
	 * App settings
	 */
	//Time after which the query is cancelled and the notification removed (in ms):
	public static long CANCEL_QUERY_TIME = 60000; // = 1min
	
	//Time after which the phone should vibrate a second time, after sending the query (in ms):
	public static long QUERY_VIBRATE_AGAIN_TIME = 30000; // = 30s
	
	// Vibrate time (in ms):
	public static long VIBRATE_TIME = 500;

	//Default value for the max number of queries per day:
	public static int MAX_QUERIES_PER_DAY = 10;
	
	// Minimum time we has to wait between two queries:
	public static long minBreak = 10 * 60 * 1000;
	
	// Time after which the data (buffers, threshold, ...) should be periodically persisted to external storage
	public static long PERSIST_PERIOD = 10 * 60 * 1000; // = 10min
	
	// Silence detection threshold (higher means more samples will be considered silent):
	public static short SILENCE_DETECTION_THRESHOLD = 1200;
	
	/*
	 *  Array of context classes that can be selected initially: 
	 *  (these classes have to match the classes of the default 
	 *  model that is deployed in the assets folder)
	 */
	public static String[] initialContextClasses = {"Office", "Conversation", "Restaurant",
		"Car", "Bus", "Train", "Street", "Vacuum", "Toilet", "Kitchen"};
	// Array indicating if the context class should be selected initially:
	public static Boolean[] defaultClasses = {true, true, true, true, true,
		true, true, true, true, true};
	
	/*
	 * Buffer lengths for the threshold calculation
	 */
	// To calculate maj vote on the last minute of  data and only incorporate those points matching the majority vote:
	public static int PRED_BUFFER_SIZE = 30;
	
	// Mean entropy values (on 2sec window) of the last min:
	public static int QUERY_BUFFER_SIZE = 30;
	
	// Store entropy values to set threshold for the first time. Separate for different classes:
	public static int INIT_THRES_BUFFER_SIZE = 90; // decrease this for testing...
	
	// Store entropy values to set threshold after the init model adaption is done. Separate for different classes:
	public static int THRES_BUFFER_SIZE = 300; // decrease this for testing...
	
	public static String MAX_NUM_QUERIES = "maxNumQueries";
	public static String PREV_MAX_NUM_QUERIES = "PrevMaxNumQueries";

	/*
	 * Client-server interaction
	 */
	public static final String IP = "129.132.131.71";
	
	public static final String PORT = "8080";
	public static final String BASE_URL = "http://" + IP + ":" + PORT + "/";
	public static final String ADD_CLASS_URL = BASE_URL + "addclass/?";
	public static final String GET_KNOWN_CLASSES_URL = BASE_URL + "getknownclasses/?";
	public static final String FEASIBILITY_CHECK_URL = BASE_URL + "feasibilitycheck/?";
	public static final String RAW_AUDIO_URL = BASE_URL + "rawaudio/?";
	public static final String INIT_CLASSIFIER_URL = BASE_URL + "initclassifier/?";
	public static final String MANAGE_CLASSES_URL = BASE_URL + "manageclasses/?";
	
	public static final long POLLING_INTERVAL_NEW_CLASS_ALREADY_DOWNLOADED = 10 * 60 * 1000; // 2 * 60 * 1000 = 2min
	public static final long POLLING_INTERVAL_NEW_CLASS_NOT_DOWNLOADED = 15 * 60 * 1000; // 15 * 60 * 1000 = 15min
	public static final long MAX_RETRY_NEW_CLASS_ALREADY_DOWNLOADED = 30; // 30 * 2min -> 1h
	public static final long MAX_RETRY_NEW_CLASS_NOT_DOWNLOADED = 12; // 12 * 15min -> 3h
	
	public static final long POLLING_INTERVAL_INITIAL_MODEL = 10 * 60 * 1000; // 10 * 60 * 1000 = 10min
	public static final long MAX_RETRY_INITIAL_MODEL = 10; // 10 * 10min = 2h
	
	public static final long POLLING_INTERVAL_UPLOAD = 1 * 3600 * 1000; // 1h
	public static final long MAX_RETRY_UPLOAD = 5;
	
	// Values used if server is not responding:
	public static final long POLLING_INTERVAL_DEFAULT = 3 * 60 * 1000; // 3 * 60 * 1000 = 3min
	public static final long MAX_RETRY_DEFAULT = 15; // 15 * 3min = 75min
	
	// IntentServices for communication:
	public static final String CONN_CHECK_FEASIBILITY_RECEIVE = "action.connCheckFeasibilityReceive";
	public static final String CONN_CHECK_FEASIBILITY_CLASS_NAME = "connCheckFeasibilityClassName";
	public static final String CONN_CHECK_FEASIBILITY_RESULT = "connCheckFeasibilityResult";
	
	public static final String CONN_INCORPORATE_NEW_CLASS_RECEIVE = "action.connIncorporateNewClassReceive";
	public static final String CONN_INCORPORATE_NEW_CLASS_NAME = "connIncorporateNewClassName";
	public static final String CONN_INCORPORATE_NEW_CLASS_FILENAME = "connIncorporateNewClassFilename";
	
	public static final String CONN_UPDATED_MODEL_RECEIVE = "action.connUpdatedModelReceive";
	public static final String CONN_UPDATED_MODEL_FILENAME = "action.connUpdatedModelFilename";
	public static final String CONN_UPDATED_MODEL_PREV_CLASSNAMES = "connUpdatedModelPrevClassnames";
	public static final String CONN_UPDATED_MODEL_RESULT = "action.connUpdatedModelResult";
	
	public static final String CONN_SEND_RAW_AUDIO_RECEIVE = "action.connSendRawAudioReceive";
	public static final String CONN_SEND_RAW_AUDIO_RESULT = "action.connSendRawAudioResult";
	
	public static final String CONN_INIT_MODEL_RECEIVE = "action.connSendInitModelReceive";
	public static final String CONN_INIT_MODEL_CLASSES = "connSendInitModelClasses";
	public static final String CONN_INIT_MODEL_INVALIDS = "connSendInitModelInvalids";
	public static final String CONN_INIT_MODEL_RESULT_FILENAME = "connSendInitModelResultFilename";
	public static final String CONN_INIT_MODEL_RESULT_WAIT = "connSendInitModelResultWait";
	
	public static final String CONN_INIT_MODEL_GET_FILENAME = "connSendInitModelGetFilename";
	public static final String CONN_INIT_MODEL_GET_WAIT = "connSendInitModelGetWait";
	
	public static final String CONN_INIT_MODEL_FINISH = "action.connSendInitModelFinish";
	public static final String CONN_INIT_MODEL_PREV_CLASSNAMES = "connSendInitModelPrevClassnames";
	
	public static final String CONN_MANAGE_CLASSES_RECEIVE = "action.connManageClassesReceive";
	public static final String CONN_MANAGE_CLASSES_INVALIDS = "connManageClassesInvalids";	
	public static final String CONN_MANAGE_CLASSES_FILENAME = "connManageClassesFilename";
	public static final String CONN_MANAGE_CLASSES_WAIT = "connManageClassesWait";
	public static final String CONN_MANAGE_CLASSES_ARRAY = "connManageClassesArray";
	
	public static final String CONN_MANAGE_CLASSES_FINISH = "action.connManageClassesFinish";
	public static final String CONN_MANAGE_CLASSES_PREV_CLASSNAMES = "action.connManageClassesPrevClassnames";
	
	public static final String CONN_MANAGE_CLASSES_GET_FILENAME = "connManageClassesTimerFilename";
	public static final String CONN_MANAGE_CLASSES_GET_WAIT = "connManageClassesTimerWait";
	
	// Results of the feasibility check (String have to match results from server):
	public static final String FEASIBILITY_DOWNLOADED = "downloaded";
	public static final String FEASIBILITY_FEASIBLE = "feasible";
	public static final String FEASIBILITY_NOT_FEASIBLE = "not_feasible";
	
	// Results of the initClassifier (String have to match results from server):
	public static final String WAIT = "wait";
	public static final String NO_WAIT = "no_wait";

	/*
	 * Broadcast intent and extra names:
	 */
	// Send from AudioWorker:
	public static final String PREDICTION_INTENT = "action.prediction";
	public static final String PREDICTION_INT = "predictionInt";
	public static final String PREDICTION_ENTROPY = "predictionEntropy";
	public static final String PREDICTION_STRING = "predictionString";
	public static final String CLASSES_DICT = "classesDict";
	public static final String RESULTCODE = "resultcode";
	public static final String SILENCE = "silence";
	public static final String FIRST_PRED_AFTER_MODEL_CHANGE = "firstPredAfterModelChange";
	public static final String SILENCE_PREDICTED = "action.silencePredicted";
	
	public static final String STATUS_INTENT = "action.status";
	public static final String CLASS_STRINGS = "classesStrings";
	public static final String GMM_OBJECT = "gmmObject";
	public static final String BUFFER_STATUS = "bufferStatus";
	public static final String BUFFER = "buffer";
	

	// Received by the StateManager:
	public static final String MODEL_ADAPTION_EXISTING_INTENT = "action.modelAdaptionExisting";
	public static final String LABEL = "label";
	
	public static final String CLASS_NAMES = "classNames";
	
	public static final String MODEL_ADAPTION_NEW_INTENT = "action.modelAdaptionNew";
	public static final String NEW_CLASS_NAME = "newClassName";
	
	public static final String MODEL_ADAPTION_FINISHED_INTENT = "action.modelAdaptionFinished";
	public static final String CALL_CONTEXT_SELECTION_INTENT = "action.callContextSelection";
	public static final String CALL_MANAGE_CLASSES_INTENT = "action.callManageClasses";
	
	public static final String DISMISS_NOTIFICATION = "action.dismissNotification";
	
	public static final String REGISTER_RECURRING_TASKS = "action.registerRecurringTasks";
	
	public static final String END_OF_DAY_TASKS = "action.endOfDayTasks";
	
	public static final String PERSIST_DATA = "action.persistData";
	
	public static final String MAX_QUERY_NUMBER_CHANGED = "action.maxQueryNumberChanged";
	
	// Send by the StateManager:
	public static final String PREDICTION_CHANGED_INTENT = "predictionChangedIntent";
	public static final String NEW_PREDICTION_STRING = "newPredictionString";

	public static final String REQUEST_CLASS_NAMES = "action.requestClassNames";
	
	public static final String CLASS_NAMES_SET = "classNamesSet";
	
	public static final String SILENCE_PREDICTED_CHANGE_UI = "action.silencePredictedUI";
	
	// Only for debugging: show current entropy value in MainActivity:
	public static final String PREDICTION_ENTROPY_INTENT = "predictionEntropyIntent";
	public static final String PREDICTION_ENTROPY_VALUE = "predictionEntropyValue";
	
	// Send from communication IntentServices:
	public static final String CLASSES_BEING_ADDED_INTENT = "classesBeingAddedIntent";
	
	
	// Define if we wait for server response when calling MainActivity from welcome activity:
	public static final String WAIT_FOR_SERVER = "waitForServer";	
	
	// IDs for different notifications:
	public static final int NOTIFICATION_ID_FILE_TRANSFER = 1;
	public static final int NOTIFICATION_ID_QUERY = 2;
	public static final int NOTIFICATION_ID_CLASSES_NOT_ADDED = 3;
	public static final int NOTIFICATION_ID_STANDARD = 4;
	
	// Preferences:
	public static final String USER_ID = "userId";
	public static final String CURRENT_CONTEXT = "currentContext";
	public static final String CONTEXT_CLASSES = "contextClasses";
	public static final String CONTEXT_CLASSES_SERVER = "contextClassesServer";
	public static final String CLASS_COUNTS = "classCounts";
	public static final String SILENCE_COUNTS = "silenceCounts";
	public static final String LASTEST_RECORDING_TIMESTAMP = "lastestRecordingTimestamp";
	public static final String CLASSES_BEING_ADDED = "classesBeingAdded";
	public static final String CLASSES_BEING_REMOVED = "classesBeingRemoved";
	// For persisting historical predictions:
	public static final String DATE_FIRST_STARTED = "dateFirstStarted"; 
	public static final String DATE_LAST_PERSISTED = "dateLastPersisted"; // Refers to the day on which the data was recorded and not when it was persisted (always a later date...)
	public static final String DATE_LAST_RECORDED = "dateLastPersisted";
	
	public static ReadWriteLock readWriteLock = new ReentrantReadWriteLock();
	
	/*
	 * From: http://stackoverflow.com/questions/7361627/how-can-write-code-to-make-sharedpreferences-for-array-in-android/7361989#7361989
	 * 
	 * In order to delete (i.e. replace by null) the StringArray, just parse null as parameter
	 */
	public static void setStringArrayPref(Context context, String key, String[] array) {
		if (array != null) {
			ArrayList<String> values = new ArrayList<String>(Arrays.asList(array));
		    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		    SharedPreferences.Editor editor = prefs.edit();
		    JSONArray a = new JSONArray();
		    for (int i = 0; i < values.size(); i++) {
		        a.put(values.get(i));
		    }
		    if (!values.isEmpty()) {
		        editor.putString(key, a.toString());
		    } else {
		        editor.putString(key, null);
		    }
		    editor.commit();
		} else {
			SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		    SharedPreferences.Editor editor = prefs.edit();
		    editor.putString(key, null);
		    editor.commit();
		}

	}

	/*
	 * From: http://stackoverflow.com/questions/7361627/how-can-write-code-to-make-sharedpreferences-for-array-in-android/7361989#7361989
	 */
	public static String[] getStringArrayPref(Context context, String key) {
	    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
	    if (prefs.getString(key, null) == null) {
	    	return null;
	    }
	    String json = prefs.getString(key, null);
	    ArrayList<String> urls = new ArrayList<String>();
	    if (json != null) {
	        try {
	            JSONArray a = new JSONArray(json);
	            for (int i = 0; i < a.length(); i++) {
	                String url = a.optString(i);
	                urls.add(url);
	            }
	        } catch (JSONException e) {
	            e.printStackTrace();
	        }
	    }
	    
	    String[] stringArray = new String[urls.size()];
	    return urls.toArray(stringArray);
	}
	
	/*
	 * From: http://stackoverflow.com/questions/7361627/how-can-write-code-to-make-sharedpreferences-for-array-in-android/7361989#7361989
	 */
	public static void setIntListPref(Context context, String key, ArrayList<Integer> values) {
		
	    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
	    SharedPreferences.Editor editor = prefs.edit();
	    JSONArray a = new JSONArray();
	    for (int i = 0; i < values.size(); i++) {
	        a.put(values.get(i));
	    }
	    if (!values.isEmpty()) {
	        editor.putString(key, a.toString());
	    } else {
	        editor.putInt(key, -1);
	    }
	    editor.commit();
	}

	/*
	 * From: http://stackoverflow.com/questions/7361627/how-can-write-code-to-make-sharedpreferences-for-array-in-android/7361989#7361989
	 */
	public static ArrayList<Integer> getIntListPref(Context context, String key) {
	    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
	    if (prefs.getString(key, null) == null) {
	    	return null;
	    }
	    String json = prefs.getString(key, null);
	    ArrayList<Integer> urls = new ArrayList<Integer>();
	    if (json != null) {
	        try {
	            JSONArray a = new JSONArray(json);
	            for (int i = 0; i < a.length(); i++) {
	                Integer url = a.getInt(i);
	                urls.add(url);
	            }
	        } catch (JSONException e) {
	            e.printStackTrace();
	        }
	    }

	    return urls;
	}
	
	/*
	 * http://stackoverflow.com/questions/9292954/how-to-make-a-copy-of-a-file-in-android
	 */
	public static void copyFile(File src, File dst) throws IOException {
	    InputStream in = new FileInputStream(src);
	    OutputStream out = new FileOutputStream(dst);

	    // Transfer bytes from in to out
	    byte[] buf = new byte[1024];
	    int len;
	    while ((len = in.read(buf)) > 0) {
	        out.write(buf, 0, len);
	    }
	    in.close();
	    out.close();
	}
}
