#!/usr/bin/env python
# -*- encoding: utf-8 -*-
"""
Find all sims (.im, .is, .ls) files in dirs (non-recursive) and open them.

Print list of files with problem.
"""
import sims
import glob, os

###########################
# list all dirs to search
dirs = ['.', './more']

# List dict keywords to print, give keywords as path,
# e.g. 'Image/width' to print sims.header['Image']['width']
keywords = ['sample type', 'analysis type', 'scan type', 'size type', 'NanoSIMSHeader/detector type', 'NanoSIMSHeader/scanning type',]

###########################

filepattern = ['*.[il][ms]']
files = zip(dirs, filepattern * len(dirs))
files = [os.path.join(d, f) for d, f in files]
allfiles = []
for f in files:
    allfiles += glob.glob(f)

errors = []
for f in allfiles:
    # read header
    fh = open(f, mode='rb')
    s = sims.SIMSBase(fh)
    s.peek()
    try:
        s.read_header()
    except:
        errors.append(f)
    fh.close()
    
    # convert list into dict keywords
    results = [k.split('/') for k in keywords]
    # kstr = 's.header' + '["{}"]' * len(klist)
    # results += [kstr.format(*klist)]
    print(results)
    
    # # eval results
    # for r in range(len(results)):
    #     try:
    #         results[r] = eval(results[r])
    #     except KeyError:
    #         results[r] = '---'
    # 
    # # print results
    # print(pr.format(os.path.basename(f), *results))

if errors:
    print('Problems opening:')
    print('\n'.join(errors))