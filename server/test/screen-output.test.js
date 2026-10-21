import test from 'node:test';
import assert from 'node:assert/strict';
import {commandWithObservation,compactSnapshot} from '../src/screen-output.js';

test('compact observations preserve all labels, ancestry, geometry and disabled state',()=>{
  const full={snapshotId:'fresh',nodes:[{nodeId:'0',parent:null,text:'',description:'',class:'android.widget.FrameLayout',bounds:[0,0,10,10],enabled:true,clickable:false},{nodeId:'0.1',parent:'0',text:'Message',description:'Auteur',viewId:'app:id/msg',class:'android.widget.TextView',longClickable:true,enabled:false,checked:true,bounds:[0,0,5,5]}]};
  const value=compactSnapshot(full);assert.equal(value.nodes.length,2);assert.equal(value.nodes[1].text,'Message');assert.equal(value.nodes[1].description,'Auteur');assert.equal(value.nodes[1].parent,'0');assert.equal(value.nodes[1].enabled,false);assert.equal(value.nodes[1].longClickable,true);assert.deepEqual(value.nodes[1].bounds,[0,0,5,5]);assert.equal(value.nodes[0].enabled,undefined);assert.equal(compactSnapshot(full,'full'),full);
});

test('one action returns a fresh observation usable directly for the next action',async()=>{
  const seen=[];const cmd=async(op,args)=>{seen.push({op,args});return op==='snapshot'?{snapshotId:'fresh',nodes:[]}:{performed:true};};
  const result=await commandWithObservation(cmd,'long_press',{packageName:'com.example',nodeId:'0.1',snapshotId:'old',settleMs:0});
  assert.deepEqual(seen.map(x=>x.op),['long_press','snapshot']);assert.equal(seen[0].args.settleMs,undefined);assert.equal(result.performed,true);assert.equal(result.observation.snapshotId,'fresh');assert.ok(result.timing.totalMs>=0);
});

test('observation failure preserves write success and never retries the write',async()=>{
  const seen=[];const cmd=async op=>{seen.push(op);if(op==='snapshot')throw Error('window changed');return {performed:true};};
  const r=await commandWithObservation(cmd,'click',{packageName:'com.example',settleMs:0});
  assert.deepEqual(seen,['click','snapshot','snapshot']);assert.equal(r.performed,true);assert.equal(r.observation,undefined);assert.equal(r.observationError,'window changed');assert.match(r.instruction,/ne répétez pas/);
});

test('action failure, disabled observation and non-UI writes never cause extra commands',async()=>{
  let calls=0;await assert.rejects(()=>commandWithObservation(async()=>{calls++;throw Error('stale');},'click',{}),/stale/);assert.equal(calls,1);
  for(const [op,args] of [['set_text',{observe:false}],['download_file',{}],['reply_notification',{}]]){calls=0;await commandWithObservation(async()=>{calls++;return {accepted:true};},op,args);assert.equal(calls,1);}
});
