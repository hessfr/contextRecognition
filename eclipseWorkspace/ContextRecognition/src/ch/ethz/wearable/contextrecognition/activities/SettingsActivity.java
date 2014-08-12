package ch.ethz.wearable.contextrecognition.activities;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import ch.ethz.wearable.contextrecognition.R;
import ch.ethz.wearable.contextrecognition.utils.Globals;

public class SettingsActivity extends ActionBarActivity {
	
	private static final String TAG = "SettingsAcitivty";
	
	SeekBar querySeekBar;
	TextView currentValueTV;
	SharedPreferences mPrefs;
	private Context context = this;
	private static final int queryLimit = 20;
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
 
        setContentView(R.layout.activity_settings);
 
        ActionBar actionBar = getSupportActionBar();
 
        // Enabling Up / Back navigation
        actionBar.setDisplayHomeAsUpEnabled(true);
        
        mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        
        int currentValue = mPrefs.getInt(Globals.MAX_NUM_QUERIES, -1);
		if (currentValue == -1) {
			Log.e(TAG, "Got invalid value from preference, change to default value instead (only for GUI)");
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
    			
    			appendToMaxQueryLog(newValue);
    			
    			Intent intent = new Intent(Globals.MAX_QUERY_NUMBER_CHANGED);
    			sendBroadcast(intent);    			
            }
        });
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
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        //Create the options entry in the ActionBar
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	//Handle ActionBar clicks
        int id = item.getItemId();
        if (id == R.id.action_diary) {
        	//Go to rating activity
        	callLabel();
        	return true;
        }
//        if (id == R.id.action_rating) {
//        	//Go to rating activity
//        	callRating();
//        	return true;
//        }
//        if (id == R.id.action_help) {
//        	//Go to help activity
//            callHelp();
//        	return true;
//        }
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
     * Launch Label activity
     * */
    private void callLabel() {
        Intent i = new Intent(SettingsActivity.this, DiaryActivity.class);
        startActivity(i);
    }
//    /**
//     * Launch Rating activity
//     * */
//    private void callRating() {
//        Intent i = new Intent(SettingsActivity.this, RatingActivity.class);
//        startActivity(i);
//    }
    /**
     * Launch Help activity
     * */
//    private void callHelp() {
//        Intent i = new Intent(SettingsActivity.this, HelpActivity.class);
//        startActivity(i);
//    }
    /**
     * Launch manage classes activity
     * */
    private void callManageClasses() {
        Intent i = new Intent(SettingsActivity.this, ManageClassesActivity.class);
        startActivity(i);
    }
    /**
     * Launch upload activity
     * */
    private void callUploadActivity() {
        Intent i = new Intent(SettingsActivity.this, UploadActivity.class);
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
}
