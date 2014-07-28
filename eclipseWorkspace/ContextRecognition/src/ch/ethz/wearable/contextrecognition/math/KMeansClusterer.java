//Code from https://github.com/DSRC-GPU/CrowGenCpp
package ch.ethz.wearable.contextrecognition.math;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.ejml.simple.SimpleMatrix;

import com.google.common.collect.Lists;

public class KMeansClusterer {
	private SimpleMatrix data;

	public KMeansClusterer(SimpleMatrix data) {
		this.data = data;
	}

	private double computeDistance(SimpleMatrix v1, SimpleMatrix v2) {
		SimpleMatrix distanceMatrix;
		distanceMatrix = v1.minus(v2);
		distanceMatrix = distanceMatrix.elementMult(distanceMatrix);
		return distanceMatrix.elementSum() / v1.numCols();
	}
	
	/*
	 * Initialize centroids with the parameters given, e.g. if 1, 4, 6 are given, intialize the clusters with
	 * the values of the 1st, the 4th and 6th data point
	 */
	public List<Centroid> chooseCentroids(int... userCentroids) {
		// 2 arguments -> 2 centroids
		List<Centroid> centroids = Lists.newArrayListWithCapacity(userCentroids.length);

		for (int c : userCentroids) { // sweep through all parameter values
			SimpleMatrix centroid = new SimpleMatrix(1, data.numCols());
			for (int j = 0; j < data.numCols(); j++) {
				centroid.set(0, j, data.get(c, j));
			}
			centroids.add(new Centroid(centroid));
		}
		return centroids;
	}
	
	/*
	 * Randomly initiialized a certain number or centroids defined by the parameter numCentroids.
	 */
	public List<Centroid> initializeRandom(int numCentroids) {
		List<Centroid> centroids = Lists.newArrayListWithCapacity(numCentroids);

		ArrayList<Integer> randomNumbersTaken = new ArrayList<Integer>();
		int r = 0;
		
		for (int i=0; i<numCentroids; i++) {
			SimpleMatrix centroid = new SimpleMatrix(1, data.numCols());
			
			Random generator = new Random();
			r = generator.nextInt(data.numRows());
			
			while(randomNumbersTaken.contains(r)) {
				r = generator.nextInt(data.numRows());	
			}

			randomNumbersTaken.add(r);
			
			for (int j = 0; j < data.numCols(); j++) {

				centroid.set(0, j, data.get(r, j));
			}
			centroids.add(new Centroid(centroid));
		}
		return centroids;
	}

	public List<Centroid> cluster(List<Centroid> centroids) {
		boolean hasConverged = false;
		while (!hasConverged) {
			// Step 1: assign each point to a centroid
			for (Centroid centroid : centroids) {
				centroid.getDataPoints().clear();
			}
			// iterate on each point and choose the closest centroid
			for (int i = 0; i < data.numRows(); i++) {
				Centroid selectedCentroid = null;
				double minDistanceWithCentroid = Double.POSITIVE_INFINITY;
				SimpleMatrix dataVector = data.extractVector(true, i);
				for (Centroid centroid : centroids) {
					double distanceWithCentroid = computeDistance(dataVector,
							centroid.getFeatures());
					if (distanceWithCentroid < minDistanceWithCentroid) {
						selectedCentroid = centroid;
						minDistanceWithCentroid = distanceWithCentroid;
					}
				}
				selectedCentroid.getDataPoints().add(i);
			}

			// Step 2: move the centroids to the average of the assigned points
			hasConverged = true;
			for (Centroid centroid : centroids) {
				SimpleMatrix averageVector = new SimpleMatrix(1, data.numCols());
				for (Integer point : centroid.getDataPoints()) {
					averageVector = averageVector.plus(data.extractVector(true,
							point));
				}
				averageVector = averageVector.divide(centroid.getDataPoints()
						.size());
				if (!centroid.getFeatures().isIdentical(averageVector, 0)) {
					hasConverged = false;
					centroid.setFeatures(averageVector);
				}
			}
		}
		return centroids;
	}

//	public static void main(String[] args) {
//		double[][] data = { { 1, 1 }, { 1, 2 }, { 1, 3 }, { 2, 1 }, { 2, 2 },
//				{ 2, 3 }, { 3, 1 }, { 3, 2 }, { 3, 3 }, { 11, 11 }, { 11, 12 },
//				{ 11, 13 }, { 12, 11 }, { 12, 12 }, { 12, 13 }, { 13, 11 },
//				{ 13, 12 }, { 13, 13 } };
//
//		KMeansClusterer clusterer = new KMeansClusterer(new SimpleMatrix(data));
//		List<Centroid> centroids = clusterer.chooseCentroids(2, 13);
//		List<Centroid> results = clusterer.cluster(centroids);
//
//		System.out.println("KMeans computed for " + centroids.size()
//				+ " clusters");
//		for (Centroid centroid : results) {
//			System.out.println("Centroid " + centroid.getFeatures().toString());
//		}
//	}

	public class Centroid {
		private List<Integer> points;
		private SimpleMatrix features;

		public Centroid(SimpleMatrix features) {
			this.features = features;
			this.points = Lists.newLinkedList();
		}

		public List<Integer> getDataPoints() {
			return points;
		}

		public void setFeatures(SimpleMatrix features) {
			this.features = features;
		}

		public SimpleMatrix getFeatures() {
			return features;
		}

		@Override
		public String toString() {
			return features.toString();
		}
	}
}