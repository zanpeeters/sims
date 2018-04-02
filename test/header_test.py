#!/usr/bin/env python
import sims.header

h = sims.header.Header()

print(h.analysis_duration)
print(type(h.analysis_duration))
print(h.analysis_type)
print(type(h.analysis_type))

h.analysis_duration = 1000000
print(h.analysis_duration)
print(type(h.analysis_duration))

h.analysis_type = 'abc'
print(h.analysis_type)
print(type(h.analysis_type))

h.analysis_type = b'abc\x00ef'
print(h.analysis_type)
print(type(h.analysis_type))
