# Device test matrix

The Galaxy F23 row below records a user-reported manual smoke test. Other rows remain planned targets. Emulator instrumentation passed in run `39603413359`; physical-phone reports are user observations, not independent measurements.

| Device / class | Coverage | Result / limits |
|---|---|---|
| Samsung Galaxy F23; Android 14; 6 GB RAM; Snapdragon 750G (user-reported) | User tried the app after the TextureView fix. Screenshot shows visible video from a 3840×1632, 9-second source and timeline UI; user says playback and features work. | **User smoke test reported successful** (2026-10-19). No independently captured codec details, A/V sync measurement, save/reopen, SAF project Save As/Open, export, lifecycle, thermal or frame-drop measurements. Do not infer universal 4K/codec support. |
| Low-memory phone | Lowest supported Android API; 4 GB RAM class | Planned: import, seek, surface recreation, storage exhaustion, background/foreground, thermal behavior |
| Mid-range phone | Current widely deployed API; 6–8 GB RAM | Planned: 1080p SDR decode/preview, A/V sync, trim/split, save/reopen, H.264/AAC export |
| High-end phone | Current API; modern GPU/codec | Planned: 1080p performance, dropped frames, export throughput, cancellation and thermal throttling |
| Tablet | Current API; large/adaptive display | Planned: landscape/adaptive UI, touch target/accessibility, surface and lifecycle behavior |

For each real device record manufacturer/model, SoC/GPU, Android build/API, ABI, codec capabilities, input sample provenance, project settings, measured frame time/dropped frames/RAM/temperature/export speed, and pass/fail artifacts. Test only advertised capability; do not infer universal 4K, 60 fps or HDR support. Add 16 KB page-size packaging/device coverage before native release.

## Initial phone smoke-test checklist

For new code builds, install the APK from the private `phone-test-*` prerelease (arm64-v8a and x86_64; verify the accompanying SHA-256 file). This is a manual debug build, not release acceptance.

1. **Completed by user (Galaxy F23):** user reports video playback and editing features work after the TextureView crash fix; supplied screenshot shows a visible frame from 3840×1632 media and timeline. This does not independently measure A/V sync, decoder support or playback performance.
2. Remaining playback validation: test a local 1080p SDR sample, pause/seek, adjacent clips and gaps; record audio sync and behavior after surface recreation. Avoid assuming universal 4K/HDR/codec support.
3. Test project Save As/Open from the Android Documents provider (included in `phone-test-39251452651`), plus app-private Save/Open saved and Recover autosave. Verify malformed or inaccessible project documents do not corrupt the current session.
4. For a moved or revoked source, select the affected timeline clip and use **মিডিয়া বদল**. Confirm a replacement with sufficient duration restores preview while retaining the source range; confirm a too-short replacement is rejected. After splitting one source into multiple clips, relink one segment and confirm all segments sharing that source update together; test undo/redo for the whole group.
5. Test rotation, background/foreground, low storage, revoked media permission, unsupported/corrupt media and lifecycle cleanup.
6. APK `phone-test-39270709637` includes the duration-rounding and explicit gap-compaction fix, but it still restricts export to one original source. Follow-up source now accepts multiple original URIs only when video/audio codec configurations and formats match, and probes the output MP4 plus decodes its first frame before copying. After a new APK is built, test one 6-second clip, three clips from the same source, and two or three compatible sources; verify Gallery thumbnail/playback, first/last frames, audio continuity, cancellation and failure cleanup. Also test mismatched resolution/codec rejection. Ask the user to attach the exported MP4 if Gallery still cannot create a thumbnail. Arbitrary-frame cuts, overlap, effects and re-encoding remain unsupported; this is not general export acceptance.

### Gallery publication follow-up

Android 10+ export saves validated MP4s automatically to `Movies/Owncut` using a pending MediaStore row; no document picker is shown. Android 8–9 retain the SAF picker. Failed/cancelled copies attempt cleanup; provider deletion may fail. Existing empty files are not automatically removed. Device checks must cover successful Gallery thumbnail/playback, cancellation, unsupported-keyframe/format rejection without a new Gallery item, and storage/write failure cleanup. This does not add re-encoding or `.ccraw` decoding; the custom RAW format specification and sample footage are still required. Android/device verification of this publication change is pending.

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

## Follow-up phone report — 2026-10-21

The user reports that the latest supplied basic-render APK works well: imported
approximately 5–6 videos, split/edited them, removed empty ranges, rendered and
played the 720p result. Record as successful user-reported sequential multi-source
render smoke test on the previously identified Galaxy F23, not measured A/V sync,
all-codec support, cancellation, persistence, thermal or lifecycle acceptance.

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
`git diff --check` passed; Android build/lint/new UI/full regression execution pending.
This new visual design has not been visually inspected on a physical phone. Large
fonts, icon discovery, TalkBack, portrait/landscape and both-language provider flows
need device validation; do not claim host tests prove those.
