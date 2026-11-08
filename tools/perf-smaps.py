#!/usr/bin/env python3
"""Split a process's memory by what it holds (issue #118).

Usage: perf-smaps.py PID [LABEL]

Reads /proc/PID/smaps (own processes only, no root needed) and prints two
tab-separated tables: PSS, USS (private clean + dirty), RSS and swap in kB
per category, then the biggest mappings. Categories follow the measures in
docs/performance.md: ART heap, boot image, JIT, app dex/apk, native libraries
(ART, bionic_translation, ATL, GTK stack, Mesa/graphics drivers, rest),
graphics buffers, malloc heap, thread stacks, other anonymous and other file
mappings.

The phone's kernel has no CONFIG_ANON_VMA_NAME, so anonymous mappings carry
no "[anon:...]" names. They are split by two facts instead: ART keeps its
heap spaces (region, non-moving, large object, zygote) below 4 GiB because
object references are 32 bit, while musl's malloc and thread stacks are
mapped at the top of the address space; and each thread's stack is the
mapping that holds its saved stack pointer (/proc/PID/task/TID/syscall).
What is left above 4 GiB is native memory: malloc of ART, GTK, Mesa and the
app's native code, which cannot be told apart without names.
"""
import os
import re
import sys

HOME = os.path.expanduser("~")

RULES = [
    # (category, regex on the mapping name)
    ("art-heap", r"\[anon:dalvik-(main|large object|free list|non moving|zygote|region|alloc|mark|card|bitmap|live|mod|rb|allocation|indirect ref|thread local|non-moving)"),
    ("art-heap", r"\[anon:dalvik-.*(space|heap|table|bitmap|stack|queue)"),
    ("art-jit", r"\[anon:dalvik-jit|jit-code-cache|/memfd:/jit-cache|/jit-[0-9]+\.dump|dalvik-CompilerMetadata|dalvik-LinearAlloc"),
    ("art-image", r"\.(art|oat|vdex)$|/\.cache/art/|boot\.art|/art/.*\.(art|oat)"),
    ("art-other", r"\[anon:dalvik-"),
    ("icu-data", r"/icu/.*\.dat"),
    ("app-dex", r"\.(apk|dex|odex|jar)(\s|$)|_installed_apks_|/apks/|/android_translation_layer/[^ ]*\.apk_/"),
    ("lib-art", r"/lib/art/lib[^/]*\.so|/libart[a-z]*\.so|libdexfile|libprofile|libartbase|libbase\.so|libsigchain|libnativehelper|libnativebridge|libnativeloader|libunwindstack|libbacktrace|libziparchive|liblog\.so|libcutils|libutils\.so|libicu|libjavacore|libopenjdk|libandroidio|libexpat_art|liblzma_art"),
    ("lib-bionic", r"lib(c|dl|m|pthread|rt|stdc\+\+)_bio\.so|bionic_translation|libpthread_bio|libandroid\.so|libOpenSLES"),
    ("lib-atl", r"/share/atl/|/lib/atl/|libtranslation_layer|api-impl|/android_translation_layer/natives|/\.local/lib/lib[^/]*\.so"),
    ("lib-gtk", r"libgtk|libgdk|libglib|libgobject|libgio|libgmodule|libpango|libcairo|libharfbuzz|libfribidi|libgraphene|libadwaita|libgdk_pixbuf|libpixman|libwayland|libxkbcommon|libepoxy|libfontconfig|libfreetype|libpng|libjpeg|libtiff|librsvg|libwebkit|libjavascriptcore|libsoup|libgstreamer|libgst|libportal|libsecret|libgudev|libgraphite|libthai|libdatrie|libcloudproviders|libappstream|libdbus|libgcrypt|libgpg-error|libsystemd|libtinysparql|libjson-glib|libxml2|libffi|libpcre|libmount|libblkid|libselinux|libz\.so|libbz2|libbrotli|libzstd|liblzma\.so"),
    ("lib-mesa", r"libGL|libEGL|libgbm|libdrm|libvulkan|/dri/|mesa|libglapi|libgallium|libLLVM|libxcb|libX11|libXau|libXdmcp|libelf|libexpat\.so|libsensors|libwayland-egl|libvdpau|libva|libzink|libspirv|libglslang|libshaderc"),
    ("lib-other", r"\.so(\.[0-9.]+)?(\s|$)|/ld-musl|/usr/lib/|/lib/"),
    ("graphics", r"/dev/dri/|/dev/kgsl|dmabuf|/dev/ion|\[anon:.*gralloc|mali|\[anon:kgsl|/dev/mali|shader_cache|mesa_shader_cache|\.shader|/dev/udmabuf"),
    ("malloc", r"\[heap\]|\[anon:libc_malloc|\[anon:scudo|\[anon:GWP-ASan|\[anon:jemalloc|\[anon:bionic_alloc_small|\[anon:bionic_alloc_lob"),
    ("stacks", r"\[stack\]|\[anon:stack|\[anon:thread stack|\[anon:bionic TLS|\[anon:stack_and_tls|\[anon:.*stack"),
    ("linker", r"\[anon:linker_alloc|\[anon:bionic_linker|\[anon:\.bss\]|\[anon:System property|\[anon:atexit"),
    ("other-anon", r"\[anon:|\[vvar\]|\[vdso\]|\[vsyscall\]|\(deleted\)$|/memfd:|/dev/zero|/dev/shm|/SYSV"),
    ("fonts", r"/fonts/|\.(ttf|otf|ttc|pcf|woff2?)(\s|$)|fontconfig"),
    ("icons-pixbuf", r"/icons/|\.(png|jpg|svg|gresource|cache)(\s|$)|gschemas\.compiled|/share/mime/|/locale/"),
]
COMPILED = [(c, re.compile(r)) for c, r in RULES]


def classify(name: str, start: int = 1 << 40, stacks=frozenset()) -> str:
    n = name.strip()
    for cat, rx in COMPILED:
        if rx.search(n):
            return cat
    if n.startswith("/"):
        return "other-file"
    if start in stacks:
        return "stacks"
    if start < (1 << 32):
        return "art-heap"
    return "native-anon"


def thread_stacks(pid: str, maps):
    """Start addresses of thread stacks: the mapping that holds a thread's
    saved stack pointer, and every anonymous rw mapping above 4 GiB that
    sits right after a PROT_NONE guard mapping (how musl lays out a
    pthread stack), for threads that are running at that moment."""
    starts = sorted((m["start"], m["end"]) for m in maps)
    found = set()
    prev = None
    for m in maps:
        if (prev is not None and not prev["name"].strip() and prev["perms"] == "---p"
                and not m["name"].strip() and m["perms"] == "rw-p"
                and prev["end"] == m["start"] and m["start"] >= 1 << 32):
            found.add(m["start"])
        prev = m
    for tid in os.listdir(f"/proc/{pid}/task"):
        try:
            with open(f"/proc/{pid}/task/{tid}/syscall") as f:
                fields = f.read().split()
        except OSError:
            continue
        if len(fields) < 2:
            continue
        sp = int(fields[-2], 16)
        for s_, e_ in starts:
            if s_ <= sp < e_:
                found.add(s_)
                break
    return found


def read_smaps(pid: str):
    maps = []
    cur = None
    with open(f"/proc/{pid}/smaps") as f:
        for line in f:
            m = re.match(r"^([0-9a-f]+)-([0-9a-f]+) (\S+) (\S+) (\S+) (\S+)\s*(.*)$", line)
            if m:
                cur = {"name": m.group(7), "perms": m.group(3),
                       "start": int(m.group(1), 16), "end": int(m.group(2), 16),
                       "size": (int(m.group(2), 16) - int(m.group(1), 16)) // 1024}
                maps.append(cur)
                continue
            if cur is None:
                continue
            k, _, v = line.partition(":")
            v = v.strip()
            if v.endswith("kB"):
                cur[k] = int(v.split()[0])
    return maps


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)
    pid = sys.argv[1]
    label = sys.argv[2] if len(sys.argv) > 2 else ""
    maps = read_smaps(pid)
    stacks = thread_stacks(pid, maps)
    cats = {}
    for m in maps:
        c = classify(m["name"], m["start"], stacks)
        d = cats.setdefault(c, {"count": 0, "size": 0, "rss": 0, "pss": 0, "uss": 0, "swap": 0, "swappss": 0, "shared": 0})
        d["count"] += 1
        d["size"] += m.get("Size", 0)
        d["rss"] += m.get("Rss", 0)
        d["pss"] += m.get("Pss", 0)
        d["uss"] += m.get("Private_Clean", 0) + m.get("Private_Dirty", 0)
        d["shared"] += m.get("Shared_Clean", 0) + m.get("Shared_Dirty", 0)
        d["swap"] += m.get("Swap", 0)
        d["swappss"] += m.get("SwapPss", 0)
    total = {k: sum(d[k] for d in cats.values()) for k in ("count", "size", "rss", "pss", "uss", "shared", "swap", "swappss")}
    with open(f"/proc/{pid}/cmdline", "rb") as f:
        cmd = f.read().replace(b"\0", b" ").decode(errors="replace").strip()
    threads = len(os.listdir(f"/proc/{pid}/task"))
    print(f"# pid {pid} threads {threads} label {label}")
    print(f"# cmd {cmd}")
    print("category\tmappings\tsize_kb\trss_kb\tpss_kb\tuss_kb\tshared_kb\tswap_kb\tswappss_kb")
    for c, d in sorted(cats.items(), key=lambda kv: -kv[1]["pss"]):
        print(f"{c}\t{d['count']}\t{d['size']}\t{d['rss']}\t{d['pss']}\t{d['uss']}\t{d['shared']}\t{d['swap']}\t{d['swappss']}")
    print(f"TOTAL\t{total['count']}\t{total['size']}\t{total['rss']}\t{total['pss']}\t{total['uss']}\t{total['shared']}\t{total['swap']}\t{total['swappss']}")
    print()
    print("# biggest mappings by PSS (merged by name)")
    print("category\tname\tmappings\tsize_kb\trss_kb\tpss_kb\tuss_kb\tswap_kb")
    byname = {}
    for m in maps:
        key = m["name"].strip() or "<anon " + classify("", m["start"], stacks) + ">"
        d = byname.setdefault(key, {"count": 0, "size": 0, "rss": 0, "pss": 0, "uss": 0, "swap": 0})
        d["count"] += 1
        d["size"] += m.get("Size", 0)
        d["rss"] += m.get("Rss", 0)
        d["pss"] += m.get("Pss", 0)
        d["uss"] += m.get("Private_Clean", 0) + m.get("Private_Dirty", 0)
        d["swap"] += m.get("Swap", 0)
    for name, d in sorted(byname.items(), key=lambda kv: -kv[1]["pss"])[:40]:
        print(f"{name[6:-1] if name.startswith('<anon ') else classify(name)}\t{name.replace(HOME, '~')}\t{d['count']}\t{d['size']}\t{d['rss']}\t{d['pss']}\t{d['uss']}\t{d['swap']}")


if __name__ == "__main__":
    main()
