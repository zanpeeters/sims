#!/usr/bin/env python
""" Test opening of a wide range of files with the sims module. """

import numpy as np
import os
import pkg_resources
import pytest

import sims

test_open_im_files = [
    'first_test.im',
    'OpenMIMS_doc_040702_06-05mos-03a.im',
    'OpenMIMS_doc_051117_05-32dRe-03b.im',
    'image windows.im',
    'image sun.im',
    'image limage.im',
    'RTIimage.im'
]

# First file is uncompressed for comparison
test_open_compressed_files = [
    'first_test.im',
    'first_test.im.gz',
    'first_test.im.bz2',
    'first_test.im.xz',
    'first_test.im.zip',
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

filedir = pkg_resources.resource_filename(__name__, 'files')


def path(file):
    return os.path.join(filedir, file)


@pytest.fixture()
def uncompressed():
    """ Open uncompressed .im file for comparison to compressed files. """
    s = sims.SIMS(path(test_open_compressed_files[0]))
    return s


@pytest.mark.parametrize('file', test_open_im_files)
def test_open_im(file):
    """ Test opening of different flavours of the .im file. """
    with sims.SIMS(path(file)):
        pass


@pytest.mark.parametrize('file', test_open_compressed_files[1:])
def test_open_compressed(file, uncompressed):
    """ Test that opening of compressed files works. """
    with sims.SIMS(path(file)) as compressed:
        assert uncompressed.header == compressed.header
        np.testing.assert_equal(uncompressed.data.values,
                                compressed.data.values)


@pytest.mark.parametrize('file', test_open_other_files)
def test_open_other(file):
    """ Test opening of other file types. """
    with sims.SIMS(path(file)):
        pass


@pytest.mark.parametrize('file', test_open_not_supported_files)
def test_open_not_supported(file):
    """ Test that unsupported but recognized file types fail. """
    with pytest.raises(NotImplementedError):
        with sims.SIMS(path(file)):
            pass


if __name__ == '__main__':
    pytest.main(args=['-v', __file__])
