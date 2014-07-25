package ch.ethz.wearable.contextrecognition.communication;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Timer;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.util.EntityUtils;
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
			String[] results = null;

			public void run() {
				
				 String URL = Globals.INIT_CLASSIFIER_URL;     
			        
			        //Log.i(TAG, URL);
			        
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
					
					String jsonString = null;

			        JSONObject jsonObj = new JSONObject();

			        for (int i = 0; i < contextClasses.length; i++) {
			            try {
			                jsonObj.put(""+i, ""+contextClasses[i]);
			            } catch (Exception e) {
			            	
			            }
			        }	
			        
			        jsonString = jsonObj.toString();
			        
			        Log.i(TAG, "JSON String: " + jsonString);

					// Send the POST request:
				    try {
				    	post.setEntity(new StringEntity(jsonString, "UTF-8"));

				    	HttpResponse response = client.execute(post);
				    	
				    	Log.i(TAG, "POST request sent");

				    	if (response.getStatusLine().getStatusCode() == 200) {
				    		String receivedString = EntityUtils.toString(response.getEntity());
				    		
				    		Log.i(TAG, "ReceivedString: " + receivedString);
				    		
				    		JSONObject jsonObject = new JSONObject(receivedString);
				    		results = new String[2];
				    		results[0] = jsonObject.getString("filename");
				    		results[1] = jsonObject.getString("wait");
				    		
				    		
				    		Log.i(TAG,"filename is " + results[0]);
				    		Log.i(TAG,"wait is " + results[1]);

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
				
				if (results != null) {
					
					Log.i(TAG, "InitModel request successful");
					
					Intent i = new Intent(Globals.CONN_INIT_MODEL_RECEIVE);
					i.putExtra(Globals.CONN_INIT_MODEL_RESULT_FILENAME, results[0]);
					i.putExtra(Globals.CONN_INIT_MODEL_RESULT_WAIT, results[1]);		
					sendBroadcast(i);

					Log.i(TAG, "IntentService finished");
					
					this.cancel();
				} else {
					
//					Toast.makeText( getBaseContext(), "Your selected classes will be available "
//									+ "in some minutes. Using default classes", Toast.LENGTH_LONG).show();
				}
				
				if (++counter == maxRetries) {
					
					 Log.w(TAG, "Server not responded to initial model request");
					 
//						Toast.makeText(
//								getBaseContext(),
//								(String) "Server not reponding, please try again later",
//								Toast.LENGTH_LONG).show();
					
					this.cancel();
				}
			}
		};
		Timer timer = new Timer();
		timer.schedule(task, 0, pollingInterval);
		
		Log.i(TAG, "IntentService finished");
		
	}
	

}
