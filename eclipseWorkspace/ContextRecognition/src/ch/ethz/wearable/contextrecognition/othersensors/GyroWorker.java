/* GyroWorker.java
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

public class GyroWorker {
	

	
	//Constants
	protected static final String DELIMITER = "\t";
	protected static final String SUFFIXGyro = "gyroscope";
	protected static final String EXT = "txt";
	private SensorManager mSensorManager;
	private File mRecFile;
	
	
private SensorEventListener mGyroListener = new SensorEventListener() {
	

	@Override
    public void onSensorChanged(SensorEvent event) {
    
    	//wl.acquire();  	
    	
        switch(event.sensor.getType())
        {

            case Sensor.TYPE_GYROSCOPE:
            	onGyroChanged(event);
            break;

        }

    }
	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		
	}
};

    public void onGyroChanged(SensorEvent event)
    {
        
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
			e.printStackTrace();
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
    public GyroWorker(Context Ctx){
    	mSensorManager= (SensorManager) Ctx.getSystemService(Context.SENSOR_SERVICE);
    	mSensorManager.registerListener(mGyroListener, mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE),(SensorManager.SENSOR_DELAY_FASTEST));
    	
        try {
        	mRecFile= new File(Globals.getLogPath(), Globals.GYRO_FILENAME);
		} catch (Exception e) {
			e.printStackTrace();
		} 
    }


	
	
	public void stop(){
		mSensorManager.unregisterListener(mGyroListener);
		
	}
	

	
	public File getFile(){
		return mRecFile;
	}

};
