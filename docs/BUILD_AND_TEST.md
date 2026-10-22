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

## Basic render checkpoint (2026-10-21, emulator verified)

Default export now routes through `TimelineRenderExporter` (Media3 1.8.1) instead of
stream-copy. The native timeline snapshot supplies clip order and source trim ranges;
Android decodes/renders/re-encodes H.264 + AAC, source video cadence, 720-high SDR, preserving aspect
ratio. A silent audio track is generated where necessary. Gap removal still needs
confirmation; overlaps/multitrack export remain rejected. Import/play/edit are existing
features; RAW and grading are explicitly deferred until basic acceptance is complete.

New real-media instrumentation covers untouched input, non-keyframe trim, three clips
with different resolutions/aspects and a silent source, duration, output first/last
frames and pre-start cancellation. Synthetic fixture provenance is in androidTest/assets.
Run `39603413359` executed the instrumentation suite successfully, including the
new real-media render cases. Galaxy F23 playback/A/V sync, mid-render cancellation,
Gallery thumbnails and lifecycle still need physical-device verification. APK publication
is gated after emulator instrumentation and `phone-test-39603413359` is published; do not distribute the prior remux APK as this
render checkpoint. Earlier remux-only descriptions above are historical, not the new
selected UI path.

## Basic reliability milestone (emulator regression verified)

Next scope selected by the user: basic editor hardening, not grading/RAW or MVP tools.
New source guards grouped-source relink durations, blocks edits during project I/O,
retains editor history/selection on export-time recreation while cancelling the old
render, preserves paused preview position and clamps seeks to the selected clip.
Gallery publication now uses a testable pending-row transaction with copy/close/size/
publish checks and explicit cleanup-failure reporting. Output duration and first/last
frame decoding are checked before publication. Android run `39620910796` executed the expanded JNI, real-codec, Activity
recreation, MediaStore and injected storage-failure suite successfully after the
initial regression failures were fixed. Build/lint and 16 KB alignment also passed.
Published APK: `phone-test-39620910796` from `4fc4590` (6,809,848 bytes).

Phone checks still needed: exact A/V sync, permission revocation/provider save/reopen,
low storage, Samsung lifecycle/process death. SAVE/LOAD/RECOVER-time recreation still
uses explicit autosave recovery, not background operation handoff. No provider-atomic
replacement, write-ahead journal, full shared preview/export graph, multitrack, grading
or RAW claim is added. Existing user-confirmed 5–6-clip 720p rendering remains valid.

### Reliability checkpoint results

Media import now participates in C++ AddClipCommand Undo/Redo history, including
last-import undo clearing preview. MP4 validation iterates by sample-track index so
negative AAC pre-roll timestamps are not mistaken for EOF. Regression tests were
not weakened after the first failing run. New hardening has emulator evidence, not
a new Galaxy F23 report; that phone's successful 5–6-clip render report concerns the
previous basic-render APK. Project schema is unchanged and reopen resets transient
history, as before. Save important work as a project document before changing debug
APK installations; do not delete app data to resolve installation errors without backup.

## Timeline interaction checkpoint (Android verification pending)

User reports the basic-hardening APK works correctly on their phone, with some
unspecified remaining issues. Next requested batch adds an original red timeline
playhead, HH:MM:SS:FF ruler/time label, unified lane scrolling, tap-to-seek, marker-head
scrubbing, follow toggle/manual browse, proportional short clips/adaptive long scale,
source/sequence time separation and Undo anchor preservation. Native C++ owns point
lookup, exclusive clip boundaries and gap/overlap validation. Android preview now
requests closest-frame seeks and waits for initial seek completion before playback.
No project schema/output-preset/new dependency or advanced-tool/RAW claim is added.

See DECISIONS/0008 for research, clock/conversion/interaction details and test scope.
New host tests pass with `make test`; `git diff --check` passes. Android build/lint and
new UI/real-player tests are pending; no new APK is offered until those tests pass.
Device checks still needed: red-marker smoothness/latency, fast repeated seeks, long
clip/zoomed timeline ergonomics, TalkBack/large fonts, source codecs and exact A/V sync.
The prior export gap-confirmation and single-track render limitations remain.
