import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.lang.Double;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.AudioInputStream;
import java.io.FileWriter;
import java.io.Writer;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.common.primitives.Shorts;

/*
 * This class extracts 12 MFCC coefficients and the log energy of given sound files or
 * folders for 32ms windows and stores it in a JSON file under the key "features".
 * 
 * Additionally the maximum amplitude value of each window is stored under the 
 * key "amps". Note that "features" and "amps" always have the exact same length
 *
 * USAGE:
 * java -cp .:\* extractFeatures --file /path/to/audio.wav will create JSON file in the
 * same directory
 *
 * java -cp .:\* extractFeatures --folder ContextClass will extract features from all 
 * files stored in ../sound/ContextClass/ and save it in ../extractedFeatures/ContextClass.json
 */
public class extractFeatures {

	private static int SAMPLERATE = 16000;
	private static double WINDOW_LENGTH = 0.032;

	private static int FFT_SIZE = 8192;
	private static int MFCCS_VALUE = 12;
	private static int MEL_BANDS = 20;

	private transient FFT featureFFT = null;
	private transient MFCC featureMFCC = null;
	private transient Window featureWin = null;

	private HashMap extractFile(String fileName) {

		int frameSize = (int) Math.round(SAMPLERATE * WINDOW_LENGTH);
		int totalSamplesRead = 0;

		featureFFT = new FFT(FFT_SIZE);
		featureWin = new Window(frameSize);
		featureMFCC = new MFCC(FFT_SIZE, MFCCS_VALUE, MEL_BANDS, SAMPLERATE);

		double fftBufferR[] = new double[FFT_SIZE];
		double fftBufferI[] = new double[FFT_SIZE];
		double featureCepstrum[] = new double[MFCCS_VALUE];
		double featureArray[] = new double[MFCCS_VALUE + 1]; // also include the log energy
																
		double logEnergy;



        // ArrayList containing the features:
		ArrayList<double[]> features = new ArrayList<double[]>();
        
        // ArrayList containing the max amplitude value of one window to calculate
        // silece later:
        ArrayList<Short> amps = new ArrayList<Short>();

		File fileIn = new File(fileName);

		try {
			System.out.println("Converting " + fileName);

			AudioInputStream audioInputStream = AudioSystem
					.getAudioInputStream(fileIn);
			int bytesPerFrame = audioInputStream.getFormat().getFrameSize();

			if (bytesPerFrame == AudioSystem.NOT_SPECIFIED) {
				bytesPerFrame = 1;
			}

			int numBytes = frameSize * bytesPerFrame;
			// System.out.println("Number bytes: " + numBytes); // = 1024 data will be written to this array
			byte[] audioBytes = new byte[numBytes];  
													 
			/*
			 * We need the data in short (16-bit) format instead of byte
			 * (8-bit), so we have to convert each chunk of data
			 */
			int shortBufferLength = (int) audioBytes.length / 2; // = 512
			short[] audioShorts = new short[shortBufferLength];

			int numBytesRead = 0;

			boolean isValid = true;

			try {
				// Try to read numBytes bytes from the file.
				while ((numBytesRead = audioInputStream.read(audioBytes)) != -1) {

					/*
					 * Convert to 16-bit (short) first:
					 * 
					 * Conversion formula from http://stackoverflow.com/questions/12314635/reading-wav-wave-file-into-short-array
					 */
					for (int i = 0; i < shortBufferLength; i++) {

						audioShorts[i] = (short) ((audioBytes[i * 2] & 0xff) | (audioBytes[i * 2 + 1] << 8));
					}

					// Frequency analysis
					Arrays.fill(fftBufferR, 0);
					Arrays.fill(fftBufferI, 0);

					// Convert audio buffer to doubles
					for (int i = 0; i < audioShorts.length; i++) {
						fftBufferR[i] = audioShorts[i];
					}

					// In-place windowing
					featureWin.applyWindow(fftBufferR);

					// In-place FFT
					featureFFT.fft(fftBufferR, fftBufferI);

					// Get MFCCs
					featureCepstrum = featureMFCC.cepstrum(fftBufferR,
							fftBufferI);

					isValid = true;

					for (int j = 0; j <= MFCCS_VALUE - 1; j++) {

						if (java.lang.Double.isInfinite(featureCepstrum[j])) {
							System.out
									.println("Problems occurred when reading file "
											+ fileName
											+ " parts of the file were skipped.");
							isValid = false;
						}
					}

					if (isValid == true) {

//						// Without log energy:
//						features.add(featureCepstrum);

						// Calculate log energy:
						logEnergy = calcLogEnergy(audioShorts);

						// Combine the MFCCs and the log energy into one array:
						System.arraycopy(featureCepstrum, 0, featureArray, 0,
								featureCepstrum.length);
						featureArray[featureCepstrum.length] = logEnergy;

						features.add(featureArray);

						featureArray = new double[MFCCS_VALUE + 1];
                    
                        // Store the maximum aplitude for silence detection:
                        amps.add(Shorts.max(audioShorts));
					}



				}
			} catch (Exception e) {
				System.out.println("exception...");
				e.printStackTrace();
			}
		} catch (Exception e) {
			System.out.println("exception...");
			e.printStackTrace();
		}

        HashMap hm = new HashMap();
        hm.put("features", features);
        hm.put("amps", amps);

		return hm;

	}

	private void saveGson(HashMap hm, String outFile) {
		try {

			// Save data as JSON:
			String fileName = outFile + ".json";
			Writer writer = new FileWriter(fileName);

			Gson gson = new GsonBuilder().create();
			gson.toJson(hm, writer);

			writer.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private HashMap extractFolder(String folderName) {
		File folder = new File(folderName);
		File[] listOfFiles = folder.listFiles();

		// This array contais all features of the folder:
		ArrayList<double[]> allFeatures = new ArrayList<double[]>();
		ArrayList<Short> allAmps = new ArrayList<Short>();

        HashMap hm = new HashMap();

		for (int i = 0; i < listOfFiles.length; i++) {

			if (listOfFiles[i].isFile()) {

				// TODO: use more stable method to check file type:
				String ext = listOfFiles[i].getName().substring(
						listOfFiles[i].getName().lastIndexOf('.'));

				// Check if wav-file:
				if (ext.equals(".wav")) {
					String fileName = listOfFiles[i].getName();

    
					String filePath = folderName + "/" + fileName;

					System.out.println("Extracting " + fileName);

					// Extract features for current file:
					hm = extractFile(filePath);
					ArrayList<double[]> currentFeatures = new ArrayList<double[]>();
                    ArrayList<Short> currentAmps = new ArrayList<Short>();

					// Add extracted features to arrayList of all features of
					// the class (=folder):
					allFeatures.addAll(currentFeatures);
                    allAmps.addAll(currentAmps);
				}
			}
		}

		return hm;
	}

	private double calcLogEnergy(short[] data) {

		double minE = 8.67e-19;
		// System.out.println("Length of short array: " + data.length);
		int dim = data.length;

		double sum = 0.0;

		for (int i = 0; i < dim; i++) {
			sum += Math.pow(data[i], 2);
		}

		double d = Math.sqrt(sum / ((double) dim));

		if (d < minE) {
			d = minE;
			System.out.println("d smaller minE");
		}

		return Math.log(d);
	}

	public static void main(String[] args) {
		if (args.length > 0) {

			if (args[0].equals("--folder")) {

				String className = args[1];

				String folderPath = "../sound/" + className;
				String jsonPath = "../extractedFeatures/" + className;

				extractFeatures extractor = new extractFeatures();

				ArrayList<double[]> features = new ArrayList<double[]>();
                ArrayList<Short> amps = new ArrayList<Short>();

				HashMap hm = extractor.extractFolder(folderPath);

				extractor.saveGson(hm, jsonPath);

			} else if (args[0].equals("--file")) {

				extractFeatures extractor = new extractFeatures();

				ArrayList<double[]> features = new ArrayList<double[]>();
                ArrayList<Short> amps = new ArrayList<Short>();

				HashMap hm = extractor.extractFile(args[1]);

                String newFilename = args[1];

                // If the given filename have an extension (e.g. *.wav) remove it: 
                if (newFilename.indexOf(".") != -1) {
                    newFilename = newFilename.substring(0, newFilename.lastIndexOf('.'));
                }

				extractor.saveGson(hm, newFilename);

			} else {
				System.out
						.println("The first argument has to be either --file or --folder");
			}

		} else {
			System.out.println("Please provide correct number of parameters");
		}

	}
}
