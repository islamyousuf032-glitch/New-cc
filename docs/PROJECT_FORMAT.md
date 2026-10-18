# Project format and persistence checkpoint

## Status

The portable C++ project codec is wired to the initial Android Activity through app-private filesystem paths for background save/load and autosave recovery; it is not integrated with the Storage Access Framework. Original media is referenced by opaque `media_id` values and is never rewritten by the project store. The Activity attempts persisted SAF media URI access, but relink UX, SAF project documents, crash-recovery journaling, fsync guarantees, and storage-space checks remain unimplemented.

## Current binary schema

All integers are fixed-width little-endian. Signed timestamps use their two's-complement 64-bit bit pattern. Text fields are length-prefixed with an unsigned 32-bit byte length; strings must be valid UTF-8 without NUL bytes and are capped at 4096 bytes. Maximum accepted project data is bounded by 4096 tracks, 8192 clips total, and these per-field caps (worst-case under roughly 85 MB). Parsing also validates enum values, booleans, rational timebases, IDs, source ranges, timestamp limits, schema version, truncation, and trailing bytes.

Header: eight bytes `OWNCUTPJ`, then a 32-bit schema version.

- **v0 legacy read-only**: project ID, project name, frame timebase numerator/denominator; track count; for each track: ID, kind, clip count; each clip has ID, media reference, timeline start, source in, and source out timestamps. v0 has no mute/lock fields; migration defaults both to `false`.
- **v1 current**: same layout, but each track additionally stores one-byte muted and locked flags after kind. Writer always emits v1. Reader rejects unknown future versions without modifying the source file.

Each timestamp stores signed 64-bit ticks and its own positive rational timebase. Timeline start is normalized to the project's frame timebase through the existing timestamp conversion rules; source in/out timebases remain explicit.

## Save behavior and limits

`ProjectStore::save_atomic` writes a temporary sibling file, flushes/closes it, then renames it over the destination. A failed write leaves an existing destination untouched and removes the temporary file. This relies on same-filesystem rename semantics. `save_autosave` writes a separate `<project-path>.autosave` snapshot; `load_autosave` returns it without changing the main project, and `clear_autosave` removes it only when explicitly called. The API does **not** call `fsync`, guarantee power-loss durability, schedule autosaves, or provide a journaling/recovery log. Android SAF document providers need an adapter that implements provider-appropriate atomic replacement; do not pass a `content://` URI as a filesystem path.

Host tests cover v1 round trip, v0 migration, locked/muted tracks, truncated/unsupported/trailing data, and failed-save preservation. Add fsync/journal/autosave and actual provider/revoked-permission tests before claiming reliable Android recovery.
