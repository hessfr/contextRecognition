package com.example.contextrecognition;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ListView;

import com.example.tools.StateManager;

public class ContextSelection extends ListActivity {
    
	private static final String TAG = "ContextSelection";
	
	private String[] classNames;
	private int conversationIdx = -1;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setListAdapter(new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_1, getResources().getStringArray(R.array.context_classes)));
        
        Bundle b = getIntent().getExtras();
        classNames = b.getStringArray(StateManager.CLASS_NAMES);     
        
		if (classNames != null) {
			// Add the "Define Own Context Class" to the bottom of the list:
			int len = classNames.length;
			String[] list = new String[len+1];
			
			for(int i=0; i<len; i++) {
				list[i] = classNames[i];
			}
			list[len] = "Define own context class";
			
			setListAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, list));
		} else {
			Log.e(TAG, "classNames String Array empty. List could not be set");
		}

		// Find index of the conversation class, as we do not want to adapt our model for these:
		for(int i=0; i<classNames.length; i++) {
			if (classNames[i].equals("Conversation")) {
				conversationIdx = i;
			}
		}
		
    }
    
    @Override
    protected void onResume() {
      super.onResume();
      
    }
    
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
      String item = (String) getListAdapter().getItem(position);
      
      //normal context class selected:
      if (position != (getListAdapter().getCount()-1)) {

    	  	if (position != conversationIdx) {
    	  		
    	  		Log.i(TAG, "Existing class " + item + " selected");
    	  		
    			Intent intent = new Intent(StateManager.MODEL_ADAPTION_EXISTING_INTENT);
    			Bundle bundle = new Bundle();
    			bundle.putInt(StateManager.LABEL, position);
    			intent.putExtras(bundle);
    			sendBroadcast(intent);
    			
    	  	} else {
    	  		Log.i(TAG, "Conversation class will not be incorporated into our model");
    	  	}

          finish();
    	  
      } else { // "Define own context class" selected
    	  
    	  
		  	// Show alert dialog
	  		AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
			
			alertDialogBuilder.setTitle("Define new context class");
			alertDialogBuilder.setMessage("");
			
			//Create the AutoCompleteTextView to suggest context class names know from other users:
			final AutoCompleteTextView autoCompleteTV = new AutoCompleteTextView(this);
			alertDialogBuilder.setView(autoCompleteTV);
			
			String[] contextClassesFromServer = getResources().
					   getStringArray(R.array.context_classes_from_server);
			
			final ArrayAdapter adapter = new ArrayAdapter(this,android.R.layout.simple_list_item_1, contextClassesFromServer);
			
			autoCompleteTV.setOnClickListener(new OnClickListener() {

	            @Override
	            public void onClick(View arg0) {
	            	autoCompleteTV.setAdapter(adapter);
	            }
	        });
			
			alertDialogBuilder.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
				
				public void onClick(DialogInterface dialog, int whichButton) {
					String enteredText = autoCompleteTV.getText().toString();
					
					Log.i(TAG, "New context class " + enteredText + " requested");
	    			
	    			Intent intent = new Intent(StateManager.MODEL_ADAPTION_NEW_INTENT);
	    			Bundle bundle = new Bundle();
	    			bundle.putString(StateManager.NEW_CLASS_NAME, enteredText);
	    			intent.putExtras(bundle);
	    			sendBroadcast(intent);
	    			
	    			finish();			  
				  }
			});
			
			alertDialogBuilder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
				
				  public void onClick(DialogInterface dialog, int whichButton) {
				    // Canceled
				  }
			});
			
			// create alert dialog
			AlertDialog alertDialog = alertDialogBuilder.create();
			
			// show it
			alertDialog.show(); 
    	  
      }           
      
    }

}
