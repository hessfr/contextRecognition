package ch.ethz.wearable.contextrecognition.communication;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

import android.app.IntentService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;
import ch.ethz.wearable.contextrecognition.tools.Globals;

public class SendRawAudio extends IntentService {

	private static final String TAG = "SendRawAudio";
	
	public SendRawAudio() {
		super("SendRawAudio");
		
		Log.d(TAG, "Constructor");
		
	}
	
	@Override
	protected void onHandleIntent(Intent arg0) {

		boolean result = false;
		
		Log.i(TAG, "onHandleIntent");
		
		// Generate string of the current data, e.g. 20140720
//		Calendar cal = Calendar.getInstance();
//		Date currentLocalTime = cal.getTime();
//		DateFormat date = new SimpleDateFormat("yyyMMdd");
//		String dateString = date.format(currentLocalTime);
		
		// Loop through all the log folders and transfer every raw audio data file is it exists:
		File[] elementsInAppFolder = Globals.APP_PATH.listFiles();
		for (File inFile : elementsInAppFolder) {
		    if (inFile.isDirectory()) {
		    	
		    	File rawAudio = new File(inFile, "rawAudio");
		    	if(rawAudio.exists()) {
		    		
		    		Log.d(TAG, "Raw audio file exists in folder: " + inFile);
		    		
		    		// Get the userID from the preferences:
		    		SharedPreferences mPrefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
		    		
		    		String userId = mPrefs.getString(Globals.USER_ID, "");
		    		if (userId.equals("")) {
		    			Log.e(TAG, "Couldn't find valid user id in preferences, maybe the assignment method failed");
		    		}
		    		
		    		// Extract the date string from the folder name:
		    		String dateString = inFile.toString().substring(inFile.toString().indexOf("_") + 1);
		    		Log.d(TAG, "----- " + dateString);
		    		
		    		// Add parameters to URL
		    		List<NameValuePair> par = new LinkedList<NameValuePair>();
		    		par.add(new BasicNameValuePair("user_id", userId));
		    		par.add(new BasicNameValuePair("date", dateString));
		    		String paramString = URLEncodedUtils.format(par, "utf-8");
		            String URL = Globals.RAW_AUDIO_URL + paramString;
		            
		            //Set timeout parameters:
		            HttpParams httpParameters = new BasicHttpParams();
		            int timeoutConnection = 3000;
		            HttpConnectionParams.setConnectionTimeout(httpParameters, timeoutConnection);
		            int timeoutSocket = 5000;
		            HttpConnectionParams.setSoTimeout(httpParameters, timeoutSocket);
		            
		    		HttpClient client = new DefaultHttpClient(httpParameters);
		            HttpPost post = new HttpPost(URL);
		            
		            // Doesn't work (500 on server: "Illegal end of multipart body.") and deprecated:
//		            MultipartEntity multipartEntity = new MultipartEntity(HttpMultipartMode.BROWSER_COMPATIBLE);
//		            File file = new File(Globals.getLogPath(), Globals.AUDIO_FILENAME);
//		            multipartEntity.addPart("Data", new FileBody(file));
//		            post.setEntity(multipartEntity);
		            
		            // Doesn't work:
//		            MultipartEntityBuilder entityBuilder = MultipartEntityBuilder.create();
//		            entityBuilder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
//		            File file = new File(Globals.getLogPath(), Globals.AUDIO_FILENAME);
//		            entityBuilder.addPart("mFile", new FileBody(file));
//		            entityBuilder.addBinaryBody("data", file);
//		            HttpEntity multiPartEntity = entityBuilder.build();
//		            post.setEntity(multiPartEntity);
		            
		            
		            // Works, but is too slow:
		            InputStreamEntity reqEntity = null;
		    		try {
		    			//File file = new File(Globals.getLogPath(), Globals.AUDIO_FILENAME);
		    			//File file = new File(Globals.APP_PATH, "rawAudio");
		    			reqEntity = new InputStreamEntity(new FileInputStream(rawAudio), -1);
		    			reqEntity.setContentType("binary/octet-stream");
		    	        reqEntity.setChunked(true);
		    	        
		    	        post.setEntity(reqEntity);
		    		} catch (FileNotFoundException e1) {
		    			Log.e(TAG, "File not found");
		    			e1.printStackTrace();
		    		}     
		            
		    	    try {
		    	    	
		    	        HttpResponse response = client.execute(post); 

		    	    	if (response.getStatusLine().getStatusCode() == 200) {
		    	    		
		    	    		result = true;
		    	    		
		    	    		Log.i(TAG, "Raw audio file successfully transfered to server");
		    	    		
		    	    		//delete the rawAudio file on the device:
		    	    		rawAudio.delete();
		    	    		
		    	    	} else {
		    	    		Log.e(TAG, "Invalid response received after sending the SendRawAudio request");
		    	    		Log.e(TAG, String.valueOf(response.getStatusLine()));

		    	    	}
		    	    	
		    	    	client.getConnectionManager().shutdown(); 
		    	    	
		    	    } catch (UnsupportedEncodingException e) {
		    	        e.printStackTrace();
		    	    } catch (ClientProtocolException e) {
		    	        e.printStackTrace();
		    	    } catch (IOException e) {
		    	        e.printStackTrace();
		    	    }
		    	    
		    	}
		    }
		}



		Intent i = new Intent(Globals.CONN_SEND_RAW_AUDIO_RECEIVE);
		i.putExtra(Globals.CONN_SEND_RAW_AUDIO_RESULT, result);	
		sendBroadcast(i);
		
		
		Log.i(TAG, "IntentService finished");
		
	}
	

}
