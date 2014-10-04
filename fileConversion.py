import scipy.io.wavfile as wav
import os
from os import rename, listdir
import shutil
import pipes
import numpy as np
import subprocess
from subprocess import Popen, PIPE
import time
import ipdb as pdb

def convertFolder(folderName):
    """
    Converts all audio files in the folder to 16KHz wav files using sox
    @param folderName: Name of the folder in the "sound" folder, i.e. if you want to convert the folder ./sound/car use "car" a folderName
    """
    dir = os.getcwd() + "/sound/" + folderName
    if not os.path.exists(dir):
        print "Folder " + folderName + " not found"
        return False
    else:        
        fileList = listdir(dir)
        for file in fileList: 
            
            # If filename contains quotations marks, just remove them and rename the file:
            if ("'" in file) or ('"' in file):
                print("Remove quotation marks in filename")
                tmp1 = file.replace('"','')
                newFilename = tmp1.replace("'", "")
                newFilename = str(dir + "/" + newFilename)
                file = str(dir + "/" + file)
                rename(file, newFilename)
                file = newFilename            
            else:
                file = str(dir + "/" + file) 

            """ Check if file is audio file first: """
            if isAudio(file):
                if len(file.rsplit('.', 1)) == 1:
                    """ If file has no extension (like .mp3) in the name: """
                    newfile = str(file + ".wav")
                else:                
                    newfile = str(file.rsplit('.', 1)[0] + ".wav")
                
                """ convert into temporary file first: """
                tmpFile = str(dir + "/tmp.wav")
                commandString = str("sox '" + file + "' -V1 -b 16 -c 1 -r 16000 '" + tmpFile + "'")
                p = subprocess.Popen(commandString, shell=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
                out, err = p.communicate()
                
               # pdb.set_trace()                
                
                if str(err) != "":
                    time.sleep(1)
                    " Conversion failed, so we move this folder to another folder, as we don't want to use it now"
                    print str(err)
                    conversionFailedFolder = os.getcwd() + "/conversionFailed"
                    commandString = str("mv '" + file + "' '" + conversionFailedFolder + "'")
                    p = subprocess.Popen(commandString, shell=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
                    out, err = p.communicate()
                    if str(err) != "":
                        print "Could not move file '" + str(file) + "' to 'conversionFailed' folder, maybe the folder does not exists?"
                    else:
                        print "Moved the file '" + str(file) + "' to the conversionFailed folder"
                
                if os.path.exists(tmpFile):
                    """ move and delete the temporary file: """
                    commandString = str("mv '" + tmpFile + "' '" + newfile + "'")
                    p = subprocess.Popen(commandString, shell=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
                    out, err = p.communicate()

                    if newfile != file:
                        commandString = str("rm " + "'" + file + "'")
                        p = subprocess.Popen(commandString, shell=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
                        out, err = p.communicate()
        return True
                        
def isAudio(fileName):
    """
    Check if file is an audio file
    @param fileName: the given file with the corresponding path
    """
    try:
        extension = str(fileName.rsplit('.', 1)[1])
    except:
        return True
        
    if extension == "txt":
        return False
    else:
        return True
