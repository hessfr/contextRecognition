import numpy as np
import math
import csv
from scipy.stats import itemfreq
import ipdb as pdb
from classifiers import confusionMatrixMulti

def onlineAccuracy(gtLogFile, predLogFile):
    """
    Evaluate the performance of the online prediction by comparing 
    the ground truth log file to the prediction log file.
    
    @param gtLogFile:
    @param predLogFile:
    """

    classesToIgnore = ["Home"]
    #classesToIgnore = []

    # Syntax: key = old class name, value = new class name:
    classesToRename = {"Cycling": "Street"}
    #classesToRename = {}

    # Syntax key = new class name, value: list of old classes that should be merged:
    #classesToMerge = {"Transport": ["Car", "Bus", "Train", "Tram"]}
    classesToMerge = {}

    with open(gtLogFile) as f:
        reader = csv.reader(f, delimiter="\t")
        gtListOriginal = list(reader)
    
    with open(predLogFile) as f:
        reader = csv.reader(f, delimiter="\t")
        predListOriginal = list(reader)
  
    # These classes will be completely removed from the prediction and the ground truth lists:
    for ignoreClass in classesToIgnore:
        # Remove every class that should be ignored:
        gtListOriginal = [el for el in gtListOriginal if ignoreClass not in el]
        predListOriginal = [el for el in predListOriginal if ignoreClass not in el]

    # Rename equivalent classes, so that both classes will be treated as one:
    for i in range(len(gtListOriginal)):
        for j in range(len(gtListOriginal[i])):
            if gtListOriginal[i][j] in classesToRename.keys():
                gtListOriginal[i][j] = classesToRename[gtListOriginal[i][j]]
    for i in range(len(predListOriginal)):
        for j in range(len(predListOriginal[i])):
            if predListOriginal[i][j] in classesToRename.keys():
                predListOriginal[i][j] = classesToRename[predListOriginal[i][j]]

    # Merge multiple classes into a new class:
    for i in range(len(gtListOriginal)):
        for j in range(len(gtListOriginal[i])):
            for k in range(len(classesToMerge.values())):
                if gtListOriginal[i][j] in classesToMerge.values()[k]:
                    gtListOriginal[i][j] = classesToMerge.keys()[k]
    for i in range(len(predListOriginal)):
        for j in range(len(predListOriginal[i])):
            for k in range(len(classesToMerge.values())):
                if predListOriginal[i][j] in classesToMerge.values()[k]:
                    predListOriginal[i][j] = classesToMerge.keys()[k]

    numRecStartedGT = 0
    numRecStartedPred = 0

    # List containing the indices of all RECORDING_STARTED entries in the
    # GT and the prediction log file
    recStartedListGT = []
    recStartedListPred = []

    for i in range(len(gtListOriginal)):
        if len(gtListOriginal[i]) <= 1:
            numRecStartedGT += 1
            recStartedListGT.append(i)

    for i in range(len(predListOriginal)):
        if (predListOriginal[i][0] == "RECORDING_STARTED"):
            numRecStartedPred += 1
            recStartedListPred.append(i)
   
    # If the predication and the ground truth file have a different number
    # of RECORDING_STARTED entry, it is useless for use and we stop here:
    if (numRecStartedPred != numRecStartedGT):
        print("Prediction and ground truth file don't match, cannot" +
        " evaluate the accuracy:")
        print("Prediction file has " + str(numRecStartedPred) + " RECORDING_STARTED entries " +
        "and GT file has " + str(numRecStartedGT) + " RECORDING_STARTED entries")
        return None 


    classesDict = createClassesDict(gtListOriginal, predListOriginal)

    y_GT = []
    y_pred = []

    # Now create predictions and ground truth arrays from one RECORDING_STARTED
    # entry the until the next one:
    for k in range(numRecStartedPred):

        tmpGT = np.array(gtListOriginal)
        tmpPred = np.array(predListOriginal)

        startIdxGT = recStartedListGT[k]+1
        startIdxPred = recStartedListPred[k]+1
        if (k < (numRecStartedPred-1)):
            endIdxGT = recStartedListGT[k+1]
            endIdxPred = recStartedListPred[k+1]
        else:
            endIdxGT = len(gtListOriginal)
            endIdxPred = len(predListOriginal)

        gtList = list(tmpGT[startIdxGT:endIdxGT])
        predList = list(tmpPred[startIdxPred:endIdxPred])
       
        # Round every entry to 0.5s:
        for i in range(len(gtList)):
            try:
                gtList[i][0] = round(2 * float(gtList[i][0]))/2
            except:
                pdb.set_trace()

        # Find start and stop time, i.e. min and max values:
        tmpArray = np.array(gtList)

        # If there is not more entry after a RECORDING_STARTED line, do nothing:
        if tmpArray.shape[0] != 0:

            start_time_gt = min(tmpArray[:,0].astype(np.float32, copy=False))
            stop_time_gt = max(tmpArray[:,0].astype(np.float32, copy=False))

            y_GT_tmp = createGTArray(gtList, classesDict)

            y_pred_tmp = createPredictionArray(predList, start_time_gt, 
            stop_time_gt, len(y_GT_tmp), classesDict)

            y_GT.extend(y_GT_tmp)
            y_pred.extend(y_pred_tmp)

    y_GT = np.array(y_GT)
    y_pred = np.array(y_pred)

    y_gt_ravel = y_GT.ravel()
    y_gt_ravel = y_gt_ravel[y_gt_ravel != -1]
    freq = itemfreq(y_gt_ravel)
    n_entries = sum(freq[:,1])
    
    print("--- GT distribution: ---")
    for i in range(freq.shape[0]):
        perc = round( 100 * (freq[i,1] / n_entries), 1)
        print(classesDict[int(freq[i,0])] + " " + str(perc) + "%")
    print("------")

    # Whenever silence is predicted, we ignore those parts for the calculation 
    # of the accuracy, i.e. we delete those entries from the GT and the
    # prediction array:

    silenceClassNum = classesDict["silence"]

    # Calculate how many % of the predicitions are silent for each classes, 
    # only points where GT provided will be considered here:
    freq = itemfreq(y_pred).astype(int)

    silenceCount = freq[np.where(freq[:,0] == silenceClassNum)[0][0], 1]
    totalCount = freq[:,1].sum()
    
    print("In total " + str(round(silenceCount/ (float(totalCount)), 2) * 100) + 
    "% of all considered samples were silent")
    print("-----")

    # Calculate how many percent of the samples are silent for each class in the ground truth:
    silencePerClass(y_pred, y_GT, classesDict, silenceClassNum)

    # Remove points that were silent or where no ground truth was provided:
    y_pred, y_GT = removeInvalids(y_pred, y_GT, silenceClassNum)

    # Calculate the overall accuracy and print it:
    correctPred = 0
    for i in range(y_pred.shape[0]):
        if y_pred[i] in y_GT[i,:]:
            correctPred += 1
    
    accuracy = correctPred / float(y_pred.shape[0])
    print("Overall accuracy: " + str(round(accuracy*100,2)) + "%")
    print("-----")

    # The method to plot the confusion matrix, needs a classes dictionary, 
    # that is NOT bidirectional, so we remove all elements, where the keys
    # are numbers:
    uniDirectionalClassesDict = {}
    for key in classesDict.keys():
        if type(key) is str:
            uniDirectionalClassesDict[key] = classesDict[key]

    # Adjust the classesDict by removing the entry for silence:
    if "silence" in uniDirectionalClassesDict.keys():
        silenceNumber = uniDirectionalClassesDict["silence"]
        # Decrement numbers after the the position where silence was:
        for key in uniDirectionalClassesDict.keys():
            if uniDirectionalClassesDict[key] > silenceNumber:
                uniDirectionalClassesDict[key] = uniDirectionalClassesDict[key] - 1
        
        # Delete the silence entry:
        del uniDirectionalClassesDict["silence"]

    # Adjust the ground truth and the prediction array, by decrementing class 
    # numbers, larger than, the silence class number:
    for i in range(silenceNumber+1, len(uniDirectionalClassesDict)+1):
        y_GT[y_GT == i] = i-1
        y_pred[y_pred == i] = i-1

    confusionMatrixMulti(y_GT, y_pred, uniDirectionalClassesDict)

def createPredictionArray(predList, start_time_gt, stop_time_gt, length, classesDict):
        
    """
    Create a numpy array of the predictions for 0.5s long windows

    @param predList: List in this format: [context_class, start_time, end_time], does not contain RECORDING_STARTED entries etc.
    @param stop_time_gt: Offset in seconds describing when the first entry in the ground truth array is was provided.
    @param stop_time_gt: last timestamp in the GT array
    @param length: Length of the ground truth array
    @param classesDict: "Bidirectional" dict containg mapping from class name to numbers and vic    e versa
    @return: Numpy array of the predicted labels

    """

    offset = start_time_gt
    #print("offset: " + str(offset))

    # First delete all entries that were before the first ground truth label was provided:
    for i in reversed(range(len(predList))):
        # Delete all entry, that are completely before the offset:
        if predList[i][2]:
            if float(predList[i][2]) < offset:
                del predList[i]           
        
        # Now adjust the start time of those entries, that started before the offset, but
        # finished after the offset:
        if predList[i][1]:
            if float(predList[i][1]) < offset:
                predList[i][1] = offset
   
    # Round every entry to 0.5s:
    for i in range(len(predList)):
        if predList[i][1]:
            predList[i][1] = round(2 * float(predList[i][1]))/2
        if predList[i][2]:
            predList[i][2] = round(2 * float(predList[i][2]))/2

    length = int(length)
    y_pred = np.empty(length)
    y_pred.fill(-1)

    
    for line in predList:
        """ Fill array from start to end with the predicted label: """
        start = int(2 * (float(line[1]) - start_time_gt))
        if line[2] != "":
            end = int(2 * (float(line[2]) - start_time_gt))
        else:
            end = length - 1

        if end >= length:
            end = length - 1

        if start < length:
            y_pred[start:end+1].fill(classesDict[line[0]])
        else:
            pass

    return np.array(y_pred)

    

def createGTArray(gtList, classesDict):
    """
    Create the numpy ground truth array for 0.5s long windows
    The maximum number of simultatious ground thruth labels per points is 5

    @param gtList: List containg lines in the following format: [timestamp, context_class, "start"/"end"] and no RECORDING_STARTED entries etc. 
    @param classesDict: "Bidirectional" dict containg mapping from class name to numbers and vice versa
    @return: Numpy array of the ground truth
    """

    n_max_labels=5

    # Find start and stop time, i.e. min and max values:
    tmpArray = np.array(gtList)

    start_time = min(tmpArray[:,0].astype(np.float32, copy=False))
    end_time = max(tmpArray[:,0].astype(np.float32, copy=False))

    # We want ignore invalid (no class assigned) values, before the first class is
    # assigned, so we have to subtract the start_time offset later

    # Create a ground truth array where one entry corresponds to 0.5s:
    length = end_time - start_time
    y_GT = np.empty([int(length*2), n_max_labels])
    y_GT.fill(-1)
    for i in range(len(gtList)):
        """ Fill array from start to end of each ground truth label with the correct label: """
        if gtList[i][2] == "start":
            tmpContext = gtList[i][1]

            start = int(2 * (gtList[i][0] - start_time))

            # Find the end time of this context:
            for j in range(i,len(gtList)):

                if ((gtList[j][1] == tmpContext) and (gtList[j][2] == "end")):

                    end = int(2 * (gtList[j][0] - start_time))
                    
                    if end > y_GT.shape[0]:
                        print("Problem when calculating GT array: index too large")
                        end = y_GT.shape[0]

                    if start == end:
                        # GT annotation was less than 0.5s, so we just ignore it
                        break
                    
                    # Check if we can write into the first column of the y_GT array:
                    if ((len(np.unique(y_GT[start:end,0])) == 1) and 
                    (np.unique(y_GT[start:end,0])[0] == -1)):

                        y_GT[start:end,0].fill(classesDict[gtList[i][1]])

                    # Check if we can write into the second column of the y_GT array:
                    elif ((len(np.unique(y_GT[start:end,1])) == 1) and 
                    (np.unique(y_GT[start:end,1])[0] == -1)):

                        y_GT[start:end,1].fill(classesDict[gtList[i][1]])
               
                    # Check if we can write into the third column of the y_GT array:
                    elif ((len(np.unique(y_GT[start:end,2])) == 1) and 
                    (np.unique(y_GT[start:end,2])[0] == -1)):

                        y_GT[start:end,2].fill(classesDict[gtList[i][1]])
                    
                    # Check if we can write into the third column of the y_GT array:
                    elif ((len(np.unique(y_GT[start:end,3])) == 1) and 
                    (np.unique(y_GT[start:end,3])[0] == -1)):

                        y_GT[start:end,3].fill(classesDict[gtList[i][1]])
                    
                    # Check if we can write into the third column of the y_GT array:
                    elif ((len(np.unique(y_GT[start:end,4])) == 1) and 
                    (np.unique(y_GT[start:end,4])[0] == -1)):

                        y_GT[start:end,4].fill(classesDict[gtList[i][1]])
                    
                    else:
                        print("Problem occurred when filling ground truth array in line " + 
                        "Maybe you are using more than 3 simultaneous context classes?")

                    break
    return y_GT

def createClassesDict(gtList, predList):
    """
    Create a dict to map class name to number (this is a "bidirectional" dict,
    i.e. elements can be access by d["className"] and d[2]
    This dict contains all classes of both, the ground truth and the prediction array
    """

    class_name_set = []
    for el in gtList:
        if len(el) > 1:
            class_name_set.append(el[1])
    for el in predList:
        if len(el) > 1:
            class_name_set.append(el[0])
    
    class_name_set = list(set(class_name_set))

    classesDict = {}
    for i in range(len(class_name_set)):
        classesDict[class_name_set[i]] = i
        
    classesDict.update(dict((v, k) for k, v in classesDict.iteritems()))

    return classesDict

def silencePerClass(y_pred, y_GT, classesDict, silenceClassNum):
    """
    Calculate and print percentage of silence for each class

    """
    silenceCountPerClass = np.zeros(len(classesDict)/2)
    
    for i in range(y_pred.shape[0]):
        if y_pred[i] == silenceClassNum:
            # increment each class that was the ground truth for that point: 
            for j in range(y_GT.shape[1]):
                if int(y_GT[i,j]) != -1:
                    # ignore invalid (-1) class entries
                    silenceCountPerClass[int(y_GT[i,j])] += 1
    
    gtFreq = itemfreq(y_GT.flat).astype(int)

    for i in range(silenceCountPerClass.shape[0]):
        if i in gtFreq[:,0]:
            silencePercentage = round(silenceCountPerClass[i] / float(
            gtFreq[np.where(gtFreq[:,0] == i)[0][0], 1]) * 100, 2)
            print("Class " + classesDict[i] + " contains " + str(silencePercentage) + 
            "% silence")
    
    print("-----")

def removeInvalids(y_pred, y_GT, silenceClassNum):
    """
    Remove all points from the prediction and the ground truth array where
    not ground truth was provided or where the point was silent
    
    @return: y_pred, y_GT: the updated arrays
    """
    # Positions where no silence predicted:
    maskValid = (y_pred != silenceClassNum)

    y_GT = y_GT[maskValid]
    y_pred = y_pred[maskValid]

    # We also ignore those samples where not ground truth was provided, 
    # i.e. we delete those entries from the GT and the prediction array:
    invalidRow = np.array([-1,-1,-1,-1,-1]) # has to match the max allow GT labels per points
    maskValid = ~np.all(y_GT==invalidRow,axis=1)

    y_GT = y_GT[maskValid]
    y_pred = y_pred[maskValid]

    # Print for how many points no ground truth was provided:
    noGtFreq = itemfreq(maskValid).astype(int)
    validCount = noGtFreq[np.where(noGtFreq[:,0] == 1)[0][0], 1]
    totalCount = sum(noGtFreq[:,1])
    percentValid = round(validCount/float(totalCount) * 100, 2)

    print("GT was provided for " + str(percentValid) + "% of all (non-silent) samples")
    print("-----")

    return y_pred, y_GT

