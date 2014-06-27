package com.example.tools;

import org.ejml.data.DenseMatrix64F;

/*
Contains context class specific parameter of a single class:
*/
public class ContextClassModel {
		
		private int n_components;
		private int n_features;
		private int n_train;
		private boolean converged;
		
		private DenseMatrix64F means;
		private DenseMatrix64F weights;
		DenseMatrix64F[] covars = new DenseMatrix64F[n_components];
		
		// Methods to access data:		
		public int get_n_components() {
			return n_components;
		}
		public int get_n_features() {
			return n_features;
		}
		
		public int get_n_train() {
			return n_train;
		}
		
		public boolean get_converged() {
			return converged;
		}
		
		public DenseMatrix64F get_means() {
			return means;
		}
		
		public DenseMatrix64F get_weights() {
			return weights;
		}
		
		public DenseMatrix64F[] get_covars() {
			return covars;
		}

		// Methods to set data:
		public void set_n_components(int n_components_new) {
			n_components = n_components_new;
		}
		
		public void set_n_features(int n_features_new) {
			n_features = n_features_new;
		}
		
		public void set_n_train(int n_train_new) {
			n_train = n_train_new;
		}
		
		public void set_converged(boolean conv) {
			converged = conv;
		}
		
		public void set_means(DenseMatrix64F means_new) {
			means = means_new;
		}
		
		public void set_weights(DenseMatrix64F weights_new) {
			weights = weights_new;
		}
		
		public void set_covars(DenseMatrix64F[] covars_new) {
			covars = covars_new;
		}
	
}
