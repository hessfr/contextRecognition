package ch.ethz.wearable.contextrecognition.utils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.utils.IOUtils;
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

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.BatteryManager;
import android.preference.PreferenceManager;
import android.util.Log;
/*
 * This IntentService compresses every logging folders (expect the one of the current date) into a tar.gz file
 * and deletes the folder when done. After that it will start a TimerTask to transfer the data to the server.
 * 
 * As folders are deleted when the compression was successful, this service can also be called to only transfer
 * the data when compress is already done.
 */
public class CompressAndSendData extends IntentService {

	private static final String TAG = "CompressAndSendData";
	
//	private Context context;
//	
//	// Constructor:
//	public CompressAndSendData(Context context) {
//		this.context = context;
//	}
	
	public CompressAndSendData() {
		super("CompressAndSendData");
		
		Log.d(TAG, "Constructor");
		
	}
	
	@Override
	protected void onHandleIntent(Intent arg0) {

		Log.i(TAG, "onHandleIntent");

		// Loop through all the log folders:
		File[] elementsInAppFolder = Globals.APP_PATH.listFiles();
		for (File logFolder : elementsInAppFolder) {
			if (logFolder.isDirectory()) {
				
				// Ignore the one from today:
				if (!logFolder.getPath().equals(Globals.getLogPath().getPath())) {
					
					// Compress each folder:
					Log.i(TAG, "Zipping of folder " + logFolder.getName() + " started");

					String zipFilename = logFolder.getName() + ".tar.gz";
					File outputFile = new File(Globals.APP_PATH, zipFilename);

					try {
						
						compressFile(logFolder, outputFile);
						
						Log.i(TAG, "Finished compressing folder " + logFolder.getName());
						
						// Delete folder after that (we have to delete all individual files first):
						Log.i(TAG, "Deleting folder " + logFolder.getName());
						
						String[] elementsInFolder = logFolder.list();
						for(String el: elementsInFolder){
						    File file = new File(logFolder.getPath(), el);
						    file.delete();
						}
						logFolder.delete();
						
					} catch (IOException e) {
						e.printStackTrace();
					}

				} else {
					Log.i(TAG, "Folder " + logFolder.getName() + " not zipped, as it the log folder from today");
				}
				
			}
		}

		// Now call the IntentService to send to data to the server:
		sendToServer();
			
	}
	
	private void sendToServer() {
		
		 Log.i(TAG, "Method to transfer experiment data to server called");
		
		final long pollingInterval = Globals.POLLING_INTERVAL_UPLOAD;
		final long maxRetries = Globals.MAX_RETRY_UPLOAD;
		
		CustomTimerTask task = new CustomTimerTask(getBaseContext(),
				null, pollingInterval, maxRetries, null, null, null) {

			private int counter;
			ArrayList<Boolean> resultList = new ArrayList<Boolean>();

			public void run() {
				
				// Loop through all the log folders and transfer every raw audio data file is it exists:
				File[] elementsInAppFolder = Globals.APP_PATH.listFiles();
				for (File zipFile : elementsInAppFolder) {
					
			    	if(zipFile.getName().contains(".tar.gz")) {
			    		
			    		Log.i(TAG, "Zip file " + zipFile + " will be transfered");
			    		
			    		ConnectivityManager connManager = (ConnectivityManager) getBaseContext().getSystemService(Context.CONNECTIVITY_SERVICE);
			    		NetworkInfo mWifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

			    		/*
			    		 * Code for wifi and battery check from: 
			    		 * http://stackoverflow.com/questions/5283491/android-check-if-device-is-plugged-in
			    		 */
			    		Intent intent = getBaseContext().registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
			            int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
			    		
			            boolean wifiAndCharging = false;
			            
			    		if (mWifi.isConnected()) {
			    			if (plugged == BatteryManager.BATTERY_PLUGGED_AC || plugged == BatteryManager.BATTERY_PLUGGED_USB) {
			    				
			    				// WIFI connected and charging:
			    				wifiAndCharging = true;
			    			}
			    		}
			    		
			    		if (wifiAndCharging == true) {
				    		// Get the userID from the preferences:
				    		SharedPreferences mPrefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
				    		
				    		String userId = mPrefs.getString(Globals.USER_ID, "");
				    		if (userId.equals("")) {
				    			Log.e(TAG, "Couldn't find valid user id in preferences, maybe the assignment method failed");
				    		}
				    		
				    		// Extract the date string from the folder name:
				    		String dateString = zipFile.toString().substring(zipFile.toString().indexOf("_") + 1);
				    		
				    		// Add parameters to URL
				    		List<NameValuePair> par = new LinkedList<NameValuePair>();
				    		par.add(new BasicNameValuePair("user_id", userId));
				    		par.add(new BasicNameValuePair("date", dateString));
				    		String paramString = URLEncodedUtils.format(par, "utf-8");
				            String URL = Globals.RAW_AUDIO_URL + paramString;
				            
				            //Set timeout parameters:
				            HttpParams httpParameters = new BasicHttpParams();
				            int timeoutConnection = 60 * 1000; // equals 1min
				            HttpConnectionParams.setConnectionTimeout(httpParameters, timeoutConnection);
				            int timeoutSocket = 60 * 1000; // equals 1min
				            HttpConnectionParams.setSoTimeout(httpParameters, timeoutSocket);
				            
				    		HttpClient client = new DefaultHttpClient(httpParameters);
				            HttpPost post = new HttpPost(URL);
				            
				            InputStreamEntity reqEntity = null;
				    		try {
				    			//File file = new File(Globals.getLogPath(), Globals.AUDIO_FILENAME);
				    			//File file = new File(Globals.APP_PATH, "rawAudio");
				    			reqEntity = new InputStreamEntity(new FileInputStream(zipFile), -1);
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
				    	    		
				    	    		resultList.add(true);
				    	    		
				    	    		Log.i(TAG, "Raw audio file successfully transfered to server");
				    	    		
				    	    		//delete the rawAudio file on the device:
				    	    		zipFile.delete();
				    	    		
				    	    	} else {
				    	    		resultList.add(false);
				    	    		Log.e(TAG, "Invalid response received after sending the SendRawAudio request");
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
			    		} else {
			    			
			    			Log.e(TAG, "Wifi not connected / not charging. Files won't be transferred to server");
			    			resultList.add(false);
			    		}
			    	}
				}

				// This end result will only become true, if ALL files were transfered successfully
				boolean endResult = true;
				
				for(int i=0; i<resultList.size(); i++) {
					if(resultList.get(i) == false) {
						endResult = false;
					}
				}
				
				if (endResult == true) {
					Log.i(TAG, "Transfering of raw audio data to server successful");
					
					Intent i = new Intent(Globals.CONN_SEND_RAW_AUDIO_RECEIVE);
					i.putExtra(Globals.CONN_SEND_RAW_AUDIO_RESULT, endResult);	
					getBaseContext().sendBroadcast(i);

					Log.i(TAG, "IntentService finished");
					
					this.cancel();
				}
				
				if (++counter == maxRetries) {
					Log.e(TAG, "Raw audio data could not be transfer to server");
					
					Intent i = new Intent(Globals.CONN_SEND_RAW_AUDIO_RECEIVE);
					i.putExtra(Globals.CONN_SEND_RAW_AUDIO_RESULT, endResult);	
					getBaseContext().sendBroadcast(i);
					
					Log.i(TAG, "IntentService finished");
					
					this.cancel();
				}

			}
		};
		Timer timer = new Timer();
		timer.schedule(task, 0, pollingInterval);
		
		
	}
	
	/**
	 * Code from http://sloanseaman.com/wordpress/2012/05/22/tar-and-gzip-compression-in-java/
	 * 
	 * Compress (tar.gz) the input file (or directory) to the output file
	 * <p/>
	 *
	 * In the case of a directory all files within the directory (and all nested
	 * directories) will be added to the archive
	 *
	 * @param file The file(s if a directory) to compress
	 * @param output The resulting output file (should end in .tar.gz)
	 * @throws IOException
	 */
	private static void compressFile(File file, File output)
		throws IOException
	{
		ArrayList<File> list = new ArrayList<File>(1);
		list.add(file);
		compressFiles(list, output);
	}

	/**
	 * Code from http://sloanseaman.com/wordpress/2012/05/22/tar-and-gzip-compression-in-java/
	 * 
	 * Compress (tar.gz) the input files to the output file
	 *
	 * @param files The files to compress
	 * @param output The resulting output file (should end in .tar.gz)
	 * @throws IOException
	 */
	private static void compressFiles(Collection<File> files, File output)
		throws IOException
	{
		Log.d(TAG, "Compressing "+files.size() + " to " + output.getAbsoluteFile());
	               // Create the output stream for the output file
		FileOutputStream fos = new FileOutputStream(output);
	               // Wrap the output file stream in streams that will tar and gzip everything
		TarArchiveOutputStream taos = new TarArchiveOutputStream(
			new GZIPOutputStream(new BufferedOutputStream(fos)));
	               // TAR has an 8 gig file limit by default, this gets around that
		taos.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_STAR); // to get past the 8 gig limit
	               // TAR originally didn't support long file names, so enable the support for it
		taos.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);

	               // Get to putting all the files in the compressed output file
		for (File f : files) {
			addFilesToCompression(taos, f, ".");
		}

	               // Close everything up
		taos.close();
		fos.close();
	}

	/**
	 * Code from http://sloanseaman.com/wordpress/2012/05/22/tar-and-gzip-compression-in-java/
	 * 
	 * Does the work of compression and going recursive for nested directories
	 * <p/>
	 *
	 * Borrowed heavily from http://www.thoughtspark.org/node/53
	 *
	 * @param taos The archive
	 * @param file The file to add to the archive
	        * @param dir The directory that should serve as the parent directory in the archivew
	 * @throws IOException
	 */
	private static void addFilesToCompression(TarArchiveOutputStream taos, File file, String dir)
		throws IOException
	{
	               // Create an entry for the file
		taos.putArchiveEntry(new TarArchiveEntry(file, dir + "/" +file.getName()));
		if (file.isFile()) {
	                       // Add the file to the archive
			BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file));
			IOUtils.copy(bis, taos);
			taos.closeArchiveEntry();
			bis.close();
		}
		else if (file.isDirectory()) {
	                       // close the archive entry
			taos.closeArchiveEntry();
	                       // go through all the files in the directory and using recursion, add them to the archive
			for (File childFile : file.listFiles()) {
				addFilesToCompression(taos, childFile, file.getName());
			}
		}
	}
}
