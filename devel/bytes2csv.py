#!/usr/bin/env python
""" Read x bytes, print as nn hex value, 20 on a line, comma separated into csv file. """
import csv

readbytes = 27400
linelength = 20
filename = 'OR1d6m_15.im'



fh = open(filename, mode='rb')
bytes = fh.read(readbytes)
fh.close()

bytes = [bytes[i:i+linelength] for i in range(0, readbytes, linelength)]

fh = open(filename + '.csv', mode='wt', encoding='utf-8')
csvwriter = csv.writer(fh)
for row in bytes:
    row = ["'{:02X}'".format(b) for b in row]
    csvwriter.writerow(row)

fh.close()
