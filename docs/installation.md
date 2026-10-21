# Install and configure Phone MCP

Use a relay in **your own account**. Each installation needs its own secrets and pairing. Do not connect to a relay copied from someone's screenshot or conversation.

## Requirements

- Android 11/API 30 or newer. Android 12+ is required for the guarded no-open notification reply path; Android 14+ provides isolated app-window capture. Individual features and OEM compatibility still vary.
- A compatible AI client with remote Streamable HTTP MCP and OAuth support.
- For cloud hosting: a Cloudflare account with Workers and SQLite Durable Objects available. Review the [current limits and pricing](https://developers.cloudflare.com/durable-objects/platform/pricing/); there is no guarantee that arbitrary usage will stay within a free allowance.
- For setup/build: Node.js 22+. Android source builds also need JDK 17, SDK Platform 36 and Build Tools 36.0.0. You can use the released APK without building it.
- For separate-display control, file operations and APK installation: [Shizuku](https://shizuku.rikka.app/) installed, running and authorized for Phone MCP. No root is required for the wireless-debugging startup path on supported Android 11+ phones.

## Option A: Cloudflare Workers (recommended)

This deploys a Worker and one SQLite Durable Object. Your phone maintains an outbound WSS connection; no incoming port needs to be opened on the phone, and your PC can be off after setup.

### 1. Get the project and authenticate

```sh
git clone https://github.com/Mirochill/Phone-MCP.git
cd Phone-MCP
npm ci --prefix server
npm ci --prefix cloudflare
```

From the `cloudflare` directory:

```sh
npx wrangler login
npx wrangler whoami
```

Complete the Cloudflare login in your browser and choose the intended account. If your login can access several Cloudflare accounts, add the intended `account_id` to your **local** `wrangler.jsonc` rather than guessing. No maintainer account ID is shipped here.

### 2. Choose your public origin

Find your Workers subdomain in the Cloudflare dashboard. With Worker name `phone-mcp`, an example origin is `https://phone-mcp.YOUR-SUBDOMAIN.workers.dev`. Use your actual subdomain; the placeholder is not a usable endpoint.

From the repository root:

```sh
node scripts/configure-cloud.mjs prepare https://phone-mcp.YOUR-SUBDOMAIN.workers.dev phone-mcp
```

Replace the example before running it. The helper updates the Worker name and `PUBLIC_URL` in `cloudflare/wrangler.jsonc` and creates two distinct random secrets in ignored `work/cloud-secrets.private.json`. It preserves an existing private secret file. Keep that file private, restrict its file permissions to your account, and back it up securely.

The configuration includes `nodejs_compat`, the `PHONE_HUB` binding, the `PhoneHub` class and the `v1` SQLite Durable Object migration. Keep them together. The Worker imports shared server code during bundling, so retain both directories.

### 3. Deploy, upload secrets, and check health

```sh
cd cloudflare
npm run deploy
cd ..
node scripts/configure-cloud.mjs upload-secrets
```

The initial deployment has no usable phone access until the secrets exist. The helper pipes the private JSON to Wrangler's secret upload command without placing secret values in command-line arguments. If you configure secrets manually, use Wrangler's interactive `secret put PHONE_TOKEN` and `secret put ADMIN_PASSWORD` commands from `cloudflare`; never place secrets in `vars` or commit `.dev.vars`.

Open `https://YOUR-WORKER.YOUR-SUBDOMAIN.workers.dev/health`. It should return `ok: true`, `hosting: "cloudflare"`, and `requiresPC: false`. An unauthenticated request to `/mcp` should return **401** with `WWW-Authenticate`; a bare browser visit is not a complete MCP test.

If the deployed hostname differs from the configured origin, correct `PUBLIC_URL` and redeploy before pairing. For a custom domain, configure the domain in Cloudflare, use that exact HTTPS origin in `PUBLIC_URL`, and redeploy. OAuth issuer/resource URLs must agree.

### 4. Create a private pairing link

From the repository root:

```sh
node scripts/configure-cloud.mjs pair
```

The helper reads the private admin secret locally and calls `cloudflare/pair.mjs`. It creates a private one-use pairing JSON, QR image and HTML page in `cloudflare`. The console link, QR code and HTML file are credentials while valid: do not post them in an issue, README or video. A code expires after 15 minutes and cannot be redeemed twice.

Open that pairing page/link on your phone **after installing the APK**. The page opens the `phonemcp://` handler and the app imports the server origin and device token. Confirm the pairing on the phone. If a browser refuses the custom scheme, open the displayed app link explicitly or use the app's manual connection fields with your own relay origin and `PHONE_TOKEN`. Do not enter an OAuth client token in the Android pairing field.

### 5. Connect the AI client

Your AI client's URL is the relay origin **plus `/mcp`**. Authenticate through OAuth using your relay's admin consent page. Follow the [Chat/Work/Codex guide](clients.md), then complete the Android setup below.

### Updates and maintenance

Keep your private secrets and signing key. Pull/review source updates, install locked dependencies with `npm ci`, run the relevant tests, and deploy the updated Worker. Update the APK separately when required. Server metadata changes may require a client refresh. A Phone MCP self-update ends its active Android session.

Do not change the existing Durable Object migration tag/class arbitrarily on an established deployment. Rotate credentials deliberately; replacing the phone token requires re-pairing the phone. To revoke an AI client, use its disconnect/revoke flow in addition to stopping the phone session.

Cloudflare references: [Durable Objects setup](https://developers.cloudflare.com/durable-objects/get-started/), [Worker secrets](https://developers.cloudflare.com/workers/configuration/secrets/), and [Wrangler configuration](https://developers.cloudflare.com/workers/wrangler/configuration/).

## Option B: Local Node server and HTTPS tunnel

This option requires the PC, Node server and tunnel to remain running. The built-in tunnel runner uses a Cloudflare Quick Tunnel with a temporary address. A stable HTTPS reverse proxy or named tunnel is preferable for a long-lived deployment.

Install `cloudflared` from [Cloudflare's official download instructions](https://developers.cloudflare.com/cloudflare-one/connections/connect-networks/downloads/), and make it available on `PATH`. Alternatively set `CLOUDFLARED_BIN` to its absolute local path. No copy of the executable is bundled in Git.

```sh
cd server
npm ci
npm run tunnel
```

The runner generates private `.env.tunnel` credentials and prints the temporary `/mcp` URL. Keep it running. In another terminal in `server`:

```sh
npm run pair
```

Open `server/runtime/Appairer-Phone-MCP.private.html` to pair. That page and its QR are private. The temporary origin can change when the tunnel restarts, requiring re-pairing and a client connection update.

For a stable reverse proxy instead, from `server`:

```sh
npm run setup -- https://phone-mcp.example.com
npm start
```

Route HTTPS traffic and WebSocket upgrades to the local bind address (default `127.0.0.1:8787`). The setup helper creates `.env`; it refuses to overwrite an existing file. For this stable-server path, create a pairing code with an authenticated `POST /admin/pairing` using the `ADMIN_PASSWORD` from that private file, then open the returned `/pairing/CODE` page on the same public origin. `npm run pair` is specifically the Quick Tunnel helper and reads `.env.tunnel`.

Do not expose an unencrypted HTTP server to the Internet. `ALLOW_LOCAL_HTTP` is a test-only option; the Android app requires a proper HTTPS origin. If a trusted proxy terminates TLS, configure `TRUST_PROXY=true` only for that actual deployment topology.

## Android setup

1. Download `Phone-MCP-0.8.2.apk` and `SHA256SUMS.txt` from [Releases](https://github.com/Mirochill/Phone-MCP/releases/latest). Check the file hash before installation. On Windows: `Get-FileHash .\Phone-MCP-0.8.2.apk -Algorithm SHA256`.
2. Allow installation from the browser/file manager you intentionally use, install the developer-signed preview and open **Phone MCP**. Android still enforces certificate continuity for updates.
3. Pair using your private link/QR. In **Réglages** (Settings), the server address is the HTTPS origin without `/mcp`. The pairing token is stored encrypted by Android Keystore.
4. Review **Toutes les apps** and the per-app **Lecture / Actions / Images / Notifications** capabilities. The current preview defaults to allowing installed apps. Disable sensitive apps or start with explicit grants. Disabling read also disables the other capabilities for that app. Changing the all-app default clears per-app overrides.
5. Enable **Accessibility** for physical-screen interaction. Enable **Notification access** for notification reads/replies. Grant the ordinary notification permission for the persistent session controls when Android asks.
6. For **Écran séparé** (Separate screen) and files/APK, install and start Shizuku. On supported Android 11+ devices, follow its [wireless debugging setup](https://shizuku.rikka.app/guide/setup/), then grant Phone MCP access in Shizuku. These system permissions are granted on the phone, not by the AI host. Shizuku runs with shell-level privileges, not universal root privileges.
7. Select **Écran du téléphone**, **Écran séparé** or **Notifications**, choose the session duration, and tap **Démarrer la session**. The app does not auto-start a session after boot.
8. In your AI client, verify `phone_connection`, then `phone_status`. In parallel mode, require `parallel.ready: true` and a non-primary display ID before screen actions. A connected phone alone does not establish a usable display.

For the read-only preview, use **Voir l'app** in the notification or open the controlled app when supported. Closing the preview does not end the game/session. A frame-based fallback may be used when hardware mirroring is unavailable.

## Troubleshooting

| Symptom | Check / action |
|---|---|
| `/health` is 503 | Secrets and `PUBLIC_URL` are missing or inconsistent. |
| `/mcp` is 401 in a browser | Expected without OAuth; check discovery and use a real MCP client. |
| Phone online, screen not ready | Inspect `controlSummary`, `nextControlAction`, Shizuku and the active display. Explicitly recover parallel mode or choose foreground; do not silently switch screens. |
| Mode change asks for unlock | Initially creating/changing a display needs an awake, unlocked phone. A previously verified independent display may continue behind the physical keyguard. |
| Missing new tool in an old chat | Refresh the client connection and begin a fresh conversation; read `tooling` guidance. |
| Native OAuth callback rejected | Deploy this public server version; use DCR, PKCE S256 and a literal loopback IP callback. |
| Shizuku unavailable after restart | Restart it using its documented procedure and re-authorize if required. |
| APK update won't install | Verify package, checksum and signing certificate; a build signed with another key cannot update in place. |
| Download still pending | Poll `phone_file_job`; do not claim completion from job creation. |
| Timeout after a write | Inspect the current app/job state before retrying; an action may already have occurred. |
