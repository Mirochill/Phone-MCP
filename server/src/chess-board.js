import {createHash} from 'node:crypto';

const pieces={pawn:'p',pion:'p',rook:'r',tour:'r',knight:'n',cavalier:'n',bishop:'b',fou:'b',queen:'q',dame:'q',king:'k',roi:'k'};
const files='abcdefgh';
function label(node){
 const text=String(node.description??'').trim();let m=text.match(/^([a-h][1-8]),\s*(?:vide|empty)$/i);
 if(m)return {square:m[1].toLowerCase(),piece:'.'};
 m=text.match(/^(White|Black|Blancs|Noirs)\s+(Pawn|Pion|Rook|Tour|Knight|Cavalier|Bishop|Fou|Queen|Dame|King|Roi)\s+(?:on|en)\s+([a-h][1-8])$/i);
 if(!m)return null;let piece=pieces[m[2].toLowerCase()];if(/^(white|blancs)$/i.test(m[1]))piece=piece.toUpperCase();return {square:m[3].toLowerCase(),piece};
}
/** Only a complete, unambiguous, visible geometric 8x8 accessibility board. No vision guessing or chess engine. */
export function chessBoard(frame){
 if(!Array.isArray(frame?.nodes)||!Number.isFinite(frame.width)||!Number.isFinite(frame.height))return null;
 const squares=new Map();
 for(const node of frame.nodes){const item=label(node);if(!item)continue;const b=node.bounds;
  if(squares.has(item.square)||node.visible===false||node.enabled===false||!Array.isArray(b)||b.length!==4||!b.every(Number.isFinite)||b[0]<0||b[1]<0||b[2]>frame.width||b[3]>frame.height||b[0]>=b[2]||b[1]>=b[3])return null;
  squares.set(item.square,{...item,nodeId:node.nodeId,clickable:node.clickable===true,bounds:[...b],x:(b[0]+b[2])/2,y:(b[1]+b[3])/2});
 }
 if(squares.size!==64)return null;
 const a=squares.get('a8'),h=squares.get('h8'),a1=squares.get('a1'),dx=(h.x-a.x)/7,dy=(a1.y-a.y)/7;
 if(Math.abs(dx)<8||Math.abs(dy)<8||Math.abs(Math.abs(dx)-Math.abs(dy))>3)return null;
 for(const [sq,c] of squares){const x=a.x+files.indexOf(sq[0])*dx,y=a.y+(8-Number(sq[1]))*dy;if(Math.abs(c.x-x)>2||Math.abs(c.y-y)>2||Math.abs(c.bounds[2]-c.bounds[0]-Math.abs(dx))>3||Math.abs(c.bounds[3]-c.bounds[1]-Math.abs(dy))>3)return null;}
 const rows=[];for(let rank=8;rank>=1;rank--){let row='',empty=0;for(const file of files){const piece=squares.get(file+rank).piece;if(piece==='.')empty++;else{if(empty)row+=empty;empty=0;row+=piece;}}if(empty)row+=empty;rows.push(row);}
 const piecePlacementFen=rows.join('/'),cells=[...squares].sort(([a],[b])=>a.localeCompare(b)).map(([square,c])=>[square,c.piece,c.bounds]);
 const boardId=createHash('sha256').update(JSON.stringify([frame.packageName,frame.sessionId,frame.displayId,frame.windowId,frame.width,frame.height,cells])).digest('hex');
 return {boardId,piecePlacementFen,sideToMove:null,castlingRights:null,enPassant:null,orientation:dy>0?'white_bottom':'black_bottom',squares};
}
export function boardSummary(frame){const b=chessBoard(frame);if(!b)return undefined;const {squares,...summary}=b;return {...summary,kind:'chess',instruction:'Observed pieces only, not a full FEN: side to move/castling/en passant are unknown. Choose a legal move yourself. Use phone_board_move with boardId and 4-character uci; it rechecks the current board before gestures. No chess engine.'};}
function binding(value,args){if((value?.packageName!=null&&value.packageName!==args.packageName)||(value?.sessionId!=null&&value.sessionId!==args.sessionId))throw Error('Chess evidence belongs to a different app/session.');}
/** Direct accessibility actions need exact semantic board evidence, not raster coordinates.
 * Selection can add highlights/change action flags. Reread all 64 cells before the
 * destination; changed pieces, app/session/window or geometry stop the partial move.
 * Never retry a refused/uncertain click and never fall back to touch after it.
 */
async function semanticMove(command,selected,frame,board,from,to){
 let executedCount=0,attempted=false;
 try{
  attempted=true;const first=await command('click',{...selected,snapshotId:frame.snapshotId,nodeId:board.squares.get(from).nodeId});binding(first,selected);
  if(first.performed!==true)throw Error('Source accessibility action was not confirmed.');
  executedCount=1;attempted=false;
  const fresh=await command('snapshot',{...selected,limit:350});binding(fresh,selected);const current=chessBoard(fresh);
  if(fresh.packageName!==selected.packageName||fresh.sessionId!==selected.sessionId||!current||current.boardId!==board.boardId)throw Error('Chess position/window changed after selection; destination not sent.');
  const target=current.squares.get(to);if(!target.clickable||typeof target.nodeId!=='string')throw Error('Destination accessibility action unavailable after selection.');
  attempted=true;const second=await command('click',{...selected,snapshotId:fresh.snapshotId,nodeId:target.nodeId});binding(second,selected);
  if(second.performed!==true)throw Error('Destination accessibility action was not confirmed.');
  executedCount=2;attempted=false;
  return {completed:true,executedCount,totalSteps:2,failedStep:null,inputMayHaveOccurred:true,inputTransport:'accessible_squares',selectionRevalidated:true,applicationEffectVerified:false};
 }catch(error){return {completed:false,executedCount,totalSteps:2,failedStep:executedCount,inputMayHaveOccurred:attempted||executedCount>0,inputTransport:'accessible_squares',requiresModel:true,error:String(error?.message??error).slice(0,500),applicationEffectVerified:false};}
}
export async function boardMove(command,args){
 const selected={packageName:args.packageName,sessionId:args.sessionId,_ownerKey:args._ownerKey};
 let frame=await command('snapshot',{...selected,limit:350});binding(frame,selected);if(frame.packageName!==args.packageName||frame.sessionId!==args.sessionId)throw Error('Missing scoped board identity; no input sent.');
 let board=chessBoard(frame);if(!board)throw Error('No complete supported accessible chess board. Observe the app; no input sent.');
 if(board.boardId!==args.boardId.toLowerCase())throw Error('Chess board or window changed. Read the new board before deciding; no input sent.');
 const from=args.uci.slice(0,2),to=args.uci.slice(2,4),source=board.squares.get(from),target=board.squares.get(to);
 if(from===to||source.piece==='.')throw Error('Invalid observed source square; no input sent.');
 const white=p=>p===p.toUpperCase();if(target.piece!=='.'&&white(source.piece)===white(target.piece))throw Error('Destination contains an observed same-color piece; no input sent.');
 if([...board.squares.values()].every(c=>c.clickable&&typeof c.nodeId==='string')){
  const result=await semanticMove(command,selected,frame,board,from,to);
  return {...result,uci:args.uci,priorBoardId:board.boardId,sourcePiece:source.piece,replayed:false,instruction:'Exact accessible square actions; full position and geometry rechecked after source selection. Check completed/executedCount and final board; never replay a partial or uncertain move. This does not choose/evaluate moves.'};
 }
 let shot=await command('screenshot',{...selected,snapshotId:frame.snapshotId,maxDimension:1920,quality:95,annotate:false});binding(shot,selected);
 if(shot.snapshotId!==frame.snapshotId||shot.frameContent!=='visible_pixels')throw Error('Fresh source capture unavailable for board move; no input sent.');
 if(shot.captureRecovery?.recoveredFromBlack){
  // The opponent/window may have changed while pixels were catching up. Recheck the
  // complete board, then bind one final capture to that new observation before input.
  const fresh=await command('snapshot',{...selected,limit:350});binding(fresh,selected);
  const current=chessBoard(fresh);
  if(fresh.packageName!==args.packageName||fresh.sessionId!==args.sessionId||!current||current.boardId!==board.boardId)throw Error('Chess board changed during capture recovery; read the new position before deciding; no input sent.');
  frame=fresh;board=current;
  shot=await command('screenshot',{...selected,snapshotId:frame.snapshotId,maxDimension:1920,quality:95,annotate:false});binding(shot,selected);
  if(shot.snapshotId!==frame.snapshotId||shot.frameContent!=='visible_pixels')throw Error('Fresh source capture unavailable after board revalidation; no input sent.');
 }
 const result=await command('action_sequence',{...selected,snapshotId:frame.snapshotId,steps:[{op:'tap',x:Math.round(source.x),y:Math.round(source.y)},{op:'tap',x:Math.round(target.x),y:Math.round(target.y)}],intervalMs:40,timeoutMs:4000});binding(result,selected);
 return {...result,uci:args.uci,priorBoardId:board.boardId,sourcePiece:source.piece,replayed:false,instruction:'One observed move sequence sent. Check executedCount/failedStep and final board; never replay uncertain inputs. This tool does not choose moves or enforce full chess legality.'};
}
