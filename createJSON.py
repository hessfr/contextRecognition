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
import ipdb as pdb #pdb.set_trace()

#tGMM = pickle.load(open("tGMM.p","rb"))
#realWorldFeatures = np.array(json.load(open("realWorldFeatures.json","rb")))
#updatePoints = pickle.load(open("updatePoints_label1.p","rb"))
# fewPoints = json.load(open("fewPoints.json","rb"))
# fewPoints = np.array(fewPoints["points"])


def createJSON(trainedGMM, returnGMM=False, filename=None):
    """
    Deserialize the GMM object in Python and dump it into a JSON file. Final JSON structure looks like this:
    GMM[0]["classesDict"]
    GMM[0]["n_classes"]
    GMM[0]["scale_mean"]
    GMM[0]["scale_stddev"]
    GMM[0]["weights"]
    GMM[0]["means"]
    GMM[0]["covars"]
    GMM[0]["n_components"]
    GMM[0]["n_features"]
    GMM[0]["n_train"]
    GMM[1]["weights"]
    .
    .
    .
    @param trainedGMM: GMM object that should be dumped
    @param filename: filename under which it should be stored
    """

    seriGMM = []

    # delete the sklearn GMM objects (as they can't be deserialized) and replace them by dicts containing weights, means, covars:
    for i in range(len(trainedGMM["clfs"])):
        seriGMM.append({})
        seriGMM[i]["classesDict"] = trainedGMM["classesDict"]
        seriGMM[i]["n_classes"] = len(trainedGMM["clfs"])
        seriGMM[i]["scale_means"] = trainedGMM["scaler"].mean_.tolist()
        seriGMM[i]["scale_stddevs"] = trainedGMM["scaler"].std_.tolist()

        seriGMM[i]["n_components"] = trainedGMM["clfs"][0].means_.shape[0]
        seriGMM[i]["n_features"] = trainedGMM["clfs"][0].means_.shape[1]
        seriGMM[i]["n_train"] = trainedGMM["n_train"][i]

        seriGMM[i]["weights"] = trainedGMM["clfs"][i].weights_.tolist()
        seriGMM[i]["means"] = trainedGMM["clfs"][i].means_.tolist()
        seriGMM[i]["covars"] = trainedGMM["clfs"][i].covars_.tolist()


    # pdb.set_trace()

    if filename != None:
        path = filename + ".json"
    
        json.dump(seriGMM, open(path,"wb"))
    
    if returnGMM == True:
        return seriGMM

def pointsToJSON(data, filename):
    """
    Dumps feature points into a JSON file that looks like this: {"points": [[123...][123....]]}

    @param trainedGMM: Numpy array of points that should be dumped
    @param filename: filename under which it should be stored
    """

    dict = {"points": data.tolist()}

    path = filename + ".json"

    json.dump(dict, open(path,"wb"))

































