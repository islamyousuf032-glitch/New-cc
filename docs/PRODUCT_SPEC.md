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

The Android acceptance bullets are implemented as code and CI configuration, but remain unverified until the remote Android workflow succeeds.

## Phase 1 acceptance criteria (partially implemented, not verified)

A Kotlin SAF picker, metadata read, MediaPlayer preview with embedded audio, play/pause/seek controls, native timeline snapshot/edit controls (split/trim/delete/ripple/undo/redo), background project save/load, explicit autosave recovery, and debounced autosave are implemented and have passed Android build/lint CI, but are not device-verified. The user supplied the startup exception from Galaxy F23 / Android 14: `TextureView doesn't support displaying a background drawable`, caused by `setBackgroundColor()` during workspace creation. Current source removes that call, improves startup-error screen contrast, and has an Activity launch smoke test; the fix is not yet built or phone-verified. The user also previously reported audio with black video on 3840×1632 and 1920×1080; the TextureView migration is still awaiting physical-device verification. The current project UI is an early visual timeline with horizontally scrollable per-track clip blocks and a shared seek/playhead bar; blocks can be selected but not dragged/reordered. Preview follows each selected clip's source range and auto-advances across exactly adjacent clips on the same track, stopping at timeline gaps. It does not composite tracks, render effects/transitions, guarantee gap-free real-time playback, or export. On a documented supported Android device: import one locally accessible SDR video; inspect metadata; confirm a decoded frame is visibly rendered; play/pause/seek with corresponding audio; perform a trim/split represented in the same timeline used for preview and export; save and reopen the project; export the edited result through a supported Android codec. Validate A/V sync, cancellation, failure cleanup, lifecycle/surface recreation, and output correctness. Host tests and successful metadata import alone do not satisfy these criteria.

## Product constraints

Initial target is representative 1080p SDR, not universal 4K/60/HDR support. Unsupported media should produce actionable errors. HDR is not claimed without end-to-end precision, display, tone mapping, encoder, and metadata validation. Phone/tablet adaptive editing, accessibility, storage recovery, privacy, and thermal/memory budgets are release requirements.
