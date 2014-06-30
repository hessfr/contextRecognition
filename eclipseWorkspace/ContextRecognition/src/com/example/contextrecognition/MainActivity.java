package com.example.contextrecognition;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

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
//import com.example.tools.ClassesDictXXX;
import com.example.tools.GMM;
import com.example.tools.ModelAdaptor;
import com.example.tools.ModelAdaptor.onModelAdaptionCompleted;
import com.example.tools.appStatus;

public class MainActivity extends ActionBarActivity {

	private static final String TAG = "MainAcitivty";
	
	public static final String STOP_RECORDING = "stopRecording";
	
	public static final String CLASS_NAMES_INTENT = "classNamesIntent";
	public static final String CLASS_NAMES = "classNames";
	
	public static final String MODEL_ADAPTION_EXISTING_INTENT = "modelAdaptionExisting";
	public static final String LABEL = "label";
	
	public static final String MODEL_ADAPTION_FINISHED_INTENT = "modelAdaptionFinished";
	
	public static final String MODEL_ADAPTION_NEW_INTENT = "modelAdaptionNew";
	public static final String NEW_CLASS_NAME = "newClassName";
	
	// Variables of the current prediction:
	private int predictionInt;
	private String predictionString;
	public Map<String, Integer> classesDict = new HashMap<String, Integer>(); //Needed??
	private String[] classNameArray;
	private boolean bufferStatus;
	private ArrayList<double[]> buffer;
	private GMM gmm; //Needed??
	
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
      filter.addAction(MODEL_ADAPTION_EXISTING_INTENT);
      filter.addAction(MODEL_ADAPTION_FINISHED_INTENT);
      filter.addAction(MODEL_ADAPTION_NEW_INTENT);
      
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

				if (classesDict.size() > 0) {
					Intent i = new Intent(MainActivity.this, ContextSelection.class);
					Bundle b = new Bundle();
					b.putStringArray(CLASS_NAMES, getStringArray(classesDict));
	    			i.putExtras(b);
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
		        
				callModelAdaption(predictionInt);
				
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
							Log.d(TAG, "Current Prediction: " + predictionString + ": " + predictionInt);
							setText(predictionString);
						} else {
							Log.i(TAG, "Received prediction result not okay, result code " + resultCode);
						}
						
		    	  	} else if (intent.getAction().equals(AudioWorker.STATUS)) {
		    	  		
		    	  		int resultCode = bundle.getInt(AudioWorker.RESULTCODE);
		    	  		
		    	  		if (resultCode == RESULT_OK) {
		    	  			classNameArray = bundle.getStringArray(AudioWorker.CLASS_STRINGS);
//			    	  		Log.i(TAG, classNameArray[0]);
		    	  			bufferStatus = bundle.getBoolean(AudioWorker.BUFFER_STATUS);
//			    	  		Log.i(TAG, String.valueOf(bufferStatus));
			    	  		Serializable s1 = bundle.getSerializable(AudioWorker.BUFFER);
			    	  		buffer = (ArrayList<double[]>) s1;		    	  		
//			    	  		Log.i(TAG, String.valueOf(buffer.get(0)[0]));
			    	  		
			    	  		gmm = bundle.getParcelable(AudioWorker.GMM_OBJECT); //Needed??
//			    	  		Log.i(TAG, gmm.get_class_name(0));
			    	  		
							Serializable s2 = new HashMap<String, Integer>();
							s2 = bundle.getSerializable(AudioWorker.CLASSES_DICT);
							classesDict = (HashMap<String, Integer>) s2;
		    	  		
		    	  		} else {
							Log.i(TAG, "Received prediction result not okay, result code " + resultCode);
						}
		    	  		

		    	  	} else if (intent.getAction().equals(MODEL_ADAPTION_EXISTING_INTENT)) {
		    	  		
		    	  		int label = bundle.getInt(LABEL);
		    	  		callModelAdaption(label);
		    			
		    	  	} else if (intent.getAction().equals(MODEL_ADAPTION_FINISHED_INTENT)) {
		    	  		
		    	  		Toast.makeText(getBaseContext(),(String) "Model adaptation finished", Toast.LENGTH_SHORT).show();
		    			
		    	  	} else if (intent.getAction().equals(MODEL_ADAPTION_NEW_INTENT)) {
		    	  		
		    	  		String newClassName = bundle.getString(NEW_CLASS_NAME);

		    	  		requestNewClassFromServer(newClassName);
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
    
	public String[] getStringArray(Map<String, Integer> classesDict) {
		
		int len = classesDict.size();
		
		String[] strArray = new String[len];
		
		int i=0;
		for ( String key : classesDict.keySet() ) {
			strArray[i] = key;
			i++;
		}
		
		return strArray;
		
	}
	
	private void requestNewClassFromServer(String newClassName) {
		
		Log.i(TAG, "Requesting new context class " + newClassName  + " from server");
		
	}
	
	private void callModelAdaption(int label) {
		
//		if(bufferStatus == true) {
				
			new ModelAdaptor(buffer, label, listener).execute(this);
			
			Toast.makeText(getBaseContext(),(String) "Model is being adapted", Toast.LENGTH_SHORT).show();
			
//		} else {
//			
//			Toast.makeText(getBaseContext(),(String) "Please wait until the system is initialized", Toast.LENGTH_SHORT).show();
//			
//			Log.w(TAG, "Model adaption not called, as the buffer is not full yet.");
//		}
		
		
	}
	
	private onModelAdaptionCompleted listener = new onModelAdaptionCompleted() {

		@Override
		public void onModelAdaptionCompleted(GMM newGMM) {
			
			Toast.makeText(getBaseContext(),(String) "Model adation completed", Toast.LENGTH_SHORT).show();
		}
	};
	
}
