package com.example.contextrecognition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import android.app.NotificationManager;
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
import com.example.tools.ModelAdaptor.onModelAdaptionCompleted;
import com.example.tools.PostRequest;
import com.example.tools.StateManager;
import com.example.tools.appStatus;

public class MainActivity extends ActionBarActivity {

	private static final String TAG = "MainAcitivty";

	private Context context = this;

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

		Boolean welcomeScreenShown = mPrefs.getBoolean(welcomeScreenShownPref,
				false);

		if (!welcomeScreenShown) {
			// Open the welcome activity if it hasn't been shown yet

			Log.i(TAG,
					"Welcome screen already shown before, going to MainActivity instead");

			SharedPreferences.Editor editor = mPrefs.edit();
			editor.putBoolean(welcomeScreenShownPref, true);
			editor.commit();

			Intent i = new Intent(MainActivity.this, Welcome1.class);
			i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
					| Intent.FLAG_ACTIVITY_NO_ANIMATION);
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
		
		// Register the daily reset of the maximum number of queries:
		Intent i2 = new Intent(Globals.REGISTER_QUERY_NUMBER_RESET);
		context.sendBroadcast(i2);
	}

	@Override
	protected void onResume() {
		super.onResume();

		// Register the broadcast receiver of the MainAcitivity and intent filters:
		IntentFilter filterMain = new IntentFilter();
		filterMain.addAction(Globals.PREDICTION_CHANGED_INTENT);
		registerReceiver(receiverMainActivity, filterMain);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Create the options entry in the ActionBar
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle ActionBar selections
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			// Go to settings activity
			callSettings();
			return true;
		}
		if (id == R.id.action_label) {
			// Go to label activity
			callLabel();
			return true;
		}
		if (id == R.id.action_rating) {
			// Go to rating activity
			callRating();
			return true;
		}
		if (id == R.id.action_help) {
			// Go to help activity
			callHelp();
			return true;
		}
		if (id == R.id.action_exit) {
			//stopRecording();
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

//				sendQuery(); //TODO
				
				Intent intent = new Intent(Globals.CALL_CONTEXT_SELECTION_INTENT);
    			Bundle bundle = new Bundle();
    			intent.putExtras(bundle);
    			sendBroadcast(intent);
			}

		});

		confirmButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View arg0) {

				Intent intent = new Intent(Globals.MODEL_ADAPTION_EXISTING_INTENT);
    			Bundle bundle = new Bundle();
    			bundle.putInt(Globals.LABEL, 1);
    			intent.putExtras(bundle);
    			sendBroadcast(intent);

			}

		});

	}

	public void setText(String str) {
		contextTV.setText(str);
	}

	private BroadcastReceiver receiverMainActivity = new BroadcastReceiver() {
		@SuppressWarnings("unchecked")
		@Override
		public void onReceive(Context context, Intent intent) {
			Bundle bundle = intent.getExtras();
			if (bundle != null) {
				
				if (intent.getAction().equals(Globals.PREDICTION_CHANGED_INTENT)) {
					setText(bundle.getString(Globals.NEW_PREDICTION_STRING));
				}

			}
		}
	};

	@Override
	public void onPause() {
		super.onPause();
		
		unregisterReceiver(receiverMainActivity);

	}

}
