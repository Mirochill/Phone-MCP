# Architecture and trust boundaries

Phone MCP is a personal bridge between an AI host and an Android session. It contains no model weights, model API key requirement, remote shell tool or independent background planner.

## Relays and command scheduling

The Cloudflare Worker and SQLite `PhoneHub` Durable Object handle HTTPS, OAuth, private pairing, authenticated WebSocket forwarding, direct-upload chunks and paired update assets. The Node relay implements the same command contract with local durable OAuth storage. Both use the shared operation registry and MCP implementation from `server/src/`.

A command has an ID, expiry and OAuth-derived owner. `command-channel.js` provides distinct owned session lanes: actions within one app session serialize, independent sessions can proceed concurrently, and the native engine manager coordinates global control transitions. The phone routes each session to its own `VirtualEngine` through `SessionEngineManager`; a single virtual display is not shared by all agents. Pending responses remain matched to their command ID. A replacement authenticated socket invalidates old transport state without replaying writes.

One deployment serves one owner/phone; it is not a multi-tenant fleet service. Distinct OAuth grants do not bypass app permissions or local stops. Navigation separation is different from account-data isolation.

## Authentication and deployment

Use distinct `PHONE_TOKEN` and `ADMIN_PASSWORD` secrets. Private single-use pairing codes expire after 15 minutes. Android stores the device token with Android Keystore; it is separate from AI OAuth tokens. AI clients use OAuth/PKCE S256 with `phone.read` / `phone.write`, dynamic registration, hashed durable token records, refresh rotation and revocation.

Public-source native OAuth accepts HTTPS redirects and literal loopback HTTP (`127.0.0.1`, `[::1]`) for desktop clients. Only the loopback port can vary for registration matching; token exchange remains bound to the exact redirect. The consent CSP includes the validated callback origin. This public client support is retained from the original release.

Production requires HTTPS/WSS and a consistent `PUBLIC_URL`. Update assets use `ASSETS` with `run_worker_first:true`; raw APK/manifest routes are not public bypasses. Downloads use authenticated, expiring, artifact-hash-bound tickets. GitHub release assets are intentionally public; personal Worker credentials and pairing remain private.

## Android ownership and resources

- `BridgeService`: manual phone service, transport, command dispatch and local capture owner.
- `SessionEngineManager` / `VirtualEngine`: per-session display/task ownership, source permissions, leases, window/target guards and native input/capture.
- `ScreenOffController`: local physical-screen policy, owned hardware blanking, native interactivity, audio restrictions and side-button restoration. Agents cannot enable physical keep-on.
- `PhoneAccessibilityService`: authorized physical-screen interaction and semantic app reads where applicable.
- `PreviewActivity`, `PreviewMirror`, `PreviewFrames`, `RecentsRegistrationActivity`: read-only viewer and content-free per-session Recents routing. Page handoff revokes old AI input before moving the owned task to the physical display.
- `PreviewMedia`, `CaptureMirror`, `PendingMedia`: local source-only PNG and hardware-encoded silent MP4. A dedicated compositor root is attached only to an own-content encoder display; no source-display 0 fallback, microphone or MediaProjection of the physical screen.
- `FileEngine`, `UploadReceiver`, `FilePolicy`: scoped files, bounded transfer/jobs, SHA-256, idempotency and guarded single-APK install. Selected Android folders and deliberately shared content have separate grants.
- Notification service and access policies retain fresh IDs, allowed apps, protected fields, local revocation and expiry.

Recording has its own short renewable source-specific lease and bounded partial wakelock while active. No recording loop runs when no local video exists. MediaStore rows remain pending until validated; failed/empty/cancelled outputs are removed. Source loss, stop and disconnection invalidate the mirror and release resources. Hardware evidence for finalization while a session ends is still incomplete; see [verification](verification.md).

## Limits and lifecycle

App sessions preserve separate navigation where supported, but same-user processes, services and data remain shared. Android/OEM behavior, keyguard, app multi-display and secure-content policies can prevent a path. Recovery cannot silently choose the physical display, relaunch an app, bypass security or claim a black frame is valid.

The relay sees forwarded payloads. TLS is transport encryption, not end-to-end protection against the relay operator. App/file/notification contents remain untrusted to the AI. User intent must authorize consequential actions.

A persistent connection does not create an always-running AI agent. Full base-APK replacement stops Android processes and sessions; no unsafe orphan-control or automatic stale-session revival is implemented. Server-only updates do not replace the APK, but transport changes can still require clients to reconnect.
