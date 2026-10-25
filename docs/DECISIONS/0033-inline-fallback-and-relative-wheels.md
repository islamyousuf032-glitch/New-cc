# ADR 0033 — Inline guarded video fallback and relative wheel interaction

Date: 2026-10-25. Status: implemented candidate; Android/device verification pending.

## Evidence and problem
The user reports Play draft now works and the UI improved on de4bad3. Their screenshot
still shows REFERENCE ONLY and a truncated IllegalArgumentException in the editor.
A null platform still frame is plausible, not a measured diagnosis. No original media
or HDR payload was measured. Fix the viewer path rather than instructing the user to
trust controls applied to an ungraded reference.

## Decision
Keep the bounded CPU sRGB still path where available. If extraction/admission fails
after actual source probing, attempt a TextureView inline player using the same
GradedPreviewRenderers/GradedFramePipeline as the user-confirmed draft. Metadata identity,
geometry, native source policy, decoder raw per-buffer gates and explicit SDR graph stay
mandatory. No synthetic ColorInfo, tone-map workaround, private replay cache, reference
bitmap re-interpretation or policy bypass. Source-probe failure remains terminal.
Only a first rendered frame hides the reference image; failed fallback exposes Details
and Refresh retry. Live button plays/pauses inline when available. Before disables grading
without removing the guarded graph. Effect replacement follows the public Media3 API.
Paused changes request decoding around a fixed clip-relative anchor (+/-1 ms bounded to
clip), not timeline edits or a claim of exact frame-cache replay. Phone validation of
paused seek re-render is essential. Live controls debounce 66ms, not a measured frame rate.
Dialog close releases the player; activity stop releases codec/audio; resume recreates
paused, never automatically restarting audio. This does not enable normal timeline grading
or graded export, and is not proof of an end-to-end 10-bit path.

Wheels now use relative pointer displacement at 0.55 gain with unit-disc clamping:
touch-down does not change grade, movement is relative to the saved puck, release/cancel
stops immediately and extra pointers cancel rather than switching coordinates. C++ still
owns wheel-to-RGB math. No grading sliders, typing requirement or parameter momentum.
Press halo/tab fade honor disabled system animation. Flat numeric stepping/buttons and
short display labels reduce chrome; complete range names remain accessibility labels.
Original implementation; no proprietary code, assets or exact interface reproduction.

## Evidence boundary / acceptance
Actual local pinned Kotlin -Werror/JVM/JNI tests pass relative geometry, wheel/HDR policy
and existing adapters. Resource checks pass335 strings/3themes/17labels. Android UI source
adds tap/no-jump/cancel coverage; compilation/execution not yet established for candidate.
Phone: check still-failure fallback, visible exposure change paused and playing, Before,
Refresh after decoder failure, background/resume paused, dismiss during prepare, rotations,
small landscape, system animation-off, multi-touch cancellation and error Details.
Premium feel remains user acceptance, not a test assertion. Readiness stays25%/75%.
No new dependency or license change; Media3 remains pinned1.8.1. See existing dependency review.
