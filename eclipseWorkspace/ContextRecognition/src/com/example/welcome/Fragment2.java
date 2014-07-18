package com.example.welcome;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

import com.example.contextrecognition.R;
import com.example.tools.Globals;

public class Fragment2 extends Fragment {

	private static final String TAG = "Welcome2";
	
	SeekBar querySeekBar;
	TextView currentValueTV;
	private static final int queryLimit = 20;

	SharedPreferences mPrefs;
	final String welcomeScreenShownPref = "welcomeScreenShown";

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View v = inflater.inflate(R.layout.frag_welcome2, container, false);
    	
        mPrefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        
        int currentValue = mPrefs.getInt(Globals.MAX_NUM_QUERIES, -1);
		if (currentValue == -1) {
			Log.i(TAG, "Maximum number of queries set to default value, as there was not entry in the preferences yet");
			currentValue = 10;
		}        
		
        // TextView to show the current query limit value
        currentValueTV = (TextView) v.findViewById(R.id.currentSeekBarValue);
        currentValueTV.setText(String.valueOf(currentValue));
        
        // SeekBar set-up
        querySeekBar = (SeekBar) v.findViewById(R.id.seekBar);
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

            	int prevValue = mPrefs.getInt(Globals.PREV_MAX_NUM_QUERIES, -1);
            	if (prevValue == -1) {
            		// Set to zero after clicking the very first time, so that StateManager sets the new value properly
            		editor.putInt(Globals.PREV_MAX_NUM_QUERIES, 0); 
            	} else {
            		int newValue = mPrefs.getInt(Globals.MAX_NUM_QUERIES, -1);
            		editor.putInt(Globals.PREV_MAX_NUM_QUERIES, newValue);
            	}
            	
    			editor.putInt(Globals.MAX_NUM_QUERIES, newValue);
    			editor.commit();
    			
    			Log.i(TAG, "Preference commited, new value of MAX_NUM_QUERIES: " + newValue);
    			
    			Intent intent = new Intent(Globals.MAX_QUERY_NUMBER_CHANGED);
    			
    			getActivity().sendBroadcast(intent);    			
            }
        });
		
		
		return v;
	}

	public static Fragment2 newInstance(String text) {

		Fragment2 f = new Fragment2();
		Bundle b = new Bundle();
		b.putString("msg", text);

		f.setArguments(b);

		return f;
	}
}
