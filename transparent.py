#!/usr/bin/env python
# -*- coding: utf-8 -*-
""" Python module to transparently open compressed files. """

import os, io

class TransparentOpen(object):
    """ File opener class with transparent support for compressed files.
        Supported compression types: .gz, .bz2, .lzma, .xz, .7z, .zip,
        .tar, .tar.gz, .tgz, .tar.bz2, .tbz, .tbz2, .tar.xz, .txz,
        .tar.lzma, .tlz.
    """
    def __init__(self, filename, file_in_archive=0, password=None):
        """ Usage: t = TransparentOpen('filename', file_in_archive='abc.txt', password='xxx')

            Returns an object t with a filehandle t.fh which points to the
            decompressed file. In the case of a multifile archive (.zip,
            .7z, and any of the tar combinations), a t.fh_archive will point to
            the opened outer archive, while t.fh points to the requested file
            inside the archive. The filename is stored in t.filename.
            
            file_in_archive can be used to select a file from a multifile
            archive, either by index (0 is the first file in the archive),
            or by filename. On python 2, the filename must be a byte string.
            
            Optionally set the password for encrypted archives. Passwords are
            only supported on zip and 7zip archives.
            
            Use t.close() to close both inner and outer filehandles at once.
            
            TransparentOpen supports the with statement.
        """
        if not isinstance(file_in_archive, (int, str)):
            raise IOError('file_in_archive must be int or str.')

        self.filename = ''
        self.fh_archive = None

        if isinstance(filename, str):
            base, ext = os.path.splitext(filename)
            base_maybe, ext_maybe = os.path.splitext(base)
            if ext_maybe == '.tar':
                base = base_maybe
                ext = ext_maybe + ext

            self.filename = base
            if ext == '.gz':
                import gzip
                self.fh = gzip.open(filename, mode='rb')

            elif ext == '.bz2':
                import bz2
                self.fh = bz2.BZ2File(filename, mode='rb')

            elif ext in ('.lzma', '.xz'):
                # available in Python 3.3 and higher
                try:
                    import lzma
                except ImportError:
                    msg = 'LZMA module is not installed on your system. Use Python 3.3 or higher,'
                    msg += ' or install a module to handle lzma/xz compressed files.'
                    raise IOError(msg)

                self.fh = lzma.LZMAFile(filename, mode='rb')

            elif ext in ('.tar', '.tar.bz2', '.tbz', '.tbz2', '.tar.gz', '.tgz',
                         '.tar.xz', '.txz', '.tar.lzma', '.tlz'):
                import tarfile

                self.fh_archive = tarfile.open(filename, mode='r')
                if isinstance(file_in_archive, int):
                    if file_in_archive == 0:
                        # No need to scan trough entire tar first
                        m = self.fh_archive.firstmember
                    else:
                        m = self.fh_archive.getmembers()
                        m = m[file_in_archive]
                    self.fh = self.fh_archive.extractfile(m)
                    self.filename = m.name
                else:
                    self.fh = self.fh_archive.extractfile(file_in_archive)
                    self.filename = file_in_archive

            elif ext == '.7z':
                # py7zlib is needed for 7z
                try:
                    import py7zlib
                except ImportError:
                    msg = 'pylzma/py7zlib module is not installed on your system. See'
                    msg += ' http://www.joachim-bauch.de/projects/pylzma'
                    raise IOError(msg)

                with open(filename, mode='rb') as fh_raw:
                    archive = py7zlib.Archive7z(fh_raw, password=password)

                    if isinstance(file_in_archive, int):
                        f = archive.files[file_in_archive]
                    else:
                        f = archive.files_map[file_in_archive]

                    self.fh = io.BytesIO(f.read())

            elif ext == '.zip':
                import zipfile

                fh_archive = zipfile.ZipFile(filename, mode='r')

                if password:
                    fh_archive.setpassword(password)

                if isinstance(file_in_archive, int):
                    infolist = fh_archive.infolist()
                    fh_file = fh_archive.open(infolist[file_in_archive])
                else:
                    fh_file = fh_archive.open(file_in_archive)

                self.fh = io.BytesIO(fh_file.read())
                fh_file.close()
                fh_archive.close()

            else:
                # No compression or unrecognized
                self.fh = open(filename, mode='rb')
                self.filename = filename

        elif hasattr(filename, 'read'):
            if (hasattr(filename, 'seek') and hasattr(filename, 'tell')):
                # Is it in binary mode?
                if 'b' in filename.mode:
                    self.fh = filename
                    self.filename = filename.name
                else:
                    msg = 'Fileobject {} opened in text-mode, reopen with mode="rb".'
                    raise IOError(msg.format(filename))
            else:
                # Read but no seek and/or tell: wrap in BytesIO; let's hope it has a name.
                self.fh = io.BytesIO(filename.read())
                self.filename = filename.name
        else:
            msg = 'Cannot open file {}, don\'t know what it is.'
            raise IOError(msg.format(filename))

    def close(self):
        """ Close the file. """
        self.fh.close()
        if self.fh_archive:
            self.fh_archive.close()

    def __enter__(self):
        return self

    def __exit__(self, type, value, traceback):
        self.close()

    