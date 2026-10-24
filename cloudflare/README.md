# Cloudflare deployment

The Worker and its SQLite Durable Object handle phone pairing, OAuth and the authenticated WebSocket relay. The phone can connect without a PC process running. Existing deployments should keep their Worker name, binding, migration history, origin and secrets.

## New installation

1. Install dependencies with `npm ci --prefix ../server` and `npm ci` from this folder.
2. Copy `wrangler.example.jsonc` to `wrangler.jsonc`; set your Worker name and public HTTPS origin. Authenticate Wrangler with your own Cloudflare account.
3. Generate two distinct random secrets: `PHONE_TOKEN` (32+ URL-safe characters) and `ADMIN_PASSWORD` (24+ characters). Store them using `wrangler secret put`; never put them in source or APK assets.
4. From the project root, run `node scripts/prepare-update-assets.mjs` to verify/copy the released APK and matching manifest. Alternatively build your own signed APK with `scripts/Build-Release.ps1`.
5. Deploy with the Wrangler version tested for this release, `npm run deploy` after `npm ci`.
6. Generate a one-use pairing link with `pair.mjs`, which reads the administrator-password JSON from standard input. Open/paste the link on the phone. Connect ChatGPT to your HTTPS `/mcp` endpoint using OAuth.

Do not replace working secrets or re-pair a connected phone during an ordinary release. Deployment uses the account's existing plan and quotas; verify them in your own account before new hosting use.

## APK updates

`ASSETS` must bind `./assets`, with `run_worker_first: true`. This is required so raw APK and manifest asset URLs cannot bypass authentication. The supplied example config includes it.

- `/updates/latest`: requires the paired phone credential, returns version, SHA-256, byte size and a private download ticket.
- `/updates/download/<ticket>`: validates ticket expiry and the exact current artifact hash.
- Direct `/phone-mcp.apk` and `/release.json` requests are not public download routes.

Build-Release prepares both files together. Deploying makes the new release available to automatic/manual checks in the app. The phone's update flow uses the existing verified transfer/install jobs. Restart the app/session locally after updating Phone MCP itself.

## Local integration tests

Tests use fake secrets and a separate local Durable Object state directory. Never enable `ALLOW_LOCAL_HTTP` on a production origin. Start local Wrangler with `PUBLIC_URL=http://127.0.0.1:8790`, `ALLOW_LOCAL_HTTP=true`, the exact fake secrets in `test/integration.test.js`, and port 8790. Then run `npm test`. A different port can be passed through `PHONE_MCP_CLOUD_TEST_URL` with a matching local public URL. The public root test runner starts an isolated local Worker and runs each suite against fresh state; see `npm run test:cloud` and the publication-check record for current results.

Persistent OAuth stores hashed token records and enforces PKCE, scope, origin, rotation and revocation. Diagnostic logs include only request method, known tool name and HTTP result, never tool arguments, notification text, image data or credentials. Persistent Worker observability is disabled in the example.
