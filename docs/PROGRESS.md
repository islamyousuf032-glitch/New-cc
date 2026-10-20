# Progress

## Implemented

- Portable C++20 rational-time timeline, non-destructive edit commands/history, versioned project serialization/store, v0 migration reader, and host tests. Local project replacement now fsyncs the temporary file and parent directory on Linux/Android; no write-ahead journal or power-loss fault test exists.
- Android shell with SAF video import, metadata extraction, MediaPlayer/TextureView preview, and play/pause/seek. The user confirmed the preview and editing UI work on a Galaxy F23 (Android 14, 6 GB RAM, Snapdragon 750G); screenshot shows a visible frame from a 3840×1632, 9-second source and timeline clips. This confirms that phone/sample smoke test only; it does not establish A/V sync, all-device codec support, or export.
- Timeline UI with clip selection, split, left/right trim, delete, ripple delete, undo/redo and playback over directly adjacent same-track clips. Same-track long-press drag/reorder is in the latest Android-built source and backed by the undoable C++ move command; it still needs phone interaction testing. Timeline gaps stop the preliminary preview; preview is not a compositor.
- Added a guarded lossless MP4 stream-copy exporter for non-overlapping clips with keyframe-aligned starts, monotonic timestamps, cancellation, and at most one video/audio track. The user’s newer screenshots show the Gallery could not create a thumbnail and the current APK rejected clips from multiple originals. Follow-up source now joins multiple URIs only when codec configuration and track formats are compatible, checks timestamps, reopens the output, and decodes its first frame before SAF copy. These changes passed Android build/lint and AndroidTest source compilation in run `39420456732`; device-media verification remains pending; this remains stream-copy, not general rendering/compositing.
- JNI session ownership, timeline snapshot bridge, background app-private project save/load, debounced autosave and explicit recovery UI.
- Added SAF project Save As/Open actions. The adapter serializes to/from a temporary app-cache file because the native project codec accepts filesystem paths, then streams bytes through `ContentResolver`; it bounds transfers to 96 MiB and has AndroidTest source for bounded-copy behavior. This is not an atomic provider-side replacement and does not persist document grants; users can choose the project document again when reopening.
- Added selected-clip media relink using SAF, persisted URI permission, duration validation and an undoable C++/JNI edit; the initial feature is in APK `phone-test-39250035336` and preserves the clip source range. New unbuilt core changes make relinking every timeline clip sharing the old media reference one atomic undoable operation; host tests pass, Android build and Galaxy F23/provider validation are pending.
- Removed the unsupported `TextureView.setBackgroundColor()` startup crash and improved startup-error contrast.

## In progress / not complete

- Latest published APK is private prerelease `phone-test-39420456732` from commit `2fc8883`. Android APK build/lint, AndroidTest source compilation, 16 KB zip alignment and publication passed. It includes compatible multi-source remux and output first-frame validation. Emulator instrumentation is still running; Galaxy F23 export validation is pending.
- Galaxy F23 screenshots from the latest APK show a Gallery item with no thumbnail and the export error for different original sources. That screenshot alone does not prove the MP4 is corrupt or unplayable. Follow-up source now handles multiple sources only when codec MIME/configuration, dimensions, profile/level, color metadata, rotation and audio layout match; it reopens the muxed file, checks sample timestamps, and decodes a first frame before copying. Planner/format compatibility tests were added and Android-compiled, but execution is not yet confirmed; no exported MP4 was attached for direct analysis.
- The limited remux exporter does not satisfy the general render/export gate: no decoder/compositor/re-encoder graph, arbitrary-frame cuts, mismatched-source normalization, black-frame gap rendering, cross-track A/V scheduling/mixing, effects, transitions, or color pipeline exists. Linux/Android local saves use file+directory fsync, but power-loss behavior is not fault-injected and SAF provider writes are not atomic.
- Multi-clip playback has decoder handoffs and only advances across exactly adjacent clips on the same track; no A/V sync or gap-free playback guarantee exists.

## Environment and verification limits

- Host tests do not verify Android UI, SAF provider behavior, Samsung codec/GPU behavior, media synchronization, lifecycle, or output rendering.
- User-reported Galaxy F23 results are recorded separately from CI/emulator results. No independent physical-device measurement or export check has been performed.
- Android emulator instrumentation has not passed in this workspace yet: earlier runs skipped because KVM was unavailable, and run `39262430899` failed while booting the emulator; run `39270709637` also failed emulator boot; current run `39420456732` is in progress. Do not treat AndroidTest source compilation as an executed smoke test.

## Tests actually run

- Portable C++ core tests, sanitizer build/run, strict-warning build, JNI syntax-only check, and `git diff --check` passed in earlier checkpoints.
- Latest portable-core CI run `39251452587` passed.
- Android run `39251452651`: build/lint, AndroidTest source compilation, APK zip alignment, and private prerelease publication passed; emulator JNI instrumentation was skipped because KVM was unavailable.
- SAF project-document source was Android-built in runs `39247694047` and later. Drag/reorder, selected/group relink and Linux/Android fsync were built in `39251452651`; provider and phone behavior remain untested.
- Android run `39270709637` for `7c06312`: APK build/lint, AndroidTest Kotlin source compilation (including new frame-rounding/gap-compaction cases), 16 KB zip alignment and private prerelease publication passed. Emulator JNI instrumentation failed on boot timeout; no codec sample export has run. Portable `make test` and `git diff --check` pass. GitHub Actions artifact storage quota is full, so workflow artifacts may not upload; the APK release asset was published separately.

- Android run `39420456732` for `2fc8883`: APK build/lint, AndroidTest Kotlin compilation, 16 KB alignment and phone-test release publication passed. Emulator instrumentation is still running. Portable CI `39420456739` passed; local `make test` and `git diff --check` passed. No physical-device export verification has run.

## Next steps

1. Check emulator instrumentation run `39420456732`; prior boot timeout `39262430899` is an environment failure, not an exporter test result.
2. Install APK `phone-test-39420456732` on the Galaxy F23 and test one clip, three same-source clips, two/three compatible sources, incompatible-source rejection, and a gapped timeline with confirmation; verify Gallery playback, audio continuity, cancellation and failure cleanup.
3. Build the general decoder/compositor/re-encoder graph for arbitrary frame cuts, multiple media sources, gaps and cross-track audio. The remux subset does not satisfy this acceptance gate.
4. Continue multitrack scheduling, provider-safe atomic replacement, power-loss fault testing, and lifecycle/device validation.

## Follow-up: zero-byte exports and automatic Gallery publication

User screenshots show multiple 0 B SAF documents. The document picker creates these before remux validation, so their presence does not establish that muxing succeeded. Android 10+ now bypasses the export document picker: validate the app-cache MP4 first, then insert a hidden `IS_PENDING` MediaStore video under Movies/Owncut, copy/close it, and publish. Copy/cancel/update failure attempts row deletion. Android 8–9 retain SAF with best-effort deletion on failure/cancellation; providers may refuse deletion. Existing old zero-byte files are not removed.

This is a publication fix, not a render pipeline: arbitrary split boundaries and incompatible sources remain unsupported by remux. User requests custom `.ccraw` camera footage support; a format specification, sample and/or writer implementation is needed before decoder design. No RAW support is claimed.

Verification: local `make test` and `git diff --check` passed; Android CI and phone publication tests pending for this change. Earlier Android run 39420456732 built/linted/compiled AndroidTests and published the multi-source APK; do not confuse that APK with this new publication change.
