# Install-over APK patches: signing and verification

An app patch here is a **full signed APK update**, not a delta or an AAB. Android
requires the same package (`com.owncut.app`), a compatible signing identity and a
non-decreasing versionCode. CI assigns `1000 + GITHUB_RUN_NUMBER`; every new run number
increases it. Re-running a job uses the same version, not a fictitious newer patch.

## Legacy limitation — do not uninstall to work around it

Previous APKs used runner-generated debug signing and versionCode 1. No durable
private signing key was configured in the repository. The private key cannot be
recovered from an APK, public certificate or SHA checksum. If the original key was
retained elsewhere, reuse it; otherwise a new key cannot silently update those old
installations. Existing app/project data must be backed up through Save As and media
retained before any deliberate migration; this work does not authorize uninstall or
data clearing. There is no honest universal install-over fix for a lost signer.

This sandbox's GitHub integration returned HTTP 403 for repository secret management.
That is a permissions limitation, not a request for passwords/tokens in chat. No key
was generated and uploaded to Git, releases, artifacts or this conversation.

## One-time owner setup (on a trusted computer, never in chat)

1. Locate and securely back up the original key if it exists. Do not generate a new
   identity just to bypass a signature mismatch. If starting a new update lineage,
   record explicitly that legacy random-key installations may not be compatible.
2. If a new lineage is deliberately chosen, JDK keytool can create a persistent key:
   `keytool -genkeypair -alias owncut-update -keyalg RSA -keysize 3072 -validity 10000 -storetype JKS -keystore /secure/location/owncut-update.jks`
   It prompts locally for passwords. Keep an encrypted offline backup and recovery
   instructions. This is private signing material; anyone holding it can sign updates.
3. In repository **Settings → Secrets and variables → Actions**, create secrets:
   - `OWNCUT_KEYSTORE_BASE64`: base64 of the keystore bytes, no wrapped newlines.
   - `OWNCUT_KEYSTORE_PASSWORD`
   - `OWNCUT_KEY_ALIAS`
   - `OWNCUT_KEY_PASSWORD`
   Use a local temporary base64 file if needed and delete it after secure upload;
   never paste any of these into chat, issues, PRs, source or release notes.
4. Create repository variable `OWNCUT_SIGNING_CERT_SHA256`, the public certificate's
   SHA-256 fingerprint (hex, optional colons). Obtain it with keytool's `-list -v` for
   the chosen alias. Do not confuse certificate SHA-256 with APK SHA-256.
5. Restrict access to signing secrets/workflows, review code before signing, and
   consider protected-environment approval before production distribution. Do not
   rotate/replace the key or pin casually: future install-over continuity depends on it.
   The pipeline also checks against approved `phone-test-39777165014`: generating a
   new key plus matching pin cannot bypass that identity. Any deliberately different
   lineage needs a separately agreed migration and explicit reference-policy change.

## Pipeline behavior

- No complete signing setup: resource/host/Android tests still run, **no downloadable
  APK is published** (including debug test artifacts).
- With setup: temporary keystore in runner temp, never source/artifact; shared signing
  for test/debug and non-debuggable release variants. Delete temp key even on failure.
- Full emulator regression, then install baseline version N−1, seed a real native
  project and actual app language/theme preferences, install N using `adb install -r`,
  and assert those survive. Initial uninstall is **only on the disposable CI emulator
  before seeding**, never between baseline and update, never on the user's phone.
- A configured release also installs over that tested debug variant (same certificate)
  and repeats persistence assertions. Signature verification pins the certificate,
  verifies actual manifest package/version and rejects debuggable release output.
  It additionally verifies signer/package/version against the previously approved
  phone-test APK. New key+pin alone is not legacy install-over compatibility.
- Alignment, output checksum and public `update-metadata.json` accompany releases named
  `app-patch-<run>`. Public metadata does not expose a private key or password.

Debug-to-debug install-over tests on a runner without secrets prove the installation
mechanism/data retention in that job only. They do **not** prove cross-run signer
continuity or compatibility with the user's currently installed legacy APK. Stable-key
release verification remains blocked until owner configuration is supplied. This is
not yet a Play Store/production signing rollout or a delta patch distribution service.

## Actual checkpoint status (not an install-over success claim)

Android 39796552264 at 8a6aab4: build/lint/alignment and full emulator suite passed;
separate N−1→N install-over step failed. The first diagnostic exceeded GitHub's
10-error-per-step annotation limit. A single escaped full-context annotation now
replaces that output, but Android 39799182223 at a1bb412 could not start: account
payment/spending limit failure. Owner must resolve GitHub Settings → Billing & plans
(or Billing & licensing in newer UI) before rerunning. This is distinct from signing
secrets permissions/configuration. No successful real install-over test or signed
release verification is claimed for this batch; no new patch is published.

The extracted test helper refuses outside GitHub Actions and refuses any physical or
multiple-device target before reset. Wrapper waits for log tee completion and emits
one diagnostic annotation, avoiding partial output/annotation truncation. Host guard
and verifier tests pass, including signer mismatch with the approved legacy APK.
Workflow concurrency supersedes older runs on the same ref; publication rejects
reruns not newer than the most recently published app-patch version. These latest
Android-facing changes still require an actual CI run once billing is restored.
