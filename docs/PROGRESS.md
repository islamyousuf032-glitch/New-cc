# Progress

## Implemented

- Added a portable C++20 rational-time, non-destructive timeline, edit-command/history, and versioned project store with host tests.
- Added JNI session ownership, synchronized edit/project methods, and a timeline snapshot API with a Kotlin parser.
- Added an Android Activity shell for SAF import, metadata reading, MediaPlayer preview/playback, clip selection, split/trim/delete/ripple/undo/redo, project save/load, and autosave recovery.
- Preview follows a selected clip's source range and advances only across exactly adjacent clips on the same track. Gaps stop this preliminary preview; this is not a composed timeline renderer.
- The user supplied the Galaxy F23 / Android 14 startup crash stack: `UnsupportedOperationException: TextureView doesn't support displaying a background drawable`, at `MainActivity.buildWorkspace`. The current unbuilt fix removes `setBackgroundColor()` from TextureView, makes the startup-error screen high-contrast, and includes a launch smoke test that requires the preview TextureView to exist.
- AndroidX Test/JUnit instrumentation dependencies are test-only, official stable artifacts under Apache-2.0. Run `39225580353` successfully resolved and compiled Android instrumentation sources; APK build, lint, and 16 KB alignment also passed. The UI launch smoke test did not run because emulator boot timed out.
- The user also reports audio with black video on 3840×1632 and 1920×1080 sources. The previous SurfaceView reattachment/diagnostic did not solve it. Current unbuilt source migrates preview output to TextureView and uses texture-update callbacks to detect frames. It also provides position/width-scaled per-track timeline blocks, a playhead directly beneath them, and clearer trim actions. These are hypotheses, not verified fixes; blocks cannot yet be dragged/reordered.

## In progress / not complete

- Latest published APK is prerelease `phone-test-39225580353` from commit `ed8030d`; build, Kotlin lint, AndroidTest source compilation, 16 KB APK alignment, and release publication succeeded. Emulator instrumentation timed out while booting despite KVM setup passing; Actions artifact storage quota is full. This APK contains the TextureView background-color call that the user has now identified as the launch crash.
- The user's audio-only/black-preview report is not reproduced here. Current source uses TextureView texture-update callbacks to detect frames, but this remains unverified on the user's device. Do not claim it fixes playback until a new APK is installed and tested.
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
