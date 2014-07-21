package ch.ethz.wearable.contextrecognition.communication;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;

import android.os.AsyncTask;
import android.util.Log;
import ch.ethz.wearable.contextrecognition.utils.Globals;

/*
 * This request initiates the server to check if it is feasible to train the given context class name, i.e.
 * if there are enough sound file available on freesound to create a trained classifier
 */

public class GetKnownClasses extends AsyncTask<String, Void, String[]> {

	private static final String TAG = "GetKnownClasses";

	private String[] result=null;

	@Override
	protected String[] doInBackground(String... params) {

		Log.i(TAG, "GetKnownClasses called");

		String URL = Globals.GET_KNOWN_CLASSES_URL;

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
				
				String jsonString = EntityUtils.toString(response.getEntity());
				Log.d(TAG, "Received JSON string of GetKnownClasses request: " + jsonString);
				jsonString = jsonString.substring(1, jsonString.length()-1);
				jsonString = jsonString.replace("\\", "");
				
				JSONArray jsonArray;
				List<String> list = new ArrayList<String>();
				try {
					jsonArray = new JSONArray(jsonString);
					
					for (int i=0; i<jsonArray.length(); i++) {
					    list.add(jsonArray.getString(i) );
					}
					
					// Convert to array:
					result = new String[list.size()];
					list.toArray(result);
				} catch (JSONException e) {
					e.printStackTrace();
				}

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
	protected void onPostExecute(String[] result) {
		// super.onPostExecute(result);

		returnResults();
	}

	public String[] returnResults() {

		return result;
	}

}