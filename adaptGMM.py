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

def adaptGMM(trainedGMM, updatePoints, label, nSteps=100):
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

    loglik_threshold = 1e-10

    newGMM = copy.deepcopy(trainedGMM)

    prevLogLik = -float("inf")

    posteriors_old = newGMM["posteriors"][int(label)]

    # print(newGMM["posteriors"][int(label)])

    n_train_old = math.ceil(posteriors_old.sum())
    n_train_new = X.shape[0]

    old_means = newGMM["clfs"][int(label)].means_
    old_covars = newGMM["clfs"][int(label)].covars_
    old_weights = newGMM["clfs"][int(label)].weights_

    means = copy.deepcopy(old_means)
    covars = copy.deepcopy(old_covars)
    weights = copy.deepcopy(old_weights)

    converged = False

    #pdb.set_trace()

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

        posteriors_new = responsibilities.sum(axis=0) # shape = n_components

        """ M-Step: """
        for c in range(n_components):
            # update weights:
            weights[c] = (posteriors_old[c] + posteriors_new[c]) / (n_train_old + n_train_new) # Noch EPS addieren??????

            # update means:
            t1 = (old_means[c,:] * posteriors_old[c])
            t2 = np.dot(X.T, responsibilities[:,c][:, np.newaxis]).ravel()

            means[c,:] = (t1 + t2) / (posteriors_old[c] + posteriors_new[c])

            # update covariance matrix:
            tmpCovar = np.zeros((n_features, n_features))
            for j in range(n_train_new):
                t3 = X[j,:] - means[c,:]
                tmpCovar = tmpCovar + (np.dot(t3,t3.T) * responsibilities[j,c])

            t4 = old_means[c,:] - means[c,:]
            t5 = old_covars[c,:] + np.dot(t4,t4.T)

            covars[c] = (posteriors_old[c] * t5 + tmpCovar) / (posteriors_old[c] + posteriors_new[c])


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

        # pdb.set_trace()

        prevLogLik = logLik

    if converged == True:
        print("EM algorithm converged after " + str(i) + " cycles")
    else:
        print("EM algorithm not converged after " + str(i) + " cycle. Increase nSteps parameter to fix this problem")

    """ Calculate posterior probabilities for the adapted class and update them in the classifier dict: """
    # calculate the probabilities:
    proba = np.zeros((n_train_new, n_components))
    for c in range(n_components):
        proba[:,c] = pdf(X, means[c,:], covars[c])

    # calculate the responsibilities:
    responsibilities = np.zeros((n_train_new, n_components))
    for j in range(n_train_new):
        responsibilities[j,:] = (weights * proba[j,:]) / np.sum(weights * proba[j,:]) # Noch EPS addieren??????

    posteriors = responsibilities.sum(axis=0) # shape = n_components

    newGMM["posteriors"][int(label)] = posteriors

    """ update all other model parameters in the classifier dict: """
    newGMM["clfs"][int(label)].weights_ = weights
    newGMM["clfs"][int(label)].means_ = means
    newGMM["clfs"][int(label)].covars_ = covars
    newGMM["clfs"][int(label)].converged_ = converged

    # print(newGMM["posteriors"][int(label)])

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

    prob = np.sum(np.dot(data.T, linalg.inv(covars)).T * data, axis=0) # catch linalg error here!!

    prob = np.exp(-0.5*prob) / float(np.sqrt((2*math.pi) ** n_features * (abs(np.linalg.det(covars))+ EPS)))

    return prob


from adaptGMM import *

















