# Architecture (current and intended)

## Current repository

- `engine/core`: portable C++20 time primitives.
- `engine/timeline`: non-destructive track/clip value model and validated clip moves.
- `engine/commands`: move-clip command and undo/redo history.
- `engine/project`: bounded versioned binary codec (v0 migration to v1) and best-effort atomic local-path save.
- `tests/unit`: portable host unit tests.
- `app/`: Kotlin Activity with SAF video picker, persisted URI grant attempt, background metadata read, aspect-ratio MediaPlayer preview/playback, clip-list selection and edit/history controls, background project I/O, and debounced autosave/explicit recovery. This is an early UI shell, not the processed timeline preview.
- `platform/android/jni`: synchronized, coarse-grained JNI calls for session lifetime, media-reference clip insertion, batched JSON timeline snapshots, split/trim/delete/ripple, undo/redo, project save/load, autosave, and status summary. Project file operations are invoked on the Activity's single-thread scheduled I/O executor.
- Android external-native build packages the portable engine in `libowncut_android.so`. Preview uses the Android MediaPlayer platform pipeline and bounds playback to one selected clip's source range; it does not sequence the timeline. No shared render/export graph, independent audio engine/mixer, effect renderer, or export path is implemented. Android code is not yet built/device-verified.

## Intended boundaries

Android app shell (Kotlin + Compose/Surface integration) owns lifecycle, permissions, picker, and user interaction. A small batched JNI boundary maps immutable requests/snapshots to a C++ engine. C++ owns timeline, commands/history, scheduling, render/effect graph, DSP, project logic, and platform-independent export orchestration. Android adapters own codec, surface, storage URI, and audio-device APIs. UI never mutates engine state directly; use commands and snapshots/events. Keep decode/render/file I/O off the UI thread. Audio callbacks must be bounded and non-blocking.

## Time and edits

`Timestamp` is integer ticks plus a positive, normalized `TimeBase` (seconds per tick). Video frame rate is represented by the frame duration rational (for example 1001/30000 seconds); audio sample time uses a sample-period base. Source and timeline timestamps remain separate fields. Rescaling rounds to nearest tick, with exact half-ticks away from zero. The current implementation uses a signed 128-bit intermediate and limits timebase terms to 1,000,000,000 to keep intermediate products bounded; it is intended for GCC/Clang toolchains. Variable-frame-rate indexing, frame-boundary policy, audio clock drift, and sync scheduling remain to be designed and tested before playback work.

A clip references a media ID and source in/out range; it does not alter source media. Current track model allows overlapping clips and does not yet enforce audio/video linking or global ID persistence. Unique IDs are validated within one timeline instance. Undoable commands currently cover move, split, left/right trim, delete, and single-track ripple delete. The in-memory history is bounded to 256 commands by default (configurable per history instance); this bounds command count, not byte use for a ripple operation touching a very long track. Ripple delete shifts clips on the selected track whose starts are at or after the removed clip end; overlapping clips and other tracks stay fixed. These command semantics are surfaced by the current Android clip-list controls through JNI; this first UI is not a scalable multitrack editor and remains uncompiled/unverified. Activity-side debounced autosave and explicit snapshot recovery are authored, while the native store remains local-filesystem-only; SAF project storage, crash-recovery journal, and media relink flow are not implemented. Project schema behavior and limitations are described in `PROJECT_FORMAT.md`.

## Ownership and concurrency policy

Use RAII and explicit ownership. Future engine session should have a serialized command owner, immutable snapshots for UI reads, bounded queues for realtime handoff, cancellation tokens for stale seeks, and explicit shutdown before Android surface/audio adapter destruction. These are design constraints, not implemented behavior.

## Graphics/media feasibility

No backend is selected. Before selection, measure Android MediaCodec-to-surface interoperability and representative 1080p SDR preview on target devices; compare OpenGL ES and Vulkan support, lifecycle behavior, synchronization, memory copies, and device matrix coverage. Reuse one processing graph for preview/export where feasible. Resolve Oboe/AAudio, MediaCodec/NDK API, and any FFmpeg dependency choice only after official docs, supported API levels, maintenance, ABI, license, and packaging review. See ADRs 0001 and 0002. Android versions are pinned in Gradle files and documented in ADR 0002; toolchain/API compatibility was checked against official release documentation.
