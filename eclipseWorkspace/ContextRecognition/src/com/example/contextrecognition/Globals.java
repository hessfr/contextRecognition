package com.example.contextrecognition;

import java.io.File;

import android.os.Environment;

public class Globals {

	
	public static final String APP_FOLDER = "ContextRecognition";
	public static final File APP_PATH = new File(Environment.getExternalStorageDirectory().getAbsolutePath()
			+ "/" + APP_FOLDER);

	public static final File AL_LOG_FILE = new File(APP_PATH, "AL_Log.txt"); //-> does this override existing file?
	
	// Time after which the query is cancelled and the notification removed in ms:
	public static long CANCEL_QUERY_TIME = 60000; //=1min

	// Default value for the max number of queries per day:
	public static int MAX_QUERIES_PER_DAY = 10;
	
//	public static final String IP = "192.168.0.23";
	public static final String IP = "172.30.152.238";
	public static final String PORT = "8080";
	public static final String BASE_URL = "http://" + IP + ":" + PORT + "/?";
	
	
}
