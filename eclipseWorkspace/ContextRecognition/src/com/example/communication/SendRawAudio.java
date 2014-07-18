package com.example.communication;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.concurrent.ExecutionException;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;

import com.example.tools.Globals;

/*
 * This is the HTTP GET request to download the new model under the given URL. It
 * replaces the current JSON classifier object with the new classifier. The 
 * IncorporateNewClass request has to be call first, in order to initiate the training
 * on the server
 */
@SuppressLint("NewApi")
public class SendRawAudio extends AsyncTask<String, Void, Boolean> {
	
	private static final String TAG = "SendRawAudio";
	
	private Boolean result = false;
	
	@Override
	protected Boolean doInBackground(String... params) {
		
		Log.i(TAG, "SendRawAudio called");
		
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
			File file = new File(Globals.getLogPath(), Globals.AUDIO_FILENAME);
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
	    		
	    		Log.i(TAG, "SendRawAudio successful");
	    		//TODO
	    		
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

	    return result;
	}
	
	@Override
    protected void onPostExecute(Boolean result) {
    	//super.onPostExecute(result);
		
		returnResults();
    }
    
    public Boolean returnResults() {

    	return result;
    }
}





