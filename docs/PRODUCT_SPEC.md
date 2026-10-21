# Product specification

## Vision

A touch-first, offline-first, non-destructive Android video editor with a distinct identity and professional editing depth. Cloud accounts and AI services are never required for core editing. Original media remains untouched.

## Phase 0 acceptance criteria

- A portable C++20 core builds independently of Android.
- Time is represented using integer ticks and explicit rational timebases.
- Timeline model accepts multiple typed tracks and media-referencing clips, validating identifiers, source ranges, and locked tracks.
- Host unit tests cover rational frame-rate comparison/rescaling and core validation.
- Android debug APK builds for arm64-v8a and x86_64 with the pinned toolchain.
- Kotlin creates/destroys one native engine session and batches user-command/project calls; instrumentation covers edit/history/project/autosave JNI round trips.
- Build, test, architecture, licensing, and limitations are documented.

Portable requirements have host tests. Android APK build/lint and alignment pass in recent CI; instrumentation execution has failed and is not accepted as passing.

## Phase 1 acceptance criteria (partially implemented)

Implemented: SAF video import, metadata read, visible MediaPlayer preview through TextureView, play/pause/seek, timeline clip selection and split/trim/delete/ripple/undo/redo, app-private save/load/autosave/recovery, and SAF project Save As/Open adapter. The SAF adapter was Android-built/linted and its instrumentation source compiled in run `39247694047`; real Documents-provider behavior remains untested. The user reports the preview and editing controls work on Galaxy F23 / Android 14; screenshot shows a visible frame and timeline for a 3840×1632 source. This is one user-reported phone smoke test only.

Not yet satisfied: one general timeline/render graph must drive preview and export; correct black-frame gap/transition rendering; robust cross-track A/V scheduling and sync; cancellation/failure cleanup and lifecycle/surface tests; broad edited output export on a supported device. A narrow lossless MP4 remux exporter is in APK `phone-test-39270709637` for keyframe-aligned cuts, frame-duration rounding and explicitly confirmed gap omission/clip concatenation. User screenshots additionally show multi-source limits and Gallery thumbnail failure. New source allows multiple URIs only with identical relevant codec/track configuration and validates the muxed MP4 plus first-frame decode before copy; this is not yet Android-built or device-tested. This is not black-frame rendering or general export. Current preview uses separate MediaPlayer clips and stops at timeline gaps. APK `phone-test-39250035336` includes same-track long-press drag/reorder with free-slot snapping through JNI; the new gesture awaits Galaxy F23 validation. Cross-track drag is absent. Project documents are copied through app-cache temporary files and provider-side atomic replacement is not guaranteed; Linux/Android local files now sync data+directory, but the write-ahead journal and power-loss fault tests remain absent. APK `phone-test-39250035336` includes selected-clip relink with source-range validation and undo. The newer source changes it to update every clip sharing the original media reference as one atomic undoable edit; that group behavior needs Android build and Galaxy F23 validation.

Acceptance gate: on documented supported Android devices, import a locally accessible SDR video, visibly render frames, play/pause/seek with corresponding audio, edit through the timeline that drives preview and export, save/reopen and recover safely, then export the edited result through a supported codec. Validate A/V sync, cancellation, failure cleanup, lifecycle/surface recreation, and output correctness. Host tests, metadata import, and user-reported playback alone do not satisfy the export/sync/persistence gate.

## Product constraints

Initial target is representative 1080p SDR, not universal 4K/60/HDR support. Unsupported media should produce actionable errors. HDR is not claimed without end-to-end precision, display, tone mapping, encoder, and metadata validation. Phone/tablet adaptive editing, accessibility, storage recovery, privacy, and thermal/memory budgets are release requirements.

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

## Basic reliability milestone (verification pending)

Next scope selected by the user: basic editor hardening, not grading/RAW or MVP tools.
New source guards grouped-source relink durations, blocks edits during project I/O,
retains editor history/selection on export-time recreation while cancelling the old
render, preserves paused preview position and clamps seeks to the selected clip.
Gallery publication now uses a testable pending-row transaction with copy/close/size/
publish checks and explicit cleanup-failure reporting. Output duration and first/last
frame decoding are checked before publication. Additional JNI, real codec, Activity
recreation, MediaStore and injected storage-failure tests await Android CI execution.

Phone checks still needed: exact A/V sync, permission revocation/provider save/reopen,
low storage, Samsung lifecycle/process death. SAVE/LOAD/RECOVER-time recreation still
uses explicit autosave recovery, not background operation handoff. No provider-atomic
replacement, write-ahead journal, full shared preview/export graph, multitrack, grading
or RAW claim is added. Existing user-confirmed 5–6-clip 720p rendering remains valid.
