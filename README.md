# Owncut

Owncut is an original, offline-first Android video-editing project. Its product direction takes inspiration from professional post-production workflows without copying another product's source, branding, assets, algorithms, or exact UI.

## Current checkpoint

The Android shell imports media through SAF, displays and plays a video preview, and exposes a timeline with split/trim/delete/ripple/undo/redo and newly authored same-track drag/reorder (pending Android validation). The user has now confirmed on a Galaxy F23 (Android 14, 6 GB RAM, Snapdragon 750G) that preview is visible and the editing features work; the attached screenshot shows a 3840×1632 source and timeline. This is a user-reported smoke test, not independent codec, A/V-sync, or export validation.

This checkpoint also adds project Save As/Open through the Android Storage Access Framework. A C++20 engine owns rational-time timeline/history and versioned project serialization. Android build/lint and APK alignment passed for the latest release, while emulator instrumentation failed; see [progress](docs/PROGRESS.md) and [build/test](docs/BUILD_AND_TEST.md) for exact limitations.

Owncut remains an early editing shell, not a complete professional editor. Preview still uses platform MediaPlayer clip handoffs rather than one timeline-driven composition graph. A restricted keyframe-aligned MP4 stream-copy exporter is in the current debug APK. User testing exposed frame-duration rounding rejection, empty gaps, and multi-source projects. The follow-up source work accepts half-frame rounding, explicitly compacts gaps after confirmation, joins multiple sources only when their codec/configuration/track formats match, and validates the muxed MP4 by reopening it and decoding its first frame before SAF copy. This new work is not yet Android-built or device-tested; current APK `phone-test-39270709637` predates it. General frame rendering/re-encoding, black-frame gap rendering, robust multi-track scheduling, write-ahead crash journaling, and production audio/color/effects are not implemented. A selected-clip media relink action is in the newest debug APK but awaits phone validation. Linux/Android local project replacements now call fsync; this is not a journal or power-loss guarantee. Project file Save As/Open does not rewrite original media.

## Build and test

Host core: a C++20 compiler (GCC or Clang); fast check: `make test`. CMake 3.20+ is supported. Android setup and commands use the pinned Gradle wrapper; see [Build and test](docs/BUILD_AND_TEST.md).

Read [the product specification](docs/PRODUCT_SPEC.md), [architecture](docs/ARCHITECTURE.md), [roadmap](docs/ROADMAP.md), [project format](docs/PROJECT_FORMAT.md), and [progress](docs/PROGRESS.md) before treating planned work as implemented.

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

## Phone-confirmed basic render checkpoint (2026-10-21)

User reports successful import of about 5–6 videos, split/edit, removal of empty
timeline ranges, rendering and playback of the 720p result using the latest supplied
APK. This is a successful physical-phone user smoke test, not an independent sync
or performance measurement. Roadmap hardening, advanced tools, grading and RAW
remain incomplete. No new feature APK is produced by this documentation checkpoint.

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

## Timeline interaction checkpoint (Android verification pending)

User reports the basic-hardening APK works correctly on their phone, with some
unspecified remaining issues. Next requested batch adds an original red timeline
playhead, HH:MM:SS:FF ruler/time label, unified lane scrolling, tap-to-seek, marker-head
scrubbing, follow toggle/manual browse, proportional short clips/adaptive long scale,
source/sequence time separation and Undo anchor preservation. Native C++ owns point
lookup, exclusive clip boundaries and gap/overlap validation. Android preview now
requests closest-frame seeks and waits for initial seek completion before playback.
No project schema/output-preset/new dependency or advanced-tool/RAW claim is added.

See DECISIONS/0008 for research, clock/conversion/interaction details and test scope.
New host tests pass with `make test`; `git diff --check` passes. Android build/lint and
new UI/real-player tests are pending; no new APK is offered until those tests pass.
Device checks still needed: red-marker smoothness/latency, fast repeated seeks, long
clip/zoomed timeline ergonomics, TalkBack/large fonts, source codecs and exact A/V sync.
The prior export gap-confirmation and single-track render limitations remain.
