# Owncut

Owncut is an original, offline-first Android video-editing project. Its product direction takes inspiration from professional post-production workflows without copying another product's source, branding, assets, algorithms, or exact UI.

## Current checkpoint

The repository contains a portable C++20 engine foundation (rational timeline, edit-command history, and versioned project codec/store) and an Android Kotlin/JNI integration shell. The Android Activity implements SAF video import, metadata/preview/playback, a clip list with selection and split/trim/delete/ripple/undo/redo controls, background project save/load, explicit autosave recovery, and debounced autosave scheduling. Android build/lint CI passed for the last published debug APK, but emulator instrumentation timed out during boot and a phone user reported a black preview for a 3840×1632 video; preview-surface reattachment, a first-frame diagnostic, and clearer Bengali trim help are being added and are not device-verified. This is an early editing shell, not a professional/full editor: preview is not driven by the edited render graph, project files use app-private filesystem paths rather than SAF, media relinking is absent, and render/export and production audio/color pipelines remain incomplete.

## Build and test

Host core: a C++20 compiler (GCC or Clang); fast check: `make test`. CMake 3.20+ is supported. Android setup and commands use the pinned Gradle wrapper; see [Build and test](docs/BUILD_AND_TEST.md). Android builds/device checks cannot be run from environments without the JDK and Android SDK/NDK.

Read [the product specification](docs/PRODUCT_SPEC.md), [architecture](docs/ARCHITECTURE.md), [roadmap](docs/ROADMAP.md), and [progress](docs/PROGRESS.md) before treating planned work as implemented.
