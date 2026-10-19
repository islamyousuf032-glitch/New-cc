# Device test matrix

No Android device or emulator is currently attached; every row below is a planned validation target, not a tested claim.

| Class | Minimum coverage | Planned checks |
|---|---|---|
| Low-memory phone | Lowest supported Android API; 4 GB RAM class | Import, seek, surface recreation, storage exhaustion, background/foreground, thermal behavior |
| Mid-range phone | Current widely deployed API; 6–8 GB RAM | 1080p SDR decode/preview, A/V sync, trim/split, save/reopen, H.264/AAC export |
| High-end phone | Current API; modern GPU/codec | 1080p performance, dropped frames, export throughput, cancellation and thermal throttling |
| Tablet | Current API; large/adaptive display | Landscape/adaptive UI, touch target/accessibility, surface and lifecycle behavior |

For each real device record manufacturer/model, SoC/GPU, Android build/API, ABI, codec capabilities, input sample provenance, project settings, measured frame time/dropped frames/RAM/temperature/export speed, and pass/fail artifacts. Test only advertised capability; do not infer universal 4K, 60 fps or HDR support. Add 16 KB page-size packaging/device coverage before native release.

## Initial phone smoke-test checklist

After a successful build stage on the fixed Arena branch, install the APK from the private `phone-test-*` prerelease (arm64-v8a and x86_64; verify the accompanying SHA-256 file). If Actions artifact storage is available, the same APK is also attached as `owncut-debug-android-test`. This is an early-shell test, not release acceptance.

1. Start with a locally accessible 1080p SDR video from the system picker. Confirm metadata import, tap **ভিডিও চালান**, wait for the decoder-frame status, then test pause and seek. If the preview stays black, note the on-screen status after 3 seconds and confirm whether the same file plays in the phone's native Gallery. The user reports audio with black video for both 1920×1080 and 3840×1632 sources. Test those separately after the TextureView APK is available; do not assume 4K/HDR/codec support.
2. Confirm a proportional clip block appears in its track row; select it and verify the playhead and preview follow the selected source range, stop at source-out, and auto-advance only across adjacent clips. Use the Bengali edit hint: **বাম ছাঁটুন** removes the part before the playhead, **ডান ছাঁটুন** removes the part after it, and **কাটুন (Split)** makes two clips. Try delete, gap removal, undo and redo; a timeline gap should stop this preliminary preview rather than render black. Check that invalid cut/trim positions show an actionable message rather than crashing.
3. Save, leave the Activity, relaunch and use **Open saved**; separately edit and use **Recover autosave**. Confirm replacement/recovery prompts appear before state changes.
4. Rotate the screen and background/foreground the Activity; check that the native editing session and selected clip survive configuration changes and the preview can reattach.
5. Test a video whose provider grants only temporary access, an unsupported/corrupt file, and a revoked URI; record observed errors. Do not expect edited-timeline preview or export yet—those features are not implemented.
6. Attach logs and the device details above, noting each action/result and any crash. This smoke test does not establish full codec compatibility, A/V sync, 16 KB page-size support, or export correctness.
