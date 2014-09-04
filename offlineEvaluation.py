import numpy as np
import math
import csv
import pickle
import json
from scipy.stats import itemfreq
import ipdb as pdb #pdb.set_trace()
from classifiers import getIndex, confusionMatrixMulti, logProb

def offlineAccuracy(gmm, jsonFileList, gtLogFile):
    """
    Test classifier on data recorded in the experiment. The features used have to be extracted
    from the individual parts of the file and not from the whole file at once. 
    
    @param gmm: GMM classifier object
    @param jsonFileList: List of files containing the extracted features for the
    indivdual parts of the file.
    @param gtLogFile: Text file containing the ground truth. Individual parts are separated
    by RECORDING_STARTED entries
    """

    with open(gtLogFile) as f:
        reader = csv.reader(f, delimiter="\t")
        gtListOriginal = list(reader)

    #TODO: ignore, rename or merge classes
    
    # These classes will be completely removed from the ground truth lists:
    #classesToIgnore = ["Home"]
    #for ignoreClass in classesToIgnore:
    #    # Remove every class that should be ignored:
    #    gtListOriginal = [el for el in gtListOriginal if ignoreClass not in el]

    # List containing the indices of all RECORDING_STARTED entries
    recStartedList = []
    for i in range(len(gtListOriginal)):
        if len(gtListOriginal[i]) <= 1:
            recStartedList.append(i)

    # The number of given feature file has to match the number of RECORDING_STARTED entries:
    if (len(recStartedList) != len(jsonFileList)):
        print("Ground truth file does not match the number of provided feature files " 
        + "evaluation will be stopped: ")
        print(str(len(jsonFileList)) + " feature files were provided, but ground truth " + 
        "file contains only " + str(len(recStartedList)) + " RECORDING_STARTED entries")
        return None

    y_pred = []
    y_gt = []
    # Make prediction and compare it to GT for each RECORDING_STARTED entry to the next one:
    for k in range(len(jsonFileList)):
        
        silenceClassNum = max(gmm["classesDict"].values())+1
        y_pred_tmp = createPrediction(gmm, jsonFileList[k], silenceClassNum)
        y_pred_tmp = y_pred_tmp.tolist()

        tmpGT = np.array(gtListOriginal)
        startIdx = recStartedList[k]+1

        if (k < (len(recStartedList)-1)):
            endIdx = recStartedList[k+1]
        else:
            endIdx = len(gtListOriginal)

        gtList = list(tmpGT[startIdx:endIdx])
        y_gt_tmp = createGTMulti(gmm["classesDict"], len(y_pred_tmp), gtList)
        y_gt_tmp = y_gt_tmp.tolist()

        y_pred.extend(y_pred_tmp)
        y_gt.extend(y_gt_tmp)

        #pdb.set_trace()

    y_gt = np.array(y_gt)
    y_pred = np.array(y_pred)
    
    # Delete invalid rows:
    invalidRow = np.array([-1,-1,-1,-1,-1])
    maskValid = ~np.all(y_gt==invalidRow,axis=1)
    y_gt = y_gt[maskValid]
    y_pred = y_pred[maskValid]
  
    # Calculate how many percent of the samples are silent and delete silent samples from
    # y_gt and y_pred:
    maskNonSilent = (y_pred != silenceClassNum)
    numSilentSamples = np.sum(~maskNonSilent)
    silentPercentage = numSilentSamples / float(y_pred.shape[0])
    print(str(round(silentPercentage*100,2)) + "% percent of all samples are silent")

    y_gt = y_gt[maskNonSilent]
    y_pred = y_pred[maskNonSilent]

    # Calculate the overall accuracy and print it:
    correctPred = 0
    for i in range(y_pred.shape[0]):
        if y_pred[i] in y_gt[i,:]:
            correctPred += 1

    accuracy = correctPred / float(y_pred.shape[0])
    print("Overall accuracy: " + str(round(accuracy*100,2)) + "%")
    print("-----")

    confusionMatrixMulti(y_gt, y_pred, gmm["classesDict"])


def createGTMulti(classesDict, length, gtList):
    """
    Create ground truth array that allows multiple labels per point
    
    @param classesDict:
    @param length: length of the final array (=length of prediction array)
    @param gtList:
    @return:
    """

    """ Create array containing label for sample point: """
    n_maxLabels = 5 #maximum number of labels that can be assign to one point
    y_GT = np.empty([length,n_maxLabels])
    y_GT.fill(-1) #-1 corresponds to no label given

    classesNotTrained = []
    for i in range(len(gtList)):
        """ Fill array from start to end of each ground truth label with the correct label: """
        if gtList[i][2] == "start":
            tmpContext = gtList[i][1]
            start = getIndex(float(gtList[i][0]))

            # Find the end time of this context:
            for j in range(i,len(gtList)):
                if ((gtList[j][1] == tmpContext) and (gtList[j][2] == "end")):

                    end = getIndex(float(gtList[j][0]))
                    if end >= y_GT.shape[0]:
                        end = y_GT.shape[0] - 1

                    """ Fill ground truth array, and check if our classifier was 
                    trained with all labels of the test file, if not give warning: """

                    if (gtList[i][1] not in classesDict.keys()):
                        classesNotTrained.append(gtList[i][1])
                    
                    else:
                        
                        # Check if we can write into the first column of the y_GT array:
                        if ((len(np.unique(y_GT[start:end+1,0])) == 1) and 
                        (np.unique(y_GT[start:end+1,0])[0] == -1)):

                            y_GT[start:end+1,0].fill(classesDict[gtList[i][1]])

                        # Check if we can write into the second column of the y_GT array:
                        elif ((len(np.unique(y_GT[start:end+1,1])) == 1) and 
                        (np.unique(y_GT[start:end+1,1])[0] == -1)):

                            y_GT[start:end+1,1].fill(classesDict[gtList[i][1]])
                       
                        # Check if we can write into the third column of the y_GT array:
                        elif ((len(np.unique(y_GT[start:end+1,2])) == 1) and 
                        (np.unique(y_GT[start:end+1,2])[0] == -1)):

                            y_GT[start:end+1,2].fill(classesDict[gtList[i][1]])
                        
                        # Check if we can write into the third column of the y_GT array:
                        elif ((len(np.unique(y_GT[start:end+1,3])) == 1) and 
                        (np.unique(y_GT[start:end+1,3])[0] == -1)):

                            y_GT[start:end+1,3].fill(classesDict[gtList[i][1]])
                        
                        # Check if we can write into the third column of the y_GT array:
                        elif ((len(np.unique(y_GT[start:end+1,4])) == 1) and 
                        (np.unique(y_GT[start:end+1,4])[0] == -1)):

                            y_GT[start:end+1,4].fill(classesDict[gtList[i][1]])
                        
                        else:
                            print("Problem occurred when filling ground truth array." +  
                            "Maybe you are using more than 3 simultaneous context classes?")
                    break
    
    if classesNotTrained:
        for el in set(classesNotTrained):
            print("The classifier wasn't trained with class '" + 
            el + "'. It will not be considered for testing.")
    return y_GT

def createPrediction(trainedGMM, jsonFile, silenceClassNum):
    """
    Create prediction with a 2s majority vote. Like in the Android app, 2s windows where all
    amplitude values are below the threshold will be ignored, and no prediction will be made.

    @param trainedGMM: already trained GMM
    @param jsonFile: path the json file that contains the features and amplitude values. This
    file has to have the features under the key "features" and the amplitude values under the
    key "amps"
    @param silenceClassNum: The class number to which silent sequences will be assigned
    """
    n_classes = len(trainedGMM['clfs'])

    jsonData = json.load(open(jsonFile, "rb"))
    featureData = np.array(jsonData["features"])
    amps = np.array(jsonData["amps"])

    X_test = trainedGMM['scaler'].transform(featureData)

    logLikelihood = np.zeros((n_classes, X_test.shape[0]))

    """ Compute log-probability for each class for all points: """
    for i in range(n_classes):

        logLikelihood[i] = logProb(X_test, trainedGMM['clfs'][i].weights_,
        trainedGMM['clfs'][i].means_, trainedGMM['clfs'][i].covars_)

    """ Select the class with the highest log-probability: """
    y_pred = np.argmax(logLikelihood, 0)

    return majorityVoteSilence(y_pred, amps, silenceClassNum)

def majorityVoteSilence(y_Raw, amps, silenceClassNum):
    """
    The method first checks for every 2s windows, if all amplitude values lie below
    the silence threshold and returns silences for those interval.
    After that a majority vote of 2s length will be applied.
    
    @param y_Raw: Input data as 1D numpy array
    @param amps: Max amplitude values as 1D numpy array. Same size as y_Raw
    @param silenceClassNum: The class number to which silent sequences will be assigned
    @return: Result of the same size as input
    """
    y_raw = y_Raw.copy()
    silenceThreshold = 1000
    majVotWindowLength = 2.0 #in seconds
    windowLength = 0.032
    frameLengthFloat = math.ceil(majVotWindowLength/windowLength)

    frameLength = int(frameLengthFloat)

    resArray = np.empty(y_raw.shape)

    n_frames = int(math.ceil(y_raw.shape[0]/frameLengthFloat))

    for i in range(n_frames):

        if ((i+1) * frameLength) < y_raw.shape[0]:

            tmpAmps = amps[(i * frameLength):(((i+1) * frameLength))]
           
            #if tmpAmps.max() >= silenceThreshold:
            if True:
                tmpArray = y_raw[(i * frameLength):(((i+1) * frameLength))]
                
                """ Get most frequent number in that frames: """
                count = np.bincount(tmpArray)
                tmpMostFrequent = np.argmax(count)

                """ Fill all elements with most frequent number: """
                tmpArray.fill(tmpMostFrequent)

                """ Write it into our result array: """
                resArray[(i * frameLength):(((i+1) * frameLength))] = tmpArray
            
            else:
                """If all amplitudes are below threshold, the 
                sample is considered silent:"""            
                resArray[(i * frameLength):(((i+1) * frameLength))] = silenceClassNum
        else:

            tmpAmps = amps[(i * frameLength):y_raw.shape[0]]


            #if tmpAmps.max() >= silenceThreshold: 
            if True:
                tmpArray = y_raw[(i * frameLength):y_raw.shape[0]]
                """ Get most frequent number in that frames and fill 
                all elements in the frame with it: """
                count = np.bincount(tmpArray)
                tmpMostFrequent = np.argmax(count)

                """ Fill all elements with most frequent number: """
                tmpArray.fill(tmpMostFrequent)

                """ Write it into our result array: """
                resArray[(i * frameLength):y_raw.shape[0]] = tmpArray
            
            else:
                """If all amplitudes are below threshold, the 
                sample is considered silent:"""            
                resArray[(i * frameLength):y_raw.shape[0]] = silenceClassNum

    return resArray

def createGTUnique(classesDict, length, gtList):
    """
    Create ground truth array where only one label is allowed per point

    @param classesDict:
    @param length: length of the final array (=length of prediction array)
    @param gtList:
    @return:
    """

    y_GT = np.empty([length])
    y_GT.fill(-1) #-1 corresponds to no label given

    classesNotTrained = []
    for i in range(len(gtList)):
        """ Fill array from start to end of each ground truth label with the correct label: """
        if gtList[i][2] == "start":
            tmpContext = gtList[i][1]
            start = getIndex(float(gtList[i][0]))

            # Find the end time of this context:
            for j in range(i,len(gtList)):
                if ((gtList[j][1] == tmpContext) and (gtList[j][2] == "end")):

                    end = getIndex(float(gtList[j][0]))
                    if end >= y_GT.shape[0]:
                        end = y_GT.shape[0] - 1

                    """ Fill ground truth array, and check if our classifier was 
                    trained with all labels of the test file, if not give warning: """

                    if gtList[i][1] not in classesDict.keys():
                        classesNotTrained.append(gtList[i][1])
                        y_GT[start:end+1].fill(-1)
                    
                    else:
                        y_GT[start:end+1].fill(classesDict[tmpContext])
                    
                    break
    
    if classesNotTrained:
        for el in set(classesNotTrained):
            print("The classifier wasn't trained with class '" + 
            el + "'. It will not be considered for testing.")
    return y_GT



