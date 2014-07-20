package ch.ethz.wearable.contextrecognition.tools;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.LinkedList;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import com.google.common.primitives.Shorts;

public class SoundHandler extends Thread {
	
	private final String TAG = "SoundHandler"; 
	
	private AudioRecord rec = null;
	
	private boolean currentlyRecording = false;

	private static int BUFFER_LENGTH = 4608; // Size of the chunks of data we read in using AudioRecord.read()
	
	/*
	 * Size of the buffer for the Audiorecord. This has to be larger than BUFFER_LENGTH to avoid potential
	 * "over-running" and loss of data
	 */
	private static int AUDIORECORD_BUFFER_LENGTH = 4608 * 2;
	
	/*
	 * One 2s sequence contains 63 32ms windows, the prediction method is called with this buffer
	 * Also, one of this sequences is exactly 7 buffer lengths long
	 * 63 * 512 ~= 2s
	 */
	private static int PREDICTION_LENGTH = 32256;
	private static int CHUNKS_PER_PREDICTION = PREDICTION_LENGTH/BUFFER_LENGTH;
	private short[] predictionBuffer;
	private boolean[] silenceDetectionBuffer = new boolean[CHUNKS_PER_PREDICTION]; // true means silent
	private boolean predictionDataAvailable = false;
	
	private int pointer = 0; // to fill the sequence for the prediction with the recorded data
	
	private Object blockSync = new Object();
	
	public static final int RECORDER_SAMPLERATE = 16000;
	
	private LinkedList<queueElement> queue = new LinkedList<queueElement>(); // contains the raw audio data, each element is 2 seconds long
	private class queueElement{
		public int numSamplesRead;
		public short[] data;
		public boolean[] silenceBuffer;
	}

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

					handleData(newEL.data, newEL.silenceBuffer);

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
				
			try {
				short[] dataShort = new short[BUFFER_LENGTH];
				int nRead = rec.read(dataShort, 0, dataShort.length);
				
				
				if (nRead == AudioRecord.ERROR_INVALID_OPERATION
						|| nRead == AudioRecord.ERROR_BAD_VALUE) {

					Log.e(TAG, "Reading audio failed");

				} else if (nRead < BUFFER_LENGTH) {

					Log.e(TAG,"Only " + nRead + " of " + BUFFER_LENGTH + " samples were recorded");

				} else {
					
					// Log.i(TAG, "Loudness: " + Shorts.max(dataShort));
					
					// Fill the prediction buffer ("ring-buffer": if full, overwrite the oldest elements...)
					System.arraycopy(dataShort, 0, predictionBuffer, (pointer * dataShort.length), dataShort.length);
					
					// Fill the silence detection buffer:
					if (Shorts.max(dataShort) > Globals.SILENCE_DETECTION_THRESHOLD) {
						silenceDetectionBuffer[pointer] = false;
					} else {
						silenceDetectionBuffer[pointer] = true;
					}
					
					
					// Write this chunk of data to a file (for evaluation only):
					// we have to convert short to byte array first, then we can use FileOutputStream:
//					Log.w(TAG, "dataShort: " + dataShort[0]);
					byte[] dataByte = short2byte(dataShort);
//					Log.w(TAG, "dataShort: " + dataShort[0]);
//					Log.w(TAG, "-------------------");
					File file = new File(Globals.getLogPath(), Globals.AUDIO_FILENAME);
					appendToFile(dataByte, file);
					
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
							newEL.silenceBuffer = silenceDetectionBuffer;

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
			
			this.rec = new AudioRecord(src, RECORDER_SAMPLERATE, mono,encoding, AUDIORECORD_BUFFER_LENGTH);
			
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
	protected void handleData(short[] data, boolean[] silenceBuffer) {
		//Log.d(TAG, "handleData called");
		
	}
}