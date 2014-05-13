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
import ipdb as pdb #pdb.set_trace()

def FX_multiFolders(classesList=None, saveFeatures=False): #TODO: Set saveFeatures to True when finished testing
    """
    Calculate 12 MFCC (1st coefficient is not considered) for all audio files in the list of classes
    (each one corresponds to a sub-folder of the sound/ folder) and return a dictionary containing the extracted features, corresponding labels and 
    mapping from class names to class number (classesDict).
    If the features were extracted before and are saved in the extractedFeatures/ folder, these pickle files will be used instead of extracting the features again.
    If no list if given, features will be extracted from all sub-folders.
    @param classesDict: Dict containing the classes (corresponds to folders) and a mapping to numbers. If not provided, features will be extracted from all sub-folders
    @param saveFeatures: If True, the extracted features will be saved, to save time. Default True
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

            if saveFeatures:
                targetFile = str("extractedFeatures/" + folder + ".p")
                pickle.dump(tmpFeatures,open(targetFile,"wb"))
                print("Saved extracted features for " + folder + " class")

        featureList.append(tmpFeatures)
        
        tmpLabels = np.empty([tmpFeatures.shape[0]])
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
    featureData = FX_Folder(folderName)

    fileDir = str("extractedFeatures/" + folderName + ".p")

    pickle.dump(featureData,open(fileDir,"wb"))


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

def FX_File(file, sampleRate=16000, windowLength=0.032):
    """
    Calculate 12 MFCC (1st coefficient is not considered) of a given file with 16kHz sample rate with non-overlapping
    frames.
    @param file: Name and location of the file you want to use
    @param SampleRate: Sample rate of the file. Default is 16000
    @param windowLength: Length of the window in seconds. Default is 0.032
    @return: Numpy array containing all 12 MFCC for the given file
    """
    loader = essentia.standard.MonoLoader(filename = file, sampleRate = sampleRate)
    audio = loader()
    w = Windowing(type = 'square')  #'hann', 'square'
    spectrum = Spectrum()  # FFT() would give the complex FFT, here we just want the magnitude spectrum
    mfcc = MFCC()
    mfccList = []
    
    """frameSize = 512 corresponds to 32ms when sampling rate is 16kHz
        hopSize = frameSize will lead to no overlap"""

    frameSize = windowLength * sampleRate

    for frame in FrameGenerator(audio, frameSize = int(frameSize), hopSize = int(frameSize)):
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
                print("No folder was found for class " + name + ". It has to be downloaded before it can be used.")
        
    """ Fill the dictionary: """
    i = 0
    for name in folderList:
        classesDict[str(name)] = i
        i = i+1
    
    return classesDict

def FX_Test(file, sampleRate = 16000, windowLength = 0.032, splitLength = 7200, noSplitting=False):
    """
    Extract 12 MFCC features from a single file. If the file is too large, it will be split into smaller files, features will be
    extracted and combined in the end.
    @param file: Name and location of the file you want to use
    @param SampleRate: Sample rate of the file. Default is 16000
    @param windowLength: Length of the window in seconds. Default is 0.032
    @param splitSize: Threshold for splitting the file, if file is longer than this parameter it will be split into parts of this size. Default value is 7200.
    @param noSplitting: When set to True, the file will never be split, no matter how large it is #TODO: remove this again later
    @return: Numpy array containing all 12 MFCC for the given file
    """

    filePath = str(os.getcwd() + "/" + file)

    if noSplitting == True: #TODO: remove this again later
        """ Do not split the file: """
        feat = FX_File(filePath, sampleRate=16000, windowLength=0.032)
        return feat

    with contextlib.closing(wave.open(filePath,'r')) as f:  #TODO: check why this doesn't work for all files
        frames = f.getnframes()
        rate = f.getframerate()
        duration = frames / float(rate)

    if duration <= splitLength:
        """ Do not split the file: """
        feat = FX_File(filePath, sampleRate=16000, windowLength=0.032)
        return feat
    else:
        """ Split file: """
        splitName = "part"
        allSplitNames = []
        i = 0

        while i*splitLength < duration:
            start = i * splitLength #Sox allows a trim region to end later than the file ending and will return just the audio to the end of the file
            #end = (i+1) * splitLength

            tmpFileName = splitName + str(i) + ".wav"
            allSplitNames.append(tmpFileName)

            commandString = str("sox '" + str(filePath) + "' '" + str(tmpFileName) + "' -V1 trim " + str(int(start)) + " " + str(int(splitLength)))
            p = subprocess.Popen(commandString, shell=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
            out, err = p.communicate()
            if str(err) != "":
                print "A problem occurred while splitting the file"

            i=i+1



        """ Extract features for each individual part: """
        tup = () #define empty tuple and insert values later
        for file in allSplitNames:
            tmp = FX_File(file, sampleRate=16000, windowLength=0.032)
            tup = tup + (tmp,)

            """ Delete the split files again: """
            commandString = str("rm '" + str(file) + "'")
            p = subprocess.Popen(commandString, shell=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
            out, err = p.communicate()
            if str(err) != "":
                print "A problem occurred while deleting temporary files"

        feat = np.concatenate(tup)

        return feat

from featureExtraction import *