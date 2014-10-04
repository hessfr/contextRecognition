from freesound.__init__ import *
from compiler.pycodegen import TRY_FINALLY

Freesound.set_api_key('ff15111274e84bebaba741b0aec023b0')

def getSoundByList(listOfTags, maxNumber):
    """
    Downloads audio files from freesound.org based on an individual search for each tag. The files will be downloaded into a folder named after each
    tag; a given number of clips for each tag defined by @param maxNumber with the highest rating will be downloaded.
    
    @param listOfTags: an individual search of freesound will be performed for each tag
    @param maxNumber: Number of clips that should be downloaded for each tag
    """
    
    for tag in listOfTags:
        getSoundBySingleTag(tag, maxNumber)
    
        
def getSoundByTags(label, tags, maxNumber):
    """
    Downloads audio files from freesound.org based on a single search for multiple tags. The files will be downloaded into a folder named after the 
    given label, a given number of clips defined by @param maxNumber with the highest rating will be downloaded.
    
    @param label: Descriptive name of the search request 
    @param tags: List of tags that will be used for the freesound search
    @param maxNumber: Number of clips that should be downloaded
    """
    tagSearchString = ""
    
    for t in tags:
        tagSearchString = tagSearchString + "tag:" + t + " "
    
    searchResults = Sound.search(f = tagSearchString,
                                 fields="original_filename,id,ref,type,tags,description,duration,samplerate,bitdepth,channels,num_ratings,avg_rating,user",
                                 s="rating_desc",
                                 sounds_per_page=maxNumber)
    
    print "Total number of search results: " + str(searchResults["num_results"])

    dir = os.getcwd() + "/sound/" + label
    if not os.path.exists(dir):
        os.makedirs(dir)
        
    try: 
        infoFile = open(dir + "/metadata.txt", "a")
    except:
        print "Couldn't create file for metadata"
    
    print "Start downloading the top " + str(maxNumber) + " clip(s) according to their rating"
        
    for sTmp in searchResults['sounds']:
        s = Sound.get_sound_from_ref(sTmp['ref'])
        
        """ Check if file already exists in the given directory and only download if it doesn't """
        fileDir = os.getcwd() + "/sound/" + label + "/" + s['original_filename']
        
        if not os.path.exists(fileDir):
            try:
                s.retrieve(dir)
                info = ''.join([s['original_filename'] + ";",
                               str(s['id']) + ";",
                               s['ref'] + ';',
                               s['type'] + ";",
                               ', '.join(map(str,s['tags'])).replace('\n', '.').replace(";", ".") + ";",
                               str(s['description']).replace('\n', '.').replace(";", ".") + ';',
                               str(s['duration']) + ";",
                               str(s['samplerate']) + ";",
                               str(s['bitdepth']) + ';',
                               str(s['channels']) + ";",
                               str(s['num_ratings']) + ";",
                               str(s['avg_rating']) + ';',
                               str(s['user']['username'])])
                
                infoFile.write(info.encode("latin-1", errors='replace'))   
                infoFile.write("\n")
                print "Downloaded " + s['original_filename'] + " successfully" 
            except Exception as inst:
                print "Exception: " + str(type(inst))
            
        else:
            print "File '"+ fileDir + "' was skipped, because it already exists in the current directory"
            
def getSoundBySingleTag(tag, maxNumber=30):
    """
    Downloads audio files from freesound.org based on a search for a single tags. The files will be downloaded into a folder named after the 
    given tag, a given number of clips defined by @param maxNumber with the highest rating will be downloaded.
    
    @param tag: Tag that will be used for the freesound search
    @param maxNumber: Number of clips that should be downloaded
    @return: returns true when download is finished
    """
    
    """ First of all check if the class requires an additional tag, e.g. searching for "Bus"+"Inside" instead of only "Bus" """
    # list of context classes that need additional "inside" tag:
    additionalTagNeeded = ["Train", "Car"]
    
    if tag in additionalTagNeeded:
        getSoundByTags(tag, [tag, "inside"], maxNumber=maxNumber)
        return True

    tagSearchString = "tag:" + tag + " "
    
    searchResults = Sound.search(f = tagSearchString,
                                 fields="original_filename,id,ref,type,tags,description,duration,samplerate,bitdepth,channels,num_ratings,avg_rating,user",
                                 s="rating_desc",
                                 sounds_per_page=maxNumber)
    
    print "Total number of search results for '" + tag + "': " + str(searchResults["num_results"])

    if searchResults["num_results"] < maxNumber:
        print("Only " + str(searchResults["num_results"]) + " instead of " + str(maxNumber) +" results were found. No files will be downloaded")
        return False

    dir = os.getcwd() + "/sound/" + tag
    if not os.path.exists(dir):
        os.makedirs(dir)
        
    try: 
        infoFile = open(dir + "/metadata.txt", "a")
    except:
        print "Couldn't create file for metadata"
    
    print "Start downloading the top " + str(maxNumber) + " clip(s) according to their rating"
        
    for sTmp in searchResults['sounds']:
        s = Sound.get_sound_from_ref(sTmp['ref'])
        
        """ Check if file already exists in the given directory and only download if it doesn't """
        fileDir = os.getcwd() + "/sound/" + tag + "/" + s['original_filename']
        
        if not os.path.exists(fileDir):
            try:
                s.retrieve(dir)
                info = ''.join([s['original_filename'] + ";",
                               str(s['id']) + ";",
                               s['ref'] + ';',
                               s['type'] + ";",
                               ', '.join(map(str,s['tags'])).replace('\n', '.').replace(";", ".") + ";",
                               str(s['description']).replace('\n', '.').replace(";", ".") + ';',
                               str(s['duration']) + ";",
                               str(s['samplerate']) + ";",
                               str(s['bitdepth']) + ';',
                               str(s['channels']) + ";",
                               str(s['num_ratings']) + ";",
                               str(s['avg_rating']) + ';',
                               str(s['user']['username'])])
                
                infoFile.write(info.encode("latin-1", errors='replace'))   
                infoFile.write("\n")
                print "Downloaded " + s['original_filename'] + " successfully" 
            except Exception as inst:
                print "Exception: " + str(type(inst))
            
        else:
            print "File '"+ fileDir + "' was skipped, because it already exists in the current directory"

    return True
    
    











