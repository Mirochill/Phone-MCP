import fs from 'node:fs';import path from 'node:path';import assert from 'node:assert/strict';import {createHash} from 'node:crypto';import {VERSION} from '../server/src/version.js';
const root=path.resolve(import.meta.dirname,'..');
const manifest=JSON.parse(fs.readFileSync(path.join(root,'cloudflare/assets/release.json'),'utf8'));
assert.match(manifest.versionName,/^\d+\.\d+\.\d+$/);
const release=path.join(root,'releases',manifest.versionName);
const bytes=fs.readFileSync(path.join(release,`Phone-MCP-${manifest.versionName}.apk`));
assert.equal(bytes.length,manifest.sizeBytes);assert.equal(createHash('sha256').update(bytes).digest('hex'),manifest.sha256);
const assets=path.join(root,'cloudflare/assets');fs.mkdirSync(assets,{recursive:true});fs.writeFileSync(path.join(assets,'phone-mcp.apk'),bytes);fs.writeFileSync(path.join(assets,'release.json'),JSON.stringify(manifest,null,2)+'\n');
console.log(`Prepared verified APK ${manifest.versionName} for server ${VERSION} and matching paired-update manifest.`);
