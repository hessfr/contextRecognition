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
from classifiers import predictGMM
from classifiers import majorityVote
from featureExtraction import FX_multiFolders
from adaptGMM import adaptGMM
import ipdb as pdb #pdb.set_trace()

_thresholdDict = {}

#tGMM = pickle.load(open("tGMM.p","rb"))
#realWorldFeatures = np.array(json.load(open("realWorldFeatures.json","rb")))

def meanAL(trainedGMM, testFeatureData):
    """
    Newest method
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
    
    revClassesDict = reverseDict(trainedGMM["classesDict"])

    """ Simulate actual behavior by reading in points in batches of size b: """
    b = 63 # equals 2 seconds

    predictedLabels = []

    currentTime = 0
    prevTime = -1000000.0

    # Booleans that indicate if the initial threshold was already set:
    initThresSet = []
    initThresSet.append(False)
    initThresSet.append(False)
    initThresSet.append(False)

    # Booleans that indicate if the initial threshold was already set:
    thresSet = []
    thresSet.append(False)
    thresSet.append(False)
    thresSet.append(False)

    # Booleans that indicate if any label was already provided by user for a class. To make sure that we
    # don't that the threshold too high for that class after the model was adapted
    feedbackReceived = []
    feedbackReceived.append(False)
    feedbackReceived.append(False)
    feedbackReceived.append(False)


    # Number of queries asked for each class:
    numQueries = [] # only for evaluation
    numQueries.append(0)
    numQueries.append(0)
    numQueries.append(0)

    # Thresholds for the different classes:
    threshold = []
    threshold.append(-1)
    threshold.append(-1)
    threshold.append(-1)

    updatePoints = []

    # ---- for plotting only ---
    plotValues = [] # only for evaluation
    plotValues.append([])
    plotValues.append([])
    plotValues.append([])
    plotThres = []
    plotThres.append([])
    plotThres.append([])
    plotThres.append([])
    plotBuffer = []
    plotBuffer.append([])
    plotBuffer.append([])
    plotBuffer.append([])

    # predicted labels in the last minute:
    predBuffer = []

    # Single buffer containing the last 30 points regardless of the predicted class
    queryBuffer = []

    # Our 3 min (?) buffer we use to initialized the threshold:
    initBuffer = []
    initBuffer.append([])
    initBuffer.append([])
    initBuffer.append([])

    # Our 10min buffer we use for threshold calculation after adapting the model:
    thresBuffer = []
    thresBuffer.append([])
    thresBuffer.append([])
    thresBuffer.append([])

    majCorrectCnt = 0 #only for evaluation
    majWrongCnt = 0 #only for evaluation
    
    thresQueriedInterval = []
    thresQueriedInterval.append(-1)
    thresQueriedInterval.append(-1)
    thresQueriedInterval.append(-1)

    # This loop loads new data every 2sec
    for i in range(simFeatures.shape[0]/b):
        start = i*b
        end = (i+1)*b
        currentPoints = simFeatures[start:end,:]
        actualLabel = int(simLabels[end]) # only the latest label is used to adapt the model, like in real-life
        currentTime = i*b*0.032

        # Buffer points of the 30 last 2 second intervals, as we used want to update our model with the last minute of data
        if len(updatePoints) < 1875:
            updatePoints.extend(currentPoints.tolist())
        else:
            updatePoints.extend(currentPoints.tolist())
            del updatePoints[0:b]

        resArray, entropy = predictGMM(currentGMM, currentPoints, scale=False, returnEntropy=True)
        predictedLabel = int(resArray.mean())
        predictedLabels.append(predictedLabel)

        # Buffer last 30 points for each class
        if len(queryBuffer) < 30:
            queryBuffer.append(entropy)
            predBuffer.append(predictedLabel)
        else:
            queryBuffer.append(entropy)
            del queryBuffer[0]
            predBuffer.append(predictedLabel)
            del predBuffer[0]

        # --- for plotting only:
        if len(plotBuffer[predictedLabel]) < 30: #TODO: xxxxxxxxxx
            # fill buffer:
            plotBuffer[predictedLabel].append(entropy)
        else:
            # buffer full:
            tmp = np.array(plotBuffer[predictedLabel])
            q = tmp.mean() + tmp.std() #TODO: xxxxxxxxxx
            plotValues[predictedLabel].append(q)
            plotThres[predictedLabel].append(threshold[predictedLabel])
            plotBuffer[predictedLabel] = []

        # --- Setting initial threshold: ---
        if (initThresSet[predictedLabel] == False):
                if len(initBuffer[predictedLabel]) < 90:
                    # Fill init threshold buffer
                    initBuffer[predictedLabel].append(entropy)

                else:
                    # set first threshold to 95% of max value if init buffer is full:
                    tmp = np.array(initBuffer[predictedLabel])
                    threshold[predictedLabel] = tmp.mean() + 1 * tmp.std() #TODO: xxxxxxxxxx
                    print("Initial threshold for " + revClassesDict[predictedLabel] + " class " + str(round(threshold[predictedLabel],4)))
                    thresSet[predictedLabel] = True
                    initThresSet[predictedLabel] = True

        # --- Setting threshold (not the initial ones): ---
        if (thresSet[predictedLabel] == False) and (feedbackReceived[predictedLabel] == True):
            if len(thresBuffer[predictedLabel]) < 300: # 300 equals 10min #TODO: xxxxxxxxxx
                # Fill threshold buffer
                thresBuffer[predictedLabel].append(entropy)
            else:
                # Threshold buffer full:
                thresBuffer[predictedLabel].append(entropy)

                if initThresSet[predictedLabel] == True:
                    # set threshold after a model adaption:
                    tmp = np.array(thresBuffer[predictedLabel])
                    thres = tmp.mean() + 3 * tmp.std() #TODO: xxxxxxxxxxxxxxxxxxxxxxxxxxxx
                    #prevThreshold = threshold[predictedLabel]
                    #threshold[predictedLabel] = (thres + prevThreshold) / 2.0
                    
                    threshold[predictedLabel] = (thres + thresQueriedInterval[predictedLabel]) / 2.0
                    
                    print("New threshold for " + revClassesDict[predictedLabel] + " class " +
                          str(round(threshold[predictedLabel],4)) + ". Set " + str(round(currentTime-prevTime)) + "s after model adaption")
                    
                    print("thresQueriedInterval for class " + str(predictedLabel) + ": " + str(thresQueriedInterval[predictedLabel]))
                    
                    thresSet[predictedLabel] = True


        # if the buffer is filled, check if we want to query:
        if (thresSet[predictedLabel] == True) and (len(queryBuffer) == 30): #(len(buffers[predictedLabel]) == 30)

            # --- calculate current query criteria: ---
            npCrit = np.array(queryBuffer)
            npPred = np.array(predBuffer)

            # Majority vote on predicted labels in this 1min:
            maj = majorityVote(npPred).mean()
            iTmp = (npPred == maj)
            majPoints = npCrit[iTmp]

            # only use points that had the same predicted class in that 1min window:
            queryCrit = majPoints.mean() #  + majPoints.std() #TODO: xxxxxxxxxx

            # Majority vote on actual labels in this 1min for comparison only:
            majActual = majorityVote(npActual).mean() # only for evaluation
            if majActual == maj:
                majCorrectCnt += 1
            else:
                majWrongCnt += 1

            # only query if more than 10min since the last query:
            if (currentTime - prevTime) > 600:
                # check if we want to query these points and update our threshold value if we adapt the model:
                if queryCrit > threshold[predictedLabel]:

                    if str(revClassesDict[actualLabel]) != "Conversation" and str(revClassesDict[predictedLabel]) != "Conversation": # ignore queries that are labeled as conversation or that were predicted as conversation
                        print("Query for " + str(revClassesDict[actualLabel]) + " class (predicted as " + str(revClassesDict[predictedLabel]) + ") received at " + str(currentTime) + " seconds.")

                        # adapt the model:
                        upd = np.array(updatePoints)

                        currentGMM = adaptGMM(currentGMM, upd, actualLabel)

                        allGMM.append(currentGMM)
                        givenLabels.append(actualLabel)
                        labelAccuracy.append(checkLabelAccuracy(simLabels[start:end], actualLabel))
                        timestamps.append(currentTime)

                        numQueries[actualLabel] += 1

                        feedbackReceived[actualLabel] = True

                        # Compute a value for the threshold on this interval, as we want to use it to calculate the new threshold later.
                        thresQueriedInterval[actualLabel] = majPoints.mean()# + 2 * majPoints.std() #xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx

                        # reset buffers:
                        queryBuffer = []
                        predBuffer = []

                        thresBuffer[0] = []
                        thresBuffer[1] = []
                        thresBuffer[2] = []

                        thresSet[0] = False
                        thresSet[1] = False
                        thresSet[2] = False

                        if(feedbackReceived[0]) == False:
                            initBuffer[0] = []
                        if(feedbackReceived[1]) == False:
                            initBuffer[1] = []
                        if(feedbackReceived[2]) == False:
                            initBuffer[2] = []


                        prevTime = currentTime

    print("Total number of queries: " + str(sum(numQueries)))
    print(str(round(100.0 * majWrongCnt/float(majWrongCnt+majCorrectCnt),2)) + "% of all majority votes were wrong")

    # ---- for plotting only: plot max values of entropy in 1min over time ---
    pl.plot(plotValues[0])
    pl.plot(plotThres[0])
    pl.show()
    pl.plot(plotValues[1])
    pl.plot(plotThres[1])
    pl.show()
    pl.plot(plotValues[2])
    pl.plot(plotThres[2])
    pl.show()


    pdb.set_trace()


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

def checkLabelAccuracy(actualLabels, label):
    """
    Calculate how many percent of the samples that are used to adapt the model, are actually of the correct class
    @param actualLabels: Ground truth labels of all points with which the model will be adapted
    @param label: The label of the class with which the model should be adapted
    """
    accuracy = len(actualLabels[actualLabels == label]) / float(len(actualLabels))
    print(str(round(accuracy*100,1)) + "% of the labels used to adapt the model were correct")

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
        # print("Precision " + str(sortedLabels[i]) + ": " + str(tmpPrecision))
        precisions.append(tmpPrecision)

    """ Calculate recall: """
    recalls = []
    for i in range(n_classes):
        recalls.append(normCM[i][i])
        # print("Recall " + str(sortedLabels[i]) + ": " + str(normCM[i][i]))

    """ Calculate F1-score: """
    F1s = {}
    for i in range(n_classes):
        tmpF1 = 2 * (precisions[i] * recalls[i]) / float(precisions[i] + recalls[i])
        # print("F1 " + str(sortedLabels[i]) + ": " + str(tmpF1))
        F1s[sortedLabels[i]] = tmpF1

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

































