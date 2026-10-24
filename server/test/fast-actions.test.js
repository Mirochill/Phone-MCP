import test from 'node:test';
import assert from 'node:assert/strict';
import {Client} from '@modelcontextprotocol/sdk/client/index.js';
import {InMemoryTransport} from '@modelcontextprotocol/sdk/inMemory.js';
import {makeMcp} from '../src/mcp.js';
import {operationDefinitions} from '../src/operations.js';
import {commandWithObservation} from '../src/screen-output.js';
import {OperationScheduler} from '../src/command-channel.js';
const packageName='com.example.fixture',snapshotId='9405dd7c-cdd2-45b3-8b1f-6157113483d7',sessionId='9405dd7c-cdd2-45b3-8b1f-6157113483d8';
const steps=[{op:'tap',x:120,y:150},{op:'tap',x:320,y:250}];
const selection={packageName,snapshotId,sessionId,steps,intervalMs:0,timeoutMs:4000,settleMs:0};
const frame=()=>({packageName,snapshotId,sessionId,nodes:[{nodeId:'0.1',text:'Result',enabled:true}]});
const data=result=>JSON.parse(result.content.find(c=>c.type==='text').text);
async function fixture(t,command,scopes=['phone.read','phone.write'],scheduler){
 const server=makeMcp({scopes,family:'owner'},command,()=>({connected:true}),undefined,scheduler);
 const client=new Client({name:'fast-client',version:'1'}),[a,b]=InMemoryTransport.createLinkedPair();
 await server.connect(b);await client.connect(a);t.after(()=>client.close());t.after(()=>server.close());return client;
}
test('sequence catalog is strict, bounded and cannot smuggle a different session, package, node or operation',()=>{
 const def=operationDefinitions().find(d=>d.op==='action_sequence');assert.equal(def.write,true);
 for(const invalid of [[],Array(9).fill(steps[0]),[{...steps[0],sessionId}],[{...steps[0],packageName:'com.other'}],[{op:'click',nodeId:'0.1'}],[{op:'stop'}],[{op:'tap',x:-1,y:2}],[{op:'swipe',fromX:0,fromY:0,toX:1,toY:1,durationMs:20}]])assert.equal(def.schema.safeParse({...selection,steps:invalid}).success,false);
 assert.equal(def.schema.safeParse(selection).success,true);assert.equal(def.schema.safeParse({...selection,timeoutMs:8001}).success,false);
 const parsed=def.schema.parse({...selection,steps:[{op:'swipe',fromX:1,fromY:2,toX:3,toY:4}]});assert.equal(parsed.steps[0].durationMs,150);
});
test('sequence uses one native write, one final read and one optional capture without intermediate network commands',async t=>{
 const seen=[],client=await fixture(t,async(op,args)=>{seen.push({op,args});if(op==='snapshot')return frame();if(op==='screenshot')return {...frame(),mimeType:'image/jpeg',data:'aW1hZ2U='};return {completed:true,executedCount:2,totalSteps:2,sessionId};});
 const result=await client.callTool({name:'phone_action',arguments:{operation:'action_sequence',arguments:{...selection,includeScreenshot:true,maxDimension:1600,quality:95,annotate:true}}});
 assert.equal(result.isError,undefined);assert.deepEqual(seen.map(c=>c.op),['action_sequence','snapshot','screenshot']);
 assert.equal(seen[0].args.steps.length,2);assert.equal(seen[0].args.includeScreenshot,undefined);assert.equal(seen[0].args.observe,undefined);assert.equal(seen[0].args.quality,undefined);
 assert.equal(seen[2].args.snapshotId,snapshotId);assert.equal(seen[2].args.quality,95);assert.equal(seen[2].args.maxDimension,1600);
 assert.ok(seen.every(c=>c.args.sessionId===sessionId&&c.args._ownerKey===seen[0].args._ownerKey));
 assert.equal(result.content[0].type,'image');assert.equal(data(result).completed,true);assert.equal(data(result).observation.nodes[0].text,'Result');
});
test('partial sequence returns precise progress and is never repeated when the final observation fails',async()=>{
 const seen=[];const partial={completed:false,executedCount:1,totalSteps:2,failedStep:1,error:'Target changed',steps:[{index:0,performed:true}]};
 const result=await commandWithObservation(async op=>{seen.push(op);if(op==='snapshot')throw Error('Session stopped');return partial;},'action_sequence',selection);
 assert.deepEqual(seen,['action_sequence','snapshot','snapshot']);assert.equal(result.completed,false);assert.equal(result.executedCount,1);assert.equal(result.failedStep,1);assert.equal(result.error,'Target changed');assert.match(result.observationError,/Session stopped/);
});
test('mismatched app/session observations and captures are never returned as the active result',async()=>{
 for(const mismatch of [{packageName:'com.other'},{sessionId:'9405dd7c-cdd2-45b3-8b1f-6157113483d9'}]){
  const seen=[];const result=await commandWithObservation(async op=>{seen.push(op);return op==='snapshot'?{...frame(),...mismatch}:{completed:true};},'action_sequence',selection);
  assert.equal(result.observation,undefined);assert.match(result.observationError,/different/);assert.equal(seen.filter(x=>x==='action_sequence').length,1);
 }
 const result=await commandWithObservation(async op=>op==='snapshot'?frame():op==='screenshot'?{snapshotId:'wrong',mimeType:'image/jpeg',data:'aW1hZ2U='}:{completed:true},'action_sequence',{...selection,includeScreenshot:true});
 assert.equal(result.screenshot,undefined);assert.match(result.screenshotError,/different snapshot/);assert.equal(result.completed,true);assert.equal(result.observation.snapshotId,snapshotId);
});
test('oversized time budget and impossible capture options reject before any write',async()=>{
 let commands=0;const command=async()=>{commands++;return {completed:true};};
 await assert.rejects(commandWithObservation(command,'action_sequence',{...selection,steps:Array(8).fill({op:'swipe',fromX:1,fromY:2,toX:3,toY:4,durationMs:1000}),intervalMs:250,timeoutMs:8000}),/exceed/);
 await assert.rejects(commandWithObservation(command,'action_sequence',{...selection,includeScreenshot:true,observe:false}),/observe=true/);assert.equal(commands,0);
});
test('sequence OAuth and read gateway guards reject before sending any device command',async t=>{
 let commands=0;const client=await fixture(t,async()=>{commands++;return frame();},['phone.read']);
 for(const name of ['phone_read','phone_action'])assert.equal((await client.callTool({name,arguments:{operation:'action_sequence',arguments:selection}})).isError,true);
 assert.equal(commands,0);
});
test('cancel interrupts a running native sequence and invalidates queued sequences, preserving partial delivery',async t=>{
 const scheduler=new OperationScheduler(),seen=[];let release;const wait=new Promise(resolve=>release=resolve);
 const client=await fixture(t,async op=>{seen.push(op);if(op==='action_sequence'){await wait;return {completed:false,executedCount:1,totalSteps:2,failedStep:1,error:'Session stopped'};}return {stopped:true};},undefined,scheduler);
 const first=client.callTool({name:'phone_action_sequence',arguments:selection});
 await new Promise(resolve=>setTimeout(resolve,5));const queued=client.callTool({name:'phone_action_sequence',arguments:selection});
 const cancelled=await client.callTool({name:'phone_action',arguments:{operation:'session_cancel',arguments:{sessionId}}});assert.equal(cancelled.isError,undefined);release();
 const one=await first;assert.equal(one.isError,undefined);assert.equal(data(one).executedCount,1);assert.match(data(one).observationError,/arrêtée/);
 assert.equal((await queued).isError,true);assert.deepEqual(seen,['action_sequence','session_cancel']);
});
test('local wait-and-click requires exact matching and returns timeout without claiming a performed click',async t=>{
 const seen=[],client=await fixture(t,async(op,args)=>{seen.push({op,args});return op==='snapshot'?frame():{found:false,timedOut:true,performed:false};});
 const result=await client.callTool({name:'phone_action',arguments:{operation:'wait_and_click',arguments:{packageName,sessionId,query:'Close',timeoutMs:500}}});
 assert.equal(result.isError,undefined);assert.equal(data(result).performed,false);assert.equal(data(result).found,false);assert.equal(data(result).timedOut,true);assert.deepEqual(seen.map(c=>c.op),['wait_and_click','snapshot']);assert.equal(seen[0].args.match,'exact');
 const def=operationDefinitions().find(d=>d.op==='wait_and_click');
 for(const extra of [{match:'contains'},{by:'any'},{timeoutMs:8001},{query:''}])assert.equal(def.schema.safeParse({packageName,query:'Close',...extra}).success,false);
});
test('a normal action can return a screenshot without changing its requested capture quality',async()=>{
 const seen=[];const result=await commandWithObservation(async(op,args)=>{seen.push({op,args});return op==='snapshot'?frame():op==='screenshot'?{snapshotId,mimeType:'image/png',data:'aW1hZ2U='}:{performed:true};},'tap',{packageName,sessionId,snapshotId,x:1,y:2,includeScreenshot:true,maxDimension:1920,quality:95,settleMs:0});
 assert.equal(result.performed,true);assert.deepEqual(seen.map(c=>c.op),['tap','snapshot','screenshot']);assert.equal(seen[2].args.quality,95);assert.equal(seen[2].args.maxDimension,1920);assert.equal(result.screenshot.mimeType,'image/png');
});
test('direct screenshots retain capture quality options after shared output handling',async()=>{
 let seen;await commandWithObservation(async(op,args)=>{seen={op,args};return {mimeType:'image/jpeg',data:'aW1hZ2U='};},'screenshot',{packageName,snapshotId,maxDimension:1920,quality:95,annotate:true});assert.equal(seen.args.quality,95);assert.equal(seen.args.maxDimension,1920);assert.equal(seen.args.annotate,true);
});
test('awake fallback requires an explicit session, authorized app and one supported failure reason',async t=>{
 const seen=[],client=await fixture(t,async(op,args)=>{seen.push({op,args});return {requested:true,sessionId:args.sessionId};});
 const def=operationDefinitions().find(d=>d.op==='session_wake');assert.equal(def.write,true);assert.equal(def.ui,false);
 for(const invalid of [{packageName},{sessionId},{packageName,sessionId,reason:'any'},{packageName,sessionId,reason:'unlock'},{packageName,sessionId,snapshotId},{packageName,sessionId,_ownerKey:'spoof'}]){
  const result=await client.callTool({name:'phone_action',arguments:{operation:'session_wake',arguments:invalid}});assert.equal(result.isError,true);
 }
 assert.equal(seen.length,0);
 const good=await client.callTool({name:'phone_action',arguments:{operation:'session_wake',arguments:{packageName,sessionId,reason:'black_render'}}});assert.equal(good.isError,undefined);assert.deepEqual(seen.map(c=>c.op),['session_wake']);assert.equal(seen[0].args.packageName,packageName);assert.equal(seen[0].args.sessionId,sessionId);assert.equal(seen[0].args.reason,'black_render');
 const read=await fixture(t,async()=>{throw Error('Unexpected write');},['phone.read']);assert.equal((await read.callTool({name:'phone_action',arguments:{operation:'session_wake',arguments:{packageName,sessionId}}})).isError,true);
});