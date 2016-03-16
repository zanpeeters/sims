#!/usr/bin/env python
# -*- coding: utf-8 -*-
""" Tools and utilities for the sims module. """
from __future__ import print_function, division
import re
import sims
import numpy as np

def format_species(name, mhchem=False, mathrm=False):
    """ Format the name of a chemical species.

        Usage: formatted_name = format_species(name, mhchem=False)

        Takes a string which represent of an atomic or molecular species
        and returns a string with LaTeX-style sub- and superscripts for input
        into Matplotlib. Multiple atoms in the input string are expected to be
        space-separated within a molecule, as in Cameca formatting. Irregularly
        formatted strings are silently skipped.
        
        If mhchem=True, the \ce command from the mhchem package is used to
        format species names. This gives much better results, but requires LaTeX
        and mhchem to be installed. For this to typeset properly in matplotlib,
        set 'text.usetex' to True and include '\\usepackage[version=3]{mhchem}'
        in 'text.latex.preamble' (or 'pgf.preamble' when exporting as pgf) in
        rcParams or matplotlibrc.
        
        If mathrm=True, a \mathrm command is inserted, to typeset upright
        letters. Useful when the full LaTeX engine is used (text.usetex: True in
        matplotlibrc); LaTeX typesets math mode text in italic by default. This
        option is ignored if mhchem=True.

        Example:
        >>> format_species('12C2 2H')
        '${}^{12}C_{2}{}^{2}H_{}$'

        >>> format_species('12C2 2H', mathrm=True)
        '$\mathrm{{}^{12}C_{2}{}^{2}H_{}}$'
        
        >>> format_species('12C2 2H', mhchem=True)
        '\ce{^{12}C}
    """
    # {} is format subst. {{}} is literal {} after expansion.
    # {{{}}} is a subst inside a literal {} after expansion.
    # First {{}} (expands to {}) aligns superscript with following
    # character, not previous.
    # The string 1A2 expands to: '{}^{1}A_{2}'
    template = '{{}}^{{{mass}}}{elem}_{{{stoich}}}'

    if mhchem:
        begin = '\ce{'
        end = '}'
        template = '^{{{mass}}}{elem}{stoich}'
    elif mathrm:
        begin = '$\mathrm{'
        end = '}$'
    else:
        begin = end = '$'

    atoms = name.split()
    body = ''
    for a in atoms:
        parts = re.split('([a-zA-Z]+)', a)
        if len(parts) != 3: return ''
        body += template.format(mass=parts[0], elem=parts[1], stoich=parts[2])

    return begin + body + end


def thumbnails(data, cycle=0, mass=None, labels=None):
    """ Generate a thumbnail sheet. """
    if not mass: mass = range(data.shape[0])
    mass = list(mass)

    row = int(np.floor(np.sqrt(len(mass))))
    diff = np.sqrt(len(mass)) - row
    if diff == 0:
        # square, MxM
        col = row
    elif diff < 0.5:
        # not square, MxN
        col = row + 1
    else:
        # next square, NxN
        row += 1
        col = row

    fig = figure(figsize=(14.2222, 14.2222), dpi=72, facecolor='white')
    for m, n in zip(mass, range(len(mass))):
            ax = mpl.subplot(row, col, n+1)
            ax.axis('off')
            ax.imshow(data[m, cycle])
            if labels: axim.title(labels[n])
            # mpl.colorbar()

    mpl.show()

def coordinates(filelist, **kwargs):
    """ Find all coordinates in a list of image files.

        Usage: fig = sims.coordinates([a.im, b.im], labels=['A', 'B'])

        For each image in the list, the stage coordinates and raster size are extracted.
        A box scaled to the rastersize is plotted for each image on a (X,Y) grid.
        A label for each file can be given. If it's omitted, the filename will be printed
        over the box, but no attempt is made to make the filename fit in the box.

        Returns a matplotlib figure instance.
    """
    from matplotlib.patches import Rectangle
    from matplotlib.collections import PatchCollection
    import matplotlib.pyplot as mpl

    labels = kwargs.pop('labels', filelist)

    patches = []
    x_min = None
    fig = mpl.figure()
    ax = fig.gca()

    for fn, lb in zip(filelist, labels):
        s = sims.SIMSOpener(fn)
        s.peek()
        s.read_header()
        s.close()

        # Remember!! In Cameca-land, X is up-down, Y is left-right. Fix here.
        x = s.header['sample y']
        y = s.header['sample x']

        if (x == 0 and y == 0):
            warnings.warn('Coordinates for file {} are 0,0.'.format(fn))

        if (('PrimaryBeam' in s.header.keys()) and ('raster' in s.header['PrimaryBeam'].keys())):
            raster = s.header['PrimaryBeam']['raster']
        elif (('Image' in s.header.keys()) and ('raster' in s.header['Image'].keys())):
            raster = s.header['Image']['raster']/1000
        else:
            warnings.warn('No raster size in header of file {}.'.format(fn))
            raster = 0

        # Text uses center of image coordinate, box uses lower left.
        ax.text(x, y, lb, ha='center', va='center', fontsize=8)

        x -= raster/2
        y -= raster/2

        # Update data limits, relim() used by autoview does not work with collections (yet)
        if not x_min:
            # first pass
            x_min = x
            x_max = x + raster
            y_min = y
            y_max = y + raster
        else:
            if x < x_min: x_min = x
            if x + raster > x_max: x_max = x + raster
            if y < y_min: y_min = y
            if y + raster > y_max: y_max = y + raster

        rect = Rectangle((x,y), raster, raster, ec='black', fc='white', fill=False)
        patches.append(rect)

    collection = PatchCollection(patches, match_original=True)
    ax.add_collection(collection)

    # Set view limits
    x_span = x_max - x_min
    y_span = y_max - y_min
    # Remember!! In Cameca-land, X-axis (up-down) is positive downwards,
    # Y-axis (left-right) is positive going right.
    ax.set_xlim(x_min - 0.1*x_span, x_max + 0.1*x_span)
    ax.set_ylim(y_max + 0.1*y_span, y_min - 0.1*y_span)

    # Keep it square
    ax.set_aspect('equal', adjustable='datalim')
    ax.set_xlabel('Stage Y (μm)')
    ax.set_ylabel('Stage X (μm)')
    return fig


def export_fits(data, filename, extend=False, **kwargs):
    """ Export data to a FITS file.

        Data can be pandas data structure or numpy ndarray.
        Filename is the filename the FITS will will be saved to.

        By default, the data structure will be saved as is, i.e.
        a 3D data cube will be saved as such. Set extend=True to
        unroll the outer dimension and save the remaining data
        structures as a list of HDUs (Header Data Units, see
        FITS documentation); a 3D data cube will be saved as a
        list of 2D images.

        Additional arguments are sent to fits.writeto(), see
        astropy.io.fits for more info.
    """
    fits = None
    try:
        from astropy.io import fits
    except ImportError:
        pass

    try:
        import pyfits as fits
    except ImportError:
        pass

    if not fits:
        msg = 'You need to install either pyfits or astropy to be able to export FITS files.'
        raise ImportError(msg)

    if hasattr(data, 'values'):
        # any of the pandas data structures; extract ndarray
        data = data.values

    # FITS supports the following data formats, given as BITPIX in header
    # BITPIX    format
    # 8         unsigned 8-bit integer
    # 16        signed 16-bit integer
    # 32        two's complement 32-bit integer
    # -32       IEEE 32-bit float
    # -64       IEEE 64-bit float

    # PyFITS handles uint16 and uint32 by storing as int16 and int32
    # and setting BZERO offset. int8, float16, and float128 give KeyError.

    # PyFITS will save (u)int64, but is non standard and at least Source Extractor
    # does not recognise it. Downcast if possible, error otherwise. Everything
    # else is handled by PyFITS.

    if data.dtype in (np.uint64, np.int64):
        if (data.min() >= np.iinfo('i4').min and
            data.max() <= np.iinfo('i4').max):
                data = data.astype('i4', copy=False)
        elif (data.min() >= np.iinfo('u4').min and
            data.max() <= np.iinfo('u4').max):
                data = data.astype('u4', copy=False)
        else:
            msg = 'Data is (u)int64 and cannot safely be downcast to (u)int32.'
            raise ValueError(msg)
    elif data.dtype == np.int8:
        if data.min() >= 0:
            data = data.astype('u1', copy=False)
        else:
            data = data.astype('i2', copy=False)
    elif data.dtype == np.float16:
        data = data.astype('f4', copy=False)
    elif data.dtype == np.float128:
        if (data.min() >= np.finfo('f8').min and
            data.max() <= np.finfo('f8').max) :
                data = data.astype('f8', copy=False)
        else:
            msg = 'Data is float128 and cannot safely be downcast to float64.'
            raise ValueError(msg)

    if extend:
        hl = [fits.PrimaryHDU()] + [fits.ImageHDU(n) for n in data]
        hdulist = fits.HDUList(hl)
        hdulist.writeto(filename, **kwargs)
    else:
        hdu = fits.PrimaryHDU(data)
        hdu.writeto(filename, **kwargs)
