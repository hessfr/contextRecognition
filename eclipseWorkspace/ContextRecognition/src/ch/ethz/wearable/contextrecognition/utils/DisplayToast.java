package ch.ethz.wearable.contextrecognition.utils;

import android.content.Context;
import android.widget.Toast;

/*
 * Code from http://stackoverflow.com/questions/3955410/create-toast-from-intentservice/3955826#3955826
 */
public class DisplayToast implements Runnable {
    private final Context mContext;
    String mText;

    public DisplayToast(Context mContext, String text){
        this.mContext = mContext;
        mText = text;
    }

    public void run(){
        Toast.makeText(mContext, mText, Toast.LENGTH_LONG).show();
    }
}
