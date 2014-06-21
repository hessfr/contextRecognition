package com.example.contextrecognition;

//import android.app.ActionBar;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;

public class Settings extends ActionBarActivity {
	
	SeekBar querySeekBar;
	TextView currentValueTV;
	private static final int queryDefault = 10; //TODO: change this to use the value defined in the welcome screen	
	private static final int queryLimit = 20;
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
 
        ActionBar actionBar = getSupportActionBar();
 
        // Enabling Up / Back navigation
        actionBar.setDisplayHomeAsUpEnabled(true);
        
        // TextView to show the current query limit value
        currentValueTV = (TextView) findViewById(R.id.currentSeekBarValue);
        currentValueTV.setText(String.valueOf(queryDefault));
        
        // SeekBar set-up
        querySeekBar = (SeekBar) findViewById(R.id.seekBar);
        querySeekBar.setMax(queryLimit);
        
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
            	
            	//TODO: call method to process this input
            	
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
        if (id == R.id.action_label) {
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
        
        return super.onOptionsItemSelected(item);
    }

    /**
     * Launch Label activity
     * */
    private void callLabel() {
        Intent i = new Intent(Settings.this, Label.class);
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
}
