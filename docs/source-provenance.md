# Source, binary provenance and signing

## Current source and APK: 0.19.0

The maintained editable Android Java/AIDL/resources, shared MCP registry and Node/Cloudflare relay source in tag `v0.19.0` correspond to this release. Public native-loopback OAuth support, consent CSP, portable tunnel lookup, example configuration and CI runners are retained. Private secrets, endpoint/account configuration, pairing links, runtime state, raw device transcripts and signing keys are excluded.

| Field | Value |
|---|---|
| Package | `fr.phonemcp` |
| Version / code | `0.19.0` / `69` |
| APK bytes | `3651555` |
| APK SHA-256 | `5be0cec79eab589aa1f9d2545659c860f37427505b0c57d532637c84b66cf06f` |
| Signing certificate SHA-256 | `a819c6ef90efe5809d48d41e02c741168b744d1705fa7ab53313e02e5295babe` |
| MCP inventory | 66 unique tools / 62 operations, API 1 |
| Catalog | `9536208318cf0507` |

This is the same developer-signed APK installed during the 0.19.0 S24 validation. It is a preview outside Google Play. The certificate is unchanged from the previous distributed companion. [Release manifest](../releases/0.19.0/Release-0.19.0.json) · [Checksums](../releases/0.19.0/SHA256SUMS.txt) · [Publication checks](../releases/0.19.0/Publication-checks.json) · [Scenario-specific real-device evidence](verification-0.19.0.json).

Publication checks compare Android source against the maintained build project, inspect the APK's package/version/signature/alignment, and compare its non-signature ZIP contents with the public-source rebuild. This does not promise an identical signed rebuild on another machine. No pinned dependency or physical-screen/audio choice was changed for 0.19.0. The relay's public OAuth/tunnel adaptations are deliberately retained rather than replaced with private deployment code.

## Rebuild

Use Node.js 22+, JDK 17, Android SDK Platform 36 and Build Tools 36.0.0. AGP 8.10.1 and Gradle 8.13 remain pinned. Install locked relay dependencies with `npm ci --prefix server` and `npm ci --prefix cloudflare`. Set `ANDROID_HOME` or an ignored `android/local.properties` to your SDK.

```sh
cd android
./gradlew --no-daemon assembleRelease lintRelease
```

On Windows use `.\gradlew.bat`. `scripts/Build-Release.ps1` also verifies signature, alignment and manifest metadata; `-SkipUpdateAssets` leaves paired publication assets untouched. The preview build configuration uses your machine's local debug signer, which normally differs from the distributed certificate. Keep your own signing key backed up privately. Do not uninstall an existing companion or erase its data merely to work around a certificate mismatch.

For your own deployment, `node scripts/prepare-update-assets.mjs` verifies/copies the release APK and manifest into ignored Worker assets. Preserve your private config, existing secrets, OAuth state, migrations and bindings. A public GitHub release does not deploy your Worker or refresh ChatGPT; follow [the update guide](updating.md).

## Historical artifacts and evidence

0.16.2 introduced the validated preview recording; [its manifest](../releases/0.16.2/Release-0.16.2.json) and [publication checks](../releases/0.16.2/Publication-checks.json) remain specific to that version. 0.17.0 was a relay-only release. 0.18.1 introduced the conditional runner; 0.18.2 added bounded relay recapture and 0.18.3 accessible-square actions. Their dated verification records are kept in [verification](verification.md); they do not represent additional 0.19.0 hardware tests.

The old 0.8.2 APK, compiled Worker and approximate JADX inspection archive under `releases/` are historical, not the current buildable source. No new decompiled archive is needed: the editable original source is included. MIT covers the project software; dependency licenses, [third-party notices](../THIRD_PARTY_NOTICES.md) and [media provenance](media/credits.md) remain applicable.
