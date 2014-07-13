package com.example.contextrecognition;

//import android.app.ActionBar;
import java.util.ArrayList;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.animation.AccelerateDecelerateInterpolator;

import com.echo.holographlibrary.PieGraph;
import com.echo.holographlibrary.PieSlice;



public class Diary extends ActionBarActivity {
	
	private static final String TAG = "DiaryAcitivty";
	
	@Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_diary);
 
        // get action bar   
        ActionBar actionBar = getSupportActionBar();
 
        // Enabling Up / Back navigation
        actionBar.setDisplayHomeAsUpEnabled(true);

        String[] contextClasses = Globals.getStringArrayPref(this, Globals.CONTEXT_CLASSES);
        
        ArrayList<Integer> t = Globals.getIntListPref(this, Globals.CLASS_COUNTS);
        Integer[] totalCounts = new Integer[t.size()];
        t.toArray(totalCounts);

        createChart(totalCounts, contextClasses);

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
        if (id == R.id.action_rating) {
        	//Go to help activity
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
    
    private void createChart(Integer[] totalCounts, String[] contextClasses) {
    	
    	String[] colors = {"#41A317", "#CC6600", "#1569C7", "#008080", "#FFF380"}; //TODO
    	
    	// Sort the arrays:
    	//TODO
    	
    	PieGraph pg = (PieGraph) findViewById(R.id.piegraph);
    	
    	for(int i=0; i<totalCounts.length; i++) {
    		PieSlice slice = new PieSlice();
    		slice.setColor(Color.parseColor(colors[i]));
    		slice.setValue(1);
    		pg.addSlice(slice);
    	}

		int holeSize = 100;
        pg.setInnerCircleRatio(holeSize);	

        int j=0;
		for (PieSlice s : pg.getSlices()) {
			s.setGoalValue(totalCounts[j]);
			j++;
		}
            
        pg.setDuration(2000);
        pg.setInterpolator(new AccelerateDecelerateInterpolator());
        pg.animateToGoalValues();
        
        //TODO: create legend
    }
    
    /**
     * Launch Settings activity
     * */
    private void callSettings() {
        Intent i = new Intent(Diary.this, Settings.class);
        startActivity(i);
    }
    /**
     * Launch Rating activity
     * */
    private void callRating() {
        Intent i = new Intent(Diary.this, Rating.class);
        startActivity(i);
    }
    /**
     * Launch Help activity
     * */
    private void callHelp() {
        Intent i = new Intent(Diary.this, Help.class);
        startActivity(i);
    }
    
}
