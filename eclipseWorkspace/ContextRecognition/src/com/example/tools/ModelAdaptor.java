package com.example.tools;

import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.stat.correlation.Covariance;
import org.ejml.alg.generic.GenericMatrixOps;
import org.ejml.data.DenseMatrix64F;
import org.ejml.factory.CholeskyDecomposition;
import org.ejml.factory.DecompositionFactory;
import org.ejml.ops.CommonOps;
import org.ejml.simple.SimpleMatrix;

//import org.apache.commons.math3.linear.CholeskyDecomposition;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/*
 * GMM updatedGMM = inst.adaptGMM(gmm, X_train, 0, 3);
 */
public class ModelAdaptor {

	public static final double EPS = Double.MIN_VALUE;
	
	public GMM adaptGMM(GMM oldGMM, DenseMatrix64F updatePoints, int label, int maxNewComp) throws IOException { 
		
		int n_merged = 0;
		int n_added = 0;
		
		int n_novel = updatePoints.numRows;
		int n_features = oldGMM.get_n_features();
		int n_components_old = oldGMM.clf(label).get_n_components();
		int n_train_old = oldGMM.clf(label).get_n_train();
		
		double[] bicArray = new double[maxNewComp];
		
		for(int c=1; c<=maxNewComp; c++) {
			
			ContextClassModel tmpModel = trainGMM(updatePoints, c);

			bicArray[c-1] = BIC(updatePoints, tmpModel, c);
			
		}
		int n_components_new = getMinIndex(bicArray) + 1;
		
		// System.out.println(n_components_new + " new components");
		
		ContextClassModel newModel = trainGMM(updatePoints, n_components_new);
		
		DenseMatrix64F likelihoods = new DenseMatrix64F(n_components_new, n_novel);
		DenseMatrix64F tmpRow = new DenseMatrix64F(1, n_novel);
		
		DenseMatrix64F tmpMeans = new DenseMatrix64F(1, n_features);
		
		for(int i=0; i<n_components_new; i++) {
			
			CommonOps.extract(newModel.get_means(), i, (i+1), 0, n_features, tmpMeans, 0, 0);
			
			tmpRow = pdf(updatePoints, tmpMeans, newModel.get_covars()[i]);
			
			CommonOps.extract(tmpRow, 0, 1, 0, n_novel, likelihoods, i, 0);
		}
		DenseMatrix64F pointComponents = findMaxRowIndex(likelihoods);
		
		/* The first element in this ArrayList is the matrix of all feature points that are assigned to the first
		 * component (of the novel model), and so on...
		*/
		ArrayList<DenseMatrix64F> Dk = new ArrayList<DenseMatrix64F>();
	    // Mk ontains the number of points for each entry (component) in the Dk ArrayList:
		ArrayList<Integer> Mk = new ArrayList<Integer>();
		
		// Fill the Mk array first
		for(int c=0; c<n_components_new; c++) {
			
			int tmpCnt = 0;
			
			for(int i=0; i<n_novel; i++) {
				if (pointComponents.get(0, i) == c) {
					tmpCnt++;
				}
			}
			
			Mk.add(tmpCnt);
			
			// Create the DenseMatrix64F element of the Dk list:
			DenseMatrix64F newMatrix = new DenseMatrix64F(tmpCnt, n_features);
			Dk.add(newMatrix);
		}
		
		// Fill the Dk matrices:
		for(int c=0; c<n_components_new; c++) {
			
			
			DenseMatrix64F tmpMatrix = Dk.get(c); // Create temp copy of the matrix
			int idx = 0; // Counts current index in the matrix for the current component
			
			for(int i=0; i<n_novel; i++) {
				if (pointComponents.get(0, i) == c) {

					CommonOps.extract(updatePoints, i, (i+1), 0, n_features, tmpMatrix, idx, 0);
					
					idx++;
				}
			}
			
			Dk.set(c, tmpMatrix);
		}
		
		/*
		 * Mapping from OLD to NOVEL (!) components, if old components is not mapped to novel one, the value equals -1.
         * Elements initialized to -1. mapping[3] = 5 means that old component 3 is equal to new component 5.
         */
		int[] mapping = new int[n_components_old];
		for(int i=0; i<n_components_old; i++) {
			mapping[i] = -1;
		}
		
		// ----- Test covariance and mean equality -----
		for(int k=0; k<n_components_new; k++) {
			for(int j=0; j<n_components_old; j++) {
//				System.out.println("Old comp: " + j + " new comp: " + k);
				
				//if (true) {
				if (covarTest(Dk.get(k), oldGMM.clf(label).get_covars()[j]) == true) {
					System.out.println("Covariance test passed");
					
					DenseMatrix64F tMeans = new DenseMatrix64F(1, n_features);
					CommonOps.extract(oldGMM.clf(label).get_means(), j, (j+1), 0, n_features, tMeans, 0, 0);
				
					if(meanTest(Dk.get(k), tMeans) == true) {
						System.out.println("Mean test passed");
						
						mapping[j] = k;
					}
				
				}
				
			}
		}
		
		// ----- Compute components and add them to the model -----
		// List containing means, covars and weights of all components:
		ArrayList<ComponentObject> componentList = new ArrayList<ComponentObject>();
		
		// Loop through the mapping array
		for(int j=0; j<n_components_old; j++) {
			int k = mapping[j];
			
			if (k != -1) {
				// Merge the two components:
				
				// System.out.println("Merging old component " + j + " with new component " + k);
				
				DenseMatrix64F tmpMeansOld = new DenseMatrix64F(1, n_features);
				CommonOps.extract(oldGMM.clf(label).get_means(), j, (j+1), 0, n_features, tmpMeansOld, 0, 0);
				
				DenseMatrix64F tmpMeansNovel = new DenseMatrix64F(1, n_features);
				CommonOps.extract(newModel.get_means(), k, (k+1), 0, n_features, tmpMeansNovel, 0, 0);
				
				ComponentObject mergedComp = mergeComponents(n_train_old, n_novel, Mk.get(k), 
						oldGMM.clf(label).get_weights().get(0,j),
						newModel.get_weights().get(0,k),
						tmpMeansOld, tmpMeansNovel,
						oldGMM.clf(label).get_covars()[j],
						newModel.get_covars()[k]);
				
				componentList.add(mergedComp);
				
				n_merged++;
				
			} else {
				// Add the historical component:
				// System.out.println("Adding historical component " + j);
				DenseMatrix64F tmpMeansOld = new DenseMatrix64F(1, n_features);
				CommonOps.extract(oldGMM.clf(label).get_means(), j, (j+1), 0, n_features, tmpMeansOld, 0, 0);
				
				ComponentObject histComp = addHistoricalComponent(n_train_old, n_novel, oldGMM.clf(label).get_weights().get(0,j),
						tmpMeansOld, oldGMM.clf(label).get_covars()[j]);
				
				componentList.add(histComp);
			}
		}
		
		// Loop through the new component to check which ones haven't been merged
		for(int i=0; i<n_components_new; i++) {
			
			// Check if the component is in the mapping array:
			boolean inArray = false;
			for(int l=0; l<n_components_old; l++) {
				if (mapping[l] == i) {
					inArray = true;
				}
			}
			
			if (inArray == false) {
				// Add novel component
				DenseMatrix64F tmpMeansNovel = new DenseMatrix64F(1, n_features);
				CommonOps.extract(newModel.get_means(), i, (i+1), 0, n_features, tmpMeansNovel, 0, 0);
				
				ComponentObject novelComp = addNovelComponent(n_train_old, n_novel, Mk.get(i), 
						tmpMeansNovel, newModel.get_covars()[i]);
				
				componentList.add(novelComp);
				
				n_added++;
			}
			
		}
		
		int n_components = n_components_new + n_components_old;
		
		// create weights, means, covars matrices from the componentList:
		DenseMatrix64F weights = new DenseMatrix64F(1,n_components);
		DenseMatrix64F means = new DenseMatrix64F(n_components,n_features);
		DenseMatrix64F[] covars = new DenseMatrix64F[n_components];
		
		for(int i=0; i<componentList.size(); i++) {
			// Fill weight vector entry by entry:
			weights.set(0, i, componentList.get(i).get_weight());
			
			// Fill means matrix row by row:
			CommonOps.extract(componentList.get(i).get_means(), 0, 1, 0, n_features, means, i, 0);

			// Insert the covariance matrix for each component:
			covars[i] = componentList.get(i).get_covars();
			
		}
		
		System.out.println(n_added + " components were added and " + n_merged + " components were merged");
		
		// ----- Merge statistically equivalent components: -----
		//TODO
		
		
		
		// ----- Create and fill GMM object that should be returned: -----
		GMM finalGMM = new GMM(oldGMM);
		
		ContextClassModel updatedClass = new ContextClassModel();
		updatedClass.set_n_components(n_components);
		updatedClass.set_n_features(n_features);
		updatedClass.set_n_train(n_novel + n_train_old);
		updatedClass.set_weights(weights);
		updatedClass.set_means(means);
		updatedClass.set_covars(covars);
		
		finalGMM.set_clf(updatedClass, label);
		
		
		
		return finalGMM;
	}
	
	/*
	 * Calculate W statistic to determine if both components have equal covariance
	 * 
     * @param covars_old: old covariance matrix for one component, shape: (n_features x n_features)
     * @return: True if covars are equal, False if not
	 */
	private boolean covarTest(DenseMatrix64F data, DenseMatrix64F covar_old) {
		int n_samples = data.numRows;
		int n_features = data.numCols;
		
		DenseMatrix64F id = CommonOps.identity(n_features);
		
		DenseMatrix64F L0 = new DenseMatrix64F(n_features, n_features);
		GenericMatrixOps.copy(covar_old, L0);
		
		CholeskyDecomposition<DenseMatrix64F> chol = DecompositionFactory.chol(n_features,true);
	    
		if( !chol.decompose(L0))
		   throw new RuntimeException("Cholesky failed!");
		
		DenseMatrix64F L0inv = new DenseMatrix64F(n_features, n_features);
		CommonOps.invert(L0, L0inv);

		DenseMatrix64F Y = new DenseMatrix64F(n_samples, n_features);
		DenseMatrix64F tmpRowX = new DenseMatrix64F(1, n_features);
		DenseMatrix64F tmpRowXT = new DenseMatrix64F(n_features, 1);		
		DenseMatrix64F tmpRowY = new DenseMatrix64F(1, n_features);
		DenseMatrix64F tmpRowYT = new DenseMatrix64F(n_features, 1);
		
		for(int i=0; i<n_samples; i++) {
			
			CommonOps.extract(data, i, (i+1), 0, n_features, tmpRowX, 0, 0);
			CommonOps.transpose(tmpRowX, tmpRowXT);
			
			CommonOps.mult(L0inv, tmpRowXT, tmpRowYT);
			
			
			
			CommonOps.transpose(tmpRowYT, tmpRowY);
			
			CommonOps.extract(tmpRowY, 0, 1, 0, n_features, Y, i, 0);
			
		}
		
		
		// Calculate covariance matrix
		double[][] tmp = convertToArray(Y);
		Covariance cv = new Covariance(tmp);
		RealMatrix rm = cv.getCovarianceMatrix();
		DenseMatrix64F Sy = new DenseMatrix64F(rm.getData());
		
		
		
		// Calculate 1st summand w1:
		DenseMatrix64F SyID = new DenseMatrix64F(n_features, n_features);
		CommonOps.sub(Sy, id, SyID);
		
		DenseMatrix64F prod = new DenseMatrix64F(n_features, n_features);
		CommonOps.mult(SyID, SyID, prod);
		
		double trace = CommonOps.trace(prod);
		
		double w1 = trace / ((double) n_features);
		
		// Calculate 2nd summand w2:
		double wt1 = n_features / ((double) n_samples);
		double wt2 = Math.pow((CommonOps.trace(Sy) / ((double) n_features)), 2);
		double w2 = wt1 * wt2;
		
		// Calculate 3rd summand w3:
		double w3 = n_features / ((double) n_samples);
		
		double W = w1 - w2 + w3;
				
		double test_statistic = (n_samples * W * n_features) / 2.0;
		
		double alphaPercentile = 0.05;
		
//		if(n_samples < 30) { //only compare if same component
//			System.out.println(w1);
//		}
		
		//TODO: XXXXXXXXXXXXXXX implement chi_squared check XXXXXXXXXXXXXXXX
		
		
		return false;
	}
	
	/*
	 * Use Hotelling T-squared test to determine if both components have equal means
	 * 
     *  @param means_old: old mean values for one component, shape: (1 x n_features)
     *  @return: True if covars are equal, False if not
	 */
	private boolean meanTest(DenseMatrix64F data, DenseMatrix64F means_old) {
		
		int n_samples = data.numRows;
		int n_features = data.numCols;
		
		// Calculate covariance matrix
		double[][] tmp = convertToArray(data);
		Covariance cv = new Covariance(tmp);
		RealMatrix rm = cv.getCovarianceMatrix();
		DenseMatrix64F S = new DenseMatrix64F(rm.getData());
		
		DenseMatrix64F Sinv = new DenseMatrix64F(n_features, n_features);
		CommonOps.invert(S, Sinv);
		
		
		//TODO: XXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
		
		
		
		
		
		
		
		return false;
	}

	
	/*
     * Merge two mixture components.
     * 
     * @param n_old: Number of training points of the old classifier
     * @param n_novel: Number of novel points
     * @param n_comp: Number of novel points assigned to this component
     * @param weight_old: double
     * @param weight_novel: double
     * @param means_old: shape = (1 x n_features)
     * @param means_novel: shape = (1 x n_features)
     * @param covars_old: shape = (n_features x n_features)
     * @param covars_novel: shape = (n_features x n_features)
	 *
     * @return: merged component
    */
	private ComponentObject mergeComponents(int n_old, int n_novel, int n_comp, 
			double weight_old, double weights_novel,
			DenseMatrix64F means_old, DenseMatrix64F means_novel, 
			DenseMatrix64F covars_old, DenseMatrix64F covars_novel) {
		
		
		
		int n_features = means_old.numCols;
		
		ComponentObject result = new ComponentObject();
		
		// ----- Compute means -----
		DenseMatrix64F t1 = new DenseMatrix64F(1, n_features);
		CommonOps.scale((n_old*weight_old), means_old, t1);
		
		DenseMatrix64F t2 = new DenseMatrix64F(1, n_features);
		CommonOps.scale(n_comp, means_old, t2);
		
		DenseMatrix64F means = new DenseMatrix64F(1, n_features);
		CommonOps.add(t1, t2, means);
		
		CommonOps.scale((1 / ( (double) n_old * weight_old + n_comp)), means);
				
		
		
		
		// ----- Compute covars -----
		// --- compute c1 ---
		CommonOps.scale((n_old * weight_old), covars_old);
		CommonOps.scale(n_comp, covars_novel);
		
		DenseMatrix64F c1 = new DenseMatrix64F(n_features, n_features);
		CommonOps.add(covars_old, covars_novel, c1);
		
		CommonOps.scale((1 / ((double) n_old * weight_old + n_comp)), c1);
		
		
		// --- compute c2 ---
		DenseMatrix64F means_old_trans = new DenseMatrix64F(n_features, 1);
		CommonOps.transpose(means_old, means_old_trans);
		DenseMatrix64F means_novel_trans = new DenseMatrix64F(n_features, 1);
		CommonOps.transpose(means_novel, means_novel_trans);	
		
		DenseMatrix64F t = new DenseMatrix64F(1, 1);
		CommonOps.mult(means_old, means_old_trans, t);
		double prodOld = t.get(0,0);
		
		CommonOps.mult(means_novel, means_novel_trans, t);
		double prodNovel = t.get(0,0);
		
		double c2;
		c2 = ((n_old * weight_old * prodOld) + (n_comp * prodNovel)) / ((double) n_old * weight_old + n_comp);
		
		// --- compute c3 ---
		double c3;
		DenseMatrix64F means_trans = new DenseMatrix64F(n_features, 1);
		CommonOps.transpose(means, means_trans);
		CommonOps.mult(means, means_trans, t);
		c3 = t.get(0,0);
		
		DenseMatrix64F covars = new DenseMatrix64F(n_features, n_features);
		CommonOps.add(c1, (c2+c3));
		GenericMatrixOps.copy(c1, covars);
		
		
		// ----- Compute weight -----
		double weight;
		weight = (n_old * weight_old + n_comp) / ((double) n_old + n_novel);
		
		result.set_means(means);
		result.set_weight(weight);
		result.set_covars(covars);
		
		
		return result;
	}
	
	private ComponentObject addHistoricalComponent(int n_old, int n_novel, double weight_old, 
			DenseMatrix64F means_old, DenseMatrix64F covars_old) {
		
		ComponentObject result = new ComponentObject();
		
		double weight = (n_old * weight_old) / ((double) n_old + n_novel);
		
		result.set_weight(weight);
		result.set_means(means_old);
		result.set_covars(covars_old);
		
		return result;
		
	}
	
	private ComponentObject addNovelComponent(int n_old, int n_novel, int n_comp, 
			DenseMatrix64F means_novel, DenseMatrix64F covars_novel) {
		
		ComponentObject result = new ComponentObject();
		
		double weight = n_comp / ((double) n_old + n_novel);
		
		result.set_weight(weight);
		result.set_means(means_novel);
		result.set_covars(covars_novel);
		
		return result;
		
	}
	
	/*
	 * Calculate BIC value. featureData has to be scaled already.
	 */
	private double BIC(DenseMatrix64F featureData, ContextClassModel model, int n_components) throws IOException {
		
		int n_samples = featureData.numRows;
		int n_features = featureData.numCols;
		
		DenseMatrix64F lp = computeLogProb(featureData, model.get_weights(), model.get_means(), model.get_covars());
		
		double logLik = -2.0 * CommonOps.elementSum(lp);
		
		double complexity = ((n_components/2.0 * (n_features+1) * (n_features+2)) - 1.0) * Math.log(n_samples);
		
		return (logLik + complexity);
	}
	
	/*
	 * Calculate Probability Density Function of Gaussian in multiple dimensions. Call this function for each component
	 * of the mixture individually
	 * 
	 * @param data: Feature data
	 * @param means: Row vector representing the mean values of a single component
	 * @param covar: Covariance matrix of the component
	 */
	private DenseMatrix64F pdf(DenseMatrix64F data, DenseMatrix64F means, DenseMatrix64F covar) {
		
		int n_samples = data.numRows;
		int n_features = data.numCols;
		
		// Substract the means vector from every row of the data matrix
		DenseMatrix64F sub = new DenseMatrix64F(n_samples, n_features);
		DenseMatrix64F tmpRow = new DenseMatrix64F(1, n_features);
		for(int i=0; i<n_samples; i++) {
			
			CommonOps.extract(data, i, (i+1), 0, n_features, tmpRow, 0, 0);
			
			CommonOps.sub(tmpRow, means, tmpRow);
			
			CommonOps.extract(tmpRow, 0, 1, 0, n_features, sub, i, 0);
		}
		DenseMatrix64F subT = new DenseMatrix64F(n_features, n_samples);
		CommonOps.transpose(sub, subT);
		
		DenseMatrix64F inv = new DenseMatrix64F(n_features, n_features);
		CommonOps.invert(covar, inv);
		
		DenseMatrix64F mult = new DenseMatrix64F(n_samples, n_features);
		CommonOps.mult(sub, inv, mult);
		CommonOps.transpose(mult);
		
		CommonOps.elementMult(mult, subT);
		
		DenseMatrix64F colSum = new DenseMatrix64F(1, n_samples);
		CommonOps.sumCols(mult, colSum);
		
		CommonOps.scale(-0.5, colSum);
		
		// Calculate exponential from colSum:
		DenseMatrix64F exp = new DenseMatrix64F(1, n_samples);
		for(int i=0; i<n_samples; i++) {
			exp.set(0, i, Math.exp(colSum.get(0, i)));
		}
		
		double absDet = Math.abs(CommonOps.det(covar) + EPS);
		double factor = Math.pow((2 * Math.PI), n_features);
		double denom = Math.sqrt(factor * absDet);
		
		DenseMatrix64F prob = new DenseMatrix64F(1, n_samples);
		CommonOps.divide(denom, exp, prob);
		
		//CommonOps.transpose(prob);
		
		return prob;
	}
	    
	    
	
	/*
	 * Train a new mixture model for one class. featureData has to be scaled already.
	 */
	private ContextClassModel trainGMM(DenseMatrix64F featureData, int n_components) throws IOException {
		
		
		
		int n_features = featureData.numCols;
		int n_samples = featureData.numRows;
		
		DenseMatrix64F X_train = new DenseMatrix64F(n_samples, n_features);
		GenericMatrixOps.copy(featureData, X_train);
		
		int n_steps = 500;
		
		DenseMatrix64F weights = new DenseMatrix64F(1,n_components);
		DenseMatrix64F means = new DenseMatrix64F(n_components,n_features);
		DenseMatrix64F[] covars = new DenseMatrix64F[n_components];
		
		DenseMatrix64F transPoints = new DenseMatrix64F(n_samples, n_features);
		GenericMatrixOps.copy(X_train, transPoints);
		CommonOps.transpose(transPoints);
		
		DenseMatrix64F id = CommonOps.identity(n_features);
		CommonOps.scale(1e-3, id);
		
		// ----- initialize means using kMeans -----
		KMeansClusterer clusterer = new KMeansClusterer(new SimpleMatrix(X_train));
		List<KMeansClusterer.Centroid> centroids = clusterer.initializeRandom(n_components);
		List<KMeansClusterer.Centroid> clusterCent = clusterer.cluster(centroids);
		
//		System.out.println("KMeans computed for " + centroids.size() + " clusters");
//		for (KMeansClusterer.Centroid centroid : clusterCent) {
//			System.out.println("Centroid " + centroid.getFeatures().toString());
//		}
		
		if (clusterCent.size() != n_components) {
			System.out.println("Dimensions missmatch: number of clusters not equal to number of components");
		}
		
		for(int i=0; i<n_components; i++) {
			DenseMatrix64F tmp = new DenseMatrix64F(clusterCent.get(i).getFeatures().getMatrix());
			
			// Fill the means matrix with the clusters (each column refers to one components):
			 CommonOps.extract(tmp, 0, 1, 0, tmp.numCols, means, i, 0);
		}		
		
		
		//for testing only
		//GenericMatrixOps.copy(m, means);
		//xxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
		
		
		// ----- initialize weights -----
		CommonOps.fill(weights, (1.0/n_components));
		
		// ----- initialize covars -----
		double[][] tmp = convertToArray(X_train);
		Covariance cv = new Covariance(tmp);
		RealMatrix rm = cv.getCovarianceMatrix();
		DenseMatrix64F cc = new DenseMatrix64F(rm.getData());
		CommonOps.add(cc, id, cc);		
		
		// fill all covars matrices with this matrix:
		for(int i=0; i<n_components; i++) {
			covars[i] = new DenseMatrix64F(cc);
		}
		
		// ===== EM Algorithm =====
		
		LinkedList<Double> llList = new LinkedList<Double>();
        boolean converged = false;

        for(int k=0; k<n_steps; k++) {
        	
        	// ----- E-Step -----
	        DenseMatrix64F proba = computeLogProb(X_train, weights, means, covars);
	        
	        llList.add(CommonOps.elementSum(proba));

			// Calculate log for each element:
			DenseMatrix64F logWeights = new DenseMatrix64F(1,n_components);
			for(int i=0; i<n_components; i++) {
				logWeights.set(0, i, Math.log(weights.get(0,i)));
			}
			
			DenseMatrix64F lprTmp = lpr(X_train, weights, means, covars);
			
			// Add logWeights to each line:
			for(int i=0; i<n_samples; i++) {
				
				DenseMatrix64F tmpRow = new DenseMatrix64F(1,n_components);
				CommonOps.extract(lprTmp, i, (i+1), 0, n_components, tmpRow, 0, 0);
				
				CommonOps.add(tmpRow, logWeights, tmpRow);
				
				CommonOps.extract(tmpRow, 0, 1, 0, n_components, lprTmp, i, 0);
			}

			DenseMatrix64F diff = new DenseMatrix64F(n_samples, n_components); // = lprTmp - proba
			
			for(int i=0; i<n_components; i++) {
				
				DenseMatrix64F tmpCol = new DenseMatrix64F(n_samples,1);
				CommonOps.extract(lprTmp, 0, n_samples, i, (i+1), tmpCol, 0, 0);

				CommonOps.sub(tmpCol, proba, tmpCol);
				
				CommonOps.extract(tmpCol, 0, n_samples, 0, 1, diff, 0, i);
			}
			
			DenseMatrix64F responsibilities = new DenseMatrix64F(n_samples, n_components);
			
			//Calculate exp for each element
			for(int row=0; row<n_samples; row++) {
				for(int col=0; col<n_components; col++) {
					responsibilities.set(row, col, Math.exp(diff.get(row,col)));
				}
			}		
				
			// Check for convergence:
			if ( (k>0) && (Math.abs(llList.get(llList.size()-1) - llList.get(llList.size()-2)) < 0.01) ) {
				converged = true;
				//System.out.println("EM converged after " + k + " iterations");
                break;
			}
			
			// ----- M-Step -----
			DenseMatrix64F tWeights = new DenseMatrix64F(1, n_components);
			CommonOps.sumCols(responsibilities, tWeights);
		
			DenseMatrix64F weighted_X_sum = new DenseMatrix64F(n_components, n_features);
			DenseMatrix64F respTrans = new DenseMatrix64F(n_components, n_samples);
			
			CommonOps.transpose(responsibilities, respTrans);
			CommonOps.mult(respTrans, X_train, weighted_X_sum);
			
			
			DenseMatrix64F inverse_weights = new DenseMatrix64F(1, n_components);
			GenericMatrixOps.copy(tWeights, inverse_weights);
			CommonOps.transpose(inverse_weights);
			
			// Fill values for the inverseWeights matrix:
			for(int i=0; i<n_components; i++) {
				inverse_weights.set(i, 0, (1.0 / inverse_weights.get(i,0)));
			}
			
			// --- Update weights ---
			double tWeightsSum = CommonOps.elementSum(tWeights) + 10.0 * EPS;
			CommonOps.add(tWeights, EPS);
			
			CommonOps.divide(tWeightsSum, tWeights, weights);
			
			
			
			// --- Update means ---
			//multiply each column of weighted_X_sum element wise with inverse_weights:
			for(int i=0; i<n_features; i++) {
				DenseMatrix64F tmpCol = new DenseMatrix64F(n_components,1);
				CommonOps.extract(weighted_X_sum, 0, n_components, i, (i+1), tmpCol, 0, 0);
				
				CommonOps.elementMult(tmpCol, inverse_weights, tmpCol);
				
				CommonOps.extract(tmpCol, 0, n_components, 0, 1, means, 0, i);
				
			}

			// --- Update covars ---
			for(int c=0; c<n_components; c++) {
				DenseMatrix64F post = new DenseMatrix64F(n_samples,1);
				
				CommonOps.extract(responsibilities, 0, n_samples, c, (c+1), post, 0, 0);
				CommonOps.transpose(post);
				
				DenseMatrix64F tmp1 = new DenseMatrix64F(n_features, n_samples); // (post * tmpTrain.T) in Python
				// Element-wise multiply each row of transposed matrix of training points with post vector:
				for(int i=0; i<n_features; i++) {
					DenseMatrix64F tmpRow = new DenseMatrix64F(1,n_samples);
					
					CommonOps.extract(transPoints, i, (i+1), 0, n_samples, tmpRow, 0, 0);
					
					CommonOps.elementMult(post, tmpRow, tmpRow);
					
					CommonOps.extract(tmpRow, 0, 1, 0, n_samples, tmp1, i, 0);

				}
				
				DenseMatrix64F dotProd = new DenseMatrix64F(n_features, n_features); // dot product of tmp1 and X_train
				
				CommonOps.mult(tmp1, X_train, dotProd);
				
				double postSum = CommonOps.elementSum(post) + 10.0 * EPS;
					
				DenseMatrix64F avgCV = new DenseMatrix64F(n_features, n_features);
				CommonOps.divide(postSum, dotProd, avgCV);

				DenseMatrix64F mu = new DenseMatrix64F(1, n_features);
				CommonOps.extract(means, c, (c+1), 0, n_features, mu, 0, 0);
				DenseMatrix64F muTrans = new DenseMatrix64F(1, n_features);
				GenericMatrixOps.copy(mu, muTrans);
				CommonOps.transpose(muTrans);
				
				DenseMatrix64F mu_squared = new DenseMatrix64F(n_features, n_features);
				CommonOps.mult(muTrans, mu, mu_squared);
				
				CommonOps.sub(avgCV, mu_squared, mu_squared);
				CommonOps.add(mu_squared, id, covars[c]);
				
				
			}	
        }
		
        if(converged == false) {
        	System.out.println("EM not converged after " + n_steps + " iterations");
        }
        
        ContextClassModel result = new ContextClassModel();
        
        result.set_n_train(n_samples);
        result.set_weights(weights);
        result.set_means(means);
        result.set_covars(covars);
        result.set_converged(converged);
        result.set_n_components(n_components);

		return result;
	}
	
	
	/*
	 * Calculate the log probability of multiple points under a GMM represented
	 * by the weights, means, covars (called logProb in Python)
	 */
	private DenseMatrix64F computeLogProb(DenseMatrix64F X,
			DenseMatrix64F weights, DenseMatrix64F means,
			DenseMatrix64F[] covars) throws IOException {

		if (X.numCols != means.numCols) {
			System.out.print("X has wrong shape" + "\n");
		}
		int n_components = covars.length;
		int n_features = X.numCols;
		int n_samples = X.numRows;

		DenseMatrix64F logProbs = new DenseMatrix64F(n_samples, n_components);
		DenseMatrix64F finalLogProb = new DenseMatrix64F(1, n_samples);

		for (int c = 0; c < n_components; c++) {

			// ============ Implementation using Apache Commons to compute Cholesky: =============		
//			double[][] tmp = convertToArray(covars[c]);
//			RealMatrix rmL0 = new Array2DRowRealMatrix(tmp);
//
////			System.out.println("Component " + c);
////			dumpArrayJSON(tmp, "covars");
//			
//			double symetryThreshold = 0.1;
//			double positivityThreshold = 1e-15;
//			
//			CholeskyDecomposition cholesky;
//			
//			try {
//				cholesky = new CholeskyDecomposition(rmL0, symetryThreshold, positivityThreshold);
//			} catch (org.apache.commons.math3.linear.NonSymmetricMatrixException e) {
//				
//				System.out.println("Cholesky Decomposition failed for component " + c + ", dumping current covars");
////				System.out.println(CommonOps.elementSum(covars[c]));
//				System.out.println(covars[c].get(0,7));
//				System.out.println(covars[c].get(7,0));
//				dumpArrayJSON(tmp, "covars");
//				
//				
//				
//				RealMatrix minAdd = MatrixUtils.createRealIdentityMatrix(n_features);
//				minAdd.scalarAdd(1e-18);
//				
//				cholesky = new CholeskyDecomposition(minAdd, symetryThreshold, positivityThreshold);
//				
//				// For other exception:
////				RealMatrix id3 = MatrixUtils.createRealIdentityMatrix(n_features);
////				rmL0.add(id3);
//			}
//
//			rmL0 = cholesky.getL();
//			DecompositionSolver cholSolver = cholesky.getSolver();
//			DenseMatrix64F L0 = new DenseMatrix64F(rmL0.getData());

			// ===================================================================================
			
			// ============ Implementation using EJML method to compute Cholesky: ================
			
			DenseMatrix64F L0 = new DenseMatrix64F(n_features, n_features);
			GenericMatrixOps.copy(covars[c], L0);
			
			CholeskyDecomposition<DenseMatrix64F> chol = DecompositionFactory.chol(n_features,true);
		    
			if( !chol.decompose(L0))
			   throw new RuntimeException("Cholesky failed!");
			        
			//SimpleMatrix L = SimpleMatrix.wrap(chol.getT(null));
			
			// ===================================================================================
			
			
			// Extract diagonal elements from L0:
			DenseMatrix64F diagEl = new DenseMatrix64F(L0.numRows, 1);
			CommonOps.extractDiag(L0, diagEl);

			// Calculate log for each element:
			DenseMatrix64F logEl = new DenseMatrix64F(diagEl.numRows, 1);
			for (int i = 0; i < diagEl.numRows; i++) {
				logEl.set(i, 0, Math.log(diagEl.get(i, 0)));
			}

			// Calculate log determinante for L0:
			double L0LogDet;
			L0LogDet = 2 * CommonOps.elementSum(logEl);

			// Calculate distance from mean for each points:
			DenseMatrix64F distMean = new DenseMatrix64F(n_samples, n_features); // (X
																					// -
																					// mu)
																					// in
																					// Python
			DenseMatrix64F featValues = new DenseMatrix64F(n_samples, 1);
			for (int f = 0; f < n_features; f++) {

				CommonOps
						.extract(X, 0, n_samples, f, (f + 1), featValues, 0, 0);

				// Substract the meanvalue from all feature values:
				double tmpMean = -1 * means.get(c, f);
				CommonOps.add(featValues, tmpMean);

				// Fill the column for the correct feature in the distMean
				// matrix:
				CommonOps.extract(featValues, 0, featValues.numRows, 0, 1,
						distMean, 0, f);
			} // TODO: Schauen ob diese Klammer richtig gesetzt!!!

			DenseMatrix64F distMeanTransp = new DenseMatrix64F(n_features,
					n_samples);
			CommonOps.transpose(distMean, distMeanTransp);

			DenseMatrix64F solved = new DenseMatrix64F(n_samples, n_features);
			

			DenseMatrix64F solution = new DenseMatrix64F(n_features, n_samples);
			DenseMatrix64F tmpCol = new DenseMatrix64F(n_features, 1);
			for(int i=0; i<n_samples; i++) {
				
				
				CommonOps.extract(distMeanTransp, 0, n_features, i, (i+1), tmpCol, 0, 0);
								
				RealVector sol = new ArrayRealVector(convertToVector(tmpCol));
				
				// Only temporarily:
				double[][] tmp2 = convertToArray(L0);
				RealMatrix rmL0 = new Array2DRowRealMatrix(tmp2);
				
				MatrixUtils.solveLowerTriangularSystem(rmL0, sol);
				
				// Write this column into the solution array:
				for(int r=0; r<n_features; r++) {
					solution.set(r, i, sol.getEntry(r));
				}
				
			}
			CommonOps.transpose(solution);
			GenericMatrixOps.copy(solution, solved);
			
			CommonOps.elementMult(solved, solved); // square element-wise

			DenseMatrix64F rowSum = new DenseMatrix64F(n_samples, 1); // calculate sum of each row

			CommonOps.sumRows(solved, rowSum); 
			
			double summand = n_features * Math.log(2 * Math.PI) + L0LogDet;

			CommonOps.add(rowSum, summand);

			CommonOps.scale(-0.5, rowSum);

			// fill column of the logProbs matrix:
			CommonOps.extract(rowSum, 0, rowSum.numRows, 0, 1, logProbs, 0, c);
			
		}

		// Calculate log for each element of weight vector:
		DenseMatrix64F logWeights = new DenseMatrix64F(1, n_components);
		for (int i = 0; i < n_components; i++) {
			logWeights.set(0, i, Math.log(weights.get(0, i)));
		}

		
		
		DenseMatrix64F tmp = new DenseMatrix64F(1, n_components);
		// add log of weight vector to each point:
		for (int i = 0; i < n_samples; i++) {
			// Extract row
			CommonOps.extract(logProbs, i, (i + 1), 0, logProbs.numCols, tmp,
					0, 0);

			CommonOps.add(tmp, logWeights, tmp);
			CommonOps.extract(tmp, 0, 1, 0, tmp.numCols, logProbs, i, 0);

		}
		
//		System.out.println(logProbs);
		
		CommonOps.transpose(logProbs); // refers to tmpArray (in Python) at this point
		
		
		
		// Get maximum component value for each point:
		DenseMatrix64F vmax = new DenseMatrix64F(1, n_samples);
		DenseMatrix64F tmpCol = new DenseMatrix64F(n_components, 1);
		Double colMax;
		for (int i = 0; i < n_samples; i++) {
			// Extract row
			CommonOps.extract(logProbs, 0, logProbs.numRows, i, (i + 1),
					tmpCol, 0, 0);
			colMax = CommonOps.elementMax(tmpCol);

			vmax.set(0, i, colMax);
		}

		// Substract vmax vector from every column in logProbs and substract it
		// from log prob value
		DenseMatrix64F substracted = new DenseMatrix64F(n_components, n_samples); // refers
																					// to
																					// (tmpArray
																					// -
																					// vmax)
																					// in
																					// Python
		
		DenseMatrix64F tmpRow = new DenseMatrix64F(1, n_samples);
		for (int i = 0; i < n_components; i++) {

			CommonOps.extract(logProbs, i, (i + 1), 0, logProbs.numCols,
					tmpRow, 0, 0);

			// CommonOps.scale(-1,tmpRow);

			// Substract the tmpRow from all vmax values:
			CommonOps.sub(tmpRow, vmax, tmpRow);

			// Fill the column for the correct feature in the substracted
			// matrix:
			CommonOps.extract(tmpRow, 0, 1, 0, tmpRow.numCols, substracted, i,
					0);
		}
		
		// System.out.println(substracted);
		
		// Calculate exp for each element
		DenseMatrix64F exp = new DenseMatrix64F(n_components, n_samples);
		for (int row = 0; row < n_components; row++) {
			for (int col = 0; col < n_samples; col++) {
				exp.set(row, col, Math.exp(substracted.get(row, col)));
			}
		}

		// Calculate sum for every column
		DenseMatrix64F colSum = new DenseMatrix64F(1, n_samples);
		CommonOps.sumCols(exp, colSum); // TODO: verify that exp is not modified

		// Calculate log for each element:
		DenseMatrix64F logs = new DenseMatrix64F(1, n_samples);
		for (int i = 0; i < n_samples; i++) {
			logs.set(0, i, Math.log(colSum.get(0, i)));
		}
		
		CommonOps.add(logs, vmax, finalLogProb);

		CommonOps.transpose(finalLogProb);

		return finalLogProb;
	}

	/*
	 * Helper method to compute the responsibilities
	 */
	private DenseMatrix64F lpr(DenseMatrix64F X, DenseMatrix64F weights,
			DenseMatrix64F means, DenseMatrix64F[] covars) {

		// TODO: test if working properly

		if (X.numCols != means.numCols) {
			System.out.print("X has wrong shape" + "\n");
		}
		int n_components = covars.length;
		int n_features = X.numCols;
		int n_samples = X.numRows;

		DenseMatrix64F logProbs = new DenseMatrix64F(n_samples, n_components);

		for (int c = 0; c < n_components; c++) {

			// ============ Implementation using Apache Commons to compute Cholesky: =============		
//			double[][] tmp = convertToArray(covars[c]);
//			RealMatrix rmL0 = new Array2DRowRealMatrix(tmp);
//
////			System.out.println("Component " + c);
////			dumpArrayJSON(tmp, "covars");
//			
//			double symetryThreshold = 0.1;
//			double positivityThreshold = 1e-15;
//			
//			CholeskyDecomposition cholesky;
//			
//			try {
//				cholesky = new CholeskyDecomposition(rmL0, symetryThreshold, positivityThreshold);
//			} catch (org.apache.commons.math3.linear.NonSymmetricMatrixException e) {
//				
//				System.out.println("Cholesky Decomposition failed for component " + c + ", dumping current covars");
////				System.out.println(CommonOps.elementSum(covars[c]));
//				System.out.println(covars[c].get(0,7));
//				System.out.println(covars[c].get(7,0));
//				dumpArrayJSON(tmp, "covars");
//				
//				
//				
//				RealMatrix minAdd = MatrixUtils.createRealIdentityMatrix(n_features);
//				minAdd.scalarAdd(1e-18);
//				
//				cholesky = new CholeskyDecomposition(minAdd, symetryThreshold, positivityThreshold);
//				
//				// For other exception:
////				RealMatrix id3 = MatrixUtils.createRealIdentityMatrix(n_features);
////				rmL0.add(id3);
//			}
//
//			rmL0 = cholesky.getL();
//			DecompositionSolver cholSolver = cholesky.getSolver();
//			DenseMatrix64F L0 = new DenseMatrix64F(rmL0.getData());

			// ===================================================================================
			
			// ============ Implementation using EJML method to compute Cholesky: ================
			
			DenseMatrix64F L0 = new DenseMatrix64F(n_features, n_features);
			GenericMatrixOps.copy(covars[c], L0);
			
			CholeskyDecomposition<DenseMatrix64F> chol = DecompositionFactory.chol(n_features,true);
		    
			if( !chol.decompose(L0))
			   throw new RuntimeException("Cholesky failed!");
			        
			//SimpleMatrix L = SimpleMatrix.wrap(chol.getT(null));
			
			// ===================================================================================

			// Extract diagonal elements from L0:
			DenseMatrix64F diagEl = new DenseMatrix64F(L0.numRows, 1);
			CommonOps.extractDiag(L0, diagEl);

			// Calculate log for each element:
			DenseMatrix64F logEl = new DenseMatrix64F(diagEl.numRows, 1);
			for (int i = 0; i < diagEl.numRows; i++) {
				logEl.set(i, 0, Math.log(diagEl.get(i, 0)));
			}

			// Calculate log determinante for L0:
			double L0LogDet;
			L0LogDet = 2 * CommonOps.elementSum(logEl);

			// Calculate distance from mean for each points:
			DenseMatrix64F distMean = new DenseMatrix64F(n_samples, n_features); // (X
																					// -
																					// mu)
																					// in
																					// Python
			DenseMatrix64F featValues = new DenseMatrix64F(n_samples, 1);
			for (int f = 0; f < n_features; f++) {

				CommonOps
						.extract(X, 0, n_samples, f, (f + 1), featValues, 0, 0);

				// Substract the meanvalue from all feature values:
				double tmpMean = -1 * means.get(c, f);
				CommonOps.add(featValues, tmpMean);

				// Fill the column for the correct feature in the distMean
				// matrix:
				CommonOps.extract(featValues, 0, featValues.numRows, 0, 1,
						distMean, 0, f);
			}

			DenseMatrix64F distMeanTransp = new DenseMatrix64F(n_features,
					n_samples);
			CommonOps.transpose(distMean, distMeanTransp);

			DenseMatrix64F solved = new DenseMatrix64F(n_samples, n_features);

			DenseMatrix64F s = new DenseMatrix64F(n_features, n_samples);
			CommonOps.solve(L0, distMeanTransp, s);
			CommonOps.transpose(s, solved);

			DenseMatrix64F solution = new DenseMatrix64F(n_features, n_samples);
			DenseMatrix64F tmpCol = new DenseMatrix64F(n_features, 1);
			for(int i=0; i<n_samples; i++) {
				
				CommonOps.extract(distMeanTransp, 0, n_features, i, (i+1), tmpCol, 0, 0);
								
				RealVector sol = new ArrayRealVector(convertToVector(tmpCol));
				
				// Only temporarily:
				double[][] tmp2 = convertToArray(L0);
				RealMatrix rmL0 = new Array2DRowRealMatrix(tmp2);
				
				MatrixUtils.solveLowerTriangularSystem(rmL0, sol);
				
				// Write this column into the solution array:
				for(int r=0; r<n_features; r++) {
					solution.set(r, i, sol.getEntry(r));
				}
				
			}
			CommonOps.transpose(solution);
			GenericMatrixOps.copy(solution, solved);
			
			CommonOps.elementMult(solved, solved); // square element-wise

			DenseMatrix64F rowSum = new DenseMatrix64F(n_samples, 1); // calculate
																		// sum
																		// of
																		// each
																		// row

			CommonOps.sumRows(solved, rowSum);

			double summand = n_features * Math.log(2 * Math.PI) + L0LogDet;

			CommonOps.add(rowSum, summand);

			CommonOps.scale(-0.5, rowSum);

			// fill column of the logProbs matrix:
			CommonOps.extract(rowSum, 0, rowSum.numRows, 0, 1, logProbs, 0, c);

		}

		return logProbs;
	}
	
	@SuppressWarnings("unused")
	private void dumpArrayJSON(double[][] arr, String filename) throws IOException {
		Writer writer = new FileWriter(filename + ".json");

        Gson gson = new GsonBuilder().create();
        gson.toJson(arr, writer);

        writer.close();
	}
	

	private static double[][] convertToArray(DenseMatrix64F matrix) {
	    double array[][] = new double[matrix.getNumRows()][matrix.getNumCols()];
	    for (int r=0; r<matrix.getNumRows(); r++)
	    { 
	        for (int c=0; c<matrix.getNumCols(); c++)
	        {
	            array[r][c] = matrix.get(r,c);
	        }
	    }
	    return array;
	}
	
	private static double[] convertToVector(DenseMatrix64F matrix) {
		if (matrix.getNumRows() > matrix.getNumCols()) {
			// Input is a column vector:
			int len = matrix.getNumRows();
			double array[] = new double[len];
			
			for (int r=0; r<len; r++)
		    { 
	            array[r] = matrix.get(r,0);
		    }
		    return array;
			
		} else {
			// Input is a row vector:
			int len = matrix.getNumCols();
			double array[] = new double[len];
			
	        for (int c=0; c<matrix.getNumCols(); c++)
	        {
	            array[c] = matrix.get(0,c);
	        }
		    return array;
			
		}
	}
	
	private int getMinIndex(double[] arr) {
		int minIdx = 0;
		
		for (int i = 1; i < arr.length; i++){
		   double tmp = arr[i];
		   if ((tmp < arr[minIdx])){
			   minIdx = i;
		  }
		} 
		
		return minIdx;
		
	}
	/*
	 * Create matrix that contains the index of the maximum element for each column vector. Identical to
	 * np.argmax(matrix, 0) in numpy.
	 */
	private DenseMatrix64F findMaxRowIndex(DenseMatrix64F arr) {
		int n_rows = arr.numRows;
		int n_cols = arr.numCols;
		
		DenseMatrix64F result = new DenseMatrix64F(1, n_cols);
		
		for(int c=0; c<n_cols; c++) {
			
			int maxRow = 0;
			
			for(int r=0; r<n_rows; r++) {
				
				double tmp = arr.get(r, c);
				if (tmp > arr.get(maxRow, c)) {
					maxRow = r;
				}
			}
			
			result.set(0, c, maxRow);
		}
		
		
		return result;
		
	}
}
