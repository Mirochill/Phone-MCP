const pause=ms=>new Promise(resolve=>setTimeout(resolve,ms));
const reads=new Set(['snapshot','screenshot','node_details']);
const blankWaits=[100,200,350,500];
/** Retry only reads: a black transition frame is not evidence that rendering has failed.
 * Every retry goes back to Android and its app/window/grant guards. No cached image,
 * app relaunch, wake command or input is used. Healthy captures incur no extra read.
 */
export async function recoverRead(command,op,args,{now=Date.now,wait=pause}={}){
 const started=now(),until=started+1800;
 let selection=args,attempts=0,blackFrames=0,first;
 while(true){try{
  attempts++;
  const value=await command(op,selection);
  if(op!=='screenshot')return value;
  if(blackFrames){
   for(const key of ['sessionId','snapshotId','displayId'])if(first[key]!=null&&value?.[key]!==first[key])throw Error('Capture recovery scope changed; read a fresh snapshot before acting.');
   if(value?.packageName!=null&&value.packageName!==args.packageName)throw Error('Capture recovery belongs to a different application.');
  }
  if(blackFrames&&value?.frameContent!=='black_or_empty'&&value?.frameContent!=='visible_pixels')throw Error('Capture recovery returned no visual evidence.');
  if(value?.frameContent!=='black_or_empty')return blackFrames?{...value,captureRecovery:{state:'visible_after_retry',attempts,blackFrames,elapsedMs:now()-started,recoveredFromBlack:true}}:value;
  if(!first){
   first=value;
   if((args.sessionId!=null&&value.sessionId!==args.sessionId)||(args.snapshotId!=null&&value.snapshotId!==args.snapshotId)||(value.packageName!=null&&value.packageName!==args.packageName))throw Error('Black capture scope mismatch; no recovery attempted.');
   // Legacy callers also remain pinned to the exact session returned by Android.
   if(!value.sessionId)return value;
   if(args.sessionId==null)selection={...args,sessionId:value.sessionId};
  }
  blackFrames++;
  const delay=blankWaits[blackFrames-1];
  if(delay==null||now()+delay>=until)return {...value,captureRecovery:{state:'still_black',attempts,blackFrames,elapsedMs:now()-started,recoveredFromBlack:false},instruction:'The short read-only recapture budget is exhausted. This is not usable visual evidence and does not mean the app/session ended. Read a fresh snapshot before deciding; never send a blind gesture or replay an input.'};
  await wait(delay);
 }catch(error){
  if(!reads.has(op)||!/Aucune fenêtre|L’app demandée n’est pas visible|Fenêtre IA temporairement indisponible|No accessible window/.test(error.message)||now()>=until)throw error;
  await wait(80);
 }}
}
