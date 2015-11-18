# ASU CSE 591
# Author: Group 4

class Classification():

    def __init__(self):
        self.ClassName = 0
        self.Freq = 0
        self.Tags = []


    def increaseCount(self):
        self.Freq += 1


class TableRow():

    def __init__(self):
        self.ClassName = 0
        self.Tags = []

    def initVals(self):
        self.Tags = []

    def stripAndAdd(self, vals):
        self.initVals()
        tags = vals.strip('{}')      #strip all tags of the curly braces
        tags = tags.split(',')      #split resulting tags by comma

        for ii in tags:
            self.Tags.append(ii)


class Probability:

    def __init__(self):
        self.likilihoods = []
        self.priors = []
        self.posteriors = []
        self.classes = []
        self.latitudes = []
        self.longitudes = []


class Coordinates:
    def __init__(self):
        self.lat = 0
        self.lon = 0
        self.confidence = 0
        self.classNum = 0
