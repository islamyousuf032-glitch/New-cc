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

Not yet satisfied: one timeline must drive preview and export; reliable gap/transition handling; robust cross-track A/V scheduling and sync; verified media relinking; cancellation/failure cleanup and lifecycle/surface tests; edited output export on a supported device. Current preview uses separate MediaPlayer clips and stops at timeline gaps. APK `phone-test-39250035336` includes same-track long-press drag/reorder with free-slot snapping through JNI; the new gesture awaits Galaxy F23 validation. Cross-track drag is absent. Project documents are copied through app-cache temporary files and provider-side atomic replacement is not guaranteed; Linux/Android local files now sync data+directory, but the write-ahead journal and power-loss fault tests remain absent. APK `phone-test-39250035336` includes selected-clip relink with source-range validation and undo. The newer source changes it to update every clip sharing the original media reference as one atomic undoable edit; that group behavior needs Android build and Galaxy F23 validation.

Acceptance gate: on documented supported Android devices, import a locally accessible SDR video, visibly render frames, play/pause/seek with corresponding audio, edit through the timeline that drives preview and export, save/reopen and recover safely, then export the edited result through a supported codec. Validate A/V sync, cancellation, failure cleanup, lifecycle/surface recreation, and output correctness. Host tests, metadata import, and user-reported playback alone do not satisfy the export/sync/persistence gate.

## Product constraints

Initial target is representative 1080p SDR, not universal 4K/60/HDR support. Unsupported media should produce actionable errors. HDR is not claimed without end-to-end precision, display, tone mapping, encoder, and metadata validation. Phone/tablet adaptive editing, accessibility, storage recovery, privacy, and thermal/memory budgets are release requirements.
