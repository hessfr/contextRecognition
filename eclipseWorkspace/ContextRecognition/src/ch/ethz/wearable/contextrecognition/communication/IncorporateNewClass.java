package ch.ethz.wearable.contextrecognition.communication;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
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

import android.app.IntentService;
import android.content.Intent;
import android.util.Log;
import ch.ethz.wearable.contextrecognition.utils.CustomTimerTask;
import ch.ethz.wearable.contextrecognition.utils.Globals;

public class IncorporateNewClass extends IntentService {

	private static final String TAG = "IncorporateNewClass";
	
	public IncorporateNewClass() {
		super("IncorporateNewClass");
		
		Log.d(TAG, "Constructor");
		
	}
	
	@Override
	protected void onHandleIntent(Intent arg0) {

		Log.i(TAG, "onHandleIntent");
		
		final String newClassName = arg0.getStringExtra(Globals.CONN_INCORPORATE_NEW_CLASS_NAME);
		final String feasibilityCheckResult = arg0.getStringExtra(Globals.CONN_CHECK_FEASIBILITY_RESULT);

		
		final long pollingInterval = Globals.POLLING_INTERVAL_DEFAULT;
		final long maxRetries = Globals.MAX_RETRY_DEFAULT;

		CustomTimerTask task = new CustomTimerTask(getBaseContext(),
				null, pollingInterval, maxRetries, newClassName, feasibilityCheckResult, null) {

			private int counter;
			String filenameOnServer = null;

			public void run() {

				//Add parameters to URL
			    List<NameValuePair> par = new LinkedList<NameValuePair>();
		        par.add(new BasicNameValuePair("new_classname", newClassName));
		        String paramString = URLEncodedUtils.format(par, "utf-8");
		        String URL = Globals.ADD_CLASS_URL + paramString;     
		        
		        //Log.i(TAG, URL);
		        
		        //Set timeout parameters:
		        HttpParams httpParameters = new BasicHttpParams();
		        int timeoutConnection = 3000;
		        HttpConnectionParams.setConnectionTimeout(httpParameters, timeoutConnection);
		        int timeoutSocket = 5000;
		        HttpConnectionParams.setSoTimeout(httpParameters, timeoutSocket);
		        
		        HttpClient client = new DefaultHttpClient(httpParameters);
			    HttpPost post = new HttpPost(URL);
			    
//			    //Add headers to URL
//			    //post.setHeader("Content-type", "text/plain");
			    post.setHeader("Accept", "application/json");
		    	post.setHeader("Content-type", "application/json");
		    	
		    	// Read our classifier from the storage into a string:
		    	String filename = "GMM.json";

				File file = new File(Globals.APP_PATH,filename);
				
				String jsonString = null;
				
				if(file.exists()) {
					
					try {
						Globals.readWriteLock.readLock().lock();
						BufferedReader br = new BufferedReader(new FileReader(file));
						
						StringBuffer strBuffer = new StringBuffer();
						char[] buf = new char[1024];
						int numRead = 0;
						while((numRead=br.read(buf)) != -1){
				            String readData = String.valueOf(buf, 0, numRead);
				            strBuffer.append(readData);
				        }
						
						br.close();
						Globals.readWriteLock.readLock().unlock();
						
						jsonString = strBuffer.toString();

						//Log.i(TAG, jsonString);
						
					} catch (IOException e) {
						Log.e(TAG,"Couldn't open JSON file");
						e.printStackTrace();
					}
					
				} else {
					Log.e(TAG, "File does not exist: " + file.toString());
		        }

				// Send the POST request:
			    try {
			    	post.setEntity(new StringEntity(jsonString, "UTF-8"));

			    	HttpResponse response = client.execute(post);
			    	
			    	Log.i(TAG, "IncorporateNewClass request sent");

			    	if (response.getStatusLine().getStatusCode() == 200) {
			    		
			    		filenameOnServer = EntityUtils.toString(response.getEntity());
			    		
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
			    } 

				Log.i(TAG, "IntentService finished");
				
				if (filenameOnServer != null) {
					
					Log.i(TAG, "IncorporateNewClass request successful");
					
				    Intent i = new Intent(Globals.CONN_INCORPORATE_NEW_CLASS_RECEIVE);
					i.putExtra(Globals.CONN_INCORPORATE_NEW_CLASS_FILENAME, filenameOnServer);
					i.putExtra(Globals.CONN_CHECK_FEASIBILITY_RESULT ,feasibilityCheckResult);
					sendBroadcast(i);

					Log.i(TAG, "IntentService finished");
					
					this.cancel();
				}
				
				if (++counter == maxRetries) {
					Log.e(TAG, "Server problems: IncorporateNewClass request not successful");
					
					Intent i = new Intent(Globals.CONN_INCORPORATE_NEW_CLASS_RECEIVE);
					i.putExtra(Globals.CONN_INCORPORATE_NEW_CLASS_FILENAME, filenameOnServer);
					i.putExtra(Globals.CONN_CHECK_FEASIBILITY_RESULT ,feasibilityCheckResult);
					sendBroadcast(i);					
					
					Log.i(TAG, "IntentService finished");
					
					this.cancel();
				}

			}
		};
		Timer timer = new Timer();
		timer.schedule(task, 0, pollingInterval);
		
		Log.i(TAG, "IntentService finished");
		

		
	}

}
