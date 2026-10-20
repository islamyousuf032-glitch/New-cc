# Build and test

## Portable C++ core

Requirements: GCC or Clang with C++20. CMake 3.20+ is supported.

```sh
make test
cmake -S . -B build -DCMAKE_BUILD_TYPE=Debug
cmake --build build --parallel
ctest --test-dir build --output-on-failure
```

`make test` compiles to `/tmp/owncut_core_tests` and runs it, avoiding generated binaries in the repository. CMake build output belongs in ignored `build/`.

## Android app shell and JNI smoke test

Pinned baseline: JDK 17, Gradle 8.13 (wrapper SHA-256 verification enabled), AGP 8.13.2, Kotlin Gradle plugin 2.3.21, compile/target SDK 36, min SDK 26, NDK 28.2.13676358, CMake 3.22.1. These are exact versions, not dynamic selectors; compatibility sources are recorded in ADR 0002. The Gradle wrapper JAR is upstream Gradle v8.13.0 and SHA-256 checked against Gradle's published wrapper checksum.

With Android SDK packages installed (`platforms;android-36`, `build-tools;36.0.0`, `ndk;28.2.13676358`, `cmake;3.22.1`) run:

```sh
./gradlew :app:assembleDebug :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
$ANDROID_HOME/build-tools/36.0.0/zipalign -c -P 16 -v 4 app/build/outputs/apk/debug/app-debug.apk
```

Instrumentation sources cover native library load, media-reference clip insertion and timeline snapshots, move/relink/split/trim/ripple-delete, undo/redo, project save/load, autosave recovery/clear, bounded SAF project-byte copy, remux-export plan constraints, native session close, and MainActivity launch/initial TextureView presence. They do not yet execute codec remux against real media. It does not exercise actual codec playback, UI editing gestures, or full lifecycle flows. The workflow builds/lints, compiles Android instrumentation sources, and checks APK alignment before emulator startup. On pushes to the fixed Arena branch it publishes a private, explicitly named `phone-test-*` prerelease containing the arm64-v8a/x86_64 debug APK and SHA-256 file, so a slow/failing emulator does not block manual phone testing. It also attempts an Actions artifact upload; the repository's artifact storage quota is currently full. The latest published APK is `phone-test-39270709637` from commit `7c06312`. Android build/lint, AndroidTest source compilation, APK alignment, and release publication passed; emulator instrumentation is still running. It includes SAF project Save As/Open, same-track drag/reorder, selected/group media relink, Linux/Android local fsync, and the frame-rounding/gap-compaction remux fix. These workflows still need physical-phone/provider testing. The guarded keyframe-aligned stream-copy exporter was first in APK `phone-test-39262430899`; user screenshots exposed its overly strict sub-frame duration check and gap rejection. APK `phone-test-39270709637` fixes the duration rounding and confirmed gap compaction. New source work additionally joins multiple URIs only if their video/audio codec configuration and track formats match, then reopens the output to validate tracks/timestamps and decodes its first frame before copying to SAF; incompatible sources fail safely. Regression-test source now covers three clips from several media references, frame rounding, gap compaction and overlap rejection. This additional work has not yet had Android build/lint or device-media verification. Actual export playback remains untested. The workflow skips instrumentation with a clear warning when KVM is unavailable. A debug APK is for manual testing only, not production. Neither CI nor the APK validates broad codec/GPU/audio support, edited-timeline render, A/V sync, or export correctness. NDK r28+ enables 16 KB ELF alignment by default; zipalign validates package alignment. A physical 16 KB ARM64 device/emulator run remains outstanding.

This local environment has no JDK, Gradle, Android SDK/NDK, CMake/Ninja, `adb`, or emulator/device, so Android tasks cannot be run here. Do not interpret host tests as Android validation.

## Environment audit at initial implementation

Linux x86_64; GCC 12.2.0 and Python 3 available. Java/Javac, Gradle, CMake, Ninja, Clang, `adb`, `sdkmanager`, and `ndk-build` were not found; common Android SDK paths were absent. No emulator/device or CI runner is attached to this local environment. The checked-in wrapper is present, but cannot run without Java. Outbound checks to allowed GitHub/npm/PyPI hosts succeeded; this does not verify Android dependency downloads.

### Gallery publication follow-up

Android 10+ export saves validated MP4s automatically to `Movies/Owncut` using a pending MediaStore row; no document picker is shown. Android 8–9 retain the SAF picker. Failed/cancelled copies attempt cleanup; provider deletion may fail. Existing empty files are not automatically removed. Device checks must cover successful Gallery thumbnail/playback, cancellation, unsupported-keyframe/format rejection without a new Gallery item, and storage/write failure cleanup. This does not add re-encoding or `.ccraw` decoding; the custom RAW format specification and sample footage are still required. Android/device verification of this publication change is pending.
