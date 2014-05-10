import numpy as np
import os
from os import listdir
from fileConversion import isAudio
from getSound import getSoundBySingleTag
from fileConversion import convertFolder
from featureExtraction import FX_multiFolders
from outlierDetection import removeOutliers
from classifiers import trainGMM

def createInitialModel(classesList):
    """
    Method to create a model for a given list of classes.
    Assume that class name always equals the freesound search tag,
    @param classesList: Dict containing the classes (corresponds to folders) and a mapping to numbers. If not provided, features will be extracted from all sub-folders
    @return: The trained model
    """
    for className in classesList:
        downloadedCompletely = False
        """ Check if sound files are already downloaded: """
        if checkDownloaded(className,2): #TODO: change this back to a reasonable (e.g. 12, because we remove outliers from the 30 downloaded files, so this number has to be smaller
            downloadedCompletely = True
        else:
            """ If not downloaded yet, get the sounds from freesound"""
            downloadResult = getSoundBySingleTag(className,12) #TODO: change this back to a reasonable (30)
            if not downloadResult:
                print("Download problems occured, models cannot be build")
                return False

            """ Verify that download was successful: """
            if not checkDownloaded(className,12):
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



    """ Extract features for all classes: """
    featureData = FX_multiFolders(classesList)

    trainedGMM = trainGMM(featureData)

    return trainedGMM #TODO: change this that we return model parameters and classesDict only and not the whole object

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


from createInitialModel import *
