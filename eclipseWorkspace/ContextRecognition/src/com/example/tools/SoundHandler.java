package com.example.tools;

import java.util.LinkedList;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

public class SoundHandler extends Thread {
	
	private final String TAG = "SoundHandler"; 
	
	private AudioRecord rec = null;
	
	private boolean currentlyRecording = false;
	
	// If there is more than one sample in the queue, do not continue the recording and process all elements first:
	private boolean processQueueFirst = false; 

//	private int SEQUENCE_LENGTH = 63*1024; // equals 2seconds
	private int SEQUENCE_LENGTH = 63*512; // equals 2seconds
	
	private Object blockSync = new Object();

	// Time interval we wait at least until all elements in the queue are processed:
	private static long SLEEPTIME = 500;
	
	public static final int RECORDER_SAMPLERATE = 16000; //TODO: better define this somewhere else??
	
	private LinkedList<queueElement> queue = new LinkedList<queueElement>(); // contains the raw audio data, each element is 2 seconds long
	private class queueElement{
		public int numSamplesRead;
		public short[] data;
	}
	
	private long prevTime;

	// Constructor:
	public SoundHandler(){		
		super();
	}
	
	private Thread recorderThread = new Thread() {
		
		public void run(){
			while(true){
				
				queueElement newEL = null;
				synchronized(blockSync) {

					//Log.i(TAG, String.valueOf(queue.size()));
					
					if(queue.size() != 0) {
						//Log.i(TAG, "queue size: " + queue.size());
						
						// get new object from queue:
						newEL = queue.poll(); // return the head element of the list and remove it
					} 
				}
				
				// If element is not null process the new data
				if(newEL != null) {

					handleData(newEL.data, newEL.numSamplesRead, newEL.data.length);

					//Log.i(TAG, "Length: " + String.valueOf(newEL.sampleRead));
					//Log.i(TAG, "Framelength: " + String.valueOf(newEL.buffer.length));
				}
				
				// Wait 10ms before getting the next element again: // TODO: is this a good value???
				try {
					sleep(10);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				
			}
		}
	};
	
	public void run(){
		
		// Start the thread
		this.recorderThread.start();
		        
		while(currentlyRecording) {
			
			// Verify that our AudioRecorder is in the correct state
			if(rec.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
				Log.e(TAG,"AudioRecorder in the wrong state");
				break;
			}
			
			//Log.e(TAG, "Queue length: " + queue.size());
			
			// Verify that sequence has the correct length //TODO: if we keep SEQUENCE_LENGTH fixed ,we can remove this
			if(SEQUENCE_LENGTH <= 0) {
				Log.e(TAG,"Invalid buffer size: " + String.valueOf(SEQUENCE_LENGTH));
			}
				
			try{
				short[] data = new short[SEQUENCE_LENGTH]; //TODO: implement this properly

				int nRead = rec.read(data, 0, data.length); //number of recorder samples (equal to SEQUENCE_LENGTH)
				//Log.i(TAG, "nRead: " + nRead);

				if (nRead == AudioRecord.ERROR_INVALID_OPERATION
						|| nRead == AudioRecord.ERROR_BAD_VALUE) {

					Log.e(TAG, "Reading audio failed");

				} else if (nRead < SEQUENCE_LENGTH) {

					Log.e(TAG,"Only " + nRead + " of " + SEQUENCE_LENGTH + " samples were recorded");

				} else {
					queueElement newEL = new queueElement();
					// Fill the new element for the queue:
					newEL.data = data;
					newEL.numSamplesRead = nRead;

					//Log.dTAG, "Queue length: " + queue.size());
					
					/*
					 * If there is already an element in the queue that is not processed yet, send the
					 * recorder thread to sleep and only continue once it is empty
					 */
					while (queue.size() != 0) {
						//Log.d(TAG, "Thread sent to sleep waiting for all elements in the queue to be processed");
						recorderThread.sleep(SLEEPTIME);
					}

					/*
					 * Only add the new element when the queue is empty again and make sure nobody reads
					 * from it at the same time:
					 */
					synchronized(this.blockSync) {

						queue.add(newEL);
						
//							if (prevTime>0) {
//								long diff = System.currentTimeMillis() - prevTime;
//								Log.i(TAG,"new element added to queue after " + diff);
//								prevTime = System.currentTimeMillis();
//							} else{
//								prevTime = System.currentTimeMillis();
//							}
						
					}
				}
			} catch(Exception recordException) {
				Log.e(TAG, "Recorder expection occured");
				recordException.printStackTrace();
			}
			
		}
		
		// Stop and release the recorder when not recording anymore
		this.rec.stop();
		this.rec.release();

	}
	
	private AudioRecord initRec() {
		
		try {
			int src = MediaRecorder.AudioSource.DEFAULT;
			int mono = AudioFormat.CHANNEL_IN_MONO;
			int encoding = AudioFormat.ENCODING_PCM_16BIT;		
			
			// TODO: We have to have SEQUENCE_LENGTH equivalent to 2sec here
			this.rec = new AudioRecord(src, RECORDER_SAMPLERATE, mono,encoding, SEQUENCE_LENGTH*2); // SEQUENCE_LENGTH * 2 ????
			
		} catch(IllegalArgumentException e){
			Log.e(TAG, "Error occured while initializing AudioRecorder");
			e.printStackTrace();
		}
		
		return this.rec;
	}
	
	public void beginRec() {
		
		try {
			// Call initRec() and only continue if successful
			if(this.rec == null) {
				if(initRec() == null)
					return;
			} else {
				// If AudioRecorder is already recording do nothing
				if(rec.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
					Log.i(TAG,"AudioRecorder is already recording");
					return;
				}
			}
			
			// Start the recording
			rec.startRecording();
			this.start();
			this.currentlyRecording = true;
			
		} catch(Exception e) {
			Log.e(TAG, "starting Audiorecorder failed");
			e.printStackTrace();
		}
	}
	
	public void endRec() {
		this.currentlyRecording = false;
	}
	
	/*
	 * Override this method in AudioWorker
	 */
	protected void handleData(short[] data, int length, int frameLength) {
		//Log.d(TAG, "handleData called");
		
	}
}