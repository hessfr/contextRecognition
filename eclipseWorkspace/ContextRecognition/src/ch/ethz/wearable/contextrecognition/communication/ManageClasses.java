package ch.ethz.wearable.contextrecognition.communication;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
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
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;
import ch.ethz.wearable.contextrecognition.utils.CustomTimerTask;
import ch.ethz.wearable.contextrecognition.utils.DisplayToast;
import ch.ethz.wearable.contextrecognition.utils.Globals;

/*
 * This intent service sends the request to change the model (remove and/or add new classes)
 * to the server and returns a list of invalid classes, a flag to indicate if we have to wait
 * and the filename on the server. In case the server is not reachable, it retries it several times
 */
public class ManageClasses extends IntentService {

	private static final String TAG = "ManageClasses";
	
	Handler handler;
	
	public ManageClasses() {
		super("ManageClasses");
		
		Log.d(TAG, "Constructor");
		
	}
	
	@Override
	public void onCreate() {
	    super.onCreate();
	    handler = new Handler();
	}
	
	@Override
	protected void onHandleIntent(Intent arg0) {

		Log.i(TAG, "onHandleIntent");
		
		final String[] classesArray = arg0.getStringArrayExtra(Globals.CONN_MANAGE_CLASSES_ARRAY);

		final long pollingInterval = Globals.POLLING_INTERVAL_DEFAULT;
		final long maxRetries = Globals.MAX_RETRY_DEFAULT;

		final Context context = getBaseContext();
		
		CustomTimerTask task = new CustomTimerTask(getBaseContext(),
				null, pollingInterval, maxRetries, null, null, classesArray) {

			private int counter;
			String filenameOnServer = null;
			String waitOrNoWait = null;
			String[] invalidClassesArray = null;

			public void run() {

				// Create JSON Array that will be passed as parameter:
				JSONArray jsonArr = new JSONArray();
				
				for(int i=0; i<classesArray.length; i++) {
					jsonArr.put(classesArray[i]);
				}

				String jsonArrayString = jsonArr.toString();				
				
				//Add parameters to URL
			    List<NameValuePair> par = new LinkedList<NameValuePair>();
			    par.add(new BasicNameValuePair("context_classes", jsonArrayString));
			    String paramString = URLEncodedUtils.format(par, "utf-8");
		        String URL = Globals.MANAGE_CLASSES_URL + paramString;     
		        
		        //Set timeout parameters:
		        HttpParams httpParameters = new BasicHttpParams();
		        int timeoutConnection = 10000;
		        HttpConnectionParams.setConnectionTimeout(httpParameters, timeoutConnection);
		        int timeoutSocket = 30000;
		        HttpConnectionParams.setSoTimeout(httpParameters, timeoutSocket);
		        
		        HttpClient client = new DefaultHttpClient(httpParameters);
			    HttpPost post = new HttpPost(URL);
			    
			    //Add headers to URL
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
			    	
			    	Log.i(TAG, "ManageClasses request sent");

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

			    		// Set the CLASSES_BEING_ADDED / CLASSES_BEING_REMOVED in the preferences:
			    		List<String> classesInNewModel = new ArrayList<String>();
			    		String[] currentClasses = Globals.getStringArrayPref(context, Globals.CONTEXT_CLASSES);
			    		List<String> classesBeingAddedList = new ArrayList<String>();
			    		List<String> classesBeingRemovedList = new ArrayList<String>();

			    		// Don't consider the invalid classes:
			    		for(int i=0; i<classesArray.length; i++) {
			    			if (!invalidClassesList.contains(classesArray[i])) {
			    				classesInNewModel.add(classesArray[i]);
			    			}
			    		}

			    		// Find classes that are were not in the previous model:
			    		for(int i=0; i<classesInNewModel.size(); i++) {
			    			if (!Arrays.asList(currentClasses).contains(classesInNewModel.get(i))) {
			    				classesBeingAddedList.add(classesInNewModel.get(i));
			    			}
			    		}
			    		
			    		// Find classes that are not in the new model anymore:
			    		for(int i=0; i<currentClasses.length; i++) {
			    			if (!classesInNewModel.contains(currentClasses[i])) {
			    				classesBeingRemovedList.add(currentClasses[i]);
			    			}
			    		}
			    		
			    		// Create the String arrays and push them to preferences:
			    		String[] classesBeingAdded = new String[classesBeingAddedList.size()];
			    		classesBeingAdded = classesBeingAddedList.toArray(classesBeingAdded);
			    		
			    		String[] classesBeingRemoved = new String[classesBeingRemovedList.size()];
			    		classesBeingRemoved = classesBeingRemovedList.toArray(classesBeingRemoved);
			    		
			    		Globals.setStringArrayPref(context, Globals.CLASSES_BEING_ADDED, classesBeingAdded);
			    		Globals.setStringArrayPref(context, Globals.CLASSES_BEING_REMOVED, classesBeingRemoved);
			    		
			    		/*
			    		 *  Broadcast to MainActivity that CLASSES_BEING_ADDED array changed, so
			    		 *  that we can update the ground truth logger
			    		 */
			    		Intent i = new Intent(Globals.CLASSES_BEING_ADDED_INTENT);
			    		context.sendBroadcast(i);
			    		
			    		handler.post(new DisplayToast(getBaseContext(), "New model will be trained on"
			    				+ " our server. This might take a while"));
			    		
			    	} else {
			    		Log.e(TAG, "Invalid response received after ManageClasses POST request");
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
					
					Log.i(TAG, "Server response to manage classes request received ");
					
				    Intent i = new Intent(Globals.CONN_MANAGE_CLASSES_RECEIVE);
					i.putExtra(Globals.CONN_MANAGE_CLASSES_INVALIDS, invalidClassesArray);
					i.putExtra(Globals.CONN_MANAGE_CLASSES_WAIT, waitOrNoWait);
					i.putExtra(Globals.CONN_MANAGE_CLASSES_FILENAME ,filenameOnServer);
					context.sendBroadcast(i);

					Log.d(TAG, "IntentService finished");
					
					this.cancel();
				}
				
				if (++counter == maxRetries) {
					
					Log.w(TAG, "Server problems: manage classes request not successful");
					
					handler.post(new DisplayToast(getBaseContext(), "Server not responding"));
					
				    Intent i = new Intent(Globals.CONN_MANAGE_CLASSES_RECEIVE);
					i.putExtra(Globals.CONN_MANAGE_CLASSES_INVALIDS, invalidClassesArray);
					i.putExtra(Globals.CONN_MANAGE_CLASSES_WAIT, waitOrNoWait);
					i.putExtra(Globals.CONN_MANAGE_CLASSES_FILENAME ,filenameOnServer);
					context.sendBroadcast(i);					
					
					Log.d(TAG, "IntentService finished");
					
					this.cancel();
				}
			}
		};
		Timer timer = new Timer();
		timer.schedule(task, 0, pollingInterval);
		
		Log.i(TAG, "IntentService finished");
	}

}
