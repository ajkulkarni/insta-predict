# ASU CSE 591
# Author: Group 4

#all the imports
from flask import Flask, request, session, g, redirect, url_for, abort, render_template, flash, jsonify
import CLASSIFIER
import json


#configuration
DEBUG = True
SECRET_KEY = 'brian'
USERNAME = 'admin'
PASSWORD = 'default'



#create application
app = Flask(__name__, static_url_path='/static')
app.config.from_object(__name__)


app.config.from_envvar('FLASKR_SETTINGS', silent=True)







@app.route('/')
def homepage():
    #print 'this is the index page!'
    return render_template('index.html', title='Test')





@app.route('/test/<tags>/<count>')
def respond_to_request(tags='',count=''):

    response = ''
    try:
        Tags = tags.split(',')

        #building json object to serialize
        request = {}
        request['tags'] = Tags
        request['count'] = count
        request = json.dumps(request)

        #calling classifier
        response = CLASSIFIER.webFacingFindOptimalClass(request)

    except Exception as exc:
        response = exc.message
    finally:
        return response




if __name__ == '__main__':
    app.run(host='0.0.0.0')
