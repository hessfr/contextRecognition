import numpy as np
import pickle
import os
from os import listdir
from lof import LOF
from lof import outliers
from featureExtraction import FX_File
from pprint import pprint
import subprocess
from subprocess import Popen, PIPE
import ipdb as pdb
        
def paramSweep(folderName, paramRange):
    """
    Calculate Local Outlier Factor (LOF) for all files in the given folder by using the mean values of the 12 MFCC features and
    sweep through different values of the minPts parameter
    @param folderName: Name of the folder in the "sound" folder, i.e. if you want to folder the folder ./sound/car give "car" a folderName
    @param paramRange: Range for the minPts parameter as list
    """
    fileFeatureDict = extractFeatures(folderName)
    
    """ Build list of the mean values: """
    values = fileFeatureDict.values()
    
    featureList = []
    for entry in values:
        featureList.append(tuple(entry))
    
    if(fileFeatureDict is not None):
        
        for minPts in paramRange:
            print "------ minPts = " + str(minPts) + "---------"
            lof = outliers(minPts, featureList)
            for outlier in lof:
                print "File " + str(fileFeatureDict.keys()[outlier["index"]]) + " has LOF of " + str(outlier["lof"])
            
            print "Total number of outliers: " + str(len(lof))


def removeOutliers(folderName, minPts=10):
    """
    Move files that were detected as outliers to the outliers/ folder
    @param folderName: Name of the folder in the "sound" folder, i.e. if you want to folder the folder ./sound/car give "car" a folderName
    @param minPts: Parameter for LOF algorithm: number of nearest neighbors used in defining the local neighborhood of
    the object (see Breunig paper for details). Default value is 10.
    """
    fileFeatureDict = extractFeatures(folderName)

    if(fileFeatureDict is not None):

        """ Build list of the mean values: """
        values = fileFeatureDict.values()
        featureList = []
        for entry in values:
            featureList.append(tuple(entry))

        """ Calculate local outlier factors for each file: """
        lof = outliers(minPts, featureList)

        dir = str(os.getcwd()) + "/sound/" + folderName + "/"

        outlierDir = os.getcwd() + "/outliers/" + str(folderName)
        if not os.path.exists(outlierDir):
            os.makedirs(outlierDir)

        print(str(len(lof)) + " outliers were found for the class " + str(folderName) + " and will be moved to the outliers folder")

        successfullyMoved = 0

        for outlier in lof:

            fileDir = dir + str(fileFeatureDict.keys()[outlier["index"]])
            command = str("mv '" + str(fileDir) + "' '" + str(outlierDir) + "/'")

            p = subprocess.Popen(command, shell=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
            out, err = p.communicate()

            if str(err) == "":
                successfullyMoved = successfullyMoved + 1

        if len(lof) == successfullyMoved:
            print("All outliers were successfully removed for the class " + str(folderName))
            return True
        else:
            print("Problems occured, when removing outliers for the class " + str(folderName))
            return False

def extractFeatures(folderName):
    """
    Extract features from given folder, extract e.g., mean feature values for each file (for each MFCC) and return a dict containing them
    @param folderName: Name of the folder in the "sound" folder, i.e. if you want to folder the folder ./sound/car give "car" a folderName
    @return: Dictionary containing a numpy array of the extracted features for every file name
    """
    dir = os.getcwd() + "/sound/" + folderName
    if not os.path.exists(dir):
        print "Folder " + folderName + " not found"
        return None
    else:
        fileList = listdir(dir)
        fileFeatureDict = {}
        for file in fileList:
            filePath = str(dir + "/" + file)
            print "Extracting features from file " + str(file)
            if filePath.endswith('.wav'):
                tmp = FX_File(filePath)
                if tmp.shape[0] != 0:
                    """ Compute mean values: """
                    means = np.mean(tmp,0,dtype=np.float64)
                    
                    """ Compute standard deviation: """
                    stddev = np.std(tmp, 0, dtype=np.float64)

                    """ Compute number of zero crossings: """
                    numZeroCrossingsList = []
                    for i in range(tmp.shape[1]):
                        numZeroCrossingsList.append(np.where(np.diff(np.sign(tmp[:,i])))[0].shape[0])
                    numZeroCrossings = np.array(numZeroCrossingsList,dtype=np.float64)
                    
                    """ Combine the features into one array with one column for each MFCC: """
                    #features = means
                    features = np.concatenate((means,stddev))
                    #features = np.concatenate((means,stddev,numZeroCrossings))
                    
                    fileFeatureDict[file] = features
             
    return fileFeatureDict

from outlierDetection import *














