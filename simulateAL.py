import numpy as np
import pickle
import json
import csv
import copy
import operator
import pylab as pl
from scipy.stats import itemfreq
from sklearn.mixture import GMM
from sklearn import preprocessing
from classifiers import getIndex
from classifiers import testGMM
from featureExtraction import FX_multiFolders
from adaptGMM import adaptGMM
import ipdb as pdb #pdb.set_trace()

_thresholdDict = {}

#tGMM = pickle.load(open("GMM.p","rb"))
#realWorldFeatures = np.array(json.load(open("realWorldFeatures.json","rb")))
#updatePoints = pickle.load(open("updatePoints_label1.p","rb"))

def defineThresholdDict():

    _thresholdDict["Conversation"] = {}
    _thresholdDict["Office"] = {}
    _thresholdDict["TrainInside"] = {}


    #values for top 10 threshold for each class:
    _thresholdDict["Conversation"]["entropy"] = 1.097963
    _thresholdDict["Conversation"]["margin"] = 1.2e-05
    _thresholdDict["Conversation"]["percentDiff"] = 2.8e-05

    _thresholdDict["Office"]["entropy"] = 1.097941
    _thresholdDict["Office"]["margin"] = 4.5e-05
    _thresholdDict["Office"]["percentDiff"] = 0.000108

    _thresholdDict["TrainInside"]["entropy"] = 1.098202
    _thresholdDict["TrainInside"]["margin"] = 1.9e-05
    _thresholdDict["TrainInside"]["percentDiff"] = 4e-05

    # #values for top 20 threshold for each class:
    # _thresholdDict["Conversation"]["entropy"] = 1.097408
    # _thresholdDict["Conversation"]["margin"] = 2.2e-05
    # _thresholdDict["Conversation"]["percentDiff"] = 4.9e-05
    #
    # _thresholdDict["Office"]["entropy"] = 1.097515
    # _thresholdDict["Office"]["margin"] = 0.000152
    # _thresholdDict["Office"]["percentDiff"] = 0.000343
    #
    # _thresholdDict["TrainInside"]["entropy"] = 1.097816
    # _thresholdDict["TrainInside"]["margin"] = 4.3e-05
    # _thresholdDict["TrainInside"]["percentDiff"] = 9.4e-05


def simulateAL(trainedGMM, testFeatureData):
    """

    @param trainedGMM: already trained GMM
    @param testFeatureData: Numpy array of already extracted features of the test file
    """
    y_GT = createGTUnique(trainedGMM['classesDict'], testFeatureData.shape[0], 'labelsAdapted.txt')
    y_GTMulti = createGTMulti(trainedGMM["classesDict"],testFeatureData.shape[0], 'labels.txt')

    print(trainedGMM["classesDict"])

    """ Create index arrays to define which elements are used to evaluate performance and which for simulation of the AL
    behavior: """
    [evalIdx, simIdx] = splitData(y_GT)

    evalFeatures = testFeatureData[evalIdx == 1]
    evalLabels = y_GTMulti[evalIdx == 1] # contains multiple labels for each point

    simFeatures = testFeatureData[simIdx == 1]
    simFeatures = trainedGMM['scaler'].transform(simFeatures)
    simLabels = y_GT[simIdx == 1]

    """ simLabels contains unique label for each point that will be used to update the model later,
    e.g. if a point has the labels office and conversation, it will be trained with conversation (accoring to the provided
    groundTruthLabels """

    currentGMM = copy.deepcopy(trainedGMM)

    allGMM = []
    allGMM.append(currentGMM)
    givenLabels = []
    givenLabels.append(-1) #because first classifiers is without active learning yet
    labelAccuracy = []
    labelAccuracy.append(-1)
    timestamps = [] #time when the query was sent on the simulation data set -> only half the length of the complete data set
    timestamps.append(0)

    numQueriesPerClass = np.zeros(3)  #TODO: remove again later!!

    updatePointsList = []

    revClassesDict = reverseDict(trainedGMM["classesDict"])

    """ Simulate actual behavior by reading in points one by one: """
    defineThresholdDict()

    for i in range(468530, simFeatures.shape[0]): ###################################
        currentPoint = simFeatures[i,:]
        currentLabel = simLabels[i]

        if numQueriesPerClass[int(currentLabel)] <= 3: # to test what happens if we allow only certain number of queries per class #TODO: remove again later!!
            if queryCriteria(currentGMM, currentPoint, revClassesDict[currentLabel], criteria="entropy"):
                print("sending query for " + str(revClassesDict[currentLabel]))

                #set the current label for the last N points:
                N = 1875 # = 1 minute
                if i > N:
                    # updatePoints = simFeatures[i-N:i,:]
                    # labelAccuracy.append(checkLabelAccuracy(simLabels[i-N:i], currentLabel))

                    """ to test when choosing only samples with correct labels: """ #TODO: remove again later!!
                    tmpFeatures = simFeatures[i-N:i,:]
                    tmpLabels = simLabels[i-N:i]
                    updatePoints = tmpFeatures[tmpLabels == currentLabel]
                    labelAccuracy.append(1)

                else:
                    # updatePoints = simFeatures[0:i,:]
                    # labelAccuracy.append(checkLabelAccuracy(simLabels[0:i], currentLabel))

                    """ to test when choosing only samples with correct labels: """ #TODO: remove again later!!
                    tmpFeatures = simFeatures[0:i,:]
                    tmpLabels = simLabels[0:i]
                    updatePoints = tmpFeatures[tmpLabels == currentLabel]
                    labelAccuracy.append(1)

                """ to test what happens if we allow only certain number of queries per class: """  #TODO: remove again later!!
                numQueriesPerClass[int(currentLabel)] += 1

                updatePointsList.append(updatePoints)
                givenLabels.append(currentLabel)

                currentGMM = adaptGMM(currentGMM, updatePoints, currentLabel, nSteps=500)
                allGMM.append(currentGMM)
                timestamps.append(i*0.032)

                #y_pred = testGMM(currentGMM, evalFeatures, useMajorityVote=True, showPlots=False)

        #for testing:
        if len(allGMM) == 3:
            print("Stopped loop (for testing)")

            pdb.set_trace() # save updatePointsList, givenLabels and allGMM list

            break

    """ Evaluate performance of all GMMs: """
    print("Evaluating performance of classifiers:")
    results = []
    i=0
    for GMM in allGMM:
        resultDict = evaluateGMM(GMM, evalFeatures, evalLabels)
        resultDict["label"] = givenLabels[i]
        resultDict["labelAccuracy"] = labelAccuracy[i]
        resultDict["timestamp"] = timestamps[i]
        resultDict["classesDict"] = GMM["classesDict"]
        resultDict["duration"] = simFeatures.shape[0] * 0.032 #length in seconds
        results.append(resultDict)
        i += 1

    return results

def queryCriteria(trainedGMM, featurePoint, className, criteria="entropy"):
    """

    @param trainedGMM:
    @param featurePoint:
    @param className:
    @return: True is query should be send, False if not
    """
    point = np.reshape(featurePoint, (1,12)) #reshape into 2D array that it is compatible with all methods
    n_classes = len(trainedGMM['clfs'])
    logLikelihood = np.zeros(n_classes)
    """ Compute (log-)probability for each class for the current point: """
    for i in range(n_classes):
        logLikelihood[i] = trainedGMM['clfs'][i].score(point)

    likelihood = np.exp(logLikelihood)
    tmpSum = np.array(likelihood.sum(axis=0), dtype=np.float64)
    likelihoodNormed = likelihood/tmpSum
    logLikelihoodNormed = np.log(likelihoodNormed)

    """ Select the class with the highest log-probability: """
    predictedClass = np.argmax(logLikelihood, 0)

    if criteria == "entropy":

        threshold = _thresholdDict[className]["entropy"]
        tmpProduct = np.zeros(n_classes)
        for i in range(n_classes):
            tmpProduct[i] = likelihoodNormed[i] * logLikelihoodNormed[i]
        entropy = tmpProduct.sum(axis=0) * -1

        if entropy > threshold:
            return True
        else:
            return False

    if criteria == "margin":
        threshold = _thresholdDict[className]["margin"]
        likelihoodSorted = np.sort(likelihoodNormed, axis=0)
        margin = (likelihoodSorted[-1] - likelihoodSorted[-2])
        if margin < threshold:
            return True
        else:
            return False

    if criteria == "percentDiff":
        threshold = _thresholdDict[className]["percentDiff"]
        likelihoodSorted = np.sort(likelihoodNormed, axis=0)
        percentDiff = (likelihoodSorted[-1] - likelihoodSorted[-2]) / likelihoodSorted[-1]
        if percentDiff < threshold:
            return True
        else:
            return False

def checkLabelAccuracy(actualLabels, label):
    """
    Calculate how many percent of the samples that are used to adapt the model, are actually of the correct class
    @param actualLabels: Ground truth labels of all points with which the model will be adapted
    @param label: The label of the class with which the model should be adapted
    """
    accuracy = len(actualLabels[actualLabels == label]) / float(len(actualLabels))
    print(str(accuracy*100) + "% of the labels used to adapt the model were correct")

    return accuracy

def evaluateGMM(trainedGMM, evalFeatures, evalLabels):
    """

    @param trainedGMM:
    @param evalFeatures: not scaled, as scaling is done in testGMM method
    @param evalLabels: Ground truth label array with multiple labels per data points
    @return:
    """
    #TODO: move this method in classifiers.py and use it there as well to avoid redundancy!

    """ Calculate the predictions on the evaluation features: """
    y_pred = testGMM(trainedGMM, evalFeatures, useMajorityVote=True, showPlots=False)

    n_classes = len(trainedGMM["classesDict"])
    validCounter = 0
    delIdx = [] #list of indexes of the rows that should be deleted
    correctlyPredicted = [0] * n_classes #list to count how often each class is predicted correctly
    for j in range(y_pred.shape[0]):

        if y_pred[j] in evalLabels[j,:]:
            #We don't have to consider invalid (=-1) entries, because y_pred never contains -1, so we will never count them
            correctlyPredicted[int(y_pred[j])] = correctlyPredicted[int(y_pred[j])] + 1 #count correctly predicted for the individual class

        if evalLabels[j,:].sum() != -3:
            #Ignore points were no GT label provided and ignore points of classes we didn't train our classifier with:
            validCounter = validCounter + 1
        else:
            delIdx.append(j)

    agreement = 100 * sum(correctlyPredicted)/float(validCounter)
    print(str(round(agreement,2)) + " % of all valid samples predicted correctly")

    notConsidered = 100*(y_pred.shape[0]-validCounter)/float(y_pred.shape[0])
    if notConsidered != 0.0:
        print(str(round(notConsidered,2)) + "% of all entries were not evaluated, because no label was provided,"
                                                                " or the classifier wasn't trained with all classes specified in the ground truth")

    """ Delete invalid entries in evalLabels and y_pred: """
    y_pred = np.delete(y_pred,delIdx)
    evalLabels = np.delete(evalLabels,delIdx,axis=0)

    """ Calculate confusion matrix: """
    cm = np.zeros((n_classes,n_classes))

    for i in range(y_pred.shape[0]):
        if y_pred[i] in evalLabels[i,:]:
            """ If correct prediction made, add one on the corresponding diagonal element in the confusion matrix: """
            cm[int(y_pred[i]),int(y_pred[i])] += 1
        else:
            """ If not predicted correctly, divide by the number of ground truth labels for that point and split
            between corresponding non-diagonal elements: """
            gtLabels = evalLabels[i,:]
            labels = gtLabels[gtLabels != -1] #ground truth labels assigned to that point (only valid ones)
            n_labels = len(labels) #number of valid labels assigned
            weight = 1/float(n_labels) #value that will be added to each assigned (incorrect) label

            for label in labels:
                cm[int(label), int(y_pred[i])] += weight

    normCM = []

    for row in cm:
        rowSum = sum(row)
        normCM.append([round(x/float(rowSum),2) for x in row])

    """ Sort labels: """
    sortedTmp = sorted(trainedGMM["classesDict"].iteritems(), key=operator.itemgetter(1))
    sortedLabels = []
    for j in range(len(sortedTmp)):
        sortedLabels.append(sortedTmp[j][0])


    """ Calculate precision: """
    colSum = np.sum(cm, axis=0)
    precisions = []
    for i in range(n_classes):
        tmpPrecision = cm[i,i] / float(colSum[i])
        print("Precision " + str(sortedLabels[i]) + ": " + str(tmpPrecision))
        precisions.append(tmpPrecision)

    """ Calculate recall: """
    recalls = []
    for i in range(n_classes):
        recalls.append(normCM[i][i])
        print("Recall " + str(sortedLabels[i]) + ": " + str(normCM[i][i]))

    """ Calculate F1-score: """
    F1s = {}
    for i in range(n_classes):
        tmpF1 = 2 * (precisions[i] * recalls[i]) / float(precisions[i] + recalls[i])
        print("F1 " + str(sortedLabels[i]) + ": " + str(tmpF1))
        F1s[sortedLabels[i]] = tmpF1

    # """ Count how often each class was predicted: """
    # allPredicted = [0] * n_classes
    # items = itemfreq(y_pred)
    # for item in items:
    #     allPredicted[int(item[0])] = int(item[1])
    #
    # precisionsDict = {}
    #
    # for cl in trainedGMM["classesDict"]:
    #     clNum = trainedGMM["classesDict"][cl]
    #
    #     if allPredicted[clNum] != 0:
    #         precision = 100 * correctlyPredicted[clNum]/float(allPredicted[clNum])
    #         print("Class '" + cl + "' achieved a precision of " + str(round(precision,2)) + "%")
    #         precisionsDict[cl] = precision
    #     else:
    #         print("Class '" + cl + "' wasn't predicted at all")

    resDict = {"accuracy": agreement, "F1dict": F1s}

    return resDict

def splitData(y_GT):
    """
    Creates index arrays to define which elements are used to evaluate performance and which for simulation of the AL
    behavior
    @param y_GT: Numpy array containing the ground truth with one unique label per point
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

from simulateAL import *

































