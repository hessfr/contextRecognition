import numpy as np
import pickle
import csv
import pylab as pl
from classifiers import getIndex
import ipdb as pdb #pdb.set_trace()

tGMM = pickle.load(open("GMM_funf.p","rb"))
realWorldFeatures = pickle.load(open("realWorldFeatures.p","rb"))

def simulateAL(trainedGMM, featureData, groundTruthLabels='labelsAdapted.txt'):
    """

    @param trainedGMM: already trained GMM
    @param featureData: Numpy array of already extracted features of the test file
    @param groundTruthLabels:
    """
    y_GT = createGT(trainedGMM['classesDict'], featureData.shape[0], groundTruthLabels)

    """ Create index arrays to define which elements are used to evaluate performance and which for simulation of the AL
    behavior: """
    [evalIdx, simIdx] = splitData(y_GT)

    evalFeatures = featureData[evalIdx == 1]
    evalLabels = y_GT[evalIdx == 1]

    simFeatures = featureData[simIdx == 1]

    currentGMM = dict(trainedGMM)

    allGMM = []
    allGMM.append(currentGMM)

    """ Simulate actual behavior by reading in points one by one: """
    for i in range(simFeatures.shape[0]):
        if queryCriteria(currentGMM, simFeatures[i,:]):
            currentGMM = adaptGMM(currentGMM, simFeatures[i,:])
            allGMM.append(currentGMM)

    """ Evaluate performance of all GMMs: """
    for GMM in allGMM:
        evaluateGMM(GMM)

def queryCriteria(trainedGMM, featurePoint, criteria="entropy"):
    """

    @param trainedGMM:
    @param featurePoint:
    @return: True is query should be send, False if not
    """
    point = np.reshape(featurePoint, (1,12)) #reshape into 2D array that it is compatible with all methods
    n_classes = len(trainedGMM['clfs'])
    logLikelihood = np.zeros(n_classes)
    """ Compute log-probability for each class for all points: """
    for i in range(n_classes):
        logLikelihood[i] = trainedGMM['clfs'][i].score(point)
    likelihood = np.exp(logLikelihood)

    """ Select the class with the highest log-probability: """
    predictedClass = np.argmax(logLikelihood, 0)

    if criteria == "entropy":
        threshold = 1e-6
        tmpProduct = np.zeros(n_classes)
        for i in range(n_classes):
            tmpProduct[i] = likelihood[i] * logLikelihood[i]
        entropy = tmpProduct.sum(axis=0) * -1

        if entropy >= threshold:
            return True
        else:
            return False


#def entropy(featurePoint):
    """

    @param featurePoint:
    @return: Entropy of the feature point
    """

def adaptGMM(trainedGMM, featurePoint, label):
    """

    @param trainedGMM:
    @param featurePoint:
    param label: Class label of the given feature point.
    @return: adapted GMM model
    """
    pass

def evaluateGMM(trainedGMM):
    """

    @param trainedGMM:
    @return:
    """
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










def createGT(classesDict, length, groundTruthLabels):
    """

    @param classesDict: already trained GMM
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






from simulateAL import *

































