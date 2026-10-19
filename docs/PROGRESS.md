# Progress

## Implemented

- Portable C++20 rational-time timeline, non-destructive edit commands/history, versioned project serialization/store, v0 migration reader, and host tests. Local project replacement now fsyncs the temporary file and parent directory on Linux/Android; no write-ahead journal or power-loss fault test exists.
- Android shell with SAF video import, metadata extraction, MediaPlayer/TextureView preview, and play/pause/seek. The user confirmed the preview and editing UI work on a Galaxy F23 (Android 14, 6 GB RAM, Snapdragon 750G); screenshot shows a visible frame from a 3840×1632, 9-second source and timeline clips. This confirms that phone/sample smoke test only; it does not establish A/V sync, all-device codec support, or export.
- Timeline UI with clip selection, split, left/right trim, delete, ripple delete, undo/redo and playback over directly adjacent same-track clips. Same-track long-press drag/reorder is in the latest Android-built source and backed by the undoable C++ move command; it still needs phone interaction testing. Timeline gaps stop the preliminary preview; preview is not a compositor.
- Added a guarded lossless MP4 stream-copy exporter for contiguous clips on one track from one source. It requires exact keyframe-aligned clip starts, matching sub-frame source/timeline durations, monotonic sample timestamps, and at most one audio/video stream. It includes cancellation and plan-validation instrumentation source. This is not transcoding/compositing; the exporter is not yet Android-built or device-tested.
- JNI session ownership, timeline snapshot bridge, background app-private project save/load, debounced autosave and explicit recovery UI.
- Added SAF project Save As/Open actions. The adapter serializes to/from a temporary app-cache file because the native project codec accepts filesystem paths, then streams bytes through `ContentResolver`; it bounds transfers to 96 MiB and has AndroidTest source for bounded-copy behavior. This is not an atomic provider-side replacement and does not persist document grants; users can choose the project document again when reopening.
- Added selected-clip media relink using SAF, persisted URI permission, duration validation and an undoable C++/JNI edit; the initial feature is in APK `phone-test-39250035336` and preserves the clip source range. New unbuilt core changes make relinking every timeline clip sharing the old media reference one atomic undoable operation; host tests pass, Android build and Galaxy F23/provider validation are pending.
- Removed the unsupported `TextureView.setBackgroundColor()` startup crash and improved startup-error contrast.

## In progress / not complete

- Latest published APK is private prerelease `phone-test-39262430899` from commit `9f7a438`. Android APK build/lint, AndroidTest source compilation, 16 KB zip alignment and publication passed. Emulator JNI instrumentation is still running, not yet reported as passed. SAF Save As/Open, same-track drag/reorder, selected/group relink, fsync and guarded MP4 export are included. These newer flows remain untested on the user's Galaxy F23.
- Runs `39260757726` and `39261676160` exposed exporter lint/compile issues (extractor sample flags passed directly to muxer metadata, then duplicate import); both were fixed. Run `39262430899` passes build/lint/compile/alignment and published the new APK. Real Documents-provider save/reopen and actual media output still need physical-device tests.
- The limited remux exporter does not satisfy the general render/export gate: no decoder/compositor/re-encoder graph, arbitrary-frame cuts, multiple source files, gap rendering, cross-track A/V scheduling/mixing, effects, transitions, or color pipeline exists. Linux/Android local saves use file+directory fsync, but power-loss behavior is not fault-injected and SAF provider writes are not atomic.
- Multi-clip playback has decoder handoffs and only advances across exactly adjacent clips on the same track; no A/V sync or gap-free playback guarantee exists.

## Environment and verification limits

- Host tests do not verify Android UI, SAF provider behavior, Samsung codec/GPU behavior, media synchronization, lifecycle, or output rendering.
- User-reported Galaxy F23 results are recorded separately from CI/emulator results. No independent physical-device measurement or export check has been performed.
- Android emulator instrumentation has not passed in this workspace yet: earlier runs failed while booting or skipped because KVM was unavailable; the new `39262430899` emulator JNI smoke test is in progress. Do not treat AndroidTest source compilation as an executed smoke test.

## Tests actually run

- Portable C++ core tests, sanitizer build/run, strict-warning build, JNI syntax-only check, and `git diff --check` passed in earlier checkpoints.
- Latest portable-core CI run `39251452587` passed.
- Android run `39251452651`: build/lint, AndroidTest source compilation, APK zip alignment, and private prerelease publication passed; emulator JNI instrumentation was skipped because KVM was unavailable.
- SAF project-document source was Android-built in runs `39247694047` and later. Drag/reorder, selected/group relink and Linux/Android fsync were built in `39251452651`; provider and phone behavior remain untested.
- Exporter Android run `39262430899`: APK build/lint, AndroidTest Kotlin source compilation, 16 KB zip alignment and private prerelease publication passed. Emulator JNI instrumentation remains in progress; no actual Android sample export has been run. Portable `make test` and `git diff --check` pass. GitHub Actions artifact storage quota is full, so workflow artifacts may not upload; the APK release asset was published separately.

## Next steps

1. Check the final result of emulator instrumentation run `39262430899`; if it fails, capture the exact cause and keep physical-device export validation separate.
2. On the Galaxy F23, test Save As/Open, long-press drag, group relink, and the keyframe-aligned MP4 output; verify output in Gallery, audio continuity, cancellation and failure cleanup.
3. Build the general decoder/compositor/re-encoder graph for arbitrary frame cuts, multiple media sources, gaps and cross-track audio. The remux subset does not satisfy this acceptance gate.
4. Continue multitrack scheduling, provider-safe atomic replacement, power-loss fault testing, and lifecycle/device validation.
