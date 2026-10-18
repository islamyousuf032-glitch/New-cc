# Roadmap and acceptance gates

## Phase 0 — Foundation (current partial checkpoint)

- [x] Repository/environment audit recorded.
- [x] Portable C++20 time/timeline foundation, tests, CMake and host CI workflow.
- [x] Minimal Kotlin Android shell, pinned Gradle/AGP/Kotlin/SDK/NDK/CMake, owned JNI session, and emulator smoke-test workflow added (not yet run locally or by remote CI).
- [x] Versioned project schema (v1) and v0 migration reader; move/split/trim/delete/single-track ripple-delete commands with undo/redo.
- [x] C++ autosave snapshot API with explicit recovery/clear; undo depth cap.
- [x] Activity-side debounced background autosave, final best-effort shutdown save, explicit recovery confirmation, and clip-edit/history controls authored (Android uncompiled/unverified).
- [ ] SAF project-storage adapter, crash-recovery journal, and broader command/history UX.

Gate: host build/tests pass; Android debug build, lint, JNI instrumentation, and APK alignment CI all pass once remote CI runs; instructions are reproducible.

## Phase 1 — End-to-end vertical slice (partial implementation; acceptance gate not met)

- [x] SAF picker, metadata extraction, platform decode/playback surface, basic seek controls, and JNI media-reference insertion authored.
- [x] Batched native timeline snapshot bridge; clip selection, split/trim/delete/ripple/undo/redo UI; background save/load, explicit recovery, and autosave scheduling authored.
- [ ] Android build/CI and device/emulator validation (code is not yet compiled here).
- [ ] One timeline must drive preview and export; playback scheduling, project reopen/recovery, robust cancellation/errors and A/V sync validation.
- [ ] Edited output export on a supported device.

Acceptance: meet the Android/device criteria in `PRODUCT_SPEC.md`, including same timeline for preview/export, persistence, A/V sync, error/cancel and lifecycle tests.

## Phase 2 — Usable MVP

Multiple clips/tracks, thumbnails/waveforms, multi-track-aware ripple semantics, basic transforms/text/transitions/audio mixing, autosave and robust cancellable export. Require deterministic command round trips, serialization/migration/recovery tests, render comparisons and device profiling before gate. Current core commands are not UI-integrated and the basic codec is local-path only.

## Phase 3 — Advanced editing

Advanced trims, keyframes/curves, speed control, proxies and measured cache/playback optimization. Gate on edit invariant tests and performance/memory budgets on the device matrix.

## Phase 4 — Color, audio and effects

Explicit color management, scopes, curves/LUT, selected effects/masks, and advanced DSP. Gate each feature on test vectors, output tolerances and device capability validation; no HDR claim without complete pipeline evidence.

## Phase 5 — Advanced/AI

Selected tracking, stabilization, subtitles and AI only after consent, on-device privacy, model license/size/RAM/acceleration and offline behavior review. HDR/high-resolution remains capability-gated.

## Phase 6 — Release hardening

Device matrix, profiling, accessibility, privacy/store checklist, dependency/license review, release signing instructions and legal review where needed.
