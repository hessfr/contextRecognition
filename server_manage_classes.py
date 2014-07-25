import pickle
import json
import sys
import operator
import copy
from getFeatures import getFeaturesMultipleClasses
from classifiers import trainGMM
from createJSON import dictToJSON

""" Script called by the server to add and/or remove classes of an
existing model.

@param classes_list: list of the classes that should be checked
@param old_filename: filename where the original classifier can be found (the JSON
string is too long to parse as parameter)
@param new_filename: filename where the change classifier will be stored
@return: True if a classifier was found, False if not

"""

print("--- server_manage_classes ---")

classes_list_str = str(sys.argv[1])
old_filename = str(sys.argv[2])
new_filename = str(sys.argv[3])

newClasses = json.loads(classes_list_str)

#print(context_classes_list)

oldGMM = json.load(open(old_filename,"rb"))

classesDict = oldGMM[0]["classesDict"]

# remove unicode notation:
oldClasses = [str(k) for k in classesDict]

#print(oldClasses)

#classesToDelete = []
indicesToDelete = []

# Check which classes to remove from the existing classifier:
for i in range(len(oldClasses)):
    if oldClasses[i] not in newClasses:
        print("Remove " + oldClasses[i])
        #classesToDelete.append(oldClasses[i])
        indicesToDelete.append(classesDict[oldClasses[i]])
   
# Sort the indicesToDelete list:
indicesToDelete = sorted(indicesToDelete)

#print(classesDict)
#print(indicesToDelete)

# Delete classes in the GMM models
tmpGMM = copy.deepcopy(oldGMM)
newGMM = [i for j, i in enumerate(tmpGMM) if j not in indicesToDelete]

# Convert the classesDict into a sorted list of tuples first:
sortedTupleList = sorted(classesDict.iteritems(), key=operator.itemgetter(1))

# Convert into a list:
sortedList = []
for el in sortedTupleList:
    sortedList.append(list(el))

# Now iterate over that list and remove the elements we want to delete and adjust successive numbers:
for i in range(len(sortedList)):
    if i in indicesToDelete:
        del sortedList[i]
        # delete this index in the indicesToDelete and decrement the remaining ones:
        indicesToDelete.remove(i)
        indicesToDelete = [(x-1) for x in indicesToDelete]
        
#print(sortedList)

prev = -1
for i in range(len(sortedList)):
    if sortedList[i][1] != (prev+1):
        for j in range(i, len(sortedList)):
            sortedList[j][1] = sortedList[j][1]-1
    prev = sortedList[i][1]
    
#print(sortedList)

# Convert back to dictionary:
newClassesDict = {}
for el in sortedList:
    newClassesDict[el[0]] = el[1]

# Assign the new classesDict to all elements in the model:
for el in newGMM:
    el["classesDict"] = newClassesDict

print(oldGMM[0]["classesDict"])
print(newGMM[4]["classesDict"])
print(newGMM[7]["weights"])
print(oldGMM[9]["weights"])

"""
for el in oldGMM:
    print(el["n_train"])

print("--------")
    
for el in newGMM:
    print(el["n_train"])
"""

    

"""
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
"""    
    
    
    
































