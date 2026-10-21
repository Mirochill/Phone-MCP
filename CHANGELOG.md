# Changelog

## Android 0.8.2 — public source release

- Original Java, AIDL, resources and pinned Android build project recovered and published under MIT.
- Compiled 0.8.2 preview retained unchanged; a fresh build matches all non-signature APK entries.
- Read-only local preview with a hardware mirror path, protected input, task ownership/reclamation and a frame-based fallback. The tested Samsung uses the frame path; emulator hardware-mirror checks are documented separately.
- Existing capabilities include physical/separate-display app control, accessible and coordinate interactions, notification replies, direct file transfers and guarded single-APK installation.

### Public relay packaging

- Added native desktop loopback OAuth redirects with PKCE callback binding and a callback-specific consent-page policy in both relay implementations.
- Added a pinned Wrangler development dependency and isolated local Cloudflare runtime test runner.
- Made the Quick Tunnel runner use an installed `cloudflared` from `PATH` when a local executable override is not supplied.
- Added private-secret setup/pairing helpers, English installation/client guides, a browser-agent setup prompt, test CI, source provenance, screenshots and launch-media sources.

Relay runtime/package metadata remains independent of the Android version. This publication does not redeploy or replace a user's existing personal relay automatically.
