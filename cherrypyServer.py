import json
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
        
        return filenameClassifier
        
    @cherrypy.expose
    @cherrypy.tools.json_out()
    def GET(self, filename):
        print("--- AddContextClass GET request ---")        

        notReadyCode = -1

        # Convert unicode to string:
        filename = str(filename)
        filename = filename[1:-1]

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
        
        baseFolder = "experimentData/"
        
        full_filename = str(baseFolder + "ExpData_" + user_id + "_" + date + ".tar.gz")

        # If file already exists, display a warning:        
        if(os.path.isfile(full_filename)):
            print("File for that day and user already exists, overwritting file")
        
        f = open(full_filename, 'wb')
        f.write(data)
        f.close()
        
        print("Writing raw audio file to disk finished")     

        return "ok"
        
class InitClassifier():

    exposed = True

    @cherrypy.expose
    @cherrypy.tools.json_in()
    @cherrypy.tools.json_out()
    def POST(self, context_classes):
        
        print("--- InitClassifier POST request ---")

        classes_list = json.loads(context_classes) 

        model_exists_result = check_model_exists(classes_list)
        
        if model_exists_result == False:
            # This model does not exists on server yet, so we have to build it:
            
            # Create random ID for this request:
            id = str(randint(1e5,1e6))

            # Create an id, where the client can get the new classifier when the calculation is done:  
            filename_new_classifier = str(id) + ".json"
            
            valid_classes = []
            invalid_classes = []
            
	    waitOrNoWait = "no_wait"

            for el in classes_list:
                if (feasibilityCheck(el) == "not_feasible"): 
                    invalid_classes.append(el)
                    
                elif (feasibilityCheck(el) == "downloaded"):
                    valid_classes.append(el)
                    
                elif (feasibilityCheck(el) == "feasible"):
                    valid_classes.append(el)
                
                else:
                    print("feasibilityCheck returned invalid result")

	    if len(valid_classes) != 0:
                # Start training the classifier in the background (list is passed as String and converted back with Regex later):
                subprocess.Popen(["python", "server_create_initial_model.py", json.dumps(valid_classes), filename_new_classifier])
		waitOrNoWait = "wait"
	    else:
		# If not a single class is valid, don't do anything:
		waitOrNoWait = "no_wait"
	
            # Return new filename and the list of invalid classes:
            response = {"filename": filename_new_classifier, "wait": waitOrNoWait, "invalid_classes": invalid_classes}
   
        else:
            # The model already exisits and the location of the model can be send to server:
            filename_exisiting = "classifiers/" + model_exists_result
            
            #response = {"wait": "no_wait", "filename": filename_exisiting}
            response = {"filename": filename_exisiting, "wait": "no_wait", "invalid_classes": []}
            
        return response
        
    @cherrypy.expose
    @cherrypy.tools.json_out()
    def GET(self, filename):
        print("--- InitClassifier GET request ---")        

        notReadyCode = -1

	    # Check if the json file of the classifier already exisits:
        if(os.path.isfile(filename)):
            print("Filename " + filename + " found on disk, will be sent to client")
        
            js = json.load(open(filename, 'rb'))
            
            #os.remove(filename)
            
            return js

        elif(os.path.isfile("classifiers/" + filename)):
            print("Filename " + filename + " found on disk, will be sent to client")
        
            js = json.load(open("classifiers/" + filename, 'rb'))
            
            #os.remove(filename)
            
            return js
        
        else:
            # Classifier not available yet, tell client that we are not ready yet:
            return notReadyCode
        
        
class ManageClasses():
    """
    Remove or add multiple context classes at once
    
    
    """

    exposed = True

    @cherrypy.expose
    @cherrypy.tools.json_in()
    @cherrypy.tools.json_out()
    def POST(self, context_classes):
        
        print("--- ManageClasses POST request ---")

        context_classes_list = json.loads(context_classes)            
        
        classifier_json = cherrypy.request.json     
        
        # Create random ID for this request:
        id = str(randint(1e5,1e6))
        
        # Dump the GMM object we received from the client, as it is too large to parse as argument directly:
        filename_old_classifier = "tmp_" + id + ".json"
        json.dump(classifier_json, open(filename_old_classifier,"wb"))
        
        # Create an id, where the client can get the new classifier when the calculation is done:  
        filename_new_classifier = "Classifier_" + id + ".json"
        
        valid_classes = []
        invalid_classes = []
        
        for el in context_classes_list:
            if (feasibilityCheck(el) == "not_feasible"): 
                invalid_classes.append(el)
                waitOrNoWait = "no_wait"
                
            elif (feasibilityCheck(el) == "downloaded"):
                valid_classes.append(el)
                waitOrNoWait = "no_wait"
                
            elif (feasibilityCheck(el) == "feasible"):
                valid_classes.append(el)
                waitOrNoWait = "wait"
                
            else:
                print("feasibilityCheck returned invalid result")
                waitOrNoWait = "no_wait"
        
        # Start training the classifier in the background:
        subprocess.Popen(["python", "server_manage_classes.py", json.dumps(valid_classes), filename_old_classifier, filename_new_classifier])
        
        # Return new filename and the list of invalid classes:
        response = {"filename": filename_new_classifier, "wait": waitOrNoWait, "invalid_classes": invalid_classes}
        
        return response
        
    @cherrypy.expose
    @cherrypy.tools.json_out()
    def GET(self, filename):
        print("--- ManageClasses GET request ---")        

        notReadyCode = -1

        if(os.path.isfile(filename)):
            print("Filename " + filename + " found on disk, will be sent to client")
        
            js = json.load(open(filename, 'rb'))
            
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
        
cherrypy.tree.mount(ManageClasses(), '/manageclasses', {
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
