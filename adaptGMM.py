import numpy as np
#from numpy import linalg #xxxxxxxxxxx check if it's still needed
from scipy import linalg
import pickle
import copy
import math
import matplotlib.pyplot as pl
from sklearn.mixture import GMM
from featureExtraction import FX_multiFolders
import ipdb as pdb #pdb.set_trace()

EPS = np.finfo(float).eps

def adaptGMM(trainedGMM, updatePoints, label):
    """
    Incorporate new (already scaled) data points of a single class into the already existing GMM model
    Implementation according to Song 2005
    @param trainedGMM: Dictionary containing data of the GMM classifiers.
    @param updatePoints: Numpy array of new points of the same class, with which the model should be updated. Has to be scaled already
    @param label: Class label of the updatePoints.
    @return: adapted GMM model
    """
    X = updatePoints #updatePoints[0:2,:]
    label = int(label)
    n_features = X.shape[1]
    n_components_old = int(trainedGMM["clfs"][label].n_components)


    """ Estimate number of components in the new data using BIC: """
    n_components_list = range(1,17)
    bicList = []
    for c in n_components_list:
        tmpClf = GMM(n_components=c, covariance_type='full', n_iter=1000)
        tmpClf.fit(X)
        bicList.append(bic(X, tmpClf.means_, tmpClf.covars_, tmpClf.weights_, c))

        # print(str(c) + " components: " + " BIC = " + str(bicList[-1]))

    # select that number of components that resulted in the best (lowest) BIC:
    val, n_components_new = min((val, idx) for (idx, val) in enumerate(bicList))
    n_components_new += 1
    print("Optimal number of components according to BIC: " + str(n_components_new))

    """ Perform EM algorithm on the new data: """
    novelGMM = GMM(n_components=n_components_new, covariance_type='full', n_iter=1000)
    novelGMM.fit(X)

    likelihoods = np.zeros((n_components_new, X.shape[0]))
    """ Assign novel points to component it most likely belongs to: """
    for k in range(n_components_new):
        likelihoods[k] = pdf(X, novelGMM.means_[k,:], novelGMM.covars_[k])
    pointComponents = np.argmax(likelihoods, 0) # Indicates for each point the component it most likely belongs to

    Dk = [] # First element is numpy array with all data points that are assigned to first components, and so on...
    for k in range(n_components_new):
        iTmp = (pointComponents == k)
        Dk.append(X[iTmp])

    """ Test covariance and mean equality: """
    mapping = [-1] * 16 # Mapping from OLD to NOVEL (!) components, if old components is not mapped to novel one, the value equals -1.
                        # Elements initialized to -1. mapping[3] = 5 means that old component 3 is equal to new component 5.
    for k in range(n_components_new):
        for j in range(n_components_old):
            if covarTest() == True:
                if meanTest() == True:
                    mapping[j] = k
                    #TODO: compute log-likelihood of novel points of that new component under component j of old model. ?????

    for el in mapping:
        if el != -1:
            mergeComponents()
        else:
            addHistoricalComponent()

    for n in range(n_components_new):
        if n not in mapping:
            addNovelComponent()








def covarTest():
    """
    Calculate W statistic to determine if both components have equal covariance

    @return: True if covars are equal, False if not
    """

    return True

def meanTest():
    """
    Use Hotelling T-squared test to determine if both components have equal means

    @return: True if covars are equal, False if not
    """

    return True

def mergeComponents(n_old, n_novel, n_comp, weight_old, weight_novel, means_old, means_novel, covars_old, covars_novel):
    """
    Merge two mixture components.
    @param n_old: Number of training points of the old classifier
    @param n_novel: Number of novel points
    @param n_comp: Number of novel points assigned to this component
    @param weight_old: scalar
    @param weight_novel: scalar
    @param means_old: shape = (n_features,)
    @param means_novel: shape = (n_features,)
    @param covars_old: shape = (n_features, n_features)
    @param covars_novel: shape = (n_features, n_features)

    @return: merged component
    """

    means = (n_old * weight_old * means_old + n_comp * means_old) / float(n_old * weight_old + n_comp)

    return True

def addHistoricalComponent():
    """
    Add mixture component mixture component from the previous GMM. Use equation (10)


    """
    pass

def addNovelComponent():
    """
    Add mixture component mixture component from the novel GMM. Use equation (9)


    """
    pass


def pdf(data, means, covars):
    """
    Calculate Probability Density Function of Gaussian in multiple dimensions. Call this function for each component
    of the mixture individually
    @param data: Numpy array of the points, shape = (n_samples, n_features)
    @param means: Means of one component only, shape = n_features
    @param covars: Covars of one component ony, shape = (n_feature, n_features)
    @return:
    """
    n_features = data.shape[1]
    n_samples = data.shape[0]

    data = data - np.tile(means,(n_samples,1))
    data = data.T

    prob = np.sum(np.dot(data.T, linalg.inv(covars)).T * data, axis=0)

    prob = np.exp(-0.5*prob) / np.sqrt((2*math.pi) ** n_features * (abs(np.linalg.det(covars)) + EPS)) # float()

    return prob

def logProb(X, weights, means, covars):
    """
    Calculate the log probability of multiple points under a GMM represented by the weights, means, covars parameters

    @param X: Numpy array representing the input data. Each row refers to one point
    @param weights: Component weights
    @param means: Means
    @param covars: Full covariance matrix of the GMM
    @return:
    """
    X = copy.copy(X)
    n_samples, n_features = X.shape
    n_components = means.shape[0]
    min_covar = 1e-7

    if X.ndim == 1:
        X = X[:, np.newaxis]
    if X.size == 0:
        return np.array([]), np.empty((0, n_components))
    if X.shape[1] != means.shape[1]:
        raise ValueError('The shape of X  is not compatible with self')

    log_prob = np.empty((n_samples, n_components))

    for c, (mu, cv) in enumerate(zip(means, covars)):
        try:
            cv_chol = linalg.cholesky(cv, lower=True)
        except linalg.LinAlgError:
            # reinitialize component, because it might be stuck with too few observations
            cv_chol = linalg.cholesky(cv + min_covar * np.eye(n_features),lower=True)

        cv_log_det = 2 * np.sum(np.log(np.diagonal(cv_chol)))

        cv_sol = linalg.solve_triangular(cv_chol, (X - mu).T, lower=True).T

        log_prob[:, c] = - .5 * (np.sum(cv_sol ** 2, axis=1) + n_features * np.log(2 * np.pi) + cv_log_det)

    tmp_log_prob = (log_prob + np.log(weights))

    # compute sum in log domain:
    tmpArray = np.rollaxis(tmp_log_prob, axis=1)
    vmax = tmpArray.max(axis=0)
    final_log_prob = np.log(np.sum(np.exp(tmpArray - vmax), axis=0))
    final_log_prob = final_log_prob + vmax

    return final_log_prob

def bic(data, means, covars, weights, n_comp):
    """
    Calculate BIC criteria (the lower this value, the better)
    @param data: Numpy array of the points, shape = (n_samples, n_features)
    @param means: Means of one component only, shape = n_features
    @param covars: Covars of one component ony, shape = (n_feature, n_features)
    @return: BIC criteria (the lower this value, the better)
    """
    n_points = data.shape[0] #number of points
    n_feat = data.shape[1] # number of features

    logLik = -2.0 * logProb(data, weights, means, covars).sum() #xxxx check if using .sum() is correct

    complexity = ((n_comp/2.0 * (n_feat+1) * (n_feat+2)) - 1.0) * np.log(n_points)

    # bic = (logLik + complexity)

    return (logLik + complexity)

def adaptGMM_OLD(trainedGMM, updatePoints, label, nSteps=100):
    """
    Incorporate new (already scaled) data points of a single class into the already existing GMM model
    @param trainedGMM: Dictionary containing data of the GMM classifiers.
    @param updatePoints: Numpy array of new points of the same class, with which the model should be updated. Have to be scaled already
    param label: Class label of the updatePoints.
    @param nSteps: Maximum number of EM iterations that should be performed. Default value is 100
    @return: adapted GMM model
    """
    X = updatePoints #updatePoints[0:2,:]
    n_features = X.shape[1]
    n_components = int(trainedGMM["clfs"][0].n_components)

    loglik_threshold = 1e-10

    newGMM = copy.deepcopy(trainedGMM)

    prevLogLik = -8.21 # -float("inf")
    bestLogLik = -8.21

    posteriors_old = copy.deepcopy(newGMM["posteriors"][int(label)])

    n_train_old = math.ceil(posteriors_old.sum())
    n_train_new = X.shape[0]

    old_means = newGMM["clfs"][int(label)].means_
    old_covars = newGMM["clfs"][int(label)].covars_
    old_weights = newGMM["clfs"][int(label)].weights_

    means = copy.deepcopy(old_means) # shape = (n_components x n_features)
    covars = copy.deepcopy(old_covars) # shape = (n_components x n_features x n_features)
    weights = copy.deepcopy(old_weights)  # shape = n_components

    best_means = copy.deepcopy(old_means) # shape = (n_components x n_features)
    best_covars = copy.deepcopy(old_covars) # shape = (n_components x n_features x n_features)
    best_weights = copy.deepcopy(old_weights)  # shape = n_components

    converged = False

    logLikList = []

    for i in range(nSteps):
        """ E-Step: """
        # calculate the probabilities:
        proba = np.empty((n_train_new, n_components))
        for c in range(n_components):
            proba[:,c] = pdf(X, means[c,:], covars[c])

        # calculate the responsibilities:
        responsibilities = np.empty((n_train_new, n_components))
        for j in range(n_train_new):
            responsibilities[j,:] = (weights * proba[j,:]) / (np.sum(weights * proba[j,:]) + EPS) + EPS

        posteriors_new = responsibilities.sum(axis=0) # shape = n_components

        """ M-Step: """
        for c in range(n_components):
            # pdb.set_trace()

            # update weights:
            weights[c] = (posteriors_old[c] + posteriors_new[c]) / (n_train_old + n_train_new)

            # update means:
            t1 = (old_means[c,:] * posteriors_old[c]) # from historical points
            t2 = np.dot(X.T, responsibilities[:,c][:, np.newaxis]).ravel() # from the new points

            means[c,:] = (t1 + t2) / (posteriors_old[c] + posteriors_new[c])

            # update covariance matrix:
            tmpCovar = np.zeros((n_features, n_features))
            for j in range(n_train_new):
                t3 = X[j,:] - means[c,:]
                tmpCovar = tmpCovar + (np.dot(t3,t3.T) * responsibilities[j,c]) # contains the information about the new points

            t4 = old_means[c,:] - means[c,:]
            t5 = old_covars[c,:] + np.dot(t4,t4.T) # contains the information about the means

            covars[c] = (posteriors_old[c] * t5 + tmpCovar) / (posteriors_old[c] + posteriors_new[c])

        """ Stopping criteria: """
        for c in range(n_components):
            # compute new probability:
            proba[:,c] = pdf(X, means[c,:], covars[c])

        # compute log-likelihoods:
        F = np.dot(proba,weights[np.newaxis].T)

  #      pdb.set_trace()

        logLik = np.mean(np.log(F))

        """ update best values: """
        if logLik > bestLogLik:
            bestLogLik = logLik
            best_covars = copy.deepcopy(covars)
            best_means = copy.deepcopy(means)
            best_weights = copy.deepcopy(weights)

        # if abs((logLik/float(prevLogLik)) - 1) < loglik_threshold:
        #     converged = True
        #     break

        # pdb.set_trace()

        if abs(logLik - prevLogLik) < loglik_threshold * abs(logLik):
            converged = True
            break

        logLikList.append(logLik)

        prevLogLik = logLik

    if converged == True:
        print("EM algorithm converged after " + str(i+1) + " iterations")
    else:
        print("EM algorithm not converged after " + str(i+1) + " iterations. Increase nSteps parameter to fix it")

    """ Calculate posterior probabilities for the adapted class and update them in the classifier dict: """
    # calculate the probabilities:
    proba = np.zeros((n_train_new, n_components))
    for c in range(n_components):
        proba[:,c] = pdf(X, means[c,:], covars[c])

    # calculate the responsibilities:
    responsibilities = np.zeros((n_train_new, n_components))
    for j in range(n_train_new):
        responsibilities[j,:] = (weights * proba[j,:]) / (np.sum(weights * proba[j,:]) + EPS) + EPS

    posteriors = responsibilities.sum(axis=0) # shape = n_components

    newGMM["posteriors"][int(label)] = (posteriors + posteriors_old)

    """ update all other model parameters in the classifier dict: """
    newGMM["clfs"][int(label)].weights_ = best_weights
    newGMM["clfs"][int(label)].means_ = best_means
    newGMM["clfs"][int(label)].covars_ = best_covars
    newGMM["clfs"][int(label)].converged_ = converged

    return logLikList, newGMM

from adaptGMM import *

















