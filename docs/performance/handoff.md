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

## Item 4: measured, not merged

Written up in [performance.md](../performance.md#item-4-framework-on-the-boot-class-path-ab-measured-not-merged)
on 2026-11-09 from `ab-boot` (branch `claude/perf-framework-boot-118`,
`a276340f`, pushed, no PR). Open on the phone: a pair run
(`perf-run.sh -2`) to show the framework's image pages shared, and the
smoke test of all apps on the branch; then PR.

## Item 11: implemented, not measured

Branch `claude/atl-app-cost-reduction-whgdbj` (2026-11-09, cloud
session without the phone): `MediaPlayer` opens its GStreamer pipeline
on first use and closes it five seconds after playback ends; the muted
play-through and the fake `prepared` are gone. What to measure and
test is in [performance.md](../performance.md#item-11-mediaplayer-pipeline-on-first-use-released-after-playback).
The branch is not built: build it on the phone first (`run-fix.sh`),
then Chooser `-i 0` against `phosh`, then the smoke test and the sound
test.

## Open, by expected gain

The ranked list with gains, confidence and next steps is in
[performance.md, "What can be implemented"](../performance.md#what-can-be-implemented)
(2026-11-09). In short: A measure GPU memory (`TU_DEBUG=bos`,
`GSK_DEBUG=cache`, a plain GTK4 program as floor), B item 4's pair run
and smoke test, C item 11's measurement and sound test, E the
differential measurement of `native-anon`, F `GDK_DISABLE=gl` as an
A/B, G a 12-hour Gadgetbridge run, D the app image once E has numbers.
Item 8 and item 9 (aports) are not worth it now.

Pull request #130 (the cover fix and the item 5 and 7 results) is
merged; `tools/perf-run.sh -c` covers fully since then.

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

## Second session on the phone, 2026-11-09 (measurements A, B, C, F)

Branch `claude/perf-measure-118` (on top of #133). Scripts in
`scripts/` (`run-a.sh`, `run-a2.sh`, …), runs under `performance/`.
Steps are listed as they finish:

- **A done** (`suite-a`): `perf-idle.sh` counted dup()ed DRM fds
  several times; GPU memory is 23 MB for a plain GTK4 window, 54 MB for
  Chooser, 45 MB for Gadgetbridge, not 99 and 64. Old `gpu_*` columns
  are too high. See performance.md "A: what the GPU memory is".
- **C done** (`suite-c`): item 11 does not reduce Chooser's threads or
  memory (92 and 91 threads): Chooser uses `SoundPool`, whose
  `nativeLoad` opens a `GtkMediaFile` per sound at load. A lazy
  `SoundPool` (open on first `play`, close after the end) is the change
  that would remove Chooser's 21 GStreamer threads; not made.
  Smoke test of `phosh`: 15 of 16, Super Productivity SIGABRT as before.
- **F done** (`suite-f`): `GDK_DISABLE=gl` Chooser −1.1 s start, −39
  threads, −18 MB PSS, −24 MB GPU; DB Navigator −7 MB, −25 MB GPU, no
  faster; the two 12 MB DRM clients are GL contexts; plain WebKitGTK
  draws the same; CoMaps' map (GL `SurfaceView`) is black, so only a
  per-app list is possible. Not implemented.

Open for the next session: a lazy `SoundPool` (C), the per-app GL
switch (F), D (app image) and E (split `native-anon`; the floor's
`native-anon` is 6.8 MB against Chooser's 43 MB, use
`suite-a/floor-fds`), the remaining 5.5 to 7 MB DRM client of A.
Tool changes of this session: `perf-idle.sh` counts DRM clients once,
`perf-run.sh` takes `PERF_CMD`, `perf-drm-fds.sh`, `perf-gtk-floor.c`,
`perf-webkit-floor.c`. The remote branch of #134 is not rebased (the
rebase was tested locally: clean merge, same diff).

## Evaluation of A to F and follow-up G, 2026-11-09 late evening

Written up in performance.md, "G: who creates each GPU client, and why
app code is not used" (measurements) and "What to optimise next"
(the plan). Data `suite-g`, scripts `run-g.sh`, `run-g2.sh`,
`opens-summary.py`, tool `tools/perf-drm-open.c` (build line in its
header).

- **GPU clients are all attributed** (`suite-g/<name>/opens`): the
  bitmap path (`Bitmap.native_create_texture`) costs every app a second
  Vulkan device (5.5 to 7 MB) and an unused GL context (12.1 MB) on
  ATL's second `GdkDisplay`; `SoundPool` costs Chooser another GL
  context (12.2 MB) through `gst_play_new`; DB Navigator's fourth
  client is WebKit's own GL context.
- **The oat rejection is in the logs** (`suite-g/oat-check-*.txt`): 9
  of 16 apps log "Found duplicate classes, falling back to extracting
  from APK" on `phosh`; with item 4 (#134) komoot and DB Navigator
  still do, because of `ghax.jar` on the app's class loader path.
- **`GDK_DISABLE=dmabuf` makes things worse** (GTK falls back to its GL
  renderer) and is ruled out.
- **Next, nothing implemented yet**: `ghax.jar` on the boot class path
  (after #134 is merged), `SoundPool` lazy or decoded to PCM, the
  bitmap path on the cairo renderer or on the window's renderer. Each
  is a separate PR from `phosh`.

**The phone needs a reboot before the next measurement.** In the last
run of `run-g2.sh` (`chooser-nodmabuf-2`) the GPU driver hung: `cage`
pid 977909 is a zombie with a thread in `dma_fence_default_wait`, the
kernel's `gpu-worker` and `irq/22-arm-smmu` are in state D,
`/sys/bus/platform/devices/3d00000.gpu/power/runtime_status` reads
`suspending`, and a `cage` started afterwards (pid 984535) blocks in
`rpm_resume` and cannot be killed; it holds `/tmp/atl-heavy.lock`, so
no heavy run can start. The kernel log needs root (owner: `sudo dmesg
> /tmp/gpu-hang-dmesg.txt` before the reboot, the agent reads it).

## RAM view and transparent huge pages, 2026-11-09 night

The owner's priority is RAM, not start time. No new run was possible
(GPU hung, see above); everything here is read from the raw smaps
already in git. In `docs/performance.md`: "Transparent huge pages: the
growth while idle", "Private memory by category (the RAM view)" and
the table "Ranked for RAM" in "What to optimise next". Scripts
`ram-by-category.py`, `thp-mappings.py`, `thp-idle-growth.py`, table
`thp-idle-growth.txt`.

- **The idle growth is `khugepaged`**: transparent huge pages are
  `always` on the phone, and in 22 of 26 stored idle runs anonymous
  memory grew 7 to 20 MB together with `AnonHugePages`; in the four
  runs without new huge pages it grew 0 to 4 MB. This is the
  "outlier" of the earlier evaluations.
- **What cannot be dropped** (private dirty plus resident GPU memory)
  at start: Chooser 119 MB, Gadgetbridge 140 MB, DB Navigator 204 MB.
  It is almost all `native-anon` (44 to 89 MB without stacks), thread
  stacks (9 to 20 MB), the ART heap (5 to 28 MB) and GPU memory (40 to
  48 MB).
- **Item 4 in this view**: Gadgetbridge -21 MB anonymous, +44 MB file
  pages that the kernel can drop. PSS rises, the memory the phone
  cannot get back falls.
- **Correction**: DB Navigator's 30 MB `app-dex` are clean pages of the
  APK, not anonymous memory; the "-32 MB private" expected from the
  `ghax.jar` fix was wrong and is taken out of the plan.
- **First run after the reboot**: `scripts/run-thp.sh` (copy in
  `~/work/perf-runs/`), the A/B of `prctl(PR_SET_THP_DISABLE)` through
  the wrapper `tools/perf-thp-off.c` (built in
  `~/work/perf-runs/bin/perf-thp-off`), about 50 minutes:
  `systemd-run --user --unit=perf-thp --collect ~/work/perf-runs/run-thp.sh`.
  It was never run, so check its first run by hand. Look at anonymous
  memory at start and after 10 minutes, `AnonHugePages`, first frame
  and CPU.
- **Nothing is implemented.** The fix would be one line in ATL's
  `main()`, a separate PR from `phosh`.
