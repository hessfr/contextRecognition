package com.example.tools;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.LinkedList;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Environment;
import android.util.Log;

import com.example.contextrecognition.Globals;

public class SoundHandler extends Thread {
	
	private final String TAG = "SoundHandler"; 
	
	private AudioRecord rec = null;
	
	private boolean currentlyRecording = false;
	
	// If there is more than one sample in the queue, do not continue the recording and process all elements first:
	private boolean processQueueFirst = false; 

	private static int BUFFER_LENGTH = 4608; //4096; // Size of the chunks of data we read in using AudioRecord.read()
	
	/*
	 * Size of the buffer for the Audiorecord. This has to be larger than BUFFER_LENGTH to avoid potential
	 * "over-running" and loss of data
	 */
	private static int AUDIORECORD_BUFFER = 4608 * 10; //10 * 4096;
	
	/*
	 * One 2s sequence contains 63 32ms windows, the prediction method is called with this buffer
	 * Also, one of this sequences is exactly 7 buffer lengths long
	 * 63 * 512 ~= 2s
	 */
	private static int PREDICTION_LENGTH = 32256;
	private short[] predictionBuffer;
	private boolean predictionDataAvailable = false;
	
	private int pointer = 0; // to fill the sequence for the prediction with the recorded data
	
	private Object blockSync = new Object();
	
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
				
			try{
//				byte[] data = new byte[BUFFER_LENGTH];
//				int nRead = rec.read(data, 0, data.length);
				
				short[] dataShort = new short[BUFFER_LENGTH];
				int nRead = rec.read(dataShort, 0, dataShort.length);
				
				byte[] dataByte = short2byte(dataShort);
				appendToFile(dataByte, Globals.AUDIO_FILE);
				
				
				
				
				//Log.i(TAG, "byte: " + data[0] + " " + data[1] + " " + data[2]);
				//Log.i(TAG, "short: " + dataShort[0] + " " + dataShort[1] + " " + dataShort[2]);
				//Log.i(TAG, "-------------");
				
				//Log.i(TAG, "nRead: " + nRead);

				
				
				if (nRead == AudioRecord.ERROR_INVALID_OPERATION
						|| nRead == AudioRecord.ERROR_BAD_VALUE) {

					Log.e(TAG, "Reading audio failed");

				} else if (nRead < BUFFER_LENGTH) {

					Log.e(TAG,"Only " + nRead + " of " + BUFFER_LENGTH + " samples were recorded");

				} else {
					
					// Write this chunk of data to a file (for evaluation only):
					
					// Fill the prediction buffer ("ring-buffer": if full, overwrite the oldest elements...)
					System.arraycopy(dataShort, 0, predictionBuffer, (pointer * dataShort.length), dataShort.length);
					
					if (pointer < 6) {
						pointer++;
					} else {
						predictionDataAvailable = true;
						pointer = 0;
					}
					
					if(predictionDataAvailable == true) {
						/*
						 * Add element (to make a prediction) only if there is no other element in
						 * the queue. Otherwise don't do anything (i.e. wait until all other elements
						 * in the queue are processed
						 */
						if (queue.size() == 0) {
							queueElement newEL = new queueElement();
							
							// Add the new element to the queue:
							newEL.data = predictionBuffer;
							newEL.numSamplesRead = nRead;

							//Log.dTAG, "Queue length: " + queue.size());

							/*
							 * Only add the new element when the queue is empty again and make sure nobody reads
							 * from it at the same time:
							 */
							synchronized(this.blockSync) {
								queue.add(newEL);
								//Log.i(TAG, "Element added to queue");
							}
							
							// After we added the element to the queue, "clear" the prediction buffer
							predictionBuffer = new short[PREDICTION_LENGTH];
							pointer = 0;
							predictionDataAvailable = false;
							Log.d(TAG, "PredictionBuffer cleared");
						}
						
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
			
			this.rec = new AudioRecord(src, RECORDER_SAMPLERATE, mono,encoding, AUDIORECORD_BUFFER);
			
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
			
			predictionBuffer = new short[PREDICTION_LENGTH];
			
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
	 * Code from http://audiorecordandroid.blogspot.in/
	 */
	// Conversion of short to byte
	private byte[] short2byte(short[] sData) {
		int shortArrsize = sData.length;
		byte[] bytes = new byte[shortArrsize * 2];

		for (int i = 0; i < shortArrsize; i++) {
			bytes[i * 2] = (byte) (sData[i] & 0x00FF);
			bytes[(i * 2) + 1] = (byte) (sData[i] >> 8);
			sData[i] = 0;
		}
		return bytes;
	}
	
	private void appendToFile(byte[] buffer, File file){
		try {
			FileOutputStream os = new FileOutputStream(file, true); // appending to file
			os.write(buffer);
			os.close();      	

		} catch (IOException e) {
			Log.e(TAG, "Error writing to audio data to file");
		}
	}
	
	/*
	 * Override this method in AudioWorker
	 */
	protected void handleData(short[] data, int length, int frameLength) {
		//Log.d(TAG, "handleData called");
		
	}
}