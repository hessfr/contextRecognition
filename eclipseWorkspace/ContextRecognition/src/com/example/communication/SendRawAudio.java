package com.example.communication;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

import android.os.AsyncTask;
import android.util.Log;

import com.example.contextrecognition.Globals;

/*
 * This is the HTTP GET request to download the new model under the given URL. It
 * replaces the current JSON classifier object with the new classifier. The 
 * IncorporateNewClass request has to be call first, in order to initiate the training
 * on the server
 */
public class SendRawAudio extends AsyncTask<String, Void, Boolean> {
	
	private static final String TAG = "SendRawAudio";
	
	private Boolean result = false;
	
	@Override
	protected Boolean doInBackground(String... params) {
		
		/*
		HttpURLConnection connection = null;
	    DataOutputStream outputStream = null;
	    //DataInputStream inputStream = null;
	    String urlServer = Globals.PUT_RAW_AUDIO;
	    String lineEnd = "\r\n";
	    String twoHyphens = "--";
	    String boundary =  "*****";
	    String serverResponseMessage;
	    //int serverResponseCode;

	    int bytesRead, bytesAvailable, bufferSize;
	    byte[] buffer;
	    int maxBufferSize = 1*1024*1024;
		
	    try
	    {

	        FileInputStream fileInputStream = new FileInputStream(Globals.TEST_FILE);

	        URL url = new URL(urlServer);
	        connection = (HttpURLConnection) url.openConnection();

	        // Allow Inputs &amp; Outputs.
	        connection.setDoInput(true);
	        connection.setDoOutput(true);
	        connection.setUseCaches(false);

	        // Set HTTP method to POST.
	        connection.setRequestMethod("POST");

	        connection.setRequestProperty("Connection", "Keep-Alive");
	        connection.setRequestProperty("Content-Type", "multipart/form-data;boundary="+boundary);

	        outputStream = new DataOutputStream( connection.getOutputStream() );
	        outputStream.writeBytes(twoHyphens + boundary + lineEnd);
	        //outputStream.writeBytes("Content-Disposition: form-data; name=\"uploadedfile\";filename=\""+"rawAudio"+"\"" + lineEnd);
	        outputStream.writeBytes(lineEnd);

	        bytesAvailable = fileInputStream.available();
	        bufferSize = Math.min(bytesAvailable, maxBufferSize);
	        buffer = new byte[bufferSize];

	        // Read file
	        bytesRead = fileInputStream.read(buffer, 0, bufferSize);

	        while (bytesRead > 0)
	        {
	            outputStream.write(buffer, 0, bufferSize);
	            bytesAvailable = fileInputStream.available();
	            bufferSize = Math.min(bytesAvailable, maxBufferSize);
	            bytesRead = fileInputStream.read(buffer, 0, bufferSize);
	        }

	        outputStream.writeBytes(lineEnd);
	        outputStream.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);

	        serverResponseMessage = connection.getResponseMessage();

	        fileInputStream.close();
	        outputStream.flush();
	        outputStream.close();
	    }
	    catch (Exception ex)
	    {
	        ex.printStackTrace();
	    }
	    */
		
		
		
        String URL = Globals.PUT_RAW_AUDIO_URL;
        
        //Set timeout parameters:
        HttpParams httpParameters = new BasicHttpParams();
        int timeoutConnection = 3000;
        HttpConnectionParams.setConnectionTimeout(httpParameters, timeoutConnection);
        int timeoutSocket = 5000;
        HttpConnectionParams.setSoTimeout(httpParameters, timeoutSocket);
        
		HttpClient client = new DefaultHttpClient(httpParameters);
        HttpPost post = new HttpPost(URL);
        
        
        // Doesn't work:
//        MultipartEntityBuilder entityBuilder = MultipartEntityBuilder.create();
//        entityBuilder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
//        entityBuilder.addPart("mFile", new FileBody(Globals.AUDIO_FILE));
//        entityBuilder.addBinaryBody("data", Globals.AUDIO_FILE);
//        HttpEntity multiPartEntity = entityBuilder.build();

        
      //FileEntity fileEntity = new FileEntity(Globals.AUDIO_FILE, "binary/octet-stream");
        InputStreamEntity reqEntity = null;
		try {
//			post.setEntity(multiPartEntity);
			
			// Works, but is too slow:
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





