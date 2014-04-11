#!/usr/bin/env python
# -*- coding: utf-8 -*-
""" Recursive keyword search in dictionaries. """
__all__ = ['rkeys', 'rkeys_path', 'print_dict']

def rkeys(dictionary, root=[], path=[]):
    """ Recursively search nested dictionaries.
        
        Usage:
        >>> d = {'k1': 0, 'k2': {'k2a': 1, 'k2b': 2}}
        >>> rkeys(d)
        [['k1'], ['k2'], ['k2', 'k2a'], ['k2', 'k2b']]
        
        Returns a sorted list of lists, containing the nested keyword elements.
        Set root to ['a', 'path'] to start searching from that depth. If path
        is non-empty, the results will be appended.
    """
    for key, value in dictionary.items():
        if root:
            kpath = root + [key]
        else:
            kpath = [key]
        path.append(kpath)
        if isinstance(value, dict):
            rkeys(value, root=kpath, path=path)
    return sorted(path)

def rkeys_path(dictionary, root='', path=[], sep='/'):
    """ Recursively prints nested dictionaries.
        
        Usage:
        >>> d = {'k1': 0, 'k2': {'k2a': 1, 'k2b': 2}}
        >>> rkeys_path(d)
        ['k1', 'k2', 'k2/k2a', 'k2/k2b']
        
        Returns a sorted list with the nested keywords represented as paths.
        Set root to ['a', 'path'] to start searching from that depth. If path
        is non-empty, the results will be appended. Set sep to use a different
        path seperator (default is '/').
    """
    for key, value in dictionary.items():
        if root:
            kpath = sep.join((root, key))
        else:
            kpath = key
        path.append(kpath)
        if isinstance(value, dict):
            rkeys_path(value, root=kpath, path=path)
    return sorted(path)

def print_dict(dictionary, indent='  ', braces=('[', ']'), _n=0):
    """ Recursively prints nested dictionaries.
        
        Usage:
        >>> d = {'k1': 0, 'k2': {'k2a': 1, 'k2b': 2}}
        >>> print_dict(d)
        k1 = 0
          [k2]
            k2a = 1
            k2b = 2
        
        Dictionary is printed as sorted 'key = value' pairs, nested dictio-
        naries are indented and the main keyword surrounded in braces. Set
        indent to the indentation string (default is 2 spaces) and braces to a
        list of 2 strings for opening and closing braces (default is ('[', ']')).
        Set braces to empty ('', '') to have no braces.
    """
    for key, value in sorted(dictionary.items()):
        if isinstance(value, dict):
            print('{}{}{}{}'.format(_n * indent,
                                    (_n + 1) * braces[0],
                                    key,
                                    (_n + 1) * braces[1]))
            print_dict(value, indent=indent, braces=braces, _n=_n+1)
        else:
            print('{}{} = {}'.format(_n * indent, key, value))

if __name__ == '__main__':
    example_dict = {'key1': 'value1',
                    'key2': 'value2',
                    'key3': {'key3a': 'value3a'},
                    'key4': {'key4a': {'key4aa': 'value4aa',
                                       'key4ab': 'value4ab',
                                       'key4ac': 'value4ac'},
                             'key4b': 'value4b'}
                   }
    print('Example dictionary:\n')
    print(example_dict)
    print()
    print('Output of rkeys():\n')
    print(rkeys(example_dict))
    print()
    print('Output of rkeys_path():\n')
    print(rkeys_path(example_dict))
    print()
    print('Print nested dictionaries nicely with print_dict():\n')
    print_dict(example_dict)
