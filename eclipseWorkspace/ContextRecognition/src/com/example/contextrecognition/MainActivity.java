package com.example.contextrecognition;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.example.tools.AudioWorker;
import com.example.tools.ClassesDict;
import com.example.tools.GMM;
import com.example.tools.appStatus;

public class MainActivity extends ActionBarActivity {

	private static final String TAG = "MainAcitivty";
	
	public static final String STOP_RECORDING = "stopRecording";
	
	// Variables of the current prediction:
	private int predictionInt;
	private String predictionString;
	private Map<String, Integer> classesDict = new HashMap<String, Integer>();
	private String[] classNameArray;
	private boolean bufferStatus;
	private ArrayList<double[]> buffer;
	private GMM gmm;
	
	
	private Context cxt = this;
	
	public String[] contextClasses;
	ImageButton changeButton;
	ImageButton confirmButton;
	SharedPreferences mPrefs;
	TextView contextTV;
	final String welcomeScreenShownPref = "welcomeScreenShown";
	 
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
	    mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
	    
	    Boolean welcomeScreenShown = mPrefs.getBoolean(welcomeScreenShownPref, false);

	    if (!welcomeScreenShown) {
	    	//Open the welcome activity if it hasn't been shown yet

	    	Log.i(TAG, "Welcome screen already shown before, going to MainActivity instead");

	    	SharedPreferences.Editor editor = mPrefs.edit();
	        editor.putBoolean(welcomeScreenShownPref, true);
	        editor.commit();
	    	
	    	Intent i = new Intent(MainActivity.this, Welcome1.class);
	    	i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_NO_ANIMATION);
	        startActivity(i);
	    } 
    	
	    addListenerOnButton();
	    contextTV = (TextView) findViewById(R.id.contextTV);
	    
		// Start the AudioWorker service:
		Intent i = new Intent(this, AudioWorker.class);
        startService(i);
    	
    	// Set app status to initializing:
		appStatus.getInstance().set(appStatus.INIT);
		Log.i(TAG, "New status: init");
		
    }
    
    @Override
    protected void onResume() {
      super.onResume();
      
      // Register the broadcast receiver and intent filters:
      IntentFilter filter = new IntentFilter();
      filter.addAction(AudioWorker.PREDICTION);
      filter.addAction(AudioWorker.STATUS);
      //filter.addAction(Params.INTENT_UPDATE);
      
      registerReceiver(receiver, filter);
      
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	//Create the options entry in the ActionBar
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	//Handle ActionBar selections
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            //Go to settings activity
        	callSettings();
        	return true;
        }
        if (id == R.id.action_label) {
        	//Go to label activity
        	callLabel();
        	return true;
        }
        if (id == R.id.action_rating) {
        	//Go to rating activity
        	callRating();
        	return true;
        }
        if (id == R.id.action_help) {
        	//Go to help activity
            callHelp();
        	return true;
        }
        if (id == R.id.action_exit) {
        	stopRecording();
        	finish();
        }
        
        
        return super.onOptionsItemSelected(item);
    }
    
    /**
     * Launch Settings activity
     * */
    private void callSettings() {
        Intent i = new Intent(MainActivity.this, Settings.class);
        startActivity(i);
    }
    /**
     * Launch Label activity
     * */
    private void callLabel() {
        Intent i = new Intent(MainActivity.this, Label.class);
        startActivity(i);
    }
    /**
     * Launch Rating activity
     * */
    private void callRating() {
        Intent i = new Intent(MainActivity.this, Rating.class);
        startActivity(i);
    }
    /**
     * Launch Help activity
     * */
    private void callHelp() {
        Intent i = new Intent(MainActivity.this, Help.class);
        startActivity(i);
    }
    
	public void addListenerOnButton() {
		 
		changeButton = (ImageButton) findViewById(R.id.changeButton);
		confirmButton = (ImageButton) findViewById(R.id.confirmButton);
 
		changeButton.setOnClickListener(new OnClickListener() {
 
			@Override
			public void onClick(View arg0) {
 
				if (ClassesDict.getInstance().isEmpty() == false) {
					Intent i = new Intent(MainActivity.this, ContextSelection.class);
			        startActivity(i);
				} else {
					// If class names not yet available, send Toast
					Toast.makeText(getBaseContext(),(String) "Please wait until the system is initialized", Toast.LENGTH_SHORT).show();
					
					Log.w(TAG, "Not changing to ContextSelection activity, as class names not available yet.");
				}
				
				
 
			}
 
		});
		
		confirmButton.setOnClickListener(new OnClickListener() {
			 
			@Override
			public void onClick(View arg0) {
		        
		    	//Toast.makeText(getBaseContext(),(String) "current prediction: " + predictionString, Toast.LENGTH_SHORT).show();
				
//				if (appStatus.getInstance().getBufferStatus() == appStatus.BUFFER_READY) {
//					
//					// TODO: call model adaptor
//					
//				} else {
//					// If not initialized yet, send Toast
//					Toast.makeText(getBaseContext(),(String) "Please wait until the system is initialized", Toast.LENGTH_SHORT).show();
//					Log.w(TAG, "Buffer not full yet. Not adapting the model");
//				}
				
			}
 
		});
 
	}

	public void setText(String str) {
		contextTV.setText(str);
	}

	private BroadcastReceiver receiver = new BroadcastReceiver() {
		@SuppressWarnings("unchecked")
		@Override
	    public void onReceive(Context context, Intent intent) {
			Bundle bundle = intent.getExtras();
		      if (bundle != null) {
		    	  	
		    	  	if (intent.getAction().equals(AudioWorker.PREDICTION)) {
		    	  		
		    	  		int resultCode = bundle.getInt(AudioWorker.RESULTCODE);
		    	  		predictionInt = bundle.getInt(AudioWorker.PREDICTION_INT);
		    	  		predictionString = bundle.getString(AudioWorker.PREDICTION_STRING);	
						
						if (resultCode == RESULT_OK) {
							Log.i(TAG, "Current Prediction: " + predictionString + ": " + predictionInt);
							setText(predictionString);
						} else {
							Log.i(TAG, "Received prediction result not okay, result code " + resultCode);
						}
						
		    	  	} else if (intent.getAction().equals(AudioWorker.STATUS)) {
		    	  		
		    	  		int resultCode = bundle.getInt(AudioWorker.RESULTCODE);
		    	  		
		    	  		if (resultCode == RESULT_OK) {
		    	  			classNameArray = bundle.getStringArray(AudioWorker.CLASS_STRINGS);
//			    	  		Log.i(TAG, "xxxxx " + classNameArray[0]);
		    	  			bufferStatus = bundle.getBoolean(AudioWorker.BUFFER_STATUS);
//			    	  		Log.i(TAG, "xxxxx " + String.valueOf(bufferStatus));
			    	  		Serializable s1 = bundle.getSerializable(AudioWorker.BUFFER);
			    	  		buffer = (ArrayList<double[]>) s1;		    	  		
//			    	  		Log.i(TAG, "xxxxx " + String.valueOf(buffer.get(0)[0]));
			    	  		
			    	  		gmm = bundle.getParcelable(AudioWorker.GMM_OBJECT);
//			    	  		Log.i(TAG, "xxxxxx " + gmm.get_class_name(0));
			    	  		
			    	  		// Update the ClassesDict
							Serializable s2 = new HashMap<String, Integer>();
							s2 = bundle.getSerializable(AudioWorker.CLASSES_DICT);
							classesDict = ((HashMap<String, Integer>) s2);
							ClassesDict.getInstance().setMap(classesDict); //TODO: delete???
//							Log.i(TAG, "xxxxxx " + ClassesDict.getInstance().getStringArray()[0]);
		    	  		
		    	  		} else {
							Log.i(TAG, "Received prediction result not okay, result code " + resultCode);
						}
		    	  		

		    	  	}
		    	  
					  
					
		      }
	    }
	};
	
	private void stopRecording() {
		Intent intent = new Intent(STOP_RECORDING);
		unregisterReceiver(receiver);
		sendBroadcast(intent);
	}
		
    @Override
    public void onPause() {
        super.onPause();
    }
}
