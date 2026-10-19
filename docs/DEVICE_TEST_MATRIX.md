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
3. Test project Save As/Open from the Android Documents provider (new SAF flow pending build/device validation), plus app-private Save/Open saved and Recover autosave. Verify malformed or inaccessible project documents do not corrupt the current session.
4. For a moved or revoked source, select the affected timeline clip and use **মিডিয়া বদল**. Confirm a replacement with sufficient duration restores preview while retaining the source range; confirm a too-short replacement is rejected. After splitting one source into multiple clips, relink each segment and test undo/redo.
5. Test rotation, background/foreground, low storage, revoked media permission, unsupported/corrupt media and lifecycle cleanup.
6. Edited-timeline export does not exist yet; do not expect a rendered output. Record device/model/API, media provenance and observed results. This smoke test does not establish full codec compatibility, A/V sync, 16 KB page-size device support, or export correctness.
