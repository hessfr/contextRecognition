package ch.ethz.wearable.contextrecognition.utils;

import java.util.ArrayList;
import java.util.concurrent.ExecutionException;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import ch.ethz.wearable.contextrecognition.communication.GetKnownClasses;

/*
 * Builder class for the AlertDialog that lets you define custom classes and suggests 
 * names received from the server
 */
public class DialogBuilder {
	
	private static final String TAG = "OwnContextClassDialog";
	
	private onClickEvent listener;
	
	Context context;
	
	public DialogBuilder(Context context, onClickEvent listener) {
		this.context = context;
		this.listener = listener;
		
		Log.d(TAG, "Constructor");
	}
	
	public interface onClickEvent {
		void onClick(Context context, String newClassName);
	}
	

	@SuppressWarnings({ "rawtypes", "unchecked" })
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
		
		String[] contextClassesFromServer = null;
		String[] validSuggestions = null; // without the already trained classes
		
			contextClassesFromServer = executegetKnownClasses();
			
			if (contextClassesFromServer != null) {
				
				// Remove the already trained classes from the array:
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
