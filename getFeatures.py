import scipy.io.wavfile as wav
import os
from os import listdir
import numpy as np
import pickle
import essentia
import essentia.standard
import essentia.streaming
from essentia.standard import *
import subprocess
from subprocess import Popen, PIPE
import wave
import contextlib
import json
#from createInitialModel import checkDownloaded
from getSound import getSoundBySingleTag
from fileConversion import convertFolder
from outlierDetection import removeOutliers
import ipdb as pdb #pdb.set_trace()

def getFeatures(className, downloadFileNum=30):
    """
    If features are already extracted, just return them, if not use the Java perform the extraction.
    Combines download, conversion, outlier removal and feature extraction for a given class.
    
    Use this method from now on
    
    @param className:
    @param downloadFileNum: Number of sound files that should be downloaded from freesound
    @return: Numpy array of extracted features
    """
    
    filename = "./extractedFeatures/" + str(className) + ".json"    
    
    if os.path.exists(filename):
        """ JSON already exists, just use this one: """
        print("Using already extracted features to train classifier")
        res = np.array(json.load(open(filename,"rb")))
    else:
        """ Check if the class is downloaded already (but not yet extracted): """
        if checkDownloaded(className, minNumber=10):
            
            if FX_Java(className):
                res = np.array(json.load(open(filename,"rb")))
            else:
                print("Features could not be extracted")    
                return
        else:
            """ If it the sound files hasn't be downloaded yet, start the downloading: """
            print("Starting download of sound files")
            downloadResult = getSoundBySingleTag(className, downloadFileNum)
            if not downloadResult:
                print("Download problems occured, models cannot be build")
                return False
            

            """ Verify that download was successful: """
            if not checkDownloaded(className, minNumber=10):
                print("Download problems occured, models cannot be build")
                return False

            """ Convert the new files: """
            conversionResult = convertFolder(className)

            if not conversionResult:
                print("Problems occurs during file conversion, model cannot be build")
                return False

            """ Remove Outliers """
            outlierDir = os.getcwd() + "/outliers/" + str(className)
            if not os.path.exists(outlierDir):
                os.makedirs(outlierDir)

            outlierResult = removeOutliers(className)

            if outlierResult == False:
                print("Problems occurs during outlier removal. The resulting model might not show perfect performance.")
            
            """ Extract the features now: """
            if FX_Java(className):
                res = np.array(json.load(open(filename,"rb")))
            else:
                print("Features could not be extracted")    
                return
            
    return res

def FX_Java(className):
    """
    Extract features of the given class and dump them in the extractedFeatures folder as a JSON File.
    This function class the Java class to do the actual feature extraction
    
    @param className:
    @return: True if successful, false if not
    """
    cmd = "cd java && java -cp gson-2.2.4.jar:. extractFeatures --folder " + str(className) + " && cd .."
    p = subprocess.Popen(cmd, shell=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    out, err = p.communicate()
    if str(err) != "":
        print("A problem occurred while extracting the features")
        return False
    
    return True
    
def FX_JSON(className):
    """
    Return numpy array of features that were extracted previously and are stored in the JSON file
    
    @param className:
    @return: Numpy array of extracted features
    """
    
    filename = "./extractedFeatures/" + str(className) + ".json"    
    
    if os.path.exists(filename):    
        res = np.array(json.load(open(filename,"rb")))
        return res
    else:
        print("File could not be found")
        return None
    

def alreadyExtracted(className, useJSON=True):
    """
    Check if features for this folder were already extracted and the data is stored in the /extractedFeatures folder
    @param className: name of the class that should be checked
    @return: True if it exists, False if it doesn't
    """
    rootDir = os.getcwd() + "/extractedFeatures"
    if useJSON == True:
        fileDir = rootDir + "/" + className + ".json"
    else:
        fileDir = rootDir + "/" + className + ".p"

    if os.path.exists(fileDir):
        exists = True
    else:
        exists = False

    return exists

def checkDownloaded(className, minNumber=10):
    """
    Check if at least minNumber of files exists for the given class in the sound/className/
    @param className: name of the class that should be checked
    @param minNumber: Minimum number of files, that have to exist in order from the class to be considered as already downloaded. Default value is 10
    @return: True if enough files exist already, False if not.
    """

    dir = os.getcwd() + "/sound" + "/" + className

    """ Check if folder exists at all: """
    if not os.path.exists(dir):
        return False

    """ Check if enough audio files exists in that folder: """
    numberOfFiles = len(listdir(dir))
    if numberOfFiles < minNumber:
        print("Not enough sound files downloaded for given class: " + str(numberOfFiles) + " instead of the required " + str(minNumber))
        return False
    else:
        return True
