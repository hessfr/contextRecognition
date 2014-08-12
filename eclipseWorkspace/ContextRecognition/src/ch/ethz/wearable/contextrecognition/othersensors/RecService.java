package ch.ethz.wearable.contextrecognition.othersensors;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.LocationManager;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

public class RecService extends Service {
	
	private static final String TAG = "RecService";
	
	// general
	private Context context;

//	Monitoring monitor;

	@Override
	public void onCreate() {
		
		Log.i(TAG, "RecService created");
		
		context = this;
		
		Toast.makeText(this, "Service Created", Toast.LENGTH_SHORT).show();
		startThreads();
		super.onCreate();

	}

	private void startThreads() {

		Config.locationNetworkWorker = new LocationWorker(context,
					LocationManager.NETWORK_PROVIDER);

		Config.locationGPSWorker = new LocationWorker(context,LocationManager.GPS_PROVIDER);

		Config.gyroWorker = new GyroWorker(context);
		
		Config.accelerationWorker = new AccelerationWorker(context);
		
//		// Labler
//		Globals.lablerWorker = new LablerWorker(context);

//		// ErrorLog
//		Globals.errorworker = new ErrorWorker(context);

		// Monitoring
//		monitor = new Monitoring(context);

//		// Comments
//		Globals.commentworker = new CommentWorker(context);
			
	}

	private void stopThreads() {
		// Monitoring
//		if (monitor != null)
//			monitor.stop();

		Config.locationNetworkWorker.stop();
		
		Config.locationGPSWorker.stop();
		
		Config.accelerationWorker.stop();
		
		Config.gyroWorker.stop();

		Log.i(TAG, "RecService threads stopped");	
	}

	@Override
	public IBinder onBind(Intent arg0) {
		return null;
	}

	public int onStart(Intent intent, int flags, int startId) {
		Toast.makeText(this, "Service Started", Toast.LENGTH_SHORT).show();
		return startId;
	}

	@Override
	public void onDestroy() {
		Toast.makeText(this, "Service Destroyed", Toast.LENGTH_SHORT).show();

		stopThreads();
	}
}
