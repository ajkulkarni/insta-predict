# ASU CSE 591
# Author: Group 4

import json
from time import sleep

import requests

class PrivateUserException(Exception):
    def __init__(self, user_id):
        self.user_id = user_id

    def __str__(self):
        return 'User {0} is private.'.format(user_id)

class InstagramClient:
    api_base = 'https://api.instagram.com/v1'
    recent_count = 100
    throttle_threshold = 100

    def __init__(self, client_id, verbose=False):
        self.client_id = client_id
        self.verbose = verbose

    def safe_access(self, jsn, alt, *args):
        if len(args) == 0:
            return jsn
        if args[0] in jsn:
            return self.safe_access(jsn[args[0]], alt, *args[1:])
        return alt

    def api_request(self, endpoint, params={}):
        '''Sends an Instagram API request, throttling as necessary.
        endpoint: The Instagram API endpoint.
        params: Additional HTTP GET parameters for the request.
        returns: A tuple containing the response code and the
            JSON document containing the response data.
        '''
        url = self.api_base + endpoint if endpoint.startswith('/') else '{0}/{1}'.format(self.api_base, endpoint)
        params['client_id'] = self.client_id
        resp = requests.get(url, params)
        content = resp.json()
        code = int(self.safe_access(content, resp.status_code, 'meta', 'code'))
        if self.verbose:
            print(str(code) + " " + resp.url[len(self.api_base):])
        # Avoid exceeding the Instagram API limits and getting access turned off.
        if resp.status_code == 429 or int(self.safe_access(resp.headers, self.throttle_threshold, 'x-ratelimit-remaining')) < self.throttle_threshold:
            if self.verbose:
                print("Pausing to throttle API calls...")
            sleep(60)
        return code, content

    def recent(self, user_id, max_id=None):
        '''Requests the recent media posts of the given user.
        user_id: The ID of the target user.
        max_id: Controls where the returned media will start,
            as per the Instagram API.
        returns: A generator which will return tuples consisting
            of a page of media and the max_id that will return
            the next page.
        '''
        path = '/users/{0}/media/recent'.format(user_id)
        params = {'count': str(self.recent_count)}
        if max_id is not None:
            params['max_id'] = str(max_id)
        while True:
            code, content = self.api_request(path, params)
            if code == 400:
                raise PrivateUserException(user_id)
            if code != 200:
                raise Exception('Unable to retrieve recent media for user {0} [Code {1}].'.format(user_id, code))

            next_max_id = self.safe_access(content, None, 'pagination', 'next_max_id')
            yield content['data'], next_max_id
            if next_max_id is None:
                return
            params['max_id'] = next_max_id

    def followers(self, user_id, throw=False):
        '''Requests a list of the user's followers.
        user_id: The ID of the target user.
        returns: A list of followers.
        '''
        code, content = self.api_request('/users/{0}/followed-by'.format(user_id))
        if code == 200:
            return content['data']
        if code == 400:
            raise PrivateUserException(user_id)
        raise Exception('Unable to retrieve followers for user {0} [Code {1}].'.format(user_id, code))

    def search(self, lat, lng):
        '''Requests recent media from the specified location.
        lat: The latitude.
        lng: The longitude.
        returns: A list of media.
        '''
        params = {'lat': lat, 'lng': lng}
        code, content = self.api_request('/media/search', params)
        if code == 200:
            return content['data']
        raise Exception('Unable to perform search [Code {0}]'.format(code))
