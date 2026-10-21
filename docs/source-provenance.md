# Source, APK provenance and rebuilding

## What is included

The Android directory contains the **actual editable project files**: 20 Java source files, two AIDL interfaces, the manifest and resources, Gradle configuration, the pinned Gradle wrapper, and standalone policy tests. These are the source of truth and are more complete than a decompiled APK.

The recovered source was taken from the maintained Phone MCP working project, including the final 0.8.2 preview changes. Historical Codex development records and versioned delivery files were used to establish the sequence from the original relay/notification prototype through separate displays, file jobs, gestures, observations and the local preview. Private transcripts, local paths, account IDs, pairing files and deployment credentials are intentionally not redistributed.

The public server adds native desktop loopback OAuth callback support and a callback-specific consent form policy. Publication helpers, English documentation, local test orchestration and the portable `cloudflared` executable lookup are also new. These changes do not modify the Android 0.8.2 source or the released APK. The existing component package versions are retained; Android, relay runtime and Node package metadata are independent version fields.

## APK

| Field | Value |
|---|---|
| Application ID | `fr.phonemcp` |
| Version | `0.8.2` |
| Version code | `18` |
| Minimum / target SDK | `30` / `36` |
| File size | `3,242,914` bytes |
| SHA-256 | `21c35404d8ba9fcbff71380f0683f47e0cec9fc43cf5445959087842450a19d0` |
| Signing certificate SHA-256 | `a819c6ef90efe5809d48d41e02c741168b744d1705fa7ab53313e02e5295babe` |
| Signature | Android debug certificate, APK Signature Scheme v2 |

The released binary is the previously delivered final 0.8.2 APK. A fresh build from the recovered Android sources succeeded. All **15 non-signature ZIP entries**, including DEX and packaged resources, matched the delivered APK byte-for-byte. The full rebuilt file hash differs because of packaging/signature metadata; no claim of an identical full-file rebuild is made. `releases/apk-validation.json` records both hashes and the comparison result.

`apksigner verify` and `zipalign -c -P 16 4` passed. Android lint produced zero errors and five warnings. The build uses JDK 17, SDK 36, Build Tools 36.0.0, AGP 8.10.1 and Gradle 8.13.

## Decompiled inspection package

[Download the app-owned decompiled Java package](https://github.com/Mirochill/Phone-MCP/releases/download/v0.8.2/Phone-MCP-0.8.2-decompiled-app.zip).

This additional archive was produced from the released APK with JADX 1.5.6 (`--no-res --show-bad-code`). It includes the generated `fr.phonemcp` namespace, including compiler-generated interfaces/classes. Third-party library implementations are excluded; use the pinned dependency declarations and their upstream sources/licenses instead.

JADX reported nine errors while processing the complete APK. Decompiled Java is approximate and may contain incomplete methods, synthetic constructs and missing source-level information. It is provided for inspection, **not as a replacement for the complete original project**. It is not promised to compile. Original resources and AIDL are available directly in `android/app/src/main`; Android cannot recover source comments or original build configuration from a binary.

## Rebuild

Set `ANDROID_HOME` or an untracked `android/local.properties` pointing to your SDK. Run:

```sh
cd android
./gradlew --no-daemon assembleRelease lintRelease
```

On Windows use `.\gradlew.bat`. The Gradle distribution checksum is pinned in `gradle/wrapper/gradle-wrapper.properties`. Install the exact Platform 36 and Build Tools 36.0.0 if missing. Use `npm ci` for both Node packages; do not upgrade locked dependencies merely to reproduce a release.

The supplied Android release configuration intentionally retains the preview's debug signer. Your machine will normally generate a different local debug key. To maintain update continuity for your own distribution, configure a dedicated private release key outside Git and keep it backed up. Never publish a keystore or password. An Android build signed with a different certificate cannot update the released preview in place.

The project is MIT-licensed; dependency licenses remain applicable. The launch music is generated media with separate provenance, not software licensed by an upstream dependency. See [media credits](media/credits.md).
