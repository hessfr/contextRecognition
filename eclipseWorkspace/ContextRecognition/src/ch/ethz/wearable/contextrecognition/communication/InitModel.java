package ch.ethz.wearable.contextrecognition.communication;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.IntentService;
import android.content.Intent;
import android.util.Log;
import ch.ethz.wearable.contextrecognition.utils.CustomTimerTask;
import ch.ethz.wearable.contextrecognition.utils.Globals;

public class InitModel extends IntentService {

	private static final String TAG = "InitModel";
	
	public InitModel() {
		super("InitModel");
		
		Log.d(TAG, "Constructor");
		
	}
	
	@Override
	protected void onHandleIntent(Intent arg0) {

		final String[] contextClasses = arg0.getStringArrayExtra(Globals.CONN_INIT_MODEL_CLASSES);
		
		Log.i(TAG, "onHandleIntent");

		final long pollingInterval = Globals.POLLING_INTERVAL_DEFAULT;
		final long maxRetries = Globals.MAX_RETRY_DEFAULT;

		CustomTimerTask task = new CustomTimerTask(getBaseContext(),
				null, pollingInterval, maxRetries, null, null, contextClasses) {

			private int counter;
			String filenameOnServer = null;
			String waitOrNoWait = null;
			String[] invalidClassesArray = null;

			public void run() {

					// Create JSON Array that will be passed as parameter:
					JSONArray jsonArr = new JSONArray();
					
					for(int i=0; i<contextClasses.length; i++) {
						jsonArr.put(contextClasses[i]);
					}
	
					String jsonArrayString = jsonArr.toString();	
				
					//Add parameters to URL
				    List<NameValuePair> par = new LinkedList<NameValuePair>();
				    par.add(new BasicNameValuePair("context_classes", jsonArrayString));
				    String paramString = URLEncodedUtils.format(par, "utf-8");
			        String URL = Globals.INIT_CLASSIFIER_URL + paramString;     
			        
//				 	String URL = Globals.INIT_CLASSIFIER_URL;   
			        
			        //Set timeout parameters:
			        HttpParams httpParameters = new BasicHttpParams();
			        int timeoutConnection = 3000;
			        HttpConnectionParams.setConnectionTimeout(httpParameters, timeoutConnection);
			        int timeoutSocket = 5000;
			        HttpConnectionParams.setSoTimeout(httpParameters, timeoutSocket);
			        
			        HttpClient client = new DefaultHttpClient(httpParameters);
				    HttpPost post = new HttpPost(URL);
				    
//				    //Add headers to URL
//				    //post.setHeader("Content-type", "text/plain");
				    post.setHeader("Accept", "application/json");
			    	post.setHeader("Content-type", "application/json");
//					
					String jsonString = null;

			        JSONObject jsonObj = new JSONObject();

			        for (int i = 0; i < contextClasses.length; i++) {
			            try {
			                jsonObj.put(""+i, ""+contextClasses[i]);
			            } catch (Exception e) {
			            	
			            }
			        }	
			        
			        jsonString = jsonObj.toString();
//			        
//			        Log.i(TAG, "JSON String: " + jsonString);

					// Send the POST request:
				    try {
				    	post.setEntity(new StringEntity(jsonString));

				    	HttpResponse response = client.execute(post);
				    	
				    	Log.i(TAG, "InitModel request sent");

				    	if (response.getStatusLine().getStatusCode() == 200) {
				    		String receivedString = EntityUtils.toString(response.getEntity());
				    		
				    		Log.d(TAG, "ReceivedString: " + receivedString);
				    		
				    		JSONObject jsonObject;

							jsonObject = new JSONObject(receivedString);
				    		filenameOnServer = jsonObject.getString("filename");
				    		waitOrNoWait = jsonObject.getString("wait");
				    		JSONArray jsonArrayInvalidClasses = jsonObject.getJSONArray("invalid_classes");
				    		
				    		List<String> invalidClassesList = new ArrayList<String>();
				    		for (int i=0; i<jsonArrayInvalidClasses.length(); i++) {
				    			invalidClassesList.add(jsonArrayInvalidClasses.getString(i) );
				    		}
				    		
				    		invalidClassesArray = new String[invalidClassesList.size()];
				    		invalidClassesArray = invalidClassesList.toArray(invalidClassesArray);

				    		
				    		/*
				    		Log.i(TAG, "ReceivedString: " + receivedString);
				    		
				    		JSONObject jsonObject = new JSONObject(receivedString);
				    		results = new String[2];
				    		results[0] = jsonObject.getString("filename");
				    		results[1] = jsonObject.getString("wait");
				    		
				    		
				    		Log.i(TAG,"filename is " + results[0]);
				    		Log.i(TAG,"wait is " + results[1]);
				    		*/

				    	} else {
				    		Log.e(TAG, "Invalid response received after POST request");
				    		Log.e(TAG, String.valueOf(response.getStatusLine()));

				    	}

				    	
				    } catch (UnsupportedEncodingException e) {
				        e.printStackTrace();
				    } catch (ClientProtocolException e) {
				        e.printStackTrace();
				    } catch (IOException e) {
				        e.printStackTrace();
				    } catch (JSONException e) {
						e.printStackTrace();
					}
				
				if (filenameOnServer != null) {
						
					Log.i(TAG, "Server response to init model request received ");
						
					Intent i = new Intent(Globals.CONN_INIT_MODEL_RECEIVE);
					i.putExtra(Globals.CONN_INIT_MODEL_INVALIDS, invalidClassesArray);
					i.putExtra(Globals.CONN_INIT_MODEL_RESULT_FILENAME, filenameOnServer);
					i.putExtra(Globals.CONN_INIT_MODEL_RESULT_WAIT, waitOrNoWait);		
					sendBroadcast(i);

					Log.i(TAG, "IntentService finished");
					
					this.cancel();
					
				} else {
					
//					Toast.makeText( getBaseContext(), "Your selected classes will be available "
//									+ "in some minutes. Using default classes", Toast.LENGTH_LONG).show();
				}
				
				if (++counter == maxRetries) {
					
					Log.e(TAG, "Server problems: init model request not successful");
					 
					Intent i = new Intent(Globals.CONN_INIT_MODEL_RECEIVE);
					i.putExtra(Globals.CONN_INIT_MODEL_INVALIDS, invalidClassesArray);
					i.putExtra(Globals.CONN_INIT_MODEL_RESULT_FILENAME, filenameOnServer);
					i.putExtra(Globals.CONN_INIT_MODEL_RESULT_WAIT, waitOrNoWait);		
					sendBroadcast(i);
					
					Log.i(TAG, "IntentService finished");

					this.cancel();
				}
			}
		};
		Timer timer = new Timer();
		timer.schedule(task, 0, pollingInterval);
		

	}
	

}
