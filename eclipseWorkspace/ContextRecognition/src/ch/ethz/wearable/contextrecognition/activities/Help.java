package ch.ethz.wearable.contextrecognition.activities;

//import android.app.ActionBar;
import com.example.contextrecognition.R;

import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.content.Intent;
import android.os.Bundle;

public class Help extends ActionBarActivity {
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help);
 
        ActionBar actionBar = getSupportActionBar();
 
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

        return super.onOptionsItemSelected(item);
    }
    
    /**
     * Launch Settings activity
     * */
    private void callSettings() {
        Intent i = new Intent(Help.this, Settings.class);
        startActivity(i);
    }
    /**
     * Launch Settings activity
     * */
    private void callLabel() {
        Intent i = new Intent(Help.this, Diary.class);
        startActivity(i);
    }
    /**
     * Launch Rating activity
     * */
    private void callRating() {
        Intent i = new Intent(Help.this, Rating.class);
        startActivity(i);
    }
}
