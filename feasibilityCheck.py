import os
from os import listdir
from freesound.__init__ import *
import ipdb as pdb #pdb.set_trace()

Freesound.set_api_key('ff15111274e84bebaba741b0aec023b0')

def feasibilityCheck(className, minResults=25):
    """
    Check if it is feasbile to train a classifier with the given context class
    @param className: name of the class that should be checked
    @param minResults: Minimun number of search results on freesound to train the classifier
    @return:    "downloaded" if audio files for this class are already downloaded
                "feasible" if files are not downloaded yet, but enough search result on freesound
                "not_feasible" if there were not enough results for the given class on freesound
    """
    
    if checkDownloaded(className):
        return "downloaded"
    
    tagSearchString = "tag:" + className
    
    searchResults = Sound.search(f = tagSearchString,
                                 fields="original_filename,id,ref,type,tags,description,duration,samplerate,bitdepth,channels,num_ratings,avg_rating,user",
                                 s="rating_desc",
                                 sounds_per_page=30)
    
    numResults =searchResults["num_results"]
    
    if numResults < minResults:
        return "not_feasible"
    else:
        return "feasible"

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
