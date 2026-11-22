#!/usr/bin/env python3
"""Compact view of perf-drm-open logs: per open, the fd and the named frames / library chain."""
import sys, re
for path in sys.argv[1:]:
    print("####", path)
    blocks = open(path).read().split("== ")[1:]
    for b in blocks:
        lines = b.splitlines()
        head = lines[0]
        chain = []
        for l in lines[1:]:
            m = re.match(r"\s+(\S+)\s+(\S+?)\+0x[0-9a-f]+ \(lib\+(0x[0-9a-f]+)\)", l)
            if not m: continue
            lib, sym, off = m.groups()
            if lib.startswith("perf-drm-open"): continue
            lib = re.sub(r"\.so.*", "", lib)
            item = f"{lib}:{sym}" if sym != "?" else lib
            if sym == "?" and chain and chain[-1].split(":")[0].split(" x")[0] == lib: continue
            if chain and chain[-1] == item: continue
            chain.append(item)
        print(" ", head)
        print("    " + " < ".join(chain[:22]))
