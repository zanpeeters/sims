#!/usr/bin/env python2
# -*- coding: utf-8 -*-
""" Basic test of operation in python 2. """
import sims

s = sims.SIMSOpener('more/grain mode ciw.im')
s.peek()
s.read_header()
s.save_header('test_output_py2.txt')
s.close()