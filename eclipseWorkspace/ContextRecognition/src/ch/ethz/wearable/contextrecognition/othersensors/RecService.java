//package ch.ethz.wearable.contextrecognition.othersensors;
//
//import com.AudioLogger.Globals;
//import com.AudioLogger.Recording.Worker.AccelerationWorker;
//import com.AudioLogger.Recording.Worker.CommentWorker;
//import com.AudioLogger.Recording.Worker.ErrorWorker;
//import com.AudioLogger.Recording.Worker.GyroWorker;
//import com.AudioLogger.Recording.Worker.LablerWorker;
//import com.AudioLogger.Recording.Worker.LocationWorker;
//import com.AudioLogger.Recording.Worker.SoundWorker;
//
//import android.app.Service;
//import android.content.Context;
//import android.content.Intent;
//import android.location.LocationManager;
//import android.os.IBinder;
//import android.widget.Toast;
//
//public class RecService extends Service {
//	// general
//	private Context context;
//
//	Monitoring monitor;
//
//	@Override
//	public void onCreate() {
//		context = this;
//		
//		Toast.makeText(this, "Service Created", Toast.LENGTH_SHORT).show();
//		startThreads();
//		super.onCreate();
//
//	}
//
//	private void startThreads() {
//		// Audio
//		if (Globals.experiment.logAudio)
//			Globals.soundWorker = new SoundWorker(context);
//
//		// Location
//		if (Globals.experiment.logNetwork)
//			Globals.locationNetworkWorker = new LocationWorker(context,
//					LocationManager.NETWORK_PROVIDER);
//
//		if (Globals.experiment.logGPS)
//			Globals.locationGPSWorker = new LocationWorker(context,
//					LocationManager.GPS_PROVIDER);
//
//		// Labler
//		Globals.lablerWorker = new LablerWorker(context);
//
//		// ErrorLog
//		Globals.errorworker = new ErrorWorker(context);
//
//		// Monitoring
//		monitor = new Monitoring(context);
//
//		// Comments
//		Globals.commentworker = new CommentWorker(context);
//		// Gyro
//		if (Globals.experiment.loggyro)
//			Globals.gyroWorker = new GyroWorker(context);
//		// Acceleration
//		if (Globals.experiment.logacc)
//			Globals.accelerationWorker = new AccelerationWorker(context);
//
//	}
//
//	private void stopThreads() {
//		// Monitoring
//		if (monitor != null)
//			monitor.stop();
//
//		// audio
//		if (Globals.experiment.logAudio)
//			Globals.soundWorker.stopSign();
//
//		// location
//		if (Globals.experiment.logNetwork)
//			Globals.locationNetworkWorker.stop();
//		if (Globals.experiment.logGPS)
//			Globals.locationGPSWorker.stop();
//
//		// Labler
//		Globals.lablerWorker.finish();
//		// Acceleration
//		if (Globals.experiment.logacc)
//			Globals.accelerationWorker.stop();
//		// Gyro
//		if (Globals.experiment.loggyro)
//			Globals.gyroWorker.stop();
//
//	}
//
//	@Override
//	public IBinder onBind(Intent arg0) {
//		return null;
//	}
//
//	public int onStart(Intent intent, int flags, int startId) {
//		Toast.makeText(this, "Service Started", Toast.LENGTH_SHORT).show();
//		return startId;
//	}
//
//	@Override
//	public void onDestroy() {
//		Toast.makeText(this, "Service Destroyed", Toast.LENGTH_SHORT).show();
//
//		stopThreads();
//	}
//}
