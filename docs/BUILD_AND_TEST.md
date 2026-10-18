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

The instrumentation test checks native library load, media-reference clip insertion and the JSON timeline snapshot (including clip identity, timing, and source range), split/trim/ripple-delete, undo/redo, project save/load, autosave recovery/clear, and native session close through JNI. The Activity now also authors timeline clip selection/edit controls, background project save/load, explicit recovery confirmation, and debounced autosave, but the instrumentation test does not exercise those UI/lifecycle flows. The workflow builds/lints and checks APK alignment before emulator startup. On pushes to the fixed Arena branch it publishes a private, explicitly named `phone-test-*` prerelease containing the arm64-v8a/x86_64 debug APK and SHA-256 file, so a slow/failing emulator does not block manual phone testing. It also attempts a 14-day Actions artifact upload for APK/reports; that fallback is best-effort because repository/account artifact quota may be exhausted. The emulator job runs the JNI smoke test on API 35 x86_64. A debug APK is for manual testing only, not production. Neither CI nor the APK validates broad codec/GPU/audio support, edited-timeline render, A/V sync, or export correctness. NDK r28+ enables 16 KB ELF alignment by default; zipalign validates package alignment. A physical 16 KB ARM64 device/emulator run remains outstanding.

This local environment has no JDK, Gradle, Android SDK/NDK, CMake/Ninja, `adb`, or emulator/device, so Android tasks cannot be run here. Do not interpret host tests as Android validation.

## Environment audit at initial implementation

Linux x86_64; GCC 12.2.0 and Python 3 available. Java/Javac, Gradle, CMake, Ninja, Clang, `adb`, `sdkmanager`, and `ndk-build` were not found; common Android SDK paths were absent. No emulator/device or CI runner is attached to this local environment. The checked-in wrapper is present, but cannot run without Java. Outbound checks to allowed GitHub/npm/PyPI hosts succeeded; this does not verify Android dependency downloads.
