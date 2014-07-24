package ch.ethz.wearable.contextrecognition.activities;

import java.util.ArrayList;

import android.app.Application;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ListView;

import com.example.contextrecognition.R;

public class ManageClassesActivity extends ActionBarActivity {
		
		private static final String TAG = "ManageClassesActivity";
		
//		ContextSelectorAdapter dataAdapter;
		ListView listView;
		ArrayList<Boolean> currentStatuses;
		static final String DEFINE_OWN_CLASS = "Define own context class";
		
	    @Override
	    protected void onCreate(Bundle savedInstanceState) {
	        super.onCreate(savedInstanceState);
	        setContentView(R.layout.activity_manage_classes);
	 
	        ActionBar actionBar = getSupportActionBar();
	 
	        Log.d(TAG, "onCreate");
	        
	        // Enabling backwards navigation
	        actionBar.setDisplayHomeAsUpEnabled(true);
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
	        if (id == R.id.action_settings) {
	            //Go to settings activity
	        	callSettings();
	        	return true;
	        }
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
			if (id == R.id.action_exit) {
				// Quit the app and stop the recording:
				callShutdown();
			}

	        return super.onOptionsItemSelected(item);
	    }
	    
	    /**
	     * Launch Settings activity
	     * */
	    private void callSettings() {
	        Intent i = new Intent(ManageClassesActivity.this, SettingsActivity.class);
	        startActivity(i);
	    }
	    /**
	     * Launch Settings activity
	     * */
	    private void callLabel() {
	        Intent i = new Intent(ManageClassesActivity.this, DiaryActivity.class);
	        startActivity(i);
	    }
	    /**
	     * Launch Rating activity
	     * */
	    private void callRating() {
	        Intent i = new Intent(ManageClassesActivity.this, RatingActivity.class);
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

