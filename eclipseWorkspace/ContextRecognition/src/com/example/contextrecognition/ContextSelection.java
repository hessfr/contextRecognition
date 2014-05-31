package com.example.contextrecognition;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Editable;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.ListView;

public class ContextSelection extends ListActivity {
    
	private static final String TAG = "ContextSelection";
	
	ModelAdaptor modelAdaptor = new ModelAdaptor();
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setListAdapter(new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_1, getResources().getStringArray(R.array.context_classes)));

    }
    
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
      String item = (String) getListAdapter().getItem(position);

      //Assuming last item is always the "Define own context class"... TODO: do this properly!
      int lastItem = getListAdapter().getCount();
      
      //normal context class selected:
      if (position != (getListAdapter().getCount()-1)) {
    	  
    	  Log.i(TAG,"Existing class selected"); 
    	  modelAdaptor.changeExistingClass(item);
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
			final ArrayAdapter adapter = new ArrayAdapter(this,android.R.layout.simple_list_item_1,contextClassesFromServer);
			
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
					modelAdaptor.incorporateNewClass(enteredText);
				  
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
