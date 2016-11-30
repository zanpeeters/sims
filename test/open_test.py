#!/usr/bin/env python
# -*- encoding: utf-8 -*-

import os
import unittest
import sims

filedir = 'files'

test_compression_files = [
    'first_test.im',
    'first_test.im.gz',
    'first_test.im.bz2',
    'first_test.im.xz',
    'first_test.tar.bz2'
]

test_open_im_files = [
    'first_test.im',
    'OpenMIMS_doc_040702_06-05mos-03a.im',
    'OpenMIMS_doc_051117_05-32dRe-03b.im',
    'OR1d6m_15.im.bz2',
    'OR1d6m_15corr.im.bz2',  # Limage output
]

test_open_other_files = [
	'depth profile.dp',
	'grain mode cameca.im',
	'grain mode ciw.im',
	'image sample scan 1.ls',
	'line scan beam control.im',
	'line scan stage control 1.ls',
	'line scan stage control 2.ls',
	'line scan stage control 3.ls',
	'oto_std1_chain2_10.is',
	'oxy1618_FC.is',
	'sample stage image.ls'
]

class TestAllInput(unittest.TestCase):
    """ Unittest for all input files in test dir. """
    def test_compression(self):
        """ Test that opening of compressed files works. """
        uncf = os.path.join(filedir, test_compression_files[0])
        unc = sims.SIMS(uncf)
        for f in test_compression_files[1:]:
            f = os.path.join(filedir, f)
            with self.subTest(f=f):
                with sims.SIMS(f) as compr:
                    self.assertEqual(unc.header, compr.header)
                    if sims.pd:
                        sims.np.testing.assert_equal(unc.data.values, compr.data.values)
                    else:
                        sims.np.testing.assert_equal(unc.data, compr.data)

    def test_open_im(self):
        """ Test opening of different flavours of the .im file. """
        for f in test_open_im_files:
            f = os.path.join(filedir, f)
            with self.subTest(f=f):
                with sims.SIMS(f) as s:
                    pass

    def test_open_other(self):
        """ Test opening of other file types. """
        for f in test_open_other_files:
            f = os.path.join(filedir, f)
            with self.subTest(f=f):
                with sims.SIMS(f) as s:
                    pass

if __name__ == '__main__':
    unittest.main(verbosity=2)
