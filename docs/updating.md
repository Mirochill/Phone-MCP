# Update Phone MCP in Android, your relay and ChatGPT

**Installing a new APK or clicking Update/Refresh in ChatGPT does not update every layer.** Updating this GitHub repository does not deploy other users' Workers. Each self-hosted user must update their own relay and Android app, then refresh their own AI client's tool metadata when it changes. A custom plugin refresh affects that connection; it is not a deployment to everyone who copied the repository.

For the 0.17.0 relay (compatible with Android 0.16.2), the expected inventory is **62 tools** (58 operations plus `phone_connection`, `phone_catalog`, `phone_read`, `phone_action`), API 1, catalog `c1b6e733309e4029`. Cloud hosting reports `0.17.0-cloud`; the Node relay reports its own version without that suffix. Future releases may legitimately change the count/hash: use their generated [tool reference](tools-reference.md) and live discovery.

## Which update changes what?

| Layer | What its update changes | What it does not update |
|---|---|---|
| Android APK | Native interaction, captures, previews, app UI and phone-side execution | Your deployed relay, ChatGPT tool imports or an existing chat's definitions |
| Your deployed Node/Cloudflare relay | Live MCP operations, validation, instructions, schemas, transport and update manifest | Another user's endpoint, their installed APK or a host's already cached tool metadata |
| ChatGPT connection: Refresh / Actualiser les outils | Imports the current server descriptors for that custom connection | Server code, APK, another user's separate connection, or guaranteed replacement of every old chat's definitions |
| New conversation with Phone MCP selected | Loads the connector/tools available to that conversation | Missing Android grants, an old relay or unsupported host file binding |
| GitHub download / `git pull` | Local files only | Running server processes, Cloudflare deployments, phone installs or ChatGPT connections |

A plugin-card/package version such as `1.0.0`, the APK version, `serverVersion`, `apiVersion` and `catalogVersion` describe different things. Do not diagnose an outdated relay from the plugin-card version alone.

## 1. Update your own relay without losing its configuration

1. Identify the existing deployment/account/origin. Keep the same OAuth endpoint, phone token, admin secret, Durable Object binding/class/migration history, private state and Android signing key. Back up private configuration locally outside the checkout; never publish it or paste credentials into an agent chat.
2. Inspect `git status` before updating. Earlier public versions tracked `cloudflare/wrangler.jsonc`; it is now private/ignored and replaced by a public `wrangler.example.jsonc`. Save your customized file before pulling, then restore it locally. Do not discard a dirty file or overwrite it with the example. Resolve unrelated changes separately.
3. Fetch/review the intended source revision and update it. On a clean branch tracking the public project:

```sh
git fetch origin --tags
git pull --ff-only
npm ci --prefix server
npm ci --prefix cloudflare
npm test
npm run test:policy
npm run test:android-doubles
npm run test:cloud
npm run check:docs
```

4. For Cloudflare, merge required new configuration into your preserved private file. In particular keep `ASSETS` bound to `./assets` with `run_worker_first: true`, so raw update assets cannot bypass authentication. Run `node scripts/prepare-update-assets.mjs` to prepare the distributed release APK/manifest, or build your own APK with your existing signer using `scripts/Build-Release.ps1`. From `cloudflare`, run `npm run deploy` in **your own account**. This does not deploy the maintainer's personal Worker for you.
5. For the Node relay, restart your actual existing service/process using its normal launcher. Downloading code without restarting the process leaves the old server running. Preserve the private `.env`, OAuth state and any tunnel/proxy configuration.
6. Verify the live `/health` version and call `phone_connection`. If it still reports an older version, fix the deployed process/origin before refreshing ChatGPT. Do not rotate credentials or re-pair just because tool schemas changed.

A server change may require transport reconnection. Full APK replacement interrupts active Android sessions; plan that separately and reopen the app locally after installation.

## 2. Update Android when the release requires it

Download the release APK and verify its SHA-256. Android requires the same signing certificate for an in-place update. A build with a different local debug/release key cannot simply replace the distributed APK; do not uninstall an existing app and erase its data as a routine workaround.

After installation, reopen Phone MCP, restart Shizuku if needed, check app/capability grants and start the phone session locally. Keep the phone unlocked briefly for initial engine readiness. Updating only the APK can leave a legacy relay advertising 21 tools; updating only the relay can expose functions the old APK cannot execute. Tool discovery and phone execution readiness are separate checks.

## 3. Refresh the custom ChatGPT connection

After deploying the server's new metadata:

1. Open [ChatGPT Plugins](https://chatgpt.com/plugins) and select your installed **Phone MCP**.
2. Open **Manage / Gérer**. In the observed French UI this leads to **Settings → Plugins → Phone MCP → Manage app / Gérer l’application**.
3. Click **Refresh tools / Actualiser les outils**, wait for completion, then review any reported schema/tool changes. Do not expand permissions or remove/recreate the connection just to refresh.
4. Start a **new conversation**, select Phone MCP with `@`, and verify both live discovery and the tools actually callable there. A browser-page reload alone is not the connector metadata refresh.
5. If the same server is shared with other users, they may still need their own connection refresh and new chat. Workspace administrators control workspace-managed connectors; the refresh option may be available only to the owner/admin. Updating your personal custom connector does not grant access or update somebody else's self-hosted endpoint.

These steps follow [OpenAI's custom MCP refresh flow](https://developers.openai.com/plugins/deploy/connect-chatgpt/#refresh-metadata). Store/workspace-published plugins have their own continuous-review/update and publication flow; source or submitted plugin-information changes are not automatically published to all installations. Follow the applicable plugin owner's flow and verify the recipient's actual inventory rather than promising a universal automatic update.

## 4. Verify names AND schemas

Ask the agent to:

- Call `phone_connection` and inspect `serverVersion`, `tooling.apiVersion`, `tooling.catalogVersion` and the tool-name set. For relay 0.17.0, compare all 62 unique names with [tools.json](tools.json), not just the total. Normalize only the host/provider namespace prefix when comparing logical tool names.
- Call `phone_catalog` for the 58 live operations, then request the schema of an affected operation. The four discovery/gateway tools are not repeated in that operations list.
- Compare this with the tools genuinely exposed to the agent by its host. A server advertising 61 names does not prove the host imported all 59, and a matching count does not prove identical schemas.
- For this release, `phone_catalog({"operation":"screenshot"})` must include optional `captureSource` with `auto` / `surface`. If the named `phone_screenshot` in the chat lacks that argument, the chat has a stale named-tool definition despite having all 62 names.
- For 0.17.0, check `start_app`, `ui_flow` and `board_move` plus `ad_status.waitMs` allowing 20000 and the optional final observation/image. See [fast workflows](performance.md). The Android 0.16.2 APK remains the current companion; do not invent a 0.17.0 APK.
- Verify app readiness separately with `phone_status` only when phone operation is needed. Metadata inspection does not require starting games, ads, installs or device actions. Never run every write tool merely to check its availability.

In the earlier 0.16.2 check on 2026-10-24, the maintainer's chat had all 59 names while its cached named screenshot definition still lacked `captureSource`; the live server schema already included it. The custom connection was refreshed in ChatGPT, and its imported UI inventory was read back as **39 write + 20 read tools**, with the updated surface-capture description. This did not replace the existing conversation's cached named arguments. See the [dated metadata verification](connection-verification-2026-10-24.json); no claim is made that every write operation was executed or that a fresh-chat capture was tested. This is why checking only the count is insufficient. A completed connector refresh must still be followed by a fresh-chat check; an existing conversation can retain its earlier definitions.

## 5. Use the stable gateways when a named schema is cached

If the chat genuinely exposes `phone_catalog`, `phone_read` and `phone_action`, first discover the current schema. An operation name is not necessarily the same as its named tool: for example `control_mode` maps to `phone_set_control_mode`, and `file_upload_begin` maps to `phone_upload_begin`. Copy the exact `operation` from discovery.

For a read, call `phone_read` with:

```json
{
  "operation": "screenshot",
  "arguments": {
    "packageName": "com.example.authorizedapp",
    "sessionId": "REPLACE_WITH_THE_REAL_SESSION_UUID",
    "captureSource": "surface"
  }
}
```

This is a shape example, not an executable request: use a real authorized package/session and the live schema. `phone_read` rejects writes. Writes use `phone_action` with the same `operation` / `arguments` envelope and existing user authorization. Do not invoke a missing named tool, invent parameters in a cached schema, silently change permissions or replay uncertain writes.

If the old chat lacks the gateways too, refresh the connection and open a new conversation. A text catalog cannot add callable tools retroactively. Host-native chat-file input metadata for `phone_send_file` must also be imported: a generic gateway cannot manufacture a file picker, readable bytes from an opaque ID, or a `sandbox:/...` reference. Use the supported direct sender for local files instead of inventing a URL.

## Instructions to give an updating agent

Use the [copy-ready agent update prompt](agent-update-prompt.md). It requires separate evidence for local source, deployed relay, Android readiness, host metadata and the current conversation. The [initial setup prompt](agent-setup-prompt.md) remains available for a new installation.

The later 0.17.0 refresh was read back as **42 write + 20 read = 62 tools**, including `phone_board_move`. The live catalog and complete name set matched; fast workflows ran through the stable gateway in the existing conversation. See [the 0.17.0 metadata check](connection-verification-0.17.0.json). This is distinct from claiming every fresh-chat named tool was executed.
