# Dependencies and licensing

## Optional host checks added 2026-10-23 (not shipped)

No new app/runtime dependency. The cache-only host JNI smoke test used **JPype1 1.7.1**
(upstream https://github.com/jpype-project/jpype, wheel metadata `Apache-2.0`), a local JVM
and original fixture bytecode. **jdk4py 25.0.2.1** came from PyPI, upstream
https://github.com/activeviam/jdk4py; its metadata classifies GPLv2 and bundled Java
25.0.2 includes GPLv2 with the Classpath exception and per-module legal notices.
This is a development runtime, not a JDK compiler or an Android build-toolchain change.
Official OpenJDK 17 JNI headers were obtained from `openjdk/jdk` at `jdk-17+35`
(GPLv2 + Classpath exception notices retained in cache). **PyYAML 6.0.3** (MIT) was used
only to parse workflow syntax, not to execute application inputs. These tools and
transitive Python packages are not vendored or included in APKs.

Local distribution metadata/notices and actual execution were inspected; this is not
a fresh full upstream maintenance/security/advisory audit or blanket approval for
redistribution. Re-audit current official releases/notices before promoting any into
required CI or shipped dependencies. Android CI remains on the existing JDK 17 pins.

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

CI emulator reproducibility follow-up: rolling SDK emulator 37.2.12 exited before
guest boot in run 39599894456. Pin the official stable 36.4.9 Linux archive, build
14788078, SHA-256 `85eab2a52517e8cac33dcd7fe3bed43a19472c9c2f17badf1f2616327d71488f`,
verified from https://developer.android.com/studio/emulator_archive . Software GLES
is used without Vulkan for headless codec tests; this is CI tooling, not an APK
dependency. Official archive license notice applies; no emulator binary is committed.

Measured basic-render APK release size: 6,786,632 bytes (`phone-test-39603413359`),
versus 3,350,454 bytes in `phone-test-39431256128` before Media3. These are GitHub
release-asset sizes, not an installed-memory or on-device performance measurement.
The final render CI run executed real codec tests successfully on the pinned emulator;
Samsung/physical-device performance remains unmeasured.


## Project/node architecture batch

No dependency added. Uses existing Android platform Activity/View/AtomicFile/SAF APIs
(min API 26 preserved), existing native project codec, and original C++20 graph contract.
Official public workflow references are linked in ADVANCED_ARCHITECTURE; they are research
references, not redistributed source, assets or licensed product components. Media3 and
AndroidX pinned versions/licenses are unchanged; no GPU/audio/RAW backend selected.


## Studio UI and language batch
No added runtime/build dependency. Original stroke-vector icon drawing uses Android
Canvas/Path; no third-party icon assets/fonts or product UI copied. Configuration,
SharedPreferences, platform dark Material theme and native tooltips already support
min API 26. Python resource gate uses standard library only. Existing Media3/AndroidX
maintenance/license pins are unchanged. ADR 0010 links official Android guidance.

Quiet-editor/theme pass adds no dependency. Palette JSON, platform layouts, existing
Canvas icons and dark-theme variants are original app code/data. ADR 0011 records
official public color guidance; no third-party UI/source/assets redistributed.

Managed-storage/patch batch adds no dependency: platform FileLock/MessageDigest and
existing native codec, SDK apksigner/aapt2/zipalign, JDK signing config, Python standard
library verifier/ledger tests. Secrets are runner-temporary only; no signing key ships
in source or app. UPDATE_APKS documents owner setup and old-debug-key constraints.

Actions currently warns that setup-gradle@v4/upload-artifact@v5 target deprecated
Node20 and are forced onto Node24 by hosted runners. Existing tasks worked through
emulator gates; updating action majors is a separate compatibility checkpoint.
Artifact report upload still hits quota; this does not imply app test success/failure.
Latest CI startup is blocked by account payment/spending limits, not a native build
failure. No signing keys or downloaded reference APKs are repository files.

Native coordinator checkpoint: no new app/runtime third-party dependency. C++20
mutex/thread facilities use the existing standard library/platform Threads. Host JNI
syntax checks used official OpenJDK `jdk-17+35` headers from openjdk/jdk (GPLv2 with
Classpath exception); these temporary check headers are not copied into the project
or app. Local CMake tooling was installed in excluded sandbox cache, not vendored.
Existing app/tool dependency pins are unchanged.
