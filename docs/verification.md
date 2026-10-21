# Verification and known limits

Verification date: **21 October 2026**. This is a developer preview, with separate evidence for source builds, protocol tests, previous device testing and the current launch preparation.

## Current publication checks

| Check | Evidence |
|---|---|
| Original Android source builds | `assembleRelease` and `lintRelease` succeeded using the pinned toolchain. |
| Delivered APK matches source build | All 15 non-signature ZIP entries match the delivered 0.8.2 binary; signature and alignment verified. |
| Android static analysis | 0 errors, 5 warnings. |
| Pure Java policy tests | 195 assertions across access permissions, files, session duration, window selection and separate-display task ownership. |
| Local relay tests | 39 Node tests, including real MCP SDK transport, WebSocket relay, OAuth/PKCE, scopes, persistence, cancellation/timeout behavior and native loopback redirects. |
| Cloudflare runtime tests | 9 integration tests plus 1 native-client OAuth test in fresh local Wrangler/SQLite Durable Object runtimes. Storage is isolated per test file so production rate limits remain enforced. |
| Production dependency audit | `npm audit --omit=dev` reported zero known vulnerabilities at the time of this check; this is not a security certification. |
| Current real phone | Phone MCP 0.8.2 connected on a Samsung S24 Ultra, Android 16; files ready and the parallel display ready. |
| Current real action | In Codex through the Phone MCP plugin: opened the Samsung Calculator, read its nodes, entered `137×24`, clicked equals, and read/captured `3 288` on separate display 36. |

The calculator's equals action and returned observation took 309 ms on the device in this single sample. This excludes model reasoning, host UI and general network/conversation time; it is not a benchmark or service guarantee. The physical screen was reported non-interactive and locked while the verified independent display remained usable. See [`live-device-verification.json`](live-device-verification.json) and the real calculator screenshot.

## Historical device validation used for release provenance

The 0.8.2 delivery record reports build/lint, task ownership/revocation checks and 28 checks on Android 16 API 36.1, including SurfaceControl attachment/removal, task reclamation, preview expiry, protected fields and AI interaction while a hardware mirror was attached. These detailed Android instrumentation checks were performed in the prior development session; they were not all rerun as part of this publication pass.

The released preview was installed and read back on the S24 Ultra in that session. Its read-only preview and AI commands were exercised. On that Samsung the observed preview transport was `frames`; hardware mirroring was demonstrated on the emulator, not established on the Samsung. No frame-rate measurement was recorded.

Earlier versions include real-device tests of gestures, app/window selection and file downloads, plus emulator file/install checks. These establish development history, not a claim that every app, notification type or APK install was retested for this launch.

## Client evidence

- The current Phone MCP plugin was actually called from this Codex conversation, and its results were visible to the agent. Both plugin tools and a separately authenticated official MCP SDK client discovered the live server.
- The original ChatGPT installation was connected through OAuth. An early October 15 session had a visible-response rendering failure; a server HTTP 200 at that time did not establish a working ChatGPT UI.
- Chat and Work connection instructions follow the current official OpenAI documentation. This launch pass did not submit a new conversation/task in every product surface or test every plan/account.
- Native direct-client OAuth is exercised by local and Cloudflare PKCE callback tests. A complete interactive login in every third-party desktop agent is not claimed.

## Remaining platform limits

Parallel display creation, task movement, rendering, keyguard isolation and power groups depend on Android, the manufacturer and the app. Some games and secure/system windows are incompatible. When the separate display is unavailable, recover it explicitly or choose foreground deliberately; do not assume an automatic fallback.

Notification reads need notification access; replies need an eligible inline reply action and the required Android version. Successful tool routing does not guarantee the receiving app's delivery.

Transfers require direct HTTPS links and are capped at 512 MiB. Installation accepts one APK, not XAPK/APKS bundles. Updating Phone MCP ends its session. Shizuku startup after reboot and Android permission approval still require user participation.

Phone MCP does not keep an AI thinking after the host conversation ends, guarantee success in arbitrary games, or make every MCP client understand images. A hosted relay removes the need for a running PC relay; it does not remove the phone's connectivity, battery and Android constraints.
