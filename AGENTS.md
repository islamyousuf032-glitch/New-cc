# Engineering guidance

- Preserve the original-product identity; do not copy proprietary code, branding, assets, algorithms, or exact UI from other editors.
- Keep portable editing/domain logic in C++20. Android lifecycle, permissions, storage, surfaces, and codecs belong in small Kotlin/platform adapters when introduced.
- Keep timeline time integer/rational and distinguish source, timeline, frame, and audio-sample domains. Document conversion and rounding.
- Validate all project/media-derived input. Keep edits non-destructive and IDs stable.
- Do not put I/O, blocking locks, or unbounded allocation in a future real-time audio callback.
- Add tests with each behavior change. Never claim an Android/device feature is verified by host-only tests.
- Update `docs/PROGRESS.md` with only tests actually run and current limitations.
- Avoid adding dependencies unless maintenance, license, Android/ABI support, security, binary size, and relevant page-size compatibility are checked and recorded.

- Treat `docs/ADVANCED_ARCHITECTURE.md` and ADR 0009 as the editable forward baseline. Keep implemented vs planned capabilities explicit; update the baseline through ADRs for incompatible changes.
- Projects shelf metadata belongs to the Android storage adapter, never duplicate timeline/graph edit semantics there. Never silently overwrite a failed project load or discard an unchosen recovery snapshot.
- Workspace navigation must preserve the live viewer/timeline; current Flow is read-only, not an effects renderer. The experimental C++ graph validator is not yet a persisted/evaluated graph.

- UI text must have default English and Bengali resources with matching format arguments. Run `python3 tests/check_ui_resources.py`. Keep project/user content and native identifiers unchanged by language selection.
- Editing action buttons use original semantic icons, localized accessibility/tooltips and at least 48dp targets. Keep confirmation/language-choice copy readable; don't replace consent with ambiguous icons.

- Follow ADR 0011: keep default Edit quiet and prioritize viewer/transport/timeline. Put metadata, source-only seek and secondary tools in contextual panels; important errors/recovery must still notify users. Palette roles live in `studio_themes.json`; test every theme, not just default colors.

- Publish downloadable APKs only with durable signing/pinned public certificate and monotonically managed versionCode; never call random debug artifacts install-over patches. Never store private signing material in Git, chat or release assets.
- Keep readiness.json weights/status/evidence explicit; only verified packages earn credit. Preserve writer ownership across rotation and do not bypass guarded checkpoints for managed projects.

- Native project/history mutation belongs in SessionCoordinator, not JNI fields. Keep JNI result keys and Kotlin synchronized-handle lifetime contract compatible.
- Pages reject stale revisions; do not silently assemble mixed snapshots. Whole JSON/Kotlin lists are still materialized; do not describe this as end-to-end bounded UI memory.
- save_copy must not mark a primary checkpoint clean, and restore_draft must stay dirty. Native saved revision does not replace Activity dirty state until managed checkpoint/catalog transactions are unified.
