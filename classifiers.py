import os
from os import listdir
import numpy as np
from sklearn.svm import SVC
from sklearn.multiclass import OneVsRestClassifier
from sklearn.svm import LinearSVC
from sklearn.mixture import GMM
from sklearn import cross_validation
from featureExtraction import FX_Test
from featureExtraction import FX_Folder
import math
from sklearn.cross_validation import KFold
import ipdb as pdb #pdb.set_trace()

def majorityVote(y_raw):
    """
    Apply a "majority vote" of fixed window length to a sequence
    @param y_raw: Input data as 1D numpy array
    @return: Result of the same size as input
    """
    print "inputSize: " + str(y_raw.shape[0])
    #TODO: implement properly with parameters:
    window_length = 2.0 #in seconds
    step_size = 0.016
    frameLength = math.ceil(window_length/step_size)
    print "frameLength: " + str(frameLength)
    
    resArray = np.empty(y_raw.shape)

    n_frames = int(math.ceil(y_raw.shape[0]/frameLength))
    print "Number of Frames: " + str(n_frames)
    
    for i in range(n_frames):
        if ((i+1) * n_frames) < y_raw.shape[0]:
            tmpArray = y_raw[(i * frameLength):(((i+1) * frameLength))] #y_raw[(i * frameLength):(((i+1) * frameLength) - 1)]
            
            """ Get most frequent number in that frames and fill all elements in the frame with it: """
            count = np.bincount(tmpArray)
            tmpMostFrequent = np.argmax(count)
            tmpArray.fill(tmpMostFrequent)
            """ Write it into our result array: """
            resArray[(i * frameLength):(((i+1) * frameLength))] = tmpArray
    
    return resArray

def trainGMM(featureData):
    """
    Build a Gaussian Mixture Model on the given data using Scikit-Learn.
    To access attributes of the GMMs use .weights_, .means_ and .covars_
    @param featureData: Dictionary containing 'features' and 'labels' as numpy array and 'classesDict' for mapping of class names to numbers
    @return: Dictionary containing trained scikit-learn GMM classifiers in 'clfs' and 'classesDict' for mapping of class names to numbers
    """
    X_train = featureData['features']
    y_train = featureData['labels']
    
    n_classes = len(np.unique(y_train))
    
    print str(n_classes) + " different classes"
    
    clfs = []
 
    for i in range(n_classes):
        tmpClf = GMM(n_components = 16)
        iTmp = (y_train[:,0] == i)
        """ use expecation-maximization to fit the Gaussians: """ 
        t = X_train[iTmp]        
        tmpClf.fit(X_train[iTmp])
        clfs.append(tmpClf)
        
    trainedGMM = {'clfs': clfs, 'classesDict': featureData['classesDict']}

    """ trainedGMM contains:
    @param clfs: Array of classifiers, containing one classifier for each class
    @param classesDict: Dict containing mapping of class names to numbers
    """
    
    return trainedGMM

def incrTrainGMM(prevTrainedGMM, newClassName, newClassData=None):
    """
    Incorporate a new class into an existing GMM model
    @param prevTrainedGMM: Dictionary containing results of previously trained GMM. Must contain classifiers in 'clfs' and mapping of
    class names to numbers in 'classesDict' 
    @param newClassName: Name of the new class. Has to match the name of the folder where the sound files are located
    @param newClassData: Already extracted MFCC Features for the given newClassName. If not provided, a feature extraction will be performed
    @return:  Dictionary containing trained scikit-learn GMM classifiers in 'clfs' and 'classesDict' for mapping of class names to numbers
    """
    
    if newClassData == None:
        newClassData = FX_Folder(newClassName)
    
    X_train = newClassData

    n_newClass = X_train.shape[0]

    newClf = GMM(n_components = 16)
    newClf.fit(X_train)
    
    """ Update the dict containing mapping of class names: """
    newClassDict = dict(prevTrainedGMM['classesDict'])
    newClassDict[newClassName] = len(newClassDict.values())
    
    allClfs = list(prevTrainedGMM['clfs'])
    allClfs.append(newClf)

    """ create new dictionary: """
    updatedGMM = {'clfs': allClfs, 'classesDict': newClassDict}
    
    return updatedGMM
    
def testGMM(clfs):
    """
    To check only
    @param clfs:
    """
    n_classes = len(clfs)
    
    X_test = FX_Test("test.wav")
    likelihood = np.zeros((n_classes, X_test.shape[0]))
    
    for i in range(n_classes):
        likelihood[i] = clfs[i].score(X_test)
    
    y_pred = np.argmax(likelihood, 0)
    
    return y_pred    

def k_FoldGMM(featureData,k):
    """
    Calculate random k-fold cross-validation using GMM
    @param featureData: Dictionary containing 'features' and 'labels' as numpy array and 'classesDict' for mapping of class names to numbers
    @param k: Cross-validation parameter, defines in how many blocks the data should be divided
    """   
    X = featureData['features']
    Y = featureData['labels']
    
    kf = KFold(len(Y), n_folds=k, indices=True, shuffle=True)
    
    for train, test in kf:
        X_train, X_test, y_train, y_test = X[train], X[test], Y[train], Y[test]

    n_classes = len(np.unique(y_train))
    print str(n_classes) + " different classes"
    
    clfs = []

    for i in range(n_classes):
        tmpClf = GMM(n_components = 16)
        iTmp = (y_train[:,0] == i)
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
            agreementCounter = agreementCounter + 1
    
    print str(100 * agreementCounter/y_test.shape[0]) + " % of all samples predicted correctly (without majority vote...)"
    
def SVM(featureData):
    """
    Build a multi-class support vector machine on the given data using Scikit-Learn
    @param data: Input data containing 12 MFCC features in the first 12 columns and the class label in the last column
    @return: Scikit-Learn SVM classifier
    """
    X_train = featureData['features']
    y_train = featureData['labels']
    
#     clf = SVC(kernel='linear')
    clf = LinearSVC() #OneVsRestClassifier(LinearSVC())
    
    clf.fit(X_train, y_train)
    
    return clf

def randomSplitSVM(featureData):
    """
    Build a Support Vector Machine on the given data and evaluate the performance by randomly splitting data into train and test
    and compare results to ground truth.
    @param data: Input data containing 12 MFCC features in the first 13 columns and the class label in the last column
    @return: Scikit-Learn SVM classifier
    """
    X_train, X_test, y_train, y_test = cross_validation.train_test_split(featureData['features'], featureData['labels'], test_size=0.33, random_state=43)
     
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

from classifiers import *


