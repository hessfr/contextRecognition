package ch.ethz.wearable.contextrecognition.welcomescreens;

import java.util.ArrayList;
import java.util.Arrays;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
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
import ch.ethz.wearable.contextrecognition.activities.MainActivity;
import ch.ethz.wearable.contextrecognition.communication.InitModel;
import ch.ethz.wearable.contextrecognition.utils.Globals;

import com.example.contextrecognition.R;

public class Fragment3 extends Fragment {

	Button startButton;
	ContextSelectorAdapter dataAdapter;
	ListView listView;
	Boolean[] actualSelection;
	static final String DEFINE_OWN_CLASS = "Define own context class";
	
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
		
		contextList.add(DEFINE_OWN_CLASS);
		defaultList.add(false);
		
		dataAdapter = new ContextSelectorAdapter(getActivity(), R.layout.listview_element_checkbox, contextList, defaultList);
		
		listView = (ListView) v.findViewById(R.id.contextSelector);
		
		listView.setOnItemClickListener(new OnItemClickListener() {
			   @Override
			   public void onItemClick(AdapterView<?> adapter, View view, int position, long arg) {
			      
			      if (position == (listView.getCount()-1)) {
			    	  Log.d(TAG, "Define own context class item was clicked");
			    	  
			    	  //TODO: call the corresponding method here
			    	  
			      }
			      
			   } 
			});
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
					
					Intent i = new Intent(getActivity(), InitModel.class);
					i.putExtra(Globals.CONN_INIT_MODEL_CLASSES, classesToRequest);
					getActivity().startService(i);
					
//					InitModel initReq = new InitModel();
					
				} else {
					Log.i(TAG, "Context classes not changed, using the default classifier");
				}
				
				callMainActivity();
			}
 
		});
	}
	
	private void callMainActivity() {
		Intent i = new Intent(getActivity(), MainActivity.class);
		i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_NO_ANIMATION); //TODO
        startActivity(i);
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
			return 2; // 1 or 2 ??
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
		
		public void addContextClass(final String contextClass, final Boolean status) {
			contextList.add(contextClass);
			cbStatus.add(status);
            notifyDataSetChanged();
        }
		
		public void addDefineOwnClass() {
			contextList.add(DEFINE_OWN_CLASS);
			cbStatus.add(false);
            notifyDataSetChanged();
        }
	}
}