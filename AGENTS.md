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
