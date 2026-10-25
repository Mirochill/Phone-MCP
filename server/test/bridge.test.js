import test from 'node:test';
import {mkdtempSync,readFileSync,existsSync,unlinkSync,rmdirSync,writeFileSync,mkdirSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { once } from 'node:events';
import WebSocket from 'ws';
import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { StreamableHTTPClientTransport } from '@modelcontextprotocol/sdk/client/streamableHttp.js';
import {sendLocalFile} from '../src/send-file.js';
import { createBridge } from '../src/bridge.js';

const config = {publicUrl:'https://phone.example',phoneToken:'test_phone_token_00000000000000000000000',adminPassword:'test-admin-password-000000000000',commandTimeoutMs:180};
const callback = 'https://chatgpt.com/connector_platform/oauth/callback';
const verifier = 'test_verifier_0000000000000000000000000000000000000';
const challenge = createHash('sha256').update(verifier).digest('base64url');

async function fixture(t,overrides={}) {
  const bridge = createBridge({...config,...overrides}); bridge.server.listen(0,'127.0.0.1'); await once(bridge.server,'listening');
  const base = `http://127.0.0.1:${bridge.server.address().port}`;
  t.after(() => bridge.close());
  const post = (path,body,headers={}) => fetch(base+path,{method:'POST',headers:{'Content-Type':'application/json',...headers},body:JSON.stringify(body),redirect:'manual'});
  return {bridge,base,post};
}
async function authorization(f,scope='phone.read phone.write') {
  let response = await f.post('/register',{redirect_uris:[callback],client_name:'ChatGPT',token_endpoint_auth_method:'none'});
  assert.equal(response.status,201); const client = await response.json();
  const query = new URLSearchParams({client_id:client.client_id,redirect_uri:callback,response_type:'code',
    code_challenge:challenge,code_challenge_method:'S256',scope,state:'test_state',resource:config.publicUrl+'/mcp'});
  response = await fetch(f.base+'/authorize?'+query); assert.equal(response.status,200);
  const html = await response.text(), nonce = html.match(/name="nonce" value="([^"]+)"/)[1];
  response = await f.post('/approve',{nonce,password:config.adminPassword,decision:'allow'}); assert.equal(response.status,303);
  const location = new URL(response.headers.get('location')); assert.equal(location.searchParams.get('state'),'test_state');
  return {client,code:location.searchParams.get('code')};
}
async function grant(f,scope) {
  const auth = await authorization(f,scope);
  const response = await f.post('/token',{grant_type:'authorization_code',client_id:auth.client.client_id,
    code:auth.code,redirect_uri:callback,code_verifier:verifier});
  assert.equal(response.status,200); return {...await response.json(),client:auth.client};
}
async function connectClient(t,f,token) {
  const client = new Client({name:'integration-test',version:'1'});
  const transport = new StreamableHTTPClientTransport(new URL(f.base+'/mcp'),{requestInit:{headers:{Authorization:`Bearer ${token}`}}});
  await client.connect(transport); t.after(() => client.close()); return client;
}
async function connectPhone(t,f,listener) {
  const socket = new WebSocket(f.base.replace('http:','ws:')+'/device',{headers:{Authorization:`Bearer ${config.phoneToken}`}});
  socket.on('error',() => {}); await once(socket,'open');
  if (listener) socket.on('message',buffer => listener(JSON.parse(buffer.toString()),socket));
  t.after(() => socket.terminate()); return socket;
}

test('authenticated replacement reconnect cancels old work and routes new commands once',async t=>{
 const f=await fixture(t),first=await connectPhone(t,f);let seen;
 first.on('message',data=>{seen=JSON.parse(data);});const token=await grant(f),client=await connectClient(t,f,token.access_token);
 const pending=client.callTool({name:'phone_status',arguments:{}});
 for(let i=0;i<20&&!seen;i++)await new Promise(ok=>setTimeout(ok,5));assert.ok(seen);
 const closed=once(first,'close');let commands=0;await connectPhone(t,f,(m,socket)=>{commands++;socket.send(JSON.stringify({id:m.id,ok:true,result:{transport:'new'}}));});
 assert.equal((await closed)[0],4001);assert.equal((await pending).isError,true);
 assert.equal(JSON.parse((await client.callTool({name:'phone_status',arguments:{}})).content[0].text).transport,'new');assert.equal(commands,1);
});

test('system-window packages and overlay metadata are relayed without accepting malformed names',async t => {
  const f=await fixture(t),seen=[];
  await connectPhone(t,f,(message,socket)=>{
    seen.push(message.args.packageName);
    socket.send(JSON.stringify({id:message.id,ok:true,result:{packageName:message.args.packageName,windowId:7,windows:[{windowId:7,packageName:message.args.packageName,layer:30}]}}));
  });
  const tokens=await grant(f),client=await connectClient(t,f,tokens.access_token);
  for(const pkg of ['android','com.android.systemui']) {
    const result=await client.callTool({name:'phone_snapshot',arguments:{packageName:pkg}});
    const data=JSON.parse(result.content[0].text);
    assert.equal(data.packageName,pkg);assert.equal(data.windows[0].windowId,7);
  }
  for(const pkg of ['other','com..discord','android/../../']) {
    let rejected=false;
    try { rejected=(await client.callTool({name:'phone_snapshot',arguments:{packageName:pkg}})).isError===true; }
    catch { rejected=true; }
    assert.equal(rejected,true);
  }
  assert.deepEqual(seen,['android','com.android.systemui']);
});

test('HTTPS and strong pairing credentials are required',() => {
  assert.throws(() => createBridge({...config,publicUrl:'http://phone.example'}));
  assert.throws(() => createBridge({...config,publicUrl:'https://phone.example/subpath'}));
  assert.throws(() => createBridge({...config,phoneToken:'weak'}));
});

test('file tools validate inputs, relay selected windows and enforce write scope',async t=>{
  const f=await fixture(t),seen=[];
  await connectPhone(t,f,(request,socket)=>{seen.push(request);socket.send(JSON.stringify({id:request.id,ok:true,result:{accepted:true,jobId:request.args.requestId??request.args.jobId}}));});
  const tokens=await grant(f),client=await connectClient(t,f,tokens.access_token),id='a8d56602-b21e-4485-8d71-e245ddc6a451';
  const valid={requestId:id,url:'https://download.example/file?token=opaque',fileName:'Mon document.pdf',sha256:'a'.repeat(64)};
  const download=await client.callTool({name:'phone_download_file',arguments:valid});assert.equal(download.isError,undefined);
  assert.ok(JSON.parse(download.content[0].text).tooling.tools.includes('phone_install_apk'));
  await client.callTool({name:'phone_snapshot',arguments:{packageName:'com.android.chrome',windowId:7}});
  await client.callTool({name:'phone_install_apk',arguments:{requestId:id,fileId:id,sha256:'b'.repeat(64),expectedPackage:'org.example.fixture'}});
  assert.deepEqual(seen.map(r=>r.op),['download_file','snapshot','install_apk']);assert.equal(seen[1].args.windowId,7);
  for(const args of [{...valid,url:'http://insecure.example/a'},{...valid,url:'https://user:secret@example.com/a'},{...valid,fileName:'../../app.apk'},{...valid,fileName:'.download.part'},{...valid,maxBytes:536870913},{...valid,sha256:'bad'},{...valid,requestId:'bad'}]){
    let refused=false;try{refused=(await client.callTool({name:'phone_download_file',arguments:args})).isError===true;}catch{refused=true;}assert.equal(refused,true);
  }
  const ro=await grant(f,'phone.read'),readClient=await connectClient(t,f,ro.access_token);
  const denied=await readClient.callTool({name:'phone_download_file',arguments:valid});assert.equal(denied.isError,true);
  assert.equal(seen.length,3);
  const tools=(await client.listTools()).tools;for(const name of ['phone_download_file','phone_install_apk','phone_file_job','phone_tap','phone_swipe'])assert.equal(tools.find(tool=>tool.name===name).annotations.destructiveHint,true);
});

test('legacy coordinate gestures and explicit control-mode changes enforce write scope',async t=>{
 const f=await fixture(t),seen=[];
 await connectPhone(t,f,(request,socket)=>{seen.push(request);socket.send(JSON.stringify({id:request.id,ok:true,result:request.op==='snapshot'?{snapshotId:'a8d56602-b21e-4485-8d71-e245ddc6a451',width:1440,height:3120,nodes:[]}:{performed:true}}));});
 const tokens=await grant(f),client=await connectClient(t,f,tokens.access_token),id='a8d56602-b21e-4485-8d71-e245ddc6a451';
 const legacy={packageName:'com.example',snapshotId:id,nodeId:'0.2147483646.1200.2200'};
 const result=JSON.parse((await client.callTool({name:'phone_click',arguments:legacy})).content[0].text);
 assert.equal(result.performedAction,'tap');assert.equal(result.observation.width,1440);assert.equal(seen[0].op,'tap');assert.equal(seen[0].args.x,1200);assert.equal(seen[0].args.nodeId,undefined);
 await client.callTool({name:'phone_set_control_mode',arguments:{mode:'foreground'}});assert.equal(seen[2].op,'control_mode');assert.equal(seen[2].args.mode,'foreground');assert.equal(seen[2].args.panelOff,false);assert.match(seen[2].args._ownerKey,/^[a-f0-9]{64}$/);
 const reader=await connectClient(t,f,(await grant(f,'phone.read')).access_token);
 assert.equal((await reader.callTool({name:'phone_click',arguments:legacy})).isError,true);
 assert.equal((await reader.callTool({name:'phone_set_control_mode',arguments:{mode:'foreground'}})).isError,true);assert.equal(seen.length,3);
});
test('discovery, bearer requirement, denied browser origin and invalid callbacks',async t => {
  const f = await fixture(t);
  let response = await fetch(f.base+'/.well-known/oauth-protected-resource/mcp');
  assert.equal((await response.json()).resource,config.publicUrl+'/mcp');
  response = await f.post('/mcp',{jsonrpc:'2.0',id:1,method:'tools/list'}); assert.equal(response.status,401);
  assert.match(response.headers.get('www-authenticate'),/resource_metadata/);
  response = await fetch(f.base+'/health',{headers:{Origin:'https://untrusted.example'}}); assert.equal(response.status,403);
  response = await f.post('/register',{redirect_uris:['http://untrusted.example/callback']}); assert.equal(response.status,400);
});
test('authorization codes bind client, callback, resource and PKCE, and are single-use',async t => {
  const f = await fixture(t), auth = await authorization(f);
  const exchange = {grant_type:'authorization_code',client_id:auth.client.client_id,code:auth.code,redirect_uri:callback,code_verifier:verifier};
  assert.equal((await f.post('/token',{...exchange,code_verifier:'x'.repeat(50)})).status,400);
  assert.equal((await f.post('/token',{...exchange,redirect_uri:'https://untrusted.example'})).status,400);
  assert.equal((await f.post('/token',{...exchange,resource:'https://untrusted.example/mcp'})).status,400);
  assert.equal((await f.post('/token',exchange)).status,200);
  assert.equal((await f.post('/token',exchange)).status,400);
});
test('refresh rotates and revocation invalidates the whole token grant',async t => {
  const f = await fixture(t), tokens = await grant(f);
  const body = {grant_type:'refresh_token',client_id:tokens.client.client_id,refresh_token:tokens.refresh_token};
  const response = await f.post('/token',body); assert.equal(response.status,200); const rotated = await response.json();
  assert.equal((await f.post('/token',body)).status,400);
  await f.post('/revoke',{token:rotated.refresh_token,client_id:tokens.client.client_id});
  assert.equal((await f.post('/mcp',{}, {Authorization:`Bearer ${tokens.access_token}`})).status,401);
  assert.equal((await f.post('/mcp',{}, {Authorization:`Bearer ${rotated.access_token}`})).status,401);
});
test('official MCP client discovers tools and refuses calls when the phone is offline',async t => {
  const f = await fixture(t), tokens = await grant(f), client = await connectClient(t,f,tokens.access_token);
  const {tools} = await client.listTools(); assert.equal(tools.length,66);
  assert.equal(tools.find(t => t.name === 'phone_click').annotations.destructiveHint,true);
  const connection=await client.callTool({name:'phone_connection',arguments:{}}),data=JSON.parse(connection.content[0].text);
  assert.equal(data.connected,false);assert.deepEqual(connection.structuredContent,data);
  assert.deepEqual(data.tooling.tools,tools.map(tool=>tool.name));
  assert.ok(Buffer.byteLength(JSON.stringify(data.tooling))<2000);
  assert.match(client.getInstructions(),/phone_catalog/);
  const result = await client.callTool({name:'phone_status',arguments:{}});
  assert.equal(result.isError,true); assert.match(result.content[0].text,/hors ligne/);
  assert.deepEqual(JSON.parse(result.content[1].text).tooling,data.tooling);
});
test('MCP relays commands and images to an authenticated phone with expiry',async t => {
  const f = await fixture(t), tokens = await grant(f), seen = [];
  await connectPhone(t,f,(request,socket) => {
    seen.push(request); assert.ok(request.expiresAt > Date.now());
    const result = request.op === 'screenshot' ? {mimeType:'image/jpeg',data:'aW1hZ2U='} : {allowedApps:[{packageName:'org.example.app'}]};
    socket.send(JSON.stringify({id:request.id,ok:true,result}));
  });
  const client = await connectClient(t,f,tokens.access_token);
  const result = await client.callTool({name:'phone_status',arguments:{}});
  assert.equal(JSON.parse(result.content[0].text).allowedApps[0].packageName,'org.example.app');
  const guidance=JSON.parse(result.content[0].text).tooling;
  assert.deepEqual(guidance.tools,(await client.listTools()).tools.map(tool=>tool.name));
  const image = await client.callTool({name:'phone_screenshot',arguments:{packageName:'org.example.app'}});
  assert.equal(image.content[0].type,'image'); assert.equal(seen[1].op,'screenshot');
  assert.deepEqual(JSON.parse(image.content[1].text).tooling,guidance);
  assert.equal(seen.length,2); // Metadata must not query or wake the phone on its own.
});

test('long press and legacy action tools return fresh observations through the real MCP transport',async t=>{
 const f=await fixture(t),seen=[];
 await connectPhone(t,f,(request,socket)=>{seen.push(request);socket.send(JSON.stringify({id:request.id,ok:true,result:request.op==='snapshot'?{snapshotId:'b8d56602-b21e-4485-8d71-e245ddc6a451',nodes:[{nodeId:'0.1',text:'Répondre',clickable:true,enabled:true}]}:{performed:true}}));});
 const tokens=await grant(f),client=await connectClient(t,f,tokens.access_token);
 const args={packageName:'com.example',snapshotId:'a8d56602-b21e-4485-8d71-e245ddc6a451',nodeId:'0.1',settleMs:0};
 const result=JSON.parse((await client.callTool({name:'phone_long_press',arguments:args})).content[0].text);assert.equal(result.performed,true);assert.equal(result.observation.nodes[0].text,'Répondre');assert.equal(result.observation.nodes[0].enabled,undefined);assert.deepEqual(seen.map(x=>x.op),['long_press','snapshot']);
 const ro=await grant(f,'phone.read'),reader=await connectClient(t,f,ro.access_token);assert.equal((await reader.callTool({name:'phone_long_press',arguments:args})).isError,true);assert.equal(seen.length,2);
});

test('cached phone_click schema executes explicit long press with the observed handle and write scope',async t=>{
 const f=await fixture(t),seen=[];
 await connectPhone(t,f,(request,socket)=>{seen.push(request);socket.send(JSON.stringify({id:request.id,ok:true,result:request.op==='snapshot'?{snapshotId:'a8d56602-b21e-4485-8d71-e245ddc6a451',nodes:[{nodeId:'0.1',text:'Message',enabled:true}]}:{performed:true}}));});
 const tokens=await grant(f),client=await connectClient(t,f,tokens.access_token);
 const screen=JSON.parse((await client.callTool({name:'phone_snapshot',arguments:{packageName:'com.example'}})).content[0].text);
 assert.match(screen.tooling.instruction,/longPressNodeId/);assert.ok(screen.tooling.tools.includes('phone_long_press'));
 const legacy={packageName:'com.example',snapshotId:screen.snapshotId,nodeId:screen.nodes[0].longPressNodeId};
 assert.deepEqual(Object.keys(legacy).sort(),['nodeId','packageName','snapshotId']);assert.match(legacy.nodeId,/^0(?:\.\d+)*$/);
 const result=JSON.parse((await client.callTool({name:'phone_click',arguments:legacy})).content[0].text);
 assert.equal(result.performed,true);assert.equal(result.performedAction,'long_press');assert.deepEqual(seen.map(x=>x.op),['snapshot','long_press','snapshot']);assert.equal(seen[1].args.nodeId,'0.1');assert.equal(seen[1].args.snapshotId,screen.snapshotId);
 const ro=await grant(f,'phone.read'),reader=await connectClient(t,f,ro.access_token);const denied=await reader.callTool({name:'phone_click',arguments:legacy});assert.equal(denied.isError,true);assert.equal(seen.length,3);
 assert.deepEqual(JSON.parse(denied.content[1].text).tooling,screen.tooling);
 assert.deepEqual(result.tooling,screen.tooling);assert.equal(result.observation.nodes[0].text,'Message');
 await client.callTool({name:'phone_click',arguments:{...legacy,nodeId:'0.1'}});assert.equal(seen[3].op,'click');
});

test('MCP status explains old APK parallel access without weakening app permissions',async t=>{
  const f=await fixture(t),tokens=await grant(f);
  const frame={controlMode:'parallel',screenControlAllowed:false,parallel:{ready:true,displayId:14,physicalPanelOff:true,deviceInteractive:true,keyguardLocked:false},allowedApps:[{packageName:'com.discord',read:true,write:false}]};
  await connectPhone(t,f,(request,socket)=>socket.send(JSON.stringify({id:request.id,ok:true,result:frame})));
  const client=await connectClient(t,f,tokens.access_token);
  let result=JSON.parse((await client.callTool({name:'phone_status',arguments:{}})).content[0].text);
  assert.equal(result.screenControlAllowed,true);assert.equal(result.screenControlReady,true);assert.equal(result.foregroundScreenControlAllowed,false);assert.equal(result.nextControlAction,'use_parallel_tools');assert.equal(result.allowedApps[0].write,false);
  frame.parallel.keyguardLocked=true;frame.parallel.deviceInteractive=false;
  result=JSON.parse((await client.callTool({name:'phone_status',arguments:{}})).content[0].text);
  assert.equal(result.screenControlAllowed,true);assert.equal(result.screenControlReady,false);assert.equal(result.nextControlAction,'unlock_and_restart_parallel_session');assert.match(result.controlSummary,/Écran séparé/);
});
test('read-only OAuth scope never relays a write to the phone',async t => {
  const f = await fixture(t), tokens = await grant(f,'phone.read'); let calls = 0;
  await connectPhone(t,f,() => { calls++; });
  const client = await connectClient(t,f,tokens.access_token);
  const result = await client.callTool({name:'phone_open_app',arguments:{packageName:'org.example.app'}});
  assert.equal(result.isError,true); assert.equal(calls,0);
  const reply = await client.callTool({name:'phone_reply_notification',arguments:{packageName:'org.example.app',notificationId:'8ff16399-c14f-4643-ae50-5300f56ec2bb',text:'Bonjour'}});
  assert.equal(reply.isError,true); assert.equal(calls,0);
});
test('notification reads and replies use background commands without opening the app',async t => {
  const f = await fixture(t), tokens = await grant(f), seen = [];
  const notificationId = '8ff16399-c14f-4643-ae50-5300f56ec2bb';
  await connectPhone(t,f,(request,socket) => {
    seen.push(request.op);
    socket.send(JSON.stringify({id:request.id,ok:true,result:request.op === 'notifications'
      ? {notifications:[{notificationId,title:'Contact',text:'Salut',canReply:true}]} : {dispatched:true}}));
  });
  const client = await connectClient(t,f,tokens.access_token);
  const read = await client.callTool({name:'phone_notifications',arguments:{packageName:'org.example.app'}});
  assert.equal(JSON.parse(read.content[0].text).notifications[0].notificationId,notificationId);
  const reply = await client.callTool({name:'phone_reply_notification',arguments:{packageName:'org.example.app',notificationId,text:'Bonjour'}});
  assert.equal(JSON.parse(reply.content[0].text).dispatched,true);
  assert.deepEqual(seen,['notifications','reply_notification']);
});
test('independent commands can overlap and each timeout never replays its action',async t => {
  const f = await fixture(t), tokens = await grant(f); let calls = 0;
  await connectPhone(t,f,() => { calls++; });
  const a = await connectClient(t,f,tokens.access_token), b = await connectClient(t,f,tokens.access_token);
  const first = a.callTool({name:'phone_status',arguments:{}});
  await new Promise(resolve => setTimeout(resolve,25));
  const second = await b.callTool({name:'phone_status',arguments:{}});
  assert.equal(second.isError,true); assert.match(second.content[0].text,/peut avoir eu lieu/);
  const expired = await first; assert.equal(expired.isError,true); assert.match(expired.content[0].text,/peut avoir eu lieu/);
  assert.equal(calls,2);
});
test('pairing endpoint rejects an invalid token',async t => {
  const f = await fixture(t);
  const socket = new WebSocket(f.base.replace('http:','ws:')+'/device',{headers:{Authorization:'Bearer incorrect'}});
  const status = await new Promise(resolve => {
    socket.on('error',() => {}); socket.on('unexpected-response',(_req,res) => { resolve(res.statusCode); socket.terminate(); });
  });
  assert.equal(status,401);
});

 test('one-time pairing rejects wrong and reused codes without disclosing device credentials',async t => {
  const f=await fixture(t);
  assert.equal((await f.post('/admin/pairing',{})).status,401);
  let response=await f.post('/admin/pairing',{}, {Authorization:`Bearer ${config.adminPassword}`});
  assert.equal(response.status,200);const pairing=await response.json();
  assert.equal(pairing.endpoint,config.publicUrl);
  assert.equal((await fetch(f.base+'/pairing/'+pairing.code)).status,200);
  assert.equal((await f.post('/pair',{code:'wrong'})).status,401);
  response=await f.post('/pair',{code:pairing.code});assert.equal(response.status,200);
  assert.equal((await response.json()).token,config.phoneToken);
  assert.equal((await f.post('/pair',{code:pairing.code})).status,401);
  assert.equal((await fetch(f.base+'/pairing/'+pairing.code)).status,410);
 });

 test('phone connection diagnostics follow actual websocket connect and disconnect',async t => {
  const f=await fixture(t), tokens=await grant(f), client=await connectClient(t,f,tokens.access_token);
  const connection=async()=>JSON.parse((await client.callTool({name:'phone_connection',arguments:{}})).content[0].text);
  assert.equal((await connection()).connected,false);
  const phone=await connectPhone(t,f);
  assert.equal((await connection()).connected,true);
  phone.close();await once(phone,'close');
  assert.equal((await connection()).connected,false);
 });

 test('OAuth grants survive restart, persist only token hashes, and preserve revocation',async t => {
  const dir=mkdtempSync(join(tmpdir(),'phone-mcp-state-test-')), stateFile=join(dir,'oauth.private.json');
  t.after(()=>{for(const name of ['oauth.private.json','oauth.private.json.tmp']){const file=join(dir,name);if(existsSync(file))unlinkSync(file);}rmdirSync(dir);});
  const first=await fixture(t,{stateFile}), tokens=await grant(first);
  const saved=readFileSync(stateFile,'utf8');
  assert.equal(saved.includes(tokens.access_token),false);assert.equal(saved.includes(tokens.refresh_token),false);
  await first.bridge.close();
  const second=await fixture(t,{stateFile});
  const response=await second.post('/token',{grant_type:'refresh_token',client_id:tokens.client.client_id,refresh_token:tokens.refresh_token});
  assert.equal(response.status,200);const renewed=await response.json();
  await second.post('/revoke',{client_id:tokens.client.client_id,token:renewed.refresh_token});
  await second.bridge.close();
  const third=await fixture(t,{stateFile});
  assert.equal((await third.post('/mcp',{}, {Authorization:`Bearer ${renewed.access_token}`})).status,401);
 });
test('direct HTTP upload enforces authorization, write scope, type and size before contacting the phone',async t=>{
 const f=await fixture(t),writer=await grant(f),readonly=await grant(f,'phone.read');let received=0;
 await connectPhone(t,f,(m,s)=>{received++;s.send(JSON.stringify({id:m.id,ok:true,result:{bytesDone:m.args.offset+Buffer.from(m.args.data,'base64').length}}));});
 const target=f.base+'/uploads/9405dd7c-cdd2-45b3-8b1f-6157113483d7?offset=0';
 assert.equal((await fetch(target,{method:'PUT',body:Buffer.from('x')})).status,401);
 assert.equal((await fetch(target,{method:'PUT',headers:{Authorization:'Bearer '+readonly.access_token,'Content-Type':'application/octet-stream'},body:Buffer.from('x')})).status,403);
 assert.equal((await fetch(target,{method:'PUT',headers:{Authorization:'Bearer '+writer.access_token,'Content-Type':'application/json'},body:'{}'})).status,415);
 assert.equal((await fetch(target,{method:'PUT',headers:{Authorization:'Bearer '+writer.access_token,'Content-Type':'application/octet-stream'},body:Buffer.alloc(1048577)})).status,413);
 assert.equal(received,0);
 const r=await fetch(target,{method:'PUT',headers:{Authorization:'Bearer '+writer.access_token,'Content-Type':'application/octet-stream'},body:Buffer.alloc(300000,39)});
 assert.equal(r.status,200);assert.equal((await r.json()).bytesDone,300000);assert.equal(received,3);
});
test('local file sender recovers a lost HTTP response by checking the confirmed offset',async t=>{
 const f=await fixture(t),tokens=await grant(f);let received=Buffer.alloc(0),job;
 await connectPhone(t,f,(m,socket)=>{
  let result;
  if(m.op==='file_upload_begin')job={jobId:m.args.requestId,fileName:m.args.fileName,totalBytes:m.args.sizeBytes,expectedSha256:m.args.sha256,bytesDone:0,state:'receiving',uploadPath:'/uploads/'+m.args.requestId,maxRequestBytes:1048576};
  if(m.op==='file_upload_chunk'){
   const bytes=Buffer.from(m.args.data,'base64');assert.equal(m.args.offset,received.length);assert.equal(m.args._ownerKey.length,64);received=Buffer.concat([received,bytes]);job.bytesDone=received.length;
  }
  if(m.op==='file_upload_commit'){job.sha256=createHash('sha256').update(received).digest('hex');assert.equal(job.sha256,job.expectedSha256);job.state='completed';}
  result={...job};socket.send(JSON.stringify({id:m.id,ok:true,result}));
 });
 mkdirSync('runtime',{recursive:true});const folder=mkdtempSync(join('runtime','local-send-test-')),file=join(folder,'fichier test.bin'),bytes=Buffer.alloc(2097189);
 for(let i=0;i<bytes.length;i++)bytes[i]=i%251;writeFileSync(file,bytes);t.after(()=>{unlinkSync(file);rmdirSync(folder);});
 const previous=process.env.PHONE_MCP_ALLOW_LOCAL_HTTP;process.env.PHONE_MCP_ALLOW_LOCAL_HTTP='true';t.after(()=>{if(previous===undefined)delete process.env.PHONE_MCP_ALLOW_LOCAL_HTTP;else process.env.PHONE_MCP_ALLOW_LOCAL_HTTP=previous;});
 let lost=false;
 const receipt=await sendLocalFile(file,{endpoint:f.base,accessToken:tokens.access_token,fetchImpl:async(...args)=>{const response=await fetch(...args);if(!lost){lost=true;await response.arrayBuffer();throw Error('Simulated lost acknowledgement');}return response;}});
 assert.equal(lost,true);assert.equal(receipt.state,'completed');assert.deepEqual(received,bytes);assert.equal(receipt.bytesDone,bytes.length);
});
