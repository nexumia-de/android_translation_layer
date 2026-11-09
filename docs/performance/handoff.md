# Issue #118: state and data for the evaluation (2026-11-09)

Everything measured for #118 on the owner's Fairphone 6 is in this
directory, so the work can go on without the phone. What is done and
open is in [performance.md](../performance.md) (Results, Fixes, Code
analysis); this file says where the data is and what is not yet
written there. The agent's step-by-step log of 2026-11-08 and
2026-11-09 is [agent-log-2026-11-08-09.md](agent-log-2026-11-08-09.md).

## Data

Each run directory has the files `tools/perf-run.sh` writes (see the
script's header): `meta.txt` (date, ATL commit, command), `start.tsv`
(`tools/perf-start.py`: column 2 = ms to the first frame),
`smaps-start.tsv` / `smaps-end.tsv` (`tools/perf-smaps.py`: per
category mappings, size, RSS, PSS, USS, shared, swap in kB),
`threads-*.tsv`, `idle.tsv` (`tools/perf-idle.sh`: per minute CPU,
voluntary context switches, PSS, USS, GPU memory), `meminfo-*.txt`.
Screenshots, app logs and raw smaps are left out, except the raw smaps
of `ab-boot` (`smaps-start.txt.gz`), which item 4 needs for Private_Dirty.
`tools/perf-report.py DIR/suite` summarises a suite.

| Directory | What | ATL |
|---|---|---|
| `suite-p1`, `suite-p2` | baseline of 2026-11-08 | `cbd9da5e` |
| `fix-0-phosh` | before items 1+6 (agent on the laptop) | `43d35a19` |
| `fix-1-flags` | items 1+3+6 (item 3 rejected) | `f4af2d77` |
| `fix-1b-flags-no3` | items 1+6 = before item 2 | `5f20712c` |
| `fix-2-webkit` | item 2, merged in #129 | `0e21d572` |
| `fix-3-phosh` | before items 5 and 7 (second agent on the phone) | `fe78b717` |
| `fix-5-vsync` | item 5, rejected | `4256e495` |
| `fix-7-bitmap` | item 7, no clear gain | `4967e110` |
| `check-cover-fs` | Chooser fully covered (foot -F), 2 min | `fe78b717` |
| `ab-boot` | item 4 A/B on one build, see below | `a276340f` |

Each `fix-*` has `smoke/smoke.tsv` (all apps of [apps.md](../apps.md),
45 s each), `run.log`, `suite/` (Chooser and Gadgetbridge, 3 start and
3 "covered" 10-minute idle runs) and, from `fix-3-phosh` on, three
visible Chooser runs `suite/chooser-visible-N` (5 min idle, not in
`perf-report.py`: compute from `idle.tsv`). `report.txt` is the
`perf-report.py` output where it was made.

**All "covered" runs before `check-cover-fs` were not fully covered**
(a strip of the app stayed visible, see performance.md "The covered
runs were not covered"): their CPU and wake-up numbers are those of a
visible window; memory is not affected.

**Conditions**: `fix-0-phosh` ran with the agent on the laptop, from
`fix-1b` on the agent ran on the phone, from `fix-3-phosh` on a second
agent session (account `user`) ran there too. Compare only pairs within
the same conditions. CPU per series drifts by about 15 %; the PSS of
shared libraries (`lib-mesa`) swings with the other processes on the
phone, so compare USS or Private_Dirty there.

## Item 4: not yet in performance.md

Branch `claude/perf-framework-boot-118` (pushed, no PR): `api-impl.jar`
on the boot class path through `BOOTCLASSPATH` (ART's ten default jars
plus `api-impl.jar`), so the framework is compiled into the boot image;
`ATL_FRAMEWORK_ON_CLASSPATH=1` switches it off. `-Xbootclasspath/a:`
was tried first and does not work: the dex2oat processes ART starts for
the apps' dex files pass only `--boot-image` and read ART's default
boot class path, take the image for stale and delete it, so it was
built again at every start (`art/runtime/oat_file_assistant.cc`,
`GetBestOatFile`). `main.c` also passes a null class loader to
`Runtime.loadLibrary` when the framework's loader is the
`BootClassLoader`, which finds no libraries.

`ab-boot` (script `scripts/ab-boot.sh`): one build, each arm with its
own `XDG_CACHE_HOME` (else each switch rebuilds the boot image), one
warm-up start, then 4 Chooser starts alternating, 30 s settle, no idle.

| Run | Private_Dirty | Private_Clean | PSS | First frame (ms) |
|---|---|---|---|---|
| boot 1-4 | 64.4 / 64.4 / 65.1 / 65.1 | 85.7 / 85.4 / 85.5 / 85.5 | 173.6 / 173.5 / 174.2 / 174.1 | 2203 / 2195 / 2236 / 2195 |
| classpath 1-4 | 67.1 / 71.0 / 70.9 / 70.9 | 75.6 / 75.7 / 75.9 / 75.5 | 166.4 / 170.4 / 170.4 / 170.0 | 2173 / 2361 / 2434 / 2336 |

(MB from `scripts/pd.py` on `smaps-start.txt.gz`.) Reading: about 6 MB
less private dirty memory per app and about 150 ms earlier first frame
(medians); 10 MB more private *clean* pages, the framework's boot image
files, which a second ATL app shares (only one ATL process ran, so they
count as private and raise PSS by 4 MB here). To confirm: a pair run
(two apps at once, `perf-run.sh -2`) to show the clean pages shared,
and the smoke test of all apps on the branch. The boot image with the
framework is built in 7 s (25 s CPU) at the first start after every ATL
update. `boot-api-impl.art` is only 244 kB: few framework classes are
in the image itself; an image class list or profile for
`GenerateImage` in art_standalone could give more. Side finding:
`api-impl.jar` is still on `java.class.path` too (`ATLPaths` and
`AssetManager` read it from there), so ART also tries to compile it as
class path dex at the first start and fails without harm.

## Open, by expected gain

1. Item 4: pair run and smoke test on the phone, then docs and PR.
2. Item 11: a GStreamer pipeline per MediaPlayer, about 25 threads in
   Chooser.
3. Item 8: Mesa threads (Chooser: 10 `gdrv`, 12 `traceq`, 8 `ir3q`,
   10 `disk$`): disk cache and u_trace environment switches.
4. Baseline again with the fixed cover; then HeliBoard, DB Navigator,
   WhatsApp and FluffyChat; work on what costs most.
5. On the real phone: does phoc stop frame callbacks for an app in the
   background? Expected yes.
6. Item 9 belongs to nexumia-de/aports (the owner's pipeline).

Pull request #130 (the cover fix and the item 5 and 7 results) is
waiting for the owner's merge; this branch builds on it.

## Scripts

`scripts/` holds the phone-side scripts as they ran (they use the
phone's paths, `~/work/...`): `run-fix.sh` (check out a ref, build,
smoke test, suite; `VISIBLE=1` adds visible Chooser runs),
`run-chain.sh` (several fixes in a row), `ab-flags.sh` and `ab-boot.sh`
(A/B on one build), `run-all.sh`, `run-p4.sh`, `run-p5.sh` (baseline
passes), and `pd.py`, `smapsum.py`, `bigdirty.py` (Private_Dirty totals,
per-mapping difference and the largest dirty mappings of raw smaps).
On the phone long jobs must run as `systemd-run --user --unit=NAME
--collect` (logind kills session scopes), and `~/.local/bin` must be on
`PATH` for `headless-shot.sh -x`.
