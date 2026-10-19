# Owncut

Owncut is an original, offline-first Android video-editing project. Its product direction takes inspiration from professional post-production workflows without copying another product's source, branding, assets, algorithms, or exact UI.

## Current checkpoint

The Android shell imports media through SAF, displays and plays a video preview, and exposes a timeline with split/trim/delete/ripple/undo/redo and newly authored same-track drag/reorder (pending Android validation). The user has now confirmed on a Galaxy F23 (Android 14, 6 GB RAM, Snapdragon 750G) that preview is visible and the editing features work; the attached screenshot shows a 3840×1632 source and timeline. This is a user-reported smoke test, not independent codec, A/V-sync, or export validation.

This checkpoint also adds project Save As/Open through the Android Storage Access Framework. A C++20 engine owns rational-time timeline/history and versioned project serialization. Android build/lint and APK alignment passed for the latest release, while emulator instrumentation failed; see [progress](docs/PROGRESS.md) and [build/test](docs/BUILD_AND_TEST.md) for exact limitations.

Owncut remains an early editing shell, not a complete professional editor. Preview still uses platform MediaPlayer clip handoffs rather than one timeline-driven composition graph. A restricted same-source, keyframe-aligned MP4 stream-copy exporter is being added; general rendering/re-encoding, robust multi-track scheduling, write-ahead crash journaling, and production audio/color/effects are not implemented. A selected-clip media relink action is in the newest debug APK but awaits phone validation. Linux/Android local project replacements now call fsync; this is not a journal or power-loss guarantee. Project file Save As/Open does not rewrite original media.

## Build and test

Host core: a C++20 compiler (GCC or Clang); fast check: `make test`. CMake 3.20+ is supported. Android setup and commands use the pinned Gradle wrapper; see [Build and test](docs/BUILD_AND_TEST.md).

Read [the product specification](docs/PRODUCT_SPEC.md), [architecture](docs/ARCHITECTURE.md), [roadmap](docs/ROADMAP.md), [project format](docs/PROJECT_FORMAT.md), and [progress](docs/PROGRESS.md) before treating planned work as implemented.
