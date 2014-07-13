import json
import simplejson
import cherrypy
import subprocess
import os
from random import randint
from feasibilityCheck import feasibilityCheck

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

        # Convert unicode to string: #TODO: check if we also need this when reveiving data from Android!!!!!!!!!!!!!!!!!!!!!!!!!!
        filename = str(filename)
        filename = filename[1:-1]

        if(os.path.isfile(filename)):
            print("Filename " + filename + " found on disk, will be sent to client")
        
            js = json.load(open(filename, 'rb'))
            
            #os.remove(filename)
            
            return js
        
        else:
            # Classifier not available yet, tell client that we are not ready yet
            return notReadyCode
        
class FeasibilityCheck():
    
    exposed = True    
    
    @cherrypy.expose
    def POST(self, classname):
        
        print("--- FeasibilityCheck POST request ---")
        
        res = feasibilityCheck(classname)
        
        return res

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

conf_global = {
    '/': { 'request.dispatch': cherrypy.dispatch.MethodDispatcher() },
    'global': {'server.socket_host': '0.0.0.0','server.socket_port': 8080}
}

""" Start the server: """
cherrypy.config.update(conf_global)
cherrypy.engine.start()
cherrypy.engine.block()
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    