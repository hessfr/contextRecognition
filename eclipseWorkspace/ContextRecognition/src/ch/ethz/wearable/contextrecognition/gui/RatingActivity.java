package ch.ethz.wearable.contextrecognition.gui;

//import android.app.ActionBar;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RatingBar;
import ch.ethz.wearable.contextrecognition.R;

public class RatingActivity extends ActionBarActivity {
    
	private static final String TAG = "RatingActivity";
	
	Button submitButton;
	RatingBar ratingBarAccuracy;
	RatingBar ratingBarUsefulness;
	EditText feedbackText;
	final Context context = this;
	
	@Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rating);
 
        Log.d(TAG, "onCreate");
        
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
		if (id == R.id.action_exit) {
			// Quit the app and stop the recording:
			callShutdown();
		}
		if (id == R.id.action_manage_classes) {
			// Go to manage classes activity:
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
        Intent i = new Intent(RatingActivity.this, SettingsActivity.class);
        startActivity(i);
    }
    /**
     * Launch Label activity
     * */
    private void callLabel() {
        Intent i = new Intent(RatingActivity.this, DiaryActivity.class);
        startActivity(i);
    }
    /**
     * Launch manage classes activity
     * */
    private void callManageClasses() {
        Intent i = new Intent(RatingActivity.this, ManageClassesActivity.class);
        startActivity(i);
    }
    /**
     * Launch upload activity
     * */
    private void callUploadActivity() {
        Intent i = new Intent(RatingActivity.this, UploadActivity.class);
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
    
	public void addListenerOnButton() {
		 
//		submitButton = (Button) findViewById(R.id.submitButton);
//		ratingBarAccuracy = (RatingBar) findViewById(R.id.ratingBarAccuracy);
//		ratingBarUsefulness = (RatingBar) findViewById(R.id.ratingBarUsefulness);
//		feedbackText = (EditText) findViewById(R.id.feedbackText);
//		
//		submitButton.setOnClickListener(new OnClickListener() {
// 
//			@SuppressWarnings("unused")
//			@Override
//			public void onClick(View arg0) {
//				float accuracyRating = ratingBarAccuracy.getRating();
//				float usefulnessRating = ratingBarUsefulness.getRating();
//				String feedbackString = feedbackText.getText().toString();
//				
//				//TODO: Call method to send feedback here
//				
//				// Show alert dialog to ask for full evaluation
//		  		AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(context);
//				
//				alertDialogBuilder.setTitle("");
//				alertDialogBuilder.setMessage(R.string.rating_ask_for_evaluation);
//				
//				alertDialogBuilder.setPositiveButton("Sure!", new DialogInterface.OnClickListener() {
//					
//					public void onClick(DialogInterface dialog, int whichButton) {
//
//							//TODO: open new activity for the complete evaluation form
//					  
//					  }
//				});
//				
//				alertDialogBuilder.setNegativeButton("No Thanks", new DialogInterface.OnClickListener() {
//					
//					  public void onClick(DialogInterface dialog, int whichButton) {
//					    // Canceled
//					  }
//				});
//				
//				// create alert dialog
//				AlertDialog alertDialog = alertDialogBuilder.create();
//				
//				// show it
//				alertDialog.show(); 
//				
//			}
// 
//		});

 
	}
}
