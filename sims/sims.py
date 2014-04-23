#!/usr/bin/env python
# -*- coding: utf-8 -*-
""" Python module to read Cameca (nano)SIMS data files. """

import numpy as np
import io
import sys
import os
import re
import inspect
import datetime
import warnings
import json
import gzip
import bz2
import zipfile
from struct import unpack

# available in Python 3.3 and higher, or with non-default extensions
try:
    import lzma
except ImportError:
    lzma = None

from .info import *


class SIMSBase(object):
    """ Base SIMS object. """
    def __init__(self, fileobject):
        """ This class object does not open or close files, and nothing will
            be read by default. An empty header will be created and all
            relevant methods for reading the header and data are available.
        """
        self.fh = fileobject
        self.header = {}
        self.data = np.array([], dtype='uint16')

    def peek(self):
        """ Peek into image file and determine basic file information.

            Usage: s.peek()

            Reads first 12 bytes of file opened as s.fh, determines byte order
            (endianess), file type, file version, and header size. Information
            is stored in s.header.
        """
        self.fh.seek(0)
        snip = self.fh.read(12)
        # 256 is an arbitrarily chosen limit, file types are in the 10s.
        if unpack('<i', snip[4:8])[0] < 256:
            self.header['byte order'] = '<'
        elif unpack('>i', snip[4:8])[0] < 256:
            self.header['byte order'] = '>'
        else:
            raise TypeError("Cannot determine file endianess.")

        self.header['file version'], self.header['file type'], \
            self.header['header size'] = \
            unpack(self.header['byte order'] + '3i', snip)

        if self.header['file type'] not in supported_file_types:
            msg = "File of type {} is not supported at the moment."
            msg = msg.format(self.header['file type'])
            raise NotImplementedError(msg)

    def read_header(self):
        """ Read the image header.

            Usage: s.read_header()

            Reads the header from the file object stored in s.fh. Extracts as
            much information as possible and stores it in a Python dictionary
            in s.header. At least byte order, header size, file type, and file
            version need to be known before the header can be read: use
            s.peek() before s.read_header().
        """
        # Read entire header into memory in one read to minimize Disk I/O.
        self.fh.seek(0)
        hdr = self.fh.read(self.header['header size'])

        # Find several markers in the byte-string
        champslist_pos = hdr.find(b'Champs_list\x00')
        offsetlist_pos = hdr.find(b'Offset_list\x00')
        analparamnano_pos = hdr.find(b'Anal_param_nano\x00')
        analparamnanobis_pos = hdr.find(b'Anal_param_nano_bis\x00')

        # There can be multiple Poly_list markers, go with last one.
        polylist_pos = [m.start() for m in re.finditer(b'Poly_list\x00', hdr)]

        if polylist_pos:
            polylist_pos = polylist_pos[-1]
        else:
            polylist_pos = -1

        # Turn byte-string into BytesIO file-like object; reading and
        # keeping track of where we are is easier that way than trying to
        # slice byte-string as an array and keeping track of indices.
        hdr = io.BytesIO(hdr)

        # Main header
        hdr.seek(12)
        self.header.update(self._main_header(hdr))

        # NanoSIMS header, starts with polylist
        self.header['polylist length'] = 0
        if polylist_pos < 0:
            msg = 'Beginning of PolyList not found, '
            msg += 'don\'t know where nanoSIMS header starts.'
            warnings.warn(msg)
        else:
            hdr.seek(polylist_pos + 16)
            self.header['polylist length'] = unpack(self.header['byte order'] + 'i', hdr.read(4))[0]
            self.header['PolyList'] = []
            for p in range(self.header['polylist length']):
                self.header['PolyList'].append(self._species(hdr))
            # 4 bytes unused
            hdr.seek(4, 1)

            # Found in isotope image, where else?
            self.header['champslist length'] = 0
            if champslist_pos >= 0:
                hdr.seek(champslist_pos + 16)
                self.header['ChampsList'] = []
                self.header['champslist length'] = unpack(self.header['byte order'] + 'i',
                                                          hdr.read(4))[0]
                for c in range(self.header['champslist length']):
                    # read something here, don't know what
                    warnings.warn('Champs list is non-null: find out how to read.')
                    pass
                hdr.seek(4, 1)

            # Found in isotope image, where else?
            self.header['offsetlist length'] = 0
            if offsetlist_pos >= 0:
                hdr.seek(offsetlist_pos + 16)
                self.header['OffsetList'] = []
                self.header['offsetlist length'] = unpack(self.header['byte order'] + 'i',
                                                          hdr.read(4))[0]
                for c in range(self.header['offsetlist length']):
                    # read something here, don't know what
                    warnings.warn('Offset list is non-null: find out how to read.')
                    pass
                hdr.seek(4, 1)

            self.header['NanoSIMSHeader'] = self._nanosims_header(hdr)

            # How much to skip? Chomping does not work; what if first value is 0?
            # This is correct so far, for nsheader v8 and 9
            hdr.seek(948, 1)
            self.header['BFields'] = []
            for b in range(self.header['NanoSIMSHeader']['b fields']):
                self.header['BFields'].append(self._bfield(hdr))
        # End nanosims_header/bfield based on Poly_list position

        # Analytical parameters
        # Called AnalyticalParamNano AND AnalysisParamNano in OpenMIMS
        # Here, split out Primary and Secondary beam
        if analparamnano_pos < 0:
            msg = 'Anal_param_nano not found in header, '
            msg += 'don\'t know where PrimaryBeam section starts.'
            warnings.warn(msg)
        else:
            hdr.seek(analparamnano_pos + 16)
            self.header['analysis version'], self.header['n50large'], self.header['comment'] = \
                unpack(self.header['byte order'] + '2i 8x 256s', hdr.read(272))

            self.header['n50large'] = bool(self.header['n50large'])
            self.header['comment'] = self._cleanup_string(self.header['comment'])

            self.header['PrimaryBeam'] = self._primary_beam(hdr)
            self.header['SecondaryBeam'] = self._secondary_beam(hdr)
            self.header['Detectors'] = self._detectors1(hdr)

            self.header['SecondaryBeam']['e0s'] = self.header['Detectors'].pop('e0s')
            self.header['SecondaryBeam']['pressure multicollection chamber'] = \
                self.header['Detectors'].pop('pressure multicollection chamber')

            # Header for non-nano SIMS
            magic = unpack(self.header['byte order'] + 'i', hdr.read(4))[0]
            if magic != 2306:
                msg = 'SIMSHeader magic number not found here at byte {}.'
                msg = msg.format(hdr.tell()-4)
                raise ValueError(msg)
            self.header['SIMSHeader'] = self._sims_header(hdr)

            # This is messy: widths and heights for XL exit slits inside detectors2.
            # There is no way to update one item of a list inside a dict inside another
            # dict using dict.update(). All widths and heights are therefore stored as a
            # single list. Pop out of dict, slice up, put back in right place.
            if self.header['analysis version'] >= 5:
                if analparamnanobis_pos < 0:
                    msg = 'Anal_param_nano_bis not found in header, '
                    msg += 'don\'t know where second Detectors section starts.'
                    warnings.warn(msg)
                else:
                    hdr.seek(analparamnanobis_pos + 24)
                    self.header['Detectors'].update(self._detectors2(hdr))
                    exsl = self.header['Detectors'].pop('exit slit xl')
                    for n in range(7):
                        det = 'Detector{}'.format(n+1)
                        self.header['Detectors'][det]['exit slit widths'][2] = exsl[5*n:5*(n+1)]
                        self.header['Detectors'][det]['exit slit heights'][2] = \
                            exsl[5*(n+1):5*(n+2)]

                # Presets
                self.header['Presets'] = self._presets(hdr)

            # End Detectors pt 2 based on anal_param_nano_bis position

            # Last part of detectors
            if self.header['analysis version'] >= 6:
                d3 = self._detectors3(hdr)
                self.header['Detectors']['TIC'] = d3.pop('TIC')
                for k, v in d3.items():
                    self.header['Detectors'][k].update(v)
        # End PrimaryBeam/SecondaryBeam/Presets/Detectors based on anal_param_nano position

        # Image header, at end of overall header
        if self.header['file type'] == 26:
            hdr.seek(-176, 2)
            self.header['Isotopes'] = self._isotopes_hdr(hdr)
        elif self.header['file type'] in (21, 22):
            # no image header for line scan
            pass
        else:
            hdr.seek(-84, 2)
            self.header['Image'] = self._image_hdr(hdr)

    def read_data(self):
        """ Read the image data.

            Usage: s.read_data()

            Reads all the image data from the file object in s.fh. The data is
            stored in s.data as a Numpy/Scipy array with 4 dimensions: mass,
            plane, width, height. The image header must be read before data can
            be read.
        """
        if not self.header['data included']:
            pass
        elif self.header['file type'] == 26:
            self._isotope_data()
        elif self.header['file type'] in (21, 22):
            # line scan types, no ImageHeader
            warnings.warn('No data read for line scan, fix')
            pass
        else:
            self._image_data()

    def save_header(self, filename):
        """ Save full header to a JSON text file.

            Usage: s.save_header("filename.txt")

            The entire header will be PrettyPrinted to a text file,
            serialized as JSON in UTF-8 encoding.
        """
        class JSONDateTimeEncoder(json.JSONEncoder):
            def default(self, obj):
                if isinstance(obj, (datetime.date, datetime.datetime)):
                    return obj.isoformat()
                else:
                    return json.JSONEncoder.default(self, obj)

        with open(filename, mode='wt', encoding='utf-8') as fp:
            print(json.dumps(self.header, sort_keys=True, indent=2,
                             separators=(',', ': '), cls=JSONDateTimeEncoder), file=fp)

    def get_info(self, *args):
        """ Print info about the header key words.

            Usage: s.get_info('masses', 'Image', 'defines', ...)

            The full header is available under s.header. Special arguments \"defines\",
            \"dicts\", or \"variables\" will print all of the defines, dicts, or
            variables. Running get_info() without arguments will print all header
            information.
        """
        pass

    ### Internal functions
    def _main_header(self, hdr):
        """ Internal function; reads variable number of bytes; returns main header dict """
        d = {}
        # Called readDefAnalysis in OpenMIMS (but skip first three, in peek)
        d['sample type'], d['data included'], d['sample x'], d['sample y'], \
            d['analysis type'], d['user name'], d['sample z'], date, time = \
            unpack(self.header['byte order'] + '4i 32s 16s i 12x 16s 16s', hdr.read(112))

        d['data included'] = bool(d['data included'])
        d['user name'] = self._cleanup_string(d['user name'])
        d['analysis type'] = self._cleanup_string(d['analysis type'])
        date = self._cleanup_string(date)
        time = self._cleanup_string(time)
        d['date'] = self._cleanup_date(date + ' ' + time)

        if self.header['file type'] in (27, 29, 39):
            # Called MaskImage/readMaskIm in OpenMIMS
            d['original filename'], d['analysis duration'], d['cycles'], d['scan type'], \
                d['magnification'], d['size type'], d['size detector'], \
                d['beam blanking'], d['presputtering'], d['presputtering duration'] = \
                unpack(self.header['byte order'] + '16s 3i 3h 2x 3i', hdr.read(48))

            d['AutoCal'] = self._autocal(hdr)
            d['HVControl'] = {}
            d['HVControl']['has hvcontrol'] = False

        elif self.header['file type'] in (22, 41):
            # Called MaskSampleStageImage/readMaskIss in OpenMIMS
            d['original filename'], d['analysis duration'], d['scan type'], \
                d['steps'], d['steps x'], d['steps y'], d['step size'], \
                d['step waittime'], d['cycles'], d['beam blanking'], \
                d['presputtering'], d['presputtering duration'] = \
                unpack(self.header['byte order'] + '16s 6i d 4i', hdr.read(64))

            d['scan type'] = stage_scan_types.get(d['scan type'], str(d['scan type']))

            d['AutoCal'] = self._autocal(hdr)
            d['HVControl'] = self._hvcontrol(hdr)
            # OpenMIMS has unused int after 'has autocal' (here in AutoCal). This is wrong.
            # Don't know if it needs to go after HVControl or after SigRef.
            hdr.seek(4, 1)

        elif self.header['file type'] in (21, 26):
            # Not in OpenMIMS
            # this bit same as image, 1 extra unused/unknown
            d['original filename'], d['analysis duration'], d['cycles'], d['scan type'], \
                d['magnification'], d['size type'], d['size detector'], \
                d['beam blanking'], d['presputtering'], d['presputtering duration'] = \
                unpack(self.header['byte order'] + '16s 4x 3i 3h 2x 3i', hdr.read(52))

            # this bit same as stage scan, (because in fact, it is)
            d['AutoCal'] = self._autocal(hdr)
            d['HVControl'] = self._hvcontrol(hdr)
            hdr.seek(4, 1)
            if self.header['file type'] == 21:
                # looks much the same as isotope (26), but have 20 bytes unaccounted for.
                hdr.seek(20, 1)
        else:
            raise TypeError('What type of image are you? {}'.format(self.header['file type']))

        # Continue main header for all types
        d['SigRef'] = self._sigref(hdr)
        d['masses'] = unpack(self.header['byte order'] + 'i', hdr.read(4))[0]

        # scan type is set for stage scan analysis, set others
        if isinstance(d['scan type'], int):
            if d['scan type'] == 0:
                d['scan type'] = ''
            else:
                d['scan type'] = str(d['scan type'])

        d['beam blanking'] = bool(d['beam blanking'])
        d['presputtering'] = bool(d['presputtering'])
        d['original filename'] = self._cleanup_string(d['original filename'])

        if self.header['file type'] in (21, 27, 29, 39):
            if self.header['file version'] >= 4108:
                n = 60
            else:
                n = 10
        elif self.header['file type'] in (22, 41):
            n = 20
        else:
            n = 0
        d['mass table ptr'] = list(unpack(self.header['byte order'] + n*'i', hdr.read(n*4)))
        d['mass table ptr'] = [n for n in d['mass table ptr'] if n != 0]

        if self.header['file type'] in (21, 22, 41):
            hdr.seek(4, 1)  # 4 bytes unused

        # Mass table, 1 info dict for each mass, indexed by number
        d['MassTable'] = []
        for m in range(d['masses']):
            mi = {}
            # either 1st or 2nd int is type mass, other is unused, NOT IN SPEC!!
            mi['type mass'], mi['type mass alt'], mi['mass'], \
                mi['matrix or trace'], mi['detector'], mi['wait time'], \
                mi['count time'], mi['offset'], mi['b field'] = \
                unpack(self.header['byte order'] + '2i d 2i 2d 2i', hdr.read(48))

            mi['Species'] = self._species(hdr)
            d['MassTable'].append(mi)
        return d

    def _autocal(self, hdr):
        """ Internal function; reads 76 bytes; returns AutoCal dict """
        # Called AutoCal in OpenMIMS source
        # OpenMIMS says extra unused byte after has autocal for stage scan image; not true
        d = {}
        d['has autocal'], d['mass label'], d['begin'], d['duration'] = \
            unpack(self.header['byte order'] + 'i 64s 2i', hdr.read(76))

        d['has autocal'] = bool(d['has autocal'])
        d['mass label'] = self._cleanup_string(d['mass label'])
        return d

    def _hvcontrol(self, hdr):
        """ Internal function; reads 112 bytes, returns HVControl dict. """
        # Called readHvControl by OpenMIMS
        d = {}
        d['has hvcontrol'], d['mass label'], d['begin'], d['duration'], d['limit low'], \
            d['limit high'], d['step'], d['bandpass width'], d['count time'] = \
            unpack(self.header['byte order'] + 'i 64s 2i 3d i d', hdr.read(112))

        d['has hvcontrol'] = bool(d['has hvcontrol'])
        d['mass label'] = self._cleanup_string(d['mass label'])
        return d

    def _sigref(self, hdr):
        """ Internal function; reads 160 bytes; returns SigRef dict """
        # Called SigRef in OpenMIMS
        d = {}
        d['has sigref'] = bool(unpack(self.header['byte order'] + 'i', hdr.read(4))[0])
        d['Species'] = self._species(hdr)
        d['detector'], d['offset'], d['quantity'] = \
            unpack(self.header['byte order'] + '3i', hdr.read(12))
        return d

    def _species(self, hdr):
        """ Internal function; reads 144 bytes, return Species dict. """
        # Called PolyAtomic in OpenMIMS source
        d = {}

        # Charge is read in OpenMIMS as a char, which is not a Java char (2-bytes),
        # but a single byte char.
        d['numeric flag'], d['numeric value'], d['elements'], \
            d['charges'], d['charge label'], d['mass label'] = \
            unpack(self.header['byte order'] + '4i c 64s', hdr.read(81))

        d['mass label'] = self._cleanup_string(d['mass label'])
        d['charge label'] = self._cleanup_string(d['charge label'])

        # OpenMIMS says 3 bytes AFTER el.table are unused; this is wrong,
        # 3 bytes BEFORE el.table (b 81-84) are unused. n_elements (here: atomic number)
        # is element number in periodic table rather than number of elements.
        # n_isotopes (here: isotope number) is offset from main atomic Z number.
        # Also: collapse ElementTable (Tabelts) into main dict, too many layers.
        hdr.seek(3, 1)
        d['atomic number'] = [0, 0, 0, 0, 0]
        d['isotope number'] = [0, 0, 0, 0, 0]
        d['stoich number'] = [0, 0, 0, 0, 0]
        for n in range(5):
            d['atomic number'][n], d['isotope number'][n], d['stoich number'][n] = \
                unpack(self.header['byte order'] + '3i', hdr.read(12))
        return d

    def _nanosims_header(self, hdr):
        """ Internal function; reads 604 bytes; returns NanoSIMSHeader dict """
        # Called MaskNano in OpenMIMS; BFieldTab separated out; create extra sub-dict PeakCenter
        d = {}
        d['PeakCenter'] = {}
        d['nanosimsheader version'], d['regulation mode'], d['mode'], \
            d['grain mode'], d['semigraphic mode'], d['delta x'], \
            d['delta y'], d['working frame width'], d['working frame height'], \
            d['scanning frame x'], d['scanning frame width'], \
            d['scanning frame y'], d['scanning frame height'], \
            d['nx lowb'], d['nx highb'], d['ny lowb'], d['ny highb'], \
            d['detector type'], d['electron scan'], d['scanning mode'], \
            d['blanking comptage'], d['PeakCenter']['has peakcenter'], \
            d['PeakCenter']['start'], d['PeakCenter']['frequency'], d['b fields'] = \
            unpack(self.header['byte order'] + '25i', hdr.read(100))

        d['PeakCenter']['has peakcenter'] = bool(d['PeakCenter']['has peakcenter'])
        d['regulation mode'] = bool(d['regulation mode'])
        d['grain mode'] = bool(d['grain mode'])
        d['semigraphic mode'] = bool(d['semigraphic mode'])
        d['scanning mode'] = bool(d['scanning mode'])

        # Found in at least one version (file v11, nsHeader v8) a repeat of Poly_list and this
        # first part of nanoSIMSHeader. Total of repeat adds up to 288.
        # After last Poly_list, 288 byte padding zone, not all null-bytes.
        hdr.seek(288, 1)

        # Is this the nPrintRed from OpenMIMS?
        d['print results'] = bool(unpack(self.header['byte order'] + 'i', hdr.read(4))[0])

        d['SibCenterHor'] = self._sib_center(hdr)
        d['SibCenterVert'] = self._sib_center(hdr)

        # Duplicate and store these two in sub dicts
        b_field_index, has_sib_center = \
            unpack(self.header['byte order'] + '2i', hdr.read(8))
        if b_field_index < 0:
            b_field_index = None
        has_sib_center = bool(has_sib_center)

        d['SibCenterHor']['b field index'] = b_field_index
        d['SibCenterVert']['b field index'] = b_field_index
        d['SibCenterHor']['has sib center'] = has_sib_center
        d['SibCenterVert']['has sib center'] = has_sib_center

        d['EnergyCenter'] = self._energy_center(hdr)
        d['E0SCenter'] = self._e0s_center(hdr)

        d['EnergyCenter']['wait time'], d['presputtering raster'], \
            d['PeakCenter']['e0p offset'], d['E0SCenter']['steps'], \
            d['baseline measurements'], d['baseline Pd offset'], \
            d['baseline frequency'] = \
            unpack(self.header['byte order'] + '5i d i', hdr.read(32))
        return d

    def _sib_center(self, hdr):
        """ Internal function; reads 40 bytes; returns SibCenter dict """
        # Called SecIonBeamNano in OpenMIMS
        d = {}
        d['detector'], d['start'], d['step size'], d['center'], \
            d['50% width'], d['count time'] = \
            unpack(self.header['byte order'] + '3i 4x 2d i 4x', hdr.read(40))

        if d['detector'] < 0:
            d['detector'] = None
        d['count time'] /= 100  # 10 ms increments to seconds
        return d

    def _energy_center(self, hdr):
        """ Internal function; reads 52 bytes; returns EnergyCenter dict """
        # Called EnergyNano in OpenMIMS
        # Added b field index, has energy center, and frequency from MaskNano
        d = {}
        d['detector'], d['start'], d['step size'], d['center'], \
            d['delta'], d['count time'], d['b field index'], \
            d['has energy center'], d['frequency'] = \
            unpack(self.header['byte order'] + '3i 4x 2d i 4x 3i', hdr.read(52))

        d['has energy center'] = bool(d['has energy center'])
        d['count time'] /= 100  # 10 ms increments to seconds
        if d['detector'] < 0:
            d['detector'] = None
        if d['b field index'] < 0:
            d['b field index'] = None
        return d

    def _e0s_center(self, hdr):
        """ Internal function; reads 40 bytes; returns E0sCenter dict """
        # Called E0SNano in OpenMIMS
        # b field index and has e0s center added to sub dict from main nano header
        d = {}
        d['b field index'], d['detector'], d['start'], d['step size'], \
            d['count time'], d['center'], d['80% width'], d['has e0s center'] = \
            unpack(self.header['byte order'] + '5i 2d i', hdr.read(40))

        d['has e0s center'] = bool(d['has e0s center'])
        d['count time'] /= 100  # 10 ms increments to seconds
        if d['detector'] < 0:
            d['detector'] = None
        if d['b field index'] < 0:
            d['b field index'] = None
        return d

    def _bfield(self, hdr):
        """ Internal function; reads 2840 bytes; returns BField dict """
        # Called TabBFieldNano in OpenMIMS
        d = {}
        d['b field selected'], d['b field'], d['wait time'], \
            d['time per pixel'], d['time per point'], d['computed'], \
            d['e0w offset'], d['q'], d['lf4'], d['hex val'], d['frames'] = \
            unpack(self.header['byte order'] + '4i d 6i', hdr.read(48))

        d['b field selected'] = bool(d['b field selected'])

        # 8 bytes unused
        hdr.seek(8, 1)

        # Trolleys 0-5
        d['Trolleys'] = [0, 0, 0, 0, 0, 0]
        for t in range(6):
            d['Trolleys'][t] = self._trolley(hdr)

        ### SKIPPING USEFUL INFO

        # Not sure which version skips how much
        # this works for file v11/nsheader v8 and file v4108/nsheader v9
        if self.header['file version'] < 4108:
            hdr.seek(768, 1)
        else:
            hdr.seek(832, 1)

        # Trolleys 6 & 7
        d['Trolleys'].append(self._trolley(hdr))
        d['Trolleys'].append(self._trolley(hdr))

        # PHD 0-5
        for t in range(6):
            d['Trolleys'][t].update(self._phd(hdr))

        ### SKIPPING USEFUL INFO
        hdr.seek(96, 1)

        # PHD 6 & 7
        d['Trolleys'][6].update(self._phd(hdr))
        d['Trolleys'][7].update(self._phd(hdr))

        return d

    def _trolley(self, hdr):
        """ Internal function; reads 192 or 208 bytes; returns Trolley dict """
        # Called TabTrolleyNano in OpenMIMS
        d = {}
        d['mass label'], d['mass'], d['radius'], d['deflection plate 1'], \
            d['deflection plate 2'], d['detector'], d['exit slit'], d['real trolley'], \
            d['trolley index'], d['peakcenter index'], d['peakcenter follow'], d['focus'], \
            d['hmr start'], d['start dac plate 1'], d['start dac plate 2'], \
            d['hmr step'], d['hmr points'], d['hmr count time'], \
            d['used for baseline'], d['50% width'], d['peakcenter side'], \
            d['peakcenter count time'], d['used for sib center'], d['unit correction'], \
            d['deflection'], d['used for energy center'], d['used for e0s center'] = \
            unpack(self.header['byte order'] + '64s 2d 8i 2d 6i d 4i d 2i', hdr.read(192))

        # 16 extra bytes per trolley entry, not in OpenMIMS
        # Only certain versions?
        if self.header['file version'] >= 4108:
            hdr.seek(16, 1)

        # Cleanup
        d['mass label'] = self._cleanup_string(d['mass label'])
        d['used for baseline'] = bool(d['used for baseline'])
        d['used for sib center'] = bool(d['used for sib center'])
        d['used for energy center'] = bool(d['used for energy center'])
        d['used for e0s center'] = bool(d['used for e0s center'])
        d['real trolley'] = bool(d['real trolley'])
        d['peakcenter side'] = peakcenter_sides.get(d['peakcenter side'], str(d['peakcenter side']))
        d['detector'] = detectors.get(d['detector'], str(d['detector']))
        d['hmr count time'] /= 100
        d['peakcenter count time'] /= 100
        return d

    def _phd(self, hdr):
        """ Internal function; reads 24 bytes; returns Phd dict """
        # Called PHDTrolleyNano in OpenMIMS
        d = {}
        d['used for phd scan'], d['phd start'], d['phd step size'], \
            d['phd points'], d['phd count time'], d['phd scan repeat'] = \
            unpack(self.header['byte order'] + '6i', hdr.read(24))

        d['used for phd scan'] = bool(d['used for phd scan'])
        d['phd count time'] /= 100
        return d

    def _primary_beam(self, hdr):
        """ Internal function; reads 552 bytes; returns PrimaryBeam dict """
        # Called ApPrimaryNano in OpenMIMS
        d = {}
        start_position = hdr.tell()
        d['source'], d['current start'], d['current end'], d['lduo'], d['l1'] =\
            unpack(self.header['byte order'] + '8s 4i', hdr.read(24))

        # Each widths list is 10 ints long
        # starred list unpacking only works in python 3.x
        d['dduo'], *d['dduo widths'] = unpack(self.header['byte order'] + '11i', hdr.read(44))
        d['d0'], *d['d0 widths'] = unpack(self.header['byte order'] + '11i', hdr.read(44))
        d['d1'], *d['d1 widths'] = unpack(self.header['byte order'] + '11i', hdr.read(44))

        # 4 bytes unused
        hdr.seek(4, 1)
        d['raster'], d['oct45'], d['oct90'], d['e0p'], d['pressure analysis chamber'] = \
            unpack(self.header['byte order'] + '4d 32s', hdr.read(64))

        d['source'] = self._cleanup_string(d['source'])
        d['pressure analysis chamber'] = self._cleanup_string(d['pressure analysis chamber'])

        if self.header['analysis version'] >= 3:
            d['l0'] = unpack(self.header['byte order'] + 'i', hdr.read(4))[0]
        if self.header['analysis version'] >= 4:
            d['hv cesium'], d['hv duo'] = unpack(self.header['byte order'] + '2i', hdr.read(8))
            # DCs not in OpenMIMS; only in certain release/version?
            d['dcs'], *d['dcs widths'] = unpack(self.header['byte order'] + '11i', hdr.read(44))

        # skip bytes until total read in this function is 552
        # OpenMIMS: size_Ap_primary_nano = 552
        # Newer versions have rest filled with \xCC continuation bytes, but
        # older versions have null-bytes, but not all bytes are null!!
        # The numbers do not seem to represent anything, though, so can be skipped.
        hdr.seek(start_position + 552)
        return d

    def _secondary_beam(self, hdr):
        """ Internal function; reads 192 bytes; returns SecondaryBeam dict. """
        # Called ApSecondaryNano in OpenMIMS
        d = {}
        # Many lists to unpack, use tmp tuple, reduce read and unpack calls.
        tmp = unpack(self.header['byte order'] + 'd 42i 2d', hdr.read(192))
        d['e0w'], d['es'] = tmp[:2]
        d['es widths'] = list(tmp[2:12])
        d['es heights'] = list(tmp[12:22])
        d['as'] = tmp[22]
        d['as widths'] = list(tmp[23:33])
        d['as heights'] = list(tmp[33:43])
        d['ens'], d['ens width'] = tmp[43:]
        return d

    def _detectors1(self, hdr):
        """ Internal function; reads 808 bytes, returns Detectors dict, part 1. """
        d = {}
        d['FCs'] = self._exit_slits(hdr)

        for n in range(1, 6):
            det = 'Detector{}'.format(n)
            d[det] = self._exit_slits(hdr)

        d['LD'] = {}
        d['LD']['exit slit width'], d['LD']['exit slit coeff a'], \
            d['LD']['exit slit coeff b'], d['e0s'], \
            d['pressure multicollection chamber'], \
            d['FCs']['fc background positive'], \
            d['FCs']['fc background negative'] = \
            unpack(self.header['byte order'] + '4d 32s 2i', hdr.read(72))

        d['pressure multicollection chamber'] = \
            self._cleanup_string(d['pressure multicollection chamber'])

        for n in range(1, 6):
            det = 'Detector{}'.format(n)
            d[det].update(self._electron_multiplier(hdr))

        d['LD'].update(self._electron_multiplier(hdr))

        d['EMBig'] = self._exit_slits(hdr)
        d['EMBig'].update(self._electron_multiplier(hdr))

        # 8 bytes unused
        hdr.seek(8, 1)
        return d

    def _detectors2(self, hdr):
        """ Internal function; reads 488 bytes, returns Detectors dict, part 2. """
        # Called AnalysisParam in OpenMIMS, first part only
        # presets separate, last part in _detectors3
        d = {}
        if self.header['analysis version'] >= 5:
            d['Detector6'] = self._exit_slits(hdr)
            d['Detector6'].update(self._electron_multiplier(hdr))
            d['Detector7'] = self._exit_slits(hdr)
            d['Detector7'].update(self._electron_multiplier(hdr))
            d['exit slit xl'] = list(unpack(self.header['byte order'] + '70i', hdr.read(280)))
        return d

    def _detectors3(self, hdr):
        """ Internal function; reads 100 bytes, returns Detectors dict, part 3. """
        # Called AnalysisParam in OpenMIMS, only last part
        d = {}
        d['TIC'] = self._electron_multiplier(hdr)

        for n in range(1, 8):
            det = 'Detector{}'.format(n)
            d[det] = {}
            d[det]['fc background positive'], d[det]['fc background negative'] = \
                unpack(self.header['byte order'] + '2i', hdr.read(8))

        for n in range(1, 8):
            det = 'Detector{}'.format(n)
            det_type = unpack(self.header['byte order'] + 'i', hdr.read(4))[0]
            d[det]['detector'] = detectors.get(det_type, str(det_type))
        return d

    def _exit_slits(self, hdr):
        """ Internal function; reads 88 bytes, returns exit slit dict. """
        # Does not exist separately in OpenMIMS, part of ApSecondaryNano and AnalysisParam
        d = {}
        # Each detector exit slit has:
        # - a position (0, 1, 2)
        # - a size (normal, large, xl)
        # The exit slits widths (and heights) are a 3x5 matrix where coordinate (size, pos)
        #  returns actual width (height). positions 4 and 5 are 0 (for future expansion?)
        # Size XL not stored in same part of header, and only in analysis version >= 5, so we
        # return a list of length 5 with 0s here. Slits 0, 1, 2 are called slit 1, slit 2,
        # slit 3, so add labels to avoid confusion.

        d['exit slit'], d['exit slit size'] = \
            unpack(self.header['byte order'] + '2i', hdr.read(8))
        d['exit slit label'] = exit_slit_labels.get(d['exit slit'], str(d['exit slit']))
        d['exit slit size label'] = \
            exit_slit_size_labels.get(d['exit slit size'], str(d['exit slit size']))

        d['exit slit widths'] = [0, 0, 0]
        d['exit slit widths'][0] = list(unpack(self.header['byte order'] + '5i', hdr.read(20)))
        d['exit slit widths'][1] = list(unpack(self.header['byte order'] + '5i', hdr.read(20)))
        d['exit slit widths'][2] = [0]*5
        d['exit slit heights'] = [0, 0, 0]
        d['exit slit heights'][0] = list(unpack(self.header['byte order'] + '5i', hdr.read(20)))
        d['exit slit heights'][1] = list(unpack(self.header['byte order'] + '5i', hdr.read(20)))
        d['exit slit heights'][2] = [0]*5
        return d

    def _electron_multiplier(self, hdr):
        """ Internal function; reads 16 bytes, returns EM dict. """
        d = {}
        d['em yield'], d['em background'], d['em deadtime'] = \
            unpack(self.header['byte order'] + 'd 2i', hdr.read(16))
        return d

    def _sims_header(self, hdr):
        """Internal function, reads 1240 bytes, returns SIMSHeader dict. """
        # Called DefAnalysisBis and DefEps in OpenMIMS
        d = {}
        d['simsheader version'], d['original filename'], d['matrix'], \
            d['sigref auto'], d['sigref points'], d['sigref delta'], \
            d['sigref scan time'], d['sigref measure time'], \
            d['sigref beam time'], d['has eps centering'], d['has eps'], \
            d['central energy'], d['b field'] = \
            unpack(self.header['byte order'] + 'i 256s 256s 10i', hdr.read(556))

        d['CentralSpecies'] = self._species(hdr)
        d['ReferenceSpecies'] = self._species(hdr)

        # Don't know how long method name is, runs into null-padded zone.
        d['ref mass tube hv'], d['ref mass tube hv max var'], \
            d['sample rotation'], d['sample rotation speed'], \
            d['sample rotation synced'], d['sample name'], \
            d['user name'], d['method name'] = \
            unpack(self.header['byte order'] + '2d 3i 80s 32s 256s', hdr.read(396))

        d['original filename'] = self._cleanup_string(d['original filename'])
        d['matrix'] = self._cleanup_string(d['matrix'])
        d['sample name'] = self._cleanup_string(d['sample name'])
        d['user name'] = self._cleanup_string(d['user name'])
        d['method name'] = self._cleanup_string(d['method name'])

        d['sigref auto'] = bool(d['sigref auto'])
        d['has eps centering'] = bool(d['has eps centering'])
        d['has eps'] = bool(d['has eps'])
        d['sample rotation'] = bool(d['sample rotation'])
        d['sample rotation synced'] = bool(d['sample rotation synced'])
        d['sigref scan time'] /= 10  # 0.1 sec increments
        return d

    def _preset_start(self, hdr):
        """ Internal function; read 0 bytes (8 and back up),
            returns True or False detecting start of preset.
        """
        test = hdr.read(8)
        hdr.seek(-8, 1)

        try:
            test = self._cleanup_string(test)
        except UnicodeDecodeError:
            # Some non-null filler bytes
            return False

        # First entry in preset is .isf filename with full path
        # If preset is used at all, first entry is non-null.
        # Paths start with / (older Sun systems), or drive letter (D: newer Windows systems)
        if re.match('[A-Z]:', test) or re.match('/.', test):
            return True
        else:
            return False

    def _preset(self, hdr, group=None):
        """ Internal function; reads 1080 (slits) or 3640 (lenses) bytes,
            returns a Preset dict.
        """
        # Called ApPresetSlit/ApPresetLens in OpenMIMS
        # ApPresetDef and ApParamPreset combined here.
        if not group:
            raise ValueError("Group not set, select either 'slit' or 'lens'.")

        d = {}
        start_position = hdr.tell()
        d['isf filename'], d['preset name'], d['calibration date'], \
            d['selected'], d['parameters'] = \
            unpack(self.header['byte order'] + '256s 224s 32s 2i', hdr.read(520))

        d['selected'] = bool(d['selected'])
        d['isf filename'] = self._cleanup_string(d['isf filename'])
        d['preset name'] = self._cleanup_string(d['preset name'])
        d['calibration date'] = self._cleanup_string(d['calibration date'])
        d['calibration date'] = self._cleanup_date(d['calibration date'])

        # Presets have a fixed length: 1080 for slits, 3640 for lenses.
        # Padded with null- or CC bytes (but there may be other stuff in there).
        # There are more than d['parameters'] parameters in here, but
        # they seem to be "left-overs" from previous presets. Much the
        # same as strings which have more text after the terminating null-byte.
        # Only read first d['parameters'] parameters.
        for p in range(d['parameters']):
            param_id, value, param_name = \
                unpack(self.header['byte order'] + '2i 20s', hdr.read(28))

            param_name = self._cleanup_string(param_name)
            if not param_name:
                param_name = str(param_id)
            d[param_name] = value

        current_position = hdr.tell()
        if group == 'slit':
            skip = 1080 - (current_position - start_position)
        else:
            skip = 3640 - (current_position - start_position)
        hdr.seek(skip, 1)
        return d

    def _presets(self, hdr):
        """ Internal function; reads 11400 bytes, returns Presets dict. """
        # presput/slit = 1080
        # presput/lens = 3640
        # measure/slit = 1080
        # measure/lens = 3640
        # 2 x 1080 padding
        # padding can be before presput, inbetween presput and measure, and after measure.

        d = {}
        d['Presputter'] = {}
        padding = 0
        if not self._preset_start(hdr):
            hdr.seek(1080, 1)
            padding += 1
        d['Presputter']['Slits'] = self._preset(hdr, group='slit')
        d['Presputter']['Lenses'] = self._preset(hdr, group='lens')
        d['Measure'] = {}
        if not self._preset_start(hdr):
            hdr.seek(1080, 1)
            padding += 1
        d['Measure']['Slits'] = self._preset(hdr, group='slit')
        d['Measure']['Lenses'] = self._preset(hdr, group='lens')
        hdr.seek(1080 * (2 - padding), 1)
        return d

    def _image_hdr(self, hdr):
        """ Internal function; reads 84 bytes, returns Image dict. """
        # Called ... in OpenMIMS
        d = {}
        d['header size'], d['type'], d['width'], d['height'], d['bytes per pixel'], \
            d['masses'], d['planes'], d['raster'], d['original filename'] = \
            unpack(self.header['byte order'] + 'i 6h i 64s', hdr.read(84))

        # Called nickname in OpenMIMS
        d['original filename'] = self._cleanup_string(d['original filename'])
        if d['header size'] != 84:
            raise ValueError("Image header size is {}, not 84.".format(d['header size']))
        return d

    def _isotopes_hdr(self, hdr):
        """ Internal function; reads 176 bytes, returns Isotopes dict. """
        # Not in OpenMIMS
        d = {}
        d['blocks'], d['cycles per block'], d['rejection sigma'], d['A'], \
            d['B'], d['C'] = \
            unpack(self.header['byte order'] + '6i', hdr.read(24))
        # rest is filler with \xFF
        return d

    def _image_data(self):
        """ internal function; read data for image type. """
        if self.header['Image']['bytes per pixel'] == 2:
            # 16-bit unsigned integers, short
            dt = np.dtype(self.header['byte order'] + 'H')
        elif self.header['Image']['bytes per pixel'] == 4:
            # 32-bit unsigned integers, int
            dt = np.dtype(self.header['byte order'] + 'I')

        shape = [self.header['Image']['planes'],
                 self.header['Image']['masses'],
                 self.header['Image']['width'],
                 self.header['Image']['height']]

        self.fh.seek(self.header['header size'])

        compressedfiles = (gzip.GzipFile, bz2.BZ2File)
        try:
            notrealfiles += (lzma.LZMAFile,)
        except (NameError, AttributeError):
            pass

        if isinstance(self.fh, compressedfiles):
            self.data = np.fromstring(self.fh.read(), dtype=dt).reshape(shape)
        else:
            self.data = np.fromfile(self.fh, dtype=dt).reshape(shape)

        # We want to have a cube of contiguous data (stacked images) for each
        # mass. Swap axes 0 and 1. Returns a view, so make full copy to make
        # data access faster.
        self.data = self.data.swapaxes(0, 1).copy()

    def _isotope_data(self):
        """ Internal function; read data for isotope type. """
        self.fh.seek(self.header['header size'])
        cols, rows = unpack(self.header['byte order'] + '2i', self.fh.read(8))

        dt = np.dtype(self.header['byte order'] + 'd')
        self.data = np.empty((2 * cols, rows), dtype=dt)

        # back up 4 to align with skipping in loop
        self.fh.seek(-4, 1)
        # Is cols number of columns per mass (always 2: time, counts)
        # or number of masses per file (2 in test file)??
        for c in range(0, 2*cols, 2):
            self.fh.seek(4, 1)
            self.data[c:c+2, :] = np.fromfile(self.fh, dtype=dt, count=2*rows).reshape(2, rows)

        # remove every time columns except first
        keepcols = [0] + list(range(1, 2*cols, 2))
        self.data = self.data[keepcols]

    def _cleanup_string(self, bytes):
        """ Internal function; cuts off bytes at first null-byte,
            decodes bytes as latin-1 string, returns string
        """
        try:
            b = bytes.index(b'\x00')
        except ValueError:
            return bytes.decode('latin-1').strip()
        else:
            return bytes[:b].decode('latin-1').strip()

    def _cleanup_date(self, date):
        """ Internal function; reads date-string, returns Python datetime object.
            Assumes date-part and time-part are space separated, date is dot-separated,
            and time is colon-separated. Returns None if date is empty or not a string.
        """
        if not (date and isinstance(date, str)):
            return None

        date, time = date.split()
        day, month, year = date.split('.')
        hour, minute = time.split(':')
        year, month, day, hour, minute = [int(x) for x in (year, month, day, hour, minute)]

        # For 2-digit years, 1969/2068 is the wrap-around (POSIX standard)
        if (69 <= year < 100):
            year += 1900
        elif (0 <= year < 69):
            year += 2000

        return datetime.datetime(year, month, day, hour, minute)

    def _chomp(self, hdr, filler=(b'\x00\x00\x00\x00', b'\xCC\xCC\xCC\xCC'), chunk=4):
        """ Internal function.

            Reads and discards filler bytes, by default null (\\x00) and continue (\\xCC) bytes.
            Stops at start of first non-filler byte. Reads chunk bytes at the time.
        """
        filler = tuple(filler)
        bytes = hdr.read(chunk)
        while(bytes and bytes in filler):
            bytes = hdr.read(chunk)
        hdr.seek(-1 * chunk, 1)


class SIMSLut(object):
    """ SIMS Look-up table object. """
    def __init__(self, smooth=True):
        """ Reads all Look-Up Tables (LUTs) from the Cameca-supplied lut files and
            the L'image colour table file, converts them to Matplotlib Colormaps,
            and registers them with Matplotlib. Use mpl.colormaps() to see all
            registered Colormaps and mpl.set_cmap() to set the Colormap to use.

            By default, smooth gradient Colormaps (LinearSegmentedColormap) is
            created. Some LUTs, however, are meant to be used with hard edges and
            strong contrast between adjacent colours. To disable the smooth
            gradient, set smooth=False and ListedColormaps will be created in
            stead. Alternatively, reload one or several Colormaps by calling
            read_cameca_lut() or read_limage_lut() with smooth=False and the
            names of the LUTs to be reloaded.
        """
        import matplotlib.pyplot as mpl
        import matplotlib.colors as mpc

        # Default directory is 'lut' alongside sims.py
        self.lut_dir = inspect.getfile(self.__class__)
        self.lut_dir = os.path.abspath(self.lut_dir)
        self.lut_dir = os.path.dirname(self.lut_dir)
        self.lut_dir = os.path.join(self.lut_dir, 'lut')

        self.limage_file = os.path.join(self.lut_dir, 'limagecolors.tbl')

        # load all
        self.read_cameca_lut(smooth=smooth)
        self.read_limage_lut(smooth=smooth)

    def read_cameca_lut(self, *names, smooth=True):
        """ Load a Cameca Look-Up Table.

            Usage: read_cameca_lut('cameca bw', 'temp', ..., smooth=True)

            Reads one or more LUTs from the Cameca supplied LUT files, converts it to a
            Matplotlib Colormap and registers it with Matplotlib. The Colormaps will
            be registered with a 'cameca ' prefix to distinguish them from the L'image
            LUTs and Matplotlib's own Colormaps. To (re)load a specific LUT, the
            'cameca ' prefix may be omitted.

            If no name is given, all LUTs in the lut-directory will be loaded. The lut-
            directory is stored under 'lut_dir'. That name can be changed to load from
            a different directory.

            By default, a smooth gradient Colormap (LinearSegmentedColormap) is created
            from the data in the LUT. Some LUTs, however, are meant to be used with
            hard edges and strong contrast between adjacent colours. To disable the
            smooth gradient, set smooth=False and a ListedColormap will be created in
            stead.
        """
        if len(names) == 0:
            fnames = os.listdir(self.lut_dir)
            fnames = [os.path.join(self.lut_dir, n) for n in fnames if n.endswith('.lut')]
        else:
            names = [n[7:] for n in names if n.startswith('cameca ')]
            fnames = [os.path.join(self.lut_dir, n, '.lut') for n in names]

        # All luts need to be 0-1 normalized
        norm = mpc.Normalize(vmin=0, vmax=255)

        for fname in fnames:
            with open(fname, mode='rb') as fh:
                # skip header
                fh.seek(76)
                # Data is in single bytes, 3 rows of unknown length (either 128 or 256)
                # Data is also double, each row appears twice (from-to?)
                lut_data = np.fromfile(fh, dtype='B').reshape(3, -1).T[::2]

            name = os.path.basename(fname)
            name = 'cameca ' + os.path.splitext(name)[0]

            lut_data = norm(lut_data)

            if smooth:
                lut = mpc.LinearSegmentedColormap.from_list(name, lut_data)
            else:
                lut = mpc.ListedColormap(lut_data, name=name)
            mpl.register_cmap(cmap=lut)

    def read_limage_lut(self, *names, smooth=True):
        """ Load a L'image Look-Up Table.

            Usage: read_limage_lut('blue/white', 'limage prism', ..., smooth=True)

            Reads one or more LUTs from the L'image colortable file, converts it to a
            Matplotlib Colormap and registers it with Matplotlib. The Colormaps will
            be registered with a 'limage ' prefix to distinguish them from the Cameca-
            supplied LUTs and Matplotlib's own Colormaps. To (re)load a specific LUT,
            the 'limage ' prefix may be omitted.

            If no name is given, all LUTs in the file will be loaded. The LUTs are read
            from the filename stored under 'limage_file'. That name can be changed to
            load from a different file.

            By default, a smooth gradient Colormap (LinearSegmentedColormap) is created
            from the data in the LUT. Some LUTs, however, are meant to be used with
            hard edges and strong contrast between adjacent colours. To disable the
            smooth gradient, set smooth=False and a ListedColormap will be created in
            stead.
        """
        names = [n[7:] for n in names if n.startswith('limage ')]

        fh = open(self.limage_file, mode='rb')
        hdr = fh.read(32)
        data = fh.read()
        fh.close()

        if 'PV-WAVE CT' not in hdr[:16].decode('utf-8'):
            raise TypeError('File {} is not a PV-Wave ColorTable.'.format(limage_file))

        # All luts need to be 0-1 normalized
        norm = mpc.Normalize(vmin=0, vmax=255)

        # Number of LUTs stored as number in an ascii-encoded, null-padded string (jeez)
        n_luts = hdr[16:].decode('utf-8').strip(' \x00')
        n_luts = int(n_luts)
        offset = 32 * n_luts
        for l in range(n_luts):
            name = data[l*32:(l+1)*32].decode('utf-8').strip(' \x00').lower()
            # len(name) == 0 -> load all
            # lut_name in names -> this is (the) one we need
            # not name.startswith('?') -> signifies empty table entry
            if (not name.startswith('?')) and ((name in names) or (len(names) == 0)):
                name = 'limage ' + name

                lut_data = np.fromstring(data[offset + l*768:offset + (l+1)*768],
                                         dtype='B').reshape(3, 256).T
                lut_data = norm(lut_data)

                if smooth:
                    lut = mpc.LinearSegmentedColormap.from_list(name, lut_data)
                else:
                    lut = mpc.ListedColormap(lut_data, name=name)

                mpl.register_cmap(cmap=lut)


class SIMS(SIMSBase, SIMSLut):
    """ Read a (nano)SIMS file and load the full header and image data. """
    def __init__(self, filename, load_luts=False):
        """ Create a SIMS object that will hold all the header information and image data.
            Header information is stored as a Python dict in s.header, while the data is
            stored as a Numpy/Scipy array in s.data. All methods from SIMSBase and SIMSLut
            are inherited. The file is closed immediately after reading.

            This class can open Cameca (nano)SIMS files ('filename.im'), or files compressed
            with gzip, bzip2, or zip (e.g. 'filename.im.bz2'). With Python 3.3 or newer
            lzma and xz compressed files are also supported.

            Usage: s = sims.SIMS('filename.im' | 'filename.im.bz2' | fileobject)

            EEG Note: Local import of info.py with 'from .info import *' returns SystemError,
            it cannot import local file if parent directory isn't in the PYTHONPATH.
            In this case, navigate up one directory level and call with:
            s = sims.sims.SIMS(...), so that the parent module is loaded first.

            In addition to header and data, a set of colour look-up tables (LUTs) are also
            loaded. This requires matplotlib and loads the default backend. To prevent this
            from happening, give load_luts=False. For more info, see help in SIMSLut class.
        """
        if isinstance(filename, str):
            if filename.lower().endswith('.gz'):
                self.fh = gzip.open(filename, mode='rb')
            elif filename.lower().endswith('.bz2'):
                self.fh = bz2.BZ2File(filename, mode='rb')
            elif filename.lower().endswith('.lzma') or filename.lower().endswith('.xz'):
                if not lzma:
                    msg = 'LZMA module is not installed on your system. Use Python 3.3 or higher,'
                    msg += ' or install a module to handle lzma/xz compressed files.'
                    raise NotImplementedError(msg)
                self.fh = lzma.LZMAFile(filename, mode='rb')
            elif filename.lower().endswith('.zip'):
                msg = 'There are too many problems with zip compression support.'
                msg += ' Use gzip, bzip2, or xz compression instead.'
                raise NotImplementedError(msg)
                z = zipfile.ZipFile(filename, mode='r')
                # zipfile can store multiple files, pick first from list,
                # raise warning if more than one
                namelist = z.namelist()
                if len(namelist) > 1:
                    msg = 'There are multiple files in this zip file: {}.\n'.format(namelist)
                    msg += 'Reading only the first file: {}.'.format(namelist[0])
                    warnings.warn(msg)
                zf = z.open(namelist[0])
                # zipfile does not support seek and tell; read entire file,
                # create BytesIO instead. This is very memory inefficient.
                self.fh = io.BytesIO(zf.read())
            else:
                self.fh = open(filename, mode='rb')
        # A fileobject needs at least read, seek, and tell.
        elif (hasattr(filename, 'read')
              and hasattr(filename, 'seek')
              and hasattr(filename, 'tell')):
                # Is it in binary mode?
                if 'b' in filename.mode:
                    self.fh = filename
                else:
                    raise IOError('Fileobject {} opened in text-mode, reopen with mode="rb".')
        else:
            raise TypeError('Cannot open file {}, don\'t know what it is.')

        if load_luts:
            SIMSLut.__init__(self)
        SIMSBase.__init__(self, self.fh)
        self.peek()
        self.read_header()
        self.read_data()
        self.fh.close()


#####################################################################################
# Not implemented yet in read_header
#
#     class CalCond
#         int n_delta;
#         int np_delta;
#         int tps_comptage;
#         int nb_cycles;
#         double no_used2;
#         double cal_ref_mass;
#         String symbol;
