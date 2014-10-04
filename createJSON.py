import numpy as np
import pickle
import json
import csv
import copy
import operator
import pylab as pl
from sklearn.mixture import GMM
from sklearn import preprocessing
import ipdb as pdb #pdb.set_trace()

#tGMM = pickle.load(open("tGMM.p","rb"))
#realWorldFeatures = np.array(json.load(open("realWorldFeatures.json","rb")))
#updatePoints = pickle.load(open("updatePoints_label1.p","rb"))
# fewPoints = json.load(open("fewPoints.json","rb"))
# fewPoints = np.array(fewPoints["points"])


def dictToJSON(trainedGMM, returnGMM=False, filename=None):
    """
    Deserialize the GMM object (dict!) in Python and dump it into a JSON file. Final JSON structure looks like this:
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
        path = filename
    
        json.dump(seriGMM, open(path,"wb"))
    
    if returnGMM == True:
        return seriGMM
        
def listToJSON(trainedGMM, returnGMM=False, filename=None):
    """
    Deserialize the GMM object (list!) in Python and dump it into a JSON file. Final JSON structure looks like this:
    GMM[0]["classesDict"]
    GMM[0]["n_classes"]
    GMM[0]["scale_means"]
    GMM[0]["scale_stddevs"]
    GMM[0]["weights"]
    GMM[0]["means"]
    GMM[0]["covars"]
    GMM[0]["n_components"]
    GMM[0]["n_features"]
    GMM[0]["n_train"]
    GMM[1]["classesDict"]
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
        
def JSONToDict(filename):
    """
    Convert the JSON classifier (i.e. the list in python) back into a dictionary, were we can use our normal methods on
    
    @param filename: path to the json classifier
    @return: classifier as dictionary
    """
    
    jsonGMM = np.array(json.load(open(filename,"rb")))
    n_classes = len(jsonGMM)
    n_components = jsonGMM[0]["n_components"]
    classesDict = jsonGMM[0]["classesDict"]
    
    clfs = []
    n_train_list = []    
    
    for i in range(n_classes):
        tmpClf = GMM(n_components=n_components, covariance_type='full')
        dummy = np.random.random((100,12))
        tmpClf.fit(dummy) # workaround, as sklearn requires that .fit is called before using this GMM. All values are overwritten later anyway  
    
        tmpClf.weights_ = np.array(jsonGMM[i]["weights"])
        tmpClf.means_ = np.array(jsonGMM[i]["means"])
        tmpClf.covars_ = np.array(jsonGMM[i]["covars"])
        
        n_train_list.append(jsonGMM[i]["n_train"])        
        
        clfs.append(tmpClf)
    
    
    
    scaler = preprocessing.StandardScaler()
    scaler.mean_ = jsonGMM[0]["scale_means"]
    scaler.std_ = jsonGMM[0]["scale_stddevs"]
    
    dictGMM = {'clfs': clfs, 'classesDict': classesDict, 'n_train': n_train_list, 'scaler': scaler}
    
    return dictGMM

