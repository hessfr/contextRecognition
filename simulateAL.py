# To save figures on ssh server:
import matplotlib
matplotlib.use('Agg')
import matplotlib.pylab as pl
import numpy as np
import pickle
import json
import csv
import copy
import operator
import random
from scipy.stats import itemfreq
from sklearn.mixture import GMM
from sklearn import preprocessing
from operator import itemgetter
from classifiers import getIndex, predictGMM, majorityVote, logProb, confusionMatrixMulti
from offlineEvaluation import createGTMulti, createGTUnique, majorityVoteSilence
from featureExtraction import FX_multiFolders
from adaptGMM import adaptGMM
from plotAL import plotAL
import ipdb as pdb #pdb.set_trace()

# --- simulation commands: ---
# res = simulateAL(gmm1, "/media/thesis-graphs/hessfr/contextRecognition/experimentData/user1_355593052044182/allDays/", ["user1_part1.json", "user1_part2.json", "user1_part3.json", "user1_part4.json", "user1_part5.json", "user1_part6.json", "user1_part7.json"], "GT_user1.txt")

# res = simulateAL(gmm1, "/media/thesis-graphs/hessfr/contextRecognition/experimentData/user1_355593052044182/", ["user1_short.json"], "GT_user1_short.txt")

# res = simulateAL(gmm2, "/media/thesis-graphs/hessfr/contextRecognition/experimentData/user2_358848046667739/allDays/", ["user2_part1.json", "user2_part2.json", "user2_part3.json", "user2_part4.json", "user2_part5.json", "user2_part6.json"], "GT_user2.txt")

# res = simulateAL(gmm4, "/media/thesis-graphs/hessfr/contextRecognition/experimentData/user4_355577053607766/allDays/", ["user4_part1.json", "user4_part2.json", "user4_part3.json", "user4_part4.json", "user4_part5.json", "user4_part6.json", "user4_part7.json", "user4_part8.json"], "GT_user4.txt")
# ----------------------------

def simulateAL(trainedGMM, path, jsonFileList, gtFile):
    """
    Query criteria is the mean entropy value on the 2 second interval.
    
    @param trainedGMM: already trained GMM
    @param path: path to the folder of the extracted features and the ground truth file
    @param jsonFileList: List of files containing the extracted features for the
    indivdual parts of the file.
    @param gtFile: Normal ground truth file with multiple labels
    one label allowed per point
    """
    n_classes = len(trainedGMM["classesDict"])
    silenceClassNum = max(trainedGMM["classesDict"].values())+1

    """ Create ground truth array with multiple labels is used to evaluate the performance: """
    with open((path+gtFile)) as f:
        reader = csv.reader(f, delimiter="\t")
        gtList = list(reader)

    # These classes will be completely removed from the ground truth lists:
    classesToIgnore = ["Home"]
    for ignoreClass in classesToIgnore:
        # Remove every class that should be ignored:
        gtList = [el for el in gtList if ignoreClass not in el]

    # These classes will be renamed:
    # Syntax: key = old class name, value = new class name:
    classesToRename = {"Cycling": "Street"}
    for i in range(len(gtList)):
        for j in range(len(gtList[i])):
            if gtList[i][j] in classesToRename.keys():
                gtList[i][j] = classesToRename[gtList[i][j]]
    
    recStartedList = []
    for i in range(len(gtList)):
        if len(gtList[i]) <= 1:
            recStartedList.append(i)

    # The number of given feature file has to match the number of RECORDING_STARTED entries:
    if (len(recStartedList) != len(jsonFileList)):
        print("Ground truth file does not match the number of provided feature files "
        + "evaluation will be stopped: ")
        print(str(len(jsonFileList)) + " feature files were provided, but ground truth " +
        "file contains only " + str(len(recStartedList)) + " RECORDING_STARTED entries")
        return None
    
    y_gt_multi = []
    for k in range(len(jsonFileList)):
        
        tmp_gt_multi = np.array(gtList)    
        startIdx = recStartedList[k]+1

        if (k < (len(recStartedList)-1)):
            endIdx = recStartedList[k+1]
        else:
            endIdx = len(gtList)

        # Get the desired length of the ground truth array by reading in the length
        # of the sample points:
        jd = json.load(open((path+jsonFileList[k]), "rb"))
        num_samples = np.array(jd["features"]).shape[0]
        
        gt_list_multi = list(tmp_gt_multi[startIdx:endIdx])
        y_gt_tmp = createGTMulti(trainedGMM["classesDict"], num_samples, gt_list_multi)
        y_gt_tmp = y_gt_tmp.tolist()

        y_gt_multi.extend(y_gt_tmp)

    y_gt_multi = np.array(y_gt_multi)
   
    """ To give the AL feedback we have to have an array with only one element for
    each sample point. Create this array be randomly selecting a single label for every
    sample points in the ground truth: """
    y_gt_unique = np.empty(y_gt_multi.shape[0])
    #emptyRow = np.array([-1,-1,-1,-1,-1]) # = no ground truth provided
    emptyRow = {-1}
    onlyConv = {-1, trainedGMM["classesDict"]["Conversation"]}
    itemsToDelete = [-1, trainedGMM["classesDict"]["Conversation"]]
    for i in range(y_gt_multi.shape[0]):
        #if np.array_equal(y_gt_multi[i,:], emptyRow):
        rowSet = set(y_gt_multi[i,:].tolist())
        if (rowSet == emptyRow or rowSet == onlyConv):
            y_gt_unique[i] = -1
        else:
            rowList = list(rowSet)
            for el in itemsToDelete:
                if el in rowList:
                    rowList.remove(el)
            y_gt_unique[i] = random.choice(rowList)        

    classesInGT = np.unique(y_gt_multi)
    classesInGT = classesInGT[classesInGT != -1]

    """ Save all features and amplitude values in single numpy arrays: """
    featureData = []
    amps = []
    for k in range(len(jsonFileList)):
        jd = json.load(open((path+jsonFileList[k]), "rb"))
        featureData.extend(np.array(jd["features"]).tolist())
        amps.extend(np.array(jd["amps"]).tolist())

    featureData = np.array(featureData)
    amps = np.array(amps)

    """ Create index arrays to define which elements are used to evaluate performance 
    and which for simulation of the AL behavior: """
    [evalIdx, simIdx] = splitData(y_gt_unique)

    evalFeatures = featureData[evalIdx == 1]
    evalAmps = amps[evalIdx == 1]
    evalLabels = y_gt_multi[evalIdx == 1] # contains multiple labels for each point

    simFeatures = featureData[simIdx == 1]
    simFeatures = trainedGMM['scaler'].transform(simFeatures)
    simAmps = amps[simIdx == 1]
    # simLabelsUnique contains unique label for each point that will be used to update 
    # the model later, e.g. if a point has the labels office and conversation, 
    # it will be trained with conversation (accoring to the provided groundTruthLabels:
    simLabelsUnique = y_gt_unique[simIdx == 1]
   
    # simLabelsMulti is used to evaluate, what actual labels the points have, that are
    # incorporated into the model:
    simLabelsMulti = y_gt_multi[simIdx == 1]

    currentGMM = copy.deepcopy(trainedGMM)

    allGMM = [] 
    allGMM.append(currentGMM)
    givenLabels = []
    givenLabels.append(-1) #because first classifiers is without active learning yet
    labelAccuracy = []
    labelAccuracy.append([-1, -1])
    timestamps = [] #time when the query was sent on the simulation data set -> only half the length of the complete data set
    timestamps.append(0)
    
    revClassesDict = reverseDict(trainedGMM["classesDict"])

    # Simulate actual behavior by reading in points in batches of size b: 
    b = 63 # equals 2 seconds

    predictedLabels = []

    currentTime = 0
    prevTime = -1000000.0

    # If all amplitudes in a 2s window are below this threshold, consider it as silent:
    silenceThreshold = 1000
    
    # When adaption the model, use only data points of the last minute, if the amplitude,
    # is above this value:
    silenceThresholdModelAdaption = 200 # TODO: if all point should be used, set this to -1 

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
 
    # Feature values, ground truth values (for evaluation only) and amplitude 
    # that the model is updated with:
    updatePoints = []
    updateGT = []
    updateAmps = []

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

    # To create the overall plot (with GT classes, predicted classes, entropy and query)
    actual_labels = []
    entropy_values = []
    # we also use the "timestamps" list and and the "givenLabels" list to create this plot 
    
    # This loop loads new data every 2sec:
    for i in range(simFeatures.shape[0]/b):
        start = i*b
        end = (i+1)*b
        currentAmps = simAmps[start:end]
        
        # Only do something if this sequence is not silent:
        if currentAmps.max() >= silenceThreshold:
            currentPoints = simFeatures[start:end,:]
            currentGT = simLabelsMulti[start:end,:]
            # only the latest label is used to adapt the model, like in real-life
            actualLabel = int(simLabelsUnique[end])
            currentTime = i*b*0.032

            # Buffer points of the 30 last 2 second intervals, as we want to update 
            # our model with the last minute of data
            if len(updatePoints) < 1875:
                updatePoints.extend(currentPoints.tolist())
                updateGT.extend(currentGT)
                updateAmps.extend(currentAmps.tolist())
            else:
                updatePoints.extend(currentPoints.tolist())
                del updatePoints[0:b]
                updateGT.extend(currentGT)
                del updateGT[0:b]
                updateAmps.extend(currentAmps.tolist())
                del updateAmps[0:b]

            resArray, entropy = predictGMM(currentGMM, currentPoints, scale=False, returnEntropy=True)
            predictedLabel = int(resArray.mean())
            
            # For the overall plot:
            predictedLabels.append(predictedLabel)
            actual_labels.append(actualLabel)
            entropy_values.append(entropy)

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

            # --- Setting initial threshold: ---
            if (initThresSet[predictedLabel] == False):
                    if len(initThresBuffer[predictedLabel]) < 30: #TODO: 90
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
                        
                        threshold[predictedLabel] = (thres + thresQueriedInterval[predictedLabel]) / 2.0
                        
                        print("New threshold for " + revClassesDict[predictedLabel] + " class " +
                              str(round(threshold[predictedLabel],4)) + ". Set " + 
                              str(round(currentTime-prevTime)) + "s after model adaption")
                        
                        thresSet[predictedLabel] = True


            # if the buffer is filled, check if we want to query:
            if (thresSet[predictedLabel] == True) and (len(queryBuffer) == 30):

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
                    # check if we want to query these points and update 
                    # our threshold value if we adapt the model:
                    if queryCrit > threshold[predictedLabel]:

                        # ignore queries that are labeled as conversation 
                        # or that were predicted as conversation
                        #if (str(revClassesDict[actualLabel]) != "Conversation" and 
                        #str(revClassesDict[predictedLabel]) != "Conversation"):
                        
                       # queryPermitted = True
                       # if (str(revClassesDict[actualLabel]) in maxQueries):
                       #     if (numQueries[actualLabel] >= 
                       #     maxQueries[str(revClassesDict[actualLabel])]):
                       #         queryPermitted = False
                        
                        if True:
                            print("-----")
                            print("Query for " + str(revClassesDict[actualLabel]) + 
                            " class (predicted as " + str(revClassesDict[predictedLabel]) + 
                            ") received at " + str(currentTime) + " seconds.")
                            print("-----")
                            
                            # Add tick marks for that actual label to the plot of 
                            # the predicted label (as the exceeding of the threshold 
                            # of the predicted label caused that query):
                            plotActualTicks[predictedLabel].append(str(revClassesDict[actualLabel]))
                            plotActualIdx[predictedLabel].append(len(plotValues[predictedLabel]))
                            
                            # adapt the model:
                            upd = np.array(updatePoints)
                            
                            #only consider non-silent samples here:
                            amp = np.array(updateAmps)                            
                            upd = upd[amp > silenceThresholdModelAdaption]
                            print("--- " + str(round(100 * upd.shape[0]/(float(len(updatePoints))), 2)) + 
                            "% of all samples of the last minute used to adapt the model, " + 
                            "the rest is silent ---")

                            currentGMM = adaptGMM(currentGMM, upd, actualLabel)

                            allGMM.append(currentGMM)
                            givenLabels.append(actualLabel)
                            labelAccuracy.append(checkLabelAccuracy(simLabelsMulti[start:end], actualLabel))
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

    createOverallPlot(actual_labels, predictedLabels, entropy_values, 
    givenLabels, timestamps, currentGMM["classesDict"])

    print("Total number of queries: " + str(sum(numQueries)))
    print(str(round(100.0 * majWrongCnt/float(majWrongCnt+majCorrectCnt),2)) + "% of all majority votes were wrong")

    # ---- for plotting only: query criteria and threshold values over time for
    # each class separately: ---
    for i in range(n_classes):
        # Only plot classes where enough data is available:
        if (len(plotValues[i]) > 0):
            fig = pl.figure()
            pl.title(revClassesDict[i])
            pl.plot(plotValues[i])
            pl.plot(plotThres[i])
            pl.xticks(plotActualIdx[i], plotActualTicks[i], rotation=45)
            #pl.show()
            fig.savefig("plotsTmp/Class_" + revClassesDict[i] + ".jpg")

    #pdb.set_trace()


    """ Evaluate performance of all GMMs: """
    print("Evaluating performance of classifiers:")
    results = []
    i=0
    for GMM in allGMM:
        resultDict = evaluateGMM(GMM, evalFeatures, evalAmps, evalLabels, silenceClassNum)
        resultDict["label"] = givenLabels[i]
        resultDict["labelAccuracy"] = labelAccuracy[i]
        resultDict["timestamp"] = timestamps[i]
        resultDict["classesDict"] = GMM["classesDict"]
        resultDict["duration"] = simFeatures.shape[0] * 0.032 #length in seconds
        resultDict["classesInGT"] = classesInGT
        results.append(resultDict)
        i += 1

    plotAL(results)

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
    return (mean - 0.5 * std)

def metricAfterFeedback(mean, std):
    """
    Part of the threshold calculation accounting for only for values after the 
    model adaption after the buffer is filled, i.e. not immediatly after (calculated with the new model)

    @param mean: Mean value (scalar) of the 2 second interval
    @param std: Standard deviation value (scalar) of the 2 second interval
    @return: Scalar value used to calculate part of the threshold
    """
    return mean
    #return (mean + std)

def metricBeforeFeedback(mean, std):
    """
    Part of the threshold calculation accounting for only for values on the 
    interval that triggered the query (calculated with the old model)

    @param mean: Mean value (scalar) of the 2 second interval
    @param std: Standard deviation value (scalar) of the 2 second interval
    @return: Scalar value used to calculate part of the threshold
    """
    return mean
    #return (mean + std)


def checkLabelAccuracy(actualLabels, label):
    """
    Calculate how many percent of the samples that are used to adapt the model, are 
    actually of the correct class and how many percent of these points have the 
    correct label AND another additional label
    
    @param actualLabels: Ground truth labels of all points with which the model will be adapted.
    Contains multiple labels per point
    @param label: The label of the class with which the model should be adapted
    @return: accuracy, multiLabels in percent
    """
    #accuracy = len(actualLabels[actualLabels == label]) / float(len(actualLabels))
    correctCnt = 0
    correctMultipleCnt = 0 # label is correct, but other classes also in GT
    for i in range(actualLabels.shape[0]):
        if label in actualLabels[i,:]:
            correctCnt += 1
            if len(set(actualLabels[i,:].tolist())) != 2:
                correctMultipleCnt += 1
    
    accuracy = correctCnt / (float(len(actualLabels)))
    multiLabels = correctMultipleCnt / (float(len(actualLabels)))

    print(str(round(accuracy*100,1)) + "% of the labels used to adapt the model correct")
    print(str(round(multiLabels*100,1)) + "% of all labels correct, but also contained " + 
    "another label")

    return [accuracy, multiLabels]

def evaluateGMM(trainedGMM, evalFeatures, evalAmps, evalLabels, silenceClassNum):
    """

    @param trainedGMM:
    @param evalFeatures: not scaled!
    @param evalAmps: Amplitude values
    @param evalLabels: Ground truth label array with multiple labels per data points
    @param silenceClassNum:
    @return:
    """

    """ Calculate the predictions on the evaluation features: """
    y_pred = makePrediction(trainedGMM, evalFeatures, evalAmps, silenceClassNum)

    n_classes = len(trainedGMM["classesDict"])
    
    # Delete invalid rows:
    invalidRow = np.array([-1,-1,-1,-1,-1])
    maskValid = ~np.all(evalLabels==invalidRow,axis=1)
    evalLabels = evalLabels[maskValid]
    y_pred = y_pred[maskValid]

    # Calculate how many percent of the samples are silent and delete silent samples:
    maskNonSilent = (y_pred != silenceClassNum)
    numSilentSamples = np.sum(~maskNonSilent)
    silentPercentage = numSilentSamples / float(y_pred.shape[0])
    print(str(round(silentPercentage*100,2)) + "% percent of all samples are silent")

    evalLabels = evalLabels[maskNonSilent]
    y_pred = y_pred[maskNonSilent]
    
    # Calculate the overall accuracy and print it:
    correctPred = 0
    for i in range(y_pred.shape[0]):
        if y_pred[i] in evalLabels[i,:]:
            correctPred += 1

    accuracy = correctPred / float(y_pred.shape[0])
    print("Overall accuracy: " + str(round(accuracy*100,2)) + "%")
    print("-----")

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

    # Plot the confusion matrix:
    confusionMatrixMulti(evalLabels, y_pred, trainedGMM["classesDict"], ssh=True)

    resDict = {"accuracy": accuracy, "F1dict": F1s}

    return resDict

def makePrediction(trainedGMM, evalFeatures, evalAmps, silenceClassNum):
    """

    @param trainedGMM:
    @param evalFeatures: not scaled!!
    @param evalAmps: Amplitude values
    @param silenceClassNum:
    @return:
    """
    n_classes = len(trainedGMM['clfs'])

    X_test = trainedGMM['scaler'].transform(evalFeatures)

    logLikelihood = np.zeros((n_classes, X_test.shape[0]))

    """ Compute log-probability for each class for all points: """
    for i in range(n_classes):
        logLikelihood[i] = logProb(X_test, trainedGMM['clfs'][i].weights_,
        trainedGMM['clfs'][i].means_, trainedGMM['clfs'][i].covars_)

    """ Select the class with the highest log-probability: """
    y_pred = np.argmax(logLikelihood, 0)

    return majorityVoteSilence(y_pred, evalAmps, silenceClassNum)

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

def createOverallPlot(actual_labels, predictedLabels, entropy_values, 
    givenLabels, timestamps, classesDict):
    """
    Create an overall plot of predicted label, actual labels, entropy values and given feedbacks 

    @param actual_labels: Ground truth labels for every 2s interval
    @param predictedLabels: Predicted label for every 2s interval
    @param entropy_values: Mean entropy value for every 2s interval
    @param givenLabels: List containing the user feedback
    @param timestamps: List of the same length as givenLabels, containing the timestamps
    when user feedback occured in seconds (needs to be converted first to match other lists) ...
    @param classesDict: 

    """
    fig = pl.figure(figsize=(20, 15))
    ax = pl.subplot(1,1,1)

    # Index of the 2s intervals:
    idx = range(len(actual_labels))

    ax.scatter(idx, actual_labels, marker="s",   color="m")

    # Place the predicted labels a bit higher, so the they don't overlap the GT labels:
    predictedLabels = [(el+0.1) for el in predictedLabels]
    ax.scatter(idx, predictedLabels, marker="o", color="b", s=1)
    
    # Set class names as labels on the y-axis:
    sortedLabels = [list(x) for x in zip(*sorted(zip(classesDict.values(), 
    classesDict.keys()), key=itemgetter(0)))][1]
    pl.yticks(range(len(sortedLabels)), sortedLabels)

    pl.xlim([0, len(predictedLabels)])

    
    # Scale entropy values:
    scale_factor = max(classesDict.values())/max(entropy_values)
    entropy_values = [(el*scale_factor) for el in entropy_values]
    
    # Calculate mean of entropy values over the last 1min:
    entropy_values = np.array(entropy_values)
    filtered_entropy_values = np.zeros(len(entropy_values))
    # From the entropy values on the 2s interval, calulcate the mean values of the last 30 elements:
    for i in range(1, len(entropy_values)):
        if i > 30:
            filtered_entropy_values[i] = np.mean(entropy_values[(i-30):i])
        else:
            filtered_entropy_values[i] = np.mean(entropy_values[0:i])
    
    ax.plot(idx, filtered_entropy_values, c="y")


    # Add the queries timestamps to the x-axis:
    scale_time = 63*0.032
    timestamps = [(el/scale_time) for el in timestamps]
    
    del timestamps[0]
    del givenLabels[0]

    revDict = reverseDict(classesDict)

    for i in range(len(givenLabels)):
        givenLabels[i] = revDict[givenLabels[i]]

    pl.xticks(timestamps, givenLabels, rotation=45)



    fig.savefig("plotsTmp/overall_plot.jpg")

    pdb.set_trace()









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

































