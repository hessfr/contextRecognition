package ch.ethz.wearable.contextrecognition.activities;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.Settings.Secure;
import android.support.v7.app.ActionBarActivity;
import android.telephony.TelephonyManager;
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
import ch.ethz.wearable.contextrecognition.R;
import ch.ethz.wearable.contextrecognition.audio.AudioWorker;
import ch.ethz.wearable.contextrecognition.othersensors.RecService;
import ch.ethz.wearable.contextrecognition.utils.AppStatus;
import ch.ethz.wearable.contextrecognition.utils.EventDetection;
import ch.ethz.wearable.contextrecognition.utils.Globals;
import ch.ethz.wearable.contextrecognition.welcomescreens.WelcomeActivity;

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
	ImageButton changeButton;
	ImageButton confirmButton;
	SharedPreferences mPrefs;
	TextView contextTV;
	TextView entropyTV;
	final String welcomeScreenShownPref = "welcomeScreenShown";
	GtSelectorAdapter dataAdapter = null;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		Log.i(TAG, "MainActivity OnCreate");
		
		mPrefs = PreferenceManager.getDefaultSharedPreferences(this);

		Boolean welcomeScreenShown = mPrefs.getBoolean(welcomeScreenShownPref,
				false);

		// Reset the classes to be added / removed arrays, as the request is forgotten when app closed:
		Globals.setStringArrayPref(context, Globals.CLASSES_BEING_ADDED, null);
		Globals.setStringArrayPref(context, Globals.CLASSES_BEING_REMOVED, null);
		
		if (!welcomeScreenShown) {
			// Obtain a unique user ID and store it in the preferences:
			final TelephonyManager mTelephony = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
			String tmpId = null;
			if (mTelephony.getDeviceId() != null) {
				tmpId = mTelephony.getDeviceId();
			} else {
				tmpId = Secure.getString(getApplicationContext()
						.getContentResolver(), Secure.ANDROID_ID);
			}
			
			// Create folder on external storage is it doesn't exist already:
			File f = new File(Environment.getExternalStorageDirectory().getAbsolutePath()
					+ "/" + Globals.APP_FOLDER);
			if (!f.exists()) {
				Log.i(TAG, "Createing app folder on external storage");
			    f.mkdir();
			}
			Log.i(TAG, "Copying asset data into external storage folder");
			copyAssetFile("GMM.json");
			
			SharedPreferences.Editor editor = mPrefs.edit();
			editor.putString(Globals.USER_ID, tmpId);
			editor.putBoolean(welcomeScreenShownPref, true);
			editor.commit();
			
			Log.d(TAG, "User ID set to: " + tmpId);
			Log.i(TAG, "Very first start of the app: displaying welcome screen first");

			// Open the welcome activity if it hasn't been shown yet (i.e. at the very first start):
			Intent i = new Intent(this, WelcomeActivity.class);
			i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NO_ANIMATION);
			startActivity(i);
			finish();
			return;
		}
		
		setContentView(R.layout.activity_main);

		addListenerOnButton();
		contextTV = (TextView) findViewById(R.id.contextTV);
		entropyTV = (TextView) findViewById(R.id.entropyTV);

		if (FIRST_RUN == true) {
			Log.i(TAG, "First run of MainActivity");

			// Start the AudioWorker service:
			Intent i = new Intent(this, AudioWorker.class);
			startService(i);
			
			Intent i2 = new Intent(MainActivity.this,RecService.class);
			startService(i2);
			
			Intent i4 = new Intent(MainActivity.this,EventDetection.class);
			startService(i4);

			// Set app status to initializing:
			AppStatus.getInstance().set(AppStatus.INIT);
			Log.i(TAG, "New status: init");
			
			// Register the daily reset of the maximum number of queries and periodic data backup:
			Intent i3 = new Intent(Globals.REGISTER_RECURRING_TASKS);
			context.sendBroadcast(i3);
			
			// Set preferences initially if they haven't been set already:
			int tmp = mPrefs.getInt(Globals.MAX_NUM_QUERIES, -1);	
			if (tmp == -1) {
				SharedPreferences.Editor editor = mPrefs.edit();
				editor.putInt(Globals.MAX_NUM_QUERIES, 10);
				editor.commit();
				
				appendToMaxQueryLog(10);
				
				Log.d(TAG, "Preference commited");
			}
			
			// Reset the current context stored in the preferences to null:
			SharedPreferences.Editor editor = mPrefs.edit();
			editor.putString(Globals.CURRENT_CONTEXT, "");
			editor.commit();
			
			// Append info to log when the app was started
			appendToGTLog(true, false, "");
			
			setFirstRun();
			
		} else {
			
			contextTV.setText(CONTEXT_CLASS_STRING);
		}

		String[] tmpStringArray = Globals.getStringArrayPref(context, Globals.CONTEXT_CLASSES);
		
		if (tmpStringArray != null) {
			
			contextClasses = tmpStringArray;
			
			createListView(contextClasses);
			
		} else {
			
			//Send broadcast to request class names:
			Log.d(TAG, "ContextClasses string array in the preferences empty, "
					+ "requested StateManager to push to prefs again");
			
			Intent i3 = new Intent(Globals.REQUEST_CLASS_NAMES);
			context.sendBroadcast(i3);
		}
	}

	@SuppressWarnings("static-access")
	@Override
	protected void onResume() {
		super.onResume();
		
		// Register the broadcast receiver of the MainAcitivity and intent filters:
		IntentFilter filterMain = new IntentFilter();
		filterMain.addAction(Globals.PREDICTION_CHANGED_INTENT);
		filterMain.addAction(Globals.PREDICTION_ENTROPY_INTENT);
		filterMain.addAction(Globals.CLASS_NAMES_SET);
		filterMain.addAction(Globals.CLASSES_BEING_ADDED_INTENT);		
		registerReceiver(receiverMainActivity, filterMain);
		
		// Set the prediction TextView to the current prediction (workaround!)
		String s = mPrefs.getString(Globals.CURRENT_CONTEXT, "");
		setText(s);
		
		/*
		 * If we stopped the recording and left the app and come back now, we want to start
		 * recording again:
		 */
		if (AppStatus.getInstance().get() == AppStatus.getInstance().STOP_RECORDING) {
			Globals.RECORDING_START_TIME = 0;
			Intent i = new Intent(this, AudioWorker.class);
			startService(i);
			AppStatus.getInstance().set(AppStatus.INIT);
			Log.i(TAG, "New status: init");
			
			Intent i2 = new Intent(MainActivity.this,RecService.class);
			startService(i2);
			
			Intent i3 = new Intent(MainActivity.this,EventDetection.class);
			startService(i3);
		}
		
		String[] tmpStringArray = Globals.getStringArrayPref(context, Globals.CONTEXT_CLASSES);
		if (tmpStringArray != null) {

			contextClasses = tmpStringArray;

			createListView(contextClasses);

		}
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
		if (id == R.id.action_diary) {
			// Go to label activity
			callLabel();
			return true;
		}
//		if (id == R.id.action_rating) {
//			// Go to rating activity
//			callRating();
//			return true;
//		}
//		if (id == R.id.action_help) {
//			// Go to help activity
//			callHelp();
//			return true;
//		}
		if (id == R.id.action_exit) {
			// Quit the app and stop the recording:
			callShutdown();
		}
		if (id == R.id.action_manage_classes) {
			// Go to manage classes activity:
			callManageClasses();
		}
		if (id == R.id.action_upload) {
			// Go to manage classes activity:
			callUploadActivity();
		}

		return super.onOptionsItemSelected(item);
	}

	/**
	 * Launch Settings activity
	 * */
	private void callSettings() {
		Intent i = new Intent(MainActivity.this, SettingsActivity.class);
		startActivity(i);
	}

	/**
	 * Launch Label activity
	 * */
	private void callLabel() {
		Intent i = new Intent(MainActivity.this, DiaryActivity.class);
		startActivity(i);
	}

//	/**
//	 * Launch Rating activity
//	 * */
//	private void callRating() {
//		Intent i = new Intent(MainActivity.this, RatingActivity.class);
//		startActivity(i);
//	}

	/**
	 * Launch Help activity
	 * */
//	private void callHelp() {
//		Intent i = new Intent(MainActivity.this, HelpActivity.class);
//		startActivity(i);
//	}
    /**
     * Launch manage classes activity
     * */
    private void callManageClasses() {
        Intent i = new Intent(MainActivity.this, ManageClassesActivity.class);
        startActivity(i);
    }
    /**
     * Launch upload activity
     * */
    private void callUploadActivity() {
        Intent i = new Intent(MainActivity.this, UploadActivity.class);
        startActivity(i);
    }
    /**
     * Launch Shutdown activity to close app and stop recording
     * */
    private void callShutdown() {
		Application app = getApplication();
	    Intent intent = new Intent(app, ShutdownActivity.class);
	    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
	    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
	    app.startActivity(intent);
    }

	public void addListenerOnButton() {

		changeButton = (ImageButton) findViewById(R.id.changeButton);
		confirmButton = (ImageButton) findViewById(R.id.confirmButton);

		changeButton.setOnClickListener(new OnClickListener() {

			@SuppressWarnings("static-access")
			@Override
			public void onClick(View arg0) {
				
				if (AppStatus.getInstance().get() != AppStatus.getInstance().MODEL_ADAPTION) {
					
					Intent i = new Intent(MainActivity.this, ContextSelection.class);
					startActivity(i);
					
	    			
				} else {
					
					Toast.makeText(context, (String) "Please wait until previous model adaption finished",
							Toast.LENGTH_LONG).show();
					
					Log.i(TAG, "Model adaption request ignored, as previous model adaption still in progress");
				}
				

			}

		});

		confirmButton.setOnClickListener(new OnClickListener() {

			@SuppressWarnings("static-access")
			@Override
			public void onClick(View arg0) {
				
				if (AppStatus.getInstance().get() != AppStatus.getInstance().MODEL_ADAPTION) {
					
					Intent intent = new Intent(Globals.MODEL_ADAPTION_EXISTING_INTENT);
	    			Bundle bundle = new Bundle();
	    			bundle.putInt(Globals.LABEL, 1);
	    			intent.putExtras(bundle);
	    			sendBroadcast(intent);
	    			
				} else {
					
					Toast.makeText(context, (String) "Please wait until previous model adaption finished",
							Toast.LENGTH_LONG).show();
					
					Log.i(TAG, "Model adaption request ignored, as previous model adaption still in progress");
				}
			}
		});

	}

	private void setText(String str) {
		
		if (str.equals(Globals.SILENCE)) {
			contextTV.setText("Silence");
			contextTV.setTextColor(getResources().getColor(R.color.silent_text_view));
			CONTEXT_CLASS_STRING = str;
			setEntropyText(0);
		} else {
			contextTV.setText(str);
			contextTV.setTextColor(getResources().getColor(R.color.normal_text_view));
			CONTEXT_CLASS_STRING = str;
		}
	}
	
	private void setEntropyText(double value) {

		entropyTV.setText(String.valueOf(Math.round(value*100)/1000.d));

	}
	
	private void createListView(String[] stringArray) {
		
		/*
		 *  Initialize the array containing the ground truth information (to set the check boxes
		 *  correctly when we come back to this activity):
		 */		
		
		if (stringArray == null) {
			Log.e(TAG, "Failed to create ListView for the GT logger, as stringArray is empty");
			return;
		}

		
		// Add the pending classes as well:
		String[] classesBeingAdded = Globals.getStringArrayPref(context, Globals.CLASSES_BEING_ADDED);
		int numClassesAdded = 0;
		if (classesBeingAdded != null) {
			numClassesAdded = classesBeingAdded.length;
		}
		
		// This array contains both, the classes already in the model and the pending classes:
		String[] allClasses = new String[(stringArray.length + numClassesAdded)];
		
		// Fill it with classes in the classifier first:
		for(int i=0; i<stringArray.length; i++) {
			allClasses[i] = stringArray[i];
		}
		
		// Then add pending classes if there are any:
		if (classesBeingAdded != null) {
			int j=0;
			for(int i=stringArray.length; i<allClasses.length; i++) {
				Log.i(TAG, "class " + classesBeingAdded[j] + " added, although it is not in clf yet");
				allClasses[i] = classesBeingAdded[j];
				j++;
			}
		}
		
		
		// Initialize the ground truth array the very first time:
		if (currentGT == null) {
			Log.d(TAG, "Initializing currentGT array");
			currentGT = new Boolean[allClasses.length];
			for(int j=0; j<currentGT.length; j++) {
				currentGT[j] = false;
			}
		}
		
		/*
		 *  When new context class(es) added, increase the size of the currentGT array to match the class names again
		 *  and fill the new elements with false value:
		 */
		if (currentGT.length != allClasses.length) {
			
			Log.i(TAG, "Adjusting length of currentGT array");
			
			Boolean[] tmp = new Boolean[currentGT.length];
			for(int i=0; i<currentGT.length; i++) {
				tmp[i] = currentGT[i];
			}
			
			Log.i(TAG, "tmp length: " + tmp.length);
			Log.i(TAG, "currentGT length: " + currentGT.length);
			
			currentGT = new Boolean[allClasses.length];
			for(int i=0; i<allClasses.length ; i++) {
				if (i<(tmp.length-1)) { // First fill the status of all classes that were already included:
					Log.d(TAG, " " + currentGT[i]);
					Log.d(TAG, " " + tmp[i]);
					currentGT[i] = tmp[i];
				} else { // Then set status of new classes to false:
					currentGT[i] = false;
				}			
			}
		}
		Log.d(TAG, "increasing length of currentGT done");
		
		if (allClasses != null) {
			ArrayList<String> contextList = new ArrayList<String>(Arrays.asList(allClasses));
			ArrayList<Boolean> gtList = new ArrayList<Boolean>(Arrays.asList(currentGT));
			
			dataAdapter = new GtSelectorAdapter(this,R.layout.listview_element_checkbox, contextList, gtList);
			
			ListView listView = (ListView) findViewById(R.id.gtSelector);
			//final ArrayAdapter dataAdapter = new ArrayAdapter(this,android.R.layout.simple_list_item_1, stringArray);
			
			// Assign adapter to ListView
			listView.setAdapter(dataAdapter);
			Log.d(TAG, "ListView for ground truth created");
		}
		
	}

	private BroadcastReceiver receiverMainActivity = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			Bundle bundle = intent.getExtras();
			
			if (bundle != null) {
				
				if (intent.getAction().equals(Globals.PREDICTION_CHANGED_INTENT)) {
					setText(bundle.getString(Globals.NEW_PREDICTION_STRING));
				}
				
				if (intent.getAction().equals(Globals.PREDICTION_ENTROPY_INTENT)) {
					setEntropyText(bundle.getDouble(Globals.PREDICTION_ENTROPY_VALUE));
				}
			} 
			
			if (intent.getAction().equals(Globals.CLASS_NAMES_SET)) {
				
				Log.d(TAG, "Class names changed, update the GT Logger");
				
				// Rebuild the GT Logger when a new class was incorporated:
				String[] tmpStringArray = Globals.getStringArrayPref(context, Globals.CONTEXT_CLASSES);
				
				if (tmpStringArray != null) {

					contextClasses = tmpStringArray;

					createListView(contextClasses);

				} else {
					Log.e(TAG, "String array of context classes could not be read in MainActivity");
				}
				
			}
			
			if (intent.getAction().equals(Globals.CLASSES_BEING_ADDED_INTENT)) {
				
				Log.i(TAG, "Pending classes changed, update the GT Logger");
				
				createListView(contextClasses);
				
			}
			
			
		}
	};
	
	/*
	 * Custom adapter for the ground truth selection list view
	 * 
	 * Code similar to: http://www.mysamplecode.com/2012/07/android-listview-checkbox-example.html
	 */
	private class GtSelectorAdapter extends ArrayAdapter<String> {

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
		 
		   if (convertView == null) {
			   LayoutInflater vi = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			   
			   convertView = vi.inflate(R.layout.listview_element_checkbox, null);
			 
			   holder = new ViewHolder();
			   holder.checkBox = (CheckBox) convertView.findViewById(R.id.checkBox1);
			   convertView.setTag(holder);		   
			   
			   holder.checkBox.setOnClickListener( new View.OnClickListener() {  
			    	
				   public void onClick(View v) {  
				    	 
					   CheckBox cb = (CheckBox) v;
					   String contextClass = cb.getText().toString();
					   
//					   // Find position of this string in the contextClasses array:
//					   int idx = -1;
//					   for(int i=0; i<contextClasses.length; i++) {
//						   if (contextClass.equals(contextClasses[i])) {
//							   idx = i;
//						   }
//					   }
					   // Find position of this string in the contextClasses array:
					   int idx = -1;
					   for(int i=0; i<contextList.size(); i++) {
						   if (contextClass.equals(contextList.get(i))) {
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
							   cbStatus.set(idx, true);
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
							   cbStatus.set(idx, false);
						   }
						   
					   }
				      
				   }  
			   });  
		   } else {
			   holder = (ViewHolder) convertView.getTag();
		   }

		   String string = contextList.get(position);
		   holder.checkBox.setText(string);
		   holder.checkBox.setTextSize(18);
		   
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
				
			double time = (System.currentTimeMillis() - Globals.RECORDING_START_TIME) / 1000.0;
			
			try {
				File file = new File(Globals.getLogPath(), Globals.GT_LOG_FILENAME);
				FileWriter f = new FileWriter(file, true);
				f.write(time + "\t" + contextClass + "\t" +  startOrEnd + "\n");
				f.close();
			} catch (IOException e) {
				Log.e(TAG, "Writing to GT log file failed");
				e.printStackTrace();
			}
		}
	}
	
	private void appendToMaxQueryLog(int newValue) {
		
		Calendar cal = Calendar.getInstance();
		Date currentLocalTime = cal.getTime();
		DateFormat date = new SimpleDateFormat("yyyMMdd HH:mm");
		String dateString = date.format(currentLocalTime);
		
		
		try {
			File file = new File(Globals.getLogPath(), Globals.MAX_QUERY_LOG_FILENAME);
			FileWriter f = new FileWriter(file, true);
			f.write(dateString + "\t" + newValue + "\n");
			f.close();
		} catch (IOException e) {
			Log.e(TAG, "Writing to AL log file failed");
			e.printStackTrace();
		}
	}
	
	private void setFirstRun() {
		if(FIRST_RUN == true) {
			FIRST_RUN = false;
		}
	}
	
	/*
	 * Copy the JSON file containing the GMM to the external storage
	 * 
	 * Code from http://stackoverflow.com/questions/4447477/android-how-to-copy-files-in-assets-to-sdcard
	 */
	private void copyAssetFile(String filename) {
		
		AssetManager assetManager = getAssets();
		InputStream in = null;
		OutputStream out = null;
		
		try {
			in = assetManager.open(filename);
			File outFile = new File(Globals.APP_PATH, filename);
			out = new FileOutputStream(outFile);

			byte[] buffer = new byte[1024];
			int read;
			while ((read = in.read(buffer)) != -1) {
				out.write(buffer, 0, read);
			}

			in.close();
			in = null;
			out.flush();
			out.close();
			out = null;
		} catch (IOException e) {
			Log.e(TAG, "Failed to copy asset file: " + filename, e);
		}
	}

}
