# Progress

## Implemented

- Added a portable C++20 rational-time, non-destructive timeline, edit-command/history, and versioned project store with host tests.
- Added JNI session ownership, synchronized edit/project methods, and an escaped JSON timeline-snapshot API with a Kotlin parser.
- Added an Android Activity shell for SAF video import, background metadata extraction, aspect-ratio MediaPlayer preview, clip selection, split/trim/delete/ripple/undo/redo, background save/load, debounced autosave, and explicit recovery.
- The Activity retains the native session over configuration changes, gates edits during project I/O/media metadata work, and provides clip-range preview. The current in-worktree change also chains exactly adjacent clips on the same track; it has not yet been built in CI.
- Project I/O is app-private filesystem based, with confirmation before loading/recovering. Manual phone-test checklist and scoped CI release path are documented.

## In progress / not complete

- Phase 0/1 acceptance gates are not met. A GitHub Actions Android run successfully built and linted the app and passed 16 KB APK zip alignment on commit `a8ccd10`; later run `39136892378` also passed those three steps and published a private prerelease APK, but the emulator smoke test timed out during boot. A KVM-permission setup fix is now pushed; its latest CI outcome is not yet known.
- The current same-track adjacent-clip preview change is uncommitted/unbuilt. The existing release APK does not include that change; wait for the next successful build before choosing which version to test.
- There is no shared full-timeline render/export graph, effects/transitions, robust multi-track A/V scheduling, or exported output. Timeline gaps stop the preliminary clip-sequence preview.

## Environment limitations

- This workspace has no local JDK/Java, Android SDK/NDK, emulator, or device; Gradle cannot run locally. Android build/lint evidence below is from GitHub Actions, not this machine.
- Phone/device behavior is not verified here. The user plans to perform phone testing after an appropriate build is ready.
- ProjectStore writes app-private filesystem paths, not SAF project documents. URI relinking, crash-safe journaling/fsync, export, and production media capability validation remain open.

## Tests actually run

- `make test` — passed with GCC 12.2.0; output: `All core, command-history, and project tests passed`.
- Direct GCC AddressSanitizer + UndefinedBehaviorSanitizer build/run — passed.
- Strict host build using `-Wconversion -Wsign-conversion -Werror` and full test executable — passed.
- JNI C++ translation unit passed host syntax-only with strict warnings and a temporary minimal JNI declaration stub; this is not NDK/ABI validation.
- `git diff --check`, Android XML parsing, and whitespace scan — passed.
- GitHub portable-core CI — passed on the session branch.
- GitHub Android CI run `39135320736`: APK build, Kotlin lint, and 16 KB zipalign passed; emulator instrumentation timed out waiting for boot.
- GitHub Android CI run `39136892378`: APK build, lint, and zipalign passed; phone-test prerelease `phone-test-39136892378` was published with APK and SHA-256. Emulator instrumentation was still pending/booting at last check; do not report it as passed. Actions artifact upload was blocked by the GitHub account's Actions artifact storage quota.
- An earlier Android CI attempt exposed a Kotlin Gradle `jvmTarget` configuration error; this was corrected by migrating to typed `compilerOptions`, and the later APK/lint steps passed.
- Local `./gradlew` attempt failed before Gradle startup because `JAVA_HOME`/`java` is absent.

## Next steps

1. Build the current adjacent-clip preview change in CI and obtain the current phone-test APK release.
2. Keep improving emulator boot reliability with KVM enabled; run JNI instrumentation when the emulator boots.
3. Implement the shared edited-timeline render/export vertical slice and SAF project/media relinking before claiming Phase 1 complete.
4. Once the desired test scope/build is ready, the user can install the private phone-test APK and report device details/results using `DEVICE_TEST_MATRIX.md`.
