# ASU CSE 591
# Author: Group 4

import UtilityClasses
import operator
import codecs
import platform
import json
import mmap



#function searches through the provided model file, calculates posterior probabilities, and returns most likely class(es)
def findOptimalClass(Given_Tags, model_filename, return_num):

    #OPEN FILE USING UNICODE
    file = codecs.open(model_filename, mode = 'rb', encoding = 'utf-8')

    #IGNORE EMPTY TAG SETS
    if Given_Tags == []:
        pass
    else:

        p = UtilityClasses.Probability()
        index = 0

        #ITERATE THROUGH EACH CLASS
        for row in file:

            #EXTRACT UNICODE ROW DATA FROM FILE

            data = row.split(',')
            p.priors.append(data[1])
            p.classes.append(data[0])
            p.latitudes.append(data[2])
            p.longitudes.append(data[3])

            last_tag_index = len(data)-1

            #Tags = data[2:last_tag_index]  before coordinates were integrated
            Tags = data[4:last_tag_index]
            denominator = last_tag_index - 4


            temp_likilihood = 1
            for tag in Given_Tags:

                #CALCULATE LIKILIHOOD RATIO
                temp_likilihood *= Tags.count(tag) / float(denominator)

            #CALCULATE POSTERIOR PROBABILITY
            temp_posterior = temp_likilihood * float(p.priors[index])
            p.posteriors.append(temp_posterior)


            index += 1




        #SORT ON CLASSNAME
        posteriors = p.posteriors[:]
        posteriors.sort()
        posteriors.reverse()
        normalizing_val = sum(posteriors)
        if normalizing_val == 0:
            normalizing_val =1


        highest_probs = posteriors[0:return_num]


        classes = []
        for ii in highest_probs:
            c = UtilityClasses.Coordinates()
            c.confidence = float(ii) / normalizing_val
            index = int(p.posteriors.index(ii))
            c.classNum = int(index)
            c.lat = float(p.latitudes[index])
            c.lon = float(p.longitudes[index])
            classes.append(c)

        print 'The top {0} classes and their longitude include: '.format(return_num)
        for ii in classes:
            print 'Class: {0}, Lat/Long: {1},{2}, Confidence: {3}'.format(c.classNum, ii.lat, ii.lon, ii.confidence)

        return classes
        file.seek(0)


#FASTER function searches through the provided model file, calculates posterior probabilities, and returns most likely class(es)
def findOptimalClassMmap(Given_Tags, model_filename, return_num):

    smoothing_constant = 0.001

    #OPEN FILE USING UNICODE
    file = codecs.open(model_filename, mode = 'rb')
    f = mmap.mmap(file.fileno(), length=0, access=mmap.ACCESS_READ)


    #IGNORE EMPTY TAG SETS
    if Given_Tags == []:
        pass
    else:
        p = UtilityClasses.Probability()
        index = 0

        #ITERATE THROUGH EACH CLASS
        while True:
            row = f.readline().decode('utf-8')
            if row == '':
                break

            #EXTRACT UNICODE ROW DATA FROM FILE
            data = row.split(',')
            p.priors.append(data[1])
            p.classes.append(data[0])
            p.latitudes.append(data[2])
            p.longitudes.append(data[3])

            last_tag_index = len(data)-1


            Tags = data[4:last_tag_index]
            denominator = last_tag_index - 4        #number of tags found in class


            temp_likilihood = 1
            for tag in Given_Tags:

                #CALCULATE LIKILIHOOD RATIO
                temp_likilihood *= (Tags.count(tag) + smoothing_constant) / (float(denominator) + smoothing_constant)

            #CALCULATE POSTERIOR PROBABILITY
            temp_posterior = temp_likilihood * float(p.priors[index])
            p.posteriors.append(temp_posterior)


            index += 1




        #SORT ON CLASSNAME
        posteriors = p.posteriors[:]
        posteriors.sort()
        posteriors.reverse()
        normalizing_val = sum(posteriors)
        if normalizing_val == 0:
            normalizing_val =1


        highest_probs = posteriors[0:return_num]


        classes = []
        for ii in highest_probs:
            c = UtilityClasses.Coordinates()
            c.confidence = float(ii) / normalizing_val
            index = int(p.posteriors.index(ii))
            c.classNum = int(index)
            c.lat = float(p.latitudes[index])
            c.lon = float(p.longitudes[index])
            classes.append(c)

        #print 'The top {0} classes and their longitude include: '.format(return_num)
        #for ii in classes:
            #print 'Class: {0}, Lat/Long: {1},{2}, Confidence: {3}'.format(c.classNum, ii.lat, ii.lon, ii.confidence)

        return classes
        #file.seek(0)


#function is web-facing.  It takes a returns a json object
def webFacingFindOptimalClass(json_request):

    json_req = json.loads(json_request)
    #filename = r'C:\Users\Vincent\workspace\cse591_swm_project\new_sorted_labeled.csv'
    filename = '/home/ubuntu/workspace/new_sorted_labeled.csv'

    #tags = given_tags.split(',')
    tags = list(json_req['tags'])
    numClasses = int(json_req['count'])


    #classes = findOptimalClass(tags, filename, numClasses)
    classes = findOptimalClassMmap(tags, filename, numClasses)

    #all_json = []
    #for ii in classes:
    #    json_obj = json.dumps(ii.__dict__)
    #    all_json.append(json_obj)
    #print 'ok got here'
    json_string = json.dumps([ii.__dict__ for ii in classes])
    #print json_string

    return json_string
