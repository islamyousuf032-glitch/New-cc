# Color workspace redesign — implementation plan (2026-10-25)

## Problem and boundaries

Phone report: blank viewer, dated controls, inconsistent dialog layout. No new screenshot
is attached to this request yet. Do not invent visual evidence or infer exact metadata.
Original design, professional workflow inspiration only; no proprietary UI/code/assets.

## Hierarchy

1. Full-window themed grade workspace, clear title/reset and persistent Apply/Cancel.
2. Fixed viewer with source-time caption, loading/empty/reference/graded states. Controls
   scroll independently, never drag the image out of view. Landscape uses two columns.
3. A short status and explicit Details action; long diagnostics cannot consume the viewer.
4. Before/refresh/live toolbar, one enable switch, segmented Primary / Balance / Tone tabs.
5. Card-like parameter rows: label, mono readout, ruler scrubber with neutral marker,
   channel accent, fine −/+ touch targets. Consistent shared StudioUi palette/spacing.
6. Preserve accessibility via SeekBar semantics and labelled 48dp fine-step actions.

## Blank-frame strategy

A metadata rejection currently happens before frame extraction, leaving the image blank.
Separate platform-reference display from native grading admission. Show a bounded paused
main-player reference immediately when same source/position is selected and ready (label
as ungraded), then attempt independent bounded source-frame extraction. Display decoded
reference before color admission. Only known admitted source + correct bitmap format may
enter native grading. Unknown/HDR remain blocked for grading/live/export. Reference is
NOT graded output, source-color proof, or live video. With no frame, show an actual empty
state and cause, not a black rectangle. Do not manufacture a sample image.

## Validation

Keep single in-flight/debounced worker and stale-revision gates. Verify reference never
enters native render input. Compile interaction/layout regression source. Native/host
checks and Android assembly/lint/identity/alignment before APK publication. Device visual,
touch/scroll/keyboard/TalkBack, landscape/insets and source behavior remain user acceptance.
No readiness/export/phone-parity credit until those gates pass.

## Implementation checkpoint

Implemented at57879db. Android40165432408 and Portable40165432394 PASS; fresh-install
candidate40165432408 published. Tests compile, device execution pending. See ADR0031 and
PROGRESS.md for artifact/source/digest and explicit reference/graded-state limitations.

## Superseding interaction choice (2026-10-25)

User explicitly rejects sliders. ADR0032 supersedes ruler design with four original
native-mapped wheels and relative drag-number controls. Candidate40199335976 is published;
UI regression compiles, phone acceptance pending. No third-party decompiled material used.


### Inline viewer candidate (2026-10-25)

See ADR0033: source-probed guarded video fallback, relative no-jump wheels, lifecycle
release/recreate-paused and compact feedback. No dependencies or safety-gate relaxation.
User confirms previous Play draft works, not this new inline path. Local geometry/JNI
checks pass; Android/phone acceptance pending. Readiness25%/75%; no10-bit export proof.
