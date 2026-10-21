# Dependencies and licensing

## Current dependencies

- Engine uses the C++ standard library only; no third-party runtime/library dependency is currently added.
- Host core: CMake 3.20+ is optional; GCC/Clang C++20 compiler is required. GitHub Actions uses `ubuntu-24.04`, `actions/checkout@v4`, and distro CMake/Ninja packages.
- Android build-time pins: Gradle 8.13, Android Gradle Plugin 8.13.2, Kotlin Gradle Plugin 2.3.21, JDK 17, SDK API 36, NDK 28.2.13676358, CMake 3.22.1. Compatibility and wrapper checksums are recorded in ADR 0002 and Gradle files. Gradle wrapper scripts/JAR are upstream Gradle v8.13.0 under Gradle's Apache-2.0 distribution; wrapper JAR SHA-256 is checked against Gradle's published reference.
- Android runtime: no external runtime library dependency; the app uses platform Android APIs and its own native library.
- Android instrumentation tests only: AndroidX Test core/runner 1.7.0 and JUnit extension 1.3.0. These are test-scope dependencies, not packaged in the app APK. The official [AndroidX Test release page](https://developer.android.com/jetpack/androidx/releases/test) lists these as stable (page updated 2026-01-14); upstream [`android/android-test`](https://github.com/android/android-test) is maintained by the Android team and its repository license is Apache-2.0. The current APK build will verify dependency resolution and instrumentation-source compilation.
- CI actions are pinned by major release tags, not immutable commit SHAs; review/pin action SHAs as part of release hardening.
- No app distribution, codec, or third-party binary is present. Repository code is not yet accompanied by a project-level license; determine the intended source/distribution license before publishing or accepting contributions.

## Before adding dependencies

Record upstream source, exact version/commit, maintenance status, license/notices, Android API/ABI and 16 KB page-size compatibility, binary size, security/advisories, and reproducibility. Prefer reproducible source builds and avoid unverified binaries. Recheck official Android/NDK, MediaCodec, Vulkan/OpenGL ES and Oboe/AAudio documentation at the decision point; no runtime backend has been selected.

FFmpeg is not included. If considered, document exact configure flags and LGPL/GPL consequences; do not enable GPL/nonfree components by default. Verify distribution/linking/source obligations and codec patent risks with qualified counsel. This document is engineering guidance, not legal advice; professional legal review is needed before release.

Never commit credentials, signing keystores, private media, or user project files.

## Media3 rendering adapter (2026-10-21)

- AndroidX Media3 Transformer and Effect pinned to **1.8.1**, official AndroidX-maintained
  media project, Apache-2.0. Official release history checked at
  https://developer.android.com/jetpack/androidx/releases/media3 and upstream 1.8.1
  source/API/license checked at https://github.com/androidx/media/tree/1.8.1 .
- Chosen as the bounded Android codec/GPU adapter, not an editing-domain replacement.
  This known patch-line API is pinned for reproducibility; newer release lines exist,
  so upgrades require the render fixture/device matrix, not an untested floating version.
- Uses platform MediaCodec and GLES; no FFmpeg/native decoder extension is added to
  the APK. Our existing JNI ABI/page alignment checks still apply. APK size increase
  and Samsung performance must be measured from CI/device builds.
- FFmpeg via imageio-ffmpeg was used only off-device to create original synthetic
  instrumentation fixtures; neither the tool nor its binaries are app dependencies.
