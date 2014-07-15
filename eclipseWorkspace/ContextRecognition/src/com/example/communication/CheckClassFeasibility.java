package com.example.communication;

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
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.util.EntityUtils;

import android.os.AsyncTask;
import android.util.Log;

import com.example.contextrecognition.Globals;

/*
 * This request initiates the server to check if it is feasible to train the given context class name, i.e.
 * if there are enough sound file available on freesound to create a trained classifier
 */

public class CheckClassFeasibility extends AsyncTask<String, Void, String> {

	private static final String TAG = "PostRequest";

	private String result=null;

	@Override
	protected String doInBackground(String... params) {

		String newClassName = params[0];

		Log.i(TAG, "PostRequest called");

		// Add parameters to URL
		List<NameValuePair> par = new LinkedList<NameValuePair>();
		par.add(new BasicNameValuePair("classname", newClassName));
		String paramString = URLEncodedUtils.format(par, "utf-8");
		String URL = Globals.FEASIBILITY_CHECK_URL + paramString;

		// Log.i(TAG, URL);

		// Set timeout parameters:
		HttpParams httpParameters = new BasicHttpParams();
		int timeoutConnection = 3000;
		HttpConnectionParams.setConnectionTimeout(httpParameters,
				timeoutConnection);
		int timeoutSocket = 5000;
		HttpConnectionParams.setSoTimeout(httpParameters, timeoutSocket);

		HttpClient client = new DefaultHttpClient(httpParameters);
		HttpPost post = new HttpPost(URL);

		// Send the POST request:
		try {
			
			HttpResponse response = client.execute(post);

			Log.i(TAG, "POST request sent");

			if (response.getStatusLine().getStatusCode() == 200) {
				result = EntityUtils.toString(response.getEntity());
				
				Log.d(TAG, "Result of feasibility check: " + result);

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

		return result;
	}

	@Override
	protected void onPostExecute(String result) {
		// super.onPostExecute(result);

		returnResults();
	}

	public String returnResults() {

		return result;
	}

}