package com.example.contextrecognition;

import java.io.File;
import java.util.ArrayList;

import android.os.Environment;

public class Globals {

	
	public static final String APP_FOLDER = "ContextRecognition";
	public static final File APP_PATH = new File(Environment.getExternalStorageDirectory().getAbsolutePath()
			+ "/" + APP_FOLDER);
	
	public static final File APP_DATA_FILE = new File(APP_PATH, "AppData.json");

	public static final File AL_LOG_FILE = new File(APP_PATH, "AL_Log.txt");
	
	/*
	 * App settings
	 */
	//Time after which the query is cancelled and the notification removed in ms:
	public static long CANCEL_QUERY_TIME = 60000; //=1min

	//Default value for the max number of queries per day:
	public static int MAX_QUERIES_PER_DAY = 10;
	
	// Minimum time we has to wait between two queries:
	public static long minBreak = 10 * 60 * 1000;
	
	// Time after which the app data (buffers, threshold, ...) should be periodically persisted to external storage
	public static long PERSIST_PERIOD = 5000;//10 * 60 * 1000;
	
	/*
	 * Buffer lengthes for the threshold calculation
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
//	public static final String IP = "192.168.0.23";
	public static final String IP = "172.30.152.238";
	public static final String PORT = "8080";
	public static final String BASE_URL = "http://" + IP + ":" + PORT + "/?";
	
	
	
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
	
	public static final String REGISTER_RECURRING_TASKS = "action.registerRecurringTasks";
	
	public static final String RESET_MAX_QUERY_NUMBER = "action.resetMaxQueryNumber";
	
	public static final String PERSIST_DATA = "action.persistData";
	
	public static final String MAX_QUERY_NUMBER_CHANGED = "action.maxQueryNumberChanged";
	
	// Send by the StateManager:
	public static final String PREDICTION_CHANGED_INTENT = "predictionChangedIntent";
	public static final String NEW_PREDICTION_STRING = "newPredictionString";
	
}
