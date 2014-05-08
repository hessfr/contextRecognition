import scipy.io.wavfile as wav
import os
from os import listdir
import numpy as np
import pickle
import essentia
import essentia.standard
import essentia.streaming
from essentia.standard import *
import ipdb as pdb #pdb.set_trace()

def FX_multiFolders(classesList=None):
    """
    Calculate 12 MFCC (1st coefficient is not considered) for all audio files in the list of classes
    (each one corresponds to a sub-folder of the sound/ folder) and return a dictionary containing the extracted features, corresponding labels and 
    mapping from class names to class number (classesDict).
    If the features were extracted before and are saved in the extractedFeatures/ folder, these pickle files will be used instead of extracting the features again.
    If no list if given, features will be extracted from all sub-folders.
    @param classesDict: Dict containing the classes (corresponds to folders) and a mapping to numbers. If not provided, features will be extracted from all sub-folders
    @return: Dictionary containing the extracted features as numpy array, corresponding labels (numbers) as numpy array and mapping from class names to class number as dict
    """

    dirSound = os.getcwd() + "/sound/"
    
    if not classesList:
        #folderList = [name for name in os.listdir(dirSound) if os.path.isdir(os.path.join(dirSound, name))]

        classesDict = fillClassesDict()
 
    else:
        classesDict = fillClassesDict(classesList)


    featureList = []
    labelList = []
    pickleDir = os.getcwd() + "/extractedFeatures"

    for folder in classesDict.keys():
        if alreadyExtracted(folder):
            """ Use already extracted data if available: """
            fileDir = pickleDir + "/" + folder + ".p"
            tmpFeatures = pickle.load(open(fileDir,"rb"))
        else:
            """ If not extracted data available, extract features from sound files: """
            tmpFeatures = FX_Folder(folder)

        featureList.append(tmpFeatures)
        
        tmpLabels = np.empty([tmpFeatures.shape[0],1])
        tmpLabels.fill(classesDict.get(folder))
        labelList.append(tmpLabels)
        
        
    allFeatures = np.concatenate(featureList, axis=0)
    allLabels = np.concatenate(labelList, axis=0)
    
    
    featureData = {'features': allFeatures, 'labels': allLabels, 'classesDict': classesDict}
    
    return featureData


def alreadyExtracted(className):
    """
    Check if features for this folder were already extracted and the data is stored in the /extractedFeatures folder
    @param className: name of the class that should be checked
    @return: True if it exists, False if it doesn't
    """
    rootDir = os.getcwd() + "/extractedFeatures"
    fileDir = rootDir + "/" + className + ".p"

    if os.path.exists(fileDir):
        exists = True
    else:
        exists = False

    return exists


def FX_Folder_Pickle(folderName):
    """
    Extracts features from the given folder and dumps them into a pickle file in the extractedFeatures/ folder with the same
    name as the folder and a *.p file extension
    @param folderName: Name of the folder in the "sound" folder, i.e. if you want to use the folder ./sound/car give "car" a folderName
    """
    data = FX_Folder(folderName)

    fileDir = str("extractedFeatures/" + folderName + ".p")

    pickle.dump(data,open(fileDir,"wb"))


def FX_Folder(folderName):
    """
    Calculate 12 MFCC (1st coefficient is not considered) for all audio files is the given folder and return one numpy array containing all features
    @param folderName: Name of the folder in the "sound" folder, i.e. if you want to use the folder ./sound/car give "car" a folderName
    @return: Single numpy array containing 12 MFCC for all files in that folder
    """
    dir = os.getcwd() + "/sound/" + folderName
    if not os.path.exists(dir):
        print "Folder " + folderName + " not found"
        return None
    else:
        fileList = listdir(dir)
        tmpFeatureList = []
        for file in fileList:
            filePath = str(dir + "/" + file)
            print "Extracting features from file " + str(file)
            if filePath.endswith('.wav'):
                tmp = FX_File(filePath)
                if tmp.shape[0] != 0:
                    tmpFeatureList.append(tmp)
        res = np.concatenate(tmpFeatureList, axis=0)
    return res    

def FX_File(file):
    """
    Calculate 12 MFCC (1st coefficient is not considered) of a given file with 16kHz sample rate, 32ms frame size and 50% overlapping and return a numpy array
    @param file: Name and location of the file you want to use
    @return: Numpy array containing all 12 MFCC for the given file
    """
    #TODO: adapt for changeable sample rate and overlapping
    #TODO: additional check that sample rate is correct
    loader = essentia.standard.MonoLoader(filename = file, sampleRate = 16000)
    audio = loader()
    w = Windowing(type = 'square')
    spectrum = Spectrum()  # FFT() would give the complex FFT, here we just want the magnitude spectrum
    mfcc = MFCC()
    mfccList = []
    
    """frameSize = 512 corresponds to 32ms when sampling rate is 16kHz"""
    """hopSize = 256 will lead to 50% overlap"""
    for frame in FrameGenerator(audio, frameSize = 512, hopSize = 256):
        mfcc_bands, mfcc_coeffs = mfcc(spectrum(w(frame)))
        mfccList.append(mfcc_coeffs)
    
    """ Create a numpy array with all 13 coefficients: """
    tmpMfccs = essentia.array(mfccList)
    
    """ Delete 1st coefficient, as it only contains information about the volume: """
    finalMfccs = np.delete(tmpMfccs,(0), axis=1)
  
    return finalMfccs

def fillClassesDict(classesList=None):
    """
    Create dictionary that contains mapping from the class names to numbers by adding a folder names in the sound/ directory
    @param classesList: create the dict only for the classes defined in this list
    @return: Dictionary containing mapping from the class names to numbers
    """
    classesDict = {}
    dir = os.getcwd() + "/sound/"
    
    if classesList == None:
        """ Get subfolders (not files) of the sound folder: """ 
        folderList = [name for name in os.listdir(dir) if os.path.isdir(os.path.join(dir, name))]
    else:
        folderList = []
        for name in classesList:
            if os.path.isdir(os.path.join(dir, name)):
                folderList.append(name)
            else:
                print "No folder was found for class " + name + ". It has to be downloaded before it can be used."
        
    """ Fill the dictionary: """
    i = 0
    for name in folderList:
        classesDict[str(name)] = i
        i = i+1
    
    return classesDict

def FX_Test(file):
    """
    Extract 12 MFCC features from a single file
    @param file: Name and location of the file you want to use
    @return: Numpy array containing all 12 MFCC for the given file
    """
    #TODO check if we still need this
    filePath = str(os.getcwd() + "/" + file)
    feat = FX_File(filePath)
    return feat 

from featureExtraction import *