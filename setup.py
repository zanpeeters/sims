#!/usr/bin/env python
""" Setuptools setup file for sims. """
from setuptools import setup, find_packages
import os

with open(os.path.join('sims', '__init__.py'), mode='rt', encoding='utf-8') as fh:
    script = []
    for l in fh.readlines():
        l = l.strip()
        # don't import anything, just get metadata
        if l.startswith('from') or l.startswith('import'):
            continue
        script.append(l)

exec('\n'.join(script))

with open('README.rst', mode='rt', encoding='utf-8') as fh:
    __long_description__ = fh.read()

setup(
    name = __name__,
    version = __version__,
    description = __description__,
    long_description = __long_description__,
    url = __url__,
    author = __author__,
    author_email = 'me@example.com',
    license = __license__,
    classifiers = [
        'License :: OSI Approved :: BSD License',
        'Programming Language :: Python :: 2.7',
        'Programming Language :: Python :: 3',
        'Development Status :: 4 - Beta',
        'Development Status :: 5 - Production/Stable',
        'Intended Audience :: Science/Research',
        'Topic :: Scientific/Engineering'
    ],
    keywords = 'sims nanosims mass-spectrometry Cameca file-format',

    install_requires = [
        'matplotlib',
        'scikit-image',
        'scipy',
        'xarray'
    ],

    packages = find_packages(),
    package_data = {'sims': ['lut/*']},
    test_suite = 'test',
    zip_safe = False
)
