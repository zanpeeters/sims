#!/usr/bin/env python
# -*- encoding: utf-8 -*-
import sims
import glob, os

d = '/Users/zan/Documents/Scripts/Python/sims/test2'
keywords = ['file type',  'scan type', 'NanoSIMSHeader/mode', 'NanoSIMSHeader/regulation mode', 'NanoSIMSHeader/semigraphic mode', 'NanoSIMSHeader/grain mode', 'analysis type',]


pr = '{:<15}'
pr *= len(keywords)
pr = '{:<30}' + pr
print(pr.format('file name', *keywords))

files = glob.glob(os.path.join(d, "*.im")) + glob.glob(os.path.join(d, "*.is")) + glob.glob(os.path.join(d, "*.ls")) + glob.glob(os.path.join(d, "../test/*.im"))
errors = []
for f in files:
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
    results = []
    for k in keywords:
        klist = k.split('/')
        kstr = 's.header' + '["{}"]' * len(klist)
        results += [kstr.format(*klist)]
    
    # eval results
    for r in range(len(results)):
        try:
            results[r] = eval(results[r])
        except KeyError:
            results[r] = '---'
    
    # print results
    print(pr.format(os.path.basename(f), *results))

if errors: print('Problems with:', errors)