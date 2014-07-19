package com.example.communication;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
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
import org.apache.http.util.EntityUtils;

import android.app.IntentService;
import android.content.Intent;
import android.util.Log;

import com.example.tools.Globals;

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

        String URL = Globals.RAW_AUDIO_URL;
        
        //Set timeout parameters:
        HttpParams httpParameters = new BasicHttpParams();
        int timeoutConnection = 3000;
        HttpConnectionParams.setConnectionTimeout(httpParameters, timeoutConnection);
        int timeoutSocket = 5000;
        HttpConnectionParams.setSoTimeout(httpParameters, timeoutSocket);
        
		HttpClient client = new DefaultHttpClient(httpParameters);
        HttpPost post = new HttpPost(URL);
        
        // Doesn't work (500 on server: "Illegal end of multipart body.") and deprecated:
//        MultipartEntity multipartEntity = new MultipartEntity(HttpMultipartMode.BROWSER_COMPATIBLE);
//        File file = new File(Globals.getLogPath(), Globals.AUDIO_FILENAME);
//        multipartEntity.addPart("Data", new FileBody(file));
//        post.setEntity(multipartEntity);
        
        // Doesn't work:
//        MultipartEntityBuilder entityBuilder = MultipartEntityBuilder.create();
//        entityBuilder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
//        File file = new File(Globals.getLogPath(), Globals.AUDIO_FILENAME);
//        entityBuilder.addPart("mFile", new FileBody(file));
//        entityBuilder.addBinaryBody("data", file);
//        HttpEntity multiPartEntity = entityBuilder.build();
//        post.setEntity(multiPartEntity);
        
        
        // Works, but is too slow:
        InputStreamEntity reqEntity = null;
		try {
			//File file = new File(Globals.getLogPath(), Globals.AUDIO_FILENAME);
			File file = new File(Globals.APP_PATH, "rawAudio");
			reqEntity = new InputStreamEntity(new FileInputStream(file), -1);
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
	    		
	    		Log.i(TAG, "SendRawAudio successful");
	    		
	    	} else {
	    		Log.e(TAG, "Invalid response received after sending the PUT request");
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
		
		
		
		Intent i = new Intent(Globals.CONN_SEND_RAW_AUDIO_RECEIVE);
		i.putExtra(Globals.CONN_SEND_RAW_AUDIO_RESULT, result);	
		sendBroadcast(i);
		
		
		Log.i(TAG, "IntentService finished");
		
	}
	

}
