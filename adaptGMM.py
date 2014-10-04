import numpy as np
from scipy import linalg
from scipy.stats import f
from scipy.stats import chi2
import pickle
import json
import copy
import math
import matplotlib.pyplot as pl
from sklearn.mixture import GMM
from classifiers import trainGMMJava
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
    n_novel = X.shape[0]
    label = int(label)
    n_features = X.shape[1]
    n_components_old = int(trainedGMM["clfs"][label].n_components)
    n_old = trainedGMM["n_train"][label]

    oldGMM = copy.deepcopy(trainedGMM["clfs"][label])

    mergedComp = 0
    addedComp = 0

    """ Estimate number of components in the new data using BIC: """
    n_components_list = range(1,8)
    bicList = []
    for c in n_components_list:
        tmpClf = GMM(n_components=c, covariance_type='full', n_iter=1000)
        tmpClf.fit(X)
        bicList.append(bic(X, tmpClf.means_, tmpClf.covars_, tmpClf.weights_, c))

        # print(str(c) + " components: " + " BIC = " + str(bicList[-1]))

    # select that number of components that resulted in the best (lowest) BIC:
    val, n_components_new = min((val, idx) for (idx, val) in enumerate(bicList))
    n_components_new += 1
    print("Optimal number of components according to BIC criteria: " + str(n_components_new))

    """ Perform EM algorithm on the new data: """
    novelGMM = GMM(n_components=n_components_new, covariance_type='full', n_iter=1000)
    novelGMM.fit(X)
    
    #pdb.set_trace()

    """ Assign novel points to component it most likely belongs to: """
    likelihoods = np.zeros((n_components_new, n_novel))
    for k in range(n_components_new):
        likelihoods[k] = pdf(X, novelGMM.means_[k,:], novelGMM.covars_[k,:,:])
    pointComponents = np.argmax(likelihoods, 0) # Indicates for each point the component it most likely belongs to

    Dk = [] # First element is numpy array with all data points that are assigned to first component (of the novel model), and so on...
            # use index for novel points (k) to get data from this list
    Mk = [] # Contains number of points for each array in Dk

    for k in range(n_components_new):
        iTmp = (pointComponents == k)
        Dk.append(X[iTmp])
        Mk.append(X[iTmp].shape[0])

    """ Test covariance and mean equality: """
    mapping = [-1] * n_components_old # Mapping from OLD to NOVEL (!) components, if old components is not mapped to novel one, the value equals -1.
                        # Elements initialized to -1. mapping[3] = 5 means that old component 3 is equal to new component 5.

    for k in range(n_components_new):
        for j in range(n_components_old):
            # print("Old comp: " + str(j) + " new comp: " + str(k))
            # if True:            
            if covarTest(Dk[k], oldGMM.covars_[j]) == True:
                if meanTest(Dk[k], oldGMM.means_[j]) == True:
                    mapping[j] = k

    new_model = [] # List containing parameters (weight, means, covars) for each component

    # pdb.set_trace()

    """ Compute components: """
    j = 0    
    for k in mapping:
        if k != -1:
            tmpComponent = mergeComponents(n_old, n_novel, Mk[k], oldGMM.weights_[j], novelGMM.weights_[k], oldGMM.means_[j], novelGMM.means_[k], oldGMM.covars_[j], novelGMM.covars_[k])
            mergedComp += 1
            new_model.append(tmpComponent)
        else:
            tmpComponent = addHistoricalComponent(n_old, n_novel, oldGMM.weights_[j], oldGMM.means_[j], oldGMM.covars_[j])
            new_model.append(tmpComponent)
        j += 1

    for n in range(n_components_new):
        if n not in mapping:
            tmpComponent = addNovelComponent(n_old, n_novel, Mk[n], novelGMM.means_[n], novelGMM.covars_[n])
            addedComp += 1
            new_model.append(tmpComponent)

    """ Create GMM object that should be return: """
    finalGMM = copy.deepcopy(trainedGMM)


    finalGMM["clfs"][label] = GMM(n_components=len(new_model), covariance_type='full')
    dummy = np.random.random((100,n_features))
    finalGMM["clfs"][label].fit(dummy) # workaround, as sklearn requires that .fit is called before using this GMM. All values are overwritten later anyway


    for i in range(len(new_model)):
        finalGMM["clfs"][label].weights_[i] = new_model[i][0]
        finalGMM["clfs"][label].means_[i] = new_model[i][1]
        finalGMM["clfs"][label].covars_[i] = new_model[i][2]
        finalGMM["n_train"][label] = n_old + n_novel

    # print(finalGMM["n_train"][label])

    print("Model adapted: new model has " + str(finalGMM["clfs"][label].n_components) +
          " component(s). " + str(mergedComp) + " component(s) merged, " + str(addedComp) + " component(s) added.")

    return finalGMM



def covarTest(data, covars_old):
    """
    Calculate W statistic to determine if both components have equal covariance

    @param covars_old: old covariance matrix for one component, shape: (n_features, n_features)

    @return: True if covars are equal, False if not
    """

    X = data
    n_samples = data.shape[0]
    n_features = data.shape[1]
    id = np.identity(n_features)
    
    L0 = linalg.cholesky(covars_old, lower=True) # shape = (n_features, n_features)
    L0inv = linalg.inv(L0)

    Y = np.zeros((n_samples, n_features))

    for i in range(n_samples):
        Y[i,:] = np.dot(L0inv, X[i,:])

    Sy = np.cov(Y, rowvar=0) # if rowvar = 0, each row represents an observation

    w1 = np.trace(np.dot((Sy-id),(Sy-id))) / float(n_features)
    w2 = (n_features/float(n_samples)) * ((np.trace(Sy) / float(n_features)) ** 2)
    w3 = n_features / float(n_samples)

    W = w1 - w2 + w3 # W statistic

    test_statistic = (n_samples * W * n_features) / 2.0

    alphaPercentile = 0.05
    threshold = chi2.ppf(alphaPercentile, (0.5 * (n_features * (n_features + 1))))

    #if (n_samples < 30): # only compare if same component
        #print(w1)

    if test_statistic <= threshold:
        print("Covariance test passed")
        return True
    else:
        return False

def meanTest(data, means_old):
    """
    Use Hotelling T-squared test to determine if both components have equal means

    @param means_old: old mean values for one component, shape: (n_features,)

    @return: True if covars are equal, False if not
    """
    X = data
    n_samples = data.shape[0]
    n_features = data.shape[1]

    S = np.cov(X, rowvar=0) # if rowvar = 0, each row represents an observation
    Sinv = linalg.inv(S)

    m = X.mean(axis=0) - means_old
    T_squared = n_samples * np.dot(np.dot(m.T, Sinv), m)

    test_statistic = ((n_samples - n_features) * T_squared) / float(n_features*(n_samples - 1))

    alphaPercentile = 0.05
    threshold = f.ppf(alphaPercentile, n_features, (n_samples - n_features))
    # f.ppf returns the k-percentile of the f-distribution

    # print(str(test_statistic) + " - threshold: " + str(threshold))

    if test_statistic <= threshold:
        print("Mean test passed")
        return True
    else:
        return False

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

    #covars:
    c1 = (n_old * weight_old * covars_old + n_comp * covars_novel) / float(n_old * weight_old + n_comp)
    c2 = (n_old * weight_old * np.dot(means_old,means_old.T) + n_comp * np.dot(means_novel, means_novel.T)) / float(n_old * weight_old + n_comp)
    c3 = np.dot(means,means.T)
    covars = c1 + c2 + c3

    weight = (n_old * weight_old + n_comp) / float(n_old + n_novel)

    pdb.set_trace()

    return [weight, means, covars]

def addHistoricalComponent(n_old, n_novel, weight_old, means_old, covars_old):
    """
    Add mixture component mixture component from the previous GMM. Use equation (10)


    """

    weight = (n_old * weight_old) / float(n_old + n_novel)

    return [weight, means_old, covars_old]

def addNovelComponent(n_old, n_novel, n_comp, means_novel, covars_novel):
    """
    Add mixture component mixture component from the novel GMM. Use equation (9)


    """
    weight = n_comp / float(n_old + n_novel)

    # print(n_comp)
    # print(means_novel.sum())
    # print(covars_novel.sum())

    return [weight, means_novel, covars_novel]

def pdf(data, means, covars):
    """
    Calculate Probability Density Function of Gaussian in multiple dimensions. Call this function for each component
    of the mixture individually
    @param data: Numpy array of the points, shape = (n_samples, n_features)
    @param means: Means of one component only, shape = n_features
    @param covars: Covars of one component ony, shape = (n_feature, n_features)
    @return:
    """
    n_samples = data.shape[0]
    n_features = data.shape[1]

    data = data - np.tile(means,(n_samples,1)) # tile creates a matrix where each row is equal to means
    data = data.T

    prob = np.sum(np.dot(data.T, linalg.inv(covars)).T * data, axis=0) #colSum in Java

    prob = np.exp(-0.5*prob) / np.sqrt((2*math.pi) ** n_features * (abs(np.linalg.det(covars)) + EPS))

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
    @param data: Numpy array - already scale. shape = (n_samples, n_features)
    @param means: Means of one component only, shape = n_features
    @param covars: Covars of one component ony, shape = (n_feature, n_features)
    @return: BIC criteria (the lower this value, the better)
    """
    n_points = data.shape[0] #number of points
    n_feat = data.shape[1] # number of features

    logLik = -2.0 * logProb(data, weights, means, covars).sum()

    complexity = ((n_comp/2.0 * (n_feat+1) * (n_feat+2)) - 1.0) * np.log(n_points)

    return (logLik + complexity)
