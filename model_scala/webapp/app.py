#!/usr/bin/env python
# ASU CSE 591
# Author: Group 4

from flask import Flask, Response, request, render_template, abort, jsonify
import requests
import argparse

app = Flask(__name__)

@app.route('/')
def index():
    return render_template('index.html')

@app.route('/api/predict/<string:tags>')
def predict(tags):
    if 'count' in request.args:
        params = { 'count': request.args['count'] }
    else:
        params = {}

    req = requests.get(args.engine + tags, params=params)
    if req.status_code != 200:
        abort(req.status_code)
    return Response(response=req.content, status=200, mimetype='application/json')

def get_args():
    parser = argparse.ArgumentParser()
    parser.add_argument('-e', '--engine', help='Prediction engine connection info.',
        default='http://localhost:8080/predict/')
    parser.add_argument('-b', '--bind', help='Address to bind to.', default='127.0.0.1')
    parser.add_argument('-d', '--debug', help='Run Flask in debug mode.', action='store_true')
    return parser.parse_args()

if __name__ == '__main__':
    args = get_args()
    app.run(debug=args.debug, host=args.bind)
