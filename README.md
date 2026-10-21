# Owncut

Owncut is an original, offline-first Android video-editing project. Its product direction takes inspiration from professional post-production workflows without copying another product's source, branding, assets, algorithms, or exact UI.

## Current checkpoint

The Android shell imports media through SAF, displays and plays a video preview, and exposes a timeline with split/trim/delete/ripple/undo/redo and newly authored same-track drag/reorder (pending Android validation). The user has now confirmed on a Galaxy F23 (Android 14, 6 GB RAM, Snapdragon 750G) that preview is visible and the editing features work; the attached screenshot shows a 3840×1632 source and timeline. This is a user-reported smoke test, not independent codec, A/V-sync, or export validation.

This checkpoint also adds project Save As/Open through the Android Storage Access Framework. A C++20 engine owns rational-time timeline/history and versioned project serialization. Android build/lint and APK alignment passed for the latest release, while emulator instrumentation failed; see [progress](docs/PROGRESS.md) and [build/test](docs/BUILD_AND_TEST.md) for exact limitations.

Owncut remains an early editing shell, not a complete professional editor. Preview still uses platform MediaPlayer clip handoffs rather than one timeline-driven composition graph. A restricted keyframe-aligned MP4 stream-copy exporter is in the current debug APK. User testing exposed frame-duration rounding rejection, empty gaps, and multi-source projects. The follow-up source work accepts half-frame rounding, explicitly compacts gaps after confirmation, joins multiple sources only when their codec/configuration/track formats match, and validates the muxed MP4 by reopening it and decoding its first frame before SAF copy. This new work is not yet Android-built or device-tested; current APK `phone-test-39270709637` predates it. General frame rendering/re-encoding, black-frame gap rendering, robust multi-track scheduling, write-ahead crash journaling, and production audio/color/effects are not implemented. A selected-clip media relink action is in the newest debug APK but awaits phone validation. Linux/Android local project replacements now call fsync; this is not a journal or power-loss guarantee. Project file Save As/Open does not rewrite original media.

## Build and test

Host core: a C++20 compiler (GCC or Clang); fast check: `make test`. CMake 3.20+ is supported. Android setup and commands use the pinned Gradle wrapper; see [Build and test](docs/BUILD_AND_TEST.md).

Read [the product specification](docs/PRODUCT_SPEC.md), [architecture](docs/ARCHITECTURE.md), [roadmap](docs/ROADMAP.md), [project format](docs/PROJECT_FORMAT.md), and [progress](docs/PROGRESS.md) before treating planned work as implemented.

### Gallery publication follow-up

Android 10+ export saves validated MP4s automatically to `Movies/Owncut` using a pending MediaStore row; no document picker is shown. Android 8–9 retain the SAF picker. Failed/cancelled copies attempt cleanup; provider deletion may fail. Existing empty files are not automatically removed. Device checks must cover successful Gallery thumbnail/playback, cancellation, unsupported-keyframe/format rejection without a new Gallery item, and storage/write failure cleanup. This does not add re-encoding or `.ccraw` decoding; the custom RAW format specification and sample footage are still required. Android/device verification of this publication change is pending.

## Basic render checkpoint (2026-10-21, verification pending)

Default export now routes through `TimelineRenderExporter` (Media3 1.8.1) instead of
stream-copy. The native timeline snapshot supplies clip order and source trim ranges;
Android decodes/renders/re-encodes H.264 + AAC, 30 fps, 720-high SDR, preserving aspect
ratio. A silent audio track is generated where necessary. Gap removal still needs
confirmation; overlaps/multitrack export remain rejected. Import/play/edit are existing
features; RAW and grading are explicitly deferred until basic acceptance is complete.

New real-media instrumentation covers untouched input, non-keyframe trim, three clips
with different resolutions/aspects and a silent source, duration, output first/last
frames and pre-start cancellation. Synthetic fixture provenance is in androidTest/assets.
Compilation is not execution: these tests and Galaxy F23 playback/A/V sync, mid-render
cancellation, Gallery thumbnails and lifecycle still need verification. APK publication
is gated after emulator instrumentation; do not distribute the prior remux APK as this
render checkpoint. Earlier remux-only descriptions above are historical, not the new
selected UI path.
