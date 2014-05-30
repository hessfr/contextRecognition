package com.example.funf;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import edu.mit.media.funf.FunfManager;
import edu.mit.media.funf.config.RuntimeTypeAdapterFactory;
import edu.mit.media.funf.json.IJsonObject;
import edu.mit.media.funf.math.MFCC;
import edu.mit.media.funf.pipeline.BasicPipeline;
import edu.mit.media.funf.probe.Probe;
import edu.mit.media.funf.probe.Probe.DataListener;
import edu.mit.media.funf.probe.builtin.AudioFeaturesProbe;
import edu.mit.media.funf.storage.NameValueDatabaseHelper;
import android.support.v4.app.Fragment;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.TextView;
import android.widget.Toast;
import android.os.Build;

public class MainActivity extends Activity implements DataListener {

	public static final String PIPELINE_NAME = "default";
	private FunfManager funfManager;
	private BasicPipeline pipeline;
	
	private AudioFeaturesProbe audioFeaturesProbe;
	
	private CheckBox enabledCheckbox;
	private Button archiveButton, scanNowButton, ListDBEntriesButton;
	private TextView dataCountView;
	private TextView sensorValuesText;
	private Handler handler;
	private ServiceConnection funfManagerConn = new ServiceConnection() {    

	@Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        funfManager = ((FunfManager.LocalBinder)service).getManager();
        
        Gson gson = funfManager.getGson();
        
        audioFeaturesProbe = gson.fromJson(new JsonObject(), AudioFeaturesProbe.class);
        
        pipeline = (BasicPipeline) funfManager.getRegisteredPipeline(PIPELINE_NAME);

        audioFeaturesProbe.registerPassiveListener(MainActivity.this);
        
        // This checkbox enables or disables the pipeline
        enabledCheckbox.setChecked(pipeline.isEnabled());
        enabledCheckbox.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (funfManager != null) {
                    if (isChecked) {
                        funfManager.enablePipeline(PIPELINE_NAME);
                        pipeline = (BasicPipeline) funfManager.getRegisteredPipeline(PIPELINE_NAME);
                    } else {
                        funfManager.disablePipeline(PIPELINE_NAME);
                    }
                }
            }
        });
    
        // Set UI ready to use, by enabling buttons
        enabledCheckbox.setEnabled(true);
        archiveButton.setEnabled(true);
        scanNowButton.setEnabled(true);
        ListDBEntriesButton.setEnabled(true);
    }
    
    @Override
    public void onServiceDisconnected(ComponentName name) {
        funfManager = null;
    }
	};
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		// Forces the pipeline to scan now
		scanNowButton = (Button) findViewById(R.id.scanNowButton);
		scanNowButton.setEnabled(false);
		scanNowButton.setOnClickListener(new OnClickListener() {
		    @Override
		    public void onClick(View v) {
		        if (pipeline.isEnabled()) {
		            // Manually register the pipeline
		            audioFeaturesProbe.registerListener(pipeline);
		        } else {
		            Toast.makeText(getBaseContext(), "Pipeline is not enabled.", Toast.LENGTH_SHORT).show();
		        }
		    }
		});
		
		ListDBEntriesButton = (Button) findViewById(R.id.ListDBEntries);
		ListDBEntriesButton.setEnabled(false);
		ListDBEntriesButton.setOnClickListener(new OnClickListener() {
		    @Override
		    public void onClick(View v) {
		    	updateScanCount();
		    }
		});
		
	    // Displays the count of rows in the data
	    dataCountView = (TextView) findViewById(R.id.dataCountText);
	    
	    // Displays the current sensor readings
	    sensorValuesText = (TextView) findViewById(R.id.sensorValuesTextView);
	    
	    // Used to make interface changes on main thread
	    handler = new Handler();
	    
	    enabledCheckbox = (CheckBox) findViewById(R.id.enabledCheckbox);
	    enabledCheckbox.setEnabled(false);

	    // Runs an archive if pipeline is enabled
	    archiveButton = (Button) findViewById(R.id.archiveButton);
	    archiveButton.setEnabled(false);
	    archiveButton.setOnClickListener(new OnClickListener() {
	        @Override
	        public void onClick(View v) {
	            if (pipeline.isEnabled()) {
	                pipeline.onRun(BasicPipeline.ACTION_ARCHIVE, null);
	              
	                // Wait 1 second for archive to finish, then refresh the UI
	                // (Note: this is kind of a hack since archiving is seamless and there are no messages when it occurs)
	                handler.postDelayed(new Runnable() {
	                    @Override
	                    public void run() {
	                        Toast.makeText(getBaseContext(), "Archived!", Toast.LENGTH_SHORT).show();
	                    }
	                }, 1000L);
	            } else {
	                Toast.makeText(getBaseContext(), "Pipeline is not enabled.", Toast.LENGTH_SHORT).show();
	            }
	        }
	    });
	    
	    // Bind to the service, to create the connection with FunfManager
	    bindService(new Intent(this, FunfManager.class), funfManagerConn, BIND_AUTO_CREATE);
		
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {

		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
	
	private static final String TOTAL_COUNT_SQL = "SELECT count(*) FROM " + NameValueDatabaseHelper.DATA_TABLE.name;
	/**
	* Queries the database of the pipeline to determine how many rows of data we have recorded so far.
	*/
	private void updateScanCount() {
	    // Query the pipeline db for the count of rows in the data table
	    SQLiteDatabase db = pipeline.getDb();
	    Cursor mcursor = db.rawQuery(TOTAL_COUNT_SQL, null);
	    mcursor.moveToFirst();
	    final int count = mcursor.getInt(0);
	    // Update interface on main thread
	    runOnUiThread(new Runnable() {
	        @Override
	        public void run() {
	            dataCountView.setText("Data Count: " + count);
	        }
	    });
	}
	
	private void updateSensorValuesText(String arg) {
		final String str = arg;
	    runOnUiThread(new Runnable() {
	        @Override
	        public void run() {
	        	sensorValuesText.setText("Currently measured MFCC values: " + str);
	        }
	    });
	}
	
	@Override
	public void onDataCompleted(IJsonObject probeConfig, JsonElement checkpoint) {
	    updateScanCount();
	    // Re-register to keep listening after probe completes.
	    audioFeaturesProbe.registerListener(this);
	}
	
	@Override
	public void onDataReceived(IJsonObject probeConfig, IJsonObject data) {
		final String str = data.get("mfccs").toString();
		updateSensorValuesText(str);
//		Log.d("MainActivity","test123: " +  probeConfig.entrySet());
//		Log.d("MainActivity","xxxxxxxxxxxxxxxxxx");
//		funfManager.requestData(MainActivity.this, audioFeaturesProbe.getConfig());
//		Log.d("MainActivity","test123: " +  pipeline.getDataRequests());
//		Log.d("MainActivity","xxxxxxxxxxxxxxxxxx");	
	}
	
	public void runMyProbe(final String probeName) {
		ArrayList<Bundle> bundles = new ArrayList<Bundle>();
		List<JsonElement> existingRequests = pipeline.getDataRequests();
		
//		Bundle params = new Bundle();
//        params.putLong(Probe.Parameter.Builtin.DURATION.name, 0L);
//        bundles.add(bundles);
	}
	
}






























