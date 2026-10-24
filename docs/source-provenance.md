# Source, binary provenance and signing

## Current source

The repository contains the maintained original Android 0.16.2 Java/AIDL/resources and build configuration, shared MCP operation registry, local relay and Cloudflare relay. Public deployment helpers, native desktop OAuth redirect support, portable tunnel lookup and public documentation are retained. Private working copies, device transcripts, tokens, QR codes, account/deployment identifiers and signing keys are excluded.

Android source correspondence is checked by hashes against the maintained release snapshot. The public relay retains its prior native-loopback OAuth adaptation, separate from the Android APK. Tests and complete MCP tool schemas are generated/run against the actual public source. Publishing this tree does not deploy a user's relay or change an installed client's cache.

Relay 0.17.0 adds server-side fast workflows, structured board evidence and algebraic moves; Android source and the distributed APK remain 0.16.2. The [performance record](performance.md) separates relay, client-turn and hardware evidence.

## Distributed APK 0.16.2

| Field | Value |
|---|---|
| Package | `fr.phonemcp` |
| Version / code | `0.16.2` / `65` |
| Bytes | `3620683` |
| SHA-256 | `fe751fb2aba57c2e6ecd627a82c1674ea779935f674da2fe3ad314bf769d3233` |
| Original source | `android/` at tag `v0.16.2` |
| Checks | Release build/lint, APK signature, zip alignment and manifest metadata |

The APK is the same verified developer-signed build installed during the S24 capture validation. It is not a Play Store release. [Manifest](../releases/0.16.2/Release-0.16.2.json) · [Checksums](../releases/0.16.2/SHA256SUMS.txt).

## Rebuild

JDK 17, Android SDK Platform 36, Build Tools 36.0.0, AGP 8.10.1 and Gradle 8.13 are pinned. Set `ANDROID_HOME` or an untracked `android/local.properties` to your SDK. Run:

```sh
cd android
./gradlew --no-daemon assembleRelease lintRelease
```

On Windows use `.\gradlew.bat`. The Windows `scripts/Build-Release.ps1` additionally verifies signature, alignment and manifest metadata. `-SkipUpdateAssets` leaves paired publication assets untouched. Install locked Node dependencies with `npm ci --prefix server` and `npm ci --prefix cloudflare`.

The preview build configuration uses your machine's local debug signer. It normally differs from the distributed key; a different certificate cannot update an existing app in place. Maintain a private backed-up signing key for your own distribution. Never commit keys or passwords. A successful rebuild does not promise an identical signed file hash. See publication checks for non-signature ZIP-entry comparison if available.

## Historical assets

The old 0.8.2 APK, compiled Worker and JADX inspection archive under `releases/` and [v0.8.2](https://github.com/Mirochill/Phone-MCP/releases/tag/v0.8.2) are historical. The decompiled archive is approximate JADX output with documented errors; it is not current source or a buildable replacement. Current `android/` is the editable original source of truth.

The MIT license applies to project software; dependency licenses and [media provenance](media/credits.md) remain applicable. No fresh decompiled archive is needed to build the current source.
