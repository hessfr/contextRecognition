import numpy as np
from numpy import linalg
import pickle
import copy
import math
import matplotlib.pyplot as pl
from sklearn.mixture import GMM
from featureExtraction import FX_multiFolders
import ipdb as pdb #pdb.set_trace()

EPS = np.finfo(float).eps

def adaptGMM(trainedGMM, updatePoints, label, nSteps=100):
    """
    Incorporate new (already scaled) data points of a single class into the already existing GMM model
    @param trainedGMM:
    @param updatePoints: already scaled
    param label: Class label of the given feature point.
    @param nSteps: Number of EM iterations that should be performed. Default value is 100
    @return: adapted GMM model
    """
    X = updatePoints[0:2000,:] #updatePoints[0:2,:]
    print(X.shape)
    n_features = X.shape[1]
    n_components = int(trainedGMM["clfs"][0].n_components)

    loglik_threshold = 1e-10

    newGMM = copy.deepcopy(trainedGMM)

    prevLogLik = -float("inf")

    posteriors_old = newGMM["posteriors"][int(label)]

    n_train_old = math.ceil(posteriors_old.sum())
    n_train_new = X.shape[0]

    old_means = newGMM["clfs"][int(label)].means_
    old_covars = newGMM["clfs"][int(label)].covars_
    old_weights = newGMM["clfs"][int(label)].weights_

    means = copy.deepcopy(old_means) # shape = (n_components x n_features)
    covars = copy.deepcopy(old_covars) # shape = (n_components x n_features x n_features)
    weights = copy.deepcopy(old_weights)  # shape = n_components

    converged = False

    # pdb.set_trace()

    for i in range(nSteps):
        # xxx_old and xxx_new refer to the old data where the algorithm was previously trained with and
        # the new data with which it should be adapted
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

        # responsibilities_sklearn = newGMM["clfs"][int(label)].predict_proba(X)

        """ M-Step: """
        for c in range(n_components):
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
                tmpCovar = tmpCovar + (np.dot(t3,t3.T) * responsibilities[j,c]) # contains information about the new points

            t4 = old_means[c,:] - means[c,:]
            t5 = old_covars[c,:] + np.dot(t4,t4.T) # contains information about the means

            covars[c] = (posteriors_old[c] * t5 + tmpCovar) / (posteriors_old[c] + posteriors_new[c])

            # pdb.set_trace()

        """ Stopping criteria: """
        for c in range(n_components):
            # compute new probability:
            proba[:,c] = pdf(X, means[c,:], covars[c])

        # compute log-likelihoods:
        F = np.dot(proba,weights[np.newaxis].T)

        logLik = np.mean(np.log(F))

        if abs((logLik/float(prevLogLik)) - 1) < loglik_threshold:
            converged = True
            break

        prevLogLik = logLik

    if converged == True:
        print("EM algorithm converged after " + str(i+1) + " iterations")
    else:
        print("EM algorithm not converged after " + str(i+1) + " iterations. Increase nSteps parameter to fix this problem")

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
    newGMM["clfs"][int(label)].weights_ = weights
    newGMM["clfs"][int(label)].means_ = means
    newGMM["clfs"][int(label)].covars_ = covars
    newGMM["clfs"][int(label)].converged_ = converged

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
    n_features = data.shape[1]
    n_samples = data.shape[0]

    data = data - np.tile(means,(n_samples,1))
    data = data.T

    prob = np.sum(np.dot(data.T, linalg.inv(covars)).T * data, axis=0)

    prob = np.exp(-0.5*prob) / np.sqrt((2*math.pi) ** n_features * (abs(np.linalg.det(covars)) + EPS)) # float()

    return prob

def reverseDict(oldDict):
    """
    Return new array were keys are the values of the old array and the other way around
    @param oldDict:
    @return:
    """
    newDict = {}
    for i, j in zip(oldDict.keys(), oldDict.values()):
        newDict[j] = i

    return newDict

from adaptGMM import *

















