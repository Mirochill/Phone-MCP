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