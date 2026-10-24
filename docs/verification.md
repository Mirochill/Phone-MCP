# Verification and remaining limits

Evidence is scoped to the version and scenario actually tested. Successful builds, accepted tool calls, visible app effects and battery improvements are distinct claims.

## 0.17.0 relay performance update

The companion remains Android 0.16.2; no installation or Android source change is part of this relay update. The new owned launch, semantic flow, partial failure and preserved state were exercised on the real S24. A real Chess.com analysis-board move used one two-tap sequence and final full-quality image. See [the measured results](performance.md) and [sanitized timing data](performance-verification-0.17.0.json). The later client trials include model/host time, including unsuccessful final-message rendering, and do not establish faster full games or universal ad completion. The final local suites passed 113 server and 19 Cloudflare checks; the imported ChatGPT inventory was read back as 42 write + 20 read tools.

## 0.16.2 capture release — S24 Ultra / Android 16

| Check | Evidence |
|---|---|
| Distributed APK | `fr.phonemcp`, version 0.16.2, code 65, 3,620,683 bytes; SHA-256 in the release manifest. Signature/alignment/build metadata verified. |
| PNG | Source AI display only, 720×1280, 100,749 bytes. First hardware check on 0.16.0; final image-writing code unchanged. User confirmed correct content. |
| Final silent MP4 | 0.16.2: 148 encoded samples / 2,459 ms / 936,662 bytes and 562 samples / 9,508 ms / 3,884,025 bytes; both 720×1280, no audio. |
| Physical-screen isolation | User confirmed animated video and image contained only the AI app, without viewer controls or physical-screen content. Recording continued while the user was on the physical home screen. |
| Cleanup | Final diagnostics: 0 active recordings, 0 pending images, 0 own test sessions; no local-recording wakelock. |
| Local regression tests in capture development | 26 actual CaptureMirror checks, 16 PendingMedia checks, 27 screen-controller checks; release/lint/signature/alignment checks passed, 0 lint errors / 35 warnings. |

The initial 0.16.0/0.16.1 encoder had no frames and removed empty outputs. The final version fixes the mirrored layer's compositor root. These short recordings are functional checks, not sustained frame-rate, battery or thermal measurements. The exact in-progress session-end auto-finalization path remains unverified on the phone: the session was ended after the observed recording had already stopped.

The sanitized [release verification JSON](../releases/0.16.2/Verification-0.16.2.json) contains sizes, timing and test results. Raw local media URIs and device-session identities are not needed for public use.

## Earlier device regressions retained by current source

- 0.15.4: user confirmed twice that the physical panel stayed black through agent gestures/display recovery, and one side-button press restored it normally. Keep-on was disabled locally; remote enabling was rejected.
- 0.15.5: user confirmed Samsung hardware-button screenshots from calculator and Chess previews, via See app and Recents. The original intermittent refusal was not reproduced universally.
- 0.15.3: repeated captures of an unchanged bot Chess.com board after an idle pause succeeded through the owned source surface. The bot board accepted a real two-tap move. The black-board report in an online-human game was not reproduced; no human match was started for diagnosis.
- 0.15.1/0.15.3: real Macadam ads returned through two Skip controls and one Close in 104.402 s / 114.947 s. First Skip appeared only after 93.429 s / 98.932 s. App-imposed waits were not bypassed. Those observations do not prove universal ad completion or cash rewards.
- 0.12–0.13: independent same-app neutral windows retained distinct state; concurrent requests stayed on distinct displays; an observed graphical action received genuine DOWN/UP events; unrelated animated layout no longer invalidated a stable target.
- Local handoff retained calculator 15, rejected the old session and allowed a fresh same-app session. Explicit Stop remains a different local revocation.

These are historical measured checks, not all repeated for a documentation/publication update. The historical 0.8.2 [device record](live-device-verification.json) and launch media remain labeled as such.

## Checks for this GitHub synchronization

See [publication checks](../releases/0.16.2/Publication-checks.json) for the exact suites and source/APK checks rerun on the public tree. Its Android source must match the maintained 0.16.2 snapshot; public relay-specific native OAuth/tunnel support is preserved. The full file hash of a rebuild can differ with its local signing key; do not infer an identical signed binary from build success.

## Not established

- Universal screen-off interaction, independent navigation or rendering for every Android/OEM/app.
- Universal removal of partly black app content or source-imposed screenshot restrictions.
- Instant rewarded-ad completion, every ad-network close path, or rewards from a return event.
- Every client plan's MCP availability, all host attachment-binding flows, or forced refresh of an existing ChatGPT conversation.
- Measured battery savings, long-duration recording stability, sustained throughput/frame rate, or a controlled energy before/after comparison. The older Samsung battery history is observational and not causal evidence.
- Uninterrupted full-APK updates; Android restarts the app and its sessions. No abandoned control context is silently resumed.
