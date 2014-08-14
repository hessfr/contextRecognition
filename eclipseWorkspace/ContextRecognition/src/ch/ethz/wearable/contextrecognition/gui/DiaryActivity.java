package ch.ethz.wearable.contextrecognition.gui;

import java.util.ArrayList;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;
import ch.ethz.wearable.contextrecognition.R;
import ch.ethz.wearable.contextrecognition.utils.Globals;

import com.viewpagerindicator.TitlePageIndicator;

public class DiaryActivity extends ActionBarActivity {
	
	private static final String TAG = "DiaryAcitivty";
	
	Context context = this;
	
	ViewPager viewPager;
    PagerAdapter adapter;
    
    String[] todayContextClasses;
    Integer[] todayCotalCounts;
	
	@Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // First check if classes loaded properly already, and finish activity if not:
        todayContextClasses = Globals.getStringArrayPref(context, Globals.CONTEXT_CLASSES);
        ArrayList<Integer> t = Globals.getIntListPref(context, Globals.CLASS_COUNTS);
        
        todayCotalCounts = new Integer[t.size()];
        t.toArray(todayCotalCounts);
        
        SharedPreferences mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
		int silenceCount = mPrefs.getInt(Globals.SILENCE_COUNTS, 0);
        
        boolean startViewPager = true;
        
        if ((todayContextClasses == null) || (t == null)) {
        	Log.w(TAG, "Diary activity not opened, because new classes not fully incorporated yet");
			Toast.makeText(context, (String) "Please wait until new class incorporated",
					Toast.LENGTH_LONG).show();
			
			startViewPager = false;
        	this.finish();
        }
        

        
        if ((todayContextClasses != null) && (t != null)) {
        	
            Integer[] totalCounts = new Integer[t.size()];
            t.toArray(totalCounts);
        	
            if (todayContextClasses.length != totalCounts.length) {
            	Log.w(TAG, "Diary activity not opened, because new classes not fully incorporated yet");
    			Toast.makeText(context, (String) "Please wait until new class incorporated",
    					Toast.LENGTH_LONG).show();
    			
    			startViewPager = false;
            	this.finish();
            }
        }

        if (startViewPager == true) {
            setContentView(R.layout.viewpager_main);
            
            viewPager = (ViewPager) findViewById(R.id.pager);
            // Pass results to ViewPagerAdapter Class
            
            adapter = new DiaryViewPagerAdapter(DiaryActivity.this, todayContextClasses, todayCotalCounts, silenceCount);
            // Binds the Adapter to the ViewPager
            viewPager.setAdapter(adapter);
            viewPager.setCurrentItem(adapter.getCount());
            
            //Bind the title indicator to the adapter
            TitlePageIndicator titleIndicator = (TitlePageIndicator) findViewById(R.id.titles);
            titleIndicator.setViewPager(viewPager);
            
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
        if (id == R.id.action_settings) {
            //Go to settings activity
        	callSettings();
        	return true;
        }
//        if (id == R.id.action_rating) {
//        	//Go to help activity
//            callRating();
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
			// Quit the app and stop the recording:
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
        Intent i = new Intent(DiaryActivity.this, SettingsActivity.class);
        startActivity(i);
    }
//    /**
//     * Launch Rating activity
//     * */
//    private void callRating() {
//        Intent i = new Intent(DiaryActivity.this, RatingActivity.class);
//        startActivity(i);
//    }
    /**
     * Launch Help activity
     * */
//    private void callHelp() {
//        Intent i = new Intent(DiaryActivity.this, HelpActivity.class);
//        startActivity(i);
//    }
    /**
     * Launch manage classes activity
     * */
    private void callManageClasses() {
        Intent i = new Intent(DiaryActivity.this, ManageClassesActivity.class);
        startActivity(i);
    }
    /**
     * Launch upload activity
     * */
    private void callUploadActivity() {
        Intent i = new Intent(DiaryActivity.this, UploadActivity.class);
        startActivity(i);
    }
    /**
     * Launch Shutdown activity to close app and stop recording
     * */
	@SuppressLint("InlinedApi")
	private void callShutdown() {
		Application app = getApplication();
	    Intent intent = new Intent(app, ShutdownActivity.class);
	    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
	    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
	    app.startActivity(intent);
    }    
}
