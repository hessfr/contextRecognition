package com.example.contextrecognition;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;

public class Welcome3 extends Activity {
	
	Button startButton;
	Button prevButton;
	
	private static final String TAG = "Welcome3";
	SharedPreferences mPrefs;
	final String welcomeScreenShownPref = "welcomeScreenShown";

	@Override
	public void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);
	    	this.requestWindowFeature(Window.FEATURE_NO_TITLE);
	    	
	    	setContentView(R.layout.activity_welcome3);
	    	
	    	addListenerOnButton();
	}
	
	public void addListenerOnButton() {
		 
		startButton = (Button) findViewById(R.id.startButton);
		prevButton = (Button) findViewById(R.id.prevButton);
 
		startButton.setOnClickListener(new OnClickListener() {
			 
			@Override
			public void onClick(View arg0) {
 
				Intent i = new Intent(Welcome3.this, MainActivity.class);
				i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_NO_ANIMATION); //TODO
		        startActivity(i);
 
			}
 
		});
		
		prevButton.setOnClickListener(new OnClickListener() {
 
			@Override
			public void onClick(View arg0) {
 
				Intent i = new Intent(Welcome3.this, Welcome2.class);
				i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_NO_ANIMATION); //TODO
		        startActivity(i);
 
			}
 
		});
		
	}
}
