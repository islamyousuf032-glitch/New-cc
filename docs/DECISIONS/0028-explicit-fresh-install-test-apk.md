# ADR0028: Owner-authorized fresh-install test candidate

Date: 2026-10-24. Status: authorized/source implemented; publication pending.

User explicitly waived the prior install-over requirement for this test and will uninstall
and freshly install. Preserve the stable update path and its approved-signer guards; add a
separate debug fresh-install publication route. Android still requires an APK signature:
use the existing runner-generated debug signer, not an unsigned APK or a claimed legacy
compatible update. No private key is published or requested from the user.

Only a successful push on the tracked branch with explicit [fresh-install-apk] commit
marker and absent stable signing can publish this candidate. No automatic fallback on
ordinary pushes/PRs. APK assembly/lint/test-source compilation/native regression and ZIP
alignment remain mandatory. Verify actual APK signature, package, version and debuggable
flag before separate fresh-install-<run> prerelease publication. Include exact source SHA,
APK/certificate SHA256, temporary signer/no guaranteed update and unrun device-test flags
in fresh-install-metadata.json, not update-metadata.json. Eight mock policy tests pass.

Warn to Save As/back up projects and preserve media before uninstall; uninstall can remove
app-local projects/settings. Agent does not uninstall or clear any phone. Future signer
continuity not promised. User owns phone acceptance; grading/export shipping guards stay.
No device/codec/parity or completed-feature claim. Stable-key setup is not required for
this explicitly accepted fresh install, but remains necessary for guaranteed updates.
