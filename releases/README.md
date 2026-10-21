# Release files

`Phone-MCP-0.8.2.apk` is the developer-signed Android preview. Check `SHA256SUMS.txt` before installation. The certificate and original-source comparison are described in [`../docs/source-provenance.md`](../docs/source-provenance.md).

`Phone-MCP-0.8.2-decompiled-app.zip` contains approximate JADX output for the app-owned namespace. It is an inspection supplement; build from `android/` instead.

`phone-mcp-worker.mjs` is the bundled Worker from this public server source, produced by the pinned Wrangler `deploy --dry-run` command. It contains no deployment secrets. Deploy it with the `PhoneHub` SQLite Durable Object migration/binding, `nodejs_compat`, your real `PUBLIC_URL` and your own secrets. Merely uploading the JavaScript file without configuring those resources does not create a working installation. The original source and Wrangler configuration are the recommended deploy path.

The launch MP4 and generated source track live in `docs/media`. The GitHub release also supplies a source ZIP and a combined download checksum manifest.
