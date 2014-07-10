package com.example.contextrecognition;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.tools.AudioWorker;
import com.example.tools.appStatus;

public class MainActivity extends ActionBarActivity {

	private static final String TAG = "MainAcitivty";

	/*
	 * Indicates if activity is called the first time at this run, i.e.
	 * it will be true again, when you completely restart the app
	 */
	private static boolean FIRST_RUN = true;
	private static String CONTEXT_CLASS_STRING;
	
	private Context context = this;

	private String[] contextClasses;
	private static Boolean[] currentGT; //same size as contextClasses string array
	static final String CURRENT_GT = "currentGT";
	ImageButton changeButton;
	ImageButton confirmButton;
	SharedPreferences mPrefs;
	TextView contextTV;
	final String welcomeScreenShownPref = "welcomeScreenShown";
	GtSelectorAdapter dataAdapter = null;
	
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

		if (FIRST_RUN == true) {
			Log.d(TAG, "First run of MainActivity");
			
			// Start the AudioWorker service:
			Intent i = new Intent(this, AudioWorker.class);
			startService(i);

			// Set app status to initializing:
			appStatus.getInstance().set(appStatus.INIT);
			Log.i(TAG, "New status: init");
			
			// Register the daily reset of the maximum number of queries and periodic data backup:
			Intent i2 = new Intent(Globals.REGISTER_RECURRING_TASKS);
			context.sendBroadcast(i2);
			
			// Set preferences initially if they haven't been set already:
			int tmp = mPrefs.getInt(Globals.MAX_NUM_QUERIES, -1);	
			if (tmp == -1) {
				SharedPreferences.Editor editor = mPrefs.edit();
				editor.putInt(Globals.MAX_NUM_QUERIES, 10);
				editor.commit();
				
				Log.i(TAG, "Preference commited");
			}
			
			// Append info to log when the app was started
			appendToGTLog(true, false, "");
			
		} else {
			
			// Restore the value of the checkboxes according to the (static) currentGT array:
			
			
			
			
			
			contextTV.setText(CONTEXT_CLASS_STRING);
		}

		String[] tmpStringArray = Globals.getStringArrayPref(context, Globals.CONTEXT_CLASSES);
		
		if (tmpStringArray != null) {
			contextClasses = tmpStringArray;
			
			/*
			 *  Initialize the array containing the ground truth information (to set the check boxes
			 *  correctly when we come back to this activity):
			 */
			if (FIRST_RUN == true) {
				currentGT = new Boolean[contextClasses.length];
				for(int j=0; j<currentGT.length; j++) {
					currentGT[j] = false;
				}
			}
			
			createListView(contextClasses);
			
		} else {
			//Send broadcast to request class names:
			Intent i3 = new Intent(Globals.REQUEST_CLASS_NAMES);
			context.sendBroadcast(i3);
		}
		
		setFirstRun();
	}

	@Override
	protected void onResume() {
		super.onResume();

		// Register the broadcast receiver of the MainAcitivity and intent filters:
		IntentFilter filterMain = new IntentFilter();
		filterMain.addAction(Globals.PREDICTION_CHANGED_INTENT);
		filterMain.addAction(Globals.CLASS_NAMES_SET);
		registerReceiver(receiverMainActivity, filterMain);
	}
	
	@Override
	public void onPause() {
		super.onPause();

		unregisterReceiver(receiverMainActivity);

	}
	
	@Override
	public void onDestroy() {
	    super.onDestroy();
	}
	
	@Override
	public void onStart() {
	    super.onStart();
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

	private void setText(String str) {
		contextTV.setText(str);
		CONTEXT_CLASS_STRING = str;
	}
	
	private void createListView(String[] stringArray) {
		ArrayList<String> contextList = new ArrayList<String>(Arrays.asList(stringArray));
		ArrayList<Boolean> gtList = new ArrayList<Boolean>(Arrays.asList(currentGT));
		
		dataAdapter = new GtSelectorAdapter(this,R.layout.gt_selector_element, contextList, gtList);
		
		ListView listView = (ListView) findViewById(R.id.gtSelector);
		//final ArrayAdapter dataAdapter = new ArrayAdapter(this,android.R.layout.simple_list_item_1, stringArray);
		
		
		// Assign adapter to ListView
		listView.setAdapter(dataAdapter);
		Log.d(TAG, "ListView for ground truth created");
		
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
			
			if (intent.getAction().equals(Globals.CLASS_NAMES_SET)) {
				
				String[] tmpStringArray = Globals.getStringArrayPref(context, Globals.CONTEXT_CLASSES);
				
				if (tmpStringArray != null) {

					contextClasses = tmpStringArray;
					currentGT = new Boolean[contextClasses.length];
					for(int i=0; i<currentGT.length; i++) {
						currentGT[i] = false;
					}
					createListView(contextClasses);

				} else {
					Log.e(TAG, "String array of context classes could not be read in MainActivity");
				}
				
			}
			
			// TODO: when new context class added, rebuild the list view
		}
	};
	
	/*
	 * Custom adaptaer for the ground truth selection list view
	 * 
	 * Code similar to: http://www.mysamplecode.com/2012/07/android-listview-checkbox-example.html
	 */
	private class GtSelectorAdapter extends ArrayAdapter<String>{

		private ArrayList<String> contextList;
		private ArrayList<Boolean> cbStatus;
		
		//Constructor:
		public GtSelectorAdapter(Context context, int resourceId, ArrayList<String> contextList, 
				ArrayList<Boolean> cbStatus) {
			super(context, resourceId, contextList);
			
			this.contextList = new ArrayList<String>();
			this.contextList.addAll(contextList);
			this.cbStatus = new ArrayList<Boolean>();
			this.cbStatus.addAll(cbStatus);
		}
		
		private class ViewHolder {
			CheckBox checkBox;
		}
		
		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
		
		   ViewHolder holder = null;
		   Log.v("ConvertView", String.valueOf(position));
		 
		   if (convertView == null) {
			   LayoutInflater vi = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			   
			   convertView = vi.inflate(R.layout.gt_selector_element, null);
			 
			   holder = new ViewHolder();
			   holder.checkBox = (CheckBox) convertView.findViewById(R.id.checkBox1);
			   convertView.setTag(holder);		   
			   
			   holder.checkBox.setOnClickListener( new View.OnClickListener() {  
			    	
				   public void onClick(View v) {  
				    	 
					   CheckBox cb = (CheckBox) v;
					   String contextClass = cb.getText().toString();
					   
					   // Find position of this string in the contextClasses array:
					   int idx = -1;
					   for(int i=0; i<contextClasses.length; i++) {
						   if (contextClass.equals(contextClasses[i])) {
							   idx = i;
						   }
					   }
					   if (cb.isChecked() == true) {
						   // CheckBox got selected just now:
						   appendToGTLog(false, true, contextClass);
						   
						   /*
						    * Update this value also in the currentGT array, so that we can restore
						    * the state of the checkboxes, when activity was closed:
						    */
						   if (idx != -1) {
							   currentGT[idx] = true;
						   }
						   
					   } else {
						   // CheckBox got unselected just now:
						   appendToGTLog(false, false, contextClass);
						   
						   /*
						    * Update this value also in the currentGT array, so that we can restore
						    * the state of the checkboxes, when activity was closed:
						    */
						   if (idx != -1) {
							   currentGT[idx] = false;
						   }
						   
					   }
				      
				   }  
			   });  
		   } 
		   else {
			   holder = (ViewHolder) convertView.getTag();
		   }

		   String string = contextList.get(position);
		   holder.checkBox.setText(string);
		   
		   holder.checkBox.setChecked(cbStatus.get(position));
		 
		   return convertView;
		 
		  }
	}
	
	private void appendToGTLog(boolean recordingInitialized, boolean isStart, String contextClass) {
		
		Log.d(TAG, "Appending to ground truth log");

		if (recordingInitialized == false) {
			String startOrEnd = null;
			if (isStart == true) {
				startOrEnd = "start";
			} else {
				startOrEnd = "end";
			}
				
			
			try {
				FileWriter f = new FileWriter(Globals.GT_LOG_FILE, true);
				f.write(System.currentTimeMillis() + "\t" + contextClass + "\t" +  startOrEnd + "\n");
				f.close();
			} catch (IOException e) {
				Log.e(TAG, "Writing to GT log file failed");
				e.printStackTrace();
			}
		} else {
			
			try {
				FileWriter f = new FileWriter(Globals.GT_LOG_FILE, true);
				f.write(System.currentTimeMillis() + "\t" + "RECORDING_STARTED" + "\t" +  "start" + "\n");
				f.close();
			} catch (IOException e) {
				Log.e(TAG, "Writing to GT log file failed");
				e.printStackTrace();
			}
		}

	}
	
	private void setFirstRun() {
		if(FIRST_RUN == true) {
			FIRST_RUN = false;
		}
	}

}
