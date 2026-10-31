#!/usr/bin/env python3
"""Print the stubs a run reached, from an app log of the stub-log build.

Usage: python3 -I tools/atl-stub-hits.py APP_LOG

One line per stub, sorted: the method, a tab, and the first stack frame outside
the framework, or "-" if there is none in the six logged frames, so ATL called
it itself. See docs/app-bring-up.md.
"""
import re
import sys

FRAMEWORK = re.compile(r"(android\.(?!support\.|arch\.)|java\.|javax\.|dalvik\.|sun\.|libcore\.|com\.android\.)")


def main():
    hits = {}
    name = None
    with open(sys.argv[1], errors="replace") as f:
        for line in f:
            if line.startswith("ATL-STUB "):
                name = line.split()[1]
                hits[name] = "-"
            elif line.startswith("    at ") and name:
                frame = line[7:].split("(")[0]
                if hits[name] == "-" and not FRAMEWORK.match(frame):
                    hits[name] = frame
            else:
                name = None
    for name in sorted(hits):
        print("%s\t%s" % (name, hits[name]))


if __name__ == "__main__":
    main()
