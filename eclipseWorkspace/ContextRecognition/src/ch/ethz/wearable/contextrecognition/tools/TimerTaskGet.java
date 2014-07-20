package ch.ethz.wearable.contextrecognition.tools;

import java.util.TimerTask;

import android.content.Context;

public class TimerTaskGet extends TimerTask  {
    String filenameOnServer;
    Context context;
    long pollingInterval;
    long maxRetries;

    public TimerTaskGet(Context context, String filenameOnServer, long pollingInterval, long maxRetries) {
        this.filenameOnServer = filenameOnServer;
        this.context = context;
        this.pollingInterval = pollingInterval;
        this.maxRetries = maxRetries;
    }

    @Override
    public void run() {
        
    }
}
