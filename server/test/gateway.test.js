import test from 'node:test';
import assert from 'node:assert/strict';
import {Client} from '@modelcontextprotocol/sdk/client/index.js';
import {InMemoryTransport} from '@modelcontextprotocol/sdk/inMemory.js';
import {makeMcp} from '../src/mcp.js';
import {runOperation} from '../src/automation.js';
const packageName='com.example.fixture',snapshotId='9405dd7c-cdd2-45b3-8b1f-6157113483d7';
const frame=(nodes=[])=>({snapshotId,packageName,nodes,contentFingerprint:'same'});
async function fixture(t,scopes=['phone.read','phone.write'],command=async()=>frame()){
  const server=makeMcp({scopes},command,()=>({connected:true,connectedSince:null,commandInProgress:false,serverVersion:'0.9.0'}));
  const client=new Client({name:'stable-client',version:'1'}),[a,b]=InMemoryTransport.createLinkedPair();
  await server.connect(b);await client.connect(a);t.after(()=>client.close());t.after(()=>server.close());return client;
}
const data=result=>JSON.parse(result.content.find(x=>x.type==='text').text);
test('stable catalog gives validated current schemas and a change token without contacting the phone',async t=>{
  const client=await fixture(t,undefined,async()=>{throw Error('Unexpected device command');});
  const catalog=data(await client.callTool({name:'phone_catalog',arguments:{}}));assert.equal(catalog.apiVersion,1);assert.ok(catalog.operations.some(op=>op.operation==='pinch'));
  const selected=data(await client.callTool({name:'phone_catalog',arguments:{operation:'pinch'}}));assert.ok(selected.operation.inputSchema.required.includes('snapshotId'));
  assert.equal(data(await client.callTool({name:'phone_catalog',arguments:{knownVersion:catalog.catalogVersion}})).unchanged,true);
});
test('a client importing only stable gateways can use later native operations through the actual MCP protocol',async t=>{
  const seen=[],client=await fixture(t,undefined,async(op,args)=>{seen.push({op,args});return op==='snapshot'?frame([{nodeId:'0.1',text:'42',visible:true}]):{performed:true};});
  const result=await client.callTool({name:'phone_action',arguments:{operation:'edit_text',arguments:{packageName,snapshotId,nodeId:'0.1',mode:'append',text:'2',settleMs:0}}});
  assert.equal(result.isError,undefined);assert.deepEqual(seen.map(x=>x.op),['edit_text','snapshot']);assert.equal(data(result).observation.nodes[0].text,'42');
});
test('read gateway rejects writes and invalid or unknown operations before any device side effect',async t=>{
  let commands=0;const client=await fixture(t,undefined,async()=>{commands++;return frame();});
  for(const request of [
    {name:'phone_read',arguments:{operation:'stop',arguments:{}}},
    {name:'phone_action',arguments:{operation:'pinch',arguments:{packageName,snapshotId,centerX:10,centerY:10,startDistance:-1,endDistance:100}}},
    {name:'phone_action',arguments:{operation:'__proto__',arguments:{}}},
    {name:'phone_read',arguments:{operation:'read_file',arguments:{locationId:snapshotId,path:'../private.txt'}}}
  ])assert.equal((await client.callTool(request)).isError,true);
  assert.equal(commands,0);
});
test('stable action gateway preserves OAuth enforcement independently of tool annotations',async t=>{
  let commands=0;const client=await fixture(t,['phone.read'],async()=>{commands++;return frame();});
  const result=await client.callTool({name:'phone_action',arguments:{operation:'double_tap',arguments:{packageName,snapshotId,x:100,y:100}}});assert.equal(result.isError,true);assert.equal(commands,0);
});
test('a failed post-action observation never repeats a stable gateway write',async t=>{
  let writes=0;const client=await fixture(t,undefined,async op=>{if(op==='snapshot')throw Error('No observation');writes++;return {performed:true};});
  const result=data(await client.callTool({name:'phone_action',arguments:{operation:'press_key',arguments:{packageName,snapshotId,nodeId:'0.1',settleMs:0}}}));assert.equal(writes,1);assert.equal(result.performed,true);assert.match(result.observationError,/No observation/);
});
test('element waiting returns timeout explicitly and never triggers an action',async()=>{
  const calls=[];const result=await runOperation(async op=>{calls.push(op);return frame();},'wait_for',{packageName,query:'absent',timeoutMs:100});assert.equal(result.found,false);assert.equal(result.timedOut,true);assert.ok(calls.length>=2);assert.ok(calls.every(x=>x==='snapshot'));
});
test('element waiting returns the found fresh observation',async()=>{
  let calls=0;const result=await runOperation(async()=>frame(++calls===1?[]:[{nodeId:'0.2',text:'Ready'}]),'wait_for',{packageName,query:'Ready',timeoutMs:1000});assert.equal(result.found,true);assert.equal(result.snapshotId,snapshotId);assert.equal(result.nodes[0].text,'Ready');
});
test('stability waiting resets after a changed fingerprint',async()=>{
  let calls=0;const result=await runOperation(async()=>({...frame(),contentFingerprint:++calls<2?'loading':'ready'}),'wait_for_idle',{packageName,stableMs:250,timeoutMs:1500});assert.equal(result.idle,true);assert.ok(calls>=3);assert.equal(result.observation.snapshotId,snapshotId);
});
test('combined screen capture is optional, bounded and keeps its node identifiers',async t=>{
  const seen=[],client=await fixture(t,undefined,async(op,args)=>{seen.push({op,args});return op==='snapshot'?frame([{nodeId:'0.1',text:'OK'}]):{mimeType:'image/jpeg',data:'aW1hZ2U=',coordinateSpace:{originX:0,originY:0,scaleX:1,scaleY:1}};});
  const result=await client.callTool({name:'phone_snapshot',arguments:{packageName,includeScreenshot:true,annotate:true,maxDimension:700}});assert.equal(result.content[0].type,'image');assert.equal(seen[1].args.snapshotId,snapshotId);assert.equal(data(result).nodes[0].nodeId,'0.1');
});

test('document writes reject oversized escaped or UTF-8 payloads before sending to Android',async t=>{
  let commands=0;const client=await fixture(t,undefined,async()=>{commands++;return {written:true};});
  for(const text of ['a'.repeat(16000),'\u0000'.repeat(3000),'漢'.repeat(8000)]){
    const result=await client.callTool({name:'phone_action',arguments:{operation:'write_file',arguments:{locationId:snapshotId,path:'test.txt',text}}});
    assert.equal(result.isError,true);
  }
  assert.equal(commands,0);
  const result=await client.callTool({name:'phone_action',arguments:{operation:'write_file',arguments:{locationId:snapshotId,path:'test.txt',text:'French é, German ü, Japanese 文'}}});
  assert.equal(result.isError,undefined);assert.equal(commands,1);
});


test('empty-task cleanup requires write scope and bounded positive identifiers',async t=>{
 let count=0;const command=async(op,args)=>{assert.equal(op,'session_cleanup');assert.deepEqual(args.taskIds,[51,52]);count++;return {removed:[51],skipped:[{taskId:52,reason:'active_or_physical_task'}]};};
 const readonly=await fixture(t,['phone.read'],command);assert.equal((await readonly.callTool({name:'phone_action',arguments:{operation:'session_cleanup',arguments:{packageName,taskIds:[51,52]}}})).isError,true);
 const writer=await fixture(t,undefined,command);
 for(const taskIds of [[-1],[0],Array(65).fill(1)])assert.equal((await writer.callTool({name:'phone_action',arguments:{operation:'session_cleanup',arguments:{packageName,taskIds}}})).isError,true);
 assert.equal(count,0);const result=await writer.callTool({name:'phone_action',arguments:{operation:'session_cleanup',arguments:{packageName,taskIds:[51,52]}}});assert.equal(result.isError,undefined);assert.deepEqual(data(result).removed,[51]);assert.equal(count,1);
});

test('stable read can select the owned surface capture without any write or silent physical source',async t=>{
 const seen=[],client=await fixture(t,['phone.read'],async(op,args)=>{seen.push({op,args});return {mimeType:'image/jpeg',data:'AA==',captureSource:'owned_display_surface'};});
 const ok=await client.callTool({name:'phone_read',arguments:{operation:'screenshot',arguments:{packageName,captureSource:'surface'}}});
 assert.notEqual(ok.isError,true);assert.equal(seen.length,1);assert.equal(seen[0].op,'screenshot');assert.equal(seen[0].args.captureSource,'surface');
 const invalid=await client.callTool({name:'phone_read',arguments:{operation:'screenshot',arguments:{packageName,captureSource:'physical'}}});assert.equal(invalid.isError,true);assert.equal(seen.length,1);
});

test('screen policy belongs to local settings and cannot be selected through any MCP gateway',async t=>{
 let commands=0;const seen=[],client=await fixture(t,undefined,async(op,args)=>{commands++;seen.push({op,args});return {woken:true,physicalScreenModeChanged:false,physicalScreenModeOwner:'phone_settings'};});
 const catalog=data(await client.callTool({name:'phone_catalog',arguments:{operation:'session_wake'}}));
 assert.match(catalog.operation.description,/cannot enable.*physical-screen-on/);
 assert.deepEqual(Object.keys(catalog.operation.inputSchema.properties).sort(),['packageName','reason','sessionId'].sort());
 for(const arguments_ of [
  {operation:'session_wake',arguments:{packageName,sessionId:snapshotId,reason:'black_render',keepPhysicalScreenOn:true}},
  {operation:'session_wake',arguments:{packageName,sessionId:snapshotId,reason:'black_render',_local:true}},
  {operation:'set_physical_screen_awake',arguments:{keepOn:true}},
  {operation:'setPhysicalScreenAwake',arguments:{keepOn:true,revision:100}}
 ])assert.equal((await client.callTool({name:'phone_action',arguments:arguments_})).isError,true);
 assert.equal(commands,0);
 const recovered=data(await client.callTool({name:'phone_action',arguments:{operation:'session_wake',arguments:{packageName,sessionId:snapshotId,reason:'black_render'}}}));
 assert.equal(commands,1);assert.equal(seen[0].op,'session_wake');assert.equal(recovered.physicalScreenModeChanged,false);assert.equal(recovered.physicalScreenModeOwner,'phone_settings');
});
