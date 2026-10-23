# Color grading reference pipeline v1

## Phase 1B update — clip/project binding (2026-10-23)

Clip now owns a Grade and ProjectCodec v2 embeds the fixed preset payload for each
clip. The standalone codec contract below is unchanged, but it is also the bounded
project grade representation. Native grouped history/reset/paste/bypass, edit inheritance,
v0/v1 neutral migration and recovery are implemented/host-tested (ADR 0017). Undo is
session-local, not persisted. Exact native settings/activity flow through immutable
snapshots. ColorEngine remains a frame adapter; no current live preview/export effect.
Active grades are guarded from ungraded export until phase 1C parity is established.

2026-10-23: implementation/host-tested, not enabled in Android preview/export.

## Contract

Original C++20 module `engine/color`, standard library only. No proprietary source,
assets, LUTs or editor-specific algorithms copied. Domain math stays native; Kotlin
ColorGrade contains immutable values and ColorEngine exposes a stateless frame call.

Explicit input: **sRGB-encoded straight-alpha RGBA8**, Rec.709/sRGB RGB primaries/D65.
This is not YUV, BT.709 video-transfer decode, arbitrary Android bitmap layout, linear
RGBA, premultiplied alpha or HDR. A future video adapter must inspect/convert color
metadata, not assume all video uses sRGB transfer. Transfer constants follow standard
sRGB; negative values use a documented sign-preserving extension. This is not an
ICC/ACES/OCIO engine or a calibrated Kelvin white-balance implementation.

## Original operation order

For each channel, decode encoded sRGB with the standard 0.04045/12.92 breakpoint,
2.4 exponent and 0.055/1.055 constants. Working channels are linear-light:

1. `x = input * 2^(exposure_stops + channel_balance_stops)`.
2. `x = (x + lift * (1 - x)) * gain`.
3. `x = sign(x) * abs(x)^(1 / gamma)`.
4. `x = pivot + (x - pivot) * contrast`.
5. `Y = 0.2126 R + 0.7152 G + 0.0722 B`;
   `saturated = Y + saturation * (channel - Y)`.
6. `result = original + strength * (saturated - original)` in linear light.
7. Encode sRGB using linear breakpoint 0.0031308 and reciprocal 2.4 exponent.
   Clamp only when packing SDR bytes; round half-up to nearest 8-bit integer.

RGB balance is per-channel stops, not temperature/tint or chromatic adaptation.
Identity/bypass/zero strength has an exact active-byte copy path. Alpha is copied
without change, including zero alpha; hidden RGB can be graded in non-identity mode.
Floating reference `Pipeline::linear` retains negative/highlight values and bounds
input to +/-65504; this alone is not HDR support. The CPU reference is not presented
as a realtime 4K solution. GPU parity and performance are still acceptance work.

## Settings and buffer safety

See color.hpp for all finite ranges: exposure [-8,8], contrast/saturation [0,4],
pivot [0.001,1], strength [0,1], channel balance [-2,2], lift [-1,1], gamma [0.1,4],
gain [0,4]. Invalid values are rejected even when bypassed, never silently clamped.

Dimensions must be positive, total pixels <= 64*1024*1024, stride >= width*4 and spans
cover every active row without arithmetic overflow. This safety budget is not a device
resolution/performance claim. Source/output extents must not overlap, even in place.
All structural/settings checks precede output writes. Input and padding never change.
Optional native cancellation is cooperative: false means discard a possibly partial
destination. No transactional output guarantee on cancellation; source remains intact.

JNI nativeRenderV1 uses a fixed 17-double block: exposure, contrast, saturation, pivot,
strength, RGB balance, RGB lift, RGB gamma, RGB gain; enabled is separate. Buffers must
be direct byte buffers, position=0, limit=capacity, output writable. Use full-capacity
slices for regions; native validates these conditions and overlap. Calls belong on a
worker; JNI does not schedule or insert grading into the current player/Transformer.

## Preset format

Standalone preset, **not** project persistence. `OWNCUTCG` (8 bytes), uint32 LE version
1, byte enabled (0/1), then 17 IEEE-754 binary64 LE values in the field order above:
**149 bytes total**. No struct padding, trailing bytes, NaN/Inf or unsupported versions.
Malformed/truncated input is rejected; unsupported versions use UnsupportedGradeVersion.
Phase 1B now embeds this payload in project/autosave v2; old v0/v1 migrate neutral.
The standalone preset remains v1. See ADR 0017 for project binding and history.

## Histogram

Native RGB channels are encoded 8-bit bins. Luminance bins encode linear Rec.709
luminance back to sRGB. By default fully transparent pixels are excluded; other alpha
values count once, not alpha-weighted. This is not a broadcast waveform, vectorscope,
calibrated nits display or HDR scope.

## Evidence and remaining acceptance

Unit tests: transfer vectors, all 17 parameter ranges/nonfinite rejection, controls,
exact identity bytes, known +1-stop pixel vector (128 -> 176), alpha/source/padding,
stride/size/overlap/overflow defenses, cancellation, row-tile equivalence, histograms,
preset round trips/truncation/future versions and randomized extreme settings.

Real JVM -Xcheck:jni tests native buffer handling. Additionally app-pinned Kotlin 2.3.21
compiles the **production ColorEngine.kt** and calls production JNI/C++ on a host JVM,
checking real pixels, 17-field order, bypass, read-only/alias/invalid-buffer rejection.
This is stronger than fixture-only JNI tests but **not Android/ART/GPU/codec testing**.

Remaining acceptance: Android managed persistence/recovery, color metadata,
shared preview/export GPU adapter, live controls/before-after, curves/LUT/secondary tools,
scopes UI and actual Android build/device/encoded-video parity. No grading APK delivered.
