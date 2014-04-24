#!/usr/bin/env python
# -*- coding: utf-8 -*-
""" Test opening of a bunch of compressed files. """

import sims

allfiles = ['first_test.im',
            'first_test.im.bz2',
            'first_test.im.gz',
            'first_test.im.xz',
            'first_test.im.zip']

for f in allfiles:
    # read only header
    try:
        s = sims.SIMSOpener(f)
        s.peek()
        s.read_header()
        s.close()
    except Exception as err:
        print(f, ":", err)
