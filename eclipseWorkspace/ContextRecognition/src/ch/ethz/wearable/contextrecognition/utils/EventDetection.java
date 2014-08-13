package ch.ethz.wearable.contextrecognition.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Timer;
import java.util.TimerTask;

import android.app.AlarmManager;
import android.app.PendingIntent;
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
    private AlarmManager amLunchBreakStart;
    private AlarmManager amWorkingOvertimeStart;
	
    private Context context = this;
	@Override
    public void onCreate() {
		
		
		// Initialize the timer for the event detection:
        if(timer != null) {
        	
        	timer.cancel();
        } else {
        	
        	timer = new Timer();
        }
        timer.scheduleAtFixedRate(new CheckEventsTimerTask(context), 0, Globals.EVENT_DETECTION_INTERVAL);
    
        /*
         *  Initialize the alarm manager to get the current values at certain times. We
         *  need this that we can how often certain events where predicted in certain time
         *  period (e.g. to detect how often restaurant was predicted between 11am and 3pm
         *  to recognize the duration of the lunch break)
         */
        // ---- For long lunch break ----- 
		Calendar calLunchBreakStart = Calendar.getInstance();
		calLunchBreakStart.set(Calendar.HOUR_OF_DAY, 11); //TODO: change back to 11
		calLunchBreakStart.set(Calendar.MINUTE, 00); //TODO: change back to 0
	    
	    // If App started after 11:00, the alarm would go off, so we have to add one day in that case
	    long lunchBreakStart = 0;
	    if(calLunchBreakStart.getTimeInMillis() <= System.currentTimeMillis()) {
	    	lunchBreakStart = calLunchBreakStart.getTimeInMillis() + (AlarmManager.INTERVAL_DAY+1);
	    } else {
	    	lunchBreakStart = calLunchBreakStart.getTimeInMillis();
	    }
	    
	    Intent intentLunch = new Intent(Globals.EVENT_START_TIME_LUNCH);
        PendingIntent piLunch = PendingIntent.getBroadcast(context, 0, intentLunch, 0);
        
        amLunchBreakStart = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        amLunchBreakStart.setRepeating(AlarmManager.RTC_WAKEUP, lunchBreakStart, 
        		AlarmManager.INTERVAL_DAY, piLunch);
	    
        
        
        
        
     // ---- For working overtime -----
		Calendar calOvertimeStart = Calendar.getInstance();
		calOvertimeStart.set(Calendar.HOUR_OF_DAY, 19);
		calOvertimeStart.set(Calendar.MINUTE, 00);
        
		// If App started after 11:00, the alarm would go off, so we have to add one day in that case
	    long overtimeStart = 0;
	    if(calOvertimeStart.getTimeInMillis() <= System.currentTimeMillis()) {
	    	overtimeStart = calOvertimeStart.getTimeInMillis() + (AlarmManager.INTERVAL_DAY+1);
	    } else {
	    	overtimeStart = calOvertimeStart.getTimeInMillis();
	    }
	    
	    Intent intentOvertime = new Intent(Globals.EVENT_START_TIME_OVERTIME);
        PendingIntent piOvertime = PendingIntent.getBroadcast(context, 0, intentOvertime, 0);
        
        amWorkingOvertimeStart = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        amWorkingOvertimeStart.setRepeating(AlarmManager.RTC_WAKEUP, overtimeStart, 
        		AlarmManager.INTERVAL_DAY, piOvertime);
	
        
	
	}
	
	
	
	// TODO make sure that events are only fired once a day!!
	
	
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
			
			if (totalRecTime >= MIN_RECORDING_TIME) {
			
				if (longLunchBreak(classCounts, classNames)) {
					//TODO
				}
				if (weekendWork(classCounts, classNames)) {
					//TODO
				}
				if (workingOvertime(classCounts, classNames)) {
					//TODO
				}
				if (fewConversations(classCounts, classNames, totalRecTime)) {
					//TODO
				}
				if (makingTrip(classCounts, classNames)) {
					//TODO
				}

				if (littleSilenceTime(silenceCount, totalRecTime)) {
					//TODO
				} 
			} else {
				Log.i(TAG, "Total recording time too short");
			}
			

		}
	}
	private boolean longLunchBreak(ArrayList<Integer> classCounts, String[] classNames) {
		
		final int MIN_TIME = 1 * 3600; // = 1h (in seconds)
		
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		int restStartCount = prefs.getInt(Globals.LUNCH_BREAK_START_COUNT, -1);
		
		if (!isWeekend()) {
			
			if (restStartCount != -1) {
				
				/*
				 *  Check if it's between 12am and 3pm:
				 *  (12am, because class has to be detected for at least 1h)
				 */
				
				Calendar cal = Calendar.getInstance();
				long now = cal.getTimeInMillis();
				cal.set(Calendar.HOUR_OF_DAY, 12);
				cal.set(Calendar.MINUTE, 0);
				long timeframeStart = cal.getTime().getTime(); //12am
				cal.set(Calendar.HOUR_OF_DAY, 15);
				cal.set(Calendar.MINUTE, 0);
				long timeframeEnd = cal.getTime().getTime(); // 3pm
				
				if((now >= timeframeStart) && (now <= timeframeEnd)) {
					
					int totalRestCount = classCounts.get(Arrays.asList(classNames).indexOf("Restaurant"));
					
					int relevantCount = totalRestCount - restStartCount;
					
					if (relevantCount < 0) {
						Log.e(TAG, "relevantCount in longLunchBreak method has invalid"
								+ " (negativ) value");
					}
					
					double time = relevantCount * PREDICTION_WINDOW;
					
					Log.i(TAG, "Restaurant time since 11am: " + time);
					
					if (time > MIN_TIME) {
						return true;
					} else {
						return false;
					}
				} else {
					
					Log.i(TAG, "It's not between 12am and 3pm now, long lunch break will"
							+ " be ignored");
					return false;
				}
				
			} else {
				
				Log.i(TAG, "LUNCH_BREAK_START_COUNT could not be loaded from preferences,"
						+ " detection of this event will be ignored");
				return false;
			}
			
		} else {
			
			Log.i(TAG, "Longer lunch break will not be considered on the weekend");
			return false;
		}
		
	}
	
	private boolean weekendWork(ArrayList<Integer> classCounts, String[] classNames) {
		
		final int MIN_TIME = 2 * 3600; // = 2h (in seconds)
		
		// Model has to be trained with office class:
		if (Arrays.asList(classNames).contains("Office")) {
			
			// Check if today is weekend:
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
		
		final int MIN_TIME = 20 * 60; // = 20min (in seconds)
		
		// Check if today is not weekend:
		if (!isWeekend()) {
			
			// Model has to be trained with office class:
			if (Arrays.asList(classNames).contains("Office")) {
				
				Calendar cal = Calendar.getInstance();
				long now = cal.getTimeInMillis();
				cal.set(Calendar.HOUR_OF_DAY, 19);
				cal.set(Calendar.MINUTE, 30);
				long timeframeStart = cal.getTime().getTime(); //12am
				
				if (now > timeframeStart) {
					
					SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
					int officeStartCount = prefs.getInt(Globals.WORKING_OVERTIME_START_COUNT, -1);
					
					if (officeStartCount != -1) {
						
						int totalOfficeCount = classCounts.get(Arrays.asList(classNames).indexOf("Office"));
						
						int relevantCount = totalOfficeCount - officeStartCount;
						
						if (relevantCount < 0) {
							Log.e(TAG, "relevantCount in workingOvertime method has invalid"
									+ " (negativ) value");
						}
						
						double time = relevantCount * PREDICTION_WINDOW;
						
						Log.i(TAG, "Office time since 19pm: " + time);
						
						if (time > MIN_TIME) {
							
							return true;
						} else {
							
							return false;
						}
						
					} else {
						
						Log.i(TAG, "WORKING_OVERTIME_START_COUNT could not be loaded from preferences,"
								+ " detection of this event will be ignored");
						return false;
					}
					
				} else {
					
					Log.i(TAG, "Working overtime will be ignored, as it's before 7:30pm");
					return false;
				}
				
			} else {
				
				Log.i(TAG, "Office class not in classifiers, working overtime will be ignored");
				return false;
			}
			
		} else {
			
			Log.i(TAG, "Working overtime is being ignored on weekends");
			return false;
		}
	
	}
	
	private boolean fewConversations(ArrayList<Integer> classCounts, 
			String[] classNames, double totalRecTime) {
		
		final int MAX_TIME = 15 * 60; // = 15min (in seconds)
		
		// Only consider this event if we have at least 4 hours of recording:
		final double MIN_TOTAL_REC_TIME = 4 * 3600; // = 4h (in seconds)
		if(totalRecTime > MIN_TOTAL_REC_TIME) {
			
			// Check if it's after 9pm:
			Calendar cal = Calendar.getInstance();
			long now = cal.getTimeInMillis();
			cal.set(Calendar.HOUR_OF_DAY, 21);
			cal.set(Calendar.MINUTE, 0);
			cal.set(Calendar.SECOND, 0);
			cal.set(Calendar.MILLISECOND, 0);
			long checkingTime = cal.getTime().getTime(); // 9pm
			
			if (checkingTime > now) {
				
				// Check if less then 15min of conversations was recorded:
				int convCount = classCounts.get(Arrays.asList(classNames).indexOf("Conversation"));
				double time = convCount * PREDICTION_WINDOW;
				if (time < MAX_TIME) {
					
					return true;
					
				} else {
					
//					Log.i(TAG, "More conversation recorded than the threshold value");
					return false;
				}
			} else {
				
				Log.i(TAG, "Not after 9pm, fewConversations event will be ignored");
				return false;
			}
			
		} else {
			
			Log.i(TAG, "Total recording time too short to detect few conversations event");
			return false;
		}
		
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
	
	/*
	 * At 9pm less than 5% of the time was detected as silence
	 */
	private boolean littleSilenceTime(int silenceCount, double totalRecTime) {
		
		final double MAX_RATIO = 0.05; // = 5%
		
		double silenceTime = silenceCount * PREDICTION_WINDOW;
		
		Calendar cal = Calendar.getInstance();
		long now = cal.getTimeInMillis();
		cal.set(Calendar.HOUR_OF_DAY, 21);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MILLISECOND, 0);
		long checkingTime = cal.getTime().getTime(); // 9pm
		
		if (checkingTime > now) {
			if ((silenceTime/totalRecTime) < MAX_RATIO) {
				
				return true;
				
			} else {
				return false;
			}
		} else {
			return false;
		}
		
		
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
