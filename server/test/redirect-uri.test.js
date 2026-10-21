import test from 'node:test';
import assert from 'node:assert/strict';
import {once} from 'node:events';
import {createHash} from 'node:crypto';
import {validRedirectUri,matchesRedirectUri} from '../src/redirect-uri.js';
import {createBridge} from '../src/bridge.js';

test('native callback validation rejects external HTTP, credentials, fragments and path substitutions',()=>{
  for(const uri of ['https://client.example/callback','http://127.0.0.1:5000/callback','http://[::1]:5000/callback'])assert.equal(validRedirectUri(uri),true,uri);
  for(const uri of ['http://example.com/callback','http://localhost/callback','http://127.0.0.1.evil.example/callback','http://192.168.1.2/callback','https://user:pass@example.com/cb','https://example.com/cb#fragment','javascript:alert(1)',' https://client.example/cb'])assert.equal(validRedirectUri(uri),false,uri);
  assert.equal(matchesRedirectUri('http://127.0.0.1/callback/id','http://127.0.0.1:41234/callback/id'),true);
  assert.equal(matchesRedirectUri('http://127.0.0.1/callback/id','http://127.0.0.1:41234/callback/other'),false);
  assert.equal(matchesRedirectUri('https://client.example/callback','https://client.example:1234/callback'),false);
  assert.equal(matchesRedirectUri('http://127.0.0.1/callback?a=1','http://127.0.0.1:41234/callback?a=2'),false);
});

test('desktop loopback client completes PKCE while retaining exact code-exchange callback binding',async t=>{
  const config={publicUrl:'https://phone.example',phoneToken:'test_phone_token_00000000000000000000000',adminPassword:'test-admin-password-000000000000'};
  const bridge=createBridge(config);bridge.server.listen(0,'127.0.0.1');await once(bridge.server,'listening');t.after(()=>bridge.close());
  const base=`http://127.0.0.1:${bridge.server.address().port}`;
  const post=(p,b)=>fetch(base+p,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(b),redirect:'manual'});
  let r=await post('/register',{redirect_uris:['http://127.0.0.1/callback/codex'],token_endpoint_auth_method:'none'});assert.equal(r.status,201);const client=await r.json();
  const callback='http://127.0.0.1:41234/callback/codex',verifier='native-client-verifier-0000000000000000000000000000';
  const q=new URLSearchParams({client_id:client.client_id,redirect_uri:callback,response_type:'code',code_challenge_method:'S256',code_challenge:createHash('sha256').update(verifier).digest('base64url'),scope:'phone.read',state:'desktop-state'});
  r=await fetch(base+'/authorize?'+q);assert.equal(r.status,200);assert.match(r.headers.get('content-security-policy'),/form-action 'self' http:\/\/127\.0\.0\.1:41234;/);
  const nonce=(await r.text()).match(/name="nonce" value="([^"]+)"/)[1];
  r=await post('/approve',{nonce,password:config.adminPassword,decision:'allow'});assert.equal(r.status,303);
  const location=new URL(r.headers.get('location'));assert.equal(location.origin,'http://127.0.0.1:41234');assert.equal(location.searchParams.get('state'),'desktop-state');
  const body={grant_type:'authorization_code',client_id:client.client_id,redirect_uri:callback,code:location.searchParams.get('code'),code_verifier:verifier};
  assert.equal((await post('/token',{...body,redirect_uri:callback.replace('41234','41235')})).status,400);
  r=await post('/token',body);assert.equal(r.status,200);assert.equal((await r.json()).scope,'phone.read');
});
