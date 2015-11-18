#!/usr/bin/env python

# ASU CSE 591
# Author: Group 4

import os
import pwd
import signal
import sys
import traceback
from collections import deque
from random import sample
from datetime import datetime

from sqlalchemy.exc import IntegrityError

from models import *
from instagram import *

class Crawler:
    def __init__(self, session, max_branching=5, queue_size=5000, max_except=10):
        self.session = session
        self.max_branching = max_branching
        self.queue = deque(maxlen=queue_size) # Queue contains user ID's not User objects
        self.max_except = max_except
        self.stop = False

    def on_except(self):
        self.max_except -= 1
        if self.max_except <= 0:
            print('Stopping due to exceptions...')
            sys.stderr.write('Stopping due to exceptions...\n\n')
            self.stop = True

    def attempt(self, func):
        try:
            func()
        except IntegrityError as e:
            # This happens when another crawler adds an object to the database between
            # the time that this crawler queries to see if an object with that id exists
            # in the database and the time that the object is added to the database.
            # We will count down self.max_except, but we also need to rollback the session
            # or else it will throw an exception on every subsequence call.
            sys.stderr.write('IntegrityError: {0} {1}\n\n'.format(e.statement, e.params))
            self.session.rollback()
            self.on_except()
        except:
            traceback.print_exc(file=sys.stderr)
            sys.stderr.write('\n')
            self.on_except()

    def run(self):
        '''Crawls the target site by performing a hybrid breadth-first/depth-first
        traversal of the users, collecting image metadata from each user.
        Assumes the crawler has already been seeded.
        '''
        self.stop = False
        while len(self.queue) > 0 and not self.stop:
            user_id = self.queue.popleft()
            user = self.session.query(User).get(user_id)
            if user is None:
                user = User(id=user_id)
                self.session.add(user)
            self.attempt(lambda: self.scrape(user))
            self.attempt(lambda: self.branch(user))
            self.attempt(lambda: self.session.commit())

    def branch(self, user):
        '''Adds to the search queue by selecting from the successors
        of this user.
        '''
        successors = self.successors(user)
        limit = min(self.max_branching, self.queue.maxlen - len(self.queue))
        if len(successors) > limit:
            self.queue.extend(sample(successors, limit))
        else:
            self.queue.extend(successors)

class InstagramCrawler(Crawler):
    def __init__(self, session, client_id, verbose, max_branching=5, queue_size=5000, max_except=10):
        super().__init__(session, max_branching, queue_size, max_except)
        self.client = InstagramClient(client_id, verbose)

    def successors(self, user):
        '''Returns a list of users connected to the specified user.
        user: The User object representing the user to be requested.
        returns: Parsed JSON representing the followers.
        '''
        if user.private:
            return []
        try:
            return [int(u['id']) for u in self.client.followers(user.id)]
        except PrivateUserException:
            user.private = True
        return []

    def has_location(self, media):
        return media['location'] is not None and 'latitude' in media['location'] and 'longitude' in media['location']

    def scrape(self, user):
        '''Scrapes the recent images of the user. Scrapes subsequent pages
        only as long as they contain at least one geotagged image.
        user: The User object representing the user to be scraped.
        returns: Nothing.
        '''
        if user.private or user.fully_scraped:
            return
        try:
            for content, next_max_id in self.client.recent(user.id, user.next_max_id):
                user.next_max_id = next_max_id
                if next_max_id is None:
                    user.fully_scraped = True
                geotagged = [m for m in content if self.has_location(m)]
                for media in geotagged:
                    self.store_media(user, media)
                if len(geotagged) == 0:
                    return
        except PrivateUserException:
            user.private = True

    def store_media(self, user, media):
        '''Stores the given media, which must contain location information.
        user: The User object associated with the media.
        media: The JSON representation of the media.
        returns: Nothing.
        '''
        id_ = int(media['id'].split('_')[0])
        if self.session.query(Media).get(id_) is None:
            if media['caption'] is not None and 'text' in media['caption']:
                caption = media['caption']['text']
            else:
                caption = None
            location = self.store_location(media['location'])

            media_obj = Media(id=id_,
                date=datetime.fromtimestamp(int(media['created_time'])),
                caption=caption,
                tags=media['tags'],
                type=media['type'],
                user=user,
                location=location)
            self.session.add(media_obj)

    def store_location(self, location):
        '''Stores the given location in the database.
        location: The JSON representation of the location as returned
            by the Instagram API.
        return: The Location object.
        '''
        id_ = int(location['id'])
        location_obj = self.session.query(Location).get(id_)
        if location_obj is None:
            location_obj = Location(id=id_,
                name=location['name'],
                latitude=float(location['latitude']),
                longitude=float(location['longitude']))
            self.session.add(location_obj)
        return location_obj

    def seed_by_location(self, lat, lng):
        '''Uses the /media/search API endpoint to initialize the search queue
        with a number of users.
        lat: The latitude.
        lng: The longitude.
        returns: Nothing.
        '''
        content = self.client.search(lat, lng)
        self.queue.extend(int(m['user']['id']) for m in content)

    def seed_from_database(self):
        '''Seeds the queue with random users for the database.
        returns: Nothing.
        '''
        limit = self.queue.maxlen - len(self.queue)
        self.queue.extend([u.id for u in random_users(self.session, limit)])

def get_args():
    import argparse
    parser = argparse.ArgumentParser()
    parser.add_argument('-v', '--verbose', action='store_true',
        help='Verbose output.')
    parser.add_argument('-c', '--create-tables', action='store_true',
        help='Create database tables.')
    parser.add_argument('-l', '--seed-location', metavar='LAT,LONG',
        help='Seed crawler with recent users at the given location.')
    parser.add_argument('-e', '--max-except', type=int, default=10,
        help='Maximum number of exceptions before exiting (default 10).')
    args = parser.parse_args()

    if args.seed_location is not None:
        try:
            args.seed_location_split = [float(c) for c in args.seed_location.split(',')]
            assert(len(args.seed_location_split) == 2)
        except:
            parser.error('Invalid format for seed location: {0}'.format(args.seed_location))
    else:
        args.seed_location_split = None

    try:
        args.dbstring = os.environ['DBSTRING']
    except:
        parser.error('DBSTRING must be provided as an environmental variable.')

    try:
        args.client_id = os.environ['INSTAGRAM_CLIENT_ID']
    except:
        parser.error('INSTAGRAM_CLIENT_ID must be provided as an environmental variable.')

    return args

def stop_crawler(crawler):
    print('Stopping crawler...')
    crawler.stop = True

def main():
    args = get_args()
    engine = create_engine(args.dbstring)

    if args.create_tables:
        print('Creating tables...')
        create_tables(engine)
        return

    session = create_session(engine)
    crawler = InstagramCrawler(session, args.client_id, args.verbose, max_except=args.max_except)

    if args.seed_location is not None:
        print('Seeding by location...')
        crawler.seed_by_location(args.seed_location_split[0], args.seed_location_split[1])
    else:
        print('Seeding from database...')
        crawler.seed_from_database()

    # Setup handler so that the interrupt signal can be caught and
    # the crawler can exit cleanly.
    signal.signal(signal.SIGINT, lambda s, f: stop_crawler(crawler))
    crawler.run()

    session.close()
    engine.dispose()

if __name__ == '__main__':
    main()
