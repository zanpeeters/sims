#!/usr/bin/env python2
# -*- coding: utf-8 -*-
""" Basic test of operation in python 2. """
import sims

fh = open('more/grain mode ciw.im', mode='rb')
s = sims.SIMSBase(fh)
s.peek()
s.read_header()
s.save_header('test_output_py2.txt')
fh.close()