import test from 'node:test';
import assert from 'node:assert/strict';
import {resolveLegacyInteraction,withLegacyLongPress,describeLegacyInteractions} from '../src/legacy-interactions.js';
import {commandWithObservation} from '../src/screen-output.js';

test('legacy snapshots expose explicit long-press handles without changing observed node IDs',()=>{
 const input={snapshotId:'same',nodes:[{nodeId:'0.1',text:'Message',bounds:[1,2,3,4]},{nodeId:'0.2',text:'[champ protégé]'},{nodeId:'0.3',text:'Disabled',enabled:false},{nodeId:'0.4'}]};
 const r=withLegacyLongPress(input);assert.equal(r.snapshotId,'same');assert.equal(r.nodes[0].nodeId,'0.1');assert.equal(r.nodes[0].longPressNodeId,'0.2147483647.0.1');assert.match(r.nodes[0].longPressNodeId,/^0(?:\.\d+)*$/);assert.deepEqual(r.nodes[0].bounds,[1,2,3,4]);for(const n of r.nodes.slice(1))assert.equal(n.longPressNodeId,undefined);assert.equal(input.nodes[0].longPressNodeId,undefined);assert.match(r.interactionGuide,/phone_click/);
});

test('normal clicks, native long presses and other operations retain their exact operation',()=>{
 for(const [op,nodeId] of [['click','0.1'],['long_press','0.1'],['set_text','0.2147483647.0.1']]){const args={nodeId,packageName:'com.example',snapshotId:'same'};assert.deepEqual(resolveLegacyInteraction(op,args),{op,args});}
});

test('explicit legacy long press forwards once and retains snapshot, app and permission boundary',async()=>{
 const seen=[];const args={packageName:'com.example',snapshotId:'same',nodeId:'0.2147483647.0.1',observe:false};
 await commandWithObservation(async(op,a)=>{seen.push({op,args:a});return {performed:true};},'click',args);
 assert.deepEqual(seen,[{op:'long_press',args:{packageName:'com.example',snapshotId:'same',nodeId:'0.1'}}]);
 assert.throws(()=>resolveLegacyInteraction('click',{nodeId:'0.2147483647.not-a-node'}),/invalide/);
});

test('legacy failure never retries an action and observation failure preserves success',async()=>{
 let actions=0;await assert.rejects(()=>commandWithObservation(async()=>{actions++;throw Error('revoked or stale');},'click',{nodeId:'0.2147483647.0.1'}),/revoked or stale/);assert.equal(actions,1);
 actions=0;const r=await commandWithObservation(async op=>{if(op==='snapshot')throw Error('changed');actions++;return {performed:true};},'click',{packageName:'com.example',nodeId:'0.2147483647.0.1',settleMs:0});assert.equal(actions,1);assert.equal(r.performed,true);assert.equal(r.performedAction,'long_press');assert.equal(r.observationError,'changed');
});

test('status describes the compatibility method without inventing screen access',()=>{
 const r=describeLegacyInteractions({screenControlAllowed:false,screenControlReady:false});assert.equal(r.screenControlAllowed,false);assert.equal(r.screenControlReady,false);assert.match(r.interactionGuide,/longPressNodeId/);
});

test('legacy click explicitly routes tap and swipe without node IDs or coordinate scale guesses',async()=>{
 const screen=withLegacyLongPress({snapshotId:'same',width:1440,height:3120,nodes:[]});
 assert.equal(screen.coordinateActions.tapNodeIdTemplate,'0.2147483646.{x}.{y}');
 const seen=[];const command=async(op,args)=>{seen.push({op,args});return {performed:true};};
 const base={packageName:'com.example',snapshotId:'same',observe:false};
 await commandWithObservation(command,'click',{...base,nodeId:'0.2147483646.1200.2200'});
 await commandWithObservation(command,'click',{...base,nodeId:'0.2147483645.900.2000.700.1500.250'});
 assert.deepEqual(seen,[{op:'tap',args:{packageName:'com.example',snapshotId:'same',x:1200,y:2200}},{op:'swipe',args:{packageName:'com.example',snapshotId:'same',fromX:900,fromY:2000,toX:700,toY:1500,durationMs:250}}]);
 for(const nodeId of ['0.2147483646.1','0.2147483646.1.2.3','0.2147483646.-1.2','0.2147483646.16384.2','0.2147483645.1.2.3.4.99','0.2147483645.1.2.3.4.1001'])assert.throws(()=>resolveLegacyInteraction('click',{...base,nodeId}));
});
