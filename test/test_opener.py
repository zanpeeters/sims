#!/usr/bin/env python
# -*- encoding: utf-8 -*-
"""
Find all sims (.im, .is, .ls) files in dirs (non-recursive) and open them.
Print list of files with problem.
"""
import sims
import glob, os


# list all dirs to search
dirs = ['.', './more']



filepattern = ['*.[il][ms]']
files = zip(dirs, filepattern * len(dirs))
files = [os.path.join(d, f) for d, f in files]
allfiles = []
for f in files:
    allfiles += glob.glob(f)

errors = []
for f in allfiles:
    # read only header
    fh = open(f, mode='rb')
    s = sims.SIMSBase(fh)
    s.peek()
    try:
        s.read_header()
    except:
        errors.append(f)
    fh.close()

if errors:
    print('Problems opening:')
    print('\n'.join(errors))