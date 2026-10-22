# Roadmap and acceptance gates

## Phase 0 — Foundation (implemented with remaining hardening)

- [x] Repository/environment audit recorded.
- [x] Portable C++20 rational-time/timeline foundation, edit-command/history, project codec/store, host tests, CMake and host CI.
- [x] Kotlin Android shell, pinned Gradle/AGP/Kotlin/SDK/NDK/CMake, owned JNI session, Android build/lint/alignment workflow, and instrumentation sources.
- [x] Versioned project schema (v1) and v0 migration reader; move/split/trim/delete/single-track ripple-delete commands with undo/redo.
- [x] C++ autosave snapshot API; debounced Activity autosave, best-effort shutdown save, and explicit recovery confirmation.
- [x] SAF video import and Android-visible preview/editing shell. Galaxy F23 user reports playback and editing features work; see device matrix for the scope of that evidence.
- [x] App-private project save/load and SAF project-document Save As/Open adapter; Android build/lint/AndroidTest-source compilation passed in run `39247694047` (real provider test still pending).
- [x] Linux/Android local project replace now syncs temporary file and parent directory after rename.
- [ ] Write-ahead crash-recovery journal, provider-safe atomic replacement, power-loss fault injection, broad instrumentation and recovery failure tests.

Gate: host tests pass; Android debug build/lint/alignment pass; instrumentation must execute successfully; device checks and reproducible instructions are documented. Android run `39603413359` passed build/lint/alignment and executed emulator instrumentation. Galaxy F23 basic rendering is now user-confirmed; recovery, lifecycle and broader failure-mode gates remain open.

## Phase 1 — End-to-end vertical slice (partial; acceptance gate not met)

- [x] SAF media picker, metadata extraction, visible platform preview/playback surface, seek controls, JNI media-reference insertion, and user-visible split/trim/delete/history controls.
- [x] Per-track timeline blocks, selected clip, playhead, background project save/load, autosave/recovery, and initial playback across directly adjacent same-track clips.
- [x] Galaxy F23 user smoke test reports visible video and working editing controls. The later basic-render phone test also confirms edited export playback (see below), but does not establish measured A/V sync, broad 4K/codec support or save/reopen correctness.
- [x] SAF project-document source compiles in Android build and its bounded-copy instrumentation source compiles; [ ] execute test on emulator and real Documents-provider save/reopen/revocation tests.
- [ ] One timeline-driven preview and export graph, multitrack A/V scheduling, gap handling and sync validation.
- [x] Replace remux-only UI export with decode/render/re-encode for arbitrary source trims and sequential mixed sources; emulator codec cases passed and Galaxy F23 user confirms 5–6 imported videos, split/edit, confirmed gap removal and playable 720p export.
- [ ] Verify mid-render cancellation, low-storage/provider cleanup and measured audio continuity on a physical phone.
- [x] Basic single-track decoder/render/re-encoder export for arbitrary frame cuts and sequential multiple sources.
- [ ] Render black gaps without compaction, shared preview/export graph, multitrack composition and cross-track audio scheduling/mixing; measured A/V sync validation.
- [ ] Android build/device acceptance for same-track timeline drag/reorder and media relink, including the new group-relink command; cross-track movement, robust recovery and lifecycle/surface-recreation test coverage.

Acceptance: meet Android/device criteria in `PRODUCT_SPEC.md`, including same timeline for preview/export, persistence, A/V sync, error/cancel and lifecycle tests. Basic single-track export is implemented and user-tested; the broader Phase 1 acceptance gate is not yet complete.

## Phase 2 — Usable MVP

Multiple clips/tracks, thumbnails/waveforms, multi-track-aware ripple semantics, basic transforms/text/transitions/audio mixing, autosave and robust cancellable export. Require deterministic command round trips, serialization/migration/recovery tests, render comparisons and device profiling before gate.

## Phase 3 — Advanced editing

Advanced trims, keyframes/curves, speed control, proxies and measured cache/playback optimization. Gate on edit invariant tests and performance/memory budgets on the device matrix.

## Phase 4 — Color, audio and effects

Explicit color management, scopes, curves/LUT, selected effects/masks, and advanced DSP. Gate each feature on test vectors, output tolerances and device capability validation; no HDR claim without complete pipeline evidence.

## Phase 5 — Advanced/AI

Selected tracking, stabilization, subtitles and AI only after consent, on-device privacy, model license/size/RAM/acceleration and offline behavior review. HDR/high-resolution remains capability-gated.

## Phase 6 — Release hardening

Device matrix, profiling, accessibility, privacy/store checklist, dependency/license review, release signing instructions and legal review where needed.

## Basic render checkpoint (2026-10-21, emulator verified)

Default export now routes through `TimelineRenderExporter` (Media3 1.8.1) instead of
stream-copy. The native timeline snapshot supplies clip order and source trim ranges;
Android decodes/renders/re-encodes H.264 + AAC, source video cadence, 720-high SDR, preserving aspect
ratio. A silent audio track is generated where necessary. Gap removal still needs
confirmation; overlaps/multitrack export remain rejected. Import/play/edit are existing
features; RAW and grading are explicitly deferred until basic acceptance is complete.

New real-media instrumentation covers untouched input, non-keyframe trim, three clips
with different resolutions/aspects and a silent source, duration, output first/last
frames and pre-start cancellation. Synthetic fixture provenance is in androidTest/assets.
Run `39603413359` executed the instrumentation suite successfully, including the
new real-media render cases. Galaxy F23 playback/A/V sync, mid-render cancellation,
Gallery thumbnails and lifecycle still need physical-device verification. APK publication
is gated after emulator instrumentation and `phone-test-39603413359` is published; do not distribute the prior remux APK as this
render checkpoint. Earlier remux-only descriptions above are historical, not the new
selected UI path.

## User-confirmed basic render acceptance (2026-10-21)

User installed the latest supplied basic-render APK and reports importing about 5–6
videos, splitting/editing them, removing empty timeline ranges with the render flow,
and successfully playing the rendered 720p video. This closes the basic sequential
import/play/edit/render smoke test on the previously identified Galaxy F23 only.
No source codec/resolution inventory, precise A/V sync or performance measurement,
mid-render cancellation, save/reopen, lifecycle or low-storage check was supplied.
Advanced tools, grading and RAW are not complete; the next implementation scope must
be an explicitly bounded milestone rather than declaring all phases complete.

## Basic reliability milestone (emulator regression verified)

Next scope selected by the user: basic editor hardening, not grading/RAW or MVP tools.
New source guards grouped-source relink durations, blocks edits during project I/O,
retains editor history/selection on export-time recreation while cancelling the old
render, preserves paused preview position and clamps seeks to the selected clip.
Gallery publication now uses a testable pending-row transaction with copy/close/size/
publish checks and explicit cleanup-failure reporting. Output duration and first/last
frame decoding are checked before publication. Android run `39620910796` executed the expanded JNI, real-codec, Activity
recreation, MediaStore and injected storage-failure suite successfully after the
initial regression failures were fixed. Build/lint and 16 KB alignment also passed.
Published APK: `phone-test-39620910796` from `4fc4590` (6,809,848 bytes).

Phone checks still needed: exact A/V sync, permission revocation/provider save/reopen,
low storage, Samsung lifecycle/process death. SAVE/LOAD/RECOVER-time recreation still
uses explicit autosave recovery, not background operation handoff. No provider-atomic
replacement, write-ahead journal, full shared preview/export graph, multitrack, grading
or RAW claim is added. Existing user-confirmed 5–6-clip 720p rendering remains valid.

### Reliability checkpoint results

Media import now participates in C++ AddClipCommand Undo/Redo history, including
last-import undo clearing preview. MP4 validation iterates by sample-track index so
negative AAC pre-roll timestamps are not mistaken for EOF. Regression tests were
not weakened after the first failing run. New hardening has emulator evidence, not
a new Galaxy F23 report; that phone's successful 5–6-clip render report concerns the
previous basic-render APK. Project schema is unchanged and reopen resets transient
history, as before. Save important work as a project document before changing debug
APK installations; do not delete app data to resolve installation errors without backup.

## Timeline interaction checkpoint — verified emulator batch (2026-10-22)

User reports the basic-hardening APK works correctly on their phone, with some
unspecified remaining issues. This requested batch adds an original red timeline
playhead, HH:MM:SS:FF ruler/time label, unified lane scrolling, tap-to-seek, marker-head
scrubbing, follow toggle/manual browse, proportional short clips/adaptive long scale,
source/sequence time separation and Undo anchor preservation. Native C++ owns point
lookup, exclusive clip boundaries and gap/overlap validation. Android preview now
requests closest-frame seeks and waits for initial seek completion before playback.
No project schema/output-preset/new dependency or advanced-tool/RAW claim is added.

See DECISIONS/0008 for research, clock/conversion/interaction details and test scope.
Host `make test` and `git diff --check` passed. Final code `8f5df51` passed
portable CI `39728248775` and Android CI `39728248796` (job `119657088154`):
APK build/lint, AndroidTest compilation, 16 KB alignment and the full Android 35
x86_64 emulator suite, including the new Canvas/touch/JNI/real-MediaPlayer seek,
playback advancement, split/Undo and non-selected clip move/source checks. Existing
render, publication, project-I/O and lifecycle tests also passed.
Phone-test prerelease: `phone-test-39728248796`, target
`8f5df51622fec4bd29542921875b330905c475b9`, APK 6,834,920 bytes.
This is emulator evidence, not a new physical-phone validation report.
Device checks still needed: red-marker smoothness/latency, fast repeated seeks, long
clip/zoomed timeline ergonomics, TalkBack/large fonts, source codecs and exact A/V sync.
The prior export gap-confirmation and single-track render limitations remain.


## Project-workspace / node architecture baseline (2026-10-22)

The user reports the playhead APK works well on their phone; this is user smoke
evidence, not an independent sync/performance benchmark. [Advanced architecture v1](ADVANCED_ARCHITECTURE.md)
and ADR 0009 now define the canonical forward design; earlier checkpoint descriptions
above are historical. The baseline distinguishes timeline editorial time, typed node
processing, contextual workspace UI, managed persistence and future shared evaluation.

This implementation adds a launcher project library (create/name/import/list/open),
per-project native save/recovery, non-destructive legacy workspace discovery, managed
checkpoint on Projects/Back, retained workspace pages and Edit/Media/Deliver panels.
Flow is a read-only explanation of the current separate preview/export routes. Native
`engine/graph` validates a bounded typed output DAG but has no renderer, JNI or file
schema integration. No advanced grading/audio/RAW/compositor or output-preset claim.

Host tests and Android verification for this batch are recorded in the final evidence
entry below when executed. Physical-phone verification of this new navigation is pending.
Test on phone: create two projects, import/edit/back/reopen isolation, external Save As,
legacy recovery, large fonts/rotation, media relink, render/Gallery, and Back during I/O.


### Executed architecture-batch evidence

Final implementation `f652c381316d209eb6be2bb5b736c89ede802e12` passed Android
run `39748116540` / job `119721585167`: APK build/lint, AndroidTest compilation,
16 KB alignment, KVM and full Android 35 x86_64 instrumentation. New catalog tests
cover isolated documents, names/path validation, non-destructive/idempotent legacy
migration, malformed/oversized metadata with valid cards preserved, launcher routing,
managed checkpoint/reopen and retained workspace page without replacing the viewer.
Existing playhead/JNI/lifecycle/project-I/O/render/publication regressions also passed.
Portable CMake/build/CTest run `39748116580` passed. Local `make test` and a strict-warning
ASan/UBSan execution of the unchanged native engine sources passed. Local CMake was
not installed; do not confuse CI execution with a local run.

Verified published prerelease `phone-test-39748116540` targets the tested source;
APK `app-debug.apk` is 6,867,764 bytes and checksum asset is uploaded. Actions' optional
report artifact upload still exceeded storage quota; step outcomes/release metadata
were checked, but downloadable XML/lint reports were not independently inspected.
This is emulator/host evidence; physical-phone validation of the new library/navigation
is pending. The user's latest successful phone report refers to the earlier playhead
APK, not this architecture batch. Later documentation-only changes do not alter APK code.


## Studio UI / Bengali-English checkpoint (2026-10-22)

User reports the project-workspace APK works well on their phone. This batch adds
an original slate/blue studio theme, card-based library, icon-only tool/navigation
actions with localized tooltips/accessibility, timeline lane/grid/clip polish, and
first-run English/Bengali selection persisted independently of project data. Home
Settings can change language. 256 default/Bengali resource pairs cover app-authored
menus, dialogs, statuses, export/recovery/errors; user names/media paths and native
diagnostics remain untouched. ADR 0010 records scope and platform references.

No new dependency, project schema, editing semantics or render preset. Current Flow
is still read-only. Host `make test`, resource/format/reference/contrast checks and
`git diff --check` passed; Android execution passed as recorded below.
This new visual design has not been visually inspected on a physical phone. Large
fonts, icon discovery, TalkBack, portrait/landscape and both-language provider flows
need device validation; do not claim host tests prove those.


### Studio UI batch — executed evidence

Final UI source `1e701d4b08bfcc71811fe54865101ce64db8098c`: Android run
`39764813788`, job `119777525804`, passed resource gate, APK build/lint,
AndroidTest compilation, alignment and full Android 35 x86_64 emulator regression.
New tests passed: mandatory first-run choice, Bengali context after recreation,
real Settings dialog selection back to English, preference retention, both-language
editor icon semantics/tooltips/minimum targets, icon bitmap unchanged by semantic
label length, and page switching without native timeline mutation. Existing project,
playhead, JNI, lifecycle, real-media render and publication regressions passed too.
Portable run `39764813603` passed resource gate, CMake configure/build and CTest.
Local `make test`, Python resource/reference/placeholder/contrast gate (256 pairs)
and diff check passed. No physical-phone visual/TalkBack/ergonomic validation yet.

Prerelease `phone-test-39764813788` targets that exact source; APK 7,018,288 bytes
and SHA-256 asset were verified as uploaded via release metadata. Optional Actions
report upload still exceeded storage quota: no independently downloaded XML/lint
report or full-screen screenshot review is claimed. Explicit CI step outcomes were
checked. Documentation-only follow-up does not change the tested APK implementation.
