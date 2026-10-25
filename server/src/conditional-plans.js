import {createHash} from 'node:crypto';
import {chessBoard} from './chess-board.js';
import {compactSnapshot} from './screen-output.js';
import {withLegacyLongPress} from './legacy-interactions.js';

export function decodePlacement(fen){
 if(typeof fen!=='string'||fen.length>90)throw Error('Invalid piece placement.');const rows=fen.split('/'),p=new Map();if(rows.length!==8)throw Error('Eight ranks required.');
 for(let y=0;y<8;y++){let x=0;for(const v of rows[y]){if(!/[1-8prnbqkPRNBQK]/.test(v))throw Error('Invalid piece.');const n=/[1-8]/.test(v)?Number(v):1;for(let i=0;i<n;i++){if(x>=8)throw Error('Rank too long.');p.set('abcdefgh'[x++]+(8-y),n===1&&!/[1-8]/.test(v)?v:'.');}}if(x!==8)throw Error('Rank too short.');}return p;
}
export function encodePlacement(p){return Array.from({length:8},(_,y)=>{let row='',empty=0;for(const f of 'abcdefgh'){const c=p.get(f+(8-y));if(c==='.')empty++;else{if(empty)row+=empty;empty=0;row+=c;}}if(empty)row+=empty;return row;}).join('/');}
export function predictedMove(fen,uci){
 if(!/^[a-h][1-8][a-h][1-8]$/.test(uci))throw Error('Four-character move required.');const p=decodePlacement(fen),a=uci.slice(0,2),b=uci.slice(2),piece=p.get(a),target=p.get(b),white=c=>c===c.toUpperCase();
 if(a===b||piece==='.'||(target!=='.'&&white(target)===white(piece)))throw Error('Invalid observed move source or destination.');
 if((piece.toLowerCase()==='k'&&Math.abs(a.charCodeAt(0)-b.charCodeAt(0))>1)||(piece.toLowerCase()==='p'&&(/[18]/.test(b[1])||(a[0]!==b[0]&&target==='.'))))throw Error('Special moves require a separate observed decision.');p.set(a,'.');p.set(b,piece);return encodePlacement(p);
}
function boardIdAt(frame,board,fen){const p=decodePlacement(fen),cells=[...board.squares].sort(([a],[b])=>a.localeCompare(b)).map(([q,c])=>[q,p.get(q),c.bounds]);return createHash('sha256').update(JSON.stringify([frame.packageName,frame.sessionId,frame.displayId,frame.windowId,frame.width,frame.height,cells])).digest('hex');}
function canonicalMatcher(m){return JSON.stringify(['all','none'].map(k=>(m[k]??[]).map(s=>[s.by??'text',s.query]).sort((a,b)=>JSON.stringify(a).localeCompare(JSON.stringify(b)))));}
function compileTree(cases,build){
 const out=[];let stages=0;
 const walk=(children,context,stage=0,depth=1)=>{
  if(depth>6||!Array.isArray(children)||children.length<1||children.length>8)throw Error('Tree limited to six levels and eight siblings.');
  const seen=new Set();
  for(const c of children){if(out.length>=32)throw Error('Tree limited to 32 cases.');const v=build(c,context);if(seen.has(v.key))throw Error('Duplicate or ambiguous predicted cases within a stage.');seen.add(v.key);
   const next=c.then?++stages:-1;out.push({...v.rule,stage,nextStage:next,terminal:next<0?v.rule.terminal:false});if(c.then)walk(c.then,v.context,next,depth+1);
  }
 };
 return {out,walk};
}
export function compileUiPlan(plan){
 // Preserve the original flat wire format for Android 0.18 clients.
 const nested=plan.cases.some(c=>c.then);
 const tree=compileTree(plan.cases,(c)=>{const key=canonicalMatcher(c.when);if(key===canonicalMatcher(c.after))throw Error('A distinct observed postcondition is required.');return {key,rule:{when:c.when,action:c.action,after:c.after,terminal:c.terminal??true}};});tree.walk(plan.cases);
 if(!nested){const {onUnexpected,...legacy}=plan;return {...legacy,...(onUnexpected==='wait'?{onUnexpected}: {})};}
 return {kind:'ui',anchor:plan.anchor,allowWindowChange:plan.allowWindowChange??false,onUnexpected:plan.onUnexpected??'yield',graphVersion:2,cases:tree.out};
}
export function compileChessPlan(frame,plan){
 const board=chessBoard(frame);if(!board||frame.width!==720||frame.height!==1280)throw Error('A complete supported accessible chess board is required.');
 if(boardIdAt(frame,board,plan.anchorFen)!==plan.boardId.toLowerCase())throw Error('Original chess position/window/geometry cannot be verified. Read its new board; no input sent.');
 const isOur=p=>p!=='.'&&(p===p.toUpperCase())===(plan.ourColor==='white');let base=plan.anchorFen;const initial=plan.initialMove?{op:'board_move',uci:plan.initialMove}:undefined;
 if(initial){if(!isOur(decodePlacement(base).get(initial.uci.slice(0,2))))throw Error('Initial move uses the wrong observed color.');base=predictedMove(base,initial.uci);}
 const tree=compileTree(plan.cases,(c,position)=>{const origin=decodePlacement(position).get(c.opponent.slice(0,2));if(origin==='.'||isOur(origin))throw Error('Predicted opponent source has the wrong color.');const fen=predictedMove(position,c.opponent);if(!isOur(decodePlacement(fen).get(c.reply.slice(0,2))))throw Error('Prepared reply uses the wrong observed color.');const after=predictedMove(fen,c.reply);return {key:fen,context:after,rule:{when:{fen},action:{op:'board_move',uci:c.reply},after:{fen:after},terminal:true}};});tree.walk(plan.cases,base);
 const nested=plan.cases.some(c=>c.then),cases=tree.out.map(c=>{if(nested)return c;const {stage,nextStage,...legacy}=c;return legacy;});
 const geometry=[...board.squares].sort(([a],[b])=>a.localeCompare(b)).map(([q,c])=>q+':'+c.bounds.join(',')+';').join('');
 return {kind:'chess',anchor:{fen:plan.anchorFen},waiting:{fen:base},windowId:frame.windowId,geometry,cases,...(initial?{initial}:{}),...(nested?{graphVersion:2}:{}),...(plan.onUnexpected==='wait'?{onUnexpected:'wait'}:{})};
}
const parseError=e=>String(e?.message??e).slice(0,400);
function binding(r,args){if(r.packageName!==args.packageName||r.sessionId!==args.sessionId||r.planId!==(args.planId??args.requestId))throw Error('Conditional job belongs to a different owner/app/session/request.');}
export async function startConditionalPlan(command,args){
 const selected={packageName:args.packageName,sessionId:args.sessionId,_ownerKey:args._ownerKey};let compiled;
 if(args.plan.kind==='chess'){const frame=await command('snapshot',{...selected,limit:350});if(frame.packageName!==args.packageName||frame.sessionId!==args.sessionId)throw Error('Chess evidence has the wrong app/session.');compiled=compileChessPlan(frame,args.plan);}else compiled=compileUiPlan(args.plan);
 if(JSON.stringify(compiled).length>12000)throw Error('Plan exceeds the native budget. Prepare fewer cases.');
 const result=await command('plan_start',{...selected,requestId:args.requestId,compiledPlan:compiled,timeoutMs:args.timeoutMs});binding(result,args);
 if(result.active&&args.waitMs>0)return conditionalResult(command,{...args,planId:args.requestId},'plan_status');
 return planEvidence(command,args,result);
}
export async function conditionalResult(command,args,op='plan_status',{now=Date.now}={}){
 const wait=op==='plan_status'?(args.waitMs??0):0,until=now()+wait,max=Math.max(1,Math.ceil(wait/8000));let r,reads=0;
 do{r=await command(op,{packageName:args.packageName,sessionId:args.sessionId,planId:args.planId,_ownerKey:args._ownerKey,...(op==='plan_status'?{waitMs:Math.min(8000,Math.max(0,until-now()))}:{})});binding(r,args);reads++;}while(r.active&&now()<until&&reads<max);
 return planEvidence(command,args,{...r,statusReads:reads});
}
async function planEvidence(command,args,result){
 const next={...result,taskGoalStatus:'not_evaluated_by_runner',sessionEnded:false,instruction:result.active?'Prepared cases run locally. Keep working toward the final user objective; start independent plans before waiting. Use plan_list and bounded plan_status, not snapshot polling. Preserve this session during thinking pauses; renew its legitimate lease if needed.':result.state==='needs_model'?'This plan paused for a new decision; the app session is preserved and the user objective is NOT declared complete. Analyze evidence, recover safely and prepare a NEW request if still authorized. Do not abandon or session_end just because a branch was not predicted. Never replay uncertain inputs; obey any user stop.':'This plan ended, not necessarily the user task. Verify effects and final objective. If unfinished, continue safely in the same authorized session with new decisions/plans; do not substitute a progress report for completion. End and clean the session once the final objective is verified, or obey explicit user stop. A failed task is not success. If blocked, report the precise blocker; do not loop blindly or evade deadlines.'};
 if(!args.observe)return next;if(result.active){next.observationDeferred=true;return next;}
 const selected={packageName:args.packageName,sessionId:args.sessionId,_ownerKey:args._ownerKey};
 try{const raw=await command('snapshot',{...selected,limit:350});if(raw.packageName!==args.packageName||raw.sessionId!==args.sessionId)throw Error('Plan evidence scope mismatch.');next.observation=withLegacyLongPress(compactSnapshot(raw,args.detail));
  if(args.includeScreenshot)try{const shot=await command('screenshot',{...selected,snapshotId:raw.snapshotId,maxDimension:args.maxDimension,quality:args.quality,annotate:args.annotate});if((shot.packageName!==undefined&&shot.packageName!==args.packageName)||shot.sessionId!==args.sessionId||shot.snapshotId!==raw.snapshotId)throw Error("Plan screenshot scope/snapshot mismatch.");next.screenshot=shot;}catch(e){next.screenshotError=parseError(e);}
 }catch(e){next.observationError=parseError(e);}return next;
}
