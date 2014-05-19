import numpy as np
import pickle
import json
import csv
import pylab as pl
from sklearn.mixture import GMM
from sklearn import preprocessing
from classifiers import getIndex
from featureExtraction import FX_multiFolders
import ipdb as pdb #pdb.set_trace()

tGMM = pickle.load(open("GMM.p","rb"))
realWorldFeatures = np.array(json.load(open("realWorldFeatures.json","rb")))

def simulateAL(trainedGMM, featureData, groundTruthLabels='labelsAdapted.txt'):
    """

    @param trainedGMM: already trained GMM
    @param featureData: Numpy array of already extracted features of the test file
    @param groundTruthLabels:
    """
    y_GT = createGTUnique(trainedGMM['classesDict'], featureData.shape[0], groundTruthLabels)

    """ Create index arrays to define which elements are used to evaluate performance and which for simulation of the AL
    behavior: """
    [evalIdx, simIdx] = splitData(y_GT)

    evalFeatures = featureData[evalIdx == 1]
    evalLabels = y_GT[evalIdx == 1]

    simFeatures = featureData[simIdx == 1]
    simLabels = y_GT[simIdx == 1]

    currentGMM = dict(trainedGMM)

    allGMM = []
    allGMM.append(currentGMM)

    """ Simulate actual behavior by reading in points one by one: """
    for i in range(simFeatures.shape[0]):
        currentPoint = trainedGMM['scaler'].transform(simFeatures[i,:]) #apply the features scaling from training phase
        if queryCriteria(currentGMM, currentPoint):
            print("sending query")
            currentLabel = simLabels[i]
            #set the current label for the last N points = x seconds:
            N = 3000
            if i > N:
                updatePoints = simFeatures[i-N:i,:]
            else:
                updatePoints = simFeatures[0:i,:]

            currentGMM = adaptGMM(currentGMM, updatePoints, currentLabel)

            pdb.set_trace()

            #allGMM.append(currentGMM)

    """ Evaluate performance of all GMMs: """
    #for GMM in allGMM:
    #    evaluateGMM(GMM, evalFeatures, evalLabels)

def queryCriteria(trainedGMM, featurePoint, criteria="entropy"):
    """

    @param trainedGMM:
    @param featurePoint:
    @return: True is query should be send, False if not
    """
    point = np.reshape(featurePoint, (1,12)) #reshape into 2D array that it is compatible with all methods
    n_classes = len(trainedGMM['clfs'])
    logLikelihood = np.zeros(n_classes)
    """ Compute (log-)probability for each class for the current point: """
    for i in range(n_classes):
        logLikelihood[i] = trainedGMM['clfs'][i].score(point)
    likelihood = np.exp(logLikelihood)

    """ Select the class with the highest log-probability: """
    predictedClass = np.argmax(logLikelihood, 0)

    if criteria == "entropy":
        threshold = 0.6
        tmpProduct = np.zeros(n_classes)
        for i in range(n_classes):
            tmpProduct[i] = likelihood[i] * logLikelihood[i]
        entropy = tmpProduct.sum(axis=0) * -1

        if entropy > threshold:
            return True
        else:
            return False

def adaptGMM(trainedGMM, featurePoints, label):
    """
    Incorporate new data point into the GMM model
    @param trainedGMM: already scaled
    @param featurePoints:
    param label: Class label of the given feature point.
    @return: adapted GMM model
    """
    featureData = FX_multiFolders(["Conversation","Office","Train"]) #TODO: implement this properly
    scaled = trainedGMM['scaler'].transform(featureData["features"])

    y_new = np.zeros(featurePoints.shape[0])
    y_new.fill(label)

    """ Add the new data points: """


    X_train = np.concatenate((scaled, featurePoints), axis=0)
    y_train = np.concatenate((featureData["labels"], y_new), axis=0)

    clf = GMM(n_components=16)
    iTmp = (y_train == label)

    tmpTrain = X_train[iTmp]

    """ use expectation-maximization to fit the Gaussians: """
    clf.fit(tmpTrain)

    newGMM = dict(trainedGMM)
    newGMM["clfs"][int(label)] = clf

    pdb.set_trace()

    return newGMM

def evaluateGMM(trainedGMM, evalFeatures, evalLabels):
    """

    @param trainedGMM:
    @param evalFeatures:
    @param evalLabels:
    @return:
    """

    #TODO: call compareGTUnique / compareGTMulti here

    pass

def splitData(y_GT):
    """
    Creates index arrays to define which elements are used to evaluate performance and which for simulation of the AL
    behavior
    @param y_GT: Numpy
    @return:
    """
    prevTransition = 0 #index of the previous transition from one value to another

    evalIdx = np.empty(y_GT.shape[0])
    evalIdx.fill(False)
    simIdx = np.empty(y_GT.shape[0])
    simIdx.fill(False)

    prevEntry = y_GT[0]

    for i in range(y_GT.shape[0]):
        if y_GT[i] != prevEntry:
            if prevEntry == -1:
                """ If it is a invalid entry, don't save it, but continue in the loop: """
                prevEntry = y_GT[i]
                prevTransition = i
                #print("Sequence ending at frame " + str(i) + " not saved.")
            else:
                """ If the entry is valid: """
                split = int(prevTransition + (i - prevTransition)/2.0)
                evalIdx[prevTransition:split] = True
                simIdx[split:i] = True

                prevEntry = y_GT[i]
                prevTransition = i

    """ Fill last sequence: """
    i = y_GT.shape[0] - 1
    if y_GT[i] != -1:
            split = int(prevTransition + (i - prevTransition)/2.0)
            evalIdx[prevTransition:split] = True
            simIdx[split:i] = True
    #else:
        #print("Sequence ending at frame " + str(i) + " not saved.")

    return [evalIdx, simIdx]

def createGTUnique(classesDict, length, groundTruthLabels='labelsAdapted.txt'):
    """
    Create ground truth array were only one label is allowed per point
    @param classesDict:
    @param length:
    @param groundTruthLabels:
    @return:
    """
    #TODO: move this method in classifiers.py and use it there as well to avoid redundancy!

    """ Preprocess ground truth labels: """
    with open(groundTruthLabels) as f:
        reader = csv.reader(f, delimiter="\t")
        labelList = list(reader) #1st column = start time, 2nd column = end time, 3rd column = class label (string)

    """ Create array containing label for sample point: """
    y_GT = np.empty([length])
    y_GT.fill(-1) #-1 corresponds to no label given

    for line in labelList:
        """ Fill array from start to end of each ground truth label with the correct label: """
        start = getIndex(float(line[0]))
        end = getIndex(float(line[1])) #fill to the end of the frame

        if end >= y_GT.shape[0]:
            end = y_GT.shape[0] - 1 #TODO: add proper check here if values are feasible

        """ Check if our classifier was trained with all labels of the test file, if not give warning: """
        classesNotTrained = []
        if line[2] not in classesDict.keys():
            classesNotTrained.append(line[2])
            y_GT[start:end+1].fill(-1)
        else:
            y_GT[start:end+1].fill(classesDict[line[2]])

        if classesNotTrained:
            print("The classifier wasn't trained with class '" + line[2] + "'. It will not be considered for testing.")

    return y_GT

def createGTMulti(classesDict, length, groundTruthLabels='labels.txt'):
    """
    Create ground truth array that allows multiple labels per point
    @param classesDict:
    @param length:
    @param groundTruthLabels:
    @return:
    """
    #TODO: move this method in classifiers.py and use it there as well to avoid redundancy!

    """ Preprocess ground truth labels: """
    with open(groundTruthLabels) as f:
        reader = csv.reader(f, delimiter="\t")
        labelList = list(reader) #1st column = start time, 2nd column = end time, 3rd column = class label (string)

    """ Create array containing label for sample point: """
    n_maxLabels = 3 #maximum number of labels that can be assign to one point
    y_GT = np.empty([length,n_maxLabels])
    y_GT.fill(-1) #-1 corresponds to no label given

    for line in labelList:
        """ Fill array from start to end of each ground truth label with the correct label: """
        start = getIndex(float(line[0]))
        end = getIndex(float(line[1])) #fill to the end of the frame

        if end >= y_GT.shape[0]:
            end = y_GT.shape[0] - 1 #TODO: add proper check here if values are feasible

        """ Fill ground truth array, and check if our classifier was trained with all labels of the test file, if not give warning: """
        classesNotTrained = []
        if line[2] not in classesDict.keys():
            classesNotTrained.append(line[2])
        else:
            if (len(np.unique(y_GT[start:end+1,0])) == 1) and (np.unique(y_GT[start:end+1,0])[0] == -1):
                y_GT[start:end+1,0].fill(classesDict[line[2]])
            elif (len(np.unique(y_GT[start:end+1,1])) == 1) and (np.unique(y_GT[start:end+1,1])[0] == -1):
                y_GT[start:end+1,1].fill(classesDict[line[2]])
            elif (len(np.unique(y_GT[start:end+1,2])) == 1) and (np.unique(y_GT[start:end+1,2])[0] == -1):
                y_GT[start:end+1,2].fill(classesDict[line[2]])
            else:
                print("Problem occurred when filling ground truth array. Maybe you are using more than 3 simultaneous context classes?")

        if classesNotTrained:
            print("The classifier wasn't trained with class '" + line[2] + "'. It will not be considered for testing.")

    return y_GT

from simulateAL import *

































