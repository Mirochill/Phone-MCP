import fs from 'node:fs';import path from 'node:path';import assert from 'node:assert/strict';import {spawnSync} from 'node:child_process';import {createHash} from 'node:crypto';
const root=path.resolve(import.meta.dirname,'..');
function walk(dir){return fs.readdirSync(dir,{withFileTypes:true}).flatMap(e=>e.isDirectory()?(e.name==='media'?[]:walk(path.join(dir,e.name))):[path.join(dir,e.name)]);}
for(const file of [path.join(root,'README.md'),path.join(root,'CHANGELOG.md'),...walk(path.join(root,'docs'))].filter(f=>f.endsWith('.md'))){
 const text=fs.readFileSync(file,'utf8');for(const match of text.matchAll(/\]\(([^)\s]+)(?:\s+[^)]*)?\)/g)){
  const target=match[1];if(/^(?:https?:|mailto:|#)/.test(target))continue;const local=decodeURIComponent(target.split('#')[0]);assert.ok(fs.existsSync(path.resolve(path.dirname(file),local)),`Broken local link in ${path.relative(root,file)}: ${target}`);
 }
}
const manifest=JSON.parse(fs.readFileSync(path.join(root,'releases/0.16.2/Release-0.16.2.json'),'utf8'));
const apk=fs.readFileSync(path.join(root,'releases/0.16.2/Phone-MCP-0.16.2.apk'));
assert.equal(createHash('sha256').update(apk).digest('hex'),manifest.sha256);assert.equal(apk.length,manifest.sizeBytes);
const run=spawnSync(process.execPath,[path.join(root,'scripts/generate-tools.mjs'),'--check'],{stdio:'inherit'});if(run.status!==0)process.exit(run.status??1);
console.log('Documentation local links, release APK and complete tool catalog verified.');
