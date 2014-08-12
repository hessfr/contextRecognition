package ch.ethz.wearable.contextrecognition.activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import ch.ethz.wearable.contextrecognition.othersensors.RecService;
import ch.ethz.wearable.contextrecognition.utils.AppStatus;

/*
 * This class is used to stop the recording and finish the activity
 * 
 * Idea from http://stackoverflow.com/questions/4758462/android-finish-all-activities
 */
public class ShutdownActivity extends Activity {

	private static final String TAG = "ShutdownActivity";
	
	@Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

		// Stop the recording and finish the activity:
		AppStatus.getInstance().set(AppStatus.STOP_RECORDING);
		Log.i(TAG, "New status: stop recording");
		Log.i(TAG, "Finishing activity");
		
		stopService(new Intent(ShutdownActivity.this,RecService.class));
		
		finish();
    }
}
