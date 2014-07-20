import json
import simplejson
import cherrypy
import subprocess
import os
from random import randint
from feasibilityCheck import feasibilityCheck
from GetKnownClasses import GetKnownClassesJSON
from server_check_model_exists import check_model_exists

class AddContextClass():

    exposed = True

    @cherrypy.expose
    @cherrypy.tools.json_in()
    def POST(self, new_classname):
        
        print("--- AddContextClass POST request ---")

        print("New class " + new_classname + " requested")
    
        json_data = cherrypy.request.json      
    
        # Create random ID for this request:
        id = str(randint(1e5,1e6))
        
        # Dump the GMM object we received from the client, as it is too large to parse as argument directly:
        filenameTmp = "tmp_" + id + ".json"
        json.dump(json_data, open(filenameTmp,"wb"))
        
        # Create an id, where the client can get the new classifier when the calculation is done:  
        filenameClassifier = "Classifier_" + id + ".json"                
        
        subprocess.Popen(["python", "server_add_context_class.py", filenameTmp, filenameClassifier, new_classname])        
        #subprocess.Popen(["nohup", "python", "simpleTask.py" ..... ]) #this should be able to run, even if this script is closed...  
        
        return filenameClassifier
        
    @cherrypy.expose
    @cherrypy.tools.json_out()
    def GET(self, filename):
        print("--- AddContextClass GET request ---")        

        notReadyCode = -1

        # Convert unicode to string:
        filename = str(filename)
        filename = filename[1:-1]

        print(filename)

        if(os.path.isfile(filename)):
            print("Filename " + filename + " found on disk, will be sent to client")
        
            js = json.load(open(filename, 'rb'))
            
            #os.remove(filename)
            
            return js
        
        else:
            # Classifier not available yet, tell client that we are not ready yet:
            return notReadyCode
        
class FeasibilityCheck():
    
    exposed = True    
    
    @cherrypy.expose
    def POST(self, classname):
        
        print("--- FeasibilityCheck POST request ---")
        
        res = feasibilityCheck(classname)
        
        return res
        
class GetKnownClasses():
    
    exposed = True    
    
    @cherrypy.expose
    @cherrypy.tools.json_out()
    def POST(self):
        
        print("--- GetKnownClasses POST request ---")
        
        classList = GetKnownClassesJSON()
        
        json_string = json.dumps(classList)

        return json_string
        
class RawAudio():  
    
    exposed = True
        
    @cherrypy.expose
    def POST(self, user_id, date):
        
        print("--- RawAudio POST request ---")        

        # Set timeout limit to 2h:
        cherrypy.response.timeout = 7200
        
        print("User ID: " + user_id)   
        print("Date: " + date)        
        
        data = cherrypy.request.body.read()
        
        baseFolder = "userAudioData/"
        
        full_filename = str(baseFolder + "RawAudio_" + user_id + "_" + date)

        # If file already exists, display a warning:        
        if(os.path.isfile(full_filename)):
            print("File for that day and user already exists, overwritting file")
        
        f = open(full_filename, 'wb')
        f.write(data)
        f.close()
        
        print("Writing raw audio file to disk finished")     





        return "abc"
        
        
        
        
        
        
        
        
        
        
class InitClassifier():

    exposed = True

    # TODO:

    @cherrypy.expose
    @cherrypy.tools.json_in()
    @cherrypy.tools.json_out()
    def POST(self):
        
        print("--- InitClassifier POST request ---")

        json_data = cherrypy.request.json     
        
        # Remove unicode notation:
        string_data = dict([(str(k), str(v)) for k, v in json_data.items()])
        
        classes_list = string_data.values()

        model_exists_result = check_model_exists(classes_list)
        
        if model_exists_result == False:
            # This model does not exists on server yet, so we have to build it:
            
            # Create random ID for this request:
            id = str(randint(1e5,1e6))

            # Create an id, where the client can get the new classifier when the calculation is done:  
            filename_new_classifier = str(id) + ".json"            
            
            # Start training the classifier in the background (list is passed as String and converted back with Regex later):
            subprocess.Popen(["python", "server_create_initial_model.py", str(classes_list), filename_new_classifier])
            
            dir = "classifiers/" + filename_new_classifier

            response = {"wait": "wait", "filename": dir}
   
        else:
            # The model already exisits and the location of the model can be send to server:
            filename_exisiting = "classifiers/" + model_exists_result
            response = {"wait": "no_wait", "filename": filename_exisiting}
            
        return response
        
    @cherrypy.expose
    @cherrypy.tools.json_out()
    def GET(self, filename):
        print("--- InitClassifier GET request ---")        

        notReadyCode = -1

        # get the filename by removing the first part of the response ('filename=classifiers/625987.json'....)
        filename = filename.split("=")[1]

        if(os.path.isfile(filename)):
            print("Filename " + filename + " found on disk, will be sent to client")
        
            js = json.load(open(filename, 'rb'))
            
            #os.remove(filename)
            
            return js
        
        else:
            # Classifier not available yet, tell client that we are not ready yet:
            return notReadyCode
        

        

""" Mount the classes to the right URL: """

cherrypy.tree.mount(AddContextClass(), '/addclass', {
    '/': {
        'request.dispatch': cherrypy.dispatch.MethodDispatcher(),
        'tools.json_in.on': True,
        'tools.json_out.on': True,
        'tools.response_headers.on': True,
        }})

cherrypy.tree.mount(FeasibilityCheck(), '/feasibilitycheck',  {
    '/': {
        'request.dispatch': cherrypy.dispatch.MethodDispatcher()
        }})
        
cherrypy.tree.mount(GetKnownClasses(), '/getknownclasses', {
    '/': {
        'request.dispatch': cherrypy.dispatch.MethodDispatcher(),
        'tools.json_out.on': True,
        'tools.response_headers.on': True,
        }})
        
cherrypy.tree.mount(RawAudio(), '/rawaudio', {
    '/': {
        'request.dispatch': cherrypy.dispatch.MethodDispatcher()
        }})
        
cherrypy.tree.mount(InitClassifier(), '/initclassifier', {
    '/': {
        'request.dispatch': cherrypy.dispatch.MethodDispatcher(),
        'tools.json_in.on': True,
        'tools.json_out.on': True,
        'tools.response_headers.on': True,
        }})
        
sizeLimitGB = 3.5 # in Gigabytes

fileSizeLimit = int(sizeLimitGB * 1024**3)

conf_global = {
    '/': { 'request.dispatch': cherrypy.dispatch.MethodDispatcher() },
    'global': {'server.socket_host': '0.0.0.0',
               'server.socket_port': 8080,
               'server.max_request_body_size': fileSizeLimit
               }
}

""" Start the server: """
cherrypy.config.update(conf_global)
cherrypy.engine.start()
cherrypy.engine.block()
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    