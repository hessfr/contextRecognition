import numpy as np
import math
import csv
from scipy.stats import itemfreq
import ipdb as pdb #pdb.set_trace()
from classifiers import confusionMatrixMulti

"""
Different methods to evaluate the results of the experiment

"""

def onlineAccuracy(gtLogFile, predLogFile):
    """
    Evaluate the performance of the online prediction by comparing 
    the ground truth log file to the prediction log file.
    
    @param gtLogFile:
    @param predLogFile:
    """
    
    with open(gtLogFile) as f:
        reader = csv.reader(f, delimiter="\t")
        gtListOriginal = list(reader)
    
    with open(predLogFile) as f:
        reader = csv.reader(f, delimiter="\t")
        predListOriginal = list(reader)
  
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
            #print("position in GT list: " + str(i))

    for i in range(len(predListOriginal)):
        if (predListOriginal[i][0] == "RECORDING_STARTED"):
            numRecStartedPred += 1
            recStartedListPred.append(i)
            #print("position in Pred list: " + str(i))
   
    # If the predication and the ground truth file have a different number
    # of RECORDING_STARTED entry, it is useless for use and we stop here:
    if (numRecStartedPred != numRecStartedGT):
        print("Prediction and ground truth file don't match, cannot" +
        " evaluate the accuracy:")
        print("Prediction file has " + str(numRecStartedPred) + " RECORDING_STARTED entries " +
        "and GT file has " + str(numRecStartedGT) + " RECORDING_STARTED entries")
        return None 

    
    class_name_set = []
    for el in gtListOriginal:
        if len(el) > 1:
            class_name_set.append(el[1])
    for el in predListOriginal:
        if len(el) > 1:
            class_name_set.append(el[0])
    
    class_name_set = list(set(class_name_set))
    # Create a dict to map class name to number (this is a "bidirectional" dict,
    # i.e. elements can be access by d["className"] and d[2]
    # This dict contains all classes of both, the ground truth and the prediction array
    classesDict = {}
    for i in range(len(class_name_set)):
        classesDict[class_name_set[i]] = i
        #print(class_name_set[i] + " = " + str(i))
        
    #print("-----")
    classesDict.update(dict((v, k) for k, v in classesDict.iteritems()))

    y_GT = []
    y_pred = []

    # Now create predictions and ground truth arrays from one RECORDING_STARTED
    # element to the next one:
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
            #gtList[i][0] = 0.5 * math.ceil(2.0 * float(gtList[i][0]))
            gtList[i][0] = round(2 * float(gtList[i][0]))/2

        # Find start and stop time, i.e. min and max values:
        tmpArray = np.array(gtList)

        # If there is not more entry after a RECORDING_STARTED line, do nothing:
        if tmpArray.shape[0] != 0:

            #start_time_gt = float(min(tmpArray[:,0]))
            #stop_time_gt = float(max(tmpArray[:,0]))
            start_time_gt = min(tmpArray[:,0].astype(np.float32, copy=False))
            stop_time_gt = max(tmpArray[:,0].astype(np.float32, copy=False))

            #print(tmpArray)

            #pdb.set_trace()

            y_GT_tmp = createGTArray(gtList, classesDict)

            y_pred_tmp = createPredictionArray(predList, start_time_gt, 
            stop_time_gt, len(y_GT_tmp), classesDict)

            y_GT.extend(y_GT_tmp)
            y_pred.extend(y_pred_tmp)

    y_GT = np.array(y_GT)
    y_pred = np.array(y_pred)

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

    # Calculate percentage of silence for each class:
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

    #pdb.set_trace()

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
            #predList[i][1] = 0.5 * math.ceil(2.0 * float(predList[i][1]))
            predList[i][1] = round(2 * float(predList[i][1]))/2
        if predList[i][2]:
            #predList[i][2] = 0.5 * math.ceil(2.0 * float(predList[i][2]))
            predList[i][2] = round(2 * float(predList[i][2]))/2

    length = int(length)
    #print("Length in seconds: " + str((length)/2.0))
    #print("Array length: " + str(length))

    y_pred = np.empty(length)
    y_pred.fill(-1)

    
    for line in predList:
        """ Fill array from start to end with the predicted label: """
        start = int(2 * (float(line[1]) - start_time_gt))
        if line[2] != "":
            end = int(2 * (float(line[2]) - start_time_gt))
        else:
            #print("Entry end time empty, changed to " + str(length/2.0))
            end = length - 1

        if end >= length:
            #print("End time " + str((end+2.0*start_time_gt)/2.0) + " later than last GT label")
            #print("Entry ended at " + str(end/2.0) + 
            #"s, changed end time to " + str(length/2.0))
            end = length - 1

        if start < length:
            y_pred[start:end+1].fill(classesDict[line[0]])
        else:
            pass
            #print("Entry ignore, because it started after the last entry of our GT array")

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
    #start_time = float(min(tmpArray[:,0]))
    #end_time = float(max(tmpArray[:,0]))

    start_time = min(tmpArray[:,0].astype(np.float32, copy=False))
    end_time = max(tmpArray[:,0].astype(np.float32, copy=False))

    #print("start_time: " + str(start_time))
    #print("end_time: " + str(end_time))

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

            #print("i: " + str(i) + " " + str(gtList[i][1]))

            # Find the end time of this context:
            for j in range(i,len(gtList)):

                #print("j: " + str(j))

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








