package ch.ethz.wearable.contextrecognition.activities;

//import android.app.ActionBar;
import com.example.contextrecognition.R;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RatingBar;

public class Rating extends ActionBarActivity {
    
	Button submitButton;
	RatingBar ratingBarAccuracy;
	RatingBar ratingBarUsefulness;
	EditText feedbackText;
	final Context context = this;
	
	@Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rating);
 
        // get action bar   
        ActionBar actionBar = getSupportActionBar();
 
        // Enabling Up / Back navigation
        actionBar.setDisplayHomeAsUpEnabled(true);
        
        addListenerOnButton();
        
        
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
            //Go to settings activity
        	callLabel();
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
     * Launch Settings activity
     * */
    private void callSettings() {
        Intent i = new Intent(Rating.this, Settings.class);
        startActivity(i);
    }
    /**
     * Launch Label activity
     * */
    private void callLabel() {
        Intent i = new Intent(Rating.this, Diary.class);
        startActivity(i);
    }
    /**
     * Launch Help activity
     * */
    private void callHelp() {
        Intent i = new Intent(Rating.this, Help.class);
        startActivity(i);
    }
    
	public void addListenerOnButton() {
		 
		submitButton = (Button) findViewById(R.id.submitButton);
		ratingBarAccuracy = (RatingBar) findViewById(R.id.ratingBarAccuracy);
		ratingBarUsefulness = (RatingBar) findViewById(R.id.ratingBarUsefulness);
		feedbackText = (EditText) findViewById(R.id.feedbackText);
		
		submitButton.setOnClickListener(new OnClickListener() {
 
			@Override
			public void onClick(View arg0) {
				float accuracyRating = ratingBarAccuracy.getRating();
				float usefulnessRating = ratingBarUsefulness.getRating();
				String feedbackString = feedbackText.getText().toString();
				
				//TODO: Call method to send feedback here
				
				// Show alert dialog to ask for full evaluation
		  		AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(context);
				
				alertDialogBuilder.setTitle("");
				alertDialogBuilder.setMessage(R.string.rating_ask_for_evaluation);
				
				alertDialogBuilder.setPositiveButton("Sure!", new DialogInterface.OnClickListener() {
					
					public void onClick(DialogInterface dialog, int whichButton) {

							//TODO: open new activity for the complete evaluation form
					  
					  }
				});
				
				alertDialogBuilder.setNegativeButton("No Thanks", new DialogInterface.OnClickListener() {
					
					  public void onClick(DialogInterface dialog, int whichButton) {
					    // Canceled
					  }
				});
				
				// create alert dialog
				AlertDialog alertDialog = alertDialogBuilder.create();
				
				// show it
				alertDialog.show(); 
				
			}
 
		});

 
	}
}
