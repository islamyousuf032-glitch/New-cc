# Device test matrix

The Galaxy F23 row below records a user-reported manual smoke test. Other rows remain planned targets, and no independent device measurement or emulator instrumentation pass is claimed.

| Device / class | Coverage | Result / limits |
|---|---|---|
| Samsung Galaxy F23; Android 14; 6 GB RAM; Snapdragon 750G (user-reported) | User tried the app after the TextureView fix. Screenshot shows visible video from a 3840×1632, 9-second source and timeline UI; user says playback and features work. | **User smoke test reported successful** (2026-10-19). No independently captured codec details, A/V sync measurement, save/reopen, SAF project Save As/Open, export, lifecycle, thermal or frame-drop measurements. Do not infer universal 4K/codec support. |
| Low-memory phone | Lowest supported Android API; 4 GB RAM class | Planned: import, seek, surface recreation, storage exhaustion, background/foreground, thermal behavior |
| Mid-range phone | Current widely deployed API; 6–8 GB RAM | Planned: 1080p SDR decode/preview, A/V sync, trim/split, save/reopen, H.264/AAC export |
| High-end phone | Current API; modern GPU/codec | Planned: 1080p performance, dropped frames, export throughput, cancellation and thermal throttling |
| Tablet | Current API; large/adaptive display | Planned: landscape/adaptive UI, touch target/accessibility, surface and lifecycle behavior |

For each real device record manufacturer/model, SoC/GPU, Android build/API, ABI, codec capabilities, input sample provenance, project settings, measured frame time/dropped frames/RAM/temperature/export speed, and pass/fail artifacts. Test only advertised capability; do not infer universal 4K, 60 fps or HDR support. Add 16 KB page-size packaging/device coverage before native release.

## Initial phone smoke-test checklist

For new code builds, install the APK from the private `phone-test-*` prerelease (arm64-v8a and x86_64; verify the accompanying SHA-256 file). This is a manual debug build, not release acceptance.

1. **Completed by user (Galaxy F23):** user reports video playback and editing features work after the TextureView crash fix; supplied screenshot shows a visible frame from 3840×1632 media and timeline. This does not independently measure A/V sync, decoder support or playback performance.
2. Remaining playback validation: test a local 1080p SDR sample, pause/seek, adjacent clips and gaps; record audio sync and behavior after surface recreation. Avoid assuming universal 4K/HDR/codec support.
3. Test project Save As/Open from the Android Documents provider (included in `phone-test-39251452651`), plus app-private Save/Open saved and Recover autosave. Verify malformed or inaccessible project documents do not corrupt the current session.
4. For a moved or revoked source, select the affected timeline clip and use **মিডিয়া বদল**. Confirm a replacement with sufficient duration restores preview while retaining the source range; confirm a too-short replacement is rejected. After splitting one source into multiple clips, relink one segment and confirm all segments sharing that source update together; test undo/redo for the whole group.
5. Test rotation, background/foreground, low storage, revoked media permission, unsupported/corrupt media and lifecycle cleanup.
6. APK `phone-test-39270709637` includes the duration-rounding and explicit gap-compaction fix, but it still restricts export to one original source. Follow-up source now accepts multiple original URIs only when video/audio codec configurations and formats match, and probes the output MP4 plus decodes its first frame before copying. After a new APK is built, test one 6-second clip, three clips from the same source, and two or three compatible sources; verify Gallery thumbnail/playback, first/last frames, audio continuity, cancellation and failure cleanup. Also test mismatched resolution/codec rejection. Ask the user to attach the exported MP4 if Gallery still cannot create a thumbnail. Arbitrary-frame cuts, overlap, effects and re-encoding remain unsupported; this is not general export acceptance.

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
