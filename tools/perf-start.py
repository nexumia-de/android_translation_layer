#!/usr/bin/env python3
"""Start an app and report the time to its first frame (issue #118).

Usage: perf-start.py RESULT_FILE COMMAND [ARGS...]

Runs COMMAND with WAYLAND_DEBUG=client, reads its output line by line, and
appends one tab-separated line to RESULT_FILE: date, the time from the
exec to the first wl_surface.commit that carries a buffer (the first frame
the compositor can show), to the tenth such commit (the window is drawing
steadily), the number of frames in the first 10 seconds, the time to the
first xdg_toplevel.configure, the command, and the time to the first
Wayland message at all (GTK is initialised; everything before it is ART
and ATL starting up). The app's
output, without the Wayland debug lines, goes on to stdout.

libwayland 1.24 prints its timestamps as UTC time of day with microseconds
(older versions: milliseconds of the realtime clock modulo 2^32), so the
start time is taken from the same clock right before the exec and compared
within the day. The wrapper forwards SIGTERM and SIGINT to the app, so a
harness can stop it through this pid.
"""
import os
import re
import signal
import subprocess
import sys
import time

# libwayland >= 1.24: "[HH:MM:SS.uuuuuu] {queue}  -> wl_surface#28.commit()" (UTC);
# older: "[1234567.890]  -> wl_surface@28.commit()" (ms of CLOCK_REALTIME mod 2^32).
TS_NEW = re.compile(r"^\[(\d{2}):(\d{2}):(\d{2})\.(\d{6})\]\s+(?:\{[^}]*\}\s+)?(->\s+)?(\S+?)#(\d+)\.(\w+)\((.*)\)")
TS_OLD = re.compile(r"^\[\s*(\d+)\.(\d{3})\]\s+(->\s+)?(\S+?)@(\d+)\.(\w+)\((.*)\)")
DAY_MS = 24 * 3600 * 1000


def main():
    if len(sys.argv) < 3:
        print(__doc__)
        sys.exit(1)
    result_file, cmd = sys.argv[1], sys.argv[2:]
    env = dict(os.environ, WAYLAND_DEBUG="client")
    t0 = time.time()
    t0_ms = int(t0 * 1000) % (1 << 32)
    t0_day_ms = int(t0 * 1000) % DAY_MS   # UTC milliseconds since midnight
    proc = subprocess.Popen(cmd, env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    # Harnesses that only know this wrapper's pid find the app's pid here.
    with open(os.path.join(os.environ.get("XDG_RUNTIME_DIR", "/tmp"), "atl.pid"), "w") as f:
        f.write(str(proc.pid))

    def forward(signum, _frame):
        proc.send_signal(signum)
    signal.signal(signal.SIGTERM, forward)
    signal.signal(signal.SIGINT, forward)

    attached = set()          # surfaces that got a buffer since their last commit
    commits = []              # ms since t0 of each commit with a buffer
    first_window = None       # first xdg_toplevel/configure
    first_wayland = None      # first Wayland message at all: GTK is up
    written = False

    def ms_since_start(sec, ms):
        t = (int(sec) * 1000 + int(ms)) % (1 << 32)
        return (t - t0_ms) % (1 << 32)

    def ms_since_start_day(h, mi, s, us):
        t = ((int(h) * 60 + int(mi)) * 60 + int(s)) * 1000 + int(us) // 1000
        return (t - t0_day_ms) % DAY_MS

    def parse(line):
        m = TS_NEW.match(line)
        if m:
            h, mi, s, us, outgoing, iface, oid, event, args = m.groups()
            return ms_since_start_day(h, mi, s, us), outgoing, iface, oid, event, args
        m = TS_OLD.match(line)
        if m:
            sec, ms, outgoing, iface, oid, event, args = m.groups()
            return ms_since_start(sec, ms), outgoing, iface, oid, event, args
        return None

    def write():
        nonlocal written
        if written:
            return
        written = True
        first = commits[0] if commits else -1
        tenth = commits[9] if len(commits) >= 10 else -1
        in10s = sum(1 for c in commits if c <= 10000)
        with open(result_file, "a") as f:
            f.write(f"{time.strftime('%Y-%m-%dT%H:%M:%S')}\t{first}\t{tenth}\t{in10s}\t{first_window if first_window is not None else -1}\t{' '.join(cmd)}\t{first_wayland if first_wayland is not None else -1}\n")

    deadline = None
    for raw in proc.stdout:
        line = raw.decode(errors="replace").rstrip("\n")
        parsed = parse(line)
        if not parsed:
            sys.stdout.write(line + "\n")
            sys.stdout.flush()
            continue
        t, outgoing, iface, oid, event, args = parsed
        if first_wayland is None:
            first_wayland = t
        if iface == "wl_surface" and event == "attach" and outgoing and not args.startswith("nil"):
            attached.add(oid)
        elif iface == "wl_surface" and event == "commit" and outgoing and oid in attached:
            attached.discard(oid)
            commits.append(t)
            if len(commits) == 1:
                sys.stdout.write(f"# perf-start: first frame after {commits[0]} ms\n")
                sys.stdout.flush()
                deadline = time.time() + 12
        elif iface == "xdg_toplevel" and event == "configure" and not outgoing and first_window is None:
            first_window = t
        if deadline and time.time() > deadline and not written:
            write()
            sys.stdout.write(f"# perf-start: {len(commits)} frames with buffers so far, tenth at {commits[9] if len(commits) >= 10 else 'n/a'} ms\n")
            sys.stdout.flush()
    proc.wait()
    write()
    sys.exit(proc.returncode)


if __name__ == "__main__":
    main()
