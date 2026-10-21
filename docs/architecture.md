# Architecture and trust boundaries

Phone MCP is a personal bridge between an AI host and an Android session. It contains no model weights, model API key requirement, remote shell tool, or independent background planner.

## Relay choices

**Cloudflare:** `cloudflare/src/index.js` exports the Worker and `PhoneHub` Durable Object. The Worker enforces the configured public origin, security headers and browser-origin policy. The SQLite-backed Durable Object stores OAuth state, pairing records and the active phone WebSocket. A command carries a unique ID and an expiry. Only one device command is in flight; concurrent commands are rejected instead of silently queued. No computer needs to stay on for this path.

**Local Node:** `server/src/bridge.js` implements the HTTP and WebSocket relay. `server/src/oauth.js` and `state.js` implement OAuth and local persistence. `server/src/index.js` reads a private `.env` and binds to loopback by default. `tunnel.js` can run an installed `cloudflared` Quick Tunnel. That path requires the computer and server process to remain running.

The shared `server/src/mcp.js` registers the tool schemas, authorization scopes, read/write annotations and routing logic. The Cloudflare implementation imports this shared code; do not deploy the `cloudflare` directory without keeping its sibling `server` source directory available during bundling.

## Authentication

1. The owner creates distinct random `PHONE_TOKEN` and `ADMIN_PASSWORD` secrets for one relay.
2. An authenticated admin request creates a one-use, 15-minute pairing code.
3. The Android app redeems that code, imports the relay origin and device token, and stores the token under Android Keystore AES-GCM encryption.
4. The phone connects to `/device` using its bearer token. The token is not an AI-client access token.
5. An AI client discovers OAuth metadata, registers its redirect, and requests `phone.read` and optionally `phone.write` using PKCE S256. The owner approves using the server admin password on the relay's consent page.
6. Access and refresh tokens are bound to the grant. Refresh rotates tokens; revocation invalidates the token family.

The public server source accepts HTTPS redirect URIs and literal loopback HTTP redirects (`127.0.0.1`, `[::1]`) for native clients. Only the port may vary for loopback registration matching; the code exchange remains bound to the exact requested redirect. The consent page's form policy includes that validated callback origin. Arbitrary non-loopback HTTP redirects, credentials and fragments are rejected.

One deployment represents one owner and one phone token. Sharing the admin password or OAuth grants shares access to that phone's active session. This is not account separation for multiple customers.

## Android paths

- `BridgeService` owns the foreground service, network connection, session expiry and command routing.
- `PhoneAccessibilityService` reads and acts on authorized physical-screen windows.
- `BackgroundClient` connects through Shizuku to `BackgroundControlService` and `VirtualEngine`, which manage the separate display and its input/capture path.
- `PreviewActivity`, `PreviewMirror` and `PreviewFrames` implement a read-only local viewer. `ParallelTaskLease` constrains task reclamation to a task owned by the current session.
- `FileEngine` and `FilePolicy` manage the dedicated transfer folder, checksums, job IDs, cancellation and single-APK installation.
- `PhoneNotificationService` reads authorized active notifications and compatible inline reply actions.
- `Settings`, `AccessPolicy`, `WindowPolicy`, `SessionPolicy` and `Audit` implement configuration, permission, window, session and local audit boundaries.

## Data and execution limits

Commands and screenshots travel through the relay, so the relay owner and hosting provider are inside the data trust boundary. Screen and notification content is untrusted input to the AI. A tool result must never be interpreted as permission to send, buy, delete or disclose something.

Foreground mode uses the physical display; parallel mode requires a verified display ID greater than zero. There is no silent fallback from parallel to foreground. A primary keyguard, a separate display's keyguard flags, and Android's display power groups are distinct states. Device-specific behavior must be measured rather than inferred from a single flag.

A manually started unlimited session remains active until stopped or interrupted by Android, but it does not make an AI client run forever. Phone MCP's own APK update ends its process/session. Notifications, downloads and installation have app/platform-specific failure paths and are reported separately from success.
