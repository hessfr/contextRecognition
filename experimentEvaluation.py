import numpy as np
import math
import csv

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
        gtList = list(reader)
    
    with open(predLogFile) as f:
        reader = csv.reader(f, delimiter="\t")
        predList = list(reader)
    
    #TODO: handle if multiple RECORDING_STARTED entries in the same log file
    
    n_maxLabels = 3 #maximum number of labels that can be assign to one point
  
    for i in reversed(range(len(gtList))):
        # Don't consider the RECORDING_STARTED entries for now:
        if len(gtList[i]) <= 1:
            del gtList[i]           
  
    class_name_set = []
    for el in gtList:
        class_name_set.append(el[1])
    class_name_set = list(set(class_name_set))
    
    # Create a dict to map class name to number (this is a "bidirectional" dict,
    # i.e. elements can be access by d["className"] and d[2]
    classesDict = {}
    for i in range(len(class_name_set)):
        classesDict[class_name_set[i]] = i
        print(class_name_set[i] + " = " + str(i))
    classesDict.update(dict((v, k) for k, v in classesDict.iteritems()))

    # Round every entry to 0.5s:
    for i in range(len(gtList)):
        gtList[i][0] = 0.5 * math.ceil(2.0 * float(gtList[i][0]))

    # Find start and stop time, i.e. min and max values:
    tmpArray = np.array(gtList)
    start_time = float(min(tmpArray[:,0]))
    end_time = float(max(tmpArray[:,0]))
    
    # Create a ground truth array where one entry corresponds to 0.5s:
    length = end_time - start_time + 1
    y_GT = np.empty([int(length*2), n_maxLabels])
    y_GT.fill(-1)
    for i in range(len(gtList)):
        """ Fill array from start to end of each ground truth label with the correct label: """
        if gtList[i][2] == "start":
            tmpContext = gtList[i][1]
            start = 2 * int(gtList[i][0]) - int(2*start_time)

            # Find the end time of this context:
            for j in range(i,len(gtList)):

                if ((gtList[j][1] == tmpContext) and (gtList[j][2] == "end")):

                    end = 2 * int(gtList[j][0]) - int(2*start_time)
                    if end >= y_GT.shape[0]:
                        print("xxxxxxxxx " + str(end))
                        end = y_GT.shape[0] - 1

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
                    
                    else:
                        print("Problem occurred when filling ground truth array." +  
                        "Maybe you are using more than 3 simultaneous context classes?")
                    
                    break

    return y_GT








