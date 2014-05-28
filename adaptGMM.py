import numpy as np
from numpy import linalg
import pickle
import copy
import math
import matplotlib.pyplot as pl
from sklearn.mixture import GMM
from featureExtraction import FX_multiFolders
from simulateAL import reverseDict
import ipdb as pdb #pdb.set_trace()

EPS = np.finfo(float).eps

def adaptGMM(trainedGMM, updatePoints, label, nSteps=1000):
    """
    Incorporate new (already scaled) data points of a single class into the already existing GMM model
    @param trainedGMM:
    @param updatePoints: already scaled
    param label: Class label of the given feature point.
    @param nSteps: Number of EM iterations that should be performed. Default value is 100
    @return: adapted GMM model
    """
    X = updatePoints #updatePoints[0:2,:]
    n_features = X.shape[1]
    n_components = trainedGMM["clfs"][0].n_components

    min_covar=1e-3

    loglik_threshold = 0.01 #1e-10

    newGMM = copy.deepcopy(trainedGMM)

    log_likelihood = []

    prevLogLik = -float("inf")

    # TODO: write this information in the GMM dict when it is created:
    allFeatureData = FX_multiFolders(["Conversation","Office","TrainInside"])
    allFeatureDataScaled = trainedGMM['scaler'].transform(allFeatureData["features"])


    log_likelihood_old, responsibilities_old = newGMM["clfs"][int(label)].score_samples(allFeatureDataScaled) # responsibilities_old will be overwritten later
    posteriors_old1 = responsibilities_old.sum(axis=0) # shape = n_components


    n_train_old = math.ceil(posteriors_old1.sum())
    n_train_new = X.shape[0]

    old_means = newGMM["clfs"][int(label)].means_
    old_covars = newGMM["clfs"][int(label)].covars_
    old_weights = newGMM["clfs"][int(label)].weights_

    """ Calculate responsibilities of historical points: """
    proba_old = np.zeros((int(n_train_old), n_components))
    for c in range(n_components):
        proba_old[:,c] = pdf(allFeatureDataScaled, old_means[c,:], old_covars[c])

    # calculate the responsibilities:
    responsibilities_old = np.zeros((int(n_train_old), n_components))
    for j in range(int(n_train_old)):
        responsibilities_old[j,:] = (old_weights * proba_old[j,:]) / np.sum(old_weights * proba_old[j,:])

    """ Calculate posterior probabilities: """
    posteriors_old = responsibilities_old.sum(axis=0) # shape = n_components

    #print(trainedGMM["clfs"][int(label)].weights_)
    #print(posteriors_old/n_train_old) # -> why is this not equal .weights_ ?????????

    #pdb.set_trace()

    means = old_means
    covars = old_covars
    weights = old_weights

    for i in range(nSteps):
        # xxx_old and xxx_new refer to the old data where the algorithm was previously trained with and
        # the new data with which it should be adapted
        """ E-Step: """
        # calculate the probabilities:
        proba = np.zeros((n_train_new, n_components))
        for c in range(n_components):
            proba[:,c] = pdf(X, means[c,:], covars[c])

        # calculate the responsibilities:
        responsibilities = np.zeros((n_train_new, n_components))
        for j in range(n_train_new):
            responsibilities[j,:] = (weights * proba[j,:]) / np.sum(weights * proba[j,:]) # Noch EPS addieren??????

        # curr_log_likelihood, responsibilities = newGMM["clfs"][int(label)].score_samples(X) # responsibilities.shape = (n_train_new x n_components)
        # log_likelihood.append(curr_log_likelihood.sum()) #only needed to check for convergence...

        posteriors_new = responsibilities.sum(axis=0) # shape = n_components

        """ M-Step: """

        # update mixture weights for all components:
        # newGMM["clfs"][int(label)].weights_ = ((posteriors_old + posteriors_new) / float(n_train_old + n_train_new + 10 * EPS) + EPS)# shape = n_components
        weights = ((posteriors_old + posteriors_new) / float(n_train_old + n_train_new + 10 * EPS) + EPS)# shape = n_components

        # update means:
        for c in range(n_components):

            t1 = (old_means[c,:] * posteriors_old[c])
            t2 = np.dot(X.T, responsibilities[:,c][:, np.newaxis]).ravel()

            # newGMM["clfs"][int(label)].means_[c,:] = (t1 + t2) / (posteriors_old[c] + posteriors_new[c])
            means[c,:] = (t1 + t2) / (posteriors_old[c] + posteriors_new[c])

        # update covariance matrix:
        for c in range(n_components):

            # sum over all data points:
            tmpCovar = np.zeros((n_features, n_features))
            for j in range(n_train_new):
                # t3 = X[j,:] - newGMM["clfs"][int(label)].means_[c,:]
                t3 = X[j,:] - means[c,:]
                tmpCovar = tmpCovar + np.dot(t3,t3.T) * responsibilities[j,c]

            # t4 = old_means[c,:] - newGMM["clfs"][int(label)].means_[c,:]
            t4 = old_means[c,:] - means[c,:]
            t5 = old_covars[c,:] + np.dot(t4,t4.T)

            # newGMM["clfs"][int(label)].covars_[c] = (t5 * posteriors_old[c] + tmpCovar) / (posteriors_old[c] + posteriors_new[c])
            covars[c] = (t5 * posteriors_old[c] + tmpCovar) / (posteriors_old[c] + posteriors_new[c])


        """ Stopping criteria: """
        for c in range(n_components):
            # compute new probability:
            proba[:,c] = pdf(X, means[c,:], covars[c])

        # compute log-likelihoods:

        F = np.dot(proba,weights[np.newaxis].T)

        # ??

        logLik = np.mean(np.log(F))

        if abs((logLik/float(prevLogLik)) - 1) < loglik_threshold:
            newGMM["clfs"][int(label)].converged_ = True
            print("EM converged")
            break

        # pdb.set_trace()

        prevLogLik = logLik

        print(i)

    print(str(i) + " loop cycles")

    newGMM["clfs"][int(label)].weights_ = weights
    newGMM["clfs"][int(label)].means_ = means
    newGMM["clfs"][int(label)].covars_ = covars

    # pdb.set_trace()

    """ To test if this method is correct, compare it to batch EM: """
    # # see simulateAL.py for details...
    # allFeatureData = FX_multiFolders(["Conversation","Office","TrainInside"])
    # allFeatureDataScaled = trainedGMM['scaler'].transform(allFeatureData["features"])
    # y_new = np.zeros(updatePoints.shape[0])
    # y_new.fill(label)
    # X_all = np.concatenate((allFeatureDataScaled, updatePoints), axis=0)
    # y_all = np.concatenate((allFeatureData["labels"], y_new), axis=0)
    #
    # pdb.set_trace()
    #
    # batchClf = GMM(n_components=16, covariance_type='full', n_iter=100)
    # iTmp = (y_all == label)
    # tmpTrain = X_all[iTmp]
    # batchClf.fit(tmpTrain)
    #
    # pdb.set_trace()

    return newGMM


def pdf(data, means, covars):
    """
    Calculate Probability Density Function of Gaussian in multiple dimensions. Call this function for each component
    of the mixture individually
    @param data:
    @param means: Means of one component only, shape = n_features
    @param covars: Covars of one component ony, shape = (n_feature, n_features)
    @return:
    """
    # TODO: test (and debug...)
    n_features = data.shape[1]
    n_samples = data.shape[0]

    data = data - np.tile(means,(n_samples,1))
    data = data.T

    prob = np.sum(np.dot(data.T, linalg.inv(covars)).T * data, axis=0) # catch linalg error here!!

    prob = np.exp(-0.5*prob) / float(np.sqrt((2*math.pi) ** n_features * (abs(np.linalg.det(covars))+ EPS)))

    return prob


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

















