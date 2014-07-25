package ch.ethz.wearable.contextrecognition.utils;

import java.util.TimerTask;

import android.content.Context;

/*
 * Custom timer task, that accepts several input parameter
 */
public class CustomTimerTask extends TimerTask {
    String filenameOnServer;
    Context context;
    long pollingInterval;
    long maxRetries;

    public CustomTimerTask(Context context, String filenameOnServer, long pollingInterval, long maxRetries,
    		String newClassName, String feasibilityCheckResult, String[] contextClasses) {
        this.filenameOnServer = filenameOnServer;
        this.context = context;
        this.pollingInterval = pollingInterval;
        this.maxRetries = maxRetries;
    }

    @Override
    public void run() {
        
    }
}
