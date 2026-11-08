#!/usr/bin/env python3
"""Summarise perf-suite.sh runs as Markdown tables for docs/performance.md.

Usage: perf-report.py SUITE_DIR... [-o DOCS_PERF_DIR]

Reads the run directories of one or more suites (<app>-start-<n>,
<app>-bg-<n>, pair-<n>, from perf-run.sh) and prints, per app: time to the
first frame, PSS/USS after start and after the idle time, the memory split
by category, idle CPU time and wake-ups per minute, the threads that used
the most CPU while idle, GPU memory, and what two processes share. Numbers
are medians over the repeats (min-max in brackets). With -o, copies the
small result files (start.tsv, smaps-*.tsv, threads-*.tsv, idle.tsv,
meta.txt) into DOCS_PERF_DIR/<suite>/<run>/ so they can be committed.
"""
import glob
import os
import shutil
import statistics
import sys


def read_tsv(path):
    rows = []
    try:
        with open(path) as f:
            header = None
            for line in f:
                line = line.rstrip("\n")
                if not line or line.startswith("#"):
                    continue
                parts = line.split("\t")
                if header is None:
                    header = parts
                    continue
                if len(parts) == len(header):
                    rows.append(dict(zip(header, parts)))
                elif header and parts[0] == "category":
                    break  # second table of perf-smaps.py
    except OSError:
        return []
    return rows


def smaps_cats(path):
    cats = {}
    for r in read_tsv(path):
        if "category" in r and "pss_kb" in r:
            cats[r["category"]] = {k: int(v) for k, v in r.items() if k != "category"}
    return cats


def med(values, unit="", div=1, fmt="{:.0f}"):
    values = [v / div for v in values if v is not None]
    if not values:
        return "-"
    m = statistics.median(values)
    if len(values) == 1:
        return fmt.format(m) + unit
    return f"{fmt.format(m)}{unit} ({fmt.format(min(values))}-{fmt.format(max(values))}, n={len(values)})"


def idle_rates(path):
    rows = read_tsv(path)
    if len(rows) < 2:
        return None
    a, b = rows[0], rows[-1]
    minutes = (int(b["elapsed_s"]) - int(a["elapsed_s"])) / 60
    if minutes <= 0:
        return None
    cpu = (int(b["utime_ms"]) + int(b["stime_ms"]) - int(a["utime_ms"]) - int(a["stime_ms"])) / minutes
    wake = (int(b["vol_cs"]) - int(a["vol_cs"])) / minutes
    return {"cpu_ms_min": cpu, "wake_min": wake, "minutes": minutes,
            "pss_first": int(a["pss_kb"]), "pss_last": int(b["pss_kb"]),
            "uss_first": int(a["uss_kb"]), "uss_last": int(b["uss_kb"]),
            "gpu_total": int(b["gpu_total_kb"]), "gpu_res": int(b["gpu_resident_kb"])}


def thread_deltas(start, end):
    s = {r["tid"]: r for r in read_tsv(start)}
    out = []
    for r in read_tsv(end):
        s0 = s.get(r["tid"])
        cpu0 = int(s0["cpu_ms"]) if s0 else 0
        cs0 = int(s0["vol_cs"]) if s0 else 0
        out.append((r["name"], int(r["cpu_ms"]) - cpu0, int(r["vol_cs"]) - cs0))
    return sorted(out, key=lambda t: -t[1])


def main():
    args = sys.argv[1:]
    out_dir = None
    if "-o" in args:
        i = args.index("-o")
        out_dir = args[i + 1]
        del args[i:i + 2]
    runs = []
    for suite in args:
        for d in sorted(glob.glob(os.path.join(suite, "*-*"))):
            if not os.path.isdir(d) or not os.path.exists(os.path.join(d, "meta.txt")):
                continue
            name = os.path.basename(d)
            if name.startswith("pair-"):
                app, kind = "pair", "pair"
            else:
                app, kind, _ = name.rsplit("-", 2)
            meta = dict(l.split(": ", 1) for l in open(os.path.join(d, "meta.txt")) if ": " in l)
            runs.append({"dir": d, "suite": os.path.basename(suite.rstrip("/")), "name": name, "app": app, "kind": kind, "meta": meta})
            if out_dir:
                dest = os.path.join(out_dir, os.path.basename(suite.rstrip("/")), name)
                os.makedirs(dest, exist_ok=True)
                for f in os.listdir(d):
                    if f.endswith(".tsv") or f == "meta.txt" or f.startswith("meminfo-"):
                        shutil.copy(os.path.join(d, f), dest)
    apps = sorted({r["app"] for r in runs if r["app"] != "pair"})
    commits = sorted({r["meta"].get("atl_commit", "?") for r in runs})
    print(f"ATL commit(s): {', '.join(commits)}; runs: {len(runs)}; suites: {', '.join(sorted({r['suite'] for r in runs}))}\n")

    print("## Start and memory per app\n")
    print("| App | First frame (ms) | 10th frame (ms) | PSS after start (MB) | USS after start (MB) | PSS after idle (MB) | USS after idle (MB) | Idle (min) | Threads |")
    print("|---|---|---|---|---|---|---|---|---|")
    for app in apps:
        starts = [r for r in runs if r["app"] == app and r["kind"] == "start"]
        bgs = [r for r in runs if r["app"] == app and r["kind"] == "bg"]
        ff, tenth = [], []
        for r in starts + bgs:
            for row in read_tsv_plain(os.path.join(r["dir"], "start.tsv")):
                if row[1] != "-1":
                    ff.append(int(row[1]))
                if row[2] != "-1":
                    tenth.append(int(row[2]))
        pss0 = [smaps_cats(os.path.join(r["dir"], "smaps-start.tsv")).get("TOTAL", {}).get("pss_kb") for r in starts + bgs]
        uss0 = [smaps_cats(os.path.join(r["dir"], "smaps-start.tsv")).get("TOTAL", {}).get("uss_kb") for r in starts + bgs]
        pss1 = [smaps_cats(os.path.join(r["dir"], "smaps-end.tsv")).get("TOTAL", {}).get("pss_kb") for r in bgs]
        uss1 = [smaps_cats(os.path.join(r["dir"], "smaps-end.tsv")).get("TOTAL", {}).get("uss_kb") for r in bgs]
        rates = [idle_rates(os.path.join(r["dir"], "idle.tsv")) for r in bgs]
        mins = [x["minutes"] for x in rates if x]
        threads = [len(read_tsv(os.path.join(r["dir"], "threads-end.tsv"))) for r in bgs]
        print(f"| {app} | {med(ff)} | {med(tenth)} | {med(pss0, div=1024, fmt='{:.0f}')} | {med(uss0, div=1024)} | {med(pss1, div=1024)} | {med(uss1, div=1024)} | {med(mins)} | {med(threads)} |")

    print("\n## Idle cost in the background (covered window)\n")
    print("| App | CPU ms per minute | Wake-ups per minute (voluntary switches) | PSS growth during idle (MB) | GPU memory total / resident (MB) |")
    print("|---|---|---|---|---|")
    for app in apps:
        bgs = [r for r in runs if r["app"] == app and r["kind"] == "bg"]
        rates = [x for x in (idle_rates(os.path.join(r["dir"], "idle.tsv")) for r in bgs) if x]
        print(f"| {app} | {med([x['cpu_ms_min'] for x in rates])} | {med([x['wake_min'] for x in rates])} | {med([x['pss_last'] - x['pss_first'] for x in rates], div=1024, fmt='{:+.1f}')} | {med([x['gpu_total'] for x in rates], div=1024)} / {med([x['gpu_res'] for x in rates], div=1024)} |")

    print("\n## Memory by category after idle (PSS MB, median of the background runs; USS in brackets)\n")
    order = ["native-anon", "art-image", "lib-gtk", "lib-mesa", "lib-other", "stacks", "lib-art", "art-heap", "app-dex", "icu-data", "art-jit", "lib-atl", "lib-bionic", "malloc", "fonts", "graphics", "other-file", "other-anon", "icons-pixbuf", "TOTAL"]
    print("| Category | " + " | ".join(apps) + " |")
    print("|---|" + "---|" * len(apps))
    for cat in order:
        cells = []
        for app in apps:
            bgs = [r for r in runs if r["app"] == app and r["kind"] == "bg"]
            vals = [smaps_cats(os.path.join(r["dir"], "smaps-end.tsv")).get(cat) for r in bgs]
            vals = [v for v in vals if v]
            if not vals:
                cells.append("-")
                continue
            pss = statistics.median(v["pss_kb"] for v in vals) / 1024
            uss = statistics.median(v["uss_kb"] for v in vals) / 1024
            cells.append(f"{pss:.1f} ({uss:.1f})")
        print(f"| {cat} | " + " | ".join(cells) + " |")

    print("\n## Threads with the most CPU while idle (ms over the idle time, median run per app)\n")
    for app in apps:
        bgs = [r for r in runs if r["app"] == app and r["kind"] == "bg"]
        if not bgs:
            continue
        r = bgs[len(bgs) // 2]
        deltas = thread_deltas(os.path.join(r["dir"], "threads-start.tsv"), os.path.join(r["dir"], "threads-end.tsv"))
        total = sum(d[1] for d in deltas)
        top = ", ".join(f"{n} {c} ms / {w} wake-ups" for n, c, w in deltas[:6] if c > 0)
        print(f"- **{app}** ({r['name']}, {total} ms in all threads): {top}")

    pairs = [r for r in runs if r["kind"] == "pair"]
    if pairs:
        print("\n## Two apps at once (Gadgetbridge first, Chooser second)\n")
        print("| Run | PSS first alone (MB) | PSS first with second (MB) | USS first alone | USS first with second | PSS second (MB) | USS second (MB) | Shared between the two (MB) |")
        print("|---|---|---|---|---|---|---|---|")
        for r in pairs:
            a = smaps_cats(os.path.join(r["dir"], "smaps-start.tsv")).get("TOTAL", {})
            b = smaps_cats(os.path.join(r["dir"], "smaps-pair-first.tsv")).get("TOTAL", {})
            c = smaps_cats(os.path.join(r["dir"], "smaps-pair-second.tsv")).get("TOTAL", {})
            if not (a and b and c):
                continue
            shared = (a["pss_kb"] - b["pss_kb"]) * 2   # the first's PSS drops by half of what it now shares with the second
            print(f"| {r['name']} | {a['pss_kb']/1024:.0f} | {b['pss_kb']/1024:.0f} | {a['uss_kb']/1024:.0f} | {b['uss_kb']/1024:.0f} | {c['pss_kb']/1024:.0f} | {c['uss_kb']/1024:.0f} | ~{shared/1024:.0f} |")


def read_tsv_plain(path):
    try:
        with open(path) as f:
            return [l.rstrip("\n").split("\t") for l in f if l.strip()]
    except OSError:
        return []


if __name__ == "__main__":
    main()
