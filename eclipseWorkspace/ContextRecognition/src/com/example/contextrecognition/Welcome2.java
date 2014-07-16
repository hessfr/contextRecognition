package com.example.contextrecognition;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

public class Welcome2 extends Activity {
	
	private static final String TAG = "Welcome2";
	
	SeekBar querySeekBar;
	TextView currentValueTV;
	private static final int queryLimit = 20;
	
	Button prevButton;
	Button nextButton;

	SharedPreferences mPrefs;
	final String welcomeScreenShownPref = "welcomeScreenShown";

	@Override
	public void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);
	    	this.requestWindowFeature(Window.FEATURE_NO_TITLE);
	    	
	    	setContentView(R.layout.activity_welcome2);
	    	
	    	addListenerOnButton();
	    	
	        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
	        
	        int currentValue = mPrefs.getInt(Globals.MAX_NUM_QUERIES, -1);
			if (currentValue == -1) {
				Log.i(TAG, "Maximum number of queries set to default value, as there was not entry in the preferences yet");
				currentValue = 10;
			}        
			
	        // TextView to show the current query limit value
	        currentValueTV = (TextView) findViewById(R.id.currentSeekBarValue);
	        currentValueTV.setText(String.valueOf(currentValue));
	        
	        // SeekBar set-up
	        querySeekBar = (SeekBar) findViewById(R.id.seekBar);
	        querySeekBar.setMax(queryLimit);
	        querySeekBar.setProgress(mPrefs.getInt(Globals.MAX_NUM_QUERIES,10));
	        
	        querySeekBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
	            int newValue = 0;
	 
	            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser){
	            	newValue = progress;
	            }
	 
	            public void onStartTrackingTouch(SeekBar seekBar) {
	                // Auto-generated method stub
	            }
	 
	            public void onStopTrackingTouch(SeekBar seekBar) {
	            	currentValueTV.setText(String.valueOf(newValue));
	            	
	            	SharedPreferences.Editor editor = mPrefs.edit();
	            	int prev = mPrefs.getInt(Globals.MAX_NUM_QUERIES, -1);
	            	if (prev != -1) {
	            		editor.putInt(Globals.PREV_MAX_NUM_QUERIES, prev);
	            	} else {
	            		Log.e(TAG, "Could not get max number of queries from preferences");
	            	}
	            	
	    			editor.putInt(Globals.MAX_NUM_QUERIES, newValue);
	    			editor.commit();
	    			
	    			Log.d(TAG, "Preference commited, new value of MAX_NUM_QUERIES: " + newValue);
	    			
	    			Intent intent = new Intent(Globals.MAX_QUERY_NUMBER_CHANGED);
	    			
	    			sendBroadcast(intent);    			
	            }
	        });
	}
	
	public void addListenerOnButton() {
		 
		prevButton = (Button) findViewById(R.id.prevButton);
		nextButton = (Button) findViewById(R.id.nextButton);
 
		prevButton.setOnClickListener(new OnClickListener() {
			 
			@Override
			public void onClick(View arg0) {
 
				Intent i = new Intent(Welcome2.this, Welcome1.class);
				i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_NO_ANIMATION); //TODO
		        startActivity(i);
 
			}
 
		});
		
		nextButton.setOnClickListener(new OnClickListener() {
 
			@Override
			public void onClick(View arg0) {
 
				Intent i = new Intent(Welcome2.this, Welcome3.class);
				i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_NO_ANIMATION); //TODO
		        startActivity(i);
 
			}
 
		});
 
	}
}
