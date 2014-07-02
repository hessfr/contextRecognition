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

def defineThresholdDict():

    _thresholdDict["Conversation"] = {}
    _thresholdDict["Office"] = {}
    _thresholdDict["TrainInside"] = {}


    #values for top 10 threshold for each class:
    _thresholdDict["Conversation"]["entropy"] = 1.098211
    _thresholdDict["Conversation"]["margin"] = 1.2e-05
    _thresholdDict["Conversation"]["percentDiff"] = 2.8e-05

    _thresholdDict["Office"]["entropy"] = 1.098206
    _thresholdDict["Office"]["margin"] = 4.5e-05
    _thresholdDict["Office"]["percentDiff"] = 0.000108

    _thresholdDict["TrainInside"]["entropy"] = 1.098023
    _thresholdDict["TrainInside"]["margin"] = 1.9e-05
    _thresholdDict["TrainInside"]["percentDiff"] = 4e-05

    _thresholdDict["Conversation"]["entropyMean"] = 0.917565
    _thresholdDict["Office"]["entropyMean"] = 0.903429
    _thresholdDict["TrainInside"]["entropyMean"] = 0.917368

def simulateAL(trainedGMM, testFeatureData):
    """
    outdated!!
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

    updatePointsList = []

    revClassesDict = reverseDict(trainedGMM["classesDict"])

    """ Simulate actual behavior by reading in points one by one: """
    defineThresholdDict()

    # print(simFeatures.shape[0])
    # to get Train samples, start with 448500

    for i in range(0, simFeatures.shape[0]):
        currentPoint = simFeatures[i,:]
        actualLabel = simLabels[i]

        # if (i*0.032 + 600) > timestamps[-1]:
        if queryCriteria(currentGMM, currentPoint, revClassesDict[actualLabel], criteria="entropy"):
            print("sending query for " + str(revClassesDict[actualLabel]))

            if str(revClassesDict[actualLabel]) != "Conversation": # str(revClassesDict[actualLabel]) != "Conversation" and str(revClassesDict[actualLabel]) != "Office": #TODO: xxxxxxxxxxxxxxxxxxxxxxx
                #set the current label for the last N points:
                N = 1875 # = 1 minute
                if i > N:
                    updatePoints = simFeatures[i-N:i,:]
                    labelAccuracy.append(checkLabelAccuracy(simLabels[i-N:i], actualLabel))

                    """ to test when choosing only samples with correct labels: """ #TODO: remove again later!!
                    # tmpFeatures = simFeatures[i-N:i,:]
                    # tmpLabels = simLabels[i-N:i]
                    # updatePoints = tmpFeatures[tmpLabels == actualLabel]
                    # labelAccuracy.append(1)

                else:
                    updatePoints = simFeatures[0:i,:]
                    labelAccuracy.append(checkLabelAccuracy(simLabels[0:i], actualLabel))

                    """ to test when choosing only samples with correct labels: """ #TODO: remove again later!!
                    # tmpFeatures = simFeatures[0:i,:]
                    # tmpLabels = simLabels[0:i]
                    # updatePoints = tmpFeatures[tmpLabels == actualLabel]
                    # labelAccuracy.append(1)

                # print(updatePoints.shape[0])
                updatePointsList.append(updatePoints)
                givenLabels.append(actualLabel)

                currentGMM = adaptGMM(currentGMM, updatePoints, actualLabel)
                allGMM.append(currentGMM)
                timestamps.append(i*0.032)

                #y_pred = testGMM(currentGMM, evalFeatures, useMajorityVote=True, showPlots=False)
            else:
                print("Query for this context class ignored.")

        # for testing:
        if len(allGMM) == 5:
            print("Stopped loop (for testing)")
            break

    # pdb.set_trace() # save allGMM here

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

def batchAL(trainedGMM, testFeatureData):
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

    updatePointsList = []

    revClassesDict = reverseDict(trainedGMM["classesDict"])

    defineThresholdDict()

    """ Simulate actual behavior by reading in points in batches of size b: """
    b = 63 # equals 2 seconds
    # disagreementList = []
    entropyList = []
    predictedLabels = []

    currentTime = 0
    prevTime = -1000000.0

    # Track entropy after the initialization
    histEntropy = []
    histEntropy.append([])
    histEntropy.append([])
    histEntropy.append([])

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


    # Number of queries asked for each class:
    numQueries = []
    numQueries.append(0)
    numQueries.append(0)
    numQueries.append(0)

    # Thresholds for the different classes:
    threshold = []
    threshold.append(-1)
    threshold.append(-1)
    threshold.append(-1)

    updatePoints = []

    # ---- for plotting only: plot max values of entropy in 1min over time ---
    # entropy buffer for points regardless of their class:
    # allEntBuffer = []
    # allEntropies = [] # max values of last min for the plot
    plotValues = []
    plotValues.append([])
    plotValues.append([])
    plotValues.append([])

    # Track entropy after the initialization
    plotEntropy = []
    plotEntropy.append([])
    plotEntropy.append([])
    plotEntropy.append([])

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

        # disagreementList.append(currentDisagreement)
        predictedLabels.append(predictedLabel)

        # ---- for plotting only: plot max values of entropy in the 30 samples buffer over time ---
        # ------------------------------------------------------------------------
        if len(plotEntropy[actualLabel]) < 30:
            plotEntropy[actualLabel].append(entropy)
        else:
            tmp = np.array(plotEntropy[actualLabel])
            plotValues[actualLabel].append(tmp.max())
            plotEntropy[actualLabel] = []
        # ------------------------------------------------------------------------

        # fill a buffer with entropy values of the last 30 samples of this class
        if thresSet[predictedLabel] == False:
            if len(histEntropy[predictedLabel]) < 30: # 30 * 2sec = 1min, 300 = 10min
                histEntropy[predictedLabel].append(entropy)
            else:
                # Buffer full:
                histEntropy[predictedLabel].append(entropy)
                # del histEntropy[predictedLabel][0] # add newest and remove oldest value to keep buffer size constant

                if initThresSet[predictedLabel] == True:
                    # set threshold:
                    tmp = np.array(histEntropy[predictedLabel])
                    threshold[predictedLabel] = tmp.max()
                    print("New threshold for " + revClassesDict[predictedLabel] + " class " +
                          str(round(threshold[predictedLabel],4)) + ". Set " + str(round(currentTime-prevTime)) + "s after model adaption")
                    thresSet[predictedLabel] = True

                else:

                    # set first threshold to 95% of max value:
                    tmp = np.array(histEntropy[predictedLabel])
                    threshold[predictedLabel] = tmp.max() * 0.95
                    print("Initial threshold for " + revClassesDict[predictedLabel] + " class " + str(round(threshold[predictedLabel],4)))
                    thresSet[predictedLabel] = True
                    initThresSet[predictedLabel] = True


        # if the buffer is filled, check if we want to query:
        if thresSet[predictedLabel] == True:
            # only query if more than 10min since the last query:
            if (currentTime - prevTime) > 60: # > 600: TODO:xxxxxxxxxxxxxx
                # check if we want to query these points and update our threshold value if we adapt the model:
                if entropy > threshold[predictedLabel]:

                    if str(revClassesDict[actualLabel]) != "Conversation" and str(revClassesDict[predictedLabel]) != "Conversation": # ignore queries that are labeled as conversation or that were predicted as conversation
                        print("Query for " + str(revClassesDict[actualLabel]) + " class (predicted as " + str(revClassesDict[predictedLabel]) + ") received at " + str(currentTime) + " seconds.")

                        # adapt the model:
                        upd = np.array(updatePoints)

                        # currentGMM = adaptGMM(currentGMM, upd, actualLabel)

                        allGMM.append(currentGMM)
                        givenLabels.append(actualLabel)
                        labelAccuracy.append(checkLabelAccuracy(simLabels[start:end], actualLabel))
                        timestamps.append(currentTime)

                        numQueries[actualLabel] += 1

                        # reset buffers:
                        histEntropy[0] = []
                        histEntropy[1] = []
                        histEntropy[2] = []
                        thresSet[0] = False
                        thresSet[1] = False
                        thresSet[2] = False

                        # # set threshold to maximal entropy of the last 10 minute:
                        # if len(histEntropy[actualLabel]) == 30: # 30 = 1min, 300 = 10min
                        #     tmp = np.array(histEntropy[actualLabel])
                        #     threshold[actualLabel] = tmp.max()
                        #     print("New threshold for " + revClassesDict[actualLabel] + " class " + str(threshold[actualLabel]))
                        #
                        #
                        # else:
                        #     tmp = np.array(histEntropy[actualLabel])
                        #     threshold[actualLabel] = tmp.max()
                        #     print("New threshold for " + revClassesDict[actualLabel] + " class " + str(threshold[actualLabel]))

                        prevTime = currentTime

                        # if sum(numQueries) == 10:
                        #     break


    print("Total number of queries: " + str(sum(numQueries)))

    # ---- for plotting only: plot max values of entropy in 1min over time ---
    pl.plot(plotValues[0])
    pl.show()
    pl.plot(plotValues[1])
    pl.show()
    pl.plot(plotValues[2])
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

    newGMM = copy.deepcopy(currentGMM)

    return results, newGMM

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

    updatePointsList = []

    revClassesDict = reverseDict(trainedGMM["classesDict"])

    defineThresholdDict()

    """ Simulate actual behavior by reading in points in batches of size b: """
    b = 63 # equals 2 seconds
    # disagreementList = []
    entropyList = []
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
    numQueries = []
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
    plotValues = []
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
    actBuffer = []

    # Single buffer containing the last 30 points regardless of the predicted class
    buffer = []

    # Our 3 min (?) buffer we use to initialized the threshold:
    initBuffer = []
    initBuffer.append([])
    initBuffer.append([])
    initBuffer.append([])

    # Our 1min buffer we use for threshold calculation after adapting the model:
    thresBuffer = []
    thresBuffer.append([])
    thresBuffer.append([])
    thresBuffer.append([])

    majCorrectCnt = 0
    majWrongCnt = 0
    
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
        if len(buffer) < 30:
            buffer.append(entropy)
            predBuffer.append(predictedLabel)
            actBuffer.append(actualLabel)
        else:
            buffer.append(entropy)
            del buffer[0]
            predBuffer.append(predictedLabel)
            del predBuffer[0]
            actBuffer.append(actualLabel)
            del actBuffer[0]


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
        if (thresSet[predictedLabel] == True) and (len(buffer) == 30): #(len(buffers[predictedLabel]) == 30)

            # --- calculate current query criteria: ---
            npCrit = np.array(buffer)
            npPred = np.array(predBuffer)
            npActual = np.array(actBuffer)

            # Majority vote on predicted labels in this 1min:
            maj = majorityVote(npPred).mean()
            iTmp = (npPred == maj)
            majPoints = npCrit[iTmp]

            # only use points that had the same predicted class in that 1min window:
            queryCrit = majPoints.mean() #  + majPoints.std() #TODO: xxxxxxxxxx

            # Majority vote on actual labels in this 1min for comparison only:
            majActual = majorityVote(npActual).mean()

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
                        buffer = []
                        predBuffer = []
                        actBuffer = []

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

                        # if sum(numQueries) == 10:
                        #     break

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

def poolAL(trainedGMM, testFeatureData):
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

    updatePointsList = []

    revClassesDict = reverseDict(trainedGMM["classesDict"])

    defineThresholdDict()

    """ Simulate actual behavior by reading in points in batches of size b: """
    b = 63 # equals 2 seconds
    # disagreementList = []
    entropyList = []
    predictedLabels = []

    currentTime = 0
    prevTime = -1000000.0

    # Track entropy after the initialization
    histEntropy = []
    histEntropy.append([])
    histEntropy.append([])
    histEntropy.append([])

    # Booleans that indicate if the initial threshold was already set:
    initThresSet = []
    initThresSet.append(False)
    initThresSet.append(False)
    initThresSet.append(False)

    # Number of queries asked for each class:
    numQueries = []
    numQueries.append(0)
    numQueries.append(0)
    numQueries.append(0)

    # Thresholds for the different classes:
    threshold = []
    threshold.append(-1)
    threshold.append(-1)
    threshold.append(-1)

    # Data pools for the different classes:
    pool = []
    pool.append([])
    pool.append([])
    pool.append([])

    updatePoints = []

    # ---- for plotting only: plot max values of entropy in 1min over time ---
    # entropy buffer for points regardless of their class:
    # allEntBuffer = []
    # allEntropies = [] # max values of last min for the plot
    # plotValues = []
    # plotValues.append([])
    # plotValues.append([])
    # plotValues.append([])

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

        # disagreementList.append(currentDisagreement)
        predictedLabels.append(predictedLabel)

        # fill a buffer with entropy values of the last 30 samples of this class to calculate an initial threshold value
        if initThresSet[predictedLabel] == False:
            if len(histEntropy[predictedLabel]) < 30: # 30 * 2sec = 1min, 300 = 10min
                histEntropy[predictedLabel].append(entropy)
            else:
                histEntropy[predictedLabel].append(entropy)
                # del histEntropy[predictedLabel][0] # add newest and remove oldest value to keep buffer size constant

                # set first threshold to 95% of max value:
                tmp = np.array(histEntropy[predictedLabel])
                threshold[predictedLabel] = tmp.max() * 0.95
                print("Initial threshold for " + revClassesDict[predictedLabel] + " class " + str(round(threshold[predictedLabel],4)))
                initThresSet[predictedLabel] = True


        # if the buffer is filled, check if we want to query:
        if initThresSet[predictedLabel] == True:
            # only query if more than 10min since the last query:
            if (currentTime - prevTime) > 600: # > 600: TODO:xxxxxxxxxxxxxx
                # check if we want to query these points and update our threshold value if we adapt the model:
                if entropy > threshold[predictedLabel]:

                    if str(revClassesDict[actualLabel]) != "Conversation" and str(revClassesDict[predictedLabel]) != "Conversation": # ignore queries that are labeled as conversation or that were predicted as conversation
                        print("Query for " + str(revClassesDict[actualLabel]) + " class (predicted as " + str(revClassesDict[predictedLabel]) + ") received at " + str(currentTime) + " seconds.")

                        # adapt the model:
                        upd = np.array(updatePoints)

                        # pdb.set_trace()

                        currentGMM = adaptGMM(currentGMM, upd, actualLabel)

                        allGMM.append(currentGMM)
                        givenLabels.append(actualLabel)
                        labelAccuracy.append(checkLabelAccuracy(simLabels[start:end], actualLabel))
                        timestamps.append(currentTime)

                        numQueries[actualLabel] += 1

                        # Add data we updated with to the pool of that class:
                        pool[actualLabel].extend(updatePoints)

                        tmpListEnt = []
                        tmpListEnt.append([])
                        tmpListEnt.append([])
                        tmpListEnt.append([])

                        # pdb.set_trace()

                        # Calculate the entropy values on the data in the pool:
                        if pool[0] != []:
                            for k in range(len(pool[0])/b):
                                tmpList = pool[0][(k*b):((k+1)*b)]

                                res, ent = predictGMM(currentGMM, np.array(tmpList), scale=False, returnEntropy=True)
                                tmpListEnt[0].append(ent)

                            # Update threshold criteria for that class:
                            tmp = np.array(tmpListEnt[0])
                            threshold[0] = tmp.max()
                            print("New threshold for " + revClassesDict[0] + " class " + str(round(threshold[0],4)))
                        # else:
                        #     # Also slightly increase threshold of the wrongly predicted class
                        #     if predictedLabel == 0 and actualLabel != 0:
                        #         threshold[predictedLabel] = threshold[predictedLabel] * 1.02
                        #         print("New threshold for " + revClassesDict[predictedLabel] + " class " + str(round(threshold[predictedLabel],4)))

                        # Calculate the entropy values on the data in the pool:
                        if pool[1] != []:
                            for k in range(len(pool[1])/b):
                                tmpList = pool[1][(k*b):((k+1)*b)]

                                res, ent = predictGMM(currentGMM, np.array(tmpList), scale=False, returnEntropy=True)
                                tmpListEnt[1].append(ent)

                            # Update threshold criteria for that class:
                            tmp = np.array(tmpListEnt[1])
                            threshold[1] = tmp.max()
                            print("New threshold for " + revClassesDict[1] + " class " + str(round(threshold[1],4)))
                        # else:
                        #     # Also slightly increase threshold of the wrongly predicted class
                        #     if predictedLabel == 1 and actualLabel != 1:
                        #         threshold[predictedLabel] = threshold[predictedLabel] * 1.02
                        #         print("New threshold for " + revClassesDict[predictedLabel] + " class " + str(round(threshold[predictedLabel],4)))

                        # Calculate the entropy values on the data in the pool:
                        if pool[2] != []:
                            for k in range(len(pool[2])/b):
                                tmpList = pool[2][(k*b):((k+1)*b)]

                                res, ent = predictGMM(currentGMM, np.array(tmpList), scale=False, returnEntropy=True)
                                tmpListEnt[2].append(ent)

                            # Update threshold criteria for that class:
                            tmp = np.array(tmpListEnt[2])
                            threshold[2] = tmp.max()
                            print("New threshold for " + revClassesDict[2] + " class " + str(round(threshold[2],4)))
                        # else:
                        #     # Also slightly increase threshold of the wrongly predicted class
                        #     if predictedLabel == 2 and actualLabel != 2:
                        #         threshold[predictedLabel] = threshold[predictedLabel] * 1.02
                        #         print("New threshold for " + revClassesDict[predictedLabel] + " class " + str(round(threshold[predictedLabel],4)))



                        prevTime = currentTime

                        # if sum(numQueries) == 10:
                        #     break

    print("Total number of queries: " + str(sum(numQueries)))

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

































