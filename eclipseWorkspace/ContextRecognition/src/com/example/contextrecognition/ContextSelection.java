package com.example.contextrecognition;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ListView;

import com.example.tools.ClassesDict;

public class ContextSelection extends ListActivity {
    
	private static final String TAG = "ContextSelection";
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setListAdapter(new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_1, getResources().getStringArray(R.array.context_classes)));
        
		if (ClassesDict.getInstance().getStringArray() != null) {
			// Add the "Define Own Context Class" to the bottom of the list:
			int len = ClassesDict.getInstance().getStringArray().length;
			String[] list = new String[len+1];
			
			for(int i=0; i<len; i++) {
				list[i] = ClassesDict.getInstance().getStringArray()[i];
			}
			list[len] = "Define own context class";
			
			setListAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, list));
		}

    }
    
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
      String item = (String) getListAdapter().getItem(position);

      //Assuming last item is always the "Define own context class"... TODO: do this properly!
      int lastItem = getListAdapter().getCount();
      
      //normal context class selected:
      if (position != (getListAdapter().getCount()-1)) {
    	  
    	  Log.i(TAG,"Existing class selected");
    	  
    	  //TODO: call model adaption

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
					
					//Call method to incorporate the new class, i.e. get new model from server
					
					//TODO: call model adaption
				  
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
