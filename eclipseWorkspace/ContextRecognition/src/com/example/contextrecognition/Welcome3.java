package com.example.contextrecognition;

import java.util.ArrayList;
import java.util.Arrays;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ListView;

public class Welcome3 extends Activity {
	
	Button startButton;
	Button prevButton;
	ContextSelectorAdapter dataAdapter;
	ListView listView;
	Boolean[] actualSelection;
	
	private static final String TAG = "Welcome3";
	SharedPreferences mPrefs;
	final String welcomeScreenShownPref = "welcomeScreenShown";

	@Override
	public void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);
	    	this.requestWindowFeature(Window.FEATURE_NO_TITLE);
	    	
	    	setContentView(R.layout.activity_welcome3);
	    	
	    	addListenerOnButton();
	    	
			ArrayList<String> contextList = new ArrayList<String>(Arrays.asList(Globals.initialContextClasses));
			ArrayList<Boolean> defaultList = new ArrayList<Boolean>(Arrays.asList(Globals.defaultClasses));
			// The checkboxes that got selected by the user will be saved here:
			actualSelection = new Boolean[Globals.initialContextClasses.length];
			for(int i=0; i<defaultList.size(); i++) {
				actualSelection[i] = defaultList.get(i);
			}
			
			dataAdapter = new ContextSelectorAdapter(this,R.layout.cb_listview_element, contextList, defaultList);
			
			listView = (ListView) findViewById(R.id.contextSelector);
			
			// Assign adapter to ListView
			listView.setAdapter(dataAdapter);
			Log.d(TAG, "ListView for initial context selection created");
	}
	
	public void addListenerOnButton() {
		 
		startButton = (Button) findViewById(R.id.startButton);
		prevButton = (Button) findViewById(R.id.prevButton);
 
		startButton.setOnClickListener(new OnClickListener() {
			 
			@Override
			public void onClick(View arg0) {
 
				// Check if actualSelection array is different than the default array:
				boolean isDifferent = false;
				for(int i=0; i<Globals.defaultClasses.length; i++){
					if (Globals.defaultClasses[i] != actualSelection[i]) {
						isDifferent = true;
					}
				}
				
				if (isDifferent == true) {
					// request model from server is it's not the default classifier:
					Log.i(TAG, "New model will be requested from server as selected context"
							+ "classes are different from the default ones");
					
					//TODO: call server
					
				} else {
					Log.i(TAG, "Context classes not changed, using the default classifier");
				}
				
				
				// Call the main activity:
				Intent i = new Intent(Welcome3.this, MainActivity.class);
				i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_NO_ANIMATION); //TODO
		        startActivity(i);
 
			}
 
		});
		
		prevButton.setOnClickListener(new OnClickListener() {
 
			@Override
			public void onClick(View arg0) {
 
				Intent i = new Intent(Welcome3.this, Welcome2.class);
				i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_NO_ANIMATION); //TODO
		        startActivity(i);
 
			}
 
		});
		
	}
	
	/*
	 * Custom adapter to let the user select the context classes initially. ListView with checkboxes
	 * 
	 * Code similar to: http://www.mysamplecode.com/2012/07/android-listview-checkbox-example.html
	 */
	private class ContextSelectorAdapter extends ArrayAdapter<String> {

		private ArrayList<String> contextList;
		private ArrayList<Boolean> cbStatus;
		
		//Constructor:
		public ContextSelectorAdapter(Context context, int resourceId, ArrayList<String> contextList, 
				ArrayList<Boolean> cbStatus) {
			super(context, resourceId, contextList);
			
			Log.d(TAG, "ContextSelectorAdapter constructor");
			
			this.contextList = new ArrayList<String>();
			this.contextList.addAll(contextList);
			this.cbStatus = new ArrayList<Boolean>();
			this.cbStatus.addAll(cbStatus);
		}
		
		private class ViewHolder {
			CheckBox checkBox;
		}
		
		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
		
		   ViewHolder holder = null;
		 
		   if (convertView == null) {
			   LayoutInflater vi = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			   
			   convertView = vi.inflate(R.layout.cb_listview_element, null);
			 
			   holder = new ViewHolder();
			   holder.checkBox = (CheckBox) convertView.findViewById(R.id.checkBox1);
			   convertView.setTag(holder);		   
			   
			   holder.checkBox.setOnClickListener( new View.OnClickListener() {  
			    	
				   public void onClick(View v) {  
				    	 
					   CheckBox cb = (CheckBox) v;
					   String contextClass = cb.getText().toString();
					   
					   // Find position of this string in the contextClasses array:
					   int idx = -1;
					   for(int i=0; i<contextList.size(); i++) {
						   if (contextClass.equals(contextList.get(i))) {
							   idx = i;
						   }
					   }
					   if (cb.isChecked() == true) {
						   // CheckBox got selected just now:

						   if (idx != -1) {
							   actualSelection[idx] = true;
						   }
						   
					   } else {
						   // CheckBox got unselected just now:

						   if (idx != -1) {
							   actualSelection[idx] = false;
						   }
						   
					   }
				      
				   }  
			   }); 
		   } else {
			   holder = (ViewHolder) convertView.getTag();
		   }

		   String string = contextList.get(position);
		   holder.checkBox.setText(string);
		   holder.checkBox.setTextSize(18);
		   holder.checkBox.setChecked(cbStatus.get(position));
		   
		   return convertView;
		 
		  }
	}
}
