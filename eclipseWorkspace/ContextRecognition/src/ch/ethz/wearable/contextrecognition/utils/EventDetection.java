package ch.ethz.wearable.contextrecognition.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Timer;
import java.util.TimerTask;

import android.app.AlarmManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

/*
 * Service that regularly check if unusual events happens, i.e. read the class count array
 * from the preferences and check if defined thresholds for the individual events are exceeded.
 * 
 */
public class EventDetection extends Service {

	private static final String TAG = "EventDetection";
	
	private static final double PREDICTION_WINDOW = 2.016; // in seconds
	private static final double MIN_RECORDING_TIME = 2 * 3600; // = 2h (in seconds)
    private Timer timer = null;
    private AlarmManager alarmManager;
	
    private Context context = this;
	@Override
    public void onCreate() {
		
        if(timer != null) {
        	timer.cancel();
        } else {
            // recreate new
        	timer = new Timer();
        }
        // schedule task
        timer.scheduleAtFixedRate(new CheckEventsTimerTask(context), 0, Globals.EVENT_DETECTION_INTERVAL);
    
	
	}
	
	
	
	// TODO stop this service if shutdown AppStatus signal received
	
	
	@Override
	public IBinder onBind(Intent arg0) {
		
		return null;
	}
	
	@Override
	public void onDestroy() {
		
		if(timer != null) {
        	timer.cancel();
		}
        	
	}
	
	class CheckEventsTimerTask extends TimerTask {
		
		Context context;
		
		public CheckEventsTimerTask(Context c) {
			this.context = c;
		}
		public void run() {

			Log.d(TAG, "CheckEventsTimerTask called");
			
			SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
			ArrayList<Integer> classCounts = Globals.getIntListPref(context, Globals.CLASS_COUNTS);
			String[] classNames = Globals.getStringArrayPref(context, Globals.CONTEXT_CLASSES);
			int silenceCount = prefs.getInt(Globals.SILENCE_COUNTS, 0);
			if (classCounts.size() != classNames.length) {
				Log.e(TAG, "Dimension mismatch!");
			}
			
			double totalRecTime = totalRecTime(classCounts, silenceCount);
			
//			if (totalRecTime >= MIN_RECORDING_TIME) {
			
				if (makingTrip(classCounts, classNames)) {
					//TODO
				}
				if (weekendWork(classCounts, classNames)) {
					//TODO
				}
				
				
				
				
//			}
			

		}
	}
	private boolean longLunchBreak(ArrayList<Integer> classCounts, String[] classNames) {
		
		//TODO
		
		return false;
	}
	
	private boolean weekendWork(ArrayList<Integer> classCounts, String[] classNames) {
		
		final int MIN_TIME = 2 * 3600; // = 2h (in seconds)
		
		// Model has to be trained with office class:
		if (Arrays.asList(classNames).contains("Office")) {
			
			// First check if today is a weekend:
			if (isWeekend()) {
				
				int count = classCounts.get(Arrays.asList(classNames).indexOf("Office"));
				
				double time = count * PREDICTION_WINDOW;
				
				if (time > MIN_TIME) {
					
					return true;
				} else {
					
					return false;
				}
				
			} else {
				
				return false;
			}
			
		} else {
			
			return false;
		}
		
		
	}
	
	private boolean workingOvertime(ArrayList<Integer> classCounts, String[] classNames) {
		
		//TODO
		
		return false;
	}
	
	private boolean fewConversations(ArrayList<Integer> classCounts, String[] classNames) {
		
		//TODO
		
		return false;
	}
	
	private boolean makingTrip(ArrayList<Integer> classCounts, String[] classNames) {
		
		final int MIN_TIME = 3 * 3600; // = 3h (in seconds)
		
		// Only check this if at least one of the context classes Car, Bus, Train are in the model:
		if (Arrays.asList(classNames).contains("Car") ||
			Arrays.asList(classNames).contains("Bus") ||
			Arrays.asList(classNames).contains("Train")) {

			int sum = 0;
			if (Arrays.asList(classNames).contains("Car")) {
				sum += classCounts.get(Arrays.asList(classNames).indexOf("Car"));
			}
			if (Arrays.asList(classNames).contains("Bus")) {
				sum += classCounts.get(Arrays.asList(classNames).indexOf("Bus"));
			}
			if (Arrays.asList(classNames).contains("Train")) {
				sum += classCounts.get(Arrays.asList(classNames).indexOf("Train"));
			}
			
			double time = sum * PREDICTION_WINDOW;
		
			if (time > MIN_TIME) {
				
				return true;
			} else {
				
				return false;
			}
		
		} else {
			
			return false;
		}
		
		
	}
	
	private boolean littleSilenceTime(ArrayList<Integer> classCounts, String[] classNames) {
		
		//TODO
		
		return false;
	}
	
	private double totalRecTime(ArrayList<Integer> classCounts, int silenceCount) {
		// Calculate the total recording time and only 
		int totalSum = 0;
		for (int el: classCounts) {
			totalSum += el;
		}
		totalSum += silenceCount;
		
		double totalTime = totalSum * PREDICTION_WINDOW;
		
		return totalTime;
	}
	
	private boolean isWeekend() {
		
		Calendar cal = Calendar.getInstance();
		int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
		
		if ((dayOfWeek == Calendar.SATURDAY) || (dayOfWeek == Calendar.SUNDAY)) {
			return true;
		} else {
			return false;
		}
		
	}

}
