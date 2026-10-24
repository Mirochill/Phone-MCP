import {recoverRead} from './read-recovery.js';
import {commandWithObservation,compactSnapshot} from './screen-output.js';
import {withLegacyLongPress} from './legacy-interactions.js';

const pause=ms=>new Promise(resolve=>setTimeout(resolve,ms));
export async function runOperation(command,op,args){
  if(['ad_start','ad_status','ad_cancel'].includes(op)){
    // Each API call is one device command. A job runs locally after ad_start has returned;
    // never retry its start/cancel or turn a status read into more phone actions.
    const result=await command(op,args);
    if(!result||typeof result!=='object'||Array.isArray(result))throw Error('Invalid ad job response.');
    if(result.packageName!==args.packageName)throw Error('Ad job belongs to a different application.');
    if(result.sessionId!==args.sessionId)throw Error('Ad job belongs to a different session.');
    const expected=op==='ad_start'?args.requestId:args.jobId;
    if(result.jobId!==expected)throw Error('Ad job response belongs to a different job.');
    return {...result,instruction:'Local job: completed confirms a matching return target, never a reward. Read ad_status with this exact packageName/sessionId/jobId. Do not restart or repeat uncertain ad actions.'};
  }
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
  const result=await commandWithObservation(command,op,args);
  if(op==='snapshot'&&args.includeScreenshot){
    try{result.screenshot=await command('screenshot',{packageName:args.packageName,sessionId:args.sessionId??result.sessionId,_ownerKey:args._ownerKey,snapshotId:result.snapshotId,maxDimension:args.maxDimension,quality:args.quality,annotate:args.annotate});}
    catch(error){result.screenshotError=error.message;}
  }
  return result;
}
