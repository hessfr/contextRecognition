package ch.ethz.wearable.contextrecognition.activities;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.TextView;
import ch.ethz.wearable.contextrecognition.R;
import ch.ethz.wearable.contextrecognition.communication.GetKnownClasses;
import ch.ethz.wearable.contextrecognition.communication.ManageClasses;
import ch.ethz.wearable.contextrecognition.utils.DialogBuilder;
import ch.ethz.wearable.contextrecognition.utils.DialogBuilder.onClickEvent;
import ch.ethz.wearable.contextrecognition.utils.Globals;

public class ManageClassesActivity extends ActionBarActivity {
		
		private static final String TAG = "ManageClassesActivity";
		
		Context context = this;
		ContextSelectorAdapter dataAdapter;
		ListView listView;
		ArrayList<Boolean> currentStatuses;
		ArrayList<Boolean> cbInitialStatusList;
		private ArrayList<String> contextList;
		Button applyButton;
		private String[] classesCurrentlyTrained;
		private String[] classNamesServer;
		private String[] classesBeingAdded; // if a server request is pending
		private String[] classesBeingRemoved; // if a server request is pending
		static final String DEFINE_OWN_CLASS = "Define own context class";
		static final String IS_BEING_ADDED = " - is being added";
		static final String IS_BEING_REMOVED = " - is being removed";
		
	    @SuppressWarnings("unchecked")
		@Override
	    protected void onCreate(Bundle savedInstanceState) {
	        super.onCreate(savedInstanceState);
	        setContentView(R.layout.activity_manage_classes);
	 
	        ActionBar actionBar = getSupportActionBar();
	 
	        Log.d(TAG, "onCreate");
	        
	        // Enabling backwards navigation
	        actionBar.setDisplayHomeAsUpEnabled(true);
	        
	        addListenerOnButton();
	        
	        classNamesServer = executegetKnownClasses();
	        if(classNamesServer != null) {
	        	// If successful, save this array to the preferences:
	        	Globals.setStringArrayPref(this, Globals.CONTEXT_CLASSES_SERVER, classNamesServer);
	        } else {
	        	// If not successful, get the last one from the preferences:
	        	classNamesServer = Globals.getStringArrayPref(this, Globals.CONTEXT_CLASSES_SERVER);
	        }
	        
	        if(classNamesServer == null) {
	        	// if the server request wasn't successful and the array in the preferences was also empty
	        	classNamesServer = Globals.getStringArrayPref(this, Globals.CONTEXT_CLASSES);
	        }
	        
	        classesCurrentlyTrained = Globals.getStringArrayPref(this, Globals.CONTEXT_CLASSES);
	        
	        classesBeingAdded = Globals.getStringArrayPref(context, Globals.CLASSES_BEING_ADDED);
	        if (classesBeingAdded != null) {
	        	Log.i(TAG, classesBeingAdded.length + " classes are currently being added");
	        }
	        
	        
	        classesBeingRemoved = Globals.getStringArrayPref(context, Globals.CLASSES_BEING_REMOVED);
	        if (classesBeingRemoved != null) {
	        	Log.i(TAG, classesBeingRemoved.length + " classes are currently being removed");
	        }
	        
        	if (classesCurrentlyTrained != null) {
				
        		// Use this contextList to build the list view:
				contextList = new ArrayList<String>(Arrays.asList(classNamesServer));
				
				// Also show the classes that are currently being added on the server:
				if (classesBeingAdded != null) {
					for (int i=0; i<classesBeingAdded.length; i++) {
						Log.i(TAG, classesBeingAdded[i] + " added to list, as it is currently trained");
						if (!contextList.contains(classesBeingAdded[i])) {
							contextList.add(classesBeingAdded[i]);
						}
					}
				}
				
				/*
				 *  Sort the list:
				 *  1. Classes that are already in our model
				 *  2. Classes that we already requested from the server and will be added then
				 *  3. All other classes available on the server
				 */
				// Create copy of the ArrayList first:
				ArrayList<String> tmpList = new ArrayList<String>();
				for(String s : contextList) {
					tmpList.add(s);
				}
				
				contextList.clear();
				
				for(int i=0; i<tmpList.size(); i++) {
					if (Arrays.asList(classesCurrentlyTrained).contains(tmpList.get(i))) {
						contextList.add(tmpList.get(i));
					}
				}
				if (classesBeingAdded != null) {
					for(int i=0; i<tmpList.size(); i++) {
						if (Arrays.asList(classesBeingAdded).contains(tmpList.get(i))) {
							contextList.add(tmpList.get(i));
						}
					}
				}
				for(int i=0; i<tmpList.size(); i++) {
					if (!contextList.contains(tmpList.get(i))) {
						contextList.add(tmpList.get(i));
					}
				}
				
				cbInitialStatusList = new ArrayList<Boolean>();
				// All check boxes have to be selected when we start this activity:
				for(int i=0; i<contextList.size(); i++) {
					if (Arrays.asList(classesCurrentlyTrained).contains(contextList.get(i))) {
						cbInitialStatusList.add(true);
					} else {
						cbInitialStatusList.add(false);
					}
				}
				
				// The check boxes that got selected by the user will be stored here:
				currentStatuses = new ArrayList<Boolean>();
				for(int i=0; i<cbInitialStatusList.size(); i++) {
					currentStatuses.add(cbInitialStatusList.get(i));
				}
				
				contextList.add(DEFINE_OWN_CLASS);
				cbInitialStatusList.add(false);
				
				dataAdapter = new ContextSelectorAdapter(this, R.layout.listview_element_checkbox, contextList, cbInitialStatusList);
				
				listView = (ListView) findViewById(R.id.contextSelector);
				
				listView.setOnItemClickListener(new OnItemClickListener() {
					   @Override
					   public void onItemClick(AdapterView<?> adapter, View view, int position, long arg) {
					      
					      if (position == (listView.getCount()-1)) {
					    	  Log.d(TAG, "Define own context class item was clicked");
					    	  
								// Show the alert dialog to select custom context classes:
								onClickEvent listener = new onClickEvent() {

									/*
									 * When clicking OK in the alert dialog, we add it to the list view
									 * and check the feasibility later:
									 */
									@Override
									public void onClick(Context context, String newClassName) {
										
										// Add the new item at the second last spot and set the check box to selected
										ArrayList<String> stringList = dataAdapter.getStringArrayList();
										stringList.add(stringList.get(stringList.size()-1));
										
										stringList.set((stringList.size()-2), newClassName);
										
										ArrayList<Boolean> statusList = dataAdapter.getStatusList();
										statusList.add(statusList.get(statusList.size()-1));
										statusList.set((statusList.size()-2), true);
										
										currentStatuses.add(true);
										
										dataAdapter = new ContextSelectorAdapter(context, R.layout.listview_element_checkbox, stringList, statusList);
										
										listView.setAdapter(dataAdapter);
										
										Log.i(TAG, "New context class " + newClassName + " added to list");

									}
								};
								
								DialogBuilder dialogBuilder = new DialogBuilder(context, listener);
								
								AlertDialog alertDialog = dialogBuilder
										.createDialog(dataAdapter.getStringArray());

								alertDialog.show();
					    	  
					      }
					      
					   } 
					});
				
				// Assign adapter to ListView
				listView.setAdapter(dataAdapter);
				
			} else {
				Log.e(TAG, "classNames String Array empty. List could not be set");
			} 
			
	        
			
	    }    
	    
	    
		/*
		 * Custom adapter to let the user select the context classes initially. ListView contains two
		 * different elements: check boxes for all normal elements (context classes) and a text view
		 * (for the element that let's us define a new context class) -> THIS HAS TO BE THE VERY LAST
		 * ONE IN THE STRING ARRAY
		 * 
		 * Code similar to: http://android.amberfog.com/?p=296
		 */
		private class ContextSelectorAdapter extends ArrayAdapter<String> {

			private ArrayList<String> contextList;
			private ArrayList<Boolean> cbStatus;
			
			private LayoutInflater mInflater;
			
			private static final int TYPE_CHECKBOX = 0;
	        private static final int TYPE_TEXTVIEW = 1;
			
			//Constructor:
			public ContextSelectorAdapter(Context context, int resourceId, ArrayList<String> contextList, 
					ArrayList<Boolean> cbStatus) {
				super(context, resourceId, contextList);
				
				Log.d(TAG, "ContextSelectorAdapter constructor");
				
				mInflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				
				this.contextList = new ArrayList<String>();
				this.contextList.addAll(contextList);
				this.cbStatus = new ArrayList<Boolean>();
				this.cbStatus.addAll(cbStatus);
				
			}
			
			private class ViewHolder {
				CheckBox checkBox;
				TextView textView;
			}

			@Override
			public View getView(int position, View convertView, ViewGroup parent) {
			
			   ViewHolder holder = null;
			   int type = getItemViewType(position);
			   
			   if (convertView == null) {
				   
				   holder = new ViewHolder();
				   
					switch (type) {
					case TYPE_CHECKBOX:
						
						convertView = mInflater.inflate(R.layout.listview_element_checkbox, null);
						holder.checkBox = (CheckBox) convertView.findViewById(R.id.checkBox1);
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
										   currentStatuses.set(idx, true);
										   cbStatus.set(idx, true);
									   }
									   
								   } else {
									   // CheckBox got unselected just now:

									   if (idx != -1) {
										   currentStatuses.set(idx, false);
										   cbStatus.set(idx, false);
									   }
									   
								   }
							   }  
						   }); 
						   
						   break;
						   
					case TYPE_TEXTVIEW:
						// For the "Define own context class" entry
						convertView = mInflater.inflate(R.layout.listview_element_textview, null);
						holder.textView = (TextView) convertView.findViewById(R.id.textView1);
						
						break;
					}
					convertView.setTag(holder);		   
				   

			   } else {
				   holder = (ViewHolder) convertView.getTag();
			   }
			   
			   String string = contextList.get(position);
			   
			   // Add additional text if the class if currently changed on the server:
			   if (classesBeingAdded != null) {
				   if(Arrays.asList(classesBeingAdded).contains(contextList.get(position))) {
					   string = string + IS_BEING_ADDED;
				   }
			   }
			   if (classesBeingRemoved != null) {
				   if(Arrays.asList(classesBeingRemoved).contains(contextList.get(position))) {
					   string = string + IS_BEING_REMOVED;
				   }
			   }

			   
				switch (type) {
				case TYPE_CHECKBOX:

					holder.checkBox.setText(string); 
					holder.checkBox.setTextSize(18);
					holder.checkBox.setChecked(cbStatus.get(position));

					break;

				case TYPE_TEXTVIEW:
					
					holder.textView.setText(string);
					holder.textView.setTextSize(18);
					holder.textView.setTypeface(holder.textView.getTypeface(), Typeface.BOLD);

					break;

				}
			   
			   return convertView;

			}

			@Override
			public int getViewTypeCount() {
				return 2;
			}
			
			@Override
	        public int getItemViewType(int position) {
				
				/* All elements are check boxes, except the last one ("Define own context class") 
				 * which is a text view:
				 */
				if (position < contextList.size()-1) {
					return TYPE_CHECKBOX;
				} else {
					return TYPE_TEXTVIEW;
				}
	        }
			
			public String[] getStringArray() {
				return contextList.toArray(new String[contextList.size()]);
			}
			
			public ArrayList<String> getStringArrayList() {
				return contextList;
			}
			
			public ArrayList<Boolean> getStatusList() {
				return cbStatus;
			}
		}
		
		public void addListenerOnButton() {
			 
			applyButton = (Button) findViewById(R.id.applyButton);
	 
			applyButton.setOnClickListener(new OnClickListener() {
				 
				@Override
				public void onClick(View arg0) {
	 
					Log.i(TAG, "cbInitialStatusList size: " + cbInitialStatusList.size());
					Log.i(TAG, "currentStatuses size: " + currentStatuses.size());
					
					// Check if some of the already incorporated class have been deselected:
					boolean isDifferent = false;
					for(int i=0; i<cbInitialStatusList.size(); i++){
						if (currentStatuses.get(i) != cbInitialStatusList.get(i)) {
							isDifferent = true;
						}
					}
					// Check if new classes have been added:
					if(cbInitialStatusList.size() != currentStatuses.size()) {
						isDifferent = true;
					}
					
					// Build the list of classes that should be included in the new model:
					ArrayList<String> classesToRequestList = new ArrayList<String>();
					// ignore the "DEFINE OWN CONTEXT CLASS" element:
					for(int i=0; i<(contextList.size()-1); i++) {
						if (currentStatuses.get(i) == true) {
							classesToRequestList.add(contextList.get(i));
						}
					}
					
//					Log.i(TAG, "contextList size: " + contextList.size());
//					Log.i(TAG, "dataAdapter size: " + dataAdapter.getStringArray().length);
					
//					Log.i(TAG, "------------- dataAdapter: ---------");
//					for(int i=0; i<dataAdapter.getStringArray().length; i++) {
//						Log.i(TAG, dataAdapter.getStringArray()[i]);
//					}
					
//					Log.i(TAG, "------------- contextList: ---------");
//					for(int i=0; i<contextList.size(); i++) {
//						Log.i(TAG, contextList.get(i));
//					}
					
					// Add the classes we added on top of previous classes:
					// ignore the "DEFINE OWN CONTEXT CLASS" element:
					for(int i=(contextList.size()-1); i<(dataAdapter.getStringArray().length-1); i++) {
						if (currentStatuses.get(i) == true) {
							classesToRequestList.add(dataAdapter.getStringArray()[i]);
						}
					}
					
					String[] classesToRequest = classesToRequestList.toArray(new String[classesToRequestList.size()]);
					
//					Log.i(TAG, "------ classes to request ----");
//					for(int i=0; i<classesToRequest.length; i++) {
//						Log.i(TAG, classesToRequest[i]);
//					}
					
					if (isDifferent == true) {
						// request model from server is it's not the default classifier:
						Log.i(TAG, "New model will be requested from server");
						
						Intent i = new Intent(context, ManageClasses.class);
						i.putExtra(Globals.CONN_MANAGE_CLASSES_ARRAY, classesToRequest);
						context.startService(i);	
						
					} else {
						Log.i(TAG, "Context classes not changed, using the default classifier");
					}
					
					Intent i = new Intent(ManageClassesActivity.this, MainActivity.class);
			        startActivity(i);
				}
	 
			});
		}
	    
	    @Override
	    public boolean onCreateOptionsMenu(Menu menu) {
	        //Create the options entry in the ActionBar
	        getMenuInflater().inflate(R.menu.main, menu);
	        return true;
	    }
	    
	    @Override
	    public boolean onOptionsItemSelected(MenuItem item) {
	    	//Handle ActionBar clicks
	        int id = item.getItemId();
	        if (id == R.id.action_settings) {
	            //Go to settings activity
	        	callSettings();
	        	return true;
	        }
	        if (id == R.id.action_diary) {
	        	//Go to rating activity
	        	callLabel();
	        	return true;
	        }
//	        if (id == R.id.action_rating) {
//	        	//Go to rating activity
//	        	callRating();
//	        	return true;
//	        }
			if (id == R.id.action_exit) {
				// Quit the app and stop the recording:
				callShutdown();
			}
			if (id == R.id.action_upload) {
				// Go to manage classes activity:
				callUploadActivity();
			}

	        return super.onOptionsItemSelected(item);
	    }
	    
	    /**
	     * Launch Settings activity
	     * */
	    private void callSettings() {
	        Intent i = new Intent(ManageClassesActivity.this, SettingsActivity.class);
	        startActivity(i);
	    }
	    /**
	     * Launch Settings activity
	     * */
	    private void callLabel() {
	        Intent i = new Intent(ManageClassesActivity.this, DiaryActivity.class);
	        startActivity(i);
	    }
//	    /**
//	     * Launch Rating activity
//	     * */
//	    private void callRating() {
//	        Intent i = new Intent(ManageClassesActivity.this, RatingActivity.class);
//	        startActivity(i);
//	    }
	    /**
	     * Launch upload activity
	     * */
	    private void callUploadActivity() {
	        Intent i = new Intent(ManageClassesActivity.this, UploadActivity.class);
	        startActivity(i);
	    }
	    /**
	     * Launch Shutdown activity to close app and stop recording
	     * */
	    private void callShutdown() {
			Application app = getApplication();
		    Intent intent = new Intent(app, ShutdownActivity.class);
		    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
		    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		    app.startActivity(intent);
	    }
	    
		/*
		 * Allow parallel execution for the AsyncTask if API > 11 (Previous APIs do this by default)
		 * 
		 * Code similar to http://stackoverflow.com/questions/4068984/running-multiple-asynctasks-at-the-same-time-not-possible/13800208#13800208
		 */
		@TargetApi(Build.VERSION_CODES.HONEYCOMB) // API 11
		public static <T> String[] executegetKnownClasses() {
			GetKnownClasses getKnownClasses = new GetKnownClasses();
		    String[] result = null;
			if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
				try {
					result = getKnownClasses.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR).get();
				} catch (InterruptedException e) {
					e.printStackTrace();
				} catch (ExecutionException e) {
					e.printStackTrace();
				}
			else
				try {
					result = getKnownClasses.execute().get();
				} catch (InterruptedException e) {
					e.printStackTrace();
				} catch (ExecutionException e) {
					e.printStackTrace();
				}
			return result;
		}
	}

