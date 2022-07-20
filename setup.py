#!/usr/bin/env python
""" Setuptools setup file for sims. """
from setuptools import setup, find_packages
from sims.__init__ import (__version__, __pkgname__, __author__,
                           __url__, __license__, __description__)

with open('README.md', mode='rt', encoding='utf-8') as fh:
    __long_description__ = fh.read()

setup(
    name = __pkgname__,
    version = __version__,
    description = __description__,
    long_description = __long_description__,
    long_description_content_type='text/markdown',
    url = __url__,
    author = __author__,
    author_email = 'me@example.com',
    license = __license__,
    classifiers = [
        'License :: OSI Approved :: BSD License',
        'Programming Language :: Python :: 3',
        'Development Status :: 4 - Beta',
        'Development Status :: 5 - Production/Stable',
        'Intended Audience :: Science/Research',
        'Topic :: Scientific/Engineering'
    ],
    keywords = 'sims nanosims mass-spectrometry Cameca file-format',

    install_requires = [
        'matplotlib',
        'scikit-image>=0.19',
        'scipy',
        'xarray'
    ],

    packages = find_packages(),
    package_data = {'sims': ['lut/*']},

    python_requires = '>=3.8',
    tests_require = ['pytest'],

    zip_safe = False
)
