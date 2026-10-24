# Release files

Current: **[Phone MCP 0.16.2](https://github.com/Mirochill/Phone-MCP/releases/tag/v0.16.2)**.

`0.16.2/` contains the exact verified installable APK, version/hash manifest, sanitized device verification, publication checks and checksums. The GitHub release also includes the tagged source archive and a compiled public Worker bundle. Verify SHA-256 before installing and retain signing-certificate continuity. Full APK self-updates interrupt active sessions.

The files at this directory's root (`Phone-MCP-0.8.2.apk`, `phone-mcp-worker.mjs`, `apk-validation.json`, the decompiled ZIP and its checksums) belong to the **historical 0.8.2** release. They are preserved for provenance, not current deployment. Do not deploy the historical compiled Worker in place of current `cloudflare/src/`.

Build/deploy the current source with your own credentials. The current source archive excludes private tokens, QR codes, local runtime/state, account configuration and signing keys. [Provenance](../docs/source-provenance.md) · [Installation](../docs/installation.md).
