import json
import simplejson
import cherrypy

class Root():

    exposed = True

    #working:
    @cherrypy.expose
    @cherrypy.tools.json_in()
    def POST(self, request_type):        
        
        json_data = cherrypy.request.json
        
        if request_type == "addContextClass":
            print("New class " + json_data["className"] + " requested")
        
        id = 34523 #random, unique number...

        return "http://www.abc123.com/classifiers/" + str(id)

if __name__ == '__main__':
    conf = {
        '/': {
            #'server.socket_host': '0.0.0.0',
            'request.dispatch': cherrypy.dispatch.MethodDispatcher(),
            'tools.json_in.on': True,
            'tools.json_out.on': True,
            'tools.response_headers.on': True,
            #'tools.response_headers.headers': [('Content-Type', 'text/plain')],
        },
        #'global': {'server.socket_host': '172.30.152.238'}
        'global': {'server.socket_host': '0.0.0.0',
                   'server.socket_port': 8080}
    }  
    cherrypy.quickstart(Root(), '/', conf)
    
    
    