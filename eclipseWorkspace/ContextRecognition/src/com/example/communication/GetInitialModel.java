package com.example.communication;

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
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.util.EntityUtils;

import android.os.AsyncTask;
import android.util.Log;

import com.example.tools.Globals;

/*
 * This is the HTTP GET request to download the new model under the given URL. It
 * replaces the current JSON classifier object with the new classifier. The 
 * IncorporateNewClass request has to be call first, in order to initiate the training
 * on the server
 */
public class GetInitialModel extends AsyncTask<String, Void, Boolean> {
	
	private static final String TAG = "GetInitialModel";
	
	private Boolean result = false;
	
	@Override
	protected Boolean doInBackground(String... params) {
		
		String filenameOnServer = params[0];
		
		Log.d(TAG,"GetRequest called");

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

	    		// Abort and return false if computation on server not finished yet:
	    		if (receveivedString.equals("-1")) {
	    			Log.w(TAG, "Server return not ready code");
	    			return false;
	    			
	    		} else {
	    			
	    			jsonString = receveivedString;
	    			
	    		}

	    		//TODO: stop prediction here
	    		
	    		// Replace the current GMM with the new one:
	    		String filename = "GMM.json";
	    		
	    		File file = new File(Globals.APP_PATH,filename);
	    		
	    		try {
	    			FileWriter out = new FileWriter(file);
	                out.write(jsonString);
	                out.close();
	                
	    	    }
	    	    catch (IOException e) {
	    	        Log.e("Exception", "File write failed: " + e.toString());
	    	    } 
	    		
	    		result = true;
	    		
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





