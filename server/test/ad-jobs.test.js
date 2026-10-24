import test from 'node:test';
import assert from 'node:assert/strict';
import {Client} from '@modelcontextprotocol/sdk/client/index.js';
import {InMemoryTransport} from '@modelcontextprotocol/sdk/inMemory.js';
import {makeMcp} from '../src/mcp.js';
import {operationDefinitions} from '../src/operations.js';
import {runOperation} from '../src/automation.js';
import {CommandChannel,OperationScheduler} from '../src/command-channel.js';
const packageName='com.example.fixture',sessionId='9405dd7c-cdd2-45b3-8b1f-6157113483d8',jobId='9405dd7c-cdd2-45b3-8b1f-6157113483d7';
const startArgs={packageName,sessionId,requestId:jobId,returnTargets:[{query:'Rewards'}]},selected={packageName,sessionId,jobId};
const result=(extra={})=>({packageName,sessionId,jobId,state:'running',actions:[],elapsedMs:0,returnMatched:false,...extra});
const data=r=>JSON.parse(r.content.find(c=>c.type==='text').text),delay=ms=>new Promise(r=>setTimeout(r,ms));
async function fixture(t,command,scopes=['phone.read','phone.write'],scheduler){const server=makeMcp({scopes,family:'ad-owner'},command,()=>({connected:true}),undefined,scheduler),client=new Client({name:'ad-client',version:'1'}),[a,b]=InMemoryTransport.createLinkedPair();await server.connect(b);await client.connect(a);t.after(()=>client.close());t.after(()=>server.close());return client;}

test('ad schemas strictly bind sessions, targets and local execution budgets',()=>{
 const defs=new Map(operationDefinitions().map(d=>[d.op,d])),start=defs.get('ad_start'),parsed=start.schema.parse(startArgs);assert.equal(start.write,true);assert.equal(start.ui,false);assert.equal(parsed.timeoutMs,90000);assert.equal(parsed.maxActions,8);assert.equal(parsed.visual,true);assert.deepEqual(parsed.returnTargets,[{query:'Rewards',by:'text',match:'exact'}]);
 for(const extra of [{sessionId:undefined},{packageName:undefined},{requestId:'bad'},{timeoutMs:999},{timeoutMs:180001},{maxActions:0},{maxActions:17},{visual:'yes'},{snapshotId:jobId},{_ownerKey:'spoof'},{returnTargets:[]},{returnTargets:Array(7).fill({query:'x'})},{returnTargets:[{query:''}]},{returnTargets:[{query:'   '}]},{returnTargets:[{query:'x'.repeat(251)}]},{returnTargets:[{query:'x',by:'any'}]},{returnTargets:[{query:'0',by:'windowId'}]},{returnTargets:[{query:'2147483648',by:'windowId'}]},{returnTargets:[{query:'5',by:'windowId',match:'contains'}]},{returnTargets:[{query:'x',x:12,y:20}]}])assert.equal(start.schema.safeParse({...startArgs,...extra}).success,false);
 assert.equal(start.schema.safeParse({...startArgs,returnTargets:[{query:'Rewards',by:'description',match:'contains'}],timeoutMs:180000,maxActions:16,visual:false}).success,true);
 assert.equal(start.schema.safeParse({...startArgs,returnTargets:[{query:'2534',by:'windowId'}]}).success,true); const status=defs.get('ad_status');assert.equal(status.write,false);assert.equal(status.schema.parse(selected).waitMs,0);for(const extra of [{waitMs:-1},{waitMs:8001},{jobId:'wrong'},{snapshotId:jobId},{sessionId:undefined}])assert.equal(status.schema.safeParse({...selected,...extra}).success,false);assert.equal(defs.get('ad_cancel').write,true);assert.equal(defs.get('ad_cancel').schema.safeParse({...selected,waitMs:1}).success,false);
});

test('named and cached gateways relay one native command and preserve partial job state',async t=>{
 const seen=[],client=await fixture(t,async(op,args)=>{seen.push({op,args});return result(op==='ad_status'?{state:'blocked',actions:[{type:'close',performed:true}],elapsedMs:456,reason:'ambiguous_close'}:op==='ad_cancel'?{state:'cancelled'}:{});});
 const start=await client.callTool({name:'phone_ad_start',arguments:startArgs});assert.equal(start.isError,undefined);const status=await client.callTool({name:'phone_read',arguments:{operation:'ad_status',arguments:{...selected,waitMs:8000}}});assert.equal(data(status).state,'blocked');assert.equal(data(status).actions.length,1);assert.equal(data(status).reason,'ambiguous_close');
 const cancelled=await client.callTool({name:'phone_action',arguments:{operation:'ad_cancel',arguments:selected}});assert.equal(data(cancelled).state,'cancelled');assert.deepEqual(seen.map(x=>x.op),['ad_start','ad_status','ad_cancel']);assert.equal(seen[0].args.visual,true);assert.equal(seen[1].args.waitMs,8000);assert.ok(seen.every(x=>x.args.sessionId===sessionId&&x.args.packageName===packageName&&x.args._ownerKey===seen[0].args._ownerKey));assert.match(seen[0].args._ownerKey,/^[a-f0-9]{64}$/);
 const tools=(await client.listTools()).tools;assert.equal(tools.length,59);assert.ok(['phone_ad_start','phone_ad_status','phone_ad_cancel'].every(name=>tools.some(v=>v.name===name)));assert.ok(Buffer.byteLength(JSON.stringify(data(start).tooling))<2000);assert.match(data(start).tooling.instruction,/prefer phone_ad_start/);assert.match(data(status).instruction,/never a reward/);
});

test('read scope can inspect ad jobs but cannot start or cancel them',async t=>{
 let calls=0;const client=await fixture(t,async()=>{calls++;return result();},['phone.read']);for(const [operation,args] of [['ad_start',startArgs],['ad_cancel',selected]])for(const tool of ['phone_read','phone_action'])assert.equal((await client.callTool({name:tool,arguments:{operation,arguments:args}})).isError,true);assert.equal(calls,0);assert.equal((await client.callTool({name:'phone_ad_status',arguments:selected})).isError,undefined);assert.equal(calls,1);
});

test('job response app/session/ID mismatches are rejected without retries or extra reads',async()=>{
 for(const op of ['ad_start','ad_status','ad_cancel'])for(const mismatch of [{packageName:'com.other'},{sessionId:'9405dd7c-cdd2-45b3-8b1f-6157113483d9'},{jobId:'9405dd7c-cdd2-45b3-8b1f-6157113483d9'}]){let calls=0;await assert.rejects(runOperation(async()=>{calls++;return result(mismatch);},op,op==='ad_start'?startArgs:selected),/different/);assert.equal(calls,1);}
 for(const op of ['ad_start','ad_status','ad_cancel']){let calls=0;await assert.rejects(runOperation(async()=>{calls++;throw Error('Aucune fenêtre sur l’affichage parallèle.');},op,op==='ad_start'?startArgs:selected),/Aucune fenêtre/);assert.equal(calls,1);}
});

test('job status and cancellation bypass busy lanes without marking the session ended',async t=>{
 const scheduler=new OperationScheduler(),seen=[];let releaseAction,releaseStatus;const action=new Promise(r=>releaseAction=r),poll=new Promise(r=>releaseStatus=r);const client=await fixture(t,async op=>{seen.push(op);if(op==='tap'){await action;return {performed:true};}if(op==='ad_status')await poll;return result({state:op==='ad_cancel'?'cancelled':'running'});},undefined,scheduler);
 const running=client.callTool({name:'phone_tap',arguments:{packageName,sessionId,snapshotId:jobId,x:1,y:1,observe:false}});await delay(5);const one=client.callTool({name:'phone_ad_status',arguments:{...selected,waitMs:8000}}),two=client.callTool({name:'phone_ad_status',arguments:selected});await delay(5);const cancel=await client.callTool({name:'phone_ad_cancel',arguments:selected});assert.equal(cancel.isError,undefined);assert.equal(data(cancel).state,'cancelled');assert.equal(seen.filter(v=>v==='ad_status').length,2);assert.equal(scheduler.ended.size,0);releaseStatus();await Promise.all([one,two]);releaseAction();await running;
 assert.equal((await client.callTool({name:'phone_tap',arguments:{packageName,sessionId,snapshotId:jobId,x:2,y:2,observe:false}})).isError,undefined);assert.equal(seen.filter(v=>v==='tap').length,2);
});

test('transport changes and ended sessions invalidate bypass commands before device submission',async()=>{
 const scheduler=new OperationScheduler(),args={...selected,_ownerKey:'one'};let calls=0;const queued=scheduler.run('ad_status',args,async guard=>guard(()=>calls++));scheduler.disconnect();await assert.rejects(queued,/Transport/);assert.equal(calls,0);await scheduler.run('session_cancel',args,async()=>{});await assert.rejects(scheduler.run('ad_cancel',args,async()=>calls++),/Session/);assert.equal(calls,0);
});

test('ad job lifetime is asynchronous and status long polls have a bounded transport budget',async()=>{
 const channel=new CommandChannel(100),messages=[],ws={readyState:1,send:text=>messages.push(JSON.parse(text))},before=Date.now(),start=channel.send(ws,'ad_start',{...startArgs,timeoutMs:180000});assert.ok(messages[0].expiresAt-before<=150);channel.receive(ws,{id:messages[0].id,ok:true,result:result()});await start;const status=channel.send(ws,'ad_status',{...selected,waitMs:8000});assert.ok(messages[1].expiresAt-Date.now()>8500);assert.ok(messages[1].expiresAt-Date.now()<=9000);channel.receive(ws,{id:messages[1].id,ok:true,result:result()});await status;assert.equal(channel.pending.size,0);
});

test('ad cancellation uses reserved capacity when ordinary command slots are full',async()=>{
 const channel=new CommandChannel(1000),messages=[],ws={readyState:1,send:text=>messages.push(JSON.parse(text))},pending=Array.from({length:16},()=>channel.send(ws,'ad_status',selected)),settled=Promise.allSettled(pending);await assert.rejects(channel.send(ws,'ad_start',startArgs),/Limite/);const cancel=channel.send(ws,'ad_cancel',selected),m=messages.at(-1);assert.equal(m.op,'ad_cancel');channel.receive(ws,{id:m.id,ok:true,result:result({state:'cancelled'})});assert.equal((await cancel).state,'cancelled');channel.disconnect(ws);assert.ok((await settled).every(v=>v.status==='rejected'));
});