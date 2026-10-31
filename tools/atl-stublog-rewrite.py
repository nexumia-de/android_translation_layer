#!/usr/bin/env python3
"""Instrument ATL's api-impl for a stub log. Never commit the rewritten sources.

Usage: python3 -I tools/atl-stublog-rewrite.py ATL_WORKTREE

Adds android.atl.StubLog and a call StubLog.hit("Class.method") at the start of
every method in src/api-impl whose body is empty or only returns a default
value (null, false, 0, -1, "", an empty collection or array). At run time each
stub logs once, with a short stack, as "ATL-STUB Class.method" lines on stderr.
Existing "not implemented" log lines of ATL are not touched; grep them too.
Use it on a throwaway worktree, see docs/app-bring-up.md.
"""
import os
import re
import sys

MODIFIERS = {"public", "protected", "private", "static", "final", "synchronized", "native",
             "abstract", "default", "strictfp", "transient", "volatile"}
DEFAULT_RETURN = re.compile(
    r"return\s+(null|false|0|0L|0l|0f|0F|0\.0f|0\.0|0\.0d|-1|-1L|\"\"|"
    r"Collections\.empty\w*\(\)|new [\w.<>]+\[0\]|new ArrayList<[^>]*>\(\)|new \w+\[\]\s*\{\s*\})\s*;")
# a method head that ends with "{" on one line, e.g. "\tpublic int foo(int a) throws X {"
HEAD = re.compile(r"^(\s+)([\w<>\[\], .?@]+?)\s+(\w+)\s*\(([^()]*)\)\s*(throws [\w., ]+)?\s*\{(.*)$")

STUBLOG = """package android.atl;

import java.util.concurrent.ConcurrentHashMap;

/* LOCAL DEBUG: logs each ATL stub once, with a short stack */
public class StubLog {
	private static final ConcurrentHashMap<String, Boolean> seen = new ConcurrentHashMap<>();

	public static void hit(String name) {
		if (seen.putIfAbsent(name, Boolean.TRUE) != null)
			return;
		StackTraceElement[] stack = new Throwable().getStackTrace();
		StringBuilder sb = new StringBuilder("ATL-STUB ").append(name).append(" [").append(Thread.currentThread().getName()).append("]");
		for (int i = 2; i < stack.length && i < 8; i++)
			sb.append("\\n    at ").append(stack[i]);
		System.err.println(sb);
	}
}
"""


def is_stub_body(body):
    body = body.strip()
    return body == "" or bool(DEFAULT_RETURN.fullmatch(body))


def rewrite(path, cls):
    lines = open(path).read().split("\n")
    out = []
    count = 0
    i = 0
    while i < len(lines):
        line = lines[i]
        m = HEAD.match(line)
        if m and "=" not in m.group(2) and not line.lstrip().startswith(("//", "*", "/*", "return", "new ", "else", "if", "for", "while", "switch", "catch", "try")):
            tokens = m.group(2).replace("@Override", "").replace("@Export", "").split()
            types = [t for t in tokens if t not in MODIFIERS and not t.startswith("@")]
            name = m.group(3)
            rest = m.group(6).strip()
            call = 'android.atl.StubLog.hit("%s.%s");' % (cls, name)
            if types and name not in ("if", "for", "while", "switch", "catch", "synchronized") and "abstract" not in tokens:
                # one-line body: "... {}" or "... { return null; }"
                if rest.endswith("}") and is_stub_body(rest[:-1]):
                    brace = line.index("{", line.index(")"))
                    out.append(line[: brace + 1] + " " + call + " " + line[brace + 1 :].strip())
                    count += 1
                    i += 1
                    continue
                # body on the following lines
                if rest == "":
                    j = i + 1
                    body = []
                    while j < len(lines) and lines[j].strip() != "}" and len(body) < 2:
                        body.append(lines[j])
                        j += 1
                    if j < len(lines) and lines[j].strip() == "}" and len(body) <= 1 and \
                            (not body or is_stub_body(body[0]) or body[0].strip().startswith("//")):
                        out.append(line)
                        out.append(m.group(1) + "\t" + call)
                        count += 1
                        i += 1
                        continue
        out.append(line)
        i += 1
    if count:
        open(path, "w").write("\n".join(out))
    return count


def main():
    root = os.path.join(sys.argv[1], "src", "api-impl")
    total = 0
    for d, _, files in os.walk(root):
        for f in files:
            if not f.endswith(".java") or f == "StubLog.java":
                continue
            path = os.path.join(d, f)
            cls = os.path.relpath(path, root)[:-5].replace("/", ".")
            total += rewrite(path, cls)
    open(os.path.join(root, "android", "atl", "StubLog.java"), "w").write(STUBLOG)
    meson = os.path.join(root, "meson.build")
    s = open(meson).read()
    if "android/atl/StubLog.java" not in s:
        s = s.replace("\t'android/atl/ATLLoadedApp.java',\n", "\t'android/atl/ATLLoadedApp.java',\n\t'android/atl/StubLog.java',\n", 1)
        open(meson, "w").write(s)
    print("instrumented %d methods" % total)


if __name__ == "__main__":
    main()
