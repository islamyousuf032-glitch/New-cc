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

Gate: host tests pass; Android debug build/lint/alignment pass; instrumentation must execute successfully; device checks and reproducible instructions are documented. Latest Android build/lint/alignment passed, but emulator instrumentation was skipped because KVM was unavailable; the full gate is not met.

## Phase 1 — End-to-end vertical slice (partial; acceptance gate not met)

- [x] SAF media picker, metadata extraction, visible platform preview/playback surface, seek controls, JNI media-reference insertion, and user-visible split/trim/delete/history controls.
- [x] Per-track timeline blocks, selected clip, playhead, background project save/load, autosave/recovery, and initial playback across directly adjacent same-track clips.
- [x] Galaxy F23 user smoke test reports visible video and working editing controls. This does not establish A/V sync, broad 4K/codec support, save/reopen correctness or export.
- [x] SAF project-document source compiles in Android build and its bounded-copy instrumentation source compiles; [ ] execute test on emulator and real Documents-provider save/reopen/revocation tests.
- [ ] One timeline-driven preview and export graph, multitrack A/V scheduling, gap handling and sync validation.
- [ ] Verify the newly authored keyframe-aligned same-source MP4 remux export on Android and a physical device, including cancellation, provider cleanup, audio continuity and output correctness.
- [ ] General decoder/compositor/re-encoder export for arbitrary frame cuts, multiple sources, gaps and cross-track audio; shared render graph and A/V sync remain unimplemented.
- [ ] Android build/device acceptance for same-track timeline drag/reorder and media relink, including the new group-relink command; cross-track movement, robust recovery and lifecycle/surface-recreation test coverage.

Acceptance: meet Android/device criteria in `PRODUCT_SPEC.md`, including same timeline for preview/export, persistence, A/V sync, error/cancel and lifecycle tests. No export implementation is claimed yet.

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
