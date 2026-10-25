import test from 'node:test';import assert from 'node:assert/strict';
import {compileChessPlan,compileUiPlan,decodePlacement,predictedMove,startConditionalPlan,conditionalResult} from '../src/conditional-plans.js';
import {chessBoard} from '../src/chess-board.js';import {operationDefinitions} from '../src/operations.js';
import {OperationScheduler} from '../src/command-channel.js';
const packageName='com.chess',sessionId='9405dd7c-cdd2-45b3-8b1f-6157113483d8',snapshotId='9405dd7c-cdd2-45b3-8b1f-6157113483d7',requestId='9405dd7c-cdd2-45b3-8b1f-6157113483d6';
const initial='rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR',e4=predictedMove(initial,'e2e4'),e5=predictedMove(e4,'e7e5');
const selected={packageName,sessionId,_ownerKey:'owner'},status={planId:requestId,packageName,sessionId,active:false,state:'completed'};
function frame(fen=initial){const p=decodePlacement(fen),names={r:'Rook',n:'Knight',b:'Bishop',q:'Queen',k:'King',p:'Pawn'},nodes=[];for(let y=0;y<8;y++)for(let x=0;x<8;x++){const sq='abcdefgh'[x]+(8-y),v=p.get(sq);nodes.push({nodeId:'0.'+nodes.length,description:v==='.'?sq+', empty':(v===v.toUpperCase()?'White':'Black')+' '+names[v.toLowerCase()]+' on '+sq,bounds:[x*90,252+y*90,(x+1)*90,342+y*90]});}return {packageName,sessionId,snapshotId,displayId:40,windowId:12,width:720,height:1280,nodes};}
const plan=()=>({kind:'chess',boardId:chessBoard(frame()).boardId,anchorFen:initial,ourColor:'white',initialMove:'e2e4',cases:[{opponent:'e7e5',reply:'g1f3'},{opponent:'c7c5',reply:'g1f3'}]});
const m=query=>({all:[{query,by:'text'}],none:[]}),ui=()=>({kind:'ui',anchor:m('Waiting'),cases:[{when:m('Ready'),action:{op:'click',query:'Next',by:'text'},after:m('Done'),terminal:true}]});
test('chess derives complete predicted positions and catches up an opponent move during model preparation',()=>{
 const a=compileChessPlan(frame(),plan());assert.deepEqual(compileChessPlan(frame(e5),plan()),a);assert.equal(a.waiting.fen,e4);assert.equal(a.cases[0].when.fen,e5);assert.equal(a.cases[0].after.fen,predictedMove(e5,'g1f3'));
 assert.deepEqual(compileChessPlan(frame(predictedMove(e4,'d7d5')),plan()),a); // Unknown actual position is left for native matching, never chosen here.
});
test('chess rejects changed scope/geometry/incomplete board, wrong colors, duplicate cases and special moves',()=>{
 for(const patch of [{packageName:'com.other'},{sessionId:snapshotId},{windowId:99},{displayId:41},{width:800},{nodes:frame().nodes.slice(1)}])assert.throws(()=>compileChessPlan({...frame(),...patch},plan()));
 for(const patch of [{ourColor:'black'},{initialMove:'e7e5'},{cases:[{opponent:'e7e5',reply:'b8c6'}]},{cases:[{opponent:'e7e5',reply:'g1f3'},{opponent:'e7e5',reply:'b1c3'}]}])assert.throws(()=>compileChessPlan(frame(),{...plan(),...patch}));
 for(const [fen,move] of [[initial,'e1g1'],['8/8/8/8/8/8/P7/8','a2a1'],[e4,'e4d5'],[initial,'e3e4']])assert.throws(()=>predictedMove(fen,move));
});
test('UI duplicate guards reject order differences and require a distinct postcondition',()=>{
 assert.deepEqual(compileUiPlan(ui()),ui());assert.throws(()=>compileUiPlan({...ui(),cases:[{...ui().cases[0],after:m('Ready')}]}),/distinct/);
 const c=ui().cases[0],when={all:[{query:'A'},{query:'B'}]};assert.throws(()=>compileUiPlan({...ui(),cases:[{...c,when},{...c,when:{all:[...when.all].reverse()}}]}),/Duplicate/);
});
test('plan schemas are bounded and reject arbitrary operations, extra app scope or promotion guesses',()=>{
 const s=operationDefinitions().find(d=>d.op==='plan_start').schema,base={packageName,sessionId,requestId,plan:plan()};assert.equal(s.safeParse(base).success,true);
 for(const patch of [{plan:{...plan(),cases:[]}},{plan:{...plan(),cases:Array(9).fill(plan().cases[0])}},{plan:{...ui(),cases:[{...ui().cases[0],action:{op:'shell',query:'x'}}]}},{plan:{...ui(),cases:[{...ui().cases[0],action:{...ui().cases[0].action,packageName:'com.other'}}]}},{timeoutMs:180001},{waitMs:20001},{plan:{...plan(),initialMove:'a7a8q'}}])assert.equal(s.safeParse({...base,...patch}).success,false);
});
test('start sends one native job after scope validation and defers evidence while active',async()=>{
 const events=[],r=await startConditionalPlan(async(op,args)=>{events.push({op,args});return op==='snapshot'?frame():{...status,active:true,state:'watching'};},{...selected,requestId,plan:plan(),observe:true,timeoutMs:60000});assert.equal(r.observationDeferred,true);assert.deepEqual(events.map(e=>e.op),['snapshot','plan_start']);assert.equal(events[1].args.requestId,requestId);
 await assert.rejects(startConditionalPlan(async()=>({...frame(),sessionId:snapshotId}),{...selected,requestId,plan:plan()}),/wrong app\/session/);
 await assert.rejects(startConditionalPlan(async()=>({...status,planId:sessionId}),{...selected,requestId,plan:ui()}),/different owner/);
});
test('long polling uses 8/8/4 second read-only waits and no active screenshots',async()=>{
 let clock=0;const calls=[],r=await conditionalResult(async(op,args)=>{calls.push({op,args});clock+=
args.waitMs;return {...status,active:true,state:'watching'};},{...selected,planId:requestId,waitMs:20000,observe:true,includeScreenshot:true},'plan_status',{now:()=>clock});assert.deepEqual(calls.map(c=>c.args.waitMs),[8000,8000,4000]);assert.equal(r.observationDeferred,true);assert.ok(calls.every(c=>c.op==='plan_status'));
});
test('terminal decision retains the full position, original IDs and requested image quality',async()=>{
 for(const detail of ['decision','full']){const calls=[],r=await conditionalResult(async(op,args)=>{calls.push({op,args});if(op==='plan_status')return status;if(op==='snapshot')return {...frame(e5),nodes:[...frame(e5).nodes,{nodeId:'clock',text:'00:30'}]};return {...frame(),mimeType:'image/jpeg',data:'AA=='};},{...selected,planId:requestId,observe:true,includeScreenshot:true,detail,quality:95,maxDimension:1920});assert.equal(r.observation.board.piecePlacementFen,e5);assert.equal(r.observation.snapshotId,snapshotId);assert.equal(r.observation.nodes.length,detail==='decision'?1:65);assert.equal(calls.at(-1).args.quality,95);assert.equal(calls.at(-1).args.maxDimension,1920);assert.deepEqual(calls.map(c=>c.op),['plan_status','snapshot','screenshot']);}
});
test('wrong-session jobs, snapshots and images cannot return mixed evidence',async()=>{
 await assert.rejects(conditionalResult(async()=>({...status,sessionId:snapshotId}),{...selected,planId:requestId}),/different owner/);
 for(const bad of ['snapshot','screenshot']){const r=await conditionalResult(async op=>op==='plan_status'?status:{...frame(),...(op===bad?{sessionId:snapshotId}:{}),data:'AA=='},{...selected,planId:requestId,observe:true,includeScreenshot:true});assert.ok(bad==='snapshot'?r.observationError:r.screenshotError);assert.equal(r.screenshot,undefined);}
});
test('cancel and another session bypass an occupied plan status lane',async()=>{
 const s=new OperationScheduler(),events=[];let release;const block=new Promise(r=>release=r);
 const wait=s.run('plan_status',selected,async guard=>{events.push('wait');await block;return guard(()=>true);});await Promise.resolve();
 await s.run('plan_cancel',selected,async guard=>guard(()=>events.push('cancel')));
 await s.run('snapshot',{...selected,sessionId:snapshotId},async guard=>guard(()=>events.push('other')));release();await wait;assert.deepEqual(events,['wait','cancel','other']);
});

import {Client} from '@modelcontextprotocol/sdk/client/index.js';import {InMemoryTransport} from '@modelcontextprotocol/sdk/inMemory.js';import {makeMcp} from '../src/mcp.js';
test('MCP scopes and stable read gateway cannot start or cancel prepared inputs',async t=>{
 let calls=0;const clients=[];
 for(const scopes of [['phone.read'],['phone.read','phone.write']]){const server=makeMcp({scopes,family:'owner'},async()=>{calls++;return status;},()=>({connected:true})),c=new Client({name:'plan-scopes',version:'1'}),[a,b]=InMemoryTransport.createLinkedPair();await server.connect(b);await c.connect(a);clients.push(c);t.after(()=>c.close());t.after(()=>server.close());}
 for(const operation of ['plan_start','plan_cancel']){const args=operation==='plan_start'?{packageName,sessionId,requestId,plan:ui()}:{packageName,sessionId,planId:requestId};assert.equal((await clients[1].callTool({name:'phone_read',arguments:{operation,arguments:args}})).isError,true);assert.equal((await clients[0].callTool({name:'phone_action',arguments:{operation,arguments:args}})).isError,true);}
 assert.equal(calls,0);
});

test('native screenshots without redundant packageName keep mandatory session and snapshot binding',async()=>{
 const r=await conditionalResult(async op=>op==='plan_status'?status:op==='snapshot'?frame():{sessionId,snapshotId,data:'AA==',mimeType:'image/jpeg'},{...selected,planId:requestId,observe:true,includeScreenshot:true});assert.equal(r.screenshotError,undefined);assert.equal(r.screenshot.snapshotId,snapshotId);
});

import {compactSnapshot} from '../src/screen-output.js';
test('decision snapshot is lossless for board pieces/hash and preserves full/default views',()=>{const f=frame(),decision=compactSnapshot(f,'decision'),full=compactSnapshot(f,'full');assert.equal(decision.nodes.length,0);assert.equal(decision.board.piecePlacementFen,initial);assert.equal(decision.board.boardId,full.board.boardId);assert.equal(full.nodes.length,64);assert.equal(compactSnapshot(f).nodes.length,64);assert.equal(decision.snapshotId,snapshotId);assert.ok(JSON.stringify(decision).length<JSON.stringify(full).length/3);});


test('chess trees derive descendant positions from their parent effect, with disjoint stage eligibility',()=>{
 const tree={...plan(),cases:[{opponent:'e7e5',reply:'g1f3',then:[{opponent:'b8c6',reply:'f1b5',then:[{opponent:'a7a6',reply:'b5a4'}]}]},{opponent:'c7c5',reply:'g1f3',then:[{opponent:'b8c6',reply:'d2d4'}]}]};
 const c=compileChessPlan(frame(),tree);assert.equal(c.graphVersion,2);assert.equal(c.cases.length,5);assert.deepEqual(c.cases.map(x=>x.stage),[0,1,2,0,3]);assert.deepEqual(c.cases.map(x=>x.nextStage),[1,2,-1,3,-1]);
 assert.equal(c.cases[1].when.fen,predictedMove(c.cases[0].after.fen,'b8c6'));assert.equal(c.cases[2].after.fen,predictedMove(predictedMove(c.cases[1].after.fen,'a7a6'),'b5a4'));assert.equal(c.cases[0].terminal,false);assert.equal(c.cases[2].terminal,true);
 assert.throws(()=>compileChessPlan(frame(),{...plan(),cases:[{opponent:'e7e5',reply:'g1f3',then:[{opponent:'e7e5',reply:'f1b5'}]}]}),/wrong color/);
});
test('UI tree limits and stage-local duplicate detection reject unsafe or oversized predictions',()=>{
 const c=ui().cases[0],tree={...ui(),cases:[{...c,then:[{...c,when:m('Done'),after:m('Final')}]}]};const out=compileUiPlan(tree);assert.equal(out.graphVersion,2);assert.equal(out.cases[0].nextStage,1);assert.equal(out.cases[1].stage,1);
 assert.throws(()=>compileUiPlan({...ui(),cases:[{...c,then:[c,c]}]}),/Duplicate/);
 let deep=c;for(let i=0;i<6;i++)deep={...c,then:[deep]};assert.throws(()=>compileUiPlan({...ui(),cases:[deep]}),/six levels/);
 assert.throws(()=>compileUiPlan({...ui(),cases:Array.from({length:8},(_,i)=>({...c,when:m('A'+i),then:Array.from({length:8},(_,j)=>({...c,when:m('B'+j)}))}))}),/32 cases/);
 const schema=operationDefinitions().find(d=>d.op==='plan_start').schema;assert.equal(schema.safeParse({...selected,_ownerKey:undefined,requestId,plan:tree}).success,false);assert.equal(schema.safeParse({packageName,sessionId,requestId,plan:tree}).success,true);
});
test('completed, failed and unknown plans preserve session and do not assert user-goal completion',async()=>{
 for(const state of ['completed','needs_model','timed_out','cancelled']){const r=await conditionalResult(async()=>({...status,state}),{...selected,planId:requestId,observe:false});assert.equal(r.sessionEnded,false);assert.equal(r.taskGoalStatus,'not_evaluated_by_runner');assert.match(r.instruction,state==='needs_model'?/NOT declared complete/:/not necessarily the user task/);}
});
test('plan_list is read-only with required app/session binding and exposed by both discovery and read gateway',async t=>{
 const {makeMcp}=await import('../src/mcp.js'),{Client}=await import('@modelcontextprotocol/sdk/client/index.js'),{InMemoryTransport}=await import('@modelcontextprotocol/sdk/inMemory.js');let calls=0;
 const server=makeMcp({scopes:['phone.read'],family:'plans-list'},async(op,args)=>{calls++;assert.equal(op,'plan_list');assert.equal(args.sessionId,sessionId);assert.equal(args.packageName,packageName);return {sessionId,packageName,plans:[],activeCount:0};},()=>({connected:true}));
 const c=new Client({name:'plans-list',version:'1'}),[a,b]=InMemoryTransport.createLinkedPair();await server.connect(b);await c.connect(a);t.after(()=>c.close());t.after(()=>server.close());
 assert.ok((await c.listTools()).tools.some(x=>x.name==='phone_plan_list'&&x.annotations.readOnlyHint));
 assert.equal((await c.callTool({name:'phone_read',arguments:{operation:'plan_list',arguments:{packageName,sessionId}}})).isError,undefined);assert.equal(calls,1);
 assert.equal((await c.callTool({name:'phone_plan_list',arguments:{packageName}})).isError,true);assert.equal(calls,1);
});
