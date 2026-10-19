# Progress

## Implemented

- Added a portable C++20 rational-time, non-destructive timeline, edit-command/history, and versioned project store with host tests.
- Added JNI session ownership, synchronized edit/project methods, and a timeline snapshot API with a Kotlin parser.
- Added an Android Activity shell for SAF import, metadata reading, MediaPlayer preview/playback, clip selection, split/trim/delete/ripple/undo/redo, project save/load, and autosave recovery.
- Preview follows a selected clip's source range and advances only across exactly adjacent clips on the same track. Gaps stop this preliminary preview; this is not a composed timeline renderer.
- The user reports audio plays but video stays black on both 3840×1632 and 1920×1080 sources; the previous SurfaceView reattachment and first-frame diagnostic did not solve it. Bengali trim guidance was also added. The current unbuilt change replaces SurfaceView with TextureView and uses texture-update callbacks to determine whether frames reach the preview view. It also turns the clip list into position/width-scaled per-track timeline blocks, puts the seek bar directly under them, and clarifies trim actions. These are plausible rendering/UI fixes, not yet built or phone-verified; clip blocks cannot yet be dragged/reordered.

## In progress / not complete

- Latest published APK is prerelease `phone-test-39211296408` from commit `86d03af`; build, Kotlin lint, 16 KB APK alignment, and release publication succeeded. The optional KVM check passed, but emulator instrumentation timed out while booting. The workflow now skips emulator instrumentation with a clear warning when KVM is unavailable. Artifact upload reports the repository's Actions storage quota is full.
- The user's black-preview report is not yet reproduced here. MediaPlayer metadata preparation may succeed without a rendered frame; the pending UI change will indicate whether Android reports a video-rendering-start event and show a codec/resolution hint if it does not. Do not claim this fixes the user's device until a new APK is installed and verified.
- No full edited-timeline render/export graph, effects/transitions, multi-track A/V scheduling, or exported output exists.

## Environment limitations

- This workspace has no local JDK/Java, Android SDK/NDK, emulator, or device; Android verification must use GitHub Actions and a real phone.
- The user performs physical-phone testing. This agent cannot verify Samsung decoder output or claim a device test passed.
- Project storage is app-private filesystem based; SAF project storage, media relinking, crash-safe journaling/fsync, export, and broad codec capability validation remain open.

## Tests actually run

- Host C++ core tests, sanitizer build/run, strict-warning build, JNI syntax-only check, and `git diff --check` passed in earlier checkpoints.
- Latest portable-core CI run `39137894529` passed.
- Android CI run `39137894514` built/linted the app, passed 16 KB APK zip alignment, and published `phone-test-39137894514`; emulator JNI instrumentation failed with `Timeout waiting for emulator to boot`. The KVM permission step succeeded. Artifact upload failed due storage quota.
- Earlier Android runs also produced private debug APK releases; emulator boot has not passed. These APKs are debug/manual-test builds, not production releases.
- The current Bengali UI and first-frame diagnostic edits have not yet been Android-built. Local Gradle is unavailable without Java/Android SDK.

## Next steps

1. Build and lint the current black-preview diagnostic and Bengali edit-help changes; publish a fresh debug APK.
2. Have the user retry with a locally playable 1080p SDR sample and the reported 3840×1632 file; capture the app status shown after Play, and compare the file's playback in the phone's native gallery.
3. Investigate actual decoder/render behavior from that evidence; do not equate metadata import or MediaPlayer prepared state with visible video frames.
4. Address emulator boot reliability separately, then continue shared timeline render/export and project/media relinking work.
