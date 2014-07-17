package com.example.welcome;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;

import com.example.contextrecognition.R;

public class Fragement1 extends Fragment {

	private static final String TAG = "Welcome1";
	SharedPreferences mPrefs;
	final String welcomeScreenShownPref = "welcomeScreenShown";

	Button nextButton;
	
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.frag_welcome1, container, false);
        
        addListenerOnButton(v);

        return v;
    }

    public static Fragement1 newInstance(String text) {

        Fragement1 f = new Fragement1();
        Bundle b = new Bundle();
        b.putString("msg", text);

        f.setArguments(b);

        return f;
    }
    
	public void addListenerOnButton(View v) {

		nextButton = (Button) v.findViewById(R.id.nextButton);

		nextButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View arg0) {
				
				//TODO
				
				
			}

		});
	}
}
