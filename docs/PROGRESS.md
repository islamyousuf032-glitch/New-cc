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

Publication fix verification update: initial Android run `39430675076` failed compilation because `android.annotation.RequiresApi` does not exist; replaced it with platform `TargetApi` (no added dependency). Corrected run `39431256128` for `7873b47` passed APK build/lint, AndroidTest source compilation, 16 KB zip alignment and publication of `phone-test-39431256128`. Emulator instrumentation is still running; Gallery publication, provider failures and cancellation have not been phone-tested. This APK still uses restricted remux and is not a solution for arbitrary split/re-encode or custom RAW.

## Basic render checkpoint (2026-10-21, verification pending)

Default export now routes through `TimelineRenderExporter` (Media3 1.8.1) instead of
stream-copy. The native timeline snapshot supplies clip order and source trim ranges;
Android decodes/renders/re-encodes H.264 + AAC, source video cadence, 720-high SDR, preserving aspect
ratio. A silent audio track is generated where necessary. Gap removal still needs
confirmation; overlaps/multitrack export remain rejected. Import/play/edit are existing
features; RAW and grading are explicitly deferred until basic acceptance is complete.

New real-media instrumentation covers untouched input, non-keyframe trim, three clips
with different resolutions/aspects and a silent source, duration, output first/last
frames and pre-start cancellation. Synthetic fixture provenance is in androidTest/assets.
Compilation is not execution: these tests and Galaxy F23 playback/A/V sync, mid-render
cancellation, Gallery thumbnails and lifecycle still need verification. APK publication
is gated after emulator instrumentation; do not distribute the prior remux APK as this
render checkpoint. Earlier remux-only descriptions above are historical, not the new
selected UI path.

Run `39595474496` (`509df3c`): Android build/lint, AndroidTest compilation and 16 KB
alignment passed; portable CI `39595474834` passed. Emulator boot timed out before
instrumentation ran, so no new render test is claimed as passed and no release APK
was published. Actions artifact quota is still full and run-log download is blocked
in this sandbox. Replaced opaque emulator action with SDK emulator boot plus captured
log/annotation diagnostics and bounded boot checks; subsequent run pending. Also
hardened render worker interruption cleanup and corrected docs: encoded video keeps
source cadence (`EditedMediaItem.setFrameRate` would only apply to images).

Diagnostic run `39598265360` (`b8a81e4`) passed build/lint, test compilation and APK
alignment, but the emulator exited before boot: `Unknown AVD name [owncut]`, with no
`owncut.ini` in the emulator's search directory. CI now explicitly aligns
ANDROID_USER_HOME / ANDROID_EMULATOR_HOME / ANDROID_AVD_HOME, creates the AVD at that
path and writes its descriptor. No render assertions executed and no APK published.

Run `39599071568` (`d5ae07b`) again passed Android build/lint/test compilation and
alignment, but this runner reported KVM unavailable and skipped instrumentation.
No release was published. CI now explicitly chmods an existing KVM device and fails
rather than reporting an apparently green acceptance run when KVM is absent. Added
JUnit XML-to-annotation reporting so real codec assertion failures remain visible
even when artifact storage is full. Basic rendering is implemented but not yet
runtime-verified; acceptance is still open.

Run `39599894456` (`be9479d`): build/lint, AndroidTest compile and alignment passed.
KVM/AVD-path checks passed, but rolling emulator 37.2.12 exited during startup before
any guest boot output or codec assertions. No APK published. Pinned official stable
36.4.9 with its official SHA-256, disabled unused Vulkan and added process exit-code
diagnostics; next CI run pending. This still does not constitute a passed render test.
