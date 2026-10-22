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
- Alignment, output checksum and public `update-metadata.json` accompany releases named
  `app-patch-<run>`. Public metadata does not expose a private key or password.

Debug-to-debug install-over tests on a runner without secrets prove the installation
mechanism/data retention in that job only. They do **not** prove cross-run signer
continuity or compatibility with the user's currently installed legacy APK. Stable-key
release verification remains blocked until owner configuration is supplied. This is
not yet a Play Store/production signing rollout or a delta patch distribution service.
