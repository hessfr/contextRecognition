package ch.ethz.wearable.contextrecognition.gui;

import java.util.Timer;
import java.util.TimerTask;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import ch.ethz.wearable.contextrecognition.R;
import ch.ethz.wearable.contextrecognition.utils.Globals;

public class QueryPopup extends Activity {

	Button okayButton;
	Button cancelButton;
	
	Context context = this;
	
	private static final String TAG = "QueryPopup";
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);
	    setContentView(R.layout.alert_dialog);
	    
	    addListenerOnButton();

	    // Start TimerTask to finish the activity if nothing happens within 60s:
		CustomTimerTask cancelQueryTask = new CustomTimerTask(context);
        Timer cancelTimer = new Timer();
        cancelTimer.schedule(cancelQueryTask, Globals.CANCEL_QUERY_TIME);
	    
	}
	
	/*
	 * When also want to stop the query (i.e. remove the notification) when users 
	 * clicks outside the pop-up in order to dismiss the request
	 * 
	 * Similar to: http://blog.twimager.com/2010/08/closing-activity-by-touching-outside.html
	 */
	@Override
	public boolean onTouchEvent ( MotionEvent event ) {
	  // I only care if the event is an UP action
	  if ( event.getAction () == MotionEvent.ACTION_UP ) {
	    // create a rect for storing the window rect
	    Rect r = new Rect ( 0, 0, 0, 0 );
	    // retrieve the windows rect
	    this.getWindow ().getDecorView ().getHitRect ( r );
	    // check if the event position is inside the window rect
	    boolean intersects = r.contains ( (int) event.getX (), (int) event.getY () );
	    // if the event is not inside then we can close the activity
	    
	    Log.i(TAG, "boolean: " + intersects);
	    
	    if ( !intersects ) {
	    	// Stop notification:
			NotificationManager notificationManager = (NotificationManager)
		            getSystemService(Context.NOTIFICATION_SERVICE);
			notificationManager.cancel(Globals.NOTIFICATION_ID_QUERY);
			// close the activity
			this.finish ();
			// notify that we consumed this event
			return true;
	    }
	  }
	  // let the system handle the event
	  return super.onTouchEvent ( event );
	}
	
    public void addListenerOnButton() {
		 
    	okayButton = (Button) findViewById(R.id.okayButton);
    	
    	cancelButton = (Button) findViewById(R.id.cancelButton);
 
    	okayButton.setOnClickListener(new OnClickListener() {
    		
    		@Override
			public void onClick(View arg0) {
    			
    			Log.d(TAG, "Click on okay button");
    			
				NotificationManager notificationManager = (NotificationManager)
			            getSystemService(Context.NOTIFICATION_SERVICE);
				notificationManager.cancel(Globals.NOTIFICATION_ID_QUERY);
				
				Intent i = new Intent(QueryPopup.this, ContextSelection.class);
				startActivity(i);
				
				QueryPopup.this.finish();
    		}
		});
		
    	cancelButton.setOnClickListener(new OnClickListener() {
			
    		@Override
			public void onClick(View arg0) {
    			
    			Log.d(TAG, "Click on cancel button");
    			
				NotificationManager notificationManager = (NotificationManager)
	            getSystemService(Context.NOTIFICATION_SERVICE);
				notificationManager.cancel(Globals.NOTIFICATION_ID_QUERY);
				
				QueryPopup.this.finish();
    		}
		});
    }
    
    /*
     * TimerTask to finish the activity if nothing happens within 60s
     */
	class CustomTimerTask extends TimerTask {
		
		Context context;
		
		public CustomTimerTask(Context c) {
			this.context = c;
		}
		public void run() {

			((Activity) context).finish();
			
		}
	}
}