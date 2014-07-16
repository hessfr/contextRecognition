package com.example.tools;

import java.util.TimerTask;

import android.content.Context;

public class TimerTaskGet extends TimerTask  {
    String filenameOnServer;
    Context context;

    public TimerTaskGet(Context context, String filenameOnServer) {
        this.filenameOnServer = filenameOnServer;
        this.context = context;
    }

    @Override
    public void run() {
        
    }
}
