import test from 'node:test';
import assert from 'node:assert/strict';
import {CommandChannel,OperationScheduler} from '../src/command-channel.js';
import {makeMcp} from '../src/mcp.js';
import {Client} from '@modelcontextprotocol/sdk/client/index.js';
import {InMemoryTransport} from '@modelcontextprotocol/sdk/inMemory.js';
const delay=ms=>new Promise(r=>setTimeout(r,ms));
const ids=['8507c370-f1c3-4b64-914b-521c59592ac1','8507c370-f1c3-4b64-914b-521c59592ac2','8507c370-f1c3-4b64-914b-521c59592ac3'];
const snapshotId='8507c370-f1c3-4b64-914b-521c59592ab1',packageName='com.example.calc';
const data=r=>JSON.parse(r.content.find(x=>x.type==='text').text);
async function client(t,command,scheduler,grant={scopes:['phone.read','phone.write'],family:'owner-a'}){
 const server=makeMcp(grant,command,()=>({connected:true}),undefined,scheduler),c=new Client({name:'agent',version:'1'}),[a,b]=InMemoryTransport.createLinkedPair();
 await server.connect(b);await c.connect(a);t.after(()=>c.close());t.after(()=>server.close());return c;
}
test('three out-of-order device replies retain their UUID, socket, session and application',async()=>{
 const channel=new CommandChannel(1000),messages=[],ws={readyState:1,send:text=>messages.push(JSON.parse(text))};
 const calls=ids.map((sessionId,i)=>channel.send(ws,'snapshot',{sessionId,packageName:'com.example.app'+i}));
 channel.receive({}, {id:messages[0].id,ok:true,result:{wrong:true}});
 for(const i of [2,0,1])channel.receive(ws,{id:messages[i].id,ok:true,result:messages[i].args});
 assert.deepEqual(await Promise.all(calls),ids.map((sessionId,i)=>({sessionId,packageName:'com.example.app'+i})));assert.equal(channel.pending.size,0);
});
test('a whole action and observation serialize within one session while another agent progresses',async t=>{
 const scheduler=new OperationScheduler(),events=[];
 const command=async(op,args)=>{events.push(op+':'+args.sessionId);if(op==='click')await delay(40);return op==='snapshot'?{snapshotId,packageName,sessionId:args.sessionId,nodes:[{nodeId:'0.1',text:args.sessionId}]}:{performed:true,sessionId:args.sessionId};};
 const c=await client(t,command,scheduler);
 const requests=[ids[0],ids[0],ids[1]].map(sessionId=>c.callTool({name:'phone_click',arguments:{sessionId,packageName,snapshotId,nodeId:'0.1',settleMs:0}}));
 const results=await Promise.all(requests);assert.ok(results.every(r=>!r.isError));assert.deepEqual(results.map(r=>data(r).observation.sessionId),[ids[0],ids[0],ids[1]]);
 assert.deepEqual(events.filter(e=>e.endsWith(ids[0])),['click:','snapshot:','click:','snapshot:'].map(e=>e+ids[0]));
 assert.ok(events.indexOf('click:'+ids[1])<events.indexOf('snapshot:'+ids[0]));
});
test('session cancellation bypasses a pending wait and discards queued actions without replay',async t=>{
 const scheduler=new OperationScheduler(),events=[];let release;
 const blocked=new Promise(r=>release=r);
 const c=await client(t,async(op,args)=>{events.push(op);if(op==='click')await blocked;return {performed:true,sessionId:args.sessionId};},scheduler);
 const first=c.callTool({name:'phone_click',arguments:{sessionId:ids[0],packageName,snapshotId,nodeId:'0.1',settleMs:0}});
 await delay(10);const queued=c.callTool({name:'phone_click',arguments:{sessionId:ids[0],packageName,snapshotId,nodeId:'0.1'}});
 const stop=await c.callTool({name:'phone_action',arguments:{operation:'session_cancel',arguments:{sessionId:ids[0]}}});assert.ok(!stop.isError);release();
 assert.ok((await first).isError||data(await first).observationError);assert.equal((await queued).isError,true);assert.deepEqual(events,['click','session_cancel']);
});
test('owner identity cannot be spoofed and is retained by combined captures and wait observations',async t=>{
 const scheduler=new OperationScheduler(),seen=[];
 const c=await client(t,async(op,args)=>{seen.push(args);return op==='screenshot'?{mimeType:'image/jpeg',data:'aW1hZ2U='}:{snapshotId,sessionId:args.sessionId,packageName,nodes:[]};},scheduler);
 const bad=await c.callTool({name:'phone_read',arguments:{operation:'snapshot',arguments:{sessionId:ids[0],packageName,_ownerKey:'other'}}});assert.equal(bad.isError,true);assert.equal(seen.length,0);
 await c.callTool({name:'phone_snapshot',arguments:{sessionId:ids[0],packageName,includeScreenshot:true}});
 await c.callTool({name:'phone_wait_for_idle',arguments:{sessionId:ids[1],packageName,stableMs:250,timeoutMs:500}});
 assert.equal(seen[0]._ownerKey,seen[1]._ownerKey);assert.equal(seen[1].sessionId,ids[0]);assert.ok(seen.slice(2).every(x=>x.sessionId===ids[1]&&x._ownerKey===seen[0]._ownerKey));
});
test('disconnect rejects every pending command and late results cannot satisfy a new request',async()=>{
 const channel=new CommandChannel(1000),messages=[],ws={readyState:1,send:text=>messages.push(JSON.parse(text))};
 const a=channel.send(ws,'snapshot',{}),b=channel.send(ws,'click',{}),settled=Promise.allSettled([a,b]);channel.disconnect(ws);assert.ok((await settled).every(r=>r.status==='rejected'));
 const c=channel.send(ws,'snapshot',{new:true});channel.receive(ws,{id:messages[0].id,ok:true,result:{old:true}});channel.receive(ws,{id:messages[2].id,ok:true,result:{new:true}});assert.deepEqual(await c,{new:true});
});

test('cold display recovery retries only missing-window reads; stop, permission and writes never retry',async()=>{
 const {recoverRead}=await import('../src/read-recovery.js');let reads=0;
 const value=await recoverRead(async()=>{if(++reads<3)throw Error('Aucune fenêtre sur l’affichage parallèle.');return {ready:true};},'snapshot',{});assert.deepEqual(value,{ready:true});assert.equal(reads,3);
 for(const [op,message] of [['click','Aucune fenêtre'],['snapshot','Session stopped'],['screenshot','Capture refused: permission']]){let attempts=0;await assert.rejects(recoverRead(async()=>{attempts++;throw Error(message);},op,{}));assert.equal(attempts,1);}
});


test('transport change discards queued actions and prevents the old post-action observation',async()=>{
 const scheduler=new OperationScheduler(),events=[],args={_ownerKey:'one',sessionId:ids[0]};let release;
 const wait=new Promise(ok=>release=ok);
 const first=scheduler.run('click',args,async guard=>{events.push('click');await wait;return guard(()=>events.push('old-observation'));});
 await delay(5);const queued=scheduler.run('click',args,async()=>events.push('queued-old-click'));
 const results=Promise.allSettled([first,queued]);scheduler.disconnect();release();
 assert.ok((await results).every(x=>x.status==='rejected'));assert.deepEqual(events,['click']);
 await scheduler.run('snapshot',args,async()=>events.push('new-read'));assert.deepEqual(events,['click','new-read']);
});
