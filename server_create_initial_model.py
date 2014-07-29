import pickle
import json
import sys
from getFeatures import getFeaturesMultipleClasses
from classifiers import trainGMM
from createJSON import dictToJSON

""" Script called by the server to check if there is already a trained 
classifier available based on the list of context classes given as paramter

@param classes_list: list of the classes that should be checked
@return: True if a classifier was found, False if not

"""

print("--- server_create_initial_model ---")

classes_list_str = str(sys.argv[1])
new_filename = str(sys.argv[2])

## convert the list passed as string back to a list (ugly :/ ...):
#splitted = classes_list_str.split()
#classes_list = [] # our final list
#i=0
#for el in splitted:
#        for ch in [',','[', ']', '\"','\'']:
#                splitted[i] = splitted[i].replace(ch,"")
#        classes_list.append(splitted[i])
#        i += 1
        
        
classes_list = json.loads(classes_list_str)

featureData = getFeaturesMultipleClasses(classes_list)

trainedGMM = trainGMM(featureData)

print("Training of classifier finished")

dir = "classifiers/" + new_filename

# convert classifier to JSON and store it:
dictToJSON(trainedGMM, returnGMM=False, filename=dir)    

# Save the information to the existing_classifiers file, so that we can find it next time:
existing_classifiers = pickle.load(open("classifiers/existing_classifiers.p","rb"))
existing_classifiers["context_classes"].append(classes_list)   
existing_classifiers["filenames"].append(new_filename)

pickle.dump(existing_classifiers, open("classifiers/existing_classifiers.p","wb"))
    
    
    
    
