import test from 'node:test';
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {createHash} from 'node:crypto';
import {once} from 'node:events';
const require=createRequire(new URL('../../server/package.json',import.meta.url));
const WebSocket=require('ws');
const {Client}=require('@modelcontextprotocol/sdk/client/index.js');
const {StreamableHTTPClientTransport}=require('@modelcontextprotocol/sdk/client/streamableHttp.js');
const base=process.env.PHONE_MCP_CLOUD_TEST_URL??'http://127.0.0.1:8790';
const phoneToken='test_phone_token_00000000000000000000000',password='test-admin-password-000000000000';
const callback='https://chatgpt.com/connector_platform/oauth/callback';
const verifier='test_verifier_0000000000000000000000000000000000000',challenge=createHash('sha256').update(verifier).digest('base64url');
const post=(path,data,headers={})=>fetch(base+path,{method:'POST',headers:{'Content-Type':'application/json',...headers},body:JSON.stringify(data),redirect:'manual'});
async function authorization(scope='phone.read phone.write'){
 let r=await post('/register',{redirect_uris:[callback],client_name:'Cloud integration test',token_endpoint_auth_method:'none'});assert.equal(r.status,201);const client=await r.json();
 const q=new URLSearchParams({client_id:client.client_id,redirect_uri:callback,response_type:'code',code_challenge_method:'S256',code_challenge:challenge,scope,state:'state123',resource:base+'/mcp'});
 r=await fetch(base+'/authorize?'+q);assert.equal(r.status,200);const nonce=(await r.text()).match(/name="nonce" value="([^"]+)"/)[1];
 r=await fetch(base+'/approve',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded',Origin:base},body:new URLSearchParams({nonce,password,decision:'allow'}),redirect:'manual'});assert.equal(r.status,303);const redirect=new URL(r.headers.get('location'));assert.equal(redirect.searchParams.get('state'),'state123');return {client,code:redirect.searchParams.get('code')};
}
async function grant(scope){const {client,code}=await authorization(scope);const r=await post('/token',{grant_type:'authorization_code',client_id:client.client_id,redirect_uri:callback,code,code_verifier:verifier});assert.equal(r.status,200);return {...await r.json(),client};}
async function clientFor(t,token){const client=new Client({name:'cloud-integration',version:'1'});await client.connect(new StreamableHTTPClientTransport(new URL(base+'/mcp'),{requestInit:{headers:{Authorization:'Bearer '+token}}}));t.after(()=>client.close());return client;}
async function phoneFor(t,listener){const socket=new WebSocket(base.replace('http:','ws:')+'/device',{headers:{Authorization:'Bearer '+phoneToken}});socket.on('error',()=>{});await once(socket,'open');if(listener)socket.on('message',data=>listener(JSON.parse(data.toString()),socket));t.after(async()=>{if(socket.readyState===1){socket.close();await once(socket,'close');}});return socket;}

test('cloud system-window packages preserve the selected window metadata',async t=>{
 const seen=[];
 await phoneFor(t,(message,socket)=>{seen.push(message.args.packageName);socket.send(JSON.stringify({id:message.id,ok:true,result:{packageName:message.args.packageName,windowId:12,windows:[{windowId:12,packageName:message.args.packageName}]}}));});
 const tokens=await grant(),client=await clientFor(t,tokens.access_token);
 for(const pkg of ['android','com.android.systemui']) {
  const result=JSON.parse((await client.callTool({name:'phone_snapshot',arguments:{packageName:pkg}})).content[0].text);
  assert.equal(result.packageName,pkg);assert.equal(result.windows[0].windowId,12);
 }
 assert.deepEqual(seen,['android','com.android.systemui']);
});

test('cloud discovery, HTTPS resource, origin filtering and authentication boundary',async()=>{
 const health=await fetch(base+'/health');assert.equal(health.status,200);
 assert.equal(health.headers.get('referrer-policy'),'same-origin');
 assert.match(health.headers.get('content-security-policy'),/form-action 'self' https:\/\/chatgpt\.com;/);
 assert.equal((await fetch(base+'/health',{headers:{Origin:'null'}})).status,403);
 assert.equal((await fetch(base+'/health',{headers:{Origin:base}})).status,200);
 assert.equal((await (await fetch(base+'/health')).json()).requiresPC,false);
 assert.equal((await fetch(base+'/.well-known/openid-configuration')).status,404);
 assert.equal((await fetch(base+'/health',{headers:{Origin:'https://untrusted.example'}})).status,403);
 const r=await post('/mcp',{jsonrpc:'2.0',id:1,method:'tools/list'});assert.equal(r.status,401);assert.match(r.headers.get('www-authenticate'),/resource_metadata/);
 assert.equal((await post('/register',{redirect_uris:['http://untrusted.example']})).status,400);
});

test('cloud pairing is authenticated, one-use and atomic under concurrent redemption',async()=>{
 assert.equal((await post('/admin/pairing',{})).status,401);
 let r=await post('/admin/pairing',{}, {Authorization:'Bearer '+password});assert.equal(r.status,200);const p=await r.json();
 assert.equal((await fetch(base+'/pairing/'+p.code)).status,200);
 assert.equal((await post('/pair',{code:'wrong'})).status,401);
 const results=await Promise.all([post('/pair',{code:p.code}),post('/pair',{code:p.code})]);assert.deepEqual(results.map(r=>r.status).sort(),[200,401]);
 assert.equal((await results.find(r=>r.status===200).json()).token,phoneToken);
 assert.equal((await fetch(base+'/pairing/'+p.code)).status,410);
});

test('cloud OAuth binds PKCE, callback and resource, then consumes the code',async()=>{
 const {client,code}=await authorization();const b={grant_type:'authorization_code',client_id:client.client_id,redirect_uri:callback,code,code_verifier:verifier};
 assert.equal((await post('/token',{...b,code_verifier:'x'.repeat(50)})).status,400);
 assert.equal((await post('/token',{...b,redirect_uri:'https://untrusted.example'})).status,400);
 assert.equal((await post('/token',{...b,resource:'https://untrusted.example/mcp'})).status,400);
 assert.equal((await post('/token',b)).status,200);assert.equal((await post('/token',b)).status,400);
});

test('cloud official MCP client discovers tools and reports the actual offline phone',async t=>{
 const tokens=await grant(),client=await clientFor(t,tokens.access_token);const {tools}=await client.listTools();assert.equal(tools.length,21);
 const r=await client.callTool({name:'phone_connection',arguments:{}});assert.equal(JSON.parse(r.content[0].text).connected,false);assert.equal(r.structuredContent.connected,false);assert.deepEqual(r.structuredContent,JSON.parse(r.content[0].text));
 assert.deepEqual(r.structuredContent.tooling.tools,tools.map(tool=>tool.name));
 assert.equal(r.structuredContent.tooling.serverVersion,r.structuredContent.serverVersion);
 const offline=await client.callTool({name:'phone_status',arguments:{}});assert.equal(offline.isError,true);
 assert.deepEqual(JSON.parse(offline.content[1].text).tooling,r.structuredContent.tooling);
});

test('cloud WebSocket relays notifications, images and legacy long press while enforcing read-only scope',async t=>{
 const seen=[];await phoneFor(t,(m,socket)=>{seen.push(m);socket.send(JSON.stringify({id:m.id,ok:true,result:m.op==='screenshot'?{mimeType:'image/jpeg',data:'aW1hZ2U='}:m.op==='snapshot'?{snapshotId:'a8d56602-b21e-4485-8d71-e245ddc6a451',nodes:[{nodeId:'0.1',text:'Message',enabled:true}]}:m.op==='long_press'?{performed:true}:{notifications:[],screenControlAllowed:false}}));});
 const token=await grant(),client=await clientFor(t,token.access_token);
 assert.equal(JSON.parse((await client.callTool({name:'phone_connection',arguments:{}})).content[0].text).connected,true);
 await client.callTool({name:'phone_notifications',arguments:{packageName:'org.example.app'}});
 const image=await client.callTool({name:'phone_screenshot',arguments:{packageName:'org.example.app'}});assert.equal(image.content[0].type,'image');
 assert.ok(JSON.parse(image.content[1].text).tooling.tools.includes('phone_install_apk'));
 const status=JSON.parse((await client.callTool({name:'phone_status',arguments:{}})).content[0].text);assert.match(status.interactionGuide,/longPressNodeId/);assert.equal(status.screenControlAllowed,false);
 const screen=JSON.parse((await client.callTool({name:'phone_snapshot',arguments:{packageName:'com.example'}})).content[0].text);
 assert.match(screen.tooling.instruction,/longPressNodeId/);assert.deepEqual(screen.tooling.tools,(await client.listTools()).tools.map(tool=>tool.name));
 const legacy={packageName:'com.example',snapshotId:screen.snapshotId,nodeId:screen.nodes[0].longPressNodeId};
 const result=JSON.parse((await client.callTool({name:'phone_click',arguments:legacy})).content[0].text);assert.equal(result.performedAction,'long_press');assert.equal(result.performed,true);assert.equal(seen[4].args.nodeId,'0.1');
 assert.deepEqual(result.tooling,screen.tooling);
 const tap=JSON.parse((await client.callTool({name:'phone_click',arguments:{...legacy,nodeId:'0.2147483646.200.300'}})).content[0].text);assert.equal(tap.performedAction,'tap');assert.equal(seen[6].args.nodeId,undefined);assert.equal(seen[6].args.x,200);
 await client.callTool({name:'phone_set_control_mode',arguments:{mode:'foreground'}});assert.equal(seen[8].op,'control_mode');
 const ro=await grant('phone.read'),readClient=await clientFor(t,ro.access_token);const r=await readClient.callTool({name:'phone_open_app',arguments:{packageName:'org.example.app'}});assert.equal(r.isError,true);
 assert.deepEqual(JSON.parse(r.content[1].text).tooling,screen.tooling);
 assert.equal((await readClient.callTool({name:'phone_click',arguments:legacy})).isError,true);
 assert.equal((await readClient.callTool({name:'phone_set_control_mode',arguments:{mode:'foreground'}})).isError,true);
 assert.deepEqual(seen.map(x=>x.op),['notifications','screenshot','status','snapshot','long_press','snapshot','tap','snapshot','control_mode']);
});

test('cloud status distinguishes parallel permission from readiness for legacy APKs',async t=>{
 const frame={controlMode:'parallel',screenControlAllowed:false,parallel:{ready:true,displayId:14,deviceInteractive:true,keyguardLocked:false},allowedApps:[{packageName:'com.discord',read:true,write:false}]};
 await phoneFor(t,(m,socket)=>socket.send(JSON.stringify({id:m.id,ok:true,result:frame})));
 const tokens=await grant(),client=await clientFor(t,tokens.access_token);
 let v=JSON.parse((await client.callTool({name:'phone_status',arguments:{}})).content[0].text);
 assert.equal(v.screenControlAllowed,true);assert.equal(v.screenControlReady,true);assert.equal(v.foregroundScreenControlAllowed,false);assert.equal(v.allowedApps[0].write,false);
 frame.parallel.keyguardLocked=true;
 v=JSON.parse((await client.callTool({name:'phone_status',arguments:{}})).content[0].text);
 assert.equal(v.screenControlAllowed,true);assert.equal(v.screenControlReady,false);assert.equal(v.nextControlAction,'unlock_and_restart_parallel_session');
});

test('cloud refresh rotates and revocation disables the complete OAuth family',async()=>{
 const token=await grant();const b={grant_type:'refresh_token',client_id:token.client.client_id,refresh_token:token.refresh_token};
 const response=await post('/token',b);assert.equal(response.status,200);const rotated=await response.json();
 assert.equal((await post('/token',b)).status,400);
 await post('/revoke',{client_id:token.client.client_id,token:rotated.refresh_token});
 assert.equal((await post('/mcp',{}, {Authorization:'Bearer '+token.access_token})).status,401);
 assert.equal((await post('/mcp',{}, {Authorization:'Bearer '+rotated.access_token})).status,401);
});

test('cloud timeouts and concurrent calls never queue or repeat a device action',async t=>{
 let calls=0;await phoneFor(t,()=>{calls++;});const token=await grant();const a=await clientFor(t,token.access_token),b=await clientFor(t,token.access_token);
 const first=a.callTool({name:'phone_status',arguments:{}});await new Promise(ok=>setTimeout(ok,40));
 const concurrent=await b.callTool({name:'phone_status',arguments:{}});assert.equal(concurrent.isError,true);assert.match(concurrent.content[0].text,/déjà en cours/);
 const timeout=await first;assert.equal(timeout.isError,true);assert.match(timeout.content[0].text,/peut-être/);assert.equal(calls,1);
});
