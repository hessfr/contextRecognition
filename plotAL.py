import numpy as np
import pickle
import matplotlib.pyplot as pl
import ipdb as pdb

def plotAL(results):
    """
    Plot accuracies, F1 scores, time stamps of model adaption and accuracy of the 
    points incorporated (bar graphs)

    @param results: Dictionary containing the results of the active learning simulation,
    created by simulateAL.py
    """

    accuracy = []
    classesDict = results[0]["classesDict"]
    revClassesDict = reverseDict(results[0]["classesDict"])

    F1list = []

    timestamps = []
    labels = []
    labels.append("")
    labelAccuracy = []
    labelAccuracy.append([-0.5 , -0.5, -0.5])
    duration = results[0]["duration"]
    classesInGT = results[0]["classesInGT"]

    for el in results:
        accuracy.append(el["accuracy"])
       
        
        tmp = []
        for i in range(len(classesDict)):
            # Only append classes that are in the ground truth:
            if i in classesInGT:
                if np.isnan(el["F1dict"][revClassesDict[i]]):
                    tmp.append(0)
                else:
                    tmp.append(el["F1dict"][revClassesDict[i]])

        F1list.append(tmp)

        timestamps.append(el["timestamp"])
        maxLength = 9 # Max number of characters in a label (for plotting only)
        if el["label"] != -1:
            labels.append(revClassesDict[el["label"]][0:maxLength])
        if el["labelAccuracy"] != [-1, -1, -1]:
            labelAccuracy.append(el["labelAccuracy"])

    F1array = np.array(F1list)

    idx = range(len(accuracy))

    fig = pl.figure()
    ax = pl.subplot(1,1,1)
    pl.title("Time when the queries were sent")
    ax.scatter(np.array(timestamps), np.zeros(len(timestamps)), s=80, marker="o")
    pl.xlim(0, duration)
    pl.xlabel("Time in seconds")
    ax.set_yticks([])
    #pl.show()
    fig.savefig("plotsTmp/time.jpg")

    fig = pl.figure()
    pl.plot(idx, accuracy, label="Accuracy")
    pl.title("Total accuracy")
    pl.xlabel('number of queries')
    pl.xticks(range(len(labels)),labels, rotation=45)
    #pl.show()
    fig.savefig("plotsTmp/accuracy.jpg")
    
    fig = pl.figure()
    j=0
    for i in range(len(classesDict)):
        if i in classesInGT:
            pl.plot(idx, F1array[:,j], label=revClassesDict[i])
            j += 1
    
    ax = pl.subplot(111)
    box = ax.get_position()
    ax.set_position([box.x0, box.y0, box.width * 0.8, box.height])

    pl.title("F1 of individual classes")
    pl.xticks(range(len(labels)),labels, rotation=45)
    ax.legend = pl.legend(loc='center left', bbox_to_anchor=(1, 0.5))
    fig.savefig("plotsTmp/F1s.jpg", bbox_inches='tight')
    #pl.show()

    fig, ax = pl.subplots()   
    width = 0.175 # the width of the bars

    correct_last_min = []
    used_for_model_adaption = []
    wrong_points_incorporated = []
    
    label_accuracy = np.array(labelAccuracy)
    label_accuracy += 0.5
    correct_last_min = label_accuracy[:,0]
    used_for_model_adaption  = label_accuracy[:,1]
    wrong_points_incorporated = label_accuracy[:,2]
    
    #correct_last_min = [(el[0]+0.5) for el[0] in labelAccuracy]
    rects1 = ax.bar(idx, correct_last_min, width, color='b')
    
    #used_for_model_adaption = [(el[1]+0.5) for el[1] in labelAccuracy]
    rects2 = ax.bar([(el+width) for el in idx], used_for_model_adaption, width, color='g')

    #wrong_points_incorporated = [(el[2]+0.5) for el[2] in labelAccuracy]
    rects3 = ax.bar([(el+2*width) for el in idx], wrong_points_incorporated, width, color='r')
    
    pl.title("Accuracy of labels")
    pl.xlabel('number of queries')
    pl.xticks([(n + 1.5*width) for n in range(len(labels))],labels, rotation=45)
    pl.ylim([0,102])

    ax.legend((rects1[0], rects2[0], rects3[0]), 
    ('% labels correct in last min', 
    '% of points in last min used for model adaption', 
    '% of incorporated points have wrong label'),
    prop={'size':8},
    loc='lower right')
    
    fig.savefig("plotsTmp/labelAccuracy.jpg")

def reverseDict(oldDict):
    """
    Return new array were keys are the values of the old array and the other way around

    """
    newDict = {}
    for i, j in zip(oldDict.keys(), oldDict.values()):
        newDict[j] = i

    return newDict
