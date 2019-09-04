""" Convert and load Cameca and L'image look-up tables.

    Module to load Look-Up Tables (LUTs) from the Cameca-supplied lut files and
    the L'image colour table file. Loaded LUTs are converted to Matplotlib
    Colormaps, and registered with Matplotlib. Use mpl.colormaps() to see all
    registered Colormaps and mpl.set_cmap() to set the Colormap to use.

    The Cameca and L'image LUTs are distributed with the sims module. Cameca
    .lut files are located in the LUT directory which is stored as
    sims.lut.lut_dir. The L'image Colour Table file is located in the same
    directory and is stored as sims.lut.limage_file. Both can be overriden to
    load from a different directory.
"""
import matplotlib.pyplot as mpl
import matplotlib.colors as mpc
import numpy as np
import os
import pkg_resources

__all__ = ['load_cameca_lut', 'load_limage_lut']

lut_dir = pkg_resources.resource_filename(__name__, 'lut')
limage_file = os.path.join(lut_dir, 'limagecolors.tbl')


def load_cameca_lut(*names, smooth=True):
    """ Load a Cameca Look-Up Table.

        Usage: load_cameca_lut('cameca bw', 'temp', ..., smooth=True)

        Reads one or more LUTs from the Cameca supplied .lut files, converts it
        to a Matplotlib Colormap and registers it with Matplotlib. The
        Colormaps will be registered with a 'cameca ' prefix to distinguish
        them from the L'image LUTs and Matplotlib's own Colormaps. To (re)load
        a specific LUT, the 'cameca ' prefix may be omitted. If no name is
        given, all LUTs in the lut-directory will be loaded.

        By default, a smooth gradient Colormap (LinearSegmentedColormap) is
        created from the data in the LUT. Some LUTs, however, are meant to be
        used with hard edges and strong contrast between adjacent colours. To
        disable the smooth gradient, set smooth=False and a ListedColormap will
        be created instead.
    """
    if len(names) == 0:
        fnames = os.listdir(lut_dir)
        fnames = [os.path.join(lut_dir, n) for n in fnames if n.endswith('.lut')]
    else:
        names = [n[7:] for n in names if n.startswith('cameca ')]
        fnames = [os.path.join(lut_dir, n, '.lut') for n in names]

    # All luts need to be 0-1 normalized
    norm = mpc.Normalize(vmin=0, vmax=255)

    for fname in fnames:
        with open(fname, mode='rb') as fh:
            # skip header
            fh.seek(76)
            # Data is in single bytes, 3 rows of length 128 or 256.
            # Data is also doubled, each row appears twice (from-to?)
            lut_data = np.fromfile(fh, dtype='B').reshape(3, -1).T[::2]

        name = os.path.basename(fname)
        name = 'cameca ' + os.path.splitext(name)[0]

        lut_data = norm(lut_data)

        if smooth:
            lut = mpc.LinearSegmentedColormap.from_list(name, lut_data)
        else:
            lut = mpc.ListedColormap(lut_data, name=name)
        mpl.register_cmap(cmap=lut)


def load_limage_lut(*names, smooth=True):
    """ Load a L'image Look-Up Table.

        Usage: load_limage_lut('blue/white', 'limage prism', ..., smooth=True)

        Reads one or more LUTs from the L'image colortable file, converts it to
        a Matplotlib Colormap and registers it with Matplotlib. The Colormaps
        will be registered with a 'limage ' prefix to distinguish them from the
        Cameca-supplied LUTs and Matplotlib's own Colormaps. To (re)load a
        specific LUT, the 'limage ' prefix may be omitted. If no name is given,
        all LUTs in the file will be loaded.

        By default, a smooth gradient Colormap (LinearSegmentedColormap) is
        created from the data in the LUT. Some LUTs, however, are meant to be
        used with hard edges and strong contrast between adjacent colours. To
        disable the smooth gradient, set smooth=False and a ListedColormap will
        be created instead.
    """
    names = [n[7:] for n in names if n.startswith('limage ')]

    fh = open(limage_file, mode='rb')
    hdr = fh.read(32)
    data = fh.read()
    fh.close()

    if 'PV-WAVE CT' not in hdr[:16].decode('utf-8'):
        raise TypeError('File {} is not a PV-Wave ColorTable.'
                        ''.format(limage_file))

    # All luts need to be 0-1 normalized
    norm = mpc.Normalize(vmin=0, vmax=255)

    # Number of LUTs stored as number in an ascii-encoded, null-padded string
    n_luts = hdr[16:].decode('utf-8').strip(' \x00')
    n_luts = int(n_luts)
    offset = 32 * n_luts
    for l in range(n_luts):
        start = l * 32
        end = start + 32
        name = data[start:end].decode('utf-8').strip(' \x00').lower()

        # len(name) == 0 -> load all
        # lut_name in names -> this is (the) one we need
        # not name.startswith('?') -> signifies empty table entry
        if (not name.startswith('?')) and (name in names or len(names) == 0):
            name = 'limage ' + name
            start = offset + l * 768
            end = start + 768
            lut_data = np.fromstring(data[start:end], dtype='B').reshape(3, 256).T
            lut_data = norm(lut_data)

            if smooth:
                lut = mpc.LinearSegmentedColormap.from_list(name, lut_data)
            else:
                lut = mpc.ListedColormap(lut_data, name=name)

            mpl.register_cmap(cmap=lut)
