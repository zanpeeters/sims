#!/usr/bin/env python
# -*- encoding: utf-8 -*-

import unittest
import sims

test_files = [
    'first_test.im',
    'OpenMIMS_doc_040702_06-05mos-03a.im',
    'OpenMIMS_doc_051117_05-32dRe-03b.im',
    'OR1d6m_15.im.bz2',
    'OR1d6m_15corr.im.bz2',  # Limage output
]

test_compression_files = [
    'first_test.im',
    'first_test.im.gz',
    'first_test.im.bz2',
    'first_test.im.xz',
    'first_test.tar.bz2'
]

class TestAllInput(unittest.TestCase):
    """ Unittest for all input files in test dir. """
    
    def test_compression(self):
        """ Test that opening of compressed files works. """
        uncompressed = sims.SIMS(test_compression_files[0])
        for f in test_compression_files[1:]:
            with self.subTest(f=f):
                with sims.SIMS(f) as compressed:
                    self.assertEqual(uncompressed.header, compressed.header)
                    if sims.pd:
                        sims.np.testing.assert_equal(uncompressed.data.values,
                            compressed.data.values)
                    else:
                        sims.np.testing.assert_equal(uncompressed.data,
                            compressed.data)

    def test_open_im(self):
        """ Test opening of different flavours of the .im file. """
        for f in test_files:
            with self.subTest(f=f):
                with sims.SIMS(f) as s:
                    pass

if __name__ == '__main__':
    unittest.main(verbosity=2)
