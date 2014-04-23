#!/usr/bin/env python3
# -*- coding: utf-8 -*-
""" Basic test of operation in python 3. """
import sims

fh = open('more/grain mode ciw.im', mode='rb')
s = sims.SIMSBase(fh)
s.peek()
s.read_header()
s.save_header('test_output_py3.txt')
fh.close()