package ch.ethz.wearable.contextrecognition.activities;

import ch.ethz.wearable.contextrecognition.tools.AppStatus;
import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

/*
 * http://stackoverflow.com/questions/4758462/android-finish-all-activities
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
		finish();
    }
}
