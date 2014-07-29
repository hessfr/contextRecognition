package ch.ethz.wearable.contextrecognition.activities;

import java.util.ArrayList;
import java.util.Arrays;

import android.app.AlertDialog;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
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
import ch.ethz.wearable.contextrecognition.communication.ManageClasses;
import ch.ethz.wearable.contextrecognition.utils.DialogBuilder;
import ch.ethz.wearable.contextrecognition.utils.DialogBuilder.onClickEvent;
import ch.ethz.wearable.contextrecognition.utils.Globals;

import com.example.contextrecognition.R;

public class ManageClassesActivity extends ActionBarActivity {
		
		private static final String TAG = "ManageClassesActivity";
		
		Context context = this;
		ContextSelectorAdapter dataAdapter;
		ListView listView;
		ArrayList<Boolean> currentStatuses;
		Button applyButton;
		private String[] prevClassNames;
		static final String DEFINE_OWN_CLASS = "Define own context class";
		
	    @Override
	    protected void onCreate(Bundle savedInstanceState) {
	        super.onCreate(savedInstanceState);
	        setContentView(R.layout.activity_manage_classes);
	 
	        ActionBar actionBar = getSupportActionBar();
	 
	        Log.d(TAG, "onCreate");
	        
	        // Enabling backwards navigation
	        actionBar.setDisplayHomeAsUpEnabled(true);
	        
	        addListenerOnButton();
	        
	        
	        Bundle b = getIntent().getExtras();
	        prevClassNames = b.getStringArray(Globals.CLASS_NAMES);     
	        
			if (prevClassNames != null) {
				
				ArrayList<String> contextList = new ArrayList<String>(Arrays.asList(prevClassNames));
				ArrayList<Boolean> defaultList = new ArrayList<Boolean>();
				// All check boxes have to be selected when we start this activity:
				for(int i=0; i<contextList.size(); i++) {
					defaultList.add(true);
				}
				
				// The check boxes that got selected by the user will be saved here:
				currentStatuses = new ArrayList<Boolean>();
				for(int i=0; i<defaultList.size(); i++) {
					currentStatuses.add(defaultList.get(i));
				}
				
				contextList.add(DEFINE_OWN_CLASS);
				defaultList.add(false);
				
				dataAdapter = new ContextSelectorAdapter(this, R.layout.listview_element_checkbox, contextList, defaultList);
				
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
						
						convertView = mInflater.inflate(R.layout.listview_element_textview, null);
						holder.textView = (TextView) convertView.findViewById(R.id.textView1);
						
						break;
					}
					convertView.setTag(holder);		   
				   

			   } else {
				   holder = (ViewHolder) convertView.getTag();
			   }
			   
			   String string = contextList.get(position);
			   
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
				// All elements check boxes, except then last one which is a text view:
				
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
	 
					// Check if some of the already incorporated class have been deselected:
					boolean isDifferent = false;
					for(int i=0; i<prevClassNames.length; i++){
						if (currentStatuses.get(i) == false) {
							isDifferent = true;
						}
					}
					// Check if new classes have been added:
					if(prevClassNames.length != currentStatuses.size()) {
						isDifferent = true;
					}
					
					
					ArrayList<String> classesToRequestList = new ArrayList<String>();
					for(int i=0; i<prevClassNames.length; i++) {
						if (currentStatuses.get(i) == true) {
							classesToRequestList.add(prevClassNames[i]);
						}
					}
					
					// Add the classes we added on top of previous classes:
					for(int i=prevClassNames.length; i<(dataAdapter.getStringArray().length-1); i++) {
						if (currentStatuses.get(i) == true) {
							classesToRequestList.add(dataAdapter.getStringArray()[i]);
						}
					}
					
					String[] classesToRequest = classesToRequestList.toArray(new String[classesToRequestList.size()]);
					
					if (isDifferent == true) {
						// request model from server is it's not the default classifier:
						Log.i(TAG, "New model will be requested from server");
						
						//TODO: check if this is working:
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
	        if (id == R.id.action_rating) {
	        	//Go to rating activity
	        	callRating();
	        	return true;
	        }
			if (id == R.id.action_exit) {
				// Quit the app and stop the recording:
				callShutdown();
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
	    /**
	     * Launch Rating activity
	     * */
	    private void callRating() {
	        Intent i = new Intent(ManageClassesActivity.this, RatingActivity.class);
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
	}

