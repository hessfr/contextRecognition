package ch.ethz.wearable.contextrecognition.activities;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;

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
import ch.ethz.wearable.contextrecognition.audio.AudioWorker;
import ch.ethz.wearable.contextrecognition.utils.AppStatus;
import ch.ethz.wearable.contextrecognition.utils.Globals;
import ch.ethz.wearable.contextrecognition.welcomescreens.WelcomeActivity;

import com.example.contextrecognition.R;

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
		
		Log.i(TAG, "MainActivity OnCreate");
		
		mPrefs = PreferenceManager.getDefaultSharedPreferences(this);

		Boolean welcomeScreenShown = mPrefs.getBoolean(welcomeScreenShownPref,
				false);

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

		if (FIRST_RUN == true) {
			Log.i(TAG, "First run of MainActivity");

			// Start the AudioWorker service:
			Intent i = new Intent(this, AudioWorker.class);
			startService(i);

			// Set app status to initializing:
			AppStatus.getInstance().set(AppStatus.INIT);
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
			
			Log.i(TAG, "contextClasses string array inthe preferences was empty, so StateManager is push to prefs again");
			
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
		filterMain.addAction(Globals.CLASS_NAMES_SET);
		registerReceiver(receiverMainActivity, filterMain);
		
		// Set the prediction TextView to the current prediction (workaround!)
		String s = mPrefs.getString(Globals.CURRENT_CONTEXT, "");
		setText(s);
		
		/*
		 * If we stopped the recording and left the app and come back now, we want to start
		 * recording again:
		 */
		if (AppStatus.getInstance().get() == AppStatus.getInstance().STOP_RECORDING) {
			Intent i = new Intent(this, AudioWorker.class);
			startService(i);
			AppStatus.getInstance().set(AppStatus.INIT);
			Log.i(TAG, "New status: init");
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
			// Quit the app and stop the recording:
			callShutdown();
		}
		if (id == R.id.action_manage_classes) {
			// Go to manage classes activity:
			callManageClasses();
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

	/**
	 * Launch Rating activity
	 * */
	private void callRating() {
		Intent i = new Intent(MainActivity.this, RatingActivity.class);
		startActivity(i);
	}

	/**
	 * Launch Help activity
	 * */
	private void callHelp() {
		Intent i = new Intent(MainActivity.this, HelpActivity.class);
		startActivity(i);
	}
    /**
     * Launch manage classes activity
     * */
    private void callManageClasses() {
		Intent intent = new Intent(Globals.CALL_MANAGE_CLASSES_INTENT);
		Bundle bundle = new Bundle();
		intent.putExtras(bundle);
		sendBroadcast(intent);
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

			@Override
			public void onClick(View arg0) {
				
				if (AppStatus.getInstance().get() != AppStatus.getInstance().MODEL_ADAPTION) {
					
					Intent intent = new Intent(Globals.CALL_CONTEXT_SELECTION_INTENT);
	    			Bundle bundle = new Bundle();
	    			intent.putExtras(bundle);
	    			sendBroadcast(intent);
	    			
				} else {
					
					Toast.makeText(context, (String) "Please wait until previous model adaption finished",
							Toast.LENGTH_LONG).show();
					
					Log.i(TAG, "Model adaption request ignored, as previous model adaption still in progress");
				}
				

			}

		});

		confirmButton.setOnClickListener(new OnClickListener() {

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
			contextTV.setText("-");
			contextTV.setTextColor(getResources().getColor(R.color.silent_text_view));
			CONTEXT_CLASS_STRING = str;
		} else {
			contextTV.setText(str);
			contextTV.setTextColor(getResources().getColor(R.color.normal_text_view));
			CONTEXT_CLASS_STRING = str;
		}
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

		// Initialize the ground truth array the very first time:
		if (currentGT == null) {
			Log.d(TAG, "Initializing currentGT array");
			currentGT = new Boolean[stringArray.length];
			for(int j=0; j<currentGT.length; j++) {
				currentGT[j] = false;
			}
		}
		
		// When a context class was added, increase the size of the currentGT array:
		if (currentGT.length != stringArray.length) {
			Boolean[] tmp = new Boolean[currentGT.length];
			for(int i=0; i<currentGT.length; i++) {
				tmp[i] = currentGT[i];
			}
			
			currentGT = new Boolean[stringArray.length];
			for(int i=0; i<stringArray.length ; i++) {
				if (i<(currentGT.length-1)) {
					currentGT[i] = tmp[i];
				} else {
					currentGT[i] = false;
				}			
			}
		}
		
		Log.d(TAG, "increasing length of currentGT done");
		
		if (stringArray != null) {
			ArrayList<String> contextList = new ArrayList<String>(Arrays.asList(stringArray));
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

		if (recordingInitialized == false) { // TODO: check if we still need this if clause!!
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
