import test from 'node:test';import assert from 'node:assert/strict';
import {chessBoard,boardSummary,boardMove} from '../src/chess-board.js';import {compactSnapshot} from '../src/screen-output.js';import {runOperation} from '../src/automation.js';import {operationDefinitions} from '../src/operations.js';
const packageName='com.chess',sessionId='9405dd7c-cdd2-45b3-8b1f-6157113483d8',snapshotId='9405dd7c-cdd2-45b3-8b1f-6157113483d7';
function frame({flipped=false,english=false}={}){const names=english?{r:'Rook',n:'Knight',b:'Bishop',q:'Queen',k:'King',p:'Pawn'}:{r:'Tour',n:'Cavalier',b:'Fou',q:'Dame',k:'Roi',p:'Pion'},rows=['rnbqkbnr','pppppppp','........','........','........','........','PPPPPPPP','RNBQKBNR'];const nodes=[];for(let y=0;y<8;y++)for(let x=0;x<8;x++){const p=rows[y][x],sq='abcdefgh'[x]+(8-y),cx=flipped?7-x:x,cy=flipped?7-y:y;nodes.push({nodeId:'0.'+nodes.length,description:p==='.'?sq+', '+(english?'empty':'vide'):(p===p.toUpperCase()?(english?'White':'Blancs'):(english?'Black':'Noirs'))+' '+names[p.toLowerCase()]+' '+(english?'on':'en')+' '+sq,bounds:[cx*90,252+cy*90,(cx+1)*90,342+cy*90]});}return {packageName,sessionId,snapshotId,displayId:40,windowId:12,width:720,height:1280,nodes};}
const selected={packageName,sessionId,_ownerKey:'owner'};

test('complete French and English boards become exact piece placement with observed orientation',()=>{
 for(const english of [false,true])for(const flipped of [false,true]){const f=frame({english,flipped}),b=chessBoard(f);assert.equal(b.piecePlacementFen,'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR');assert.equal(b.orientation,flipped?'black_bottom':'white_bottom');assert.equal(b.squares.size,64);assert.equal(boardSummary(f).sideToMove,null);assert.equal(compactSnapshot(f).board.boardId,b.boardId);assert.equal(compactSnapshot(f,'full').board.boardId,b.boardId);}
});
test('incomplete duplicate hidden malformed or inconsistent boards are never guessed',()=>{
 const f=frame();for(const nodes of [f.nodes.slice(1),[...f.nodes,f.nodes[0]],f.nodes.map((n,i)=>i? n:{...n,visible:false}),f.nodes.map((n,i)=>i? n:{...n,bounds:[NaN,252,90,342]}),f.nodes.map((n,i)=>i? n:{...n,bounds:[20,252,110,342]})])assert.equal(chessBoard({...f,nodes}),null);
});
test('board binding changes with app/session/window/geometry/pieces but not clock or evaluation labels',()=>{
 const f=frame(),id=chessBoard(f).boardId;assert.equal(chessBoard({...f,nodes:[...f.nodes,{nodeId:'clock',text:'00:30'}]}).boardId,id);
 for(const changed of [{packageName:'com.other'},{sessionId:snapshotId},{windowId:13},{displayId:41},{nodes:f.nodes.map(n=>n.description==='Blancs Pion en e2'?{...n,description:'e2, vide'}:n)}])assert.notEqual(chessBoard({...f,...changed}).boardId,id);
});
test('one algebraic move captures/rechecks then sends exactly one guarded two-tap sequence',async()=>{
 const f=frame(),events=[],args={...selected,boardId:chessBoard(f).boardId,uci:'e2e4'};
 const out=await boardMove(async(op,args)=>{events.push({op,args});if(op==='snapshot')return f;if(op==='screenshot')return {sessionId,snapshotId,frameContent:'visible_pixels'};return {...selected,executedCount:2,completed:true};},args);
 assert.equal(out.completed,true);assert.equal(out.uci,'e2e4');assert.deepEqual(events.map(e=>e.op),['snapshot','screenshot','action_sequence']);assert.deepEqual(events[2].args.steps,[{op:'tap',x:405,y:837},{op:'tap',x:405,y:657}]);assert.equal(events[2].args.snapshotId,snapshotId);assert.equal(out.replayed,false);
});
test('stale position, cross-session evidence, empty source, own capture or black frame cannot send any move',async()=>{
 const f=frame(),args={...selected,boardId:chessBoard(f).boardId,uci:'e2e4'};
 for(const change of [{boardId:'a'.repeat(64)},{uci:'e3e4'},{uci:'e1e2'},{uci:'e2e2'}]){const ops=[];await assert.rejects(boardMove(async op=>{ops.push(op);return f;},{...args,...change}));assert.deepEqual(ops,['snapshot']);}
 await assert.rejects(boardMove(async()=>({...f,sessionId:snapshotId}),args),/different app/);
 const ops=[];await assert.rejects(boardMove(async op=>{ops.push(op);return op==='snapshot'?f:{sessionId,snapshotId,frameContent:'black_or_empty'};},args),/capture unavailable/);assert.deepEqual(ops,['snapshot','screenshot']);
});
test('board move keeps native partial results, final board and quality without choosing or replaying moves',async()=>{
 const f=frame(),events=[],after={...f,nodes:f.nodes.map(n=>n.description==='Blancs Pion en e2'?{...n,description:'e2, vide'}:n.description==='e4, vide'?{...n,description:'Blancs Pion en e4'}:n)};let snapshots=0;
 const result=await runOperation(async(op,args)=>{events.push({op,args});if(op==='snapshot')return ++snapshots===1?f:after;if(op==='screenshot')return {sessionId,snapshotId,frameContent:'visible_pixels',mimeType:'image/jpeg',data:'AA=='};return {...selected,completed:true,executedCount:2};},'board_move',{...selected,boardId:chessBoard(f).boardId,uci:'e2e4',includeScreenshot:true,maxDimension:1920,quality:95});
 assert.equal(result.boardChanged,true);assert.equal(result.moveVerified,true);assert.equal(result.observation.board.piecePlacementFen,'rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR');assert.equal(events.filter(e=>e.op==='action_sequence').length,1);assert.equal(events.at(-1).args.quality,95);
 const schema=operationDefinitions().find(d=>d.op==='board_move').schema;assert.equal(schema.safeParse({...selected,_ownerKey:undefined,boardId:chessBoard(f).boardId,uci:'e2e4'}).success,false);assert.equal(schema.safeParse({packageName,sessionId,boardId:chessBoard(f).boardId,uci:'a7a8q'}).success,false);
});

test('decision format verifies a real move from its complete server-derived position after square-node omission',async()=>{const f=frame(),after={...f,nodes:f.nodes.map(n=>n.description==='Blancs Pion en e2'?{...n,description:'e2, vide'}:n.description==='e4, vide'?{...n,description:'Blancs Pion en e4'}:n)};let reads=0;const result=await runOperation(async op=>op==='snapshot'?(++reads===1?f:after):op==='screenshot'?{sessionId,snapshotId,frameContent:'visible_pixels'}:{...selected,completed:true,executedCount:2},'board_move',{...selected,boardId:chessBoard(f).boardId,uci:'e2e4',detail:'decision'});assert.equal(result.moveVerified,true);assert.equal(result.observation.nodes.length,0);assert.equal(result.finalPiecePlacementFen,'rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR');});

test('board_move waits out a black transition, revalidates the complete position and sends only one move',async()=>{
 const f=frame(),after={...f,nodes:f.nodes.map(n=>n.description==='Blancs Pion en e2'?{...n,description:'e2, vide'}:n.description==='e4, vide'?{...n,description:'Blancs Pion en e4'}:n)};let reads=0,captures=0;const events=[];
 const result=await runOperation(async(op,a)=>{events.push({op,args:a});if(op==='snapshot')return ++reads<=2?f:after;if(op==='screenshot')return {packageName,sessionId,snapshotId,displayId:40,frameContent:++captures===1?'black_or_empty':'visible_pixels'};return {...selected,completed:true,executedCount:2};},'board_move',{...selected,boardId:chessBoard(f).boardId,uci:'e2e4'});
 assert.equal(result.moveVerified,true);assert.deepEqual(events.slice(0,6).map(e=>e.op),['snapshot','screenshot','screenshot','snapshot','screenshot','action_sequence']);assert.equal(events.filter(e=>e.op==='action_sequence').length,1);
});
test('no move is sent if the position changes while a black capture is recovering',async()=>{
 const f=frame(),changed={...f,nodes:f.nodes.map(n=>n.description==='Blancs Pion en d2'?{...n,description:'d2, vide'}:n.description==='d4, vide'?{...n,description:'Blancs Pion en d4'}:n)};let reads=0,captures=0;const ops=[];
 await assert.rejects(runOperation(async op=>{ops.push(op);if(op==='snapshot')return ++reads===1?f:changed;if(op==='screenshot')return {packageName,sessionId,snapshotId,displayId:40,frameContent:++captures===1?'black_or_empty':'visible_pixels'};throw Error('Unexpected input');},'board_move',{...selected,boardId:chessBoard(f).boardId,uci:'e2e4'}),/changed during capture recovery/);assert.equal(ops.includes('action_sequence'),false);
});
const accessible=f=>({...f,nodes:f.nodes.map(n=>({...n,clickable:true}))});
test('semantic board move tolerates selection affordance changes and needs no raster capture',async()=>{
 const f=accessible(frame()),selectedFrame={...f,snapshotId:'selection',nodes:f.nodes.map(n=>({...n,nodeId:'new.'+n.nodeId,longClickable:true,selected:n.description==='Blancs Pion en e2'}))},events=[];let reads=0;
 const r=await boardMove(async(op,a)=>{events.push({op,args:a});if(op==='snapshot')return ++reads===1?f:selectedFrame;if(op==='click')return {sessionId,performed:true};throw Error('A semantic move must not ask for coordinates or pixels');},{...selected,boardId:chessBoard(f).boardId,uci:'e2e4'});
 assert.equal(r.completed,true);assert.equal(r.executedCount,2);assert.equal(r.inputTransport,'accessible_squares');assert.deepEqual(events.map(e=>e.op),['snapshot','click','snapshot','click']);assert.equal(events[3].args.nodeId,'new.0.36');assert.equal(events[3].args.snapshotId,'selection');
});
test('semantic source errors are never retried or followed by coordinate input',async()=>{
 const f=accessible(frame()),ops=[];const r=await boardMove(async op=>{ops.push(op);if(op==='snapshot')return f;throw Error('Delivery uncertain');},{...selected,boardId:chessBoard(f).boardId,uci:'e2e4'});
 assert.deepEqual(ops,['snapshot','click']);assert.equal(r.completed,false);assert.equal(r.executedCount,0);assert.equal(r.inputMayHaveOccurred,true);assert.equal(r.replayed,false);
});
test('semantic selection stops if pieces, geometry or session change; no destination replay',async()=>{
 const f=accessible(frame());for(const patch of [{sessionId:'other'},{displayId:99},{windowId:99},{nodes:f.nodes.map(n=>n.description==='Blancs Pion en d2'?{...n,description:'d2, vide'}:n.description==='d4, vide'?{...n,description:'Blancs Pion en d4'}:n)},{nodes:f.nodes.map(n=>n.description==='e4, vide'?{...n,clickable:false}:n)}]){
  const ops=[];let reads=0;const r=await boardMove(async op=>{ops.push(op);return op==='snapshot'?(++reads===1?f:{...f,...patch}):{sessionId,performed:true};},{...selected,boardId:chessBoard(f).boardId,uci:'e2e4'});
  assert.equal(r.completed,false);assert.equal(r.executedCount,1);assert.deepEqual(ops,['snapshot','click','snapshot']);assert.equal(r.inputMayHaveOccurred,true);
 }
});
test('a destination refusal remains a partial semantic move; no fallback or extra source click',async()=>{
 const f=accessible(frame());let clicks=0;const ops=[];const r=await boardMove(async op=>{ops.push(op);if(op==='snapshot')return f;if(++clicks===1)return {sessionId,performed:true};throw Error('Session stopped');},{...selected,boardId:chessBoard(f).boardId,uci:'e2e4'});
 assert.equal(r.completed,false);assert.equal(r.executedCount,1);assert.equal(r.failedStep,1);assert.equal(r.inputMayHaveOccurred,true);assert.deepEqual(ops,['snapshot','click','snapshot','click']);
});