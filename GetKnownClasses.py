import os
from os import listdir
import json

def GetKnownClassesJSON():
    """
    Return list of all known (valid) context classes: a context class is added to the
    list, if sound files were downloaded for that class
    
    @return: JSON string of the list of known context classes
    """

    dir = os.getcwd() + "/sound"
    knownClasses = listdir(dir)
    
    json_string = json.dumps(knownClasses)
    
    return knownClasses
    
