# Architecture (current and intended)

## Current repository

- `engine/core`: portable C++20 time primitives.
- `engine/timeline`: non-destructive track/clip value model and validated clip moves.
- `engine/commands`: move-clip command and undo/redo history.
- `engine/project`: bounded versioned binary codec (v0 migration to v1) and best-effort atomic local-path save.
- `tests/unit`: portable host unit tests.
- `app/`: Kotlin Activity with SAF video picker and project-document Save As/Open adapter, persisted media URI grant attempt, background metadata read, aspect-ratio MediaPlayer preview/playback, clip timeline/edit-history controls, background project I/O, and debounced autosave/explicit recovery. User-reported Galaxy F23 smoke test confirms visible playback and editing controls; export/sync and SAF provider validation remain incomplete. This is an early UI shell, not the processed timeline preview.
- `platform/android/jni`: synchronized, coarse-grained JNI calls for session lifetime, media-reference clip insertion, batched JSON timeline snapshots, move/relink/split/trim/delete/ripple, undo/redo, project save/load, autosave, and status summary. Project file operations are invoked on the Activity's single-thread scheduled I/O executor.
- Android external-native build packages the portable engine in `libowncut_android.so`. Preview uses separate Android MediaPlayer instances attached to a TextureView Surface, bounds each segment to that clip's source range, and auto-advances only to an exactly adjacent clip on the same track; a timeline gap stops playback. The Activity shows clips as position/width-scaled blocks in horizontally scrollable per-track rows with a shared seek bar. The current working tree adds long-press same-track drag/reorder, routed through the undoable C++ move command; the new gesture is not Android-built or device-tested yet. Cross-track drag is not implemented. This is not a compositor or shared render/export graph and may have decode handoff gaps. The Android `MediaExtractor`/`MediaMuxer` stream-copy exporter in APK `phone-test-39270709637` handles keyframe-starting clips and explicitly compacts gaps after user confirmation. Unbuilt follow-up source also permits multiple source URIs when codec configuration and track formats match, then reopens the muxed file and decodes a first frame before SAF copy. It still cannot render black gaps, re-encode mismatched formats, apply effects or mix tracks; no real-media output has been device-verified. No independent audio engine/mixer or effect renderer exists. The Galaxy F23 user previously reported black video under SurfaceView and now confirms visible video and working editing UI after the TextureView migration. This is a user-reported phone smoke test only; multi-device codec, A/V-sync and export validation remain outstanding.

## Intended boundaries

Android app shell (Kotlin + Compose/Surface integration) owns lifecycle, permissions, picker, and user interaction. A small batched JNI boundary maps immutable requests/snapshots to a C++ engine. C++ owns timeline, commands/history, scheduling, render/effect graph, DSP, project logic, and platform-independent export orchestration. Android adapters own codec, surface, storage URI, and audio-device APIs. UI never mutates engine state directly; use commands and snapshots/events. Keep decode/render/file I/O off the UI thread. Audio callbacks must be bounded and non-blocking.

## Time and edits

`Timestamp` is integer ticks plus a positive, normalized `TimeBase` (seconds per tick). Video frame rate is represented by the frame duration rational (for example 1001/30000 seconds); audio sample time uses a sample-period base. Source and timeline timestamps remain separate fields. Rescaling rounds to nearest tick, with exact half-ticks away from zero. The current implementation uses a signed 128-bit intermediate and limits timebase terms to 1,000,000,000 to keep intermediate products bounded; it is intended for GCC/Clang toolchains. Variable-frame-rate indexing, frame-boundary policy, audio clock drift, and sync scheduling remain to be designed and tested before playback work.

A clip references a media ID and source in/out range; it does not alter source media. Current track model allows overlapping clips and does not yet enforce audio/video linking or global ID persistence. Unique IDs are validated within one timeline instance. Undoable commands currently cover move, media relink, split, left/right trim, delete, and single-track ripple delete. The in-memory history is bounded to 256 commands by default (configurable per history instance); this bounds command count, not byte use for a ripple operation touching a very long track. Ripple delete shifts clips on the selected track whose starts are at or after the removed clip end; overlapping clips and other tracks stay fixed. Move and relink are surfaced through JNI; APK `phone-test-39250035336` compiled the same-track drag and selected-clip relink source. Physical-device verification remains pending. This first UI is not a scalable multitrack editor. Activity-side debounced autosave and explicit snapshot recovery use the local filesystem. The native store remains path-based; Kotlin now stages SAF project documents through temporary cache files and adds an undoable selected-clip media relink flow. Provider-side atomic replacement and write-ahead recovery journal remain absent; local Linux/Android writes now fsync data+directory. The initial selected-clip relink is included in a debug APK but not yet device-tested; the current working tree adds an atomic group update for all clips sharing that URI, pending Android build. Project schema behavior and limitations are described in `PROJECT_FORMAT.md`.

## Ownership and concurrency policy

Use RAII and explicit ownership. Future engine session should have a serialized command owner, immutable snapshots for UI reads, bounded queues for realtime handoff, cancellation tokens for stale seeks, and explicit shutdown before Android surface/audio adapter destruction. These are design constraints, not implemented behavior.

## Graphics/media feasibility

No backend is selected. Before selection, measure Android MediaCodec-to-surface interoperability and representative 1080p SDR preview on target devices; compare OpenGL ES and Vulkan support, lifecycle behavior, synchronization, memory copies, and device matrix coverage. Reuse one processing graph for preview/export where feasible. Resolve Oboe/AAudio, MediaCodec/NDK API, and any FFmpeg dependency choice only after official docs, supported API levels, maintenance, ABI, license, and packaging review. See ADRs 0001 and 0002. Android versions are pinned in Gradle files and documented in ADR 0002; toolchain/API compatibility was checked against official release documentation.

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
