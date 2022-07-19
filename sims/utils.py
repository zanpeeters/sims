""" Tools and utilities for the sims module. """

import re
import json
import warnings
import datetime
import numpy as np
import xarray

from matplotlib.patches import Rectangle
from matplotlib.collections import PatchCollection
from scipy.ndimage import shift
from scipy.io import savemat
from skimage.registration import phase_cross_correlation

import sims
from sims.transparent import TransparentOpen

from matplotlib import is_interactive
if is_interactive():
    from matplotlib.pyplot import figure as Figure
else:
    from matplotlib.figure import Figure

try:
    from astropy.io import fits
except ImportError:
    try:
        import pyfits as fits
    except ImportError:
        fits = None

__all__ = [
    'coulomb',
    'ions_per_amp',
    'format_species',
    'thumbnails',
    'coordinates',
    'align',
    'em_correct',
    'fc_correct',
    'export_fits',
    'export_header',
    'export_matlab'
]

# Factor for converting A to counts/s on faraday cups.
# Cameca uses 6.24142e18, which is incorrect rounding: 6.241509... e18
# Number used here is NIST 2010CODATA
# http://physics.nist.gov/cgi-bin/cuu/Value?e
coulomb = 1.6021766208e-19  # charge/ion
ions_per_amp = 1/coulomb  # ions/A


def format_species(name, mhchem=False, mathrm=False):
    """ Format the name of a chemical species.

        Usage: formatted_name = format_species(name, mhchem=False)

        Takes a string which represents an atomic or molecular species
        and returns a string with LaTeX-style sub- and superscripts for input
        into Matplotlib. Multiple atoms in the input string are expected to be
        space-separated within a molecule, as in Cameca formatting. Irregularly
        formatted strings are silently skipped.

        If mhchem=True, then the \\ce command from the mhchem package is used to
        format species names. This gives much better results, but requires LaTeX
        and mhchem to be installed. For this to typeset properly in matplotlib,
        set 'text.usetex' to True and include '\\usepackage{mhchem}' in
        'text.latex.preamble' (or 'pgf.preamble' when exporting as pgf) in
        rcParams or matplotlibrc.

        If mathrm=True, a \\mathrm command is inserted, to typeset upright
        letters. Useful when the full LaTeX engine is used (text.usetex: True in
        matplotlibrc); LaTeX typesets math mode text in italic by default. This
        option is ignored if mhchem=True.

        Example:
        >>> format_species('12C2 2H')
        '${}^{12}C_{2}{}^{2}H$'

        >>> format_species('12C2 2H', mathrm=True)
        '$\\mathrm{{}^{12}C_{2}{}^{2}H}$'

        >>> format_species('12C2 2H', mhchem=True)
        '\\ce{{}^{12}C_{2}{}^{2}H}
    """
    # {} is format subst. {{}} is literal {} after expansion.
    # {{{}}} is a subst inside a literal {} after expansion.
    # First {{}} (expands to {}) aligns superscript with following
    # character, not previous.
    # The string 1A2 expands to: '{}^{1}A_{2}'
    mass_tmpl = '{{}}^{{{mass}}}'
    elem_tmpl = '{elem}'
    stoich_tmpl = '_{{{stoich}}}'
    charge_tmpl = '^{{{charge}}}'

    if mhchem:
        begin = '\\ce{'
        end = '}'
    elif mathrm:
        begin = '$\\mathrm{'
        end = '}$'
    else:
        begin = end = '$'

    body = ''
    for atom in name.split():
        if '+' in atom or '-' in atom:
            body += charge_tmpl.format(charge=atom)
            continue

        parts = re.split('([a-zA-Z]+)', atom)
        if not len(parts) == 3:
            continue

        if not parts[1]:
            continue
        else:
            elem = elem_tmpl.format(elem=parts[1])

        if parts[0]:
            mass = mass_tmpl.format(mass=parts[0])
        else:
            mass = ''

        if parts[2]:
            stoich = stoich_tmpl.format(stoich=parts[2])
        else:
            stoich = ''

        body += mass + elem + stoich

    return begin + body + end


def thumbnails(simsobj, frame=0, masses=[], labels=[], **kwargs):
    """ Generate a thumbnail sheet.

        Usage: fig = thumbnails(s)

        Generates a sheet of thumbnails for the masses in simsobj.

        Set frame=number to select a specific frame. By default the first
        frame (0) is used.

        Give a list of masses to select which masses to generate thumbnails of.
        By default a thumbnail is generated for all masses in simsobj.

        Set labels to a list of labels, one for each mass. By default the
        formatted labels from the simsobj.header are used.

        Additional keyword arguments are passed on to matplotlib.imshow().

        Returns a matplotlib figure instance. If you call this from 'interactive'
        mode (e.g. from ipython --pylab or jupyter), an active figure is shown.
        If it is not called from interactive mode, use fig.savefig('filename.pdf')
        to save the figure to file.
    """
    if not masses:
        masses = simsobj.data.species.values

    if not labels:
        labels = simsobj.header['label list fmt']

    rows = int(np.floor(np.sqrt(len(masses))))
    diff = np.sqrt(len(masses)) - rows
    if diff == 0:
        # square, MxM
        cols = rows
    elif diff < 0.5:
        # not square, MxN
        cols = rows + 1
    else:
        # next square, NxN
        rows += 1
        cols = rows

    fig = Figure(figsize=(14.2222, 14.2222), dpi=72, facecolor='white')
    plots = fig.subplots(nrows=rows, ncols=cols)

    for row in range(rows):
        for col in range(cols):
            ax = plots[row, col]
            ax.axis('off')
            n = row * cols + col
            if n < len(masses):
                mass = masses[n]
                if isinstance(mass, str):
                    ax.imshow(simsobj.data.loc[mass, frame], **kwargs)
                elif isinstance(mass, int):
                    ax.imshow(simsobj.data[mass, frame], **kwargs)
                if labels:
                    ax.set_title(labels[n])

    return fig


def coordinates(filelist, **kwargs):
    """ Find all coordinates in a list of image files.

        Usage: fig = sims.coordinates([a.im, b.im], labels=['A', 'B'])

        For each image in the list, the stage coordinates and raster size are
        extracted. A box scaled to the rastersize is plotted for each image on
        a (X,Y) grid. A label for each file can be given. If it's omitted, the
        filename will be printed over the box, but no attempt is made to make
        the filename fit in the box.

        Returns a matplotlib figure instance. If you call this from 'interactive'
        mode (e.g. from ipython --pylab or jupyter), an active figure is shown.
        If it is not called from interactive mode, use fig.savefig('filename.pdf')
        to save the figure to file.
    """
    labels = kwargs.pop('labels', filelist)

    patches = []
    x_min = float('inf')
    y_min = float('inf')
    x_max = float('-inf')
    y_max = float('-inf')

    fig = Figure()
    ax = fig.gca()

    for fn, lb in zip(filelist, labels):
        with TransparentOpen(fn) as th:
            s = sims.SIMSReader(th.fh)
            s.peek()
            s.read_header()

            # Remember!! In Cameca-land, X is up-down, Y is left-right. Fix here.
            x = s.header['sample y']
            y = s.header['sample x']

            if x == 0 and y == 0:
                warnings.warn('Coordinates for file {} are 0,0.'.format(fn))

            if 'PrimaryBeam' in s.header and 'raster' in s.header['PrimaryBeam']:
                raster = s.header['PrimaryBeam']['raster']
            elif 'Image' in s.header and 'raster' in s.header['Image']:
                raster = s.header['Image']['raster']/1000
            else:
                warnings.warn('No raster size in header of file {}.'.format(fn))
                raster = 0

            # Text uses center of image coordinate, box uses lower left.
            ax.text(x, y, lb, ha='center', va='center', fontsize=8)

            x -= raster/2
            y -= raster/2

            # Update data limits, relim() does not work with collections
            x_min = min(x, x_min)
            x_max = max(x + raster, x_max)
            y_min = min(y, y_min)
            y_max = max(y + raster, y_max)

            rect = Rectangle((x, y), raster, raster, ec='black', fc='white', fill=False)
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


def export_fits(simsobj, filename, extend=False, **kwargs):
    """ Export a SIMS object to a FITS file.

        By default, the data structure will be saved as is, i.e. a 3D data cube
        will be saved as such. Set extend=True to unroll the outer dimension
        and save the remaining data structures as a list of HDUs (Header Data
        Units, see FITS documentation); a 3D data cube will be saved as a list
        of 2D images.

        Additional arguments are sent to fits.writeto(), see astropy.io.fits
        for more info.
    """
    if not fits:
        raise ImportError('You need to install either pyfits or astropy '
                          'to be able to export FITS files.')

    data = simsobj.data

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
            raise ValueError('Data is (u)int64 and cannot safely be downcast '
                             'to (u)int32.')
    elif data.dtype == np.int8:
        if data.min() >= 0:
            data = data.astype('u1', copy=False)
        else:
            data = data.astype('i2', copy=False)
    elif data.dtype == np.float16:
        data = data.astype('f4', copy=False)
    elif data.dtype == np.float128:
        if (data.min() >= np.finfo('f8').min and
            data.max() <= np.finfo('f8').max):
                data = data.astype('f8', copy=False)
        else:
            raise ValueError('Data is float128 and cannot safely be downcast '
                             'to float64.')

    if extend:
        hl = [fits.PrimaryHDU()] + [fits.ImageHDU(n) for n in data]
        hdulist = fits.HDUList(hl)
        hdulist.writeto(filename, **kwargs)
    else:
        hdu = fits.PrimaryHDU(data)
        hdu.writeto(filename, **kwargs)


def align(simsobj, reference_species='', reference_frame=0,
          upsample_factor=10, center=True):
    """ Perform image alignment on data.

        usage: aligned_data, shifts = align(data)

        Performs sub-pixel image translation registration (image alignment) on
        the data. skimage.registration.phase_cross_correlation() is used for
        calculating the shifts and scipy.ndimage.shift() is used to apply the
        shifts to the data. See the documentation of those functions for more
        information.

        All species are aligned together. The biggest source of drift is stage
        drift, which is independent of the species. This makes it possible to
        align images with weak signal, by assuming that the drift is the same
        as the parent species with higher signal, e.g. a 13C image is aligned
        based on the shifts calculated from the 12C image. The signal in an 13C
        image is usually too low to calculate shifts accurately.

        The reference species is the species to which all other images in the
        data are adjusted. By default this is the first species in the list.

        The reference frame is the frame to which all other images in the
        stack are adjusted. By default this is 0, the first frame.

        The upsample factor is the amount in fraction of a pixel by which the
        images are aligned, i.e. upsample_factor=10 means 1/10th of a pixel.

        If center=True (the default), the shifts are centered to the median of
        the shifts to minimize blank edges.

        Returns the aligned data and the shifts.
    """
    idx = tuple(simsobj.data.indexes['species'])
    if not reference_species:
        reference_species = 0
        refname = idx[0]
    elif isinstance(reference_species, str):
        refname = reference_species
        reference_species = idx.index(reference_species)
    else:
        refname = idx[reference_species]

    data = simsobj.data[reference_species]
    shifts = []
    for n, frame in enumerate(data):
        if n == reference_frame:
            shifts.append(np.zeros(2))
            continue
        sh = phase_cross_correlation(data[reference_frame], frame,
                                     upsample_factor=upsample_factor,
                                     return_error=False)
        shifts.append(sh)
    shifts = xarray.DataArray(shifts,
                              dims=('frame', 'shift'),
                              attrs={'reference species': refname,
                                     'reference frame': reference_frame,
                                     'unit': 'pixels'})

    if center:
        ymedian = shifts.loc[:, 0].median()
        xmedian = shifts.loc[:, 1].median()
        shifts.loc[:, 0] += ymedian
        shifts.loc[:, 1] += xmedian

    shifted = []
    for block in simsobj.data:
        shifted_block = []
        for dframe, sframe in zip(block, shifts):
            shifted_block.append(shift(dframe, sframe))
        shifted.append(shifted_block)
    shifted = xarray.DataArray(shifted,
                               dims=simsobj.data.dims,
                               coords=simsobj.data.coords,
                               attrs=simsobj.data.attrs)

    return shifted, shifts


def em_correct(simsobj, species=None, deadtime=None, emyield=None, background=None):
    """ Correct data recorded with EMs for several factors.

        Usage: sims.utils.em_correct(s)

        Applies background, yield, and deadtime correction to all species
        recorded with electron multipliers (EMs). By default, all correction
        factors are read from the header. To supply alternative values, give
        the deadtime (in seconds), emyield (in fraction, not %), or background
        (in same units as data, counts or counts/s) as keyword arguments.

        For background it is possible to set up a "baseline" measurement,
        although this is more commonly done for FC detectors. These values are
        read from the .chk_is file. Set the background keyword to "baseline"
        to use these values.

        Correction factors can be given either a single value to be used for
        all species in the data, or as a dict with label:value pairs to specify
        different values per species. This dictionary will override the default
        values for that species, the other species in the data will still use
        the values from the header.

        By default, all EM-recorded species in the data will be corrected. It
        is possible to limit that list by specifying the species keyword with a
        list of species labels. The data of any species not in the list will
        not be altered.

        The EM deadtime correction with deadtime t assumes a non-paralyzable
        detector (see Williamson et al, Anal. Chem, 1988, 60, 2198-2203) and
        the correction applied is:

        Ireal = Icounted/(1 - Icounted * t)

        This adjusts the data in the simsobj. It will also set keywords in the
        header to show that corrections have been applied. It is not possible
        to apply the same corrections more than once.
    """
    EMs = {}
    for spc, mt in simsobj.header['MassTable'].items():
        bfi = mt['b field index']
        tri = mt['trolley index']
        tr = simsobj.header['BFields'][bfi]['Trolleys'][tri]
        if tr['trolley enabled'] and tr['detector'] == 'EM':
            det = simsobj.header['Detectors'][tr['detector label']]
            EMs[spc] = {'deadtime': det['em deadtime']/1e9,  # in ns
                        'yield': det['em yield']/100,  # in %
                        'background': det['em background']}
            if background == 'baseline':
                if tr['used for baseline'] and 'em background baseline' in tr:
                    EMs[spc]['background'] = tr['em background baseline']

    # Override species
    if species:
        for spc in species:
            if spc not in EMs:
                raise KeyError('{} not in data or not using an EM.'.format(spc))
        for spc in EMs:
            if spc not in species:
                EMs.pop(spc)

    # Override deadtime values.
    if deadtime is None:
        pass
    elif isinstance(deadtime, (int, float)):
        for spc in EMs:
            EMs[spc]['deadtime'] = deadtime
    elif isinstance(deadtime, dict):
        for spc, dt in deadtime.items():
            # raise KeyError if label does not exist in data.
            EMs[spc]
            EMs[spc]['deadtime'] = dt
    else:
        raise TypeError('deadtime value not understood. Give either a single '
                        'value, or a dictionary {species: value}, or None to '
                        'use the deadtime value from the header (the '
                        'default).')

    # Override yield values.
    if emyield is None:
        pass
    elif isinstance(emyield, (int, float)):
        for spc in EMs:
            EMs[spc]['yield'] = emyield
    elif isinstance(emyield, dict):
        for spc, yld in emyield.items():
            # raise KeyError if label does not exist in data.
            EMs[spc]
            EMs[spc]['yield'] = yld
    else:
        raise TypeError('yield value not understood. Give either a single '
                        'value, or a dictionary {species: value}, or None to '
                        'use the yield value from the header (the default).')

    # Override background values.
    if background is None:
        pass
    elif isinstance(background, (int, float)):
        for spc in EMs:
            EMs[spc]['background'] = background
    elif isinstance(background, dict):
        for spc, bg in background.items():
            # raise KeyError if label does not exist in data.
            EMs[spc]
            EMs[spc]['background'] = bg
    else:
        if isinstance(background, str) and background == 'baseline':
            pass
        else:
            raise TypeError('background value not understood. Give either a '
                            'single value, or a dictionary {species: value}, '
                            'or the string "baseline" to use the values from '
                            'the .chk_is file from the baseline measurement, '
                            'or None to use the background value from the '
                            'header (the default).')

    for spc, dct in EMs.items():
        if not simsobj.header['MassTable'][spc]['background corrected']:
            simsobj.data.loc[spc] -= dct['background']
            simsobj.header['MassTable'][spc]['background corrected'] = True
        if not simsobj.header['MassTable'][spc]['yield corrected']:
            simsobj.data.loc[spc] *= dct['yield']
            simsobj.header['MassTable'][spc]['yield corrected'] = True
        if not simsobj.header['MassTable'][spc]['deadtime corrected']:
            simsobj.data.loc[spc] /= (1 - dct['deadtime'] * simsobj.data.loc[spc])
            simsobj.header['MassTable'][spc]['deadtime corrected'] = True


def fc_correct(simsobj, background='setup', species=None, resistor=None, gain=None):
    """ Correct and convert data recorded with FCs.

        Usage: sims.utils.fc_correct(s, resistor={'16O': 10e9, '18O': 100e9})

        Applies background correction to all species in the data that were
        recorded with Faraday cups (FCs) and converts the data from "cps"
        (units of 0.01 mV/s) to real counts/s.

        By default the background values are read from the header, which is
        referred to as the "setup" background values. Alternatively, the
        background values that were recorded before and after the measurement
        for 1 s intervals can be used. This is referred to as the "analysis"
        background and is only available if the corresponding .chk_is file
        is present. A more elaborate background can be measured by setting
        up a "baseline" measurement. These values are also read from the
        .chk_is file. Finally, custom background values can be supplied either
        as a single value for all FCs, or as a dictionary with a value for each
        species that was recorded with an FC (skip EM recorded data). Give the
        values in "cps".

        Faraday cups work by running the collected charge through a large
        resistor and measuring the voltage across that resistor. In the
        nanoSIMS (what about other instruments?) 2 resistors are present for
        each FC: 10 GOhm and 100 GOhm. They are selected by setting a jumper in
        the Finnigan can and selecting the corresponding value in Setup. Which
        resistor is selected is not recorded in the header. By default, a guess
        will be made by comparing the raw data with the Cameca-corrected data.
        See SIMSReader.read_data() for more info. If the guess fails, or is
        incorrect, resistor values can be supplied either as a single value
        (in Ohm: 10e9 or 100e9) or as a dictionary with a value for each
        species recorded with a FC.

        By default, all FC-recorded species in the data will be corrected. It
        is possible to limit the data correction to only a few species by
        specifying the species keyword with a list of labels. The data of all
        species not in the list will not be altered.

        This adjusts the data in the simsobj. It will also set keywords in the
        header to show that background correction has been applied. It is not
        possible to apply the correction more than once.
    """
    # Get all the trolleys that are set to FC, link to species label, and get
    # background.
    FCs = {}
    for spc, mt in simsobj.header['MassTable'].items():
        bfi = mt['b field index']
        tri = mt['trolley index']
        tr = simsobj.header['BFields'][bfi]['Trolleys'][tri]
        if tr['trolley enabled'] and tr['detector'] == 'FC':
            FCs[spc] = {}
            det = simsobj.header['Detectors'][tr['detector label']]
            # Always read setup value, override if other is requested.
            if simsobj.header['polarity'] == '+':
                FCs[spc]['background'] = det['fc background setup positive']
            else:
                FCs[spc]['background'] = det['fc background setup negative']
            if background == 'baseline':
                if tr['used for baseline']:
                    FCs[spc]['background'] = tr['fc background baseline']
                else:
                    warnings.warn('no baseline measurement found for {}, '
                                  '({}), using background value from '
                                  'setup.'.format(spc, simsobj.filename))
            elif background == 'analysis':
                if 'fc background before analysis' in det:
                    before = det['fc background before analysis']
                    after = det['fc background after analysis']
                    FCs[spc]['background'] = (before + after)/2
                else:
                    warnings.warn('no analysis background found for {}, '
                                  '({}), using background value from '
                                  'setup.'.format(simsobj.filename))

    # Override species
    if species:
        for spc in species:
            if spc not in FCs:
                raise KeyError('{} not in data or not using a FC.'.format(spc))
        for spc in FCs:
            if spc not in species:
                FCs.pop(spc)

    # Override background values.
    if background is None:
        pass
    elif isinstance(background, (float, int)):
        for spc in FCs:
            FCs[spc]['background'] = background
    elif isinstance(background, dict):
        for spc, bg in background:
            # raise KeyError if label does not exist in data.
            FCs[spc]
            FCs[spc]['background'] = bg
    else:
        if not (isinstance(background, str) and
                background in ('setup', 'analysis', 'baseline')):
            raise TypeError('background value not understood. Give either a '
                            'string, one of "setup", "analysis", or "baseline"'
                            ', or a single value, or a dictionary '
                            '{species: value}.')

    # Resistor values
    if not resistor:
        resistor = _guess_fc_resistors(simsobj)
        for spc in resistor.coords['species'].values:
            FCs[spc]['resistor'] = resistor.loc[spc]
    elif isinstance(resistor, (float, int)):
        for spc in FCs:
            FCs[spc]['resistor'] = resistor
    elif isinstance(resistor, dict):
        for spc, r in resistor.items():
            # raise KeyError if label does not exist in data.
            FCs[spc]
            FCs[spc]['resistor'] = r
    else:
        raise TypeError('resistor value not understood. Give either a single '
                        'value, or a dictionary {species: value}, or None to '
                        'have the resistor value guessed automatically.')

    for spc, dct in FCs.items():
        if not simsobj.header['MassTable'][spc]['background corrected']:
            simsobj.data.loc[spc] -= dct['background']
            simsobj.header['MassTable'][spc]['background corrected'] = True
            # 1e5 is the magic factor to go from "cps",
            # which is aparently 0.01 mV/s, to V/s.
            simsobj.data.loc[spc] *= ions_per_amp/(1e5 * dct['resistor'])


def _guess_fc_resistors(simsobj):
    """ Internal function; guess FC resistor value from raw and
        Cameca-corrected data. Returns a xarray.DataArray with
        species labels as coordinates.
    """
    sp = []
    bg = []
    for species, mt in simsobj.header['MassTable'].items():
        bfi = mt['b field index']
        tri = mt['trolley index']
        tr = simsobj.header['BFields'][bfi]['Trolleys'][tri]
        if tr['detector'] == 'FC':
            sp.append(species)
            dt = tr['detector label']
            if simsobj.header['polarity'] == '+':
                bg.append(simsobj.header['Detectors'][dt]['fc background setup positive'])
            else:
                bg.append(simsobj.header['Detectors'][dt]['fc background setup negative'])

    data = simsobj.data.loc[sp].mean(dim='frame')
    corr = simsobj._data_corr.loc[sp].mean(dim='frame')
    resistors = (data - bg) * ions_per_amp / (corr * 1e5)
    resistors = np.log10(resistors).round()
    resistors = 10**resistors
    resistors.attrs['unit'] = 'Ohm'
    return resistors


class _JSONDateTimeEncoder(json.JSONEncoder):
    """ Converts datetime objects to json format. """
    def default(self, obj):
        if isinstance(obj, (datetime.date, datetime.datetime)):
            return obj.isoformat()
        else:
            return json.JSONEncoder.default(self, obj)


def export_header(simsobj, filename=''):
    """ Export header to a JSON text file.

        Usage: export_header(simsobj, filename="alt_filename.txt")

        The entire header will be pretty-printed to a text file, serialized as
        JSON in UTF-8 encoding. Uses sims_filename.im.txt by default.
    """
    if not filename:
        filename = simsobj.filename + '.txt'

    with open(filename, mode='wt', encoding='utf-8') as fh:
        print(json.dumps(simsobj.header, sort_keys=True,
                         indent=2, ensure_ascii=False,
                         separators=(',', ': '), cls=_JSONDateTimeEncoder),
              file=fh)


def export_matlab(simsobj, filename='', prefix='m', **kwargs):
    """ Export data to MatLab file.

        Usage: export_matlab(simsobj, filename="alt_filename.mat")

        Saves data to filename.im.mat, or alt_filename.mat if supplied. All
        other keyword arguments are passed on to scipy.io.savemat().

        By default the MatLab file will be saved in MatLab 5 format (the
        default in MatLab 5 - 7.2), with long_field_names=True (compatible with
        MatLab 7.6+) and with do_compression=True.

        The data is stored as a dictionary with the labels as keywords and
        3D numpy arrays as values. Each label is taken from the nanoSIMS label,
        prefixed with 'prefix' (default 'm'), because MatLab doesn't allow
        variable names starting with a number.
    """
    if not filename:
        filename = simsobj.filename
    if 'do_compression' not in kwargs:
        kwargs['do_compression'] = True
    if 'long_field_names' not in kwargs:
        kwargs['long_field_names'] = True

    export = {}
    for l in simsobj.data.species.values:
        export[prefix + l] = simsobj.data.loc[l].values

    savemat(filename, export, **kwargs)
