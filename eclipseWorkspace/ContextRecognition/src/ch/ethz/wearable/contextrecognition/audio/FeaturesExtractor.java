/**
 * 
 * Funf: Open Sensing Framework
 * Copyright (C) 2010-2011 Nadav Aharony, Wei Pan, Alex Pentland.
 * Acknowledgments: Alan Gardner
 * Contact: nadav@media.mit.edu
 * 
 * This file is part of Funf.
 * 
 * Funf is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 * 
 * Funf is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with Funf. If not, see <http://www.gnu.org/licenses/>.
 * 
 */
package ch.ethz.wearable.contextrecognition.audio;

import java.util.Arrays;

import ch.ethz.wearable.contextrecognition.data.Features;
import ch.ethz.wearable.contextrecognition.math.FFT;
import ch.ethz.wearable.contextrecognition.math.MFCC;
import ch.ethz.wearable.contextrecognition.math.Window;

public class FeaturesExtractor {

	private static int RECORDER_SAMPLERATE = 16000;
	private static int FFT_SIZE = 8192;
	private static int MFCCS_VALUE = 12;
	private static int MEL_BANDS = 20;
	
	double featureArray[] = new double[MFCCS_VALUE + 1]; // also include the log energy
	
	double logEnergy;
	
	private MFCC featureMFCC;
	private FFT featureFFT;
	private Window featureWin;
	private int bufferSamples;

	public FeaturesExtractor() {
		
		featureMFCC = new MFCC(FFT_SIZE, MFCCS_VALUE, MEL_BANDS, RECORDER_SAMPLERATE);
		featureFFT = new FFT(FFT_SIZE);
		featureWin = new Window(bufferSamples);

	}

	public Features extractFeatures(short[] sound) {
		
		Features features = new Features();
		
		bufferSamples = sound.length;
		short data16bit[] = sound;
		int readAudioSamples = sound.length;
		
		double fftBufferR[] = new double[FFT_SIZE];
		double fftBufferI[] = new double[FFT_SIZE];
		double featureCepstrum[] = new double[MFCCS_VALUE];

		if (readAudioSamples > 0) {
			
			// Frequency analysis
			Arrays.fill(fftBufferR, 0);
			Arrays.fill(fftBufferI, 0);

			// Convert audio buffer to doubles
			for (int i = 0; i < readAudioSamples; i++) {
				fftBufferR[i] = data16bit[i];
			}

			// In-place windowing
			featureWin.applyWindow(fftBufferR);

			// In-place FFT
			featureFFT.fft(fftBufferR, fftBufferI);

			// Get MFCCs
			featureCepstrum = featureMFCC.cepstrum(fftBufferR, fftBufferI);
			//mfcc.set(featureCepstrum);
			
			// When using MFCC values only:
			//features.set(featureCepstrum);
			
			// Calculate log energy:
			logEnergy = calcLogEnergy(data16bit);

			// Combine the MFCCs and the log energy into one array:
			System.arraycopy(featureCepstrum, 0, featureArray, 0,
					featureCepstrum.length);
			featureArray[featureCepstrum.length] = logEnergy;
			
			features.set(featureArray);

			featureArray = new double[MFCCS_VALUE + 1];
		}
		return features;
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

}
