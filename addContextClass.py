import json
import copy
import pickle
from sklearn.mixture import GMM
from getFeatures import getFeatures
from sklearn import preprocessing
import ipdb as pdb #pdb.set_trace()

def addNewClass(jsonGMM, newClassName, newClassData=None):
    """
    Incorporate a new class into an existing GMM JSON model (i.e. list not dict!)
    @param jsonGMM: List containing GMMs, see createJSON.py for exact structure
    @param newClassName: Name of the new class. Has to match the name of the folder where the sound files are located
    @param newClassData: Already extracted MFCC Features for the given newClassName. If not provided, a feature extraction will be performed
    @return: List containing the added class as the last element
    """
    
    jGMM = copy.deepcopy(jsonGMM)

#    if newClassData == None:
#        tmp = getFeatures(newClassName) # TODO: do this properly
#        newClassData = tmp["features"]

    newClassData = getFeatures(newClassName)

    scaler = preprocessing.StandardScaler()
    scaler.mean_ = jGMM[0]["scale_means"]
    scaler.std_ = jGMM[0]["scale_stddevs"]
    
    X_train = scaler.transform(newClassData)

    n_train_new = X_train.shape[0]
    n_features = X_train.shape[1]
    n_components = 16

    newClf = GMM(n_components = n_components, covariance_type='full', n_iter=1000)
    newClf.fit(X_train)

    """ Update the dict containing mapping of class names: """
    newClassDict = dict(jGMM[0]['classesDict'])
    newClassDict[newClassName] = len(newClassDict.values())

    new_n_classes = jGMM[0]["n_classes"] + 1
    
    """ Update classesDict and n_classes for all old classes: """
    for i in range(len(jGMM)):
        jGMM[i]["classesDict"] = newClassDict
        jGMM[i]["n_classes"] = new_n_classes
     
    #pdb.set_trace()    

    """ Add the new class to the list: """
    jGMM.append({})   
    
    jGMM[-1]["classesDict"] = newClassDict
    jGMM[-1]["n_classes"] = new_n_classes
    jGMM[-1]["scale_mean"] = scaler.mean_
    jGMM[-1]["scale_stddev"] = scaler.std_
    
    jGMM[-1]["n_components"] = n_components
    jGMM[-1]["n_features"] = n_features
    jGMM[-1]["n_train"] = n_train_new    
    
    jGMM[-1]["weights"] = newClf.weights_.tolist()
    jGMM[-1]["means"] = newClf.means_.tolist()
    jGMM[-1]["covars"] = newClf.covars_.tolist()

    return jGMM
    
