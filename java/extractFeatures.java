import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.AudioInputStream;
import java.io.FileWriter;
import java.io.Writer;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class extractFeatures {
    
	private static int SAMPLERATE = 16000;
	private static double WINDOW_LENGTH = 0.032;
	
	private static int FFT_SIZE = 8192; 	//need to change this????
	private static int MFCCS_VALUE = 12;
	private static int MEL_BANDS = 20;
	
	private transient FFT featureFFT = null;
	private transient MFCC featureMFCC = null;
	private transient Window featureWin = null;

    private ArrayList<double[]> extractFile(String fileName) {    	
    	
    	int frameSize = (int)Math.round(SAMPLERATE * WINDOW_LENGTH);
        int totalSamplesRead = 0;
        int numFrame = 0;
        
		featureFFT = new FFT(FFT_SIZE);
	    featureWin = new Window(frameSize); //correct????????
	    featureMFCC = new MFCC(FFT_SIZE, MFCCS_VALUE, MEL_BANDS, SAMPLERATE);
        
		double fftBufferR[] = new double[FFT_SIZE];
    	double fftBufferI[] = new double[FFT_SIZE];
    	double featureCepstrum[] = new double[MFCCS_VALUE];
	    
    	ArrayList<double[]> features = new ArrayList<double[]>();
    	
        File fileIn = new File(fileName);
        
        try {
          AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(fileIn);
          int bytesPerFrame = audioInputStream.getFormat().getFrameSize();
    
            if (bytesPerFrame == AudioSystem.NOT_SPECIFIED) {
            bytesPerFrame = 1;
          } 
    
          int numBytes = frameSize * bytesPerFrame; 
          byte[] audioBytes = new byte[numBytes]; //actual data will be written to this array later
    
          try {
            int numBytesRead = 0;
            int numSamplesRead = 0;
            // Try to read numBytes bytes from the file.
            while ((numBytesRead = audioInputStream.read(audioBytes)) != -1) {
            	
				numSamplesRead = numBytesRead / bytesPerFrame;
				totalSamplesRead += numSamplesRead;
				numFrame += 1;
  
				// Frequency analysis
				Arrays.fill(fftBufferR, 0);
				Arrays.fill(fftBufferI, 0);
				
				// Convert audio buffer to doubles
				for (int i = 0; i < audioBytes.length; i++)
						{
					fftBufferR[i] = audioBytes[i];
				}
				
				// In-place windowing
				featureWin.applyWindow(fftBufferR);
				
				// In-place FFT
				featureFFT.fft(fftBufferR, fftBufferI);
				
				// Get MFCCs
				featureCepstrum = featureMFCC.cepstrum(fftBufferR, fftBufferI);	           
				
				features.add(featureCepstrum);
				
				//System.out.println(String.valueOf(featureCepstrum[0]));
            }
          } catch (Exception e) { 
          	  e.printStackTrace();
          }
        } catch (Exception e) {
        	e.printStackTrace();
        }
        
        System.out.println(String.valueOf(features.size()));
        
        System.out.println("totalSamplesRead: " + String.valueOf(totalSamplesRead));
        System.out.println("numFrame: " + String.valueOf(numFrame));
        
        return features;
        
    }
    
    private void saveGson(ArrayList<double[]> data, String outFile) {
		try {
			// Save data as JSON:
			String fileName = outFile + ".json";
			Writer writer = new FileWriter(fileName);
	
			Gson gson = new GsonBuilder().create();
			gson.toJson(data, writer);
	
			writer.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
    
	private ArrayList<double[]> extractFolder(String folderName) {
		File folder = new File(folderName);
		File[] listOfFiles = folder.listFiles();

		//This array contais all features of the folder:
		ArrayList<double[]> allFeatures = new ArrayList<double[]>();
		
		for (int i = 0; i < listOfFiles.length; i++) {
			
			if (listOfFiles[i].isFile()) {
				
				//TODO: use more stable method to check file type:
				String ext = listOfFiles[i].getName().substring(listOfFiles[i].getName().lastIndexOf('.'));
				
				//Check if wav-file:
				if (ext.equals(".wav")) {
					String fileName = listOfFiles[i].getName();
					ArrayList<double[]> currentFeatures = new ArrayList<double[]>();
					
					//Extract features for current file:
					currentFeatures = extractFile(fileName);
					
					//Add extracted features to arrayList of all features of the class (=folder):
					allFeatures.addAll(currentFeatures);
				}
			} 
		}
		
		return allFeatures;
	}
	
    public static void main(String[] args) { 
    	if(args.length > 0) {
    		
    		extractFeatures extractor = new extractFeatures();
    		
    		ArrayList<double[]> arrComplete = new ArrayList<double[]>();
    		
    		arrComplete = extractor.extractFolder(System.getProperty("user.dir"));
    		
    		extractor.saveGson(arrComplete,"outfile");
    		
    	} else {
    		System.out.println("Please provide wav-file as parameter");
    	}

    }
}



















