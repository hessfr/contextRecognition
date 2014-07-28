package ch.ethz.wearable.contextrecognition.communication;

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
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;
import ch.ethz.wearable.contextrecognition.utils.AppStatus;
import ch.ethz.wearable.contextrecognition.utils.CustomTimerTask;
import ch.ethz.wearable.contextrecognition.utils.GMM;
import ch.ethz.wearable.contextrecognition.utils.Globals;

/*
 * Intent service that regularly checks if the request to create the initial model on the server 
 * is finished and downloads the new classifiers if so
 */

public class InitModelGet extends IntentService {

	private static final String TAG = "InitModelGet";
	
	public InitModelGet() {
		super("InitModelGet");
		
		Log.d(TAG, "Constructor");
		
	}
	
	@Override
	protected void onHandleIntent(Intent arg0) {

		final String filenameOnServer = arg0.getStringExtra(Globals.CONN_INIT_MODEL_GET_FILENAME);
		final String waitOrNoWait = arg0.getStringExtra(Globals.CONN_INIT_MODEL_GET_WAIT);

		Log.i(TAG, "onHandleIntent");

		long pollingInteval;
		long maxRetry;
		
		if (waitOrNoWait.equals(Globals.NO_WAIT)) {
			pollingInteval = Globals.POLLING_INTERVAL_DEFAULT;
			maxRetry = Globals.MAX_RETRY_DEFAULT;
		} else {
			pollingInteval = Globals.POLLING_INTERVAL_INITIAL_MODEL;
			maxRetry = Globals.MAX_RETRY_INITIAL_MODEL;
		}
		
		final Context context = getBaseContext();
		
		// Now check periodically if the computation on server is finished
		CustomTimerTask task = new CustomTimerTask(context, filenameOnServer, 
				pollingInteval, maxRetry, null, null, null) {

			private int counter;

			public void run() {

				Log.i(TAG, "Waiting for new classifier from server");
				
				Boolean resGet = false;
				
				String[] prevClassnames=null;
				
				//Add parameters to URL
			    List<NameValuePair> par = new LinkedList<NameValuePair>();
		        par.add(new BasicNameValuePair("filename", filenameOnServer));
		        String paramString = URLEncodedUtils.format(par, "utf-8");
		        String URL = Globals.INIT_CLASSIFIER_URL + paramString;
		        
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

			    		//Log.i(TAG, "received string: " + receveivedString);	
			    		
			    		// Abort and return false if computation on server not finished yet:
			    		if (receveivedString.equals("-1")) {
			    			Log.i(TAG, "Server return not ready code");
			    			resGet = false;
			    			
			    		} else {
			    			
			    			jsonString = receveivedString;
			    			
			    			String filename = "GMM.json";
			    			
							// Save the previous class names, so that we can update buffers and threshold value later:
							GMM prevGMM = new GMM("GMM.json");
							prevClassnames = prevGMM.get_string_array();
			    			
				    		// Replace the current GMM with the new one:
				    		File file = new File(Globals.APP_PATH,filename);
				    		
				    		try {
				    			Globals.readWriteLock.writeLock().lock();
				    			FileWriter out = new FileWriter(file);
				                out.write(jsonString);
				                out.close();
				                Globals.readWriteLock.writeLock().unlock();
				    	    }
				    	    catch (IOException e) {
				    	        Log.e("Exception", "File write failed: " + e.toString());
				    	    } 

			    			resGet = true;
			    		}
			    		
			    	} else {
			    		Log.e(TAG, "Invalid response received after GET request");
			    		Log.e(TAG, String.valueOf(response.getStatusLine()));

			    	}
			    	
			    } catch (UnsupportedEncodingException e) {
			        e.printStackTrace();
			    } catch (ClientProtocolException e) {
			        e.printStackTrace();
			    } catch (IOException e) {
			        e.printStackTrace();
			    }

				if (resGet == true) {

					// Model received from the server:
					Log.i(TAG, "New classifier available on server");
					
					// Load the classifier temporarily here to store new context classes to preferences:
					GMM tmpGMM = new GMM("GMM.json");
					
					// Save String array of the context classes to preferences:
					Globals.setStringArrayPref(context, Globals.CONTEXT_CLASSES, tmpGMM.get_string_array());
					
					Log.i(TAG, "Number of context classes: " + tmpGMM.get_n_classes());
					
//					Toast.makeText(context, "Initial classifier loaded", Toast.LENGTH_LONG).show();

					// Set status to updated, so that the AudioWorker can load the new classifier
					AppStatus.getInstance().set(AppStatus.MODEL_UPDATED);
					Log.i(TAG, "New status: model updated");

					// Broadcast this message, that other activities can rebuild their views:
					Intent i = new Intent(Globals.CONN_INIT_MODEL_FINISH);
					i.putExtra(Globals.CONN_INIT_MODEL_PREV_CLASSNAMES, prevClassnames);
					context.sendBroadcast(i);
					
					Log.i(TAG, "IntentService finished");

					this.cancel();

				}

				if (++counter == Globals.MAX_RETRY_INITIAL_MODEL) {
					
					Log.w(TAG, "Server not responded to GET request intitial model");
					
//					Toast.makeText( //-> Toast doesn't work like that! 
//		    			  	context,
//							(String) "Server not reponding, deploying default model, user specific classes "
//									+ "will be requested when server online again ",
//							Toast.LENGTH_LONG).show();
					
					this.cancel();
				}

			}
		};

		Timer timer = new Timer();
		timer.schedule(task, 0, 20000);	//TODO: reset this to correct values!
	}
	

}





