package ch.ethz.wearable.contextrecognition.communication;

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

import android.app.IntentService;
import android.content.Intent;
import android.util.Log;
import ch.ethz.wearable.contextrecognition.utils.Globals;

public class CheckClassFeasibility extends IntentService {

	private static final String TAG = "CheckClassFeasibility";
	
	public CheckClassFeasibility() {
		super("CheckClassFeasibility");
		
		Log.d(TAG, "Constructor");
		
	}
	
	@Override
	protected void onHandleIntent(Intent arg0) {

		String className = arg0.getStringExtra(Globals.CONN_CHECK_FEASIBILITY_CLASS_NAME);
		
		String result = null;
		
		Log.i(TAG, "onHandleIntent");

		// Add parameters to URL
		List<NameValuePair> par = new LinkedList<NameValuePair>();
		par.add(new BasicNameValuePair("classname", className));
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

			Log.i(TAG, "CheckClassFeasibility POST request sent");

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

		Intent i = new Intent(Globals.CONN_CHECK_FEASIBILITY_RECEIVE);
		i.putExtra(Globals.CONN_CHECK_FEASIBILITY_RESULT, result);
		i.putExtra(Globals.CONN_CHECK_FEASIBILITY_CLASS_NAME, className);		
		sendBroadcast(i);
		
		
		Log.i(TAG, "IntentService finished");
		
	}
	

}
