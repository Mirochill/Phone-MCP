import test from 'node:test';
import assert from 'node:assert/strict';
import {recoverRead} from '../src/read-recovery.js';
test('temporarily missing window retries a read with the identical session only',async()=>{
 const selection={sessionId:'owned-session',packageName:'com.example'};const calls=[];
 const value=await recoverRead(async(op,args)=>{calls.push([op,args]);if(calls.length===1)throw Error('Fenêtre IA temporairement indisponible. La tâche est conservée.');return {snapshotId:'fresh'};},'snapshot',selection);
 assert.equal(value.snapshotId,'fresh');assert.equal(calls.length,2);for(const [op,args] of calls){assert.equal(op,'snapshot');assert.equal(args,selection);}
});
test('window recovery never replays a write or restarts an app',async()=>{
 for(const op of ['tap','click','open_app','back','session_create']){let calls=0;await assert.rejects(()=>recoverRead(async()=>{calls++;throw Error('Fenêtre IA temporairement indisponible.');},op,{}),/temporairement/);assert.equal(calls,1);}
});
test('window recovery does not retry revoked sessions or security failures',async()=>{
 for(const message of ['Session stopped','Session is not active','Affichage parallèle arrêté ou session expirée.','Capture refusée : fenêtre non autorisée']){let calls=0;await assert.rejects(()=>recoverRead(async()=>{calls++;throw Error(message);},'snapshot',{}));assert.equal(calls,1);}
});
const captureSelection={packageName:'com.chess',sessionId:'owned',snapshotId:'observed'};
const black={...captureSelection,displayId:17,frameContent:'black_or_empty',data:'AA=='};
const visible={...black,frameContent:'visible_pixels',data:'AQ=='};
const fakeTime=()=>{let time=0;return {now:()=>time,wait:async ms=>{time+=ms;}};};
test('a transient black image is recaptured within the same call, without a write or a model turn',async()=>{
 const events=[],time=fakeTime();
 const out=await recoverRead(async(op,args)=>{events.push({op,args});return events.length<3?black:visible;},'screenshot',captureSelection,time);
 assert.equal(out.data,visible.data);assert.equal(out.captureRecovery.recoveredFromBlack,true);assert.equal(out.captureRecovery.attempts,3);assert.equal(out.captureRecovery.elapsedMs,300);
 for(const e of events){assert.equal(e.op,'screenshot');assert.equal(e.args,captureSelection);}
});
test('healthy captures do not wait, recapture or change quality/data',async()=>{
 let calls=0;const out=await recoverRead(async()=>{calls++;return visible;},'screenshot',captureSelection,{now:()=>0,wait:async()=>{throw Error('unnecessary wait');}});
 assert.equal(calls,1);assert.equal(out,visible);
});
test('a persistently black frame remains black after a bounded read-only budget',async()=>{
 let calls=0;const out=await recoverRead(async()=>{calls++;return black;},'screenshot',captureSelection,fakeTime());
 assert.equal(calls,5);assert.equal(out.frameContent,'black_or_empty');assert.equal(out.captureRecovery.state,'still_black');assert.equal(out.captureRecovery.elapsedMs,1150);assert.equal(out.captureRecovery.recoveredFromBlack,false);
});
test('blank recovery refuses changed app/session/display/snapshot and missing image evidence',async()=>{
 for(const changed of [{packageName:'com.other'},{sessionId:'other'},{displayId:18},{snapshotId:'other'},{frameContent:undefined}]){
  let calls=0;await assert.rejects(recoverRead(async()=>++calls===1?black:{...visible,...changed},'screenshot',captureSelection,fakeTime()),/scope|different application|no visual evidence/);assert.equal(calls,2);
 }
 let calls=0;await assert.rejects(recoverRead(async()=>{calls++;return {...black,sessionId:'other'};},'screenshot',captureSelection,fakeTime()),/scope mismatch/);assert.equal(calls,1);
});
test('a local stop, disconnect or lost capture grant during recapture prevents another read/input',async()=>{
 for(const message of ['Session stopped','Transport disconnected','Capture refusée : fenêtre non autorisée']){let calls=0;await assert.rejects(recoverRead(async()=>{if(++calls===1)return black;throw Error(message);},'screenshot',captureSelection,fakeTime()));assert.equal(calls,2);}
});
test('legacy screenshot recovery pins the first returned session and never captures another display',async()=>{
 const events=[];const out=await recoverRead(async(op,args)=>{events.push(args);return events.length===1?black:visible;},'screenshot',{packageName:'com.chess',snapshotId:'observed'},fakeTime());
 assert.equal(events[1].sessionId,'owned');assert.equal(out.captureRecovery.recoveredFromBlack,true);
});
test('a write result containing blank-frame metadata is never retried',async()=>{
 for(const op of ['tap','click','board_move','action_sequence','session_wake','open_app']){let calls=0;const out=await recoverRead(async()=>{calls++;return black;},op,captureSelection,fakeTime());assert.equal(calls,1);assert.equal(out,black);}
});