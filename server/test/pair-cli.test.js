import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp,writeFile,mkdir,readFile,rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join,dirname,basename,resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {execFile} from 'node:child_process';
import {promisify} from 'node:util';
import {once} from 'node:events';
import {createBridge} from '../src/bridge.js';
const run=promisify(execFile),script=fileURLToPath(new URL('../src/pair.js',import.meta.url));
for(const mode of ['configured-local','legacy-tunnel'])test(`pairing CLI supports ${mode} without manual credential copying`,async t=>{
  const temp=await mkdtemp(join(tmpdir(),'phone-mcp-pair-test-'));
  t.after(async()=>{
    if(dirname(resolve(temp))!==resolve(tmpdir())||!basename(temp).startsWith('phone-mcp-pair-test-'))throw Error('Unexpected fixture path.');
    await rm(temp,{recursive:true,force:true});
  });
  const bridge=createBridge({publicUrl:'https://phone.example',phoneToken:'test_phone_credential_0000000000000000',adminPassword:'test_admin_password_0000000000000000'});
  bridge.server.listen(0,'127.0.0.1');await once(bridge.server,'listening');t.after(()=>bridge.close());const port=bridge.server.address().port;
  const env=`PUBLIC_URL=https://phone.example\nADMIN_PASSWORD=test_admin_password_0000000000000000\nPORT=${port}\n`;
  await writeFile(join(temp,mode==='configured-local'?'.env':'.env.tunnel'),env);
  if(mode==='legacy-tunnel'){await mkdir(join(temp,'runtime'));await writeFile(join(temp,'runtime/endpoint.json'),JSON.stringify({port}));}
  await run(process.execPath,[script],{cwd:temp,windowsHide:true});
  const pairing=JSON.parse(await readFile(join(temp,'runtime/pairing.private.json'),'utf8'));
  assert.match(pairing.webLink,/^https:\/\/phone\.example\/pairing\/[A-Za-z0-9_-]+$/);
  assert.ok(pairing.expiresAt>Date.now());
  const response=await fetch(`http://127.0.0.1:${port}/pair`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({code:pairing.code})});
  assert.equal(response.status,200);
  assert.doesNotMatch(await readFile(join(temp,'runtime/Appairer-Phone-MCP.private.html'),'utf8'),/0\.2\.0|écran principal/);
});
