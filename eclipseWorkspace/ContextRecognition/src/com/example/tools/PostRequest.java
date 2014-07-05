package com.example.tools;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import android.os.AsyncTask;
import android.util.Log;

import com.google.gson.GsonBuilder;

public class PostRequest extends AsyncTask<Void, Void, Void> {
	
	private static final String TAG = "PostRequest";
	
//	public void postData() {
//		
//
//	}

	@Override
	protected Void doInBackground(Void... params) {
		
		Log.i(TAG,"PostRequest called");
		
		String IP = "192.168.0.23";
	    String PORT = "8080";
	    
	    String URL = "http://" + IP + ":" + PORT + "/?";
	    
	    //Add parameters to URL
	    List<NameValuePair> par = new LinkedList<NameValuePair>();
        par.add(new BasicNameValuePair("request_type", "addContextClass"));
        String paramString = URLEncodedUtils.format(par, "utf-8");
        URL += paramString;     
        
        //Log.i(TAG, URL);
        
        HttpClient client = new DefaultHttpClient();
	    HttpPost post = new HttpPost(URL);
	    
	    //Add headers to URL
	    //post.setHeader("Content-type", "text/plain");
	    post.setHeader("Accept", "application/json");
    	post.setHeader("Content-type", "application/json");

	    
	    //Add data:
	    Map<String, String> comment = new HashMap<String, String>();
	    comment.put("GMM", "thisIsMyGMMObject");
	    comment.put("className", "nameOfNewClass");
	    comment.put("moreData", "moreData");
	    comment.put("moreDatamoreData", "moreDatamoreData");
	    String json = new GsonBuilder().create().toJson(comment, Map.class);

	    try {
	    	
	    	post.setEntity(new StringEntity(json, "UTF-8"));
	    	
	    	HttpResponse response = client.execute(post);
	    	Log.w(TAG, response.getStatusLine().toString());
	    	Log.w(TAG, EntityUtils.toString(response.getEntity()));
	    } catch (UnsupportedEncodingException e) {
	        e.printStackTrace();
	    } catch (ClientProtocolException e) {
	        e.printStackTrace();
	    } catch (IOException e) {
	        e.printStackTrace();
	    }

	    
	    return null;
	}
}
