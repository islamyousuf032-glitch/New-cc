# Progress

## Latest basic reliability checkpoint — 2026-10-21

- Code `4fc4590`: Android run **39620910796** passed build/lint, AndroidTest
  compilation, 16 KB alignment and actual expanded emulator instrumentation steps.
  APK release publication followed those successful tests. Portable CI
  **39620910793 passed**; local `make test` and `git diff --check` passed.
- New regression coverage passed: grouped relink longest-range rejection, atomic group
  undo/redo, move/history, project reopen and invalid-load preservation; idle/export
  Activity recreation, busy edit/history lock and last-import undo; active render
  cancellation/retry, missing media/invalid output, duration mismatch and visible
  first/middle/last fixture frames; storage write/close/size/publish failure cleanup,
  cleanup refusal reporting, cancelled copy, and exact real MediaStore publication;
  zero-read and write/flush project-stream behavior.
- First expanded run `39619055951` failed two real cases. Fixed native import history
  with C++ AddClipCommand and validator EOS detection for negative AAC pre-roll, then
  reran the unchanged regression cases successfully. No failed-run APK was published.
- New APK: https://github.com/islamyousuf032-glitch/New-cc/releases/tag/phone-test-39620910796
  (`app-debug.apk`, 6,809,848 bytes; accompanying SHA-256 asset). No local APK checksum
  claim: sandbox release-asset host restrictions remain. Actions artifact storage
  quota still prevents report upload; release assets are available separately.
- User selected **basic hardening**, not the full advanced roadmap. This checkpoint
  improves the selected basic path and its automated regression evidence; it does
  not close every manual/device acceptance gate. Galaxy F23 has user-confirmed
  multi-source split/edit/gap-compacted 720p render on the previous APK, not yet on
  this new one. Exact A/V sync, permission revocation/provider save/reopen, low-storage
  and process-death checks remain manual. SAVE/LOAD/RECOVER-time recreation still uses
  explicit autosave recovery; export-time recreation retains the project and cancels
  the old job. No full asynchronous operation handoff or foreground render service.
- No new dependency, project schema, output-resolution preset, advanced tools,
  multitrack compositor, grading or RAW support is added in this hardening checkpoint.


## Selected next milestone: basic reliability (implementation, verification pending)

User chose basic hardening rather than MVP/advanced tooling. Prior phone report now
confirms 5–6 imported videos, split/edit, accepted gap compaction and playable 720p
render; this is not a measured A/V-sync/performance result.

New source changes:
- Validate replacement media duration against **every** clip sharing the old source
  before the native atomic relink command; preserve source ranges/history on rejection.
- Block split/trim/delete/history/save/load/recovery while project I/O is busy; guard
  delayed picker metadata and repeated confirmation callbacks. Undoing the last import
  clears the stale preview. Stale autosave success messages do not overwrite render UI.
- Retain the native session/history during export-time configuration changes, cancel
  the old render instead of handing off its job, preserve paused preview position/range
  across recreation and clamp seeks to the selected clip. This is not a foreground
  render service or automatic continuation after process death.
- Separate Gallery pending-row transaction from Activity. Flush/close, exact copy size,
  provider size (when available) and commit are checked; copy/close/cancel/commit failure
  discards only the newly created row. Refused cleanup is explicitly reported.
- Before publication, verify rendered duration against source ranges (250 ms or two
  observed video-sample intervals tolerance) and decode first/last frames.
- Project stream copy handles zero-length reads without hanging and propagates write/
  flush errors. SAF project-provider replacement remains non-atomic.

Added regression sources: group relink/reopen/corrupt-load preservation/history,
active render cancellation/retry/missing media/invalid output path/duration mismatch,
visible fixture content, Gallery transaction fault injection and real MediaStore copy,
Activity recreation/busy-edit lock/last-import undo, project zero-read/write/flush cases.
Local `make test` and `git diff --check` pass; Android compilation/execution pending.
Remaining manual acceptance: real Documents-provider revocation/save/reopen, precise
A/V sync, low storage, Samsung decoder and background/process-death behavior. Rotation
during project save/load/recovery still uses the existing explicit autosave recovery
path, not a retained asynchronous project-operation controller. No new APK yet.


## Latest verified basic render checkpoint — 2026-10-21

- Code `4837c7f`: Android CI **39603413359 passed**, including actual emulator
  instrumentation, build/lint, AndroidTest compilation and 16 KB zip alignment.
  Portable CI **39603413469 passed**; local `make test` and `git diff --check` passed.
- The new codec tests ran successfully: untouched media is re-encoded, 500 ms
  non-keyframe trim works, three clips with mixed dimensions/aspects and missing
  audio export, H.264/AAC tracks and duration are verified, first/last frames decode,
  and pre-start cancellation leaves no output. Existing JNI/UI/storage/planner
  instrumentation also completed successfully. This is not a physical-phone test.
- Published debug APK: https://github.com/islamyousuf032-glitch/New-cc/releases/tag/phone-test-39603413359
  (`app-debug.apk`, 6,786,632 bytes). Publication happened only after instrumentation.
- Galaxy F23 must still check real source codecs, playback/audio synchronization,
  Gallery visibility/thumbnail, mid-render cancellation, background/rotation behavior,
  low storage and provider failure cleanup. Import/play/edit were previously confirmed
  on that phone; the new renderer has not yet been phone-tested.
- Scope: one non-overlapping video track; H.264/AAC, 720-high SDR with source cadence.
  Gap compaction requires confirmation. No grading, advanced effects, multitrack
  compositor or RAW support is claimed; these follow basic phone acceptance.
- Older pending/failed run notes below are chronological history, superseded by this
  result where applicable. Artifact storage quota still prevents Actions artifact
  upload; the separate release APK asset exists. Local release-asset download was
  blocked by sandbox host restrictions, so no local APK checksum verification is claimed.


## Implemented

- Portable C++20 rational-time timeline, non-destructive edit commands/history, versioned project serialization/store, v0 migration reader, and host tests. Local project replacement now fsyncs the temporary file and parent directory on Linux/Android; no write-ahead journal or power-loss fault test exists.
- Android shell with SAF video import, metadata extraction, MediaPlayer/TextureView preview, and play/pause/seek. The user confirmed the preview and editing UI work on a Galaxy F23 (Android 14, 6 GB RAM, Snapdragon 750G); screenshot shows a visible frame from a 3840×1632, 9-second source and timeline clips. This confirms that phone/sample smoke test only; it does not establish A/V sync, all-device codec support, or export.
- Timeline UI with clip selection, split, left/right trim, delete, ripple delete, undo/redo and playback over directly adjacent same-track clips. Same-track long-press drag/reorder is in the latest Android-built source and backed by the undoable C++ move command; it still needs phone interaction testing. Timeline gaps stop the preliminary preview; preview is not a compositor.
- Added a guarded lossless MP4 stream-copy exporter for non-overlapping clips with keyframe-aligned starts, monotonic timestamps, cancellation, and at most one video/audio track. The user’s newer screenshots show the Gallery could not create a thumbnail and the current APK rejected clips from multiple originals. Follow-up source now joins multiple URIs only when codec configuration and track formats are compatible, checks timestamps, reopens the output, and decodes its first frame before SAF copy. These changes passed Android build/lint and AndroidTest source compilation in run `39420456732`; device-media verification remains pending; this remains stream-copy, not general rendering/compositing.
- JNI session ownership, timeline snapshot bridge, background app-private project save/load, debounced autosave and explicit recovery UI.
- Added SAF project Save As/Open actions. The adapter serializes to/from a temporary app-cache file because the native project codec accepts filesystem paths, then streams bytes through `ContentResolver`; it bounds transfers to 96 MiB and has AndroidTest source for bounded-copy behavior. This is not an atomic provider-side replacement and does not persist document grants; users can choose the project document again when reopening.
- Added selected-clip media relink using SAF, persisted URI permission, duration validation and an undoable C++/JNI edit; the initial feature is in APK `phone-test-39250035336` and preserves the clip source range. New unbuilt core changes make relinking every timeline clip sharing the old media reference one atomic undoable operation; host tests pass, Android build and Galaxy F23/provider validation are pending.
- Removed the unsupported `TextureView.setBackgroundColor()` startup crash and improved startup-error contrast.

## In progress / not complete

- Latest published APK is private prerelease `phone-test-39420456732` from commit `2fc8883`. Android APK build/lint, AndroidTest source compilation, 16 KB zip alignment and publication passed. It includes compatible multi-source remux and output first-frame validation. Emulator instrumentation is still running; Galaxy F23 export validation is pending.
- Galaxy F23 screenshots from the latest APK show a Gallery item with no thumbnail and the export error for different original sources. That screenshot alone does not prove the MP4 is corrupt or unplayable. Follow-up source now handles multiple sources only when codec MIME/configuration, dimensions, profile/level, color metadata, rotation and audio layout match; it reopens the muxed file, checks sample timestamps, and decodes a first frame before copying. Planner/format compatibility tests were added and Android-compiled, but execution is not yet confirmed; no exported MP4 was attached for direct analysis.
- The limited remux exporter does not satisfy the general render/export gate: no decoder/compositor/re-encoder graph, arbitrary-frame cuts, mismatched-source normalization, black-frame gap rendering, cross-track A/V scheduling/mixing, effects, transitions, or color pipeline exists. Linux/Android local saves use file+directory fsync, but power-loss behavior is not fault-injected and SAF provider writes are not atomic.
- Multi-clip playback has decoder handoffs and only advances across exactly adjacent clips on the same track; no A/V sync or gap-free playback guarantee exists.

## Environment and verification limits

- Host tests do not verify Android UI, SAF provider behavior, Samsung codec/GPU behavior, media synchronization, lifecycle, or output rendering.
- User-reported Galaxy F23 results are recorded separately from CI/emulator results. No independent physical-device measurement or export check has been performed.
- Android emulator instrumentation has not passed in this workspace yet: earlier runs skipped because KVM was unavailable, and run `39262430899` failed while booting the emulator; run `39270709637` also failed emulator boot; current run `39420456732` is in progress. Do not treat AndroidTest source compilation as an executed smoke test.

## Tests actually run

- Portable C++ core tests, sanitizer build/run, strict-warning build, JNI syntax-only check, and `git diff --check` passed in earlier checkpoints.
- Latest portable-core CI run `39251452587` passed.
- Android run `39251452651`: build/lint, AndroidTest source compilation, APK zip alignment, and private prerelease publication passed; emulator JNI instrumentation was skipped because KVM was unavailable.
- SAF project-document source was Android-built in runs `39247694047` and later. Drag/reorder, selected/group relink and Linux/Android fsync were built in `39251452651`; provider and phone behavior remain untested.
- Android run `39270709637` for `7c06312`: APK build/lint, AndroidTest Kotlin source compilation (including new frame-rounding/gap-compaction cases), 16 KB zip alignment and private prerelease publication passed. Emulator JNI instrumentation failed on boot timeout; no codec sample export has run. Portable `make test` and `git diff --check` pass. GitHub Actions artifact storage quota is full, so workflow artifacts may not upload; the APK release asset was published separately.

- Android run `39420456732` for `2fc8883`: APK build/lint, AndroidTest Kotlin compilation, 16 KB alignment and phone-test release publication passed. Emulator instrumentation is still running. Portable CI `39420456739` passed; local `make test` and `git diff --check` passed. No physical-device export verification has run.

## Next steps

1. Check emulator instrumentation run `39420456732`; prior boot timeout `39262430899` is an environment failure, not an exporter test result.
2. Install APK `phone-test-39420456732` on the Galaxy F23 and test one clip, three same-source clips, two/three compatible sources, incompatible-source rejection, and a gapped timeline with confirmation; verify Gallery playback, audio continuity, cancellation and failure cleanup.
3. Build the general decoder/compositor/re-encoder graph for arbitrary frame cuts, multiple media sources, gaps and cross-track audio. The remux subset does not satisfy this acceptance gate.
4. Continue multitrack scheduling, provider-safe atomic replacement, power-loss fault testing, and lifecycle/device validation.

## Follow-up: zero-byte exports and automatic Gallery publication

User screenshots show multiple 0 B SAF documents. The document picker creates these before remux validation, so their presence does not establish that muxing succeeded. Android 10+ now bypasses the export document picker: validate the app-cache MP4 first, then insert a hidden `IS_PENDING` MediaStore video under Movies/Owncut, copy/close it, and publish. Copy/cancel/update failure attempts row deletion. Android 8–9 retain SAF with best-effort deletion on failure/cancellation; providers may refuse deletion. Existing old zero-byte files are not removed.

This is a publication fix, not a render pipeline: arbitrary split boundaries and incompatible sources remain unsupported by remux. User requests custom `.ccraw` camera footage support; a format specification, sample and/or writer implementation is needed before decoder design. No RAW support is claimed.

Verification: local `make test` and `git diff --check` passed; Android CI and phone publication tests pending for this change. Earlier Android run 39420456732 built/linted/compiled AndroidTests and published the multi-source APK; do not confuse that APK with this new publication change.

Publication fix verification update: initial Android run `39430675076` failed compilation because `android.annotation.RequiresApi` does not exist; replaced it with platform `TargetApi` (no added dependency). Corrected run `39431256128` for `7873b47` passed APK build/lint, AndroidTest source compilation, 16 KB zip alignment and publication of `phone-test-39431256128`. Emulator instrumentation is still running; Gallery publication, provider failures and cancellation have not been phone-tested. This APK still uses restricted remux and is not a solution for arbitrary split/re-encode or custom RAW.

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

Run `39595474496` (`509df3c`): Android build/lint, AndroidTest compilation and 16 KB
alignment passed; portable CI `39595474834` passed. Emulator boot timed out before
instrumentation ran, so no new render test is claimed as passed and no release APK
was published. Actions artifact quota is still full and run-log download is blocked
in this sandbox. Replaced opaque emulator action with SDK emulator boot plus captured
log/annotation diagnostics and bounded boot checks; subsequent run pending. Also
hardened render worker interruption cleanup and corrected docs: encoded video keeps
source cadence (`EditedMediaItem.setFrameRate` would only apply to images).

Diagnostic run `39598265360` (`b8a81e4`) passed build/lint, test compilation and APK
alignment, but the emulator exited before boot: `Unknown AVD name [owncut]`, with no
`owncut.ini` in the emulator's search directory. CI now explicitly aligns
ANDROID_USER_HOME / ANDROID_EMULATOR_HOME / ANDROID_AVD_HOME, creates the AVD at that
path and writes its descriptor. No render assertions executed and no APK published.

Run `39599071568` (`d5ae07b`) again passed Android build/lint/test compilation and
alignment, but this runner reported KVM unavailable and skipped instrumentation.
No release was published. CI now explicitly chmods an existing KVM device and fails
rather than reporting an apparently green acceptance run when KVM is absent. Added
JUnit XML-to-annotation reporting so real codec assertion failures remain visible
even when artifact storage is full. Basic rendering is implemented but not yet
runtime-verified; acceptance is still open.

Run `39599894456` (`be9479d`): build/lint, AndroidTest compile and alignment passed.
KVM/AVD-path checks passed, but rolling emulator 37.2.12 exited during startup before
any guest boot output or codec assertions. No APK published. Pinned official stable
36.4.9 with its official SHA-256, disabled unused Vulkan and added process exit-code
diagnostics; next CI run pending. This still does not constitute a passed render test.

Run `39600810217` (`3cbbad6`) built/linted/compiled AndroidTests and aligned the APK,
but even the pinned emulator exited with code 1 before guest boot. Codec assertions
still did not run; no release published. Next diagnostics add headless Qt mode,
explicit Linux runtime libraries, verbose/kernel output and the QEMU linker report.
The acceptance gate remains blocked; no completed-rendering/device claim is made.

Run `39601748480` (`ce4df59`) again failed before guest boot (emulator exit 1),
not on render assertions. GitHub's per-step warning annotation limit hid most
verbose emulator lines; diagnostics now emit one multiline annotation rather than
one per line. No APK is being offered while acceptance is unverified.

Root cause established in run `39602614968` (`3686119`): verbose emulator log ends
`Not enough space to create userdata partition. Available: 3977.91 MB ... need
12288.00 MB`. This is infrastructure, not a render assertion. CI now frees unrelated
preinstalled toolchains on the ephemeral runner and explicitly sizes the AVD userdata
partition to 2 GiB. No APK was published; real render assertions remain pending.

## Phone-confirmed basic render checkpoint (2026-10-21)

User reports successful import of about 5–6 videos, split/edit, removal of empty
timeline ranges, rendering and playback of the 720p result using the latest supplied
APK. This is a successful physical-phone user smoke test, not an independent sync
or performance measurement. Roadmap hardening, advanced tools, grading and RAW
remain incomplete. No new feature APK is produced by this documentation checkpoint.

Hardening CI `39619055951` (`e6dc029`): build/lint, test compilation and alignment
passed; emulator executed the expanded suite but two regressions failed, so no APK
was published. Last-import undo exposed that native import bypassed command history;
added C++ AddClipCommand and JNI routing, with host undo/redo/split and failed-command
history tests. Validator fixture exposed negative AAC pre-roll PTS being mistaken for
EOF; loops now use sampleTrackIndex for EOS. Existing new tests remain intact.

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

Timeline first full run `39727045607` (`aa57e0d`) passed Android build/lint,
AndroidTest compilation, 16 KB alignment and the expanded emulator suite, including
real preview seek/clock advancement, split/Undo anchor, JNI/native mapping and red
Canvas/ruler/scrub/accessibility actions. Portable CI `39727045626` passed. No phone
validation of the new playhead yet. The automatically published first checkpoint is
not being offered as the final batch while review fixes remain.

Final review source adds scrub/seek stale-tick guards, one pause request per scrub,
source-slider availability/reset, seek-after-end callback reuse, decoder-error seek
state recovery, delayed-surface metadata guards, visible/accessibility hover/focus
handling and reveal-on-keyboard/accessible seek. Moving a non-selected clip now loads
that clip's source instead of leaving the old preview paired with the new selection;
the real-player regression is extended to cover this. Final CI rerun passed as
recorded above; no further implementation changes after `8f5df51`.


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


Architecture batch `c1bc69b`: `make test`, strict-warning ASan/UBSan host execution,
and portable CMake/CTest run `39746688048` passed. Local CMake was unavailable (not
claimed run); the CI portable job did run configure/build/CTest. Android run
`39746688501` passed build/lint/alignment but correctly stopped publication on the
new malformed-catalog test: its bad-entry fixture was outside `files/projects`,
so it could not be discovered. Corrected the fixture path without removing the
assertion, expanded it to malformed JSON and oversized metadata, and retained
valid-card count and no-deletion assertions. The emulator annotations reported no
other test failure. Added create-time library-budget guard and launcher native-load
error reporting. Full rerun evidence follows below; no APK from the failed run is offered.


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


## Quiet editor / theme checkpoint (2026-10-22)

User phone screenshots show the studio-icon build works but still looks cluttered.
ADR 0011 records screenshot-driven hierarchy changes and official color-role research.
A fixed portrait editor prioritizes fitted viewer, transport, timeline and one focused
edit row; metadata/source seek, help and secondary/project tools are contextual.
Small-height/large-font windows retain a scroll fallback. Three persisted role-based
themes (Graphite, Midnight, Ember) share palette JSON and contrast tests. Home Settings
selects language or Appearance. Existing processing/render behavior is unchanged; Flow
is read-only. Quiet status retains important-failure/recovery notifications.

Resource/three-palette contrast checks and host tests are run below; Android layout/
theme/full regression and actual-phone aesthetic approval are separate gates. No new
dependency, proprietary assets, project migration, grade or node renderer added.
