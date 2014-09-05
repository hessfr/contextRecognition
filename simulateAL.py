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
from classifiers import getIndex, predictGMM, majorityVote
from offlineEvaluation import createGTMulti, createGTUnique, createPrediction
from featureExtraction import FX_multiFolders
from adaptGMM import adaptGMM
import ipdb as pdb #pdb.set_trace()

def simulateAL(trainedGMM, jsonFileList, gtFileMulti, gtFileUnique):
    """
    Query criteria is the mean entropy value on the 2 second interval.
    
    @param trainedGMM: already trained GMM
    @param jsonFileList: List of files containing the extracted features for the
    indivdual parts of the file.
    @param gtFileMulti: Normal ground truth file with multiple labels
    @param gtFileUnique: Ground truth file used to give the query feedback -> only
    one label allowed per point
    """
    n_classes = len(trainedGMM["classesDict"])

    """ Create ground truth array with multiple labels is used to evaluate the performance: """
    with open(gtFileMulti) as f:
        reader = csv.reader(f, delimiter="\t")
        gtListMulti = list(reader)

    recStartedListMulti = []
    for i in range(len(gtListMulti)):
        if len(gtListMulti[i]) <= 1:
            recStartedListMulti.append(i)

    # The number of given feature file has to match the number of RECORDING_STARTED entries:
    if (len(recStartedListMulti) != len(jsonFileList)):
        print("Ground truth file does not match the number of provided feature files "
        + "evaluation will be stopped: ")
        print(str(len(jsonFileList)) + " feature files were provided, but ground truth " +
        "file contains only " + str(len(recStartedListMulti)) + " RECORDING_STARTED entries")
        return None
    
    y_gt_multi = []
    for k in range(len(jsonFileList)):
        tmp_gt_multi = np.array(gtListMulti)    
        startIdx = recStartedListMulti[k]+1

        if (k < (len(recStartedListMulti)-1)):
            endIdx = recStartedListMulti[k+1]
        else:
            endIdx = len(gtListMulti)

        # Get the desired length of the ground truth array by reading in the length
        # of the sample points:
        jd = json.load(open(jsonFileList[k], "rb"))
        num_samples = np.array(jd["features"]).shape[0]
        
        gt_list_multi = list(tmp_gt_multi[startIdx:endIdx])
        y_gt_tmp = createGTMulti(trainedGMM["classesDict"], num_samples, gt_list_multi)
        y_gt_tmp = y_gt_tmp.tolist()

        y_gt_multi.extend(y_gt_tmp)

    y_gt_multi = np.array(y_gt_multi)
   
    """ Create a ground truth array with only one entry for each data point
    that will be used to provide the query feedback: """
    with open(gtFileUnique) as f:
        reader = csv.reader(f, delimiter="\t")
        gtListUnique = list(reader)

    recStartedListUnique = []
    for i in range(len(gtListUnique)):
        if len(gtListUnique[i]) <= 1:
            recStartedListUnique.append(i)

    # The number of given feature file has to match the number of RECORDING_STARTED entries:
    if (len(recStartedListUnique) != len(jsonFileList)):
        print("Ground truth file does not match the number of provided feature files "
        + "evaluation will be stopped: ")
        print(str(len(jsonFileList)) + " feature files were provided, but ground truth " +
        "file contains only " + str(len(recStartedListUnique)) + " RECORDING_STARTED entries")
        return None
    
    y_gt_unique = []
    for k in range(len(jsonFileList)):
        tmp_gt_unique = np.array(gtListUnique)    
        startIdx = recStartedListUnique[k]+1

        if (k < (len(recStartedListUnique)-1)):
            endIdx = recStartedListUnique[k+1]
        else:
            endIdx = len(gtListUnique)

        # Get the desired length of the ground truth array by reading in the length
        # of the sample points:
        jd = json.load(open(jsonFileList[k], "rb"))
        num_samples = np.array(jd["features"]).shape[0]
        
        gt_list_unique = list(tmp_gt_unique[startIdx:endIdx])
        y_gt_tmp = createGTUnique(trainedGMM["classesDict"], num_samples, gt_list_unique)
        
        y_gt_tmp = y_gt_tmp.tolist()

        y_gt_unique.extend(y_gt_tmp)

    y_gt_unique = np.array(y_gt_unique)

    classesInGT = np.unique(y_gt_multi)
    classesInGT = classesInGT[classesInGT != -1]

    """ Save all features and amplitude values in single numpy arrays: """
    featureData = []
    amps = []
    for k in range(len(jsonFileList)):
        jd = json.load(open(jsonFileList[k], "rb"))
        featureData.extend(np.array(jd["features"]).tolist())
        amps.extend(np.array(jd["amps"]).tolist())

    featureData = np.array(featureData)
    amps = np.array(amps) #TODO: until now we don't use this!

    """ Create index arrays to define which elements are used to evaluate performance 
    and which for simulation of the AL behavior: """
    [evalIdx, simIdx] = splitData(y_gt_unique)

    pdb.set_trace()
    
    evalFeatures = featureData[evalIdx == 1]
    evalLabels = y_gt_multi[evalIdx == 1] # contains multiple labels for each point

    simFeatures = featureData[simIdx == 1]
    simFeatures = trainedGMM['scaler'].transform(simFeatures)
    simLabels = y_gt_unique[simIdx == 1]

    """ simLabels contains unique label for each point that will be used to update 
    the model later, e.g. if a point has the labels office and conversation, 
    it will be trained with conversation (accoring to the provided groundTruthLabels """
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

    # Simulate actual behavior by reading in points in batches of size b: 
    b = 63 # equals 2 seconds

    predictedLabels = []

    currentTime = 0
    prevTime = -1000000.0


    """ Initialize buffers etc. """

    # Booleans that indicate if the initial threshold was already set:
    initThresSet = []

    # Booleans that indicate if the initial threshold was already set:
    thresSet = []
    
    # Booleans that indicate if any label was already provided by user for a class. To make sure that we
    # don't set the threshold too high for that class after the model was adapted
    feedbackReceived = []
    
    # Number of queries asked for each class:
    numQueries = [] # only for evaluation

    # Thresholds for the different classes:
    threshold = []
 
    updatePoints = []

    # ---- for plotting only ---
    plotValues = [] # only for evaluation
    plotThres = []
    plotBuffer = []
    plotActualTicks = []
    plotActualIdx = []

    # predicted labels in the last minute:
    predBuffer = []

    # Single buffer containing the last 30 points regardless of the predicted class
    queryBuffer = []

    # Actual ground truth labels:
    actBuffer = []

    # Our 3 min (?) buffer we use to initialized the threshold:
    initThresBuffer = []
    
    # Our 10min buffer we use for threshold calculation after adapting the model:
    thresBuffer = []
    
    thresQueriedInterval = []

    for i in range(n_classes):
        initThresSet.append(False)
        thresSet.append(False)
        feedbackReceived.append(False)
        numQueries.append(0)
        threshold.append(-1)

        initThresBuffer.append([])
        thresBuffer.append([])
        thresQueriedInterval.append(-1)

        # ---- for plotting only: ---
        plotValues.append([])
        plotThres.append([])
        plotBuffer.append([])
        # Add tickmark to the plot, of the classes that was actually 
        # predicted and caused this query:
        plotActualTicks.append([])
        plotActualIdx.append([])
    

    majCorrectCnt = 0 #only for evaluation
    majWrongCnt = 0 #only for evaluation
    
    
    # This loop loads new data every 2sec:
    for i in range(simFeatures.shape[0]/b):
        start = i*b
        end = (i+1)*b
        currentPoints = simFeatures[start:end,:]
        actualLabel = int(simLabels[end]) # only the latest label is used to adapt the model, like in real-life
        currentTime = i*b*0.032

        # Buffer points of the 30 last 2 second intervals, as we want to update 
        # our model with the last minute of data
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
            actBuffer.append(actualLabel)
        else:
            queryBuffer.append(entropy)
            del queryBuffer[0]
            predBuffer.append(predictedLabel)
            del predBuffer[0]
            actBuffer.append(actualLabel)
            del actBuffer[0]

        # --- for plotting only:
       # if len(plotBuffer[predictedLabel]) < 30:
       #     # fill buffer:
       #     plotBuffer[predictedLabel].append(entropy)
       # else:
       #     # buffer full, so we want to add this value to the plot:
       #     tmp = np.array(plotBuffer[predictedLabel])
       #     plotValues[predictedLabel].append(queryCriteria(tmp))
       #     plotThres[predictedLabel].append(threshold[predictedLabel])
       #     plotBuffer[predictedLabel] = []

        # --- Setting initial threshold: ---
        if (initThresSet[predictedLabel] == False):
                if len(initThresBuffer[predictedLabel]) < 90:
                    # Fill init threshold buffer
                    initThresBuffer[predictedLabel].append(entropy)

                else:
                    # set first threshold init buffer is full:
                    tmp = np.array(initThresBuffer[predictedLabel])
                    threshold[predictedLabel] = initMetric(tmp.mean(), tmp.std())
                    print("Initial threshold for " + revClassesDict[predictedLabel] + 
                    " class " + str(round(threshold[predictedLabel],4)))
                    thresSet[predictedLabel] = True
                    initThresSet[predictedLabel] = True

        # --- Setting threshold (not the initial ones): ---
        if (thresSet[predictedLabel] == False) and (feedbackReceived[predictedLabel] == True):
            if len(thresBuffer[predictedLabel]) < 300: # 300 equals 10min 
                # Fill threshold buffer
                thresBuffer[predictedLabel].append(entropy)
            else:
                # Threshold buffer full:
                thresBuffer[predictedLabel].append(entropy)


                if initThresSet[predictedLabel] == True:
                    # set threshold after a model adaption:
                    tmp = np.array(thresBuffer[predictedLabel])
                    thres = metricAfterFeedback(tmp.mean(), tmp.std())
                    #prevThreshold = threshold[predictedLabel]
                    #threshold[predictedLabel] = (thres + prevThreshold) / 2.0
                    
                    threshold[predictedLabel] = (thres + thresQueriedInterval[predictedLabel]) / 2.0
                    
                    print("New threshold for " + revClassesDict[predictedLabel] + " class " +
                          str(round(threshold[predictedLabel],4)) + ". Set " + 
                          str(round(currentTime-prevTime)) + "s after model adaption")
                    
                    #print("thresQueriedInterval for class " + str(revClassesDict[predictedLabel]) + 
                    #": " + str(thresQueriedInterval[predictedLabel]))
                    
                    
                    thresSet[predictedLabel] = True


        # if the buffer is filled, check if we want to query:
        if (thresSet[predictedLabel] == True) and (len(queryBuffer) == 30): #(len(buffers[predictedLabel]) == 30)

            # --- calculate current query criteria: ---
            npCrit = np.array(queryBuffer)
            npPred = np.array(predBuffer)
            npActual = np.array(actBuffer)

            # Majority vote on predicted labels in this 1min:
            maj = majorityVote(npPred).mean()
            iTmp = (npPred == maj)
            majPoints = npCrit[iTmp]

            # only use points that had the same predicted class in that 1min window:
            queryCrit = queryCriteria(majPoints)

            # Majority vote on actual labels in this 1min for comparison only:
            majActual = majorityVote(npActual).mean() # only for evaluation
            if majActual == maj:
                majCorrectCnt += 1
            else:
                majWrongCnt += 1
            
            # --- for plotting only: ---
            # This will create plots, where the values when the buffers are
            # being filled are totally ignored, and were we cannot see the
            # waiting time of 10min
            plotValues[predictedLabel].append(queryCrit)
            plotThres[predictedLabel].append(threshold[predictedLabel])
            # --------------------------
       
            # only query if more than 10min since the last query:
            if (currentTime - prevTime) > 600:
                # check if we want to query these points and update our threshold value if we adapt the model:
                if queryCrit > threshold[predictedLabel]:

                    # ignore queries that are labeled as conversation or that were predicted as conversation
                    #if (str(revClassesDict[actualLabel]) != "Conversation" and 
                    #str(revClassesDict[predictedLabel]) != "Conversation"):
                    if True:
                        print("-----")
                        print("Query for " + str(revClassesDict[actualLabel]) + 
                        " class (predicted as " + str(revClassesDict[predictedLabel]) + 
                        ") received at " + str(currentTime) + " seconds.")
                      
                        # Add tick marks for that actual label to the plot of 
                        # the predicted label (as the exceeding of the threshold 
                        # of the predicted label caused that query):
                        plotActualTicks[predictedLabel].append(str(revClassesDict[actualLabel]))
                        plotActualIdx[predictedLabel].append(len(plotValues[predictedLabel]))

                        # adapt the model:
                        upd = np.array(updatePoints)

                        currentGMM = adaptGMM(currentGMM, upd, actualLabel)

                        allGMM.append(currentGMM)
                        givenLabels.append(actualLabel)
                        labelAccuracy.append(checkLabelAccuracy(simLabels[start:end], actualLabel))
                        timestamps.append(currentTime)

                        numQueries[actualLabel] += 1

                        feedbackReceived[actualLabel] = True

                        # Compute a value for the threshold on this interval (that triggered the query), 
                        # as we want to use it to calculate the new threshold later.
                        thresQueriedInterval[actualLabel] = metricBeforeFeedback(majPoints.mean(), 
                        majPoints.std())

                        # reset buffers:
                        queryBuffer = []
                        predBuffer = []
                        actBuffer = []

                        for i in range(n_classes):
                            thresBuffer[i] = []
                            thresSet[i] = False
                            if(feedbackReceived[i]) == False:
                                initThresBuffer[i] = []

                        prevTime = currentTime

    print("Total number of queries: " + str(sum(numQueries)))
    print(str(round(100.0 * majWrongCnt/float(majWrongCnt+majCorrectCnt),2)) + "% of all majority votes were wrong")

    # ---- for plotting only: query criteria and threshold values over time for each class separately: ---
    for i in range(n_classes):
        # Don't show plots for classes with too few samples:
        if (len(plotValues[i]) > 0):
            fig = pl.figure()
            pl.title(revClassesDict[i])
            pl.plot(plotValues[i])
            pl.plot(plotThres[i])
            pl.xticks(plotActualIdx[i], plotActualTicks[i], rotation=45)
            #pl.show()
            fig.savefig("plotsTmp/Class_" + revClassesDict[i] + ".jpg")

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
        resultDict["classesInGT"] = classesInGT
        results.append(resultDict)
        i += 1

    return results

def queryCriteria(data):
    """
    The metric that is used to check the query criteria

    @param data: Numpy array of the batch of data that should be checked
    @return: Scalar value, that will be compared to the threshold
    """
    return data.mean()

def initMetric(mean, std):
    """
    Metric that is used to set the initial threshold for each class

    @param mean: Mean value (scalar) of the 2 second interval
    @param std: Standard deviation value (scalar) of the 2 second interval
    @return: Scalar value that is used to set the initial threshold
    """
    return (mean + std)

def metricAfterFeedback(mean, std):
    """
    Part of the threshold calculation accounting for only for values after the 
    model adaption after the buffer is filled, i.e. not immediatly after (calculated with the new model)

    @param mean: Mean value (scalar) of the 2 second interval
    @param std: Standard deviation value (scalar) of the 2 second interval
    @return: Scalar value used to calculate part of the threshold
    """
    return (mean + std)

def metricBeforeFeedback(mean, std):
    """
    Part of the threshold calculation accounting for only for values on the 
    interval that triggered the query (calculated with the old model)

    @param mean: Mean value (scalar) of the 2 second interval
    @param std: Standard deviation value (scalar) of the 2 second interval
    @return: Scalar value used to calculate part of the threshold
    """
    return (mean + std)



def checkLabelAccuracy(actualLabels, label):
    """
    Calculate how many percent of the samples that are used to adapt the model, are actually of the correct class
    @param actualLabels: Ground truth labels of all points with which the model will be adapted
    @param label: The label of the class with which the model should be adapted
    """
    accuracy = len(actualLabels[actualLabels == label]) / float(len(actualLabels))
    #print(str(round(accuracy*100,1)) + "% of the labels used to adapt the model were correct")

    return accuracy

def evaluateGMM(trainedGMM, evalFeatures, evalLabels):
    """

    @param trainedGMM:
    @param evalFeatures: not scaled, as scaling is done in testGMM method
    @param evalLabels: Ground truth label array with multiple labels per data points
    @return:
    """

    """ Calculate the predictions on the evaluation features: """
    y_pred = createPrediction(trainedGMM, evalFeatures) #TODO:xxxxx

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

































