package ch.ethz.wearable.contextrecognition.welcomescreens;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import ch.ethz.wearable.contextrecognition.R;

public class Fragement1 extends Fragment {

	private static final String TAG = "Welcome1";
	
	SharedPreferences mPrefs;
	final String welcomeScreenShownPref = "welcomeScreenShown";

	Button nextButton;
	
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.frag_welcome1, container, false);

        Log.d(TAG, "onCreateView");
        
        return v;
    }

    public static Fragement1 newInstance(String text) {

        Fragement1 f = new Fragement1();
        Bundle b = new Bundle();
        b.putString("msg", text);

        f.setArguments(b);

        return f;
    }
}
