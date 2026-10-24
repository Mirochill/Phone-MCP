import test from 'node:test';import assert from 'node:assert/strict';
import {Client} from '@modelcontextprotocol/sdk/client/index.js';import {InMemoryTransport} from '@modelcontextprotocol/sdk/inMemory.js';
import {makeMcp} from '../src/mcp.js';import {OperationScheduler} from '../src/command-channel.js';import {operationDefinitions} from '../src/operations.js';import {uiFlow,startApp,adResult} from '../src/fast-workflows.js';
const packageName='com.example.fixture',sessionId='9405dd7c-cdd2-45b3-8b1f-6157113483d8',snapshotId='9405dd7c-cdd2-45b3-8b1f-6157113483d7';
const selected={packageName,sessionId,_ownerKey:'owner'},click={op:'wait_and_click',query:'Next',by:'text',timeoutMs:1000};
const frame=()=>({packageName,sessionId,snapshotId,nodes:[{nodeId:'0',text:'Result'}]});const result=r=>JSON.parse(r.content.find(c=>c.type==='text').text);
async function client(t,command,{scopes=['phone.read','phone.write'],scheduler=new OperationScheduler()}={}){const server=makeMcp({scopes,family:'owner'},command,()=>({connected:true}),undefined,scheduler),c=new Client({name:'fast-flow',version:'1'}),[a,b]=InMemoryTransport.createLinkedPair();await server.connect(b);await c.connect(a);t.after(()=>c.close());t.after(()=>server.close());return c;}

test('fast schemas refuse unbounded steps, changed scope, arbitrary operations and writes through read gateway',async t=>{
 const defs=operationDefinitions(),flow=defs.find(d=>d.op==='ui_flow').schema,start=defs.find(d=>d.op==='start_app').schema;
 assert.equal(start.safeParse({packageName,requestId:sessionId,sessionId}).success,false);
 for(const steps of [[],Array(9).fill(click),[{...click,packageName:'com.other'}],[{op:'shell',query:'x'}],[{...click,query:' '}],[{...click,timeoutMs:9000}]])assert.equal(flow.safeParse({packageName,sessionId,steps}).success,false);
 let writes=0;const c=await client(t,async()=>{writes++;return {};});const denied=await c.callTool({name:'phone_read',arguments:{operation:'ui_flow',arguments:{packageName,sessionId,steps:[click]}}});assert.equal(denied.isError,true);assert.equal(writes,0);
 const readOnly=await client(t,async()=>{writes++;return {};},{scopes:['phone.read']});assert.equal((await readOnly.callTool({name:'phone_start_app',arguments:{packageName,requestId:sessionId}})).isError,true);assert.equal(writes,0);
});

test('start app combines owned creation, one launch, observation and unchanged-quality image into one host call',async t=>{
 const events=[];const c=await client(t,async(op,args)=>{events.push({op,args});if(op==='session_list')return {sessions:[]};if(op==='session_create')return {created:true,session:{packageName,sessionId}};if(op==='open_app')return {packageName,sessionId,opened:true};if(op==='snapshot')return frame();return {...frame(),mimeType:'image/jpeg',data:'AA=='};});
 const r=await c.callTool({name:'phone_start_app',arguments:{packageName,requestId:sessionId,settleMs:0,maxDimension:1920,quality:95}}),b=result(r);assert.equal(r.isError,undefined);assert.equal(b.opened,true);assert.equal(b.sessionId,sessionId);assert.deepEqual(events.map(x=>x.op),['session_list','session_create','open_app','snapshot','screenshot']);assert.equal(events.at(-1).args.quality,95);assert.equal(events.at(-1).args.maxDimension,1920);assert.equal(r.content[0].type,'image');assert.equal(b.relayTiming.deviceCommands,5);assert.equal(b.relayTiming.modelTimeIncluded,false);assert.ok(events.every(e=>e.args._ownerKey===events[0].args._ownerKey));
});

test('repeating start inspects same owned session without another create/open or resetting app state',async()=>{
 const events=[],r=await startApp(async(op,args)=>{events.push(op);return {sessions:[{packageName,sessionId,state:'active'}]};},{...selected,requestId:sessionId});assert.equal(r.reused,true);assert.equal(r.opened,false);assert.deepEqual(events,['session_list']);
 await assert.rejects(startApp(async()=>({sessions:[{packageName:'com.other',sessionId}]}),{...selected,requestId:sessionId}),/different application/);
});

test('launch failure preserves the known session and reports uncertainty without any retry or cleanup of other work',async()=>{
 const events=[],r=await startApp(async op=>{events.push(op);if(op==='session_list')return {sessions:[]};if(op==='session_create')return {session:{packageName,sessionId}};throw Error('network uncertain');},{...selected,requestId:sessionId});assert.equal(r.launchUncertain,true);assert.equal(r.sessionId,sessionId);assert.deepEqual(events,['session_list','session_create','open_app']);
});

test('semantic flow executes exact fresh targets then a single final observation and image',async t=>{
 const events=[],c=await client(t,async(op,args)=>{events.push({op,args});if(op==='wait_and_click')return {packageName,sessionId,found:true,performed:true};if(op==='snapshot')return frame();return {...frame(),mimeType:'image/jpeg',data:'AA=='};});
 const r=await c.callTool({name:'phone_ui_flow',arguments:{packageName,sessionId,steps:[click,{...click,query:'Done'}],settleMs:0,maxDimension:1920,quality:95}}),b=result(r);assert.equal(b.completed,true);assert.equal(b.completedSteps,2);assert.equal(b.performedActions,2);assert.deepEqual(events.map(e=>e.op),['wait_and_click','wait_and_click','snapshot','screenshot']);assert.equal(events[0].args.match,'exact');assert.equal(events[1].args.query,'Done');assert.equal(events.at(-1).args.quality,95);assert.equal(b.relayTiming.deviceCommands,4);
});

test('missing and ambiguous flow targets stop all subsequent writes; completed writes are never replayed',async()=>{
 for(const kind of ['timeout','ambiguous']){const events=[];const r=await uiFlow(async op=>{events.push(op);if(events.length===1)return {packageName,sessionId,found:true,performed:true};if(kind==='ambiguous')throw Error('Several targets');return {packageName,sessionId,found:false,performed:false,timedOut:true};},{...selected,steps:[click,click,click],timeoutMs:8000});assert.equal(r.completed,false);assert.equal(r.completedSteps,1);assert.equal(r.failedStep,1);assert.equal(events.length,2);assert.equal(r.replayed,false);}
});

test('flow deadline prevents remaining steps and cross-session action acknowledgements are refused',async()=>{
 let clock=0,calls=0;const r=await uiFlow(async()=>{calls++;clock+=150;return {packageName,sessionId,found:true,performed:true};},{...selected,steps:[click,click],timeoutMs:200},{now:()=>clock});assert.equal(r.failedStep,1);assert.equal(calls,1);
 const wrong=await uiFlow(async()=>({packageName,sessionId:snapshotId,found:true,performed:true}),{...selected,steps:[click,click],timeoutMs:500});assert.equal(wrong.completed,false);assert.equal(wrong.failedStep,0);assert.match(wrong.reason,/different session/);
});

test('one waiting flow does not block another session; cancellation rejects the queued next step',async t=>{
 const scheduler=new OperationScheduler(),events=[];let release;const wait=new Promise(r=>release=r);
 const c=await client(t,async(op,args)=>{events.push(op+':'+args.sessionId);if(op==='wait_and_click'&&args.sessionId===sessionId){await wait;return {packageName,sessionId,found:true,performed:true};}if(op==='snapshot')return {...frame(),sessionId:args.sessionId};return {ended:true,sessionId:args.sessionId};},{scheduler});
 const flow=c.callTool({name:'phone_ui_flow',arguments:{packageName,sessionId,steps:[click,click],includeScreenshot:false}});await new Promise(r=>setTimeout(r,5));
 const other=await c.callTool({name:'phone_snapshot',arguments:{packageName,sessionId:snapshotId}});assert.equal(other.isError,undefined);
 await c.callTool({name:'phone_session_cancel',arguments:{sessionId}});release();const b=result(await flow);assert.equal(b.completed,false);assert.equal(events.filter(x=>x.startsWith('wait_and_click:')).length,1);assert.equal(b.failedStep,1);
});

test('extended ad wait is bounded into native <=8s reads and returns final evidence without a write',async()=>{
 let clock=0;const events=[],jobId=snapshotId;
 const r=await adResult(async(op,args)=>{events.push({op,args});if(op==='ad_status'){clock+=args.waitMs;return {packageName,sessionId,jobId,active:clock<16000,state:clock<16000?'running':'completed',returnMatched:clock>=16000};}if(op==='snapshot')return frame();return {...frame(),mimeType:'image/jpeg',data:'AA=='};},'ad_status',{...selected,jobId,waitMs:20000,observe:true,includeScreenshot:true,maxDimension:1920,quality:95},{now:()=>clock});assert.equal(r.state,'completed');assert.equal(r.statusReads,2);assert.deepEqual(events.map(e=>e.op),['ad_status','ad_status','snapshot','screenshot']);assert.ok(events.filter(e=>e.op==='ad_status').every(e=>e.args.waitMs<=8000));assert.equal(r.screenshot.quality,undefined);assert.equal(events.at(-1).args.quality,95);assert.equal(r.returnMatched,true);
});

test('pending ad wait respects its entire budget and does not capture/restart/click',async()=>{
 let clock=0;const events=[],jobId=snapshotId;const r=await adResult(async(op,args)=>{events.push({op,args});clock+=args.waitMs;return {packageName,sessionId,jobId,active:true,state:'running'};},'ad_status',{...selected,jobId,waitMs:20000,observe:true,includeScreenshot:true},{now:()=>clock});assert.deepEqual(events.map(x=>x.args.waitMs),[8000,8000,4000]);assert.equal(r.observationDeferred,true);assert.equal(r.observation,undefined);
});

test('final ad capture mismatch fails as evidence, without claiming reward or replaying inputs',async()=>{
 const jobId=snapshotId,events=[];const r=await adResult(async op=>{events.push(op);if(op==='ad_status')return {packageName,sessionId,jobId,state:'completed',active:false,returnMatched:true};if(op==='snapshot')return frame();return {...frame(),snapshotId:sessionId,mimeType:'image/jpeg',data:'AA=='};},'ad_status',{...selected,jobId,waitMs:0,observe:true,includeScreenshot:true});assert.match(r.screenshotError,/snapshot mismatch/);assert.equal(r.screenshot,undefined);assert.match(r.instruction,/never a reward/);assert.deepEqual(events,['ad_status','snapshot','screenshot']);
});


test('clickable-parent evidence prevents label-only clicks without guessing missing, disabled, hidden or protected nodes',async()=>{
 const {compactSnapshot}=await import('../src/screen-output.js');
 const parent={nodeId:'0',clickable:true,bounds:[0,0,100,100]},label={nodeId:'0.1',parent:'0',text:'Plus',bounds:[10,10,70,30]};
 assert.equal(compactSnapshot({nodes:[parent,label]}).nodes[1].clickNodeId,'0');
 for(const changed of [{...parent,enabled:false},{...parent,visible:false},{...parent,bounds:[20,20,30,30]},{...parent,text:'[champ protégé]'}])assert.equal(compactSnapshot({nodes:[changed,label]}).nodes[1].clickNodeId,undefined);
 assert.equal(compactSnapshot({nodes:[label]}).nodes[0].clickNodeId,undefined);
 assert.equal(compactSnapshot({nodes:[parent,{...label,text:'[champ protégé]'}]}).nodes[1].clickNodeId,undefined);
 assert.equal(compactSnapshot({nodes:[parent,{...label,clickable:true}]}).nodes[1].clickNodeId,undefined);
});

test('timing history is owner-scoped, content-free, bounded and expires without a background timer',()=>{
 const ledger=new OperationScheduler(),now=Date.now(),event={operation:'ui_flow',startedAt:now-1,completedAt:now,totalMs:1,deviceMs:1,deviceCommands:2,queueMs:0,success:true,secret:'not retained',packageName:'not retained'};
 for(let i=0;i<60;i++)ledger.recordTiming('owner',event);
 const rows=ledger.recentTimings('owner');assert.equal(rows.length,48);assert.equal(rows[0].secret,undefined);assert.equal(rows[0].packageName,undefined);assert.deepEqual(ledger.recentTimings('other'),[]);rows[0].operation='tampered';assert.equal(ledger.recentTimings('owner')[0].operation,'ui_flow');assert.deepEqual(ledger.recentTimings('owner',now+300001),[]);
 for(let i=0;i<40;i++)ledger.recordTiming('other'+i,event);assert.equal(ledger.timingHistory.size,32);assert.deepEqual(ledger.recentTimings('owner'),[]);
});
