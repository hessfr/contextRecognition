package com.example.contextrecognition;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;

public class Welcome2 extends Activity {
	
	Button prevButton;
	Button nextButton;
	
	private static final String TAG = "Welcome2";
	SharedPreferences mPrefs;
	final String welcomeScreenShownPref = "welcomeScreenShown";

	@Override
	public void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);
	    	this.requestWindowFeature(Window.FEATURE_NO_TITLE);
	    	
	    	setContentView(R.layout.activity_welcome2);
	    	
	    	addListenerOnButton();
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
