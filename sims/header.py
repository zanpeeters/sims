from datetime import datetime

# https://stackoverflow.com/questions/17330160/how-does-the-property-decorator-work
# https://stackoverflow.com/questions/371753/how-do-i-implement-getattribute-without-an-infinite-recursion-error

_all_vars = {
    # 'name': (type, default, (limits), doc)
    # For int, float: limits is (min, max)
    # For str: limits is (allowed strings)
    # 
    'analysis_duration': ('int', None, (0, None), 'The total duration of the measurement in seconds.'),
    'analysis_type': ('str', None, None, 'Description of the type of measurement.'),
    'analysis_version': ('int', None, (0, 4108), 'Secondary version number, see also file_version.'),
    'beam_blanking': ('bool', None, None, 'Is beam blanking enabled?'),
    'byte_order': ('str', None, ('<', '>'), 'The byte order in which the file was saved. Uses struct.pack byte order symbols.'),
    'comment': ('str', None, None, 'Comment stored in the file.'),
    'has_data': ('bool', None, None, 'Is data stored in the file?'),
    'date': ('datetime', None, None, 'Date at which file was created.'),
    # Make filetype enum
    'file_type': ('int', None, None, 'The type of experiment and related file fomat.'),
    'file_version': ('int', None, (0, 4108), 'The version number of the file format.'),
    'frames': ('int', None, (0, 5600), 'The number of frames/planes/cycles.'),
    'header_size': ('int', None, (0, None), 'The size in bytes of the header of this file.'),
    # How to deal with list?
    'label_list': ('list-str', [], None, 'The labels of the masses.'),
    'label_list_fmt': ('list-str', [], None, 'The labels of the masses formatted for display.'),
    'magnification': ('int', None, None, 'Unknown'),
    'mass_list': ('list-float', [], (0, 1000), 'The masses in amu of the measured species.'),
    'masses': ('int', None, (0, 10), 'The number of masses recorded.'),
    'n50large': ('bool', None, None, 'Is the machine a nanoSIMS 50L (large)?'),
    'original_filename': ('str', None, None, 'The original name of the file at the time of recording.'),
    'polarity': ('str', None, ('+', '-'), 'The polarity of the machine.'),
    'presputtering': ('bool', None, None, 'Is presputtering enabled?'),
    'presputtering_duration': ('int', None, (0, None), 'The duration of presputtering in seconds.'),
    'sample_type': ('int', None, None, 'Unknown'),
    'sample_x': ('int', None, (-15000, 15000), 'The X position of the sample in micrometers.'),
    'sample_y': ('int', None, (-15000, 15000), 'The Y position of the sample in micrometers.'),
    'sample_z': ('int', None, (0, 7000), 'The Z position of the sample in micrometers.'),
    'scan_type': ('str', None, None, 'Unknown'),
    'size_detector': ('int', None, None, 'Unknown'),
    'size_type': ('int', None, None, 'Unknown'),
    'username': ('str', None, None, 'Name of the user registered in the Setup program.')
}

def _setInt(cls, value, _minval=None, _maxval=None):
    if not isinstance(value, int):
        raise TypeError('{} must be int'.format(var))
    if _minval and _maxval:
        if value < _minval or value > _maxval:
            raise ValueError('{} must be >= {} and <= {}'.format(var, _minval, _maxval))
    elif _minval and value < _minval:
        raise ValueError('{} must be >= {}'.format(var, _minval))
    elif _maxval and value > _maxval:
        raise ValueError('{} must be <= {}'.format(var, _maxval))
    setattr(cls, uvar, value)

def _setFloat(cls, value, _minval=None, _maxval=None):
    if not isinstance(value, float):
        raise TypeError('{} must be float'.format(var))
    if _minval and _maxval:
        if value < _minval or value > _maxval:
            raise ValueError('{} must be >= {} and <= {}'.format(var, _minval, _maxval))
    elif _minval and value < _minval:
        raise ValueError('{} must be >= {}'.format(var, _minval))
    elif _maxval and value > _maxval:
        raise ValueError('{} must be <= {}'.format(var, _maxval))
    setattr(cls, uvar, value)


class Header(object):
    def __init__(self):
        """This is a Header."""
        super().__init__()
        for var, (typ, default, limits, doc) in _all_vars.items():
            print(var)
            minval, maxval = (None, None)
            if isinstance(limits, tuple):
                minval, maxval = limits

            uvar = '_{}'.format(var)
            setattr(self, uvar, default)

            def getfunc(self):
                return getattr(self, uvar)
            getfunc.__name__ = var
            getfunc.__doc__ = doc

            if typ == 'int':
                def setfunc(self, value, _minval=minval, _maxval=maxval):
                    _setInt(self, value, _minval=_minval, _maxval=_maxval)
            elif typ == 'float':
                def setfunc(self, value, _minval=minval, _maxval=maxval):
                    _setFloat(self, value, _minval=_minval, _maxval=_maxval)
            elif typ == 'str':
                def setfunc(self, value, _limits=limits):
                    if not isinstance(value, (bytes, str)):
                        raise TypeError('{} must be str or bytes'.format(var))
                    if isinstance(value, bytes):
                        try:
                            idx = value.index(b'\x00')
                        except ValueError:
                            value = value.decode('latin-1').strip()
                        else:
                            value = value[:idx].decode('latin-1').strip()
                    if _limits and value not in _limits:
                        raise ValueError('{} must be one of {}'.format(var, _limits))
                    setattr(self, uvar, value)
            elif typ == 'bool':
                def setfunc(self, value):
                    if not isinstance(value, bool):
                        raise TypeError('{} must be boolean'.format(var))
                    setattr(self, uvar, value)
            elif typ == 'datetime':
                def setfunc(self, value):
                    if not isinstance(value, datetime):
                        raise TypeError('{} must be datetime.datetime object'.format(var))
                    setattr(self, uvar, value)
            elif typ.startswith('list'):
                second_typ = typ.split('-')[1]
                def setfunc(self, value):
                    if not isinstance(value, (list, tuple)):
                        raise TypeError('{} must be a tuple of type ({}, )'.format(var, second_typ))
                        
            p = property(getfunc)
            p = p.setter(setfunc)
            setattr(self.__class__, var, p)

    def _cleanup_date(self, date):
        """ Reads date-string, returns Python datetime object.
            Assumes date-part and time-part are space separated, date is dot-separated,
            and time is colon-separated. Returns None if date is empty, contains 'N/A',
            or is not a string.
        """
        if (not date
            or not isinstance(date, str)
            or 'N/A' in date):
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

        return datetime(year, month, day, hour, minute)
