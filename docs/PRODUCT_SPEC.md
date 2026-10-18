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

A Kotlin SAF picker, metadata read, MediaPlayer preview with embedded audio, play/pause/seek controls, native timeline snapshot/edit controls (split/trim/delete/ripple/undo/redo), background project save/load, explicit autosave recovery, and debounced autosave are authored. They have not been Android-built or device-tested here. The current project UI is an early clip list; preview seeks to and stops at the selected clip's source range but does not sequence the edited timeline or render effects/transitions. On a documented supported Android device: import one locally accessible SDR video; inspect metadata; decode and preview it; play/pause/seek with corresponding audio; perform a trim/split represented in the same timeline used for preview and export; save and reopen the project; export the edited result through a supported Android codec. Validate A/V sync, cancellation, failure cleanup, lifecycle/surface recreation, and output correctness. Host tests alone do not satisfy these criteria.

## Product constraints

Initial target is representative 1080p SDR, not universal 4K/60/HDR support. Unsupported media should produce actionable errors. HDR is not claimed without end-to-end precision, display, tone mapping, encoder, and metadata validation. Phone/tablet adaptive editing, accessibility, storage recovery, privacy, and thermal/memory budgets are release requirements.
