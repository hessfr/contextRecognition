import sys
import shutil
import json
import os
from addContextClass import addNewClass

""" Script called by the server to add a new context class and dump it to disk """

print("--- server_add_context_class ---")

# Get the first param:
filenameTmp = str(sys.argv[1]) # Classifer that we received from the client (the one that should be adapted)
filenameClassifier = str(sys.argv[2]) # Filename where we will stored the adapted classifier
newClassName = str(sys.argv[3])

oldGMM = json.load(open(filenameTmp,"rb"))

print("Incorporating the new class " + newClassName)
newGMM = addNewClass(oldGMM, newClassName)

print("Incorporation of context class " + newClassName + " finished")

# Dump the adapted GMM into the right location:
json.dump(newGMM, open(filenameClassifier, "wb"))

os.remove(filenameTmp)

#shutil.copy('jsonGMM.json', filename) # later call a external method and save the GMM into the right location

#f = open(filename, 'w')
#f.write("This is a test, the requested class is " + classname)
