# ASU CSE 591
# Author: Group 4

#!/usr/bin/env python

import sys
import csv
import random

STYLE = """<Style id="random{id}"><IconStyle>
<color>{color}</color>
<Icon><href>http://maps.google.com/mapfiles/kml/shapes/placemark_circle.png</href></Icon>
</IconStyle></Style>
"""

PLACEMARK = """<Placemark>
<description>{tags}</description>
<styleUrl>#random{id}</styleUrl>
<Point><coordinates>{lon},{lat}</coordinates></Point>
</Placemark>
"""

def xml_escape(s):
    return (s.replace('&', '&amp;')
        .replace('"', '&quot;')
        .replace("'", '&apos;')
        .replace('<', '&lt;')
        .replace('>', '&gt;'))

def random_color():
    return 'ff{0:02x}{1:02x}{2:02x}'.format(
        random.randint(0, 255),
        random.randint(0, 255),
        random.randint(0, 255))

def main():
    if len(sys.argv) != 3:
        print("Usage: ./clusters_to_kml.py INPUTFILE OUTPUTFILE")
        return

    # Generate KML by formatting strings rather than building a DOM
    # in memory with xml.minidom because files may potentially be too
    # large to hold in memory at once.
    with open(sys.argv[1], 'r') as inf:
        reader = csv.reader(inf)
        colorset = set(r[4] for r in reader)
        colors = { c: random_color() for c in colorset }

    with open(sys.argv[1], 'r') as inf:
        with open(sys.argv[2], 'w') as outf:
            outf.write('<kml>\n<Document>\n')
            for k, v in colors.items():
                outf.write(STYLE.format(id=k, color=v))

            reader = csv.reader(inf)
            for line in reader:
                outf.write(PLACEMARK.format(tags=xml_escape(line[1]),
                    lat=line[2], lon=line[3], id=line[4]))
            outf.write('</Document>\n</kml>\n')

if __name__ == '__main__':
    main()
