import numpy as np
import pickle
import copy
import matplotlib.pyplot as pl
from sklearn.mixture import GMM
from featureExtraction import FX_multiFolders
from simulateAL import reverseDict
import ipdb as pdb #pdb.set_trace()

EPS = np.finfo(float).eps

def adaptGMM(trainedGMM, featurePoints, label, nSteps=100):
    """
    Incorporate new (already scaled) data points of a single class into the already existing GMM model
    @param trainedGMM:
    @param featurePoints: already scaled
    param label: Class label of the given feature point.
    @param nSteps: Number of EM iterations that should be performed. Default value is 100
    @return: adapted GMM model
    """
    X = featurePoints
    n_features = X.shape[1]
    n_components = trainedGMM["clfs"][0].n_components

    min_covar=1e-3

    newGMM = copy.deepcopy(trainedGMM)

    log_likelihood = []


    # TODO: write this information in the GMM dict when it is created:
    allFeatureData = FX_multiFolders(["Conversation","Office","Train"])
    allFeatureDataScaled = trainedGMM['scaler'].transform(allFeatureData["features"])
    curr_log_likelihood, responsibilities = trainedGMM["clfs"][int(label)].score_samples(allFeatureDataScaled)
    n_train_old = allFeatureDataScaled.shape[0]
    posteriors_old = responsibilities.sum(axis=0) # shape = n_components
    n_train_new = X.shape[0]
    old_means = trainedGMM["clfs"][int(label)].means_
    old_covars = trainedGMM["clfs"][int(label)].covars_

    # weights_old = n_train_old * trainedGMM["clfs"][int(label)].weights_ #remove xxxxxxxxxx
    # weighted_X_sum_old = trainedGMM["clfs"][int(label)].means_ * weights_old[:, np.newaxis] #remove xxxxxxxxxx



    for i in range(nSteps):
        # xxx_old and xxx_new refer to the old data where the algorithm was previously trained with and
        # the new data with which it should be adapted
        """ E-Step: """
        # calculate the membership weights (stored in the responsibilities matrix)

        curr_log_likelihood, responsibilities = newGMM["clfs"][int(label)].score_samples(X) # responsibilities.shape = (n_train_new x n_components)
        #log_likelihood.append(curr_log_likelihood.sum()) #only needed to check for convergence...

        posteriors_new = responsibilities.sum(axis=0) # shape = n_components

        """ M-Step: """
        # implementation of algorithm similar to Calinon 2007

        # update mixture weights for all components:
        newGMM["clfs"][int(label)].weights_ = (posteriors_old + posteriors_new) / float(n_train_old + n_train_new) # shape = n_components

        # to delete:
        # weights_new = responsibilities.sum(axis=0)
        # inverse_weights_new = 1.0 / (weights_new[:, np.newaxis] + 10 * EPS)
        # weights = weights_old + weights_new
        # # update mixture weights:
        # newGMM["clfs"][int(label)].weights_ = (weights / n_train_new) #TODO: check this again


        # update means:
        for c in range(n_components):

            t1 = (old_means[c,:] * posteriors_old[c])
            t2 = np.dot(X.T, responsibilities[:,c][:, np.newaxis]).ravel()

            newGMM["clfs"][int(label)].means_[c,:] = (t1 + t2) / (posteriors_old[c] + posteriors_new[c])

        # # update means:
        # weighted_X_sum_new = np.dot(responsibilities.T, X)
        # weighted_X_sum = weighted_X_sum_old + weighted_X_sum_new
        # inverse_weights = 1.0 / (weights[:, np.newaxis] + 10 * EPS)
        # newGMM["clfs"][int(label)].means_ = weighted_X_sum * inverse_weights

        # update covariance matrix:
        for c in range(n_components):

            # sum over all data points:
            tmpCovar = np.zeros((n_features, n_features))
            for j in range(n_train_new):
                t3 = X[j,:] - newGMM["clfs"][int(label)].means_[c,:]
                tmpCovar = tmpCovar + np.dot(t3,t3.T) * responsibilities[j,c]

            t4 = old_means[c,:] - newGMM["clfs"][int(label)].means_[c,:]
            t5 = old_covars[c,:] + np.dot(t4,t4.T)

            newGMM["clfs"][int(label)].covars_[c] = (t5 * posteriors_old[c] + tmpCovar) / (posteriors_old[c] + posteriors_new[c])

            #pdb.set_trace()

            # posteriors_new = responsibilities[:, c]
            # # Underflow Errors in doing post * X.T are  not important
            # np.seterr(under='ignore')
            #
            # avg_cv = np.dot(posteriors_new * X.T, X) / (posteriors_new.sum() + 10 * EPS)
            #
            # mu = newGMM["clfs"][int(label)].means_[c][np.newaxis]
            #
            # newCovars = (avg_cv - np.dot(mu.T, mu) + min_covar * np.eye(n_features))





    """ To test if this method is correct, compare it to batch EM: """
    # see simulateAL.py for details...
    allFeatureData = FX_multiFolders(["Conversation","Office","Train"])
    allFeatureDataScaled = trainedGMM['scaler'].transform(allFeatureData["features"])
    y_new = np.zeros(featurePoints.shape[0])
    y_new.fill(label)
    X_all = np.concatenate((allFeatureDataScaled, featurePoints), axis=0)
    y_all = np.concatenate((allFeatureData["labels"], y_new), axis=0)

    pdb.set_trace()

    batchClf = GMM(n_components=16, covariance_type='full', n_iter=100)
    iTmp = (y_all == label)
    tmpTrain = X_all[iTmp]
    batchClf.fit(tmpTrain)

    pdb.set_trace()

def _covar_mstep_full(gmm, X, responsibilities, weighted_X_sum, norm,
                      min_covar):
    """Performing the covariance M step for full cases"""
    # Eq. 12 from K. Murphy, "Fitting a Conditional Linear Gaussian
    # Distribution"
    n_features = X.shape[1]
    cv = np.empty((gmm.n_components, n_features, n_features))
    for c in range(gmm.n_components):
        post = responsibilities[:, c]
        # Underflow Errors in doing post * X.T are  not important
        np.seterr(under='ignore')
        avg_cv = np.dot(post * X.T, X) / (post.sum() + 10 * EPS)
        mu = gmm.means_[c][np.newaxis]
        cv[c] = (avg_cv - np.dot(mu.T, mu) + min_covar * np.eye(n_features))
    return cv

def fit(self, X):
    """Estimate model parameters with the expectation-maximization
    algorithm.

    A initialization step is performed before entering the em
    algorithm. If you want to avoid this step, set the keyword
    argument init_params to the empty string '' when creating the
    GMM object. Likewise, if you would like just to do an
    initialization, set n_iter=0.

    Parameters
    ----------
    X : array_like, shape (n, n_features)
        List of n_features-dimensional data points.  Each row
        corresponds to a single data point.
    """
    ## initialization step
    X = np.asarray(X, dtype=np.float)
    if X.ndim == 1:
        X = X[:, np.newaxis]
    if X.shape[0] < self.n_components:
        raise ValueError(
            'GMM estimation with %s components, but got only %s samples' %
            (self.n_components, X.shape[0]))

    max_log_prob = -np.infty

    for _ in range(self.n_init):

        #### initialization ###
        if 'm' in self.init_params or not hasattr(self, 'means_'):
            self.means_ = cluster.KMeans(
                n_clusters=self.n_components,
                random_state=self.random_state).fit(X).cluster_centers_

        if 'w' in self.init_params or not hasattr(self, 'weights_'):
            self.weights_ = np.tile(1.0 / self.n_components,
                                    self.n_components)

        if 'c' in self.init_params or not hasattr(self, 'covars_'):
            cv = np.cov(X.T) + self.min_covar * np.eye(X.shape[1])
            if not cv.shape:
                cv.shape = (1, 1)
            self.covars_ = \
                distribute_covar_matrix_to_match_covariance_type(
                    cv, self.covariance_type, self.n_components)
        ######


        # EM algorithms
        log_likelihood = []
        # reset self.converged_ to False
        self.converged_ = False
        for i in range(self.n_iter):
            # Expectation step
            curr_log_likelihood, responsibilities = self.score_samples(X)
            log_likelihood.append(curr_log_likelihood.sum())

            # Check for convergence.
            if i > 0 and abs(log_likelihood[-1] - log_likelihood[-2]) < \
                    self.thresh:
                self.converged_ = True
                break

            # Maximization step
            self._do_mstep(X, responsibilities, self.params,
                           self.min_covar)

        # if the results are better, keep it
        if self.n_iter:
            if log_likelihood[-1] > max_log_prob:
                max_log_prob = log_likelihood[-1]
                best_params = {'weights': self.weights_,
                               'means': self.means_,
                               'covars': self.covars_}
    # check the existence of an init param that was not subject to
    # likelihood computation issue.
    if np.isneginf(max_log_prob) and self.n_iter:
        raise RuntimeError(
            "EM algorithm was never able to compute a valid likelihood " +
            "given initial parameters. Try different init parameters " +
            "(or increasing n_init) or check for degenerate data.")
    # self.n_iter == 0 occurs when using GMM within HMM
    if self.n_iter:
        self.covars_ = best_params['covars']
        self.means_ = best_params['means']
        self.weights_ = best_params['weights']
    return self

def _do_mstep(self, X, responsibilities, params, min_covar=0):
    """ Perform the Mstep of the EM algorithm and return the class weihgts.
    """
    weights = responsibilities.sum(axis=0)
    weighted_X_sum = np.dot(responsibilities.T, X)
    inverse_weights = 1.0 / (weights[:, np.newaxis] + 10 * EPS)

    if 'w' in params:
        self.weights_ = (weights / (weights.sum() + 10 * EPS) + EPS)
    if 'm' in params:
        self.means_ = weighted_X_sum * inverse_weights
    if 'c' in params:
        covar_mstep_func = _covar_mstep_funcs[self.covariance_type]
        self.covars_ = covar_mstep_func(
            self, X, responsibilities, weighted_X_sum, inverse_weights,
            min_covar)
    return weights








from adaptGMM import *

















