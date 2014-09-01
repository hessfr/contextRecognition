import numpy as np
import math
import csv
import pickle
import json
from scipy.stats import itemfreq
import ipdb as pdb #pdb.set_trace()
from classifiers import testGMM, getIndex, confusionMatrixMulti

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
    classesToIgnore = ["Home"]
    for ignoreClass in classesToIgnore:
        # Remove every class that should be ignored:
        gtListOriginal = [el for el in gtListOriginal if ignoreClass not in el]

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
        
        features = np.array(json.load(open(jsonFileList[k],"rb")))

        y_pred_tmp = testGMM(gmm, features)
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
   
    # Calculate the overall accuracy and print it:
    correctPred = 0
    for i in range(y_pred.shape[0]):
        if y_pred[i] in y_gt[i,:]:
            correctPred += 1

    pdb.set_trace()

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






