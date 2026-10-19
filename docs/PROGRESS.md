# Progress

## Implemented

- Portable C++20 rational-time timeline, non-destructive edit commands/history, versioned project serialization/store, v0 migration reader, and host tests.
- Android shell with SAF video import, metadata extraction, MediaPlayer/TextureView preview, and play/pause/seek. The user confirmed the preview and editing UI work on a Galaxy F23 (Android 14, 6 GB RAM, Snapdragon 750G); screenshot shows a visible frame from a 3840×1632, 9-second source and timeline clips. This confirms that phone/sample smoke test only; it does not establish A/V sync, all-device codec support, or export.
- Timeline UI with clip selection, split, left/right trim, delete, ripple delete, undo/redo and playback over directly adjacent same-track clips. Timeline gaps stop the preliminary preview; blocks cannot yet be dragged/reordered, and preview is not a compositor.
- JNI session ownership, timeline snapshot bridge, background app-private project save/load, debounced autosave and explicit recovery UI.
- Added SAF project Save As/Open actions. The adapter serializes to/from a temporary app-cache file because the native project codec accepts filesystem paths, then streams bytes through `ContentResolver`. This is not an atomic provider-side replacement and does not persist document grants; users can choose the project document again when reopening.
- Removed the unsupported `TextureView.setBackgroundColor()` startup crash and improved startup-error contrast.

## In progress / not complete

- Latest APK is private prerelease `phone-test-39243277355` from commit `2e6d398`. Android APK build/lint, AndroidTest source compilation and 16 KB zip alignment passed; release publication succeeded. The emulator JNI instrumentation step failed, so no emulator launch/JNI test is claimed. See the run for current details. The Galaxy F23 user has now confirmed successful visible playback and working editing controls.
- SAF project Save As/Open source has been added after that APK was built and still needs Android CI compilation and a real Documents-provider save/reopen test.
- No single timeline-driven compositor/export graph, edited output export, cross-track A/V scheduling, media relinking, or robust crash-recovery journal/fsync exists. Effects, transitions, audio mixing, and color management are also absent.
- Multi-clip playback has decoder handoffs and only advances across exactly adjacent clips on the same track; no A/V sync or gap-free playback guarantee exists.

## Environment and verification limits

- Host tests do not verify Android UI, SAF provider behavior, Samsung codec/GPU behavior, media synchronization, lifecycle, or output rendering.
- User-reported Galaxy F23 results are recorded separately from CI/emulator results. No independent physical-device measurement or export check has been performed.
- An earlier Android CI job compiled tests and built APK successfully but its emulator startup/instrumentation did not pass. The most recent workflow also marked instrumentation failed after successful build/lint/alignment/publication; inspect the run/job for exact failure details before asserting an emulator launch result.

## Tests actually run

- Portable C++ core tests, sanitizer build/run, strict-warning build, JNI syntax-only check, and `git diff --check` passed in earlier checkpoints.
- Latest portable-core CI run `39243277200` passed.
- Android run `39243277355`: build/lint passed; instrumentation Kotlin source compiled; APK zip alignment passed; private prerelease published; instrumentation smoke-test step failed. The user successfully ran that release on Galaxy F23 and reports visible video and working editing features.
- SAF project-document adapter edits in the current working tree have not yet been Android-built or device-tested. Run `git diff --check` and host tests before committing; CI can verify Kotlin compilation/build but not provider behavior.

## Next steps

1. Build/lint and compile the new SAF project document flow; add focused Android tests where a reliable document-provider test fixture is practical.
2. Verify Save As/Open and malformed/revoked documents on a physical Android device; document provider-specific error behavior.
3. Design and test a shared timeline render/export pipeline with cancellation, failure cleanup, audio/video synchronization, and codec capability gating. This is the largest unmet Phase 1 acceptance gate.
4. Continue media relinking, crash-safe persistence, timeline drag/reorder and multitrack behavior as separate acceptance-gated changes.
