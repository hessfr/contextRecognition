import numpy as np
import pickle
import matplotlib.pyplot as pl
from simulateAL import reverseDict
import ipdb as pdb #pdb.set_trace()

def plotAL(results):
    """

    @param results: Dictionary containing the results of the active learning simulation,
    created by simulateAL.py
    """

    accuracy = []
    classesDict = results[0]["classesDict"]
    revClassesDict = reverseDict(results[0]["classesDict"])

    F1list = []
    #for el in classesDict:
    #    F1list.append([])


    #Conversation = []
    #Office = []
    #TrainInside = []

    timestamps = []
    labels = []
    labels.append("")
    labelAccuracy = []
    labelAccuracy.append(1)
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
        if el["label"] != -1:
            labels.append(revClassesDict[el["label"]])
        if el["labelAccuracy"] != -1:
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

    #ax.grid(True)
    #ax.set_xticklabels([])

    fig = pl.figure()
    pl.plot(idx, accuracy, label="Accuracy")
    #pl.plot(idx, labelAccuracy,label="Label correctness")
    pl.title("Total accuracy")
    pl.xlabel('number of queries')
    pl.xticks(range(len(labels)),labels, rotation=45)
    #legend = pl.legend(loc='upper left', shadow=True)
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

    """
    pl.plot(idx, labelAccuracy, label="LabelAccuracy")
    #pl.plot(idx, labelAccuracy,label="Label correctness")
    pl.title("Accuracy of labels")
    pl.xlabel('number of queries')
    pl.xticks(range(len(labels)),labels, rotation=45)
    #legend = pl.legend(loc='upper left', shadow=True)
    pl.show()
    """

