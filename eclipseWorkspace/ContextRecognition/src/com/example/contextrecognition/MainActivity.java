package com.example.contextrecognition;

import java.util.LinkedList;

import org.ejml.data.DenseMatrix64F;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;
import android.widget.TextView;

import com.example.tools.Classifier;
import com.example.tools.FeaturesExtractor;
import com.example.tools.GMM;
import com.example.tools.Mfccs;
import com.example.tools.SoundHandler;

public class MainActivity extends ActionBarActivity {

	private static final String TAG = "MainAcitivty";
	
	
	String[] contextClasses = {"Context Class 1", "Context Class 2", "Context Class 3", 
			   "Context Class 4", "Define new class"};
	ImageButton changeButton;
	ImageButton confirmButton;
	SharedPreferences mPrefs;
	TextView contextTV;
	final String welcomeScreenShownPref = "welcomeScreenShown";
	
	private Handler guiHandler = new Handler();
	
	// Objects needed for classifications:
	private GMM gmm;
	private Classifier clf;
	private SoundHandler soundHandler;
	private FeaturesExtractor featuresExtractor;
	private LinkedList<double[]> mfccList;
	
	private String currentContext;
	
	ModelAdaptor modelAdaptor = new ModelAdaptor(); //TODO: delete??????
	 
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
	    mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
	    
	    Boolean welcomeScreenShown = mPrefs.getBoolean(welcomeScreenShownPref, false);

	    if (!welcomeScreenShown) {
	    	//Open the welcome activity if it hasn't been shown yet

	    	Log.i(TAG, "Welcome screen already shown before, going to MainActivity instead");

	    	SharedPreferences.Editor editor = mPrefs.edit();
	        editor.putBoolean(welcomeScreenShownPref, true);
	        editor.commit();
	    	
	    	Intent i = new Intent(MainActivity.this, Welcome1.class);
	    	i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_NO_ANIMATION);
	        startActivity(i);
	    } 
    	
	    addListenerOnButton();
	    contextTV = (TextView) findViewById(R.id.contextTV);
	    currentContext = new String();
	    
	    // Initialize objects needed for classification
	    mfccList = new LinkedList<double[]>();
		featuresExtractor = new FeaturesExtractor();
		soundHandler = new SoundHandler();
		clf = new Classifier();
		gmm = new GMM("jsonGMM.json"); //TODO
		
		//Start recording when the app is started
		startRec();
    }
    
    // ---------- Some methods for the user interface: ----------
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	//Create the options entry in the ActionBar
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	//Handle ActionBar selections
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            //Go to settings activity
        	callSettings();
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
        	endRec();
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
 
				Intent i = new Intent(MainActivity.this, ContextSelection.class);
		        startActivity(i);
 
			}
 
		});
		
		confirmButton.setOnClickListener(new OnClickListener() {
			 
			@Override
			public void onClick(View arg0) {
 
				modelAdaptor.currentClassCorrect();
 
			}
 
		});
 
	}

	public void setText(String str) {
		contextTV.setText(str);
	}
	
	// ---------- Methods for classification: ----------
	
	public void startRec() {

		soundHandler = new SoundHandler() {

			@Override
			protected void handleData(short[] data, int length, int frameLength) {
				
				//length of data has to be 1024 to match our 32ms windows, 64512 to match 63 32ms windows
				if (data.length != 64512) {
					Log.e(TAG,"data sequence has wrong length, aborting calculation");
					return;
				}
				
				// Call handle data from SoundHandler class
				super.handleData(data, length, frameLength);

				// Loop through the audio data and extract our features for each 32ms window
				
				for(int i=0; i<63; i++) {
					
					// Split the data into 32ms chunks (equals 1024 elements)
					short[] tmpData = new short[1024];
					System.arraycopy(data, i*1024, tmpData, 0, 1024);
					
					Mfccs currentMFCCs = featuresExtractor.extractFeatures(tmpData);
					mfccList.add(currentMFCCs.get());
				}
				
				// If we have 2 seconds of data, call our prediction method and clear the List afterwards again 
				if (mfccList.size() == 63) {
					// Convert data to DenseMatrix:
					double[][] array = new double[mfccList.size()][12]; // TODO: n_features instead of 12
					for (int i=0; i<mfccList.size(); i++) {
					    array[i] = mfccList.get(i);
					}
					DenseMatrix64F samples = new DenseMatrix64F(array);

					DenseMatrix64F res = clf.predict(gmm, samples);
					
					int tmp = (int) res.get(10); // TODO: implement this properly! As this array is exactly the length of one majority vote window, all elements in it are the same 
					
					currentContext = gmm.get_class_name(tmp);
					
					Log.i(TAG, "Current Context: " + currentContext);
					
					// Delete all elements of the list afterwards
					mfccList.clear();
				}
				
				guiHandler.post(new Runnable() {
				@Override
				public void run() {
					
					setText(currentContext);
					
					}
				});

			}

		};

		soundHandler.beginRec();
	}

	private void endRec() {
		soundHandler.endRec();
	}	
}
