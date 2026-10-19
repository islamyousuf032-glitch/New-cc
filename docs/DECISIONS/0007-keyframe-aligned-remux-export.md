# ADR 0007: Offer a guarded, lossless MP4 timeline remux

- Status: Accepted as an initial export subset; not full render/export acceptance.
- Date: 2026-10-19

## Context

The app needs an output path, but it does not yet have a shared decoder/compositor/encoder graph, resampling/audio mixer, or device-verified transcode pipeline. Publishing an export feature that silently ignores trims, gaps, or sync would be unsafe.

## Decision

Use Android's platform `MediaExtractor` and `MediaMuxer` for a lossless stream-copy MP4 export of a deliberately narrow plan: one video track, all clips referencing the same original media URI, clips contiguous from timeline frame zero, source/timeline duration matching within 1 ms, and each clip's source-in exactly on a video sync sample. At most one audio track is copied. Timestamp monotonicity is checked, and extractor sample flags are translated to muxer flags; encrypted and partial-frame samples are rejected. Unsupported edits fail with an actionable message; there is no re-encoding, compositing, effects, cross-track mixing, subtitle/caption preservation, or arbitrary-frame-accurate cut.

Output is staged in app cache before copying to a SAF document. Cancellation is checked while muxing and copying. A canceled/failed copy to the selected provider may leave an empty or partial destination document; provider deletion/atomic replacement is not assumed.

## Consequences and validation limits

- Compatible split/trim timelines can produce an MP4 without an external codec dependency or quality loss.
- Most arbitrary split points are not keyframes and will be rejected; no smart-render/transcode fallback exists.
- Container/codec combinations still depend on the device's Android extractor/muxer support.
- Planner instrumentation tests validate structural constraints only. Actual sample export, playback, A/V sync, cancellation, and format compatibility require physical-device testing.
- This is not the final shared render graph or general export acceptance gate.
