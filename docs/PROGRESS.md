# Progress

## Implemented

- Portable C++20 rational-time timeline, non-destructive edit commands/history, versioned project serialization/store, v0 migration reader, and host tests. Local project replacement now fsyncs the temporary file and parent directory on Linux/Android; no write-ahead journal or power-loss fault test exists.
- Android shell with SAF video import, metadata extraction, MediaPlayer/TextureView preview, and play/pause/seek. The user confirmed the preview and editing UI work on a Galaxy F23 (Android 14, 6 GB RAM, Snapdragon 750G); screenshot shows a visible frame from a 3840×1632, 9-second source and timeline clips. This confirms that phone/sample smoke test only; it does not establish A/V sync, all-device codec support, or export.
- Timeline UI with clip selection, split, left/right trim, delete, ripple delete, undo/redo and playback over directly adjacent same-track clips. Added same-track long-press drag/reorder backed by the existing C++ move command through JNI; destination placement snaps out of occupied intervals to a free slot. This UI/bridge change is unbuilt and untested on Android so far. Timeline gaps stop the preliminary preview; preview is not a compositor.
- JNI session ownership, timeline snapshot bridge, background app-private project save/load, debounced autosave and explicit recovery UI.
- Added SAF project Save As/Open actions. The adapter serializes to/from a temporary app-cache file because the native project codec accepts filesystem paths, then streams bytes through `ContentResolver`; it bounds transfers to 96 MiB and has AndroidTest source for bounded-copy behavior. This is not an atomic provider-side replacement and does not persist document grants; users can choose the project document again when reopening.
- Added selected-clip media relink using SAF, persisted URI permission, duration validation and an undoable C++/JNI edit; included in APK `phone-test-39250035336`. It preserves the clip source range. Galaxy F23/provider validation is pending; split siblings sharing the original currently need individual relinks.
- Removed the unsupported `TextureView.setBackgroundColor()` startup crash and improved startup-error contrast.

## In progress / not complete

- Latest published APK is private prerelease `phone-test-39250035336` from commit `5eb8834`. Android APK build/lint, AndroidTest source compilation, 16 KB zip alignment, and release publication passed. Emulator JNI instrumentation was skipped because KVM was unavailable. The Galaxy F23 user tested the crash-fixed APK and confirmed visible playback and working editing controls; the newer drag/relink flows have not been phone-tested.
- SAF project Save As/Open source has been added after that APK was built and still needs Android CI compilation and a real Documents-provider save/reopen test.
- No single timeline-driven compositor/export graph, edited output export, cross-track A/V scheduling, or write-ahead crash-recovery journal exists. Linux/Android local saves use file+directory fsync, but power-loss behavior is not fault-injected and SAF provider writes are not atomic. Effects, transitions, audio mixing, and color management are also absent.
- Multi-clip playback has decoder handoffs and only advances across exactly adjacent clips on the same track; no A/V sync or gap-free playback guarantee exists.

## Environment and verification limits

- Host tests do not verify Android UI, SAF provider behavior, Samsung codec/GPU behavior, media synchronization, lifecycle, or output rendering.
- User-reported Galaxy F23 results are recorded separately from CI/emulator results. No independent physical-device measurement or export check has been performed.
- An earlier Android CI job compiled tests and built APK successfully but its emulator startup/instrumentation did not pass. The most recent workflow also marked instrumentation failed after successful build/lint/alignment/publication; inspect the run/job for exact failure details before asserting an emulator launch result.

## Tests actually run

- Portable C++ core tests, sanitizer build/run, strict-warning build, JNI syntax-only check, and `git diff --check` passed in earlier checkpoints.
- Latest portable-core CI run `39250035441` passed.
- Android run `39250035336`: build/lint, AndroidTest source compilation, APK zip alignment, and private prerelease publication passed; emulator JNI instrumentation was skipped because KVM was unavailable.
- SAF project-document adapter was Android-built in run `39247694047`; provider behavior still needs a phone test. Same-track drag/reorder and media relink are included in `39250035336`; instrumentation source compilation passed, but Android test execution was skipped and phone UI validation remains outstanding.
- Linux/Android local save fsync changes in the current working tree passed `make test`, ASan/UBSan host run, and `git diff --check`; they still need Android CI build after commit.

## Next steps

1. Build/lint the new local fsync durability implementation and publish the next debug APK.
2. Verify project Save As/Open, drag/reorder, and relink on the Galaxy F23; test malformed/revoked documents and split sibling relinking.
3. Design and test a shared timeline render/export pipeline with cancellation, failure cleanup, audio/video synchronization, and codec capability gating. This remains the largest unmet Phase 1 acceptance gate.
4. Continue multitrack scheduling, write-ahead recovery journaling, and lifecycle/device validation.
