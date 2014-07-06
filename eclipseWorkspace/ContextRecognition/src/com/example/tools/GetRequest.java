package com.example.tools;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.LinkedList;
import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;

public class GetRequest extends AsyncTask<String, Void, String> {
	
	private static final String TAG = "GetRequest";

	@Override
	protected String doInBackground(String... params) {
		
		String filenameOnServer = params[0];
		
		Log.i(TAG,"PostRequest called");
		
		String IP = "192.168.0.23";
	    String PORT = "8080";
	    
	    String URL = "http://" + IP + ":" + PORT + "/?";
	    
	    //Add parameters to URL
	    List<NameValuePair> par = new LinkedList<NameValuePair>();
        par.add(new BasicNameValuePair("filename", filenameOnServer));
        String paramString = URLEncodedUtils.format(par, "utf-8");
        URL += paramString;
        
		HttpClient client = new DefaultHttpClient();
        HttpGet get = new HttpGet(URL);
        
		// Send the POST request:
	    try {
	    	HttpResponse response = client.execute(get);

	    	if (response.getStatusLine().getStatusCode() == 200) {
	    		// This is our new classifier. Overwrite the existing one:
	    		
	    		String jsonString = EntityUtils.toString(response.getEntity());
	    		
	    		//String filename = "111_newGMM_test.json";
	    		String filename = "GMM.json";
	    		
	    		File dir = Environment.getExternalStorageDirectory();
	    		File file = new File(dir,filename);
	    		
	    		try {
	    			FileWriter out = new FileWriter(file);
	                out.write(jsonString);
	                out.close();
	    	    }
	    	    catch (IOException e) {
	    	        Log.e("Exception", "File write failed: " + e.toString());
	    	    } 
	    		
//	    		Log.i(TAG,EntityUtils.toString(response.getEntity()));
	    		
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

	    return null; //TODO
	}
	
	@Override
    protected void onPostExecute(String result) {
    	//super.onPostExecute(result);
    	
    	returnResults();
    }
    
    public String returnResults() {

    	return null;
    }

}





