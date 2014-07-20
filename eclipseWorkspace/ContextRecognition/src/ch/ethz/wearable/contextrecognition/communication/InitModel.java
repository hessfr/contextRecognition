package ch.ethz.wearable.contextrecognition.communication;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;

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
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.os.AsyncTask;
import android.util.Log;
import ch.ethz.wearable.contextrecognition.tools.Globals;

/*
 * When the selected context classes at the very first start are different then the default
 * ones, this Task is called to request the classifier from the server
 */
public class InitModel extends AsyncTask<String[], Void, String[]> {
	
	private static final String TAG = "IncorporateNewClass";

    /*
     *  Contains response from the server:
     *  1. string: Indicates if we have to wait for the classifier to be trained
     *  2. string: Filename under which the adapted GMM can be requested from the server
     */
	
    private String[] results=null;
    
	@Override
	protected String[] doInBackground(String[]... params) {
		
		String[] contextClasses = params[0];
		
		Log.i(TAG,"InitClassifier request called");
	    
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
	    
//	    //Add headers to URL
//	    //post.setHeader("Content-type", "text/plain");
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

	    return results;
	}
	
	@Override
    protected void onPostExecute(String[] result) {
    	
    	returnResults();
    }
    
    public String[] returnResults() {

    	return results;
    }

}




