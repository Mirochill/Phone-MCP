import {boardMove,chessBoard} from './chess-board.js';
import {startApp,uiFlow,adResult} from './fast-workflows.js';
import {recoverRead} from './read-recovery.js';
import {commandWithObservation,compactSnapshot} from './screen-output.js';
import {withLegacyLongPress} from './legacy-interactions.js';

const pause=ms=>new Promise(resolve=>setTimeout(resolve,ms));
export async function runOperation(command,op,args){
  if(['ad_start','ad_status','ad_cancel'].includes(op))return adResult(command,op,args);
  if(op==='send_file'){
    const extensions={'video/mp4':'.mp4','image/png':'.png','image/jpeg':'.jpg','application/pdf':'.pdf','text/plain':'.txt','application/zip':'.zip'};
    const fileName=args.fileName??args.file.file_name??('fichier-'+args.requestId.slice(0,8)+(extensions[args.file.mime_type]??''));
    if(!fileName||fileName.length>140||fileName!==fileName.trim()||fileName.startsWith('.')||/[\\/:*?"<>|\x00-\x1f\x7f]/.test(fileName)||fileName.toLowerCase().endsWith('.part'))throw Error('Invalid file name. Supply a simple fileName without directories.');
    return command('download_file',{requestId:args.requestId,url:args.file.download_url,fileName,sha256:args.sha256,maxBytes:args.maxBytes,_ownerKey:args._ownerKey});
  }
  const send=command;command=(operation,selection)=>recoverRead(send,operation,selection);
  if(op==='find_nodes'||op==='wait_for'){
    const start=Date.now(),deadline=start+(op==='wait_for'?args.timeoutMs:0);
    do{
      const {timeoutMs,...selection}=args;
      const screen=await command('snapshot',{...selection,limit:200,find:true});
      const result=withLegacyLongPress(compactSnapshot(screen));
      if(result.nodes?.length||op==='find_nodes')return {...result,found:!!result.nodes?.length,elapsedMs:Date.now()-start};
      if(Date.now()>=deadline)return {...result,found:false,timedOut:true,elapsedMs:Date.now()-start};
      await pause(Math.min(250,Math.max(0,deadline-Date.now())));
    }while(true);
  }
  if(op==='wait_for_idle'){
    const start=Date.now(),deadline=start+args.timeoutMs;
    let last,stableSince=Date.now(),screen;
    do{
      screen=await command('snapshot',{packageName:args.packageName,sessionId:args.sessionId,_ownerKey:args._ownerKey,limit:200});
      const fingerprint=screen.contentFingerprint??JSON.stringify(screen.nodes);
      if(fingerprint!==last){last=fingerprint;stableSince=Date.now();}
      if(Date.now()-stableSince>=args.stableMs)return {idle:true,elapsedMs:Date.now()-start,observation:withLegacyLongPress(compactSnapshot(screen))};
      if(Date.now()>=deadline)return {idle:false,timedOut:true,elapsedMs:Date.now()-start,observation:withLegacyLongPress(compactSnapshot(screen))};
      await pause(Math.min(250,Math.max(0,deadline-Date.now())));
    }while(true);
  }
  const deviceCommand=command;
  if(op==='start_app'||op==='ui_flow'||op==='board_move')command=(operation,selection)=>operation==='start_app'?startApp(deviceCommand,selection):operation==='ui_flow'?uiFlow(deviceCommand,selection):operation==='board_move'?boardMove(deviceCommand,selection):deviceCommand(operation,selection);
  const result=await commandWithObservation(command,op,args);
  if(op==='board_move'){const final=chessBoard(result.observation);result.moveVerified=!!final&&final.squares.get(args.uci.slice(0,2))?.piece==='.'&&final.squares.get(args.uci.slice(2,4))?.piece===result.sourcePiece;if(final){result.boardChanged=final.boardId!==result.priorBoardId;result.finalPiecePlacementFen=final.piecePlacementFen;}}
  if(op==='snapshot'&&args.includeScreenshot){
    try{result.screenshot=await command('screenshot',{packageName:args.packageName,sessionId:args.sessionId??result.sessionId,_ownerKey:args._ownerKey,snapshotId:result.snapshotId,maxDimension:args.maxDimension,quality:args.quality,annotate:args.annotate});}
    catch(error){result.screenshotError=error.message;}
  }
  return result;
}
