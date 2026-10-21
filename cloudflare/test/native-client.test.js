import test from 'node:test';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
const base=process.env.PHONE_MCP_CLOUD_TEST_URL??'http://127.0.0.1:8790';
const post=(p,b)=>fetch(base+p,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(b),redirect:'manual'});
test('Cloudflare native desktop login supports loopback ports and rejects non-loopback HTTP',async()=>{
  for(const uri of ['http://evil.example/cb','http://127.0.0.1.evil.example/cb','https://a:b@client.example/cb','https://client.example/cb#fragment'])assert.equal((await post('/register',{redirect_uris:[uri]})).status,400);
  let r=await post('/register',{redirect_uris:['http://127.0.0.1/callback/desktop'],token_endpoint_auth_method:'none'});assert.equal(r.status,201);const client=await r.json();
  const callback='http://127.0.0.1:41234/callback/desktop',verifier='cloud-native-verifier-00000000000000000000000000000';
  const q=new URLSearchParams({client_id:client.client_id,redirect_uri:callback,response_type:'code',code_challenge_method:'S256',code_challenge:createHash('sha256').update(verifier).digest('base64url'),scope:'phone.read',state:'desktop-cloud'});
  r=await fetch(base+'/authorize?'+q);assert.equal(r.status,200);assert.match(r.headers.get('content-security-policy'),/form-action 'self' http:\/\/127\.0\.0\.1:41234;/);
  const nonce=(await r.text()).match(/name="nonce" value="([^"]+)"/)[1];
  r=await post('/approve',{nonce,password:'test-admin-password-000000000000',decision:'allow'});assert.equal(r.status,303);const location=new URL(r.headers.get('location'));assert.equal(location.searchParams.get('state'),'desktop-cloud');
  const grant={grant_type:'authorization_code',client_id:client.client_id,redirect_uri:callback,code:location.searchParams.get('code'),code_verifier:verifier};
  assert.equal((await post('/token',{...grant,redirect_uri:callback.replace('41234','41235')})).status,400);
  r=await post('/token',grant);assert.equal(r.status,200);const tokens=await r.json();assert.equal(tokens.scope,'phone.read');
});
