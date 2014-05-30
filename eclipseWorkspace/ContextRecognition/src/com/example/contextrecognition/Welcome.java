package com.example.contextrecognition;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.util.Log;

public class Welcome extends Activity {
	
	Button button;
	private static final String TAG = "Welcome";
	SharedPreferences mPrefs;
	final String welcomeScreenShownPref = "welcomeScreenShown";

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);
	    setContentView(R.layout.activity_welcome);
	    
	    mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
	    
	    Boolean welcomeScreenShown = mPrefs.getBoolean(welcomeScreenShownPref, false);

	    if (welcomeScreenShown) {

	    	Log.i(TAG, "Welcome screen already shown before, going to MainActivity instead");

	    	Intent i = new Intent(Welcome.this, MainActivity.class);
	        startActivity(i);
	    }
	    
	    else {
	    	
	    	addListenerOnButton();
	    	
	    	SharedPreferences.Editor editor = mPrefs.edit();
	        editor.putBoolean(welcomeScreenShownPref, true);
	        editor.commit();
	    }
	}
	
	public void addListenerOnButton() {
		 
		button = (Button) findViewById(R.id.button1);
 
		button.setOnClickListener(new OnClickListener() {
 
			@Override
			public void onClick(View arg0) {
 
				Intent i = new Intent(Welcome.this, MainActivity.class);
		        startActivity(i);
 
			}
 
		});
 
	}
}
