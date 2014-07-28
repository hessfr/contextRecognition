package ch.ethz.wearable.contextrecognition.utils;

import java.util.ArrayList;
import java.util.concurrent.ExecutionException;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import ch.ethz.wearable.contextrecognition.communication.GetKnownClasses;
import ch.ethz.wearable.contextrecognition.math.ModelAdaptor.onModelAdaptionCompleted;

public class DialogBuilder {
	
	private static final String TAG = "OwnContextClassDialog";
	
	private onClickEvent listener;
	
	Context context;
	
	public DialogBuilder(Context context, onClickEvent listener) {
		this.context = context;
		this.listener = listener;
	}
	
	public interface onClickEvent {
		void onClick(Context context, String newClassName);
	}
	

	public AlertDialog createDialog(String[] classesToRemove) {
		
	  	// Show alert dialog
  		AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(context);
		
		alertDialogBuilder.setTitle("Define new context class");
		alertDialogBuilder.setMessage("");
		
		// Create the AutoCompleteTextView to suggest context class names know from other users:
		final AutoCompleteTextView autoCompleteTV = new AutoCompleteTextView(context);
		autoCompleteTV.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
				android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);
		alertDialogBuilder.setView(autoCompleteTV);
		
		GetKnownClasses getKnownClasses = new GetKnownClasses();
		
		String[] contextClassesFromServer = null;
		String[] validSuggestions = null; // without the already trained classes
		
		try {
			contextClassesFromServer = getKnownClasses.execute().get();
			if (contextClassesFromServer != null) {
				// Remove the already trained classes from the array:
				//String[] trainedClasses = Globals.getStringArrayPref(context, Globals.CONTEXT_CLASSES);
				ArrayList<String> tmp = new ArrayList<String>();
				
				for(int j=0; j<contextClassesFromServer.length; j++) {
					boolean classAlreadyTrained = false;
					for(int k=0; k<classesToRemove.length; k++) {
						if (contextClassesFromServer[j].equals(classesToRemove[k])) {
							classAlreadyTrained = true;
						}
					}
					
					if (classAlreadyTrained == false) {
						tmp.add(contextClassesFromServer[j]);
					}
				}
				
				// Convert the ArrayList to a String array:
				validSuggestions = new String[tmp.size()];
				validSuggestions = tmp.toArray(validSuggestions);
			}

		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (ExecutionException e) {
			e.printStackTrace();
		}
		
		// If no data could be received from the server, initialize empty array:
		if (validSuggestions == null) {
			validSuggestions = new String[0];
		}
		
		final ArrayAdapter adapter = new ArrayAdapter(context,android.R.layout.simple_list_item_1, validSuggestions);

		autoCompleteTV.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View arg0) {
            	if (adapter != null) {
            		autoCompleteTV.setAdapter(adapter);
            	}
            }
        });
		
		alertDialogBuilder.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
			
			public void onClick(DialogInterface dialog, int whichButton) {
				
				String enteredText = autoCompleteTV.getText().toString();
				
				listener.onClick(context, enteredText);
				
			  }
		});
		
		alertDialogBuilder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
			
			  public void onClick(DialogInterface dialog, int whichButton) {
			    // Canceled
			  }
		});
		
		return alertDialogBuilder.create();
    }
}
