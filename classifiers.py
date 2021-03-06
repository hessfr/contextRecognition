import matplotlib
matplotlib.use('Agg')
import matplotlib.pylab as pl
import os
from os import listdir
import numpy as np
from scipy import linalg
from numpy import asarray
import csv
import pickle
import json
from sklearn.mixture import GMM
from sklearn import cross_validation
from sklearn import preprocessing
from sklearn.metrics import confusion_matrix
from sklearn.cross_validation import KFold
import math
import operator
import copy
from scipy.stats import itemfreq
import ipdb as pdb

EPS = np.finfo(float).eps

def majorityVote(y_Raw, returnDisagreement=False):
    """
    Apply a "majority vote" of fixed window length to a sequence
    @param y_Raw: Input data as 1D numpy array
    @param returnDisagreement: Set to True if the disagreement of the majority vote should be returned.
    Only use this when you call the majorityVote method for exactly one frame. Default is False
    @return: Result of the same size as input
    """
    y_raw = y_Raw.copy()
    majVotWindowLength = 2.0 #in seconds
    windowLength = 0.032
    frameLengthFloat = math.ceil(majVotWindowLength/windowLength)

    frameLength = int(frameLengthFloat)

    resArray = np.empty(y_raw.shape)

    n_frames = int(math.ceil(y_raw.shape[0]/frameLengthFloat))

    for i in range(n_frames):
        if ((i+1) * frameLength) < y_raw.shape[0]:

            tmpArray = y_raw[(i * frameLength):(((i+1) * frameLength))]
            
            """ Get most frequent number in that frames: """
            count = np.bincount(tmpArray)
            tmpMostFrequent = np.argmax(count)

            """ Calculate disagreement: """
            iTmp = (tmpArray != tmpMostFrequent)
            t = tmpArray[iTmp]
            disagreement = t.shape[0] / frameLengthFloat


            """ Fill all elements with most frequent number: """
            tmpArray.fill(tmpMostFrequent)

            """ Write it into our result array: """
            resArray[(i * frameLength):(((i+1) * frameLength))] = tmpArray

        else:

            tmpArray = y_raw[(i * frameLength):y_raw.shape[0]]

            """ Get most frequent number in that frames and fill all elements in the frame with it: """
            count = np.bincount(tmpArray)
            tmpMostFrequent = np.argmax(count)


            """ Calculate disagreement: """
            iTmp = (tmpArray != tmpMostFrequent)
            t = tmpArray[iTmp]
            disagreement = t.shape[0] / frameLengthFloat

            """ Fill all elements with most frequent number: """
            tmpArray.fill(tmpMostFrequent)

            """ Write it into our result array: """
            resArray[(i * frameLength):y_raw.shape[0]] = tmpArray

    if returnDisagreement == False:
        return resArray
    else:
        return resArray, disagreement

def trainGMM(featureData):
    """
    Build a Gaussian Mixture Model on the given data using Scikit-Learn.
    To access attributes of the GMMs use .weights_, .means_ and .covars_
    @param featureData: Dictionary containing 'features' and 'labels' as numpy array and 'classesDict' for mapping of class names to numbers
    @return: Dictionary containing trained scikit-learn GMM classifiers in 'clfs' and 'classesDict' for mapping of class names to numbers
    """
    X_train = featureData['features']
    scaler = preprocessing.StandardScaler().fit(X_train)
    X_train = scaler.transform(X_train)

    y_train = featureData['labels']
    
    n_classes = len(np.unique(y_train))

    n_comp = 16 # number of components

    print("Training classifier with " + str(n_classes) + " different classes")

    clfs = []
    n_train_list = []

 
    for i in range(n_classes):
        tmpClf = GMM(n_components=n_comp, covariance_type='full', n_iter=500)
        iTmp = (y_train == i)

        tmpTrain = X_train[iTmp]

        n_tmp = tmpTrain.shape[0]

        """ use expectation-maximization to fit the Gaussians: """
        tmpClf.fit(tmpTrain)
        clfs.append(tmpClf)

        n_train_list.append(n_tmp)

        print("Component trained with " + str(n_train_list[-1]) + " sample points")

    trainedGMM = {'clfs': clfs, 'classesDict': featureData['classesDict'], 'n_train': n_train_list, 'scaler': scaler}

    return trainedGMM
    
def testGMM(trainedGMM, featureData, useMajorityVote=True, scale=True, showPlots=False):
    """

    @param trainedGMM: already trained GMM
    @param featureData: Numpy array of already extracted features of the test file
    @param useMajorityVote: Set to False if you don't want to use majority vote. Default is True
    @param scale: Set to False if you do not want featureData to be scaled. Default is True
    @param showPlots:
    """
    n_classes = len(trainedGMM['clfs'])

    if scale==True:
        X_test = trainedGMM['scaler'].transform(featureData)
    else:
        X_test = featureData

    logLikelihood = np.zeros((n_classes, X_test.shape[0]))

    """ Compute log-probability for each class for all points: """
    for i in range(n_classes):

        # logLikelihood[i] = trainedGMM['clfs'][i].score(X_test) # uses scikit function
        logLikelihood[i] = logProb(X_test, trainedGMM['clfs'][i].weights_, trainedGMM['clfs'][i].means_, trainedGMM['clfs'][i].covars_) # uses logProb function defined below

    """ Select the class with the highest log-probability: """
    y_pred = np.argmax(logLikelihood, 0)

    if showPlots == True:

        likelihood = np.zeros((n_classes, X_test.shape[0]))
        likelihoodSorted = np.zeros((n_classes, X_test.shape[0]))

        tmpProduct = np.zeros((n_classes, X_test.shape[0]))
        entropy = np.zeros((X_test.shape[0]))

        percentDiff = np.zeros((X_test.shape[0])) #Percentage-difference between two most likely classes

        margin = np.zeros((X_test.shape[0])) #Difference between two most likely classes

        likelihood = np.exp(logLikelihood)

        tmpSum = np.array(likelihood.sum(axis=0), dtype=np.float64)
        likelihoodNormed = likelihood/tmpSum

        logLikelihoodNormed = np.log(likelihoodNormed)

        for i in range(n_classes):
            tmpProduct[i,:] = likelihoodNormed[i,:] * logLikelihoodNormed[i,:]
            #tmpProduct[i,:] = likelihood[i,:] * logLikelihood[i,:]

        entropy = tmpProduct.sum(axis=0) * -1

        likelihoodSorted = np.sort(likelihoodNormed, axis=0)
        #likelihoodSorted = np.sort(likelihood, axis=0)

        percentDiff = (likelihoodSorted[-1,:] - likelihoodSorted[-2,:]) / likelihoodSorted[-1,:]

        margin = (likelihoodSorted[-1,:] - likelihoodSorted[-2,:])

        timestamps = np.array(range(X_test.shape[0])) * 0.032

        """ calculate mean entropy value of the last k samples: """
        k = 300 # equals approx. 10sec
        entropyMean = np.zeros((X_test.shape[0]))
        for i in range(X_test.shape[0]):
            if i < k:
                entropyMean[i] = -1
            else:
                entropyMean[i] = entropy[i-k:i].mean()


        """ Calculate threshold values that that divides array with all query criteria value (e.g. entropy) Top x percent: """
        topK = 10
        for key in trainedGMM["classesDict"]:
            tmp = entropy[y_pred == trainedGMM["classesDict"][key]]
            sorted = np.sort(tmp)
            threshold = sorted[-topK]
            print("Entropy threshold for class " + str(key) + " is " + str(round(threshold,6)))

        """ Plot histogram(s): """
        variableToPlot = entropy

        for key in trainedGMM["classesDict"]:
            pl.hist(variableToPlot[y_pred == trainedGMM["classesDict"][key]], 500, histtype='bar') # range=[0.0, 1.0]
            pl.title("Entropy - " + str(key))
            pl.show()


        """ Plot values over time: """

        f, ax = pl.subplots(2, sharex=True)

        ax[0].plot(timestamps,majorityVote(y_pred), 'r+')
        ax[0].text(0, 0.5, str(trainedGMM["classesDict"]))
        ax[0].set_title("Predicted classes")

        ax[1].plot(timestamps, margin, 'bo')
        ax[1].set_title("Entropy")
        pl.xlabel('time (s)')

        pl.show()


    if useMajorityVote:
        return majorityVote(y_pred)
    else:
        return y_pred

def predictGMM(trainedGMM, featureData, scale=True, returnEntropy=False):
    """
    Always use majority vote and return the mean entropy of the 2second interval
    @param trainedGMM: already trained GMM
    @param featureData: Numpy array of features of the points that should be tested
    @param scale: Set to False if you do not want featureData to be scaled. Default is True
    @param returnEntropy: Set to True if you want to return the mean value of the entropy
    """
    n_classes = len(trainedGMM['clfs'])

    if scale==True:
        X_test = trainedGMM['scaler'].transform(featureData)
    else:
        X_test = featureData

    logLikelihood = np.zeros((n_classes, X_test.shape[0]))

    """ Compute log-probability for each class for all points: """
    for i in range(n_classes):

        # logLikelihood[i] = trainedGMM['clfs'][i].score(X_test) # uses scikit function
        logLikelihood[i] = logProb(X_test, trainedGMM['clfs'][i].weights_, 
        trainedGMM['clfs'][i].means_, trainedGMM['clfs'][i].covars_) # uses logProb function defined below

    """ Select the class with the highest log-probability: """
    y_pred = np.argmax(logLikelihood, 0) # =predictions in Java

    """ Calculate the entropy for each point """
    likelihood = np.zeros((n_classes, X_test.shape[0]))
    likelihoodSorted = np.zeros((n_classes, X_test.shape[0]))

    tmpProduct = np.zeros((n_classes, X_test.shape[0]))
    entropy = np.zeros((X_test.shape[0]))

    likelihood = np.exp(logLikelihood)

    tmpSum = np.array(likelihood.sum(axis=0), dtype=np.float64)
    likelihoodNormed = likelihood/tmpSum

    logLikelihoodNormed = np.log(likelihoodNormed)

    for i in range(n_classes):
        tmpProduct[i,:] = likelihoodNormed[i,:] * logLikelihoodNormed[i,:]
    
    entropy = tmpProduct.sum(axis=0) * -1

    """ Calculate the mean entropy for the whole (2sec) window """
    entropyMean = entropy.mean()

    if returnEntropy == True:
        return majorityVote(y_pred, returnDisagreement=False), entropyMean
    else:
        return majorityVote(y_pred, returnDisagreement=False)

def logProb(X, weights, means, covars, return_component_matrix=False):
    """
    Calculate the log probability of multiple points under a GMM represented by the weights, means, covars parameters

    @param X: Numpy array representing the input data. Each row refers to one point
    @param weights: Component weights
    @param means: Means
    @param covars: Full covariance matrix of the mixture
    @param return_component_matrix: Set to true to return a (1 x n_samples) boolean matrix, indicating
    if most likely component for every point was from user (True) or from Freesound (False)
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
        # loops through each component in means and covars, i.e. cv has shape (12,12) and mu has shape (12,)

        try:
            cv_chol = linalg.cholesky(cv, lower=True) # = L0 in Java
        except linalg.LinAlgError:
            # reinitialize component, because it might be stuck with too few observations
            print("LinAlgError")
            cv_chol = linalg.cholesky(cv + min_covar * np.eye(n_features),lower=True)

        cv_log_det = 2 * np.sum(np.log(np.diagonal(cv_chol)))

        cv_sol = linalg.solve_triangular(cv_chol, (X - mu).T, lower=True).T # = solved in Java

        log_prob[:, c] = - .5 * (np.sum(cv_sol ** 2, axis=1) + n_features * np.log(2 * np.pi) + cv_log_det) #=rowSum in Java

    tmp_log_prob = (log_prob + np.log(weights))

    # compute sum in log domain:
    tmpArray = np.rollaxis(tmp_log_prob, axis=1) # transpose
    vmax = tmpArray.max(axis=0)

    if return_component_matrix == True:
        #If we want to check which components is the most likely one: -> evaluate this for every class:
        # To check if most likely component was from freesound model or user adaption:
        FS_COMPONENTS = 16
        mostLikelyComp = tmpArray.argmax(axis=0)
        #num_freesound_components = (mostLikelyComp < FS_COMPONENTS).sum()
        #num_user_components = (mostLikelyComp >= FS_COMPONENTS).sum()

        #print("num_freesound_components: " + str(num_freesound_components))
        #print("num_user_components: " + str(num_user_components))
        
        # Matrix indicating for every point, if most likely component was from user-centric
        # model (True) or from Freesound model (False)
        user_component_matrix = (mostLikelyComp >= FS_COMPONENTS)

    final_log_prob = np.log(np.sum(np.exp(tmpArray - vmax), axis=0))

    final_log_prob = final_log_prob + vmax # shape = (n_samples,)
    
    if return_component_matrix == True:
        return final_log_prob, user_component_matrix
    else:
        return final_log_prob

def compareGTMulti(trainedGMM, featureData=None, groundTruthLabels='labels.txt', useMajorityVote=True):
    """

    @param trainedGMM:
    @param featureData: Numpy array of already extracted features of the test file
    @param groundTruthLabels:
    @param useMajorityVote:
    @return:
    """
    """ Make predictions for the given test file: """
    y_pred = testGMM(trainedGMM,featureData, useMajorityVote=useMajorityVote, showPlots=False)

    """ Preprocess ground truth labels: """
    with open(groundTruthLabels) as f:
        reader = csv.reader(f, delimiter="\t")
        labelList = list(reader) #1st column = start time, 2nd column = end time, 3rd column = class label (string)

    """ Create array containing label for sample point: """
    n_maxLabels = 3 #maximum number of labels that can be assign to one point
    y_GT = np.empty([y_pred.shape[0],n_maxLabels])
    y_GT.fill(-1) #-1 corresponds to no label given

    y_GT = createGTMulti(trainedGMM["classesDict"], y_pred.shape[0], 'labels.txt')

    n_classes = len(trainedGMM["classesDict"])
    agreementCounter = 0
    validCounter = 0.0
    delIdx = [] #list of indexes of the rows that should be deleted
    correctlyPredicted = [0] * n_classes #list to count how often each class is predicted correctly
    for j in range(y_pred.shape[0]):

        if y_pred[j] in y_GT[j,:]:
            # We don't have to consider invalid (=-1) entries, because 
            # y_pred never contains -1, so we will never count them
            correctlyPredicted[int(y_pred[j])] = correctlyPredicted[int(y_pred[j])] + 1 #count correctly predicted for the individual class

        if y_GT[j,:].sum() != -3:
            # Ignore points were no GT label provided and ignore points 
            # of classes we didn't train our classifier with:
            validCounter = validCounter + 1
        else:
            delIdx.append(j)

    agreement = 100 * sum(correctlyPredicted)/validCounter
    print(str(round(agreement,2)) + " % of all valid samples predicted correctly")

    notConsidered = 100*(y_pred.shape[0]-validCounter)/float(y_pred.shape[0])
    print(str(round(notConsidered,2)) + "% of all entries were not evaluated, because no label was provided,"
                                                                " or the classifier wasn't trained with all classes specified in the ground truth")


    """ Delete invalid entries in y_GT and y_pred: """
    y_pred = np.delete(y_pred,delIdx)
    y_GT = np.delete(y_GT,delIdx,axis=0)

    resDict = {'predictions': y_pred, 'groundTruth': y_GT}

    print(trainedGMM["classesDict"])

    confusionMatrixMulti(y_GT, y_pred, trainedGMM["classesDict"])

    return resDict

def confusionMatrixMulti(y_GT, y_pred, classesDict, ssh=False):
    """

    @param y_GT: Ground truth array that can contain multiple labels for each data point
    @param y_pred:
    @param classesDict:i
    @param ssh: If True the plot will only be saved in the plotsTmp folder. If False, the
    plot will be displayed
    """

    """ Sort classesDict to show labels in the CM: """
    sortedTmp = sorted(classesDict.iteritems(), key=operator.itemgetter(1))
    sortedLabels = []
    
    for j in range(len(sortedTmp)):
        sortedLabels.append(sortedTmp[j][0])

    n_classes = len(classesDict)

    cm = np.zeros((n_classes,n_classes))

    for i in range(y_pred.shape[0]):
        # Only count points where prediction value is valid:
        if int(y_pred[i]) != -1:
            
            if y_pred[i] in y_GT[i,:]:
                """ If correct prediction made, add one on the corresponding diagonal element in the confusion matrix: """
                
                cm[int(y_pred[i]),int(y_pred[i])] += 1

            else:
                """ If not predicted correctly, divide by the number of ground truth labels for that point and split
                between corresponding non-diagonal elements: """
                gtLabels = y_GT[i,:]
                labels = gtLabels[gtLabels != -1] #ground truth labels assigned to that point (only valid ones)
                n_labels = len(labels) #number of valid labels assigned
                
                weight = 1/float(n_labels) #value that will be added to each assigned (incorrect) label

                for label in labels:
                    cm[int(label), int(y_pred[i])] += weight
                
    normalized = []
    for row in cm:
        rowSum = sum(row)
        normalized.append([round(x/float(rowSum),2) for x in row])

    """ Calculate precision: """
    colSum = np.sum(cm, axis=0)
    precisions = []
    for i in range(n_classes):
        tmpPrecision = cm[i,i] / float(colSum[i])
        # print("Precision " + str(sortedLabels[i]) + ": " + str(tmpPrecision))
        precisions.append(tmpPrecision)

    """ Calculate recall: """
    recalls = []
    for i in range(n_classes):
        recalls.append(normalized[i][i])
        # print("Recall " + str(sortedLabels[i]) + ": " + str(normalized[i][i]))

    """ Calculate F1-score: """
    F1s = []
    for i in range(n_classes):
        tmpF1 = 2 * (precisions[i] * recalls[i]) / float(precisions[i] + recalls[i])
        print("F1 " + str(sortedLabels[i]) + ": " + str(tmpF1))
        F1s.append(tmpF1)

    width = len(cm)
    height = len(cm[0])

    pl.figure(figsize=(15,15))
    pl.matshow(normalized)
    pl.ylabel('True label')
    pl.xlabel('Predicted label')
    pl.xticks(range(len(sortedLabels)), sortedLabels)
    pl.yticks(range(len(sortedLabels)), sortedLabels)

    for x in xrange(width):
        for y in xrange(height):
            pl.annotate(str(normalized[x][y]), xy=(y, x),
                        horizontalalignment='center',
                        verticalalignment='center')

    # Force the min and max value, so that the colors will have the full range from 0 to 1:
    pl.clim(0,1)

    pl.colorbar()
    if ssh == False:
        pl.show()
    else:
        # When saving to disk, don't overwrite previous files, but instead
        # create new file with increased index:
        # "plotsTmp/CM_0.jpg"
        filename_beginning = "plotsTmp/CM_"
        cnt = 0
        filename_extension = ".jpg"
        filename = filename_beginning + str(cnt) + filename_extension 
        while os.path.isfile(filename):
            cnt += 1
            filename = filename_beginning + str(cnt) + filename_extension
        
        fig = matplotlib.pyplot.gcf()
        fig.set_size_inches(15,15)
        pl.savefig(filename)

def createGTMulti(classesDict, length, groundTruthLabels='labels.txt'):
    
    """
    Create ground truth array that allows multiple labels per point
    @param classesDict:
    @param length:
    @param groundTruthLabels:
    @return:
    """

    """ Preprocess ground truth labels: """
    with open(groundTruthLabels) as f:
        reader = csv.reader(f, delimiter="\t")
        labelList = list(reader)

    """ Create array containing label for sample point: """
    n_maxLabels = 3 #maximum number of labels that can be assign to one point
    y_GT = np.empty([length,n_maxLabels])
    y_GT.fill(-1) #-1 corresponds to no label given

    classesNotTrained = []
    for i in range(len(labelList)):
        """ Fill array from start to end of each ground truth label with the correct label: """
        if labelList[i][2] == "start":
            tmpContext = labelList[i][1]
            start = getIndex(float(labelList[i][0]))

            # Find the end time of this context:
            for j in range(i,len(labelList)):
                if ((labelList[j][1] == tmpContext) and (labelList[j][2] == "end")):

                    end = getIndex(float(labelList[j][0]))
                    if end >= y_GT.shape[0]:
                        end = y_GT.shape[0] - 1

                    """ Fill ground truth array, and check if our classifier was 
                    trained with all labels of the test file, if not give warning: """

                    if (labelList[i][1] not in classesDict.keys()):
                        classesNotTrained.append(labelList[i][1])
                    
                    else:
                        
                        # Check if we can write into the first column of the y_GT array:
                        if ((len(np.unique(y_GT[start:end+1,0])) == 1) and 
                        (np.unique(y_GT[start:end+1,0])[0] == -1)):

                            y_GT[start:end+1,0].fill(classesDict[labelList[i][1]])

                        # Check if we can write into the second column of the y_GT array:
                        elif ((len(np.unique(y_GT[start:end+1,1])) == 1) and 
                        (np.unique(y_GT[start:end+1,1])[0] == -1)):

                            y_GT[start:end+1,1].fill(classesDict[labelList[i][1]])
                       
                        # Check if we can write into the third column of the y_GT array:
                        elif ((len(np.unique(y_GT[start:end+1,2])) == 1) and 
                        (np.unique(y_GT[start:end+1,2])[0] == -1)):

                            y_GT[start:end+1,2].fill(classesDict[labelList[i][1]])
                        
                        else:
                            print("Problem occurred when filling ground truth array." +  
                            "Maybe you are using more than 3 simultaneous context classes?")
                    break
    
    if classesNotTrained:
        for el in set(classesNotTrained):
            print("The classifier wasn't trained with class '" + 
            el + "'. It will not be considered for testing.")
    return y_GT

def getIndex(timeStamp, windowLength=0.032):
    """
    Calculates index in the feature array of a given time given no overlapping of frames. Will return the beginning
    of the frame
    @param timeStamp: Position in the input file in seconds
    @param windowLength: Length of the window in seconds. Default is 0.032
    @return: Index of the window, to which the timestamp corresponds to as int
    """

    return int(timeStamp/windowLength)

def k_FoldGMM(featureData, k):
    """
    Trains a GMM classifier and calculates the accuracy of a random k-fold cross-validation on the freesound data iteself
    
    @param featureData: Dictionary containing 'features' and 'labels' as numpy array and 'classesDict' for mapping of class names to numbers
    @param k: Cross-validation parameter, defines in how many blocks the data should be divided
    """   
    X = featureData['features']
    scaler = preprocessing.StandardScaler().fit(X)
    X = scaler.transform(X)
    Y = featureData['labels']
    
    kf = KFold(len(Y), n_folds=k, indices=True, shuffle=True)
    
    for train, test in kf:
        X_train, X_test, y_train, y_test = X[train], X[test], Y[train], Y[test]

    n_classes = len(np.unique(y_train))
    print str(n_classes) + " different classes"

    clfs = []

    for i in range(n_classes):
        
        iTmp = (y_train == i)
        
#        """ Estimate number of components using BIC criteria: """
#        n_components_list = range(1,17)
#        bicList = []
#        for c in n_components_list:
#            tmpClf = GMM(n_components=c, covariance_type='full', n_iter=100)
#            tmpClf.fit(X_train[iTmp])
#            bicList.append(bic(X_train[iTmp], tmpClf.means_, tmpClf.covars_, tmpClf.weights_, c))
#
#            #print(str(c) + " components: " + " BIC = " + str(bicList[-1]))
#
#        # select that number of components that resulted in the best (lowest) BIC:
#        val, n_components = min((val, idx) for (idx, val) in enumerate(bicList))
#        n_components += 1
#        print("Optimal number of components :" + str(n_components))
        
        n_components = 16
        tmpClf = GMM(n_components = n_components, covariance_type='full', n_iter=100)
        
        tmpClf.fit(X_train[iTmp])
        clfs.append(tmpClf)
        
    likelihood = np.zeros((n_classes, X_test.shape[0]))
    
    """ Find class with highest likelihood: """ 
    for i in range(n_classes):
        likelihood[i] = clfs[i].score(X_test)
        
    y_pred = np.argmax(likelihood, 0)
    
    """ Compare predictions to ground truth: """
    agreementCounter = 0
    for j in range(y_test.shape[0]):
        if y_test[j] == y_pred[j]:
            agreementCounter += 1
 
    print str(100 * agreementCounter/y_test.shape[0]) + " % of all samples predicted correctly (without majority vote...)"

    """ Sort classesDict to show labels in the CM: """
    sortedTmp = sorted(featureData["classesDict"].iteritems(), key=operator.itemgetter(1))
    sortedLabels = []
    for j in range(len(sortedTmp)):
        sortedLabels.append(sortedTmp[j][0])

    cm = confusion_matrix(y_test, y_pred)

    print(cm)

    normalized = []

    for row in cm:
        rowSum = sum(row)
        normalized.append([round(x/float(rowSum),2) for x in row])

    width = len(cm)
    height = len(cm[0])

    pl.matshow(normalized)
    pl.ylabel('True label')
    pl.xlabel('Predicted label')
    pl.xticks(range(len(sortedLabels)), sortedLabels)
    pl.yticks(range(len(sortedLabels)), sortedLabels)

    for x in xrange(width):
        for y in xrange(height):
            pl.annotate(str(normalized[x][y]), xy=(y, x),
                        horizontalalignment='center',
                        verticalalignment='center')
    
    # Force the min and max value, so that the colors will have the full range from 0 to 1:
    pl.clim(0,1)

    pl.colorbar()

    pl.show()

