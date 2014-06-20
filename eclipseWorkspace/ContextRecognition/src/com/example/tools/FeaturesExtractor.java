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
package com.example.tools;

import java.util.Arrays;

import com.example.math.FFT;
import com.example.math.MFCC;
import com.example.math.Window;

public class FeaturesExtractor {
	
	
	private static int RECORDER_SAMPLERATE = 16000;
	private static int FFT_SIZE = 8192;
	private static int MFCCS_VALUE = 12;
	private static int MEL_BANDS = 20;
	
	private MFCC featureMFCC;
	private FFT featureFFT;
	private Window featureWin;
	private int bufferSamples;

	public FeaturesExtractor() {
		
		featureMFCC = new MFCC(FFT_SIZE, MFCCS_VALUE, MEL_BANDS, RECORDER_SAMPLERATE);
		featureFFT = new FFT(FFT_SIZE);
		featureWin = new Window(bufferSamples);

	}

	public Mfccs extractFeatures(short[] sound) {
		Mfccs mfcc = new Mfccs();
		
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
			mfcc.set(featureCepstrum);
		}
		return mfcc;
	}

}
