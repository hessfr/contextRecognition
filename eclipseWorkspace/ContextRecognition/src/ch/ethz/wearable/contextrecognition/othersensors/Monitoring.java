//package ch.ethz.wearable.contextrecognition.othersensors;
//
//import java.io.BufferedWriter;
//import java.io.File;
//import java.io.FileWriter;
//import java.io.IOException;
//import java.util.Timer;
//import java.util.TimerTask;
//
//
//import android.content.BroadcastReceiver;
//import android.content.Context;
//import android.content.Intent;
//import android.content.IntentFilter;
//
//
//// TODO: do we need this class at all??
//
//
///**
// *  Monitor the battery level and write all data to files, stop the experiment if the battery gets too low
// * @author 
// *
// */
//public class Monitoring {
//
//	// Constants
//	final protected int PERIOD=1000*60*1;
//	final protected String SUFFIX="SystemMonitor";
//	final protected String EXT="txt";
//	final protected int SDFREELIMIT=100;
//	final protected int BATTERYLIMIT_WARNING=15; // bellow 15%
//	final protected int BATTERYLIMIT_STOP=7; // experiment will stop if the battery is below 7%
//
//	// Objects
//	Timer t;
//	File mFile;
//	Context context;
//	boolean first_notify_battery=true;
//	boolean first_notify_SD=true;
//
//	int BatteryLevel=-1;
//	int PowerSource=0; // 0 for battery other for other power sources;
//	int scale = 100;
//
//	private BroadcastReceiver mBatInfoReceiver = new BroadcastReceiver() {
//		@Override
//		public void onReceive(Context context, Intent intent) {			
//			BatteryLevel = intent.getIntExtra("level", 0);	// the current lbattery level, %
//			PowerSource= intent.getIntExtra("plugged", 0); // whether the device is plugged in
//			scale = intent.getIntExtra("scale", 100);
//		}
//	};
//
//
//	public Monitoring(Context c){
//		context=c;
//
//		mFile= Globals.createPrefFile(SUFFIX, EXT); // create SystemMonitor.txt file
//
//
//		if(!mFile.exists()){
//			try {
//				BufferedWriter out = new BufferedWriter(new FileWriter(mFile,true));
//				String outString= String.format("timestamp\tSDCardFree\tSDCardTotal\tBatteryLevel[%s]\tlogGPS\tlogNetwork\tAudio_fs\n", "%");
//				out.write(outString);
//				out.close();
//			} catch (IOException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}			
//		}
//
//		t=new Timer();
//		t.schedule(new UpdateTask(), 5000, PERIOD); // after 5 seconds, up
//
//		context.registerReceiver(mBatInfoReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
//
//
//	}
//
//	public void stop(){
//		context.unregisterReceiver(mBatInfoReceiver);
//		t.cancel();	
//	}
//
//
//	public class UpdateTask extends TimerTask {
//		@Override
//		public void run() {
//			long now= System.currentTimeMillis();
//			long sdtotal= Globals.getTotalSizeofSD();
//			long sdfree= Globals.getFreeSizeofSD();
//
//			// Notify User //first_notify_battery && first_notify_battery && 
//			if( (BatteryLevel * 100 / scale) < BATTERYLIMIT_WARNING && PowerSource==0){
//				first_notify_battery=false;
//				Globals.notifyUser(1,"BATTERY LOW!!", "Battery low", 
//						String.format("BLevel= %d%s. Experiment will stop at %d%s!", BatteryLevel, "%",BATTERYLIMIT_STOP,"%"), context);				
//			}
//			if(sdfree<SDFREELIMIT){
//				first_notify_battery=false;
//				Globals.notifyUser(2,"Low Free Space!!", "Low Free Space", String.format("SDCard Free Space is %dMB!", sdfree), context);				
//			}
//
//			if(BatteryLevel<BATTERYLIMIT_STOP && PowerSource==0) {			
//				Globals.guistartstop.stopExperiment();
//				Globals.notifyUser(1,"BATTERY LOW!EXPERIMENT STOPPED!", "Battery low", String.format("Experiment stopped!"), context);	
//			}
//
//
//			try {
//				BufferedWriter out = new BufferedWriter(new FileWriter(mFile,true));				
//				String outString= String.format("%d\t%d\t%d\t%d\t%s\t%s\t%d\n", now,sdfree,sdtotal,
//						BatteryLevel,Globals.experiment.logGPS, Globals.experiment.logNetwork,Globals.experiment.audio_frequency);
//
//				out.write(outString);
//				out.close();
//			} catch (IOException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
//
//		} 
//	}
//}
