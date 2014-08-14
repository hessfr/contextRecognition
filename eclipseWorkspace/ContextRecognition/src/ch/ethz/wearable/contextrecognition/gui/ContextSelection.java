package ch.ethz.wearable.contextrecognition.gui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import ch.ethz.wearable.contextrecognition.utils.DialogBuilder;
import ch.ethz.wearable.contextrecognition.utils.DialogBuilder.onClickEvent;
import ch.ethz.wearable.contextrecognition.utils.Globals;

public class ContextSelection extends ListActivity {
    
	private static final String TAG = "ContextSelection";
	
	private String[] classNames;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

//        Bundle b = getIntent().getExtras();
//        classNames = b.getStringArray(Globals.CLASS_NAMES);     
        
        classNames = Globals.getStringArrayPref(this, Globals.CONTEXT_CLASSES);
        
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

	  		Log.i(TAG, "Existing class " + item + " selected");
	  		
			Intent intent = new Intent(Globals.MODEL_ADAPTION_EXISTING_INTENT);
			Bundle bundle = new Bundle();
			bundle.putInt(Globals.LABEL, position);
			intent.putExtras(bundle);
			sendBroadcast(intent);

			finish();

		} else { // "Define own context class" selected
			
			onClickEvent listener = new onClickEvent() {

				/*
				 * When clicking in the alert dialog OK, we directly call the model adaption
				 */
				@Override
				public void onClick(Context context, String newClassName) {
					
					Log.i(TAG, "New context class " + newClassName + " requested");
	    			
	    			Intent intent = new Intent(Globals.MODEL_ADAPTION_NEW_INTENT);
	    			Bundle bundle = new Bundle();
	    			bundle.putString(Globals.NEW_CLASS_NAME, newClassName);
	    			intent.putExtras(bundle);
	    			context.sendBroadcast(intent);
	    			
	    			((Activity) context).finish();
					
				}
			};
			
			DialogBuilder dialogBuilder = new DialogBuilder(this, listener);
			
			AlertDialog alertDialog = dialogBuilder
					.createDialog(Globals.getStringArrayPref(this, Globals.CONTEXT_CLASSES));

			alertDialog.show();

		}

	}

}
