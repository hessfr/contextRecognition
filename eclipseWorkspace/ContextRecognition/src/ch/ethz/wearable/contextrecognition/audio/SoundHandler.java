package ch.ethz.wearable.contextrecognition.audio;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.preference.PreferenceManager;
import android.util.Log;
import ch.ethz.wearable.contextrecognition.utils.Globals;

import com.google.common.primitives.Shorts;

/*
 * Structure of this class similar to: github.com/sumeetkr
 */
public class SoundHandler extends Thread {
	
	private final String TAG = "SoundHandler"; 
	
	private Context context;
	
	public boolean isRunning = true;
	
	private AudioRecord rec = null;
	
	private boolean currentlyRecording = false;

	private static int BUFFER_LENGTH = 4608; // Size of the chunks of data we read in using AudioRecord.read()
	
	/*
	 * Size of the buffer for the audio recorder. This has to be larger than BUFFER_LENGTH to avoid potential
	 * "over-running" and loss of data
	 */
	private static int AUDIORECORD_BUFFER_LENGTH = 4608 * 2;
	
	/*
	 * One 2s sequence contains 63 32ms windows, the prediction method is called with this buffer
	 * Also, one of this sequences is exactly 7 buffer lengths long
	 * 63 * 0.032 ~= 2s
	 */
	private static int PREDICTION_LENGTH = 32256;
	private static int CHUNKS_PER_PREDICTION = PREDICTION_LENGTH/BUFFER_LENGTH;
	private short[] predictionBuffer;
	private boolean[] silenceDetectionBuffer = new boolean[CHUNKS_PER_PREDICTION]; // true means silent
	private boolean predictionDataAvailable = false;
	
	SharedPreferences mPrefs;
	
	private int pointer = 0; // to fill the sequence for the prediction with the recorded data
	
	private Object blockSync = new Object();
	
	public static final int RECORDER_SAMPLERATE = 16000;
	
	private LinkedList<queueElement> queue = new LinkedList<queueElement>(); // contains the raw audio data, each element is 2 seconds long
	
	private class queueElement{
		@SuppressWarnings("unused")
		public int numSamplesRead;
		public short[] data;
		public boolean[] silenceBuffer;
	}
	
	// Variables used for the permutation:
	private File mPermSeqFile; //initialize this file when using permutation
	private File mRecFile; //initialize this file when using permutation
	int []perm_frameIndex;
	byte[][] perm_Buffer;
	int perm_Pointer;
	int perm_nFrameInBuffer;
	int perm_nBytePerFramInBuffer;
	boolean perm_duplicateLastFrameInBuffer;
	byte [] perm_RestBuffer;
	int perm_RestLen=0;
	long sampleNr=0;
	private int  bytePerSamples=0;
	float PERM_SUBFRAMELENGTH_MOD;

	// Constructor:
	public SoundHandler(Context context){
		super();
		
		this.context = context;	
		
		
//		//--- Initialize variables for the permutation: ---
//		int nSMPBuffer= (int) Math.round(32.0 /(1000.0/16000.0));
//		PERM_SUBFRAMELENGTH_MOD= nSMPBuffer*((float)1000.0/((float)16000));
//
//		perm_nBytePerFramInBuffer = bytePerSamples * nSMPBuffer;
//
//		float n= (float) 1000.0 / PERM_SUBFRAMELENGTH_MOD;
//		perm_nFrameInBuffer = (int) Math.ceil(n);
//		if(n!=(float)perm_nFrameInBuffer)
//			perm_duplicateLastFrameInBuffer=true;
//		else
//			perm_duplicateLastFrameInBuffer=false;
//
//		perm_Buffer = new byte[perm_nFrameInBuffer][perm_nBytePerFramInBuffer];
//		for (int i = 0; i < perm_nFrameInBuffer; i++) {
//			perm_Buffer[i] = new byte[perm_nBytePerFramInBuffer];
//		}
//		perm_Pointer = 0;
//
//		//For debugging:
//		perm_frameIndex= new int[perm_nFrameInBuffer];
//
//		perm_RestBuffer= new byte[perm_nBytePerFramInBuffer];
//		perm_RestLen=0;
//
//		//For debugging:
//		//createFramePermIndex();
//		//---------------------------------------------------

	}
	
	/*
	 * Makes new data available when a new samples arrives. The actual processing will be done
	 * by overriding the handleData method
	 */
	private Thread queueThread = new Thread() {

		public void run() {
			
			while(true) {
				
				queueElement newEL = null;
				synchronized(blockSync) {

					//Log.i(TAG, String.valueOf(queue.size()));
					
					if(queue.size() != 0) {
						
						// get new object from queue:
						newEL = queue.poll(); // return the head element of the list and remove it
					} 
				}
				
				// If element is not null process the new data
				if(newEL != null) {

					handleData(newEL.data, newEL.silenceBuffer);

				}
				
				// Wait 10ms before getting the next element again:
				try {
					sleep(10);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				
			}
		}
		
	};
	
	public void run() {
		
		// Start the thread
		this.queueThread.start();
		        
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
					
					/*
					 *  Fill the prediction buffer ("ring-buffer": if full, overwrite the 
					 *  oldest elements...):
					 */
					System.arraycopy(dataShort, 0, predictionBuffer, (pointer * dataShort.length), dataShort.length);
					
					// Fill the silence detection buffer:
					if (Shorts.max(dataShort) > Globals.SILENCE_DETECTION_THRESHOLD) {
						silenceDetectionBuffer[pointer] = false;
					} else {
						silenceDetectionBuffer[pointer] = true;
					}
					
					
					/*
					 *  Write this chunk of data to a file (for evaluation only), we 
					 *  have to convert short to byte array first, then we can use FileOutputStream:
					 */
					byte[] dataByte = short2byte(dataShort);
					File file = new File(Globals.getLogPath(), Globals.AUDIO_FILENAME);
					appendToFile(dataByte, file);
					
					/*
					 *  Put the current time to preferences, that we can get the exact time when the 
					 *  recording stopped at the next start of the app
					 */
					mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
					SharedPreferences.Editor editor = mPrefs.edit();
					editor.putLong(Globals.LASTEST_RECORDING_TIMESTAMP, System.currentTimeMillis());
					editor.commit();
					
					if (Globals.RECORDING_START_TIME == 0) {
						Globals.RECORDING_START_TIME = System.currentTimeMillis();
						Log.i(TAG, "start recording at time " + Globals.RECORDING_START_TIME);
					}
					
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
		
		// Stop and release the recorder when not recording anymore:
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
			
			// Initialize recorder and only continue if successful:
			if(this.rec == null) {
				
				if(initRec() == null){
					
					return;
				}
					
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
		Log.i(TAG, "endRec()");
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
	
	private void appendToFile(byte[] buffer, File file) {
		try {
			FileOutputStream os = new FileOutputStream(file, true);
			os.write(buffer);
			os.close();      	

		} catch (IOException e) {
			Log.e(TAG, "Error writing to audio data to file");
		}
	}
	
	/*
	 * Permute the audio data for privacy protection, currently not used and not tested:
	 * 
	 * Code from Amir Muaremi, Mirco Rossi, ETH Zurich (mrossi@ethz.ch)
	 */
	@SuppressWarnings("unused")
	private void appendToFilePermuted(byte[] buffer, File file) {
		int bufferPointer=0;

		if(perm_RestLen!=0){
			try{
				System.arraycopy(perm_RestBuffer, 0, perm_Buffer[perm_frameIndex[perm_Pointer]], 0, perm_RestLen);
			}catch(Exception e){
				e.toString();
			}

			System.arraycopy(buffer, bufferPointer, perm_Buffer[perm_frameIndex[perm_Pointer]], perm_RestLen, perm_nBytePerFramInBuffer-perm_RestLen);

			bufferPointer+=perm_nBytePerFramInBuffer-perm_RestLen;
			perm_Pointer++;
			sampleNr+=perm_nBytePerFramInBuffer/bytePerSamples;
			perm_RestLen=0;
			if (perm_Pointer >= perm_nFrameInBuffer) {

				for (int i = 0; i < perm_nFrameInBuffer; i++)
					appendToFile(perm_Buffer[i], mRecFile);

				if (perm_duplicateLastFrameInBuffer) {
					System.arraycopy(perm_Buffer[perm_frameIndex[--perm_Pointer]], 0,perm_Buffer[0], 0, perm_nBytePerFramInBuffer);
					perm_Pointer = 1;
				} else
					perm_Pointer = 0;

				// For debugging:
				createFramePermIndex();

			}
		}



		while((bufferPointer+perm_nBytePerFramInBuffer-1)<buffer.length){
			System.arraycopy(buffer, bufferPointer, perm_Buffer[perm_frameIndex[perm_Pointer]], 0, perm_nBytePerFramInBuffer);


			bufferPointer+=perm_nBytePerFramInBuffer;
			perm_Pointer++;
			sampleNr+=perm_nBytePerFramInBuffer/bytePerSamples;
			if (perm_Pointer >= perm_nFrameInBuffer) {

				for (int i = 0; i < perm_nFrameInBuffer; i++)
					appendToFile(perm_Buffer[i], mRecFile);

				if (perm_duplicateLastFrameInBuffer) {
					System.arraycopy(perm_Buffer[perm_frameIndex[--perm_Pointer]], 0,perm_Buffer[0], 0, perm_nBytePerFramInBuffer);
					perm_Pointer = 1;
				} else
					perm_Pointer = 0;

				// For debugging:
				createFramePermIndex();

			}
		}

		if(bufferPointer!=buffer.length){
			perm_RestLen=buffer.length-bufferPointer;
			System.arraycopy(buffer, bufferPointer, perm_RestBuffer, 0, perm_RestLen); 
		}
	}
	
	/*
	 * Generate a random permutation frame index and store in perm_frameIndex
	 * 
	 * Code from Amir Muaremi, Mirco Rossi, ETH Zurich (mrossi@ethz.ch)
	 */
	private void createFramePermIndex(){
		int stop=perm_nFrameInBuffer;
		int start=0;
		if(perm_duplicateLastFrameInBuffer){
			start=1;
			stop=perm_nFrameInBuffer-1;
			perm_frameIndex[perm_nFrameInBuffer-1]=perm_nFrameInBuffer-1;
			perm_frameIndex[0]=0;
		}

		ArrayList<Integer> pick= new ArrayList<Integer>();
		for(int i=start;i<stop;i++)
			pick.add(i);

		int r;
		for(int i=start;i<stop;i++){
			r=(int) Math.floor(Math.random() * pick.size());
			perm_frameIndex[i]=pick.get(r).intValue();
			pick.remove(r);
		}

		//if(Constants.experiment.audio_perm_saveVer){
		try {
			BufferedWriter out = new BufferedWriter(new FileWriter(mPermSeqFile,true));
			String out_string="";
			for(int i =0; i<perm_frameIndex.length; i++)
				out_string+=perm_frameIndex[i] +",";
			out_string=out_string.substring(0, out_string.length()-1)+"\n";
			out.write(out_string);
			out.close();
		} catch (IOException e) {
			e.printStackTrace();
		}		   
		//}




	}
	
	/*
	 * Override this method in the AudioWorker class
	 */
	protected void handleData(short[] data, boolean[] silenceBuffer) {
		//Log.d(TAG, "handleData called");
		
	}
}