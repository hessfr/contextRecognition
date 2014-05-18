import os
from os import listdir
import numpy as np
import csv
import pickle
from sklearn.svm import SVC
from sklearn.multiclass import OneVsRestClassifier
from sklearn.svm import LinearSVC
from sklearn.mixture import GMM
from sklearn import cross_validation
from sklearn import preprocessing
from sklearn.metrics import confusion_matrix
from sklearn.metrics import classification_report
from sklearn.cross_validation import KFold
import pylab as pl
from featureExtraction import FX_Test
from featureExtraction import FX_Folder
import math
import operator
from scipy.stats import itemfreq
import ipdb as pdb #pdb.set_trace()

def majorityVote(y_Raw):
    """
    Apply a "majority vote" of fixed window length to a sequence
    @param y_Raw: Input data as 1D numpy array
    @return: Result of the same size as input
    """
    y_raw = y_Raw.copy()
    #TODO: implement properly with parameters:
    majVotWindowLength = 2.0 #in seconds
    windowLength = 0.032
    frameLengthFloat = math.ceil(majVotWindowLength/windowLength)

    frameLength = int(frameLengthFloat)

    resArray = np.empty(y_raw.shape)

    n_frames = int(math.ceil(y_raw.shape[0]/frameLengthFloat))
    
    for i in range(n_frames):
        if ((i+1) * frameLength) < y_raw.shape[0]:
            tmpArray = y_raw[(i * frameLength):(((i+1) * frameLength))]
            
            """ Get most frequent number in that frames and fill all elements in the frame with it: """
            count = np.bincount(tmpArray)
            tmpMostFrequent = np.argmax(count)
            tmpArray.fill(tmpMostFrequent)
            """ Write it into our result array: """
            resArray[(i * frameLength):(((i+1) * frameLength))] = tmpArray
        else:
            tmpArray = y_raw[(i * frameLength):y_raw.shape[0]]
            """ Get most frequent number in that frames and fill all elements in the frame with it: """
            count = np.bincount(tmpArray)
            tmpMostFrequent = np.argmax(count)
            tmpArray.fill(tmpMostFrequent)
            """ Write it into our result array: """
            resArray[(i * frameLength):y_raw.shape[0]] = tmpArray

    return resArray

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
    
    print(str(n_classes) + " different classes")

    clfs = []
 
    for i in range(n_classes):
        tmpClf = GMM(n_components=16)
        iTmp = (y_train == i) #iTmp = (y_train[:,0] == i)

        tmpTrain = X_train[iTmp]

        """ use expectation-maximization to fit the Gaussians: """
        tmpClf.fit(tmpTrain)
        clfs.append(tmpClf)

    trainedGMM = {'clfs': clfs, 'classesDict': featureData['classesDict'], 'scaler': scaler}

    return trainedGMM
    
def testGMM(trainedGMM, featureData=None, useMajorityVote=True, showPlots=True):
    """

    @param trainedGMM: already trained GMM
    @param featureData: Numpy array of already extracted features of the test file
    @param useMajorityVote: Set to False if you don't want to use majority vote here. Default is True
    @param showPlots:
    """
    n_classes = len(trainedGMM['clfs'])

    if featureData==None:
        X_test = trainedGMM['scaler'].transform(FX_Test("test.wav"))
    else:
        X_test = trainedGMM['scaler'].transform(featureData)

    logLikelihood = np.zeros((n_classes, X_test.shape[0]))

    """ Compute log-probability for each class for all points: """
    for i in range(n_classes):
        logLikelihood[i] = trainedGMM['clfs'][i].score(X_test)

    """ Select the class with the highest log-probability: """
    y_pred = np.argmax(logLikelihood, 0)

    if showPlots == True:

        likelihood = np.zeros((n_classes, X_test.shape[0]))
        likelihoodSorted = np.zeros((n_classes, X_test.shape[0]))

        tmpProduct = np.zeros((n_classes, X_test.shape[0]))
        entropy = np.zeros((X_test.shape[0]))

        percentDiff = np.zeros((X_test.shape[0])) #Percentage-difference between two most likely classes

        margin = np.zeros((X_test.shape[0])) #Difference between two most likely classes

        maxMin = np.zeros((X_test.shape[0])) #Difference between most likely and least likely class

        likelihood = np.exp(logLikelihood)

        for i in range(n_classes):
            tmpProduct[i,:] = likelihood[i,:] * logLikelihood[i,:]

        entropy = tmpProduct.sum(axis=0) * -1

        likelihoodSorted = np.sort(likelihood, axis=0)

        percentDiff = (likelihoodSorted[-1,:] - likelihoodSorted[-2,:]) / likelihoodSorted[-1,:]

        margin = (likelihoodSorted[-1,:] - likelihoodSorted[-2,:])

        maxMin = likelihoodSorted[-1,:] - likelihoodSorted[0,:]

        timestamps = np.array(range(X_test.shape[0])) * 0.032

        #pl.hist(entropy, 500, histtype='bar')

        f, ax = pl.subplots(2, sharex=True)

        ax[0].plot(timestamps,majorityVote(y_pred), 'r+')
        ax[0].text(0, 0.5, str(trainedGMM["classesDict"]))
        ax[0].set_title("Predicted classes")

        ax[1].plot(timestamps, entropy, 'bo')
        #ax[1].set_title("Different between most likely and most unlikely class")
        #ax[1].set_title("Percentage difference between two most likely classes")
        ax[1].set_title("Entropy")
        #ax[1].set_title("Margin (between two most likely classes)")
        pl.xlabel('time (s)')

        pl.show()

        #pdb.set_trace()


    if useMajorityVote:
        y_majVote = majorityVote(y_pred)
        return y_majVote
    else:
        return y_pred


def compareGTMulti(trainedGMM, featureData=None, groundTruthLabels='labels.txt', useMajorityVote=True):
    """

    @param trainedGMM:
    @param featureData: Numpy array of already extracted features of the test file
    @param groundTruthLabels:
    @param useMajorityVote:
    @return:
    """
    """ Make predictions for the given test file: """
    y_pred = testGMM(trainedGMM,featureData, useMajorityVote=True)

    """ Preprocess ground truth labels: """
    with open(groundTruthLabels) as f:
        reader = csv.reader(f, delimiter="\t")
        labelList = list(reader) #1st column = start time, 2nd column = end time, 3rd column = class label (string)

    """ Create array containing label for sample point: """
    n_maxLabels = 3 #maximum number of labels that can be assign to one point
    y_GT = np.empty([y_pred.shape[0],n_maxLabels])
    y_GT.fill(-1) #-1 corresponds to no label given

    for line in labelList:
        """ Fill array from start to end of each ground truth label with the correct label: """
        start = getIndex(float(line[0]))
        end = getIndex(float(line[1])) #fill to the end of the frame

        if end >= y_GT.shape[0]:
            end = y_GT.shape[0] - 1 #TODO: add proper check here if values are feasible

        """ Fill ground truth array, and check if our classifier was trained with all labels of the test file, if not give warning: """
        classesNotTrained = []
        if line[2] not in trainedGMM['classesDict'].keys():
            classesNotTrained.append(line[2])
        else:
            if (len(np.unique(y_GT[start:end+1,0])) == 1) and (np.unique(y_GT[start:end+1,0])[0] == -1):
                y_GT[start:end+1,0].fill(trainedGMM['classesDict'][line[2]])
            elif (len(np.unique(y_GT[start:end+1,1])) == 1) and (np.unique(y_GT[start:end+1,1])[0] == -1):
                y_GT[start:end+1,1].fill(trainedGMM['classesDict'][line[2]])
            elif (len(np.unique(y_GT[start:end+1,2])) == 1) and (np.unique(y_GT[start:end+1,2])[0] == -1):
                y_GT[start:end+1,2].fill(trainedGMM['classesDict'][line[2]])
            else:
                print("Problem occurred when filling ground truth array. Maybe you are using more than 3 simultaneous context classes?")

        if classesNotTrained:
            print("The classifier wasn't trained with class '" + line[2] + "'. It will not be considered for testing.")

    n_classes = len(trainedGMM["classesDict"])
    agreementCounter = 0
    validCounter = 0
    delIdx = [] #list of indexes of the rows that should be deleted
    correctlyPredicted = [0] * n_classes #list to count how often each class is predicted correctly
    for j in range(y_pred.shape[0]):

        if y_pred[j] in y_GT[j,:]:
            #We don't have to consider invalid (=-1) entries, because y_pred never contains -1, so we will never count them
            correctlyPredicted[int(y_pred[j])] = correctlyPredicted[int(y_pred[j])] + 1 #count correctly predicted for the individual class

        if y_GT[j,:].sum() != -3:
            #Ignore points were no GT label provided and ignore points of classes we didn't train our classifier with:
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

    """ Count how often each class was predicted: """
    allPredicted = [0] * n_classes
    items = itemfreq(y_pred)
    for item in items:
        allPredicted[int(item[0])] = int(item[1])

    for cl in trainedGMM["classesDict"]:
        clNum = trainedGMM["classesDict"][cl]

        if allPredicted[clNum] != 0:
            precision = 100 * correctlyPredicted[clNum]/float(allPredicted[clNum])
            print("Class '" + cl + "' achieved a precision of " + str(round(precision,2)) + "%")
        else:
            print("Class '" + cl + "' wasn't predicted at all")

    resDict = {'predictions': y_pred, 'groundTruth': y_GT}

    print(trainedGMM["classesDict"])

    return resDict

def compareGTUnique(trainedGMM, featureData=None, groundTruthLabels='labelsAdapted.txt', useMajorityVote=True):
    """

    @param trainedGMM:
    @param featureData: Numpy array of already extracted features of the test file
    @param groundTruthLabels:
    @param useMajorityVote:
    @return:
    """
    """ Make predictions for the given test file: """
    y_pred = testGMM(trainedGMM,featureData, useMajorityVote=True, showPlots=False)

    """ Preprocess ground truth labels: """
    with open(groundTruthLabels) as f:
        reader = csv.reader(f, delimiter="\t")
        labelList = list(reader) #1st column = start time, 2nd column = end time, 3rd column = class label (string)

    """ Create array containing label for sample point: """
    y_GT = np.empty([y_pred.shape[0]])
    y_GT.fill(-1) #-1 corresponds to no label given

    for line in labelList:
        """ Fill array from start to end of each ground truth label with the correct label: """
        start = getIndex(float(line[0]))
        end = getIndex(float(line[1])) #fill to the end of the frame

        if end >= y_GT.shape[0]:
            end = y_GT.shape[0] - 1 #TODO: add proper check here if values are feasible

        """ Check if our classifier was trained with all labels of the test file, if not give warning: """
        classesNotTrained = []
        if line[2] not in trainedGMM['classesDict'].keys():
            classesNotTrained.append(line[2])
            y_GT[start:end+1].fill(-1)
        else:
            y_GT[start:end+1].fill(trainedGMM['classesDict'][line[2]])

        if classesNotTrained:
            print("The classifier wasn't trained with class '" + line[2] + "'. It will not be considered for testing.")


    """ Compare predictions to ground truth: """
    agreementCounter = 0
    for j in range(y_pred.shape[0]):
        if y_pred[j] == y_GT[j]:
            #We don't have to consider invalid (=-1) entries, because y_pred never contains -1, so we will never count
            #them
            agreementCounter = agreementCounter + 1

    #Ignore points were no GT label provided and ignore points of classes we didn't train our classifier with:
    validEntries = (y_GT != -1).sum() #Gives the number of valid points

    agreement = 100 * agreementCounter/validEntries
    print(str(round(agreement,2)) + " % of all samples predicted correctly")

    notConsidered = 100*(y_pred.shape[0]-validEntries)/float(y_pred.shape[0])
    print(str(round(notConsidered,2)) + "% of all entries were not evaluated, because no label was provided,"
                                                                " or the classifier wasn't trained with all classes specified in the ground truth")

    """ Delete invalid entries in y_GT and y_pred: """
    idx = np.where(y_GT == -1)[0] #get indexes of invalid entries

    y_pred = np.delete(y_pred,idx)
    y_GT = np.delete(y_GT,idx)

    resDict = {'predictions': y_pred, 'groundTruth': y_GT}

    print(trainedGMM["classesDict"])

    confusionMatrix(y_GT,y_pred, trainedGMM["classesDict"])

    return resDict

def confusionMatrix(y_GT, y_pred, classesDict):
    """

    @param y_GT:
    @param y_pred:
    @param classesDict:
    """

    """ Sort classesDict to show labels in the CM: """
    sortedTmp = sorted(classesDict.iteritems(), key=operator.itemgetter(1))
    sortedLabels = []
    for j in range(len(sortedTmp)):
        sortedLabels.append(sortedTmp[j][0])

    cm = confusion_matrix(y_GT, y_pred)

    print(cm)

    width = len(cm)
    height = len(cm[0])

    pl.matshow(cm)
    pl.ylabel('True label')
    pl.xlabel('Predicted label')
    pl.xticks(range(len(sortedLabels)), sortedLabels)
    pl.yticks(range(len(sortedLabels)), sortedLabels)

    for x in xrange(width):
        for y in xrange(height):
            pl.annotate(str(cm[x][y]), xy=(y, x),
                        horizontalalignment='center',
                        verticalalignment='center')

    # norm_conf = []
    # for i in cm:
    #     a = 0
    #     tmp_arr = []
    #     a = sum(i, 0)
    #     for j in i:
    #         tmp_arr.append(float(j)/float(a))
    #     norm_conf.append(tmp_arr)
    #
    # res = pl.imshow(np.array(norm_conf), cmap=pl.cm.jet,
    #             interpolation='nearest')
    #
    # pl.colorbar(cm)

    pl.colorbar()

    pl.show()


def getIndex(timeStamp, windowLength=0.032):
    """
    Calculates index in the feature array of a given time given no overlapping of frames. Will return the beginning
    of the frame
    @param timeStamp: Position in the input file in seconds
    @param windowLength: Length of the window in seconds. Default is 0.032
    @return: Index of the window, to which the timestamp corresponds to as int
    """

    return int(timeStamp/windowLength)

def k_FoldGMM(featureData,k):
    """
    Calculate random k-fold cross-validation using GMM
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
        tmpClf = GMM(n_components = 16)
        iTmp = (y_train == i) #iTmp = (y_train[:,0] == i)
        tmpClf.fit(X_train[iTmp])
        clfs.append(tmpClf)
        
    likelihood = np.zeros((n_classes, X_test.shape[0]))
    
    """ Find class with highest likelihood: """ 
    for i in range(n_classes):
        likelihood[i] = clfs[i].score(X_test)
        
    y_pred = \
        np.argmax(likelihood, 0)
    
    """ Compare predictions to ground truth: """
    agreementCounter = 0
    for j in range(y_test.shape[0]):
        if y_test[j] == y_pred[j]:
            agreementCounter = agreementCounter + 1
    
    print str(100 * agreementCounter/y_test.shape[0]) + " % of all samples predicted correctly (without majority vote...)"
    
def SVM(featureData):
    """
    Build a multi-class support vector machine on the given data using Scikit-Learn
    @param data: Input data containing 12 MFCC features in the first 12 columns and the class label in the last column
    @return: Scikit-Learn SVM classifier
    """
    X_train = featureData['features'] #preprocessing.scale(featureData['features'])
    scaler = preprocessing.StandardScaler().fit(X_train)
    X_train = scaler.transform(X_train)

    y_train = featureData['labels'][:,0]

#     clf = SVC(kernel='linear')
    clf = LinearSVC() #OneVsRestClassifier(LinearSVC())
    
    clf.fit(X_train, y_train)
    
    return clf

def testSVM(trainedSVM,featureData=None,useMajorityVote=True):
    """
    To check only
    @param trainedSVM:
    @param featureData: Numpy array of already extracted features of the test file
    @param useMajorityVote: Set to False if you don't want to use majority vote here. Default is True
    """
    if featureData==None:
        X_test = FX_Test("test.wav")
    else:
        X_test = featureData

    y_pred = trainedSVM.predict(X_test)

    if useMajorityVote:
        y_majVote = majorityVote(y_pred)
        return y_majVote
    else:
        return y_pred

def randomSplitSVM(featureData):
    """
    Build a Support Vector Machine on the given data and evaluate the performance by randomly splitting data into train and test
    and compare results to ground truth.
    @param data: Input data containing 12 MFCC features in the first 13 columns and the class label in the last column
    @return: Scikit-Learn SVM classifier
    """
    #TODO: change to using scaler:
    X_train, X_test, y_train, y_test = cross_validation.train_test_split(preprocessing.scale(featureData['features']), featureData['labels'], test_size=0.33, random_state=43)
     
    clf = LinearSVC()
 
    clf.fit(X_train, y_train)
     
    y_pred = clf.predict(X_test)
 
    """ Compare predictions to ground truth: """
    agreementCounter = 0
    for j in range(y_test.shape[0]):
        if y_test[j] == y_pred[j]:
            agreementCounter = agreementCounter + 1
     
    print str(100 * agreementCounter/y_test.shape[0]) + " % of all samples predicted correctly (without majority vote...)"
     
    return clf

def addNewClassGMM(prevTrainedGMM, newClassName, newClassData=None):
    """
    Incorporate a new class into an existing GMM model
    @param prevTrainedGMM: Dictionary containing results of previously trained GMM. Must contain classifiers in 'clfs' and mapping of
    class names to numbers in 'classesDict'
    @param newClassName: Name of the new class. Has to match the name of the folder where the sound files are located
    @param newClassData: Already extracted MFCC Features for the given newClassName. If not provided, a feature extraction will be performed
    @return: Dictionary containing trained scikit-learn GMM classifiers in 'clfs' and 'classesDict' for mapping of class names to numbers
    """

    if newClassData == None:
        newClassData = FX_Folder(newClassName)

    X_train = prevTrainedGMM['scaler'].transform(newClassData)

    n_newClass = X_train.shape[0]

    newClf = GMM(n_components = 16)
    newClf.fit(X_train)

    """ Update the dict containing mapping of class names: """
    newClassDict = dict(prevTrainedGMM['classesDict'])
    newClassDict[newClassName] = len(newClassDict.values())

    allClfs = list(prevTrainedGMM['clfs'])
    allClfs.append(newClf)

    #TODO: calculate new scaler and add it to result dictionary

    """ create new dictionary: """
    updatedGMM = {'clfs': allClfs, 'classesDict': newClassDict}

    return updatedGMM

from classifiers import *


