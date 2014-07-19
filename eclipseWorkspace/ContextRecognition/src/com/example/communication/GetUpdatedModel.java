package com.example.communication;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.util.EntityUtils;

import android.app.IntentService;
import android.content.Intent;
import android.util.Log;

import com.example.tools.Globals;
import com.example.tools.TimerTaskGet;

public class GetUpdatedModel extends IntentService {

	private static final String TAG = "GetUpdatedModel";
	
	public GetUpdatedModel() {
		super("GetUpdatedModel");
		
		Log.d(TAG, "Constructor");
		
	}
	
	@Override
	protected void onHandleIntent(Intent arg0) {

		Log.i(TAG, "onHandleIntent");
		
		final String filenameOnServer = arg0.getStringExtra(Globals.CONN_UPDATED_MODEL_FILENAME);
		String feasibilityCheckResult = arg0.getStringExtra(Globals.CONN_CHECK_FEASIBILITY_RESULT);
		
		final long maxRetries;
		final long pollingInterval;
		long delay = 200; // wait 200ms before starting the first execution
		
		if (feasibilityCheckResult.equals(Globals.FEASIBILITY_DOWNLOADED)) {
			
			Log.i(TAG, "Initialize TimerTask with shorter polling interval and timeout, "
					+ "as class was downloaded already");
			maxRetries = Globals.MAX_RETRY_NEW_CLASS_ALREADY_DOWNLOADED;
			pollingInterval = Globals.POLLING_INTERVAL_NEW_CLASS_ALREADY_DOWNLOADED;
			
		} else if (feasibilityCheckResult.equals(Globals.FEASIBILITY_FEASIBLE)) {
			
			Log.i(TAG, "Initialize TimerTask with longer polling interval and timeout, "
					+ "as class is not downloaded yet");
			maxRetries = Globals.MAX_RETRY_NEW_CLASS_NOT_DOWNLOADED;
			pollingInterval = Globals.POLLING_INTERVAL_NEW_CLASS_NOT_DOWNLOADED;
			
		} else {
			Log.e(TAG, "Wrong extra received for CONN_CHECK_FEASIBILITY_RESULT");
			maxRetries = 0;
			pollingInterval = 0;
		}
		
		
	    
		// Now check periodically if the computation on server is finished
		TimerTaskGet task = new TimerTaskGet(getBaseContext(), filenameOnServer, pollingInterval, maxRetries) {

			private int counter;

			public void run() {
				// Looper.prepare();

				boolean result = false;
				
				//Add parameters to URL
			    List<NameValuePair> par = new LinkedList<NameValuePair>();
		        par.add(new BasicNameValuePair("filename", filenameOnServer));
		        String paramString = URLEncodedUtils.format(par, "utf-8");
		        String URL = Globals.ADD_CLASS_URL + paramString;
		        
		        //Set timeout parameters:
		        HttpParams httpParameters = new BasicHttpParams();
		        int timeoutConnection = 3000;
		        HttpConnectionParams.setConnectionTimeout(httpParameters, timeoutConnection);
		        int timeoutSocket = 5000;
		        HttpConnectionParams.setSoTimeout(httpParameters, timeoutSocket);
		        
				HttpClient client = new DefaultHttpClient(httpParameters);
		        HttpGet get = new HttpGet(URL);
				
				try {
			    	HttpResponse response = client.execute(get);

			    	if (response.getStatusLine().getStatusCode() == 200) {
			    		// This is our new classifier. Overwrite the existing one:
			    		
			    		String receveivedString = EntityUtils.toString(response.getEntity());	    		
			    		String jsonString = null;

			    		// Computation on server not finished yet:
			    		if (receveivedString.equals("-1")) {

			    			result = false;
			    			
			    		} else {
			    			
			    			jsonString = receveivedString;
			    			result = true;
			    			
				    		//TODO: stop prediction here
				    		
				    		
				    		// Replace the current GMM with the new one:
				    		String filename = "GMM.json";
				    		
				    		File file = new File(Globals.APP_PATH,filename);
				    		
				    		try {
				    			FileWriter out = new FileWriter(file);
				                out.write(jsonString);
				                out.close();
				                
				                //TODO send broadcast that new class incorporated and start prediction again
				    	    }
				    	    catch (IOException e) {
				    	        Log.e("Exception", "File write failed: " + e.toString());
				    	    } 
			    			
			    		}

			    		
			    	} else {
			    		Log.e(TAG, "Invalid response received after GetUpdatedModel request");
			    		Log.e(TAG, String.valueOf(response.getStatusLine()));

			    	}

			    	
			    } catch (UnsupportedEncodingException e) {
			        e.printStackTrace();
			    } catch (ClientProtocolException e) {
			        e.printStackTrace();
			    } catch (IOException e) {
			        e.printStackTrace();
			    }


				if (result == true) {

					// Model received from the server:
					Log.i(TAG, "New classifier received from server");
					
					
					Intent i = new Intent(Globals.CONN_UPDATED_MODEL_RECEIVE);
					i.putExtra(Globals.CONN_UPDATED_MODEL_RESULT, true);
					sendBroadcast(i);
					
					Log.i(TAG, "Finsishing IntentService");
					this.cancel();

				} else {
					
					Log.i(TAG, "Waiting for new classifier from server");
				}

				if (++counter == maxRetries) {
					Log.e(TAG,
							"Server timeout, model adaption failed, as server didn't provide new classifier"
									+ "with the specified time limit");
					
					Intent i = new Intent(Globals.CONN_UPDATED_MODEL_RECEIVE);
					i.putExtra(Globals.CONN_UPDATED_MODEL_RESULT, false);
					sendBroadcast(i);
					
					Log.i(TAG, "Finsishing IntentService");
					this.cancel();
				}
			}
		};

		Timer timer = new Timer();
		timer.schedule(task, delay, pollingInterval);
	}

}
