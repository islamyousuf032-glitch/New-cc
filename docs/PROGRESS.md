# Progress

## Completed

- Audited the initially near-empty repository; only `README.md` existed and Git working tree was clean on the session branch.
- Added portable C++20 rational timebase/timestamp operations and a validated non-destructive multitrack timeline model.
- Added unit tests, CMake project, direct host `make test` path, and portable-core GitHub Actions workflow.
- Added and maintained product, architecture, roadmap, build, dependency/license, device-matrix, and decision documentation.
- Added a Kotlin Android shell with SAF video picker, persisted URI-grant attempt, off-main-thread metadata extraction, aspect-ratio preview surface, MediaPlayer decode/playback/seek controls, and JNI clip registration. Android compile/device verification is still outstanding.
- Added explicit native session lifetime, synchronized JNI editing/project APIs, native library build target, and Android instrumentation coverage for edit/history/project/autosave round trips.
- Added a JSON timeline-snapshot JNI API and Kotlin parser for clip ID, track/media ID, timeline start/duration frames, and source in/out milliseconds.
- Added an Activity clip list with clip selection and split-at-playhead, left/right trim-at-playhead, delete, ripple-delete, undo, and redo controls. The platform preview now starts at the selected clip's source-in and stops playback at its source-out; it does not sequence clips or render timeline transitions/effects. The workspace scrolls on small displays and applies system-bar/display-cutout insets for modern edge-to-edge windows.
- Added background project save/load, explicit user-confirmed saved-project replacement and autosave recovery, a debounced autosave after edits, and a final best-effort autosave before native session shutdown. Configuration changes retain the native session; process recreation intentionally requires the explicit recovery flow to avoid overwriting a full autosave with a single restored-media clip. Saves track edit generations and project operations gate further editing while background I/O is active.
- Updated Android CI to upload the debug APK (arm64-v8a and x86_64) and available lint/instrumentation reports for manual device testing after the workflow succeeds.
- Added undoable move, split, left/right trim, delete, and single-track ripple-delete commands. Ripple semantics are explicit: downstream clips starting at/after the removed clip end shift on that track; overlaps and other tracks remain fixed.
- Added strict UTF-8 validation for project text fields and tests for invalid UTF-8 and malformed field lengths.
- Added explicit autosave snapshot save/load/clear APIs; recovery reads a separate snapshot and never overwrites the main project automatically.
- Added versioned project serialization (v1 write; legacy v0 read/migration), bounded parsing, corruption checks, and same-directory temp/rename save with failed-write preservation tests.

## In progress

- Phase 0 foundation and Phase 1 Android editing shell. Neither acceptance gate is met because Android build/CI and device validation remain outstanding; the shared render/export path is not implemented.

## Blocked / unavailable in this environment

- No Java/JDK, Gradle, Android SDK/NDK, CMake/Ninja, Clang, `adb`, emulator or physical device detected. Android build, Kotlin lint, instrumentation, hardware codec/GPU/audio, lifecycle and device tests cannot run here.
- App/JNI editing, project, recovery and autosave code is authored but not yet remotely executed. Android project persistence currently uses app-private filesystem paths, not a SAF document adapter; media relinking, production media/render pipeline and export are not implemented.

## Tests actually run

- `make test` — passed with GCC 12.2.0 (`-std=c++20 -O2 -Wall -Wextra -Wpedantic`); output: `All core, command-history, and project tests passed`.
- Direct GCC build and run with AddressSanitizer + UndefinedBehaviorSanitizer — passed; output: `All core, command-history, and project tests passed`.
- Direct GCC build with `-Wconversion -Wsign-conversion -Werror` in addition to standard warnings, followed by the full test executable — passed with no warnings.
- `git diff --check` — passed.
- Gradle wrapper JAR SHA-256 verified against Gradle-published v8.13 checksum — passed.
- Current JNI C++ translation unit passed host `-fsyntax-only` with `-Wall -Wextra -Wpedantic -Wconversion -Wsign-conversion -Werror` and a temporary minimal JNI declaration stub. This checks C++ syntax only; it is not NDK/ABI validation.
- `./gradlew --no-daemon :app:assembleDebug :app:lintDebug :app:connectedDebugAndroidTest` — attempted; Gradle did not start because `JAVA_HOME` and `java` are unavailable.
- Android build/lint/instrumentation and remote GitHub Actions — not run; local Java/SDK/NDK/emulator are unavailable.
- CMake/CTest and both GitHub Actions workflows were not run: CMake/Ninja are absent locally and no CI runner was invoked.

## Known limitations

- Android Activity and EngineSession Kotlin changes remain uncompiled. The instrumentation test contains snapshot assertions but has not run on Android.
- The preview is Android MediaPlayer playback of the selected source asset, not a preview of timeline edits; there is no shared render graph, export, robust codec capability negotiation, A/V sync guarantee, or output validation.
- Timeline UI is an early clip list rather than a scalable multitrack timeline/inspector. Editing commands are C++/JNI-backed but clip timing is currently surfaced at a fixed 30 fps in this Android shell.
- ProjectStore uses a private binary format and local filesystem paths, same-directory rename without fsync/journal. App project paths are internal; SAF project selection, external backup, media relinking, and recovery across process death/device migration need device validation and further UX.
- Command history is in-memory and is not persisted. Multi-track-aware ripple behavior and linked A/V editing remain unimplemented.
- No effect/color pipeline, audio mixer/DSP, or export. The Activity's MediaPlayer preview is a platform playback path, not the project's processed timeline/export graph.
- Portable host CI and Android emulator CI workflows are configured, but neither has run remotely from this unpushed worktree.

## Next steps

1. Obtain JDK/SDK/NDK or remote Android CI evidence; compile/lint/run instrumentation and fix issues before calling the UI implementation complete.
2. Validate SAF import, media grants, preview lifecycle and codec behavior on ARM64 and 16 KB page-size devices; implement project SAF/relink support.
3. Implement a shared edited-timeline preview/render path and export, then validate A/V sync, cancellation, output correctness and performance on the device matrix.
