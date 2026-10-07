#!/usr/bin/env python3
"""Lista normalizada dos nós com rótulo: texto|desc|clicável|bounds, um por linha."""
import re, sys
s = open(sys.argv[1]).read()
for m in re.finditer(r"<node [^>]*>", s):
    n = m.group(0)
    t = re.search(r' text="([^"]*)"', n)[1]; d = re.search(r'content-desc="([^"]*)"', n)[1]
    b = re.search(r'bounds="([^"]*)"', n)[1]; c = 'clickable="true"' in n
    if t or d or c:
        print(f"{t}|{d}|{c}|{b}")
