package ch.ethz.wearable.contextrecognition.activities;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import org.apache.commons.lang3.ArrayUtils;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import ch.ethz.wearable.contextrecognition.R;
import ch.ethz.wearable.contextrecognition.utils.Globals;

import com.echo.holographlibrary.PieGraph;
import com.echo.holographlibrary.PieSlice;



public class DiaryActivity extends ActionBarActivity {
	
	private static final String TAG = "DiaryAcitivty";
	
	Context context = this;
	
	private static double PREDICTION_WINDOW = 2.016; // in seconds
	
	ListView legend;
	TextView recordingTimeTV;
	TextView silentTimeTV;
	
	@Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_diary);
 
        Log.d(TAG, "onCreate");
        
        // get action bar   
        ActionBar actionBar = getSupportActionBar();
 
        // Enabling Up / Back navigation
        actionBar.setDisplayHomeAsUpEnabled(true);

        String[] contextClasses = Globals.getStringArrayPref(this, Globals.CONTEXT_CLASSES);
        
        ArrayList<Integer> t = Globals.getIntListPref(this, Globals.CLASS_COUNTS);
        Integer[] totalCounts = new Integer[t.size()];
        t.toArray(totalCounts);

        legend = (ListView) findViewById(R.id.listView1);
        
        if (contextClasses.length == totalCounts.length) {
        	createChart(totalCounts, contextClasses);
        } else {
        	Log.w(TAG, "Diary activity not opened, because new classes not fully incorporated yet");
			Toast.makeText(this, (String) "Please wait until new class incorporated",
					Toast.LENGTH_LONG).show();
        	finish();
        }

        recordingTimeTV = (TextView) findViewById(R.id.recordingTime);
        silentTimeTV = (TextView) findViewById(R.id.silentTime);
        
        int totalPredSum = 0;
        for (int i : totalCounts) {
        	totalPredSum += i;
        }
        double totalPredTime = totalPredSum * PREDICTION_WINDOW;
        
        SharedPreferences mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
		int silenceCount = mPrefs.getInt(Globals.SILENCE_COUNTS, 0);
        double totalSilenceTime = silenceCount * PREDICTION_WINDOW;
        SimpleDateFormat df = new SimpleDateFormat("HH:mm");
        df.setTimeZone(TimeZone.getTimeZone("UTC"));
        
        double totalRecTime = totalPredTime + totalSilenceTime; // Total recording time in seconds
        
        Log.i(TAG, "total rec time: " + totalRecTime);
        
        Date totalRecDate = new Date((long) totalRecTime*1000);
        String totalRecTimeString = df.format(totalRecDate);
        recordingTimeTV.setText(totalRecTimeString + "h\nin total");

        Log.i(TAG, "total silences time: " + totalSilenceTime);
        
        Date totalSilenceDate = new Date((long) totalSilenceTime*1000);
        String silenceTimeString = df.format(totalSilenceDate);
        silentTimeTV.setText(silenceTimeString + "h\nsilences");
        
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
    
    private void createChart(final Integer[] t, String[] c) {
    	
    	String[] colors = {
    			
    			"#E9AB17", "#9DC209", "#C35817", "#966F33", "#566D7E",
    			"#F87217", "#728C00", "#E55451", "#827839",	"#3090C7", 
    			"#95B9C7", "#3BB9FF", "#4C4646", "#8EEBEC", "#78866B", 
    			"#CD7F32", "#52D017", "#966F33", "#566D7E", "#6F4E37", 
    			"#3090C7", "#438D80", "#566D7E", 

    			};
    			
    	
    	/*
    	 * From http://stackoverflow.com/questions/112234/sorting-matched-arrays-in-java
    	 */
    	Integer[] idx = new Integer[t.length];
    	for( int i = 0 ; i < idx.length; i++ ) idx[i] = i;              
    	Arrays.sort(idx, new Comparator<Integer>() {
    	    public int compare(Integer i1, Integer i2) {                        
    	        return Double.compare(t[i1], t[i2]);
    	    }                   
    	});
    	ArrayUtils.reverse(idx);
    	
    	Integer[] totalCounts = new Integer[t.length];
    	String[] contextClasses = new String[t.length];
    	
    	
    	for(int i=0; i<totalCounts.length; i++) {
    		totalCounts[i] = t[idx[i]];
    		contextClasses[i] = c[idx[i]];
    	}
    	
    	// Calculate the percentage of each context and append it to the strings:
    	int totalSum = 0;
    	for(int i=0; i<t.length; i++) {
    		totalSum += totalCounts[i];
    	}
    	for(int i=0; i<t.length; i++) {
    		double percentage = 100 * totalCounts[i] / ((double) totalSum);
    		contextClasses[i] = contextClasses[i] + " " + Math.round(percentage) + "%";
//    		contextClasses[i] = contextClasses[i] + " " + String.format("%.1f",percentage) + "%";
    	}

    	PieGraph pg = (PieGraph) findViewById(R.id.piegraph);
    	
    	for(int i=0; i<totalCounts.length; i++) {
    		PieSlice slice = new PieSlice();
			slice.setColor(Color.parseColor(colors[i]));
    		slice.setValue(1);
    		pg.addSlice(slice);
    	}

		int holeSize = 100;
        pg.setInnerCircleRatio(holeSize);	
		
		/*
		 *  Workaround to avoid the bug, that diagram is not displayed anymore,
		 *  if only one class:
		 */
        for(int i=0; i<totalCounts.length; i++) {
        	// Add a tiny dummy slice, if there would be only one class:
    		if (totalCounts[i] == 1) {
    			PieSlice slice = new PieSlice();
    			slice.setColor(Color.parseColor("#ffffff"));
    	        slice.setValue(0.001f);
    	        pg.addSlice(slice);
    		}
        }

        
		for(int i=0; i<pg.getSlices().size(); i++) {
			PieSlice s = pg.getSlice(i);
			if (i<totalCounts.length) {
				s.setGoalValue(totalCounts[i]);
			} else {
				// The dummy slice:
				s.setGoalValue(0.001f);
			}
			
		}
            
        pg.setDuration(2000);
        pg.setInterpolator(new AccelerateDecelerateInterpolator());
        pg.animateToGoalValues();
        
        
        // Create legend ListView:
        final ArrayList<String> list = new ArrayList<String>();
        for (int i = 0; i < contextClasses.length; ++i) {
          list.add(contextClasses[i]);
        }
        
        CustomListAdapter listAdapter = new CustomListAdapter(this, 
        		R.layout.legend_list_element, list, colors);
        legend.setAdapter(listAdapter);        
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
    
    
    /*
     * Code from: http://stackoverflow.com/questions/7361135/how-to-change-color-and-font-on-listview
     */
    @SuppressWarnings("rawtypes")
	private class CustomListAdapter extends ArrayAdapter {

        private Context mContext;
        private int id;
        private List <String>items ;
        private String[] mColors;

        @SuppressWarnings("unchecked")
		public CustomListAdapter(Context context, int textViewResourceId , List<String> list, String[] colors) 
        {
            super(context, textViewResourceId, list);      
            mColors = colors;
            mContext = context;
            id = textViewResourceId;
            items = list ;
        }

        @Override
        public View getView(int position, View v, ViewGroup parent)
        {
            View mView = v ;
            if(mView == null){
                LayoutInflater vi = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                mView = vi.inflate(id, null);
            }

            TextView text = (TextView) mView.findViewById(R.id.textView);
            
            if(items.get(position) != null )
            {
                text.setTextColor(Color.WHITE);
                text.setText(items.get(position));
                text.setBackgroundColor(Color.parseColor(mColors[position]));
                text.setTextSize(18);
            }

            return mView;
        }

    }
    
}
