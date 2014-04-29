#!/usr/bin/env python
# -*- encoding: utf-8 -*-
"""
Find all sims (.im, .is, .ls) files in dirs (non-recursive) and open them.
Print list of files with problem.
"""
import sims
import glob, os, itertools

# list all dirs and file extensions to search
dirs = ['.', './more']
filepatterns = ['*.is', '*.ls', '*.im', '*.dp']



files = itertools.product(dirs, filepatterns)
files = [os.path.join(d, f) for d, f in files]
allfiles = []
for f in files:
    allfiles += glob.glob(f)

print('Trying to open {} files...'.format(len(allfiles)))
errors = []
for f in allfiles:
    s = sims.SIMSOpener(f)
    s.peek()
    try:
        s.read_header()
    except:
        errors.append(f)
    s.close()

if errors:
    print('Problems opening:')
    print('\n'.join(errors))