package com.example.contextrecognition;

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

import com.example.tools.AudioWorker;

public class MainActivity extends ActionBarActivity {

	private static final String TAG = "MainAcitivty";
	
	public static final String STOP_RECORDING = "stopRecording";
	
	String[] contextClasses = {"Context Class 1", "Context Class 2", "Context Class 3", 
			   "Context Class 4", "Define new class"};
	ImageButton changeButton;
	ImageButton confirmButton;
	SharedPreferences mPrefs;
	TextView contextTV;
	final String welcomeScreenShownPref = "welcomeScreenShown";
	
	ModelAdaptor modelAdaptor = new ModelAdaptor(); //TODO: delete??????
	 
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
    }
    
    @Override
    protected void onResume() {
      super.onResume();
      registerReceiver(receiver, new IntentFilter(AudioWorker.RESULT_REQUEST));
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
 
				Intent i = new Intent(MainActivity.this, ContextSelection.class);
		        startActivity(i);
 
			}
 
		});
		
		confirmButton.setOnClickListener(new OnClickListener() {
			 
			@Override
			public void onClick(View arg0) {
 
				modelAdaptor.currentClassCorrect();
 
			}
 
		});
 
	}

	public void setText(String str) {
		contextTV.setText(str);
	}

	private BroadcastReceiver receiver = new BroadcastReceiver() {
		@Override
	    public void onReceive(Context context, Intent intent) {
			Bundle bundle = intent.getExtras();
		      if (bundle != null) {
		    	  
		        int resultCode = bundle.getInt(AudioWorker.RESULTCODE);
		        int predInt = bundle.getInt(AudioWorker.PREDICTION_INT);
		        String predString = bundle.getString(AudioWorker.PREDICTION_STRING);	
		        
		        if (resultCode == RESULT_OK) {
		        	Log.i(TAG, "Current Prediction: " + predString + ": " + predInt);
		        	setText(predString);		        	
		        	
		        } else {
		        	Log.i(TAG, "Result not okay, result code " + resultCode);
		        }
		      }
	    }
	};
	
	private void stopRecording() {
		Intent intent = new Intent(STOP_RECORDING);
		unregisterReceiver(receiver);
		sendBroadcast(intent);
	}

}
