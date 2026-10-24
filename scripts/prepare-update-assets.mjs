import fs from 'node:fs';import path from 'node:path';import assert from 'node:assert/strict';import {createHash} from 'node:crypto';import {VERSION} from '../server/src/version.js';
const root=path.resolve(import.meta.dirname,'..'),release=path.join(root,'releases',VERSION),manifest=JSON.parse(fs.readFileSync(path.join(release,`Release-${VERSION}.json`),'utf8'));
const bytes=fs.readFileSync(path.join(release,`Phone-MCP-${VERSION}.apk`));
assert.equal(manifest.versionName,VERSION);assert.equal(bytes.length,manifest.sizeBytes);assert.equal(createHash('sha256').update(bytes).digest('hex'),manifest.sha256);
const assets=path.join(root,'cloudflare/assets');fs.mkdirSync(assets,{recursive:true});fs.writeFileSync(path.join(assets,'phone-mcp.apk'),bytes);fs.writeFileSync(path.join(assets,'release.json'),JSON.stringify(manifest,null,2)+'\n');
console.log(`Prepared verified ${VERSION} APK and matching paired-update manifest.`);
