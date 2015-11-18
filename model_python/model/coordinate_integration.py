# ASU CSE 591
# Author: Group 4

import codecs


model_filename = r'C:\Users\Vincent\workspace\cse591_swm_project\sorted_labeled.csv'
coor_filename = r'C:\Users\Vincent\workspace\cse591_swm_project\classes.csv'
new_filename = r'C:\Users\Vincent\workspace\cse591_swm_project\new_sorted_labeled.csv'


#OPEN FILE USING UNICODE
model_file = codecs.open(model_filename, mode = 'rb', encoding = 'utf-8')
coor_file = codecs.open(coor_filename, 'rb', encoding='utf-8')
new_file = codecs.open(new_filename, mode='w', encoding='utf-8')

index = 0

for row in model_file:
    data = row.rstrip('\n')
    data = data.split(',')
    coordinates = coor_file.next()
    coords = coordinates.rstrip('\n')
    coords = coords.split(',')


    lat = coords[1]
    lon = coords[2]


    data.insert(2, lon)
    data.insert(2, lat)
    data.remove('')

    #new_file.write('{0}\n'.format(data))
    for ii in data:
        new_file.write(ii + ',')
    new_file.write('\n')



    print index
    index +=1
