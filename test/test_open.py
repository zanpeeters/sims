#!/usr/bin/env python

import os
import pkg_resources
import unittest
import warnings

import numpy as np

import sims

filedir = pkg_resources.resource_filename(__name__, 'files')

test_open_im_files = [
    'first_test.im',
    'OpenMIMS_doc_040702_06-05mos-03a.im',
    'OpenMIMS_doc_051117_05-32dRe-03b.im',
    'OR1d6m_15.im.bz2',
    'OR1d6m_15corr.im.bz2',  # Limage output
    'RTIimage.im'
]

test_open_compressed_files = [
    'first_test.im',
    'first_test.im.gz',
    'first_test.im.bz2',
    'first_test.im.xz',
    'first_test.tar.bz2'
]

test_open_other_files = [
    'beamstability.bs',
    'depth profile.dp',
    'grain mode cameca.im',
    'grain mode ciw.im',
    'hmr_16O.hmr',
    'image sample scan 1.ls',
    'leakcurrent.lc',
    'line scan beam control.im',
    'line scan stage control 1.ls',
    'line scan stage control 2.ls',
    'line scan stage control 3.ls',
    'is01 R1 KH03-27 01.is',
    'oxy1618_FC.is',
    'sample stage image.ls',
    'SIB_horizontal.sib',
    'SIB_vertical.sib',
    'trolley_step_scan.tss'
]

test_open_not_supported_files = [
    'c4xscan.tls',
    'energy_scan.nrj',
    'E0S.e0s',
    'PHDscan_raw.phd',
    'PHDscan.phd',
]

class TestOpen(unittest.TestCase):
    """ Unittest for all input files in test dir. """
    def setUp(self):
        """ Ignore ResourceWarnings, since opening of unsupported files may fail. """
        warnings.simplefilter("ignore", ResourceWarning)

    def test_open_im(self):
        """ Test opening of different flavours of the .im file. """
        for f in test_open_im_files:
            f = os.path.join(filedir, f)
            with self.subTest(f=f):
                with sims.SIMS(f) as s:
                    pass

    def test_open_compressed(self):
        """ Test that opening of compressed files works. """
        uncf = os.path.join(filedir, test_open_compressed_files[0])
        unc = sims.SIMS(uncf)
        for f in test_open_compressed_files[1:]:
            f = os.path.join(filedir, f)
            with self.subTest(f=f):
                with sims.SIMS(f) as compr:
                    self.assertEqual(unc.header, compr.header)
                    np.testing.assert_equal(unc.data.values, compr.data.values)

    def test_open_other(self):
        """ Test opening of other file types. """
        for f in test_open_other_files:
            f = os.path.join(filedir, f)
            with self.subTest(f=f):
                with sims.SIMS(f) as s:
                    pass

    def test_open_not_supported(self):
        """ Test that unsupported but recognized file types fail. """
        for f in test_open_not_supported_files:
            f = os.path.join(filedir, f)
            with self.subTest(f=f):
                with self.assertRaises(NotImplementedError) as err:
                    with sims.SIMS(f) as s:
                        pass

if __name__ == '__main__':
    unittest.main(verbosity=2)
