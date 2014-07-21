package ch.ethz.wearable.contextrecognition.activities;

//import android.app.ActionBar;
import ch.ethz.wearable.contextrecognition.audio.AudioWorker;
import ch.ethz.wearable.contextrecognition.utils.AppStatus;
import ch.ethz.wearable.contextrecognition.utils.Globals;

import com.example.contextrecognition.R;

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

public class Settings extends ActionBarActivity {
	
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
    			
    			Intent intent = new Intent(Globals.MAX_QUERY_NUMBER_CHANGED);
    			
    			sendBroadcast(intent);    			
            }
        });
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
			// Quit the app and stop the recording:
			callShutdown();
		}
        
        return super.onOptionsItemSelected(item);
    }

    /**
     * Launch Label activity
     * */
    private void callLabel() {
        Intent i = new Intent(Settings.this, Diary.class);
        startActivity(i);
    }
    /**
     * Launch Rating activity
     * */
    private void callRating() {
        Intent i = new Intent(Settings.this, Rating.class);
        startActivity(i);
    }
    /**
     * Launch Help activity
     * */
    private void callHelp() {
        Intent i = new Intent(Settings.this, Help.class);
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
