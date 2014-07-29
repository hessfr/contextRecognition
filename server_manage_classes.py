import json
import sys
import operator
import copy
import os
from addContextClass import addNewClass

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

""" Delete classes that user deselected: """
#classesToDelete = []
indicesToDelete = []

# Check which classes to remove from the existing classifier:
for i in range(len(oldClasses)):
    if oldClasses[i] not in newClasses:
        indicesToDelete.append(classesDict[oldClasses[i]])
   
# Sort the indicesToDelete list in reverse order, so that we don't have to care about changing indices when removing highest indices first:
indicesToDelete = sorted(indicesToDelete, reverse=True)
numberClassesDelete = len(indicesToDelete)

#print(classesDict)
#print("The following indice(s) will be deleted:")
#for el in indicesToDelete:
#   print(el)
#print("---")


# Delete classes in the GMM models
tmpGMM = copy.deepcopy(oldGMM)
newGMM = [i for j, i in enumerate(tmpGMM) if j not in indicesToDelete]

# Convert the classesDict into a sorted list of tuples first:
sortedTupleList = sorted(classesDict.iteritems(), key=operator.itemgetter(1))

# Convert into a list:
sortedList = []
for el in sortedTupleList:
    sortedList.append(list(el))


#print("Before removing:")
#print sortedList
#print("---")

for i in indicesToDelete:
    del sortedList[i]

for i in range(len(sortedList)):
    sortedList[i][1] = i

#print("After removing, following class(es) left: ")    
print(sortedList)

# Convert back to dictionary:
newClassesDict = {}
for el in sortedList:
    newClassesDict[el[0]] = el[1]

# Assign the new classesDict to all elements in the model:
for el in newGMM:
    el["classesDict"] = newClassesDict

""" Incorporate new classes: """
classesToBeAdded = []
for el in newClasses:
    if el not in oldClasses:
        classesToBeAdded.append(el)
        
for el in classesToBeAdded:
    print("Adding class " + el)
    newGMM = addNewClass(newGMM, el)

json.dump(newGMM, open(new_filename, "wb"))
os.remove(old_filename) 

print("Manage classes finished: " + str(len(classesToBeAdded)) + " class(es) added and " + str(numberClassesDelete) + " class(es) removed")
    
