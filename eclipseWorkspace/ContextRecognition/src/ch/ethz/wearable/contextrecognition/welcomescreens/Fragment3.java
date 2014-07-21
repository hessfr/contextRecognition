package ch.ethz.wearable.contextrecognition.welcomescreens;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Timer;
import java.util.concurrent.ExecutionException;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.Toast;
import ch.ethz.wearable.contextrecognition.activities.MainActivity;
import ch.ethz.wearable.contextrecognition.communication.GetInitialModel;
import ch.ethz.wearable.contextrecognition.communication.InitModel;
import ch.ethz.wearable.contextrecognition.data.TimerTaskGet;
import ch.ethz.wearable.contextrecognition.utils.Globals;

import com.example.contextrecognition.R;

public class Fragment3 extends Fragment {

	Button startButton;
	ContextSelectorAdapter dataAdapter;
	ListView listView;
	Boolean[] actualSelection;
	
	private static final String TAG = "Welcome3";
	SharedPreferences mPrefs;
	final String welcomeScreenShownPref = "welcomeScreenShown";
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View v = inflater.inflate(R.layout.frag_welcome3, container, false);

		addListenerOnButton(v);
    	
		ArrayList<String> contextList = new ArrayList<String>(Arrays.asList(Globals.initialContextClasses));
		ArrayList<Boolean> defaultList = new ArrayList<Boolean>(Arrays.asList(Globals.defaultClasses));
		// The checkboxes that got selected by the user will be saved here:
		actualSelection = new Boolean[Globals.initialContextClasses.length];
		for(int i=0; i<defaultList.size(); i++) {
			actualSelection[i] = defaultList.get(i);
		}
		
		dataAdapter = new ContextSelectorAdapter(getActivity(), R.layout.cb_listview_element, contextList, defaultList);
		
		listView = (ListView) v.findViewById(R.id.contextSelector);
		
		// Assign adapter to ListView
		listView.setAdapter(dataAdapter);
		Log.d(TAG, "ListView for initial context selection created");
		
		return v;
	}

	public static Fragment3 newInstance(String text) {

		Fragment3 f = new Fragment3();
		Bundle b = new Bundle();
		b.putString("msg", text);

		f.setArguments(b);

		return f;
	}
	
	public void addListenerOnButton(View v) {
		 
		startButton = (Button) v.findViewById(R.id.startButton);
 
		startButton.setOnClickListener(new OnClickListener() {
			 
			@Override
			public void onClick(View arg0) {
 
				// Check if actualSelection array is different than the default array:
				boolean isDifferent = false;
				int numClasses = 0;
				for(int i=0; i<Globals.defaultClasses.length; i++){
					if (Globals.defaultClasses[i] != actualSelection[i]) {
						isDifferent = true;
					}
					
					if (actualSelection[i] == true) {
						numClasses++;
					}
				}
				
				String[] classesToRequest = new String[numClasses];
				int k=0;
				for(int i=0; i<Globals.initialContextClasses.length; i++) {
					if (actualSelection[i] == true) {
						classesToRequest[k] = Globals.initialContextClasses[i];
						k++;
					}
				}
				
				if (isDifferent == true) {
					// request model from server is it's not the default classifier:
					Log.i(TAG, "New model will be requested from server as selected context"
							+ "classes are different from the default ones");
					
					InitModel initReq = new InitModel();
					
					try {
						
						 String[] res = initReq.execute(classesToRequest).get();
						 
						 if (res != null) {
							 final String filenameOnServer = res[0];
							 String wait = res[1];
							 
							 Log.i(TAG, "filenameOnServer: " + filenameOnServer);

							// Now check periodically if the computation on server
							// is finished
							TimerTaskGet task = new TimerTaskGet(getActivity(), filenameOnServer, 
									Globals.POLLING_INTERVAL_INITIAL_MODEL, Globals.MAX_RETRY_INITIAL_MODEL) {

								private int counter;

								public void run() {

									GetInitialModel getReq = new GetInitialModel();
									Boolean resGet = false;

									try {

										resGet = getReq.execute(filenameOnServer).get();

									} catch (InterruptedException e) {
										e.printStackTrace();
									} catch (ExecutionException e) {
										e.printStackTrace();
									}

									if (resGet == true) {

										// Model received from the server:
										Log.i(TAG, "New classifier received from server");
										
										// Call the main activity:
										callMainActivity();
										
										this.cancel();

									}

									if (++counter == Globals.MAX_RETRY_INITIAL_MODEL) {
										Log.w(TAG, "Server not responded to GET request intitial model");
										
										getActivity().runOnUiThread(new Runnable() {
										      @Override
										          public void run() {
										    	  Toast.makeText(
										    			  	getActivity(),
															(String) "Server not reponding, deploying default model, user specific classes "
																	+ "will be requested when server online again ",
															Toast.LENGTH_LONG).show();
										          }
										   });
										
										callMainActivity();
										this.cancel();
									}

									Log.i(TAG, "Waiting for new classifier from server");
								}
							};

							Timer timer = new Timer();
							timer.schedule(task, 0, Globals.POLLING_INTERVAL_INITIAL_MODEL);
						 } else {
							 Log.w(TAG, "Server not responded to POST request intitial model");
							 
							 //TODO: start MainActivity, but set recurring task to request this model
							 
							Toast.makeText(
									getActivity(),
									(String) "Server not reponding, deploying default model, user specific classes "
											+ "will be requested when server online again ",
									Toast.LENGTH_LONG).show();
							 
							 callMainActivity();
						 }
						 

					} catch (InterruptedException e) {
						e.printStackTrace();
					} catch (ExecutionException e) {
						e.printStackTrace();
					}
					
				} else {
					Log.i(TAG, "Context classes not changed, using the default classifier");
					
					// Call the main activity:
					callMainActivity();
				}
			}
 
		});
	}
	
	private void callMainActivity() {
		Intent i = new Intent(getActivity(), MainActivity.class);
		i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_NO_ANIMATION); //TODO
        startActivity(i);
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
			   LayoutInflater vi = (LayoutInflater) getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			   
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