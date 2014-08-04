package ch.ethz.wearable.contextrecognition.math;

import java.util.Arrays;

import org.ejml.alg.generic.GenericMatrixOps;
import org.ejml.data.DenseMatrix64F;
import org.ejml.factory.CholeskyDecomposition;
import org.ejml.factory.DecompositionFactory;
import org.ejml.ops.CommonOps;

import ch.ethz.wearable.contextrecognition.data.PredictionResult;
import ch.ethz.wearable.contextrecognition.utils.GMM;

/*
 * This class contains the necessary code to make the prediction using a Gaussian Mixture
 * Model and returns the predicted class and the mean entropy of the interval
 */
public class Classifier {
	
	public final String TAG = "Classifier";
   
   	// Make a prediction for any number of data points
    public PredictionResult predict(GMM gmm, DenseMatrix64F featureData) {

    	int n_samples = featureData.numRows;
    	int n_classes = gmm.get_n_classes();
    	
    	DenseMatrix64F predictions = new DenseMatrix64F(n_samples, 1);
    	
    	DenseMatrix64F X = norm(featureData, gmm.get_scale_means(), gmm. get_scale_stddevs());
    	
    	DenseMatrix64F tmpLL = new DenseMatrix64F(n_samples, 1);
    	DenseMatrix64F logLikelihood = new DenseMatrix64F(n_samples, n_classes);
    	
    	// Compute log-likelihoods for each class for each points
    	for(int i=0; i<n_classes; i++) {
    		tmpLL = computeLogProb(X, gmm.clf(i).get_weights(), gmm.clf(i).get_means(), gmm.clf(i).get_covars());
    		
    		CommonOps.extract(tmpLL, 0, tmpLL.numRows, 0, 1, logLikelihood, 0, i);
    	}

    	// For each point select the class with the highest log-likelihood:
    	for(int i=0; i<n_samples; i++) {
    		DenseMatrix64F tmpRow = new DenseMatrix64F(1, n_classes);
    		
    		CommonOps.extract(logLikelihood, i, (i+1), 0, n_classes, tmpRow, 0, 0);
    		
    		double largest = tmpRow.get(0,0);
    		int index = 0;
    		for (int j=0; j < n_classes; j++) {
    		  if (tmpRow.get(j) >= largest) {
    		      largest = tmpRow.get(j);
    		      index = j;
    		   }
    		}
    		
    		predictions.set(i,0,index);
    	}

		DenseMatrix64F res = majorityVote(predictions.getData());
		
		//------- Calculate the entropy for each point -------
		DenseMatrix64F logLikelihoodT = new DenseMatrix64F(n_classes, n_samples);
		CommonOps.transpose(logLikelihood, logLikelihoodT);
		
		DenseMatrix64F likelihood = new DenseMatrix64F(n_classes, n_samples);
		DenseMatrix64F tmpProduct = new DenseMatrix64F(n_classes, n_samples);
		DenseMatrix64F entropy = new DenseMatrix64F(1, n_samples);
		
		// Calculate the normal likelihood from the logLikelihood:
		for(int r=0; r<n_classes; r++) {
			for(int c=0; c<n_samples; c++) {
				likelihood.set(r,c,Math.exp(logLikelihoodT.get(r,c)));
			}
		}
		
		// Norm the likelihood for every point to one
		for(int c=0; c<n_samples; c++) {
			double sum=0;
			for(int r=0; r<n_classes; r++) {
				sum = sum + likelihood.get(r,c);
			}
			for(int r=0; r<n_classes; r++) {
				likelihood.set(r,c, (likelihood.get(r,c)/sum));
			}
		}
		
		// Calculate the normed log-likelihood now:
		DenseMatrix64F loglikelihoodNormed = new DenseMatrix64F(n_classes, n_samples);
		for(int c=0; c<n_samples; c++) {
			for(int r=0; r<n_classes; r++) {
				loglikelihoodNormed.set(r,c, Math.log(likelihood.get(r,c)));
			}
		}
		
		// Element-wise multiply likelihood with log-likelihood:
		for(int c=0; c<n_samples; c++) {
			for(int r=0; r<n_classes; r++) {
				tmpProduct.set(r,c, (loglikelihoodNormed.get(r,c) * likelihood.get(r,c)));
			}
		}
		
		// Calculate sum for each column:
		for(int c=0; c<n_samples; c++) {
			double sum=0;
			for(int r=0; r<n_classes; r++) {
				sum = sum + tmpProduct.get(r,c);
			}
			entropy.set(0, c, sum);
		}
		
		CommonOps.scale(-1, entropy);
		
		//------- Calculate the mean entropy for the whole (2sec) window -------
		double entropyMean = CommonOps.elementSum(entropy) / entropy.numCols;

		//------- Calculate the number of the resulting class: -------
		double elSum = CommonOps.elementSum(res);
		double div = elSum / ((double) n_samples);
		int resultInt = (int) Math.round(div);
		
//		Log.i(TAG, String.valueOf(elSum));
//		Log.i(TAG, String.valueOf(div));

		PredictionResult predictionResult = new PredictionResult(resultInt, entropyMean);
		
    	return predictionResult;
    }
   
    // Calculate the log probability of multiple points under a GMM represented by the weights, means, covars
	public DenseMatrix64F computeLogProb(DenseMatrix64F X, DenseMatrix64F weights, DenseMatrix64F means, DenseMatrix64F[] covars) {
		
		if (X.numCols != means.numCols) {
			System.out.print("X has wrong shape" + "\n");
		}
		int n_components = covars.length;
		int n_features = X.numCols;
		int n_samples = X.numRows;
		
		DenseMatrix64F logProbs = new DenseMatrix64F(n_samples,n_components);
		DenseMatrix64F finalLogProb = new DenseMatrix64F(1, n_samples);

		for(int c=0; c<n_components; c++) {
			
			CholeskyDecomposition<DenseMatrix64F> chol = DecompositionFactory.chol(covars[c].numRows, true); //if 2nd parameter is set to true, the lower triangular matrix is returned
			DenseMatrix64F L0 = new DenseMatrix64F(covars[c].numRows, covars[c].numCols);
			GenericMatrixOps.copy(covars[c], L0);
			chol.decompose(L0);
			
			// Extract diagonal elements from L0:
			DenseMatrix64F diagEl = new DenseMatrix64F(L0.numRows,1);
			CommonOps.extractDiag(L0, diagEl);
			
			//Calculate log for each element:
			DenseMatrix64F logEl = new DenseMatrix64F(diagEl.numRows,1);
			for(int i=0; i<diagEl.numRows; i++) {
				logEl.set(i,0,Math.log(diagEl.get(i,0)));
			}

			//Calculate log determinante for L0:
			double L0LogDet;
			L0LogDet = 2 * CommonOps.elementSum(logEl);
			
			//Calculate distance from mean for each points:
			DenseMatrix64F distMean = new DenseMatrix64F(n_samples,n_features); // (X - mu) in Python
			DenseMatrix64F featValues = new DenseMatrix64F(n_samples,1);
			for(int f=0; f<n_features; f++) {
				
				CommonOps.extract(X, 0, n_samples, f, (f+1), featValues, 0, 0);
				
				//Substract the meanvalue from all feature values:
				double tmpMean = -1 * means.get(c,f);
				CommonOps.add(featValues, tmpMean);
				
				//Fill the column for the correct feature in the distMean matrix:
				CommonOps.extract(featValues, 0, featValues.numRows, 0, 1, distMean, 0, f);
			}
			
			DenseMatrix64F distMeanTransp = new DenseMatrix64F(n_features,n_samples);
			CommonOps.transpose(distMean, distMeanTransp);
			
			DenseMatrix64F solved = new DenseMatrix64F(n_samples,n_features);
//			GenericMatrixOps.copy(distMeanTransp, solved);

			CommonOps.solve(L0, distMeanTransp, solved);
			
			DenseMatrix64F s = new DenseMatrix64F(n_features, n_samples);
			CommonOps.solve(L0, distMeanTransp, s);
			CommonOps.transpose(s, solved);
			
			CommonOps.elementMult(solved,solved); //square element-wise
			
			DenseMatrix64F rowSum = new DenseMatrix64F(n_samples,1); //calculate sum of each row
			
			CommonOps.sumRows(solved,rowSum);
			
			double summand = n_features * Math.log(2*Math.PI) + L0LogDet;
			
			CommonOps.add(rowSum, summand);
			
			CommonOps.scale(-0.5, rowSum);
			
			//fill column of the logProbs matrix:
			CommonOps.extract(rowSum, 0, rowSum.numRows, 0, 1, logProbs, 0, c); 
			
		}
		
		
		
		//Calculate log for each element of weight vector:
		DenseMatrix64F logWeights = new DenseMatrix64F(1, n_components);
		for(int i=0; i<n_components; i++) {
			logWeights.set(0,i,Math.log(weights.get(0,i)));
		}
		
		DenseMatrix64F tmp = new DenseMatrix64F(1, n_components);
		//add log of weight vector to each point:
		for(int i=0; i<n_samples; i++) {
			//Extract row
			CommonOps.extract(logProbs, i, (i+1), 0, logProbs.numCols, tmp, 0, 0);
			
			CommonOps.add(tmp, logWeights, tmp);
			CommonOps.extract(tmp, 0, 1, 0, tmp.numCols, logProbs, i, 0);
			
		}
		CommonOps.transpose(logProbs);
		
		
		// Get maximum component value for each point:
		DenseMatrix64F vmax = new DenseMatrix64F(1, n_samples);
		DenseMatrix64F tmpCol = new DenseMatrix64F(n_components, 1);
		Double colMax;
		for(int i=0; i<n_samples; i++) {
			//Extract row
			CommonOps.extract(logProbs, 0, logProbs.numRows, i, (i+1), tmpCol, 0, 0);
			colMax = CommonOps.elementMax(tmpCol);
			
			vmax.set(0,i,colMax);
		}
		
		// Substract vmax vector from every column in logProbs and substract it from log prob value
		DenseMatrix64F substracted = new DenseMatrix64F(n_components, n_samples); //refers to (tmpArray - vmax) in Python
		DenseMatrix64F tmpRow = new DenseMatrix64F(1, n_samples);
		// DenseMatrix64F vmaxNeg = new DenseMatrix64F(1, n_samples);
		for(int i=0; i<n_components; i++) {
			
			CommonOps.extract(logProbs, i, (i+1), 0, logProbs.numCols, tmpRow, 0, 0);
			
			// CommonOps.scale(-1,tmpRow);
			// CommonOps.scale(-1,vmax,vmaxNeg);
			
			//Substract the tmpRow from all vmax values:
			CommonOps.sub(tmpRow, vmax, tmpRow); //used to be CommonOps.add(tmpRow, vmax, tmpRow);
			
			//Fill the column for the correct feature in the substracted matrix:
			CommonOps.extract(tmpRow, 0, 1, 0, tmpRow.numCols, substracted, i, 0);
		}
		
		
		
		//Calculate exp for each element
		DenseMatrix64F exp = new DenseMatrix64F(n_components, n_samples);
		for(int row=0; row<n_components; row++) {
			for(int col=0; col<n_samples; col++) {
				exp.set(row, col, Math.exp(substracted.get(row,col)));
			}
		}

		//Calculate sum for every column
		DenseMatrix64F colSum = new DenseMatrix64F(1, n_samples);
		CommonOps.sumCols(exp, colSum);
		
		//Calculate log for each element:
		DenseMatrix64F logs = new DenseMatrix64F(1, n_samples);
		for(int i=0; i<n_samples; i++) {
			logs.set(0,i,Math.log(colSum.get(0,i)));
		}

		CommonOps.add(logs, vmax, finalLogProb);
		
		CommonOps.transpose(finalLogProb);
		
		return finalLogProb;
	}

	/*
	 * Subtract mean and norm standard deviation of of input data according to the mean and stddev values of the training data
	 * means and stddevs have shape of (1 x n_features)
	 */
	public DenseMatrix64F norm(DenseMatrix64F X, DenseMatrix64F means, DenseMatrix64F stddevs) {
		DenseMatrix64F normed = new DenseMatrix64F(X.numRows,X.numCols);
		DenseMatrix64F tmpCol = new DenseMatrix64F(X.numRows, 1);

		
		for(int i=0; i<X.numCols; i++) {
			tmpCol = CommonOps.extract(X, 0, X.numRows, i, (i+1));
			
			CommonOps.add(tmpCol, -1 * means.get(0,i), tmpCol);
			
			CommonOps.scale((1/stddevs.get(0,i)), tmpCol);
			
			CommonOps.insert(tmpCol, normed, 0, i);
		}
		
		return normed;
		
	}
	
	/*
	 * Calculate a majority vote of given window length on a double array and returns the resulting DenseMatrix64F
	 */
	public DenseMatrix64F majorityVote(double[] y_in) {
		double MAJORITY_WINDOW = 2.0; // in seconds
		double WINDOW_LENGTH = 0.1; // in seconds

		int frameLength = (int) Math.ceil(MAJORITY_WINDOW/WINDOW_LENGTH);
		
		int n_frames = (int) Math.ceil(( (double) y_in.length) / ((double) frameLength) );
		
		//System.out.print(frameLength + "\n");
		//System.out.print(y_in.length + "\n");
		//System.out.print(n_frames + "\n");

		double[] resArray = new double[y_in.length];
		
		for(int i=0; i<n_frames; i++) {
			if (((i+1) * frameLength) < y_in.length) {
				// All except the very last one:
				
				// Create temporary array for the current window:
				int len = ((i+1) * frameLength) - (i * frameLength);
				double tmpArray[] = new double[len];
			    System.arraycopy(y_in, (i * frameLength), tmpArray, 0, len); // arraycopy(Object src, int srcPos, Object dest, int destPos, int length)
				
				// Find most frequent number in array:
			    double mostFrequent = getMostFrequent(tmpArray);
				
				// Fill with most frequent element:
				Arrays.fill(tmpArray, mostFrequent);
				
				// Write into result array:
				System.arraycopy(tmpArray, 0, resArray, (i * frameLength), len);
				
			}
			
			else {
				// The last sequence most likely not exactly 2.0 seconds long:
				
				// Create temporary array for the current window:
				int len = y_in.length - (i * frameLength);
				double tmpArray[] = new double[len];
			    System.arraycopy(y_in, (i * frameLength), tmpArray, 0, len); // arraycopy(Object src, int srcPos, Object dest, int destPos, int length)
				
			    // Find most frequent number in array:
			    double mostFrequent = getMostFrequent(tmpArray);
				
				// Fill with most frequent element:
				Arrays.fill(tmpArray, mostFrequent);
				
				// Write into result array:
				System.arraycopy(tmpArray, 0, resArray, (i * frameLength), len);
				
			}
		}
		
		// Wrap the double array into a Densematrix64F 
		DenseMatrix64F res = new DenseMatrix64F();
		res.setData(resArray);
		res.setNumCols(1);		
		res.setNumRows(resArray.length);

		return res;
	}
	
	public double getMostFrequent(double[] a) {
		
	  int count = 1, tempCount;
	  double popular = a[0];
	  double temp = 0;
	  for (int i = 0; i < (a.length - 1); i++)
	  {
	    temp = a[i];
	    tempCount = 0;
	    for (int j = 1; j < a.length; j++)
	    {
	      if (temp == a[j])
		tempCount++;
	    }
	    if (tempCount > count)
	    {
	      popular = temp;
	      count = tempCount;
	    }
	  }
	  return popular;
	}
	
	/*
	// to delete:
	public DenseMatrix64F getPointsFromJSON(String filename) {
		
		Gson gson = new Gson();
		
		//Map<String, ArrayList> points = new HashMap<String, ArrayList>();

		featurePoint points;
		
		try {

			FileReader fileReader = new FileReader(filename);

			BufferedReader buffered = new BufferedReader(fileReader);

			points = gson.fromJson(buffered, featurePoint.class);

		} catch (IOException e) {
			points = null;
			e.printStackTrace();
		}
		
		DenseMatrix64F res = convertToEJML_2D(points.get());
		
		return res;
	}

	*/
	
/*
	// Converts a 2 dimensional ArrayList into a EJML DenseMatrix64F
	private DenseMatrix64F convertToEJML_2D(ArrayList<ArrayList<Double>> in) {
		
		int nRows = in.size();
		int nCols = in.get(0).size();		
		
		DenseMatrix64F out = new DenseMatrix64F(nRows, nCols);
		
		for(int r=0; r<nRows; r++) {
			for(int c=0; c<nCols; c++) {
				out.set(r, c, in.get(r).get(c));
			}
			
		}
		
		return out;
		
	}
*/
	

}

