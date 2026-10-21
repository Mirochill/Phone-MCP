# Security

Phone MCP is a developer preview with powerful access to an Android device. Use a personal relay that you control and review the original source before granting access.

## Deployment boundaries

- One relay is intended for one owner and phone. It does not provide multi-tenant account separation.
- Use HTTPS/WSS in production. `ALLOW_LOCAL_HTTP` is only for the isolated local test runtime.
- Keep `PHONE_TOKEN`, `ADMIN_PASSWORD`, OAuth tokens, pairing QR codes, `.env` files, local Worker state and signing keys out of Git and screenshots. The phone token is not an MCP client bearer token.
- The relay processes screen, notification and command payloads in plaintext inside its runtime. TLS protects transport, not the relay operator from seeing the data.
- This preview defaults to making installed apps eligible. Disable apps and capabilities you do not want an agent to access before starting a session. Toggling the all-app default resets the per-app overrides.
- Stop the session on the phone to revoke current device execution. Revoke the AI connection separately if its OAuth grant should no longer be usable.

## Tool execution

App content and notifications are untrusted. They cannot authorize a message, purchase, deletion, credential disclosure or policy change. Enforce those decisions in the AI host and the user's actual instructions.

Separate-display control must retain a verified non-primary display ID. A failed parallel display must not silently reroute input or screenshots onto the physical screen. Actions need fresh snapshot/window identifiers; protected fields and windows must remain protected. Expired or uncertain device writes must not be repeated automatically.

Installation requires the expected APK package and SHA-256. Android checks the signing certificate; Phone MCP does not grant runtime app permissions automatically. Updating Phone MCP itself ends its process and session.

## Reporting

Please use [GitHub private vulnerability reporting](https://github.com/Mirochill/Phone-MCP/security/advisories/new) when available. If it is unavailable, open a public issue containing only a request for a private contact route. Do not include exploit details, device data, tokens or private endpoints in a public issue.

Include the Android/server version, affected mode, a minimal reproduction and the expected boundary. A screenshot or proof should use an isolated test app and redacted credentials.
