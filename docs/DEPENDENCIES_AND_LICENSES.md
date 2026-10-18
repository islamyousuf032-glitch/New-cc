# Dependencies and licensing

## Current dependencies

- Engine uses the C++ standard library only; no third-party runtime/library dependency is currently added.
- Host core: CMake 3.20+ is optional; GCC/Clang C++20 compiler is required. GitHub Actions uses `ubuntu-24.04`, `actions/checkout@v4`, and distro CMake/Ninja packages.
- Android build-time pins: Gradle 8.13, Android Gradle Plugin 8.13.2, Kotlin Gradle Plugin 2.3.21, JDK 17, SDK API 36, NDK 28.2.13676358, CMake 3.22.1. Compatibility and wrapper checksums are recorded in ADR 0002 and Gradle files. Gradle wrapper scripts/JAR are upstream Gradle v8.13.0 under Gradle's Apache-2.0 distribution; wrapper JAR SHA-256 is checked against Gradle's published reference.
- Android runtime: no external runtime library dependency yet; the app uses platform Android APIs and its own native library. CI actions are pinned by major release tags, not immutable commit SHAs; review/pin action SHAs as part of release hardening.
- No app distribution, codec, or third-party binary is present. Repository code is not yet accompanied by a project-level license; determine the intended source/distribution license before publishing or accepting contributions.

## Before adding dependencies

Record upstream source, exact version/commit, maintenance status, license/notices, Android API/ABI and 16 KB page-size compatibility, binary size, security/advisories, and reproducibility. Prefer reproducible source builds and avoid unverified binaries. Recheck official Android/NDK, MediaCodec, Vulkan/OpenGL ES and Oboe/AAudio documentation at the decision point; no runtime backend has been selected.

FFmpeg is not included. If considered, document exact configure flags and LGPL/GPL consequences; do not enable GPL/nonfree components by default. Verify distribution/linking/source obligations and codec patent risks with qualified counsel. This document is engineering guidance, not legal advice; professional legal review is needed before release.

Never commit credentials, signing keystores, private media, or user project files.
