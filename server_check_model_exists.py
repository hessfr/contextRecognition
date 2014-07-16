import sys
import json
import os
import pickle
import collections

""" Script called by the server to check if there is already a trained 
classifier available based on the list of context classes given as paramter

@param classes_list: list of the classes that should be checked
@return: True if a classifier was found, False if not

"""

def check_model_exists(classes_list):
    print("--- server_get_init_model ---")

    exisiting_classifiers = pickle.load(open("classifiers/existing_classifiers.p","rb"))
    
    i=0
    found = False
    filename = None
    for context_list in exisiting_classifiers["context_classes"]:
        if collections.Counter(classes_list) == collections.Counter(context_list):
            #print("classifier found, filename: " + exisiting_classifiers["filenames"][i])
            filename = exisiting_classifiers["filenames"][i]
            found = True
        i += 1
      
    if found == True:
        return filename
    else:      
        return found
