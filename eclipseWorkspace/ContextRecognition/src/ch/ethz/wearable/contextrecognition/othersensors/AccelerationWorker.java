/* AccelerationWorker.java
 * 
 *  
 *  
 *  Copyright 2011 Julia Seiter, ETH Zurich (jseiter@ethz.ch)
 *  
 */

package ch.ethz.wearable.contextrecognition.othersensors;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;

/*
import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
*/
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import ch.ethz.wearable.contextrecognition.utils.Globals;

public class AccelerationWorker {
	
	
	//Constants
	protected static final String DELIMITER = "\t";
	protected static final String SUFFIXAcc = "acceleration";
	protected static final String EXT = "txt";

	private SensorManager mSensorManager;
	private File mRecFile;
	
	
private SensorEventListener mAccelerationListener = new SensorEventListener() {
	

	@Override
    public void onSensorChanged(SensorEvent event) {
    
	
    	
        switch(event.sensor.getType())
        {
            case Sensor.TYPE_ACCELEROMETER:
                onAccelChanged(event);
            break;
/*
            case Sensor.TYPE_ORIENTATION:
                onCompassChanged(event);
            break;
            
            case Sensor.TYPE_GYROSCOPE:
            	onGyroChanged(event);
            break;*/

        }

    }
	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// TODO Auto-generated method stub
		
	}
};

    public void onAccelChanged(SensorEvent event)
    {
        float aX,aY,aZ;
        //wl.acquire();
        aX = event.values[0];
        aY = event.values[1];
        aZ = event.values[2];
        
        
		String str = System.currentTimeMillis()	+ DELIMITER
		+ event.timestamp   + DELIMITER
		+ event.values[0] 	+ DELIMITER
		+ event.values[1] 	+ DELIMITER
		+ event.values[2] 		+ DELIMITER +System.getProperty("line.separator");
		

		FileOutputStream os;
		try {
			os = new FileOutputStream(mRecFile, true);
			OutputStreamWriter out = new OutputStreamWriter(os, "UTF-8");
			out.write(str);
			out.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
    
    public AccelerationWorker(Context Ctx){
    	mSensorManager= (SensorManager) Ctx.getSystemService(Context.SENSOR_SERVICE);
    	mSensorManager.registerListener(mAccelerationListener, mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),(SensorManager.SENSOR_DELAY_FASTEST));
    	
        try {
        	mRecFile= new File(Globals.getLogPath(), Globals.ACCEL_FILENAME);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 
    }


	
	/*
	public LocationWorker(Context Ctx, String networkProvider){
		// get location manager
		mLocationManager = (LocationManager) Ctx.getSystemService(Context.LOCATION_SERVICE);

		
        try {
        	if(networkProvider.equals(LocationManager.GPS_PROVIDER))
        			mRecFile= Constants.createFile(SUFFIXGPS, EXT,Constants.MODE_EXP);
        	else
        		mRecFile= Constants.createFile(SUFFIXPROVIDER, EXT,Constants.MODE_EXP);
		} catch (IOException e) {
			e.printStackTrace();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}  */
		/*
		mMinTime= Constants.experiment.loc_minTime;
		mMinDistance= Constants.experiment.loc_minDistance;
		
		mLocationManager.requestLocationUpdates(networkProvider, mMinTime, mMinDistance, mLocationListener);
	}*/
	
	public void stop(){
		//mAccelerationManager.removeUpdates(mAccelerationListener);
		mSensorManager.unregisterListener(mAccelerationListener);
		
	}
	

	
	public File getFile(){
		return mRecFile;
	}

};

