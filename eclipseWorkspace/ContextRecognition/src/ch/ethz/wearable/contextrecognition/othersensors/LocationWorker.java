/* LocationWorker.java
 * 
 *  
 *  
 *  Copyright 2011 Mirco Rossi, ETH Zurich (mrossi@ethz.ch)
 *  
 */


package ch.ethz.wearable.contextrecognition.othersensors;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;

import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import ch.ethz.wearable.contextrecognition.utils.Globals;

public class LocationWorker {
	
	// Parameters
	private long mMinTime = 10 * 60 * 1000; // = 10min
	private float mMinDistance = 10; // meters
	
	//Constants
	protected static final String DELIMITER = "\t";
	protected static final String SUFFIXPROVIDER = "locationNetwork";
	protected static final String SUFFIXGPS = "locationGPS";
	protected static final String EXT = "txt";
	protected static final int GPS=0;
	protected static final int NETWORK_PROVIDER=1;
	
	//Objects
	private LocationManager mLocationManager;
	private File mRecFile;
	
	
private LocationListener mLocationListener = new LocationListener() {
		
		public void onStatusChanged(String provider, int status, Bundle extras) {
			
		}
		
		public void onProviderEnabled(String provider) {
			
		}
		
		public void onProviderDisabled(String provider) {
			
		}
		
		public void onLocationChanged(Location location) {
			// System.currentTimeMillis()==location.getTime()
			String str = location.getTime() 		+ DELIMITER
						+ location.getAccuracy() 	+ DELIMITER
						+ location.getLatitude() 	+ DELIMITER
						+ location.getLongitude() 	+ DELIMITER
						+ location.getAltitude() 	+ DELIMITER
						+ location.getBearing() 	+ DELIMITER
						+ location.getSpeed() 		+ DELIMITER +System.getProperty("line.separator");
						

            	FileOutputStream os;
				try {
					os = new FileOutputStream(mRecFile, true);
	            	OutputStreamWriter out = new OutputStreamWriter(os, "UTF-8");
	            	out.write(str);
	            	out.close();
				} catch (FileNotFoundException e) {
					e.printStackTrace();
				} catch (UnsupportedEncodingException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
	};
	
	
	public LocationWorker(Context Ctx, String networkProvider){
		// get location manager
		mLocationManager = (LocationManager) Ctx.getSystemService(Context.LOCATION_SERVICE);

		
        try {
        	if(networkProvider.equals(LocationManager.GPS_PROVIDER))
    			mRecFile= new File(Globals.getLogPath(), Globals.GPS_FILENAME);
        	else
        		mRecFile= new File(Globals.getLogPath(), Globals.NETWORK_FILENAME);
		} catch (Exception e) {
			e.printStackTrace();
		}  
		
		mLocationManager.requestLocationUpdates(networkProvider, mMinTime, mMinDistance, mLocationListener);
	}
	
	public void stop(){
		mLocationManager.removeUpdates(mLocationListener);
	}
	
	public File getFile(){
		return mRecFile;
	}

}

