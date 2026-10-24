import {compactSnapshot} from './screen-output.js';
import {withLegacyLongPress} from './legacy-interactions.js';

const binding=(value,args,label)=>{
 if(value?.packageName!=null&&value.packageName!==args.packageName)throw Error(label+' belongs to a different application.');
 if(value?.sessionId!=null&&value.sessionId!==args.sessionId)throw Error(label+' belongs to a different session.');
};
const summary=error=>String(error?.message??error).slice(0,500);

/** Inspect an idempotent session before opening: repeating the macro never reopens its app. */
export async function startApp(command,args){
 const sessionId=args.requestId,selected={packageName:args.packageName,sessionId,_ownerKey:args._ownerKey};
 const inventory=await command('session_list',{_ownerKey:args._ownerKey});
 if(!Array.isArray(inventory?.sessions))throw Error('Invalid owned session inventory.');
 const existing=inventory.sessions.find(s=>s.sessionId===sessionId);
 if(existing){binding(existing,selected,'Existing session');return {...existing,reused:true,opened:false,sessionCreated:false,instruction:'Existing session retained; the app was not reopened. Read the returned observation. Never repeat an uncertain launch.'};}
 const createArgs={requestId:args.requestId,packageName:args.packageName,leaseSeconds:args.leaseSeconds,keepRendering:args.keepRendering,_ownerKey:args._ownerKey,...(args.label?{label:args.label}:{})};
 const created=await command('session_create',createArgs);
 if(created?.session?.sessionId!==sessionId||created.session.packageName!==args.packageName)throw Error('Created session identity mismatch. Inspect session_list; never repeat an uncertain launch.');
 try{const result=await command('open_app',selected);binding(result,selected,'Launch');return {...result,sessionId,packageName:args.packageName,sessionCreated:true,opened:true,reused:false};}
 catch(error){return {sessionId,packageName:args.packageName,sessionCreated:true,opened:false,launchUncertain:true,launchError:summary(error),instruction:'Launch not confirmed. Inspect this exact session before any explicit launch retry. Do not repeat the whole start macro.'};}
}

/** Exact semantic steps; fresh native guards for each write, one final observation. */
export async function uiFlow(command,args,{now=()=>performance.now()}={}){
 const began=now(),deadline=began+args.timeoutMs,steps=[],selected={packageName:args.packageName,sessionId:args.sessionId,_ownerKey:args._ownerKey};
 let completedSteps=0,performedActions=0,failedStep=null,uncertain=false,reason='';
 for(let index=0;index<args.steps.length;index++){
  const step=args.steps[index],remaining=Math.floor(deadline-now());
  if(remaining<100){failedStep=index;reason='flow_deadline';break;}
  const timeoutMs=Math.min(remaining,step.timeoutMs??3000);
  try{
   let result;
   const query={...selected,query:step.query,by:step.by??'text',match:'exact'};
   if(step.op==='wait_and_click'){
    result=await command('wait_and_click',{...query,timeoutMs});binding(result,selected,'Flow action');
    if(result.performed===true)performedActions++;
    steps.push({index,op:step.op,found:result.found===true,performed:result.performed===true});
    if(result.performed!==true){failedStep=index;reason=result.timedOut?'target_timeout':'action_not_confirmed';break;}
   }else{
    const until=now()+timeoutMs;let found;
    do{
     result=await command('snapshot',{...query,limit:200,find:true});binding(result,selected,'Flow read');
     if(result.nodes?.length){found=true;break;}
     if(now()>=until)break;
     await new Promise(r=>setTimeout(r,Math.min(120,Math.max(0,until-now()))));
    }while(true);
    steps.push({index,op:step.op,found:found===true,performed:false});
    if(!found){failedStep=index;reason='target_timeout';break;}
   }
   completedSteps++;
  }catch(error){failedStep=index;uncertain=step.op==='wait_and_click';reason=summary(error);steps.push({index,op:step.op,uncertain,error:reason});break;}
 }
 return {...selected,_ownerKey:undefined,completed:completedSteps===args.steps.length,completedSteps,performedActions,failedStep,uncertain,reason,steps,elapsedMs:Math.round(now()-began),replayed:false};
}

/** Long-poll in <=8s native reads, compatible with existing APKs; never start/click/replay. */
export async function adResult(command,op,args,{now=Date.now}={}){
 const {observe=false,includeScreenshot=false,maxDimension=1000,quality=75,annotate=false,waitMs=0,...selected}=args;
 if(includeScreenshot&&!observe)throw Error('includeScreenshot requires observe=true.');
 const deadline=now()+waitMs,maxPolls=op==='ad_status'?Math.max(1,Math.ceil(waitMs/8000)):1;let result,polls=0;
 do{
  const native={...selected,...(op==='ad_status'?{waitMs:Math.min(8000,Math.max(0,deadline-now()))}:{})};
  result=await command(op,native);polls++;
  if(!result||typeof result!=='object'||Array.isArray(result))throw Error('Invalid ad job response.');
  if(result.packageName!==args.packageName)throw Error('Ad job belongs to a different application.');
  if(result.sessionId!==args.sessionId)throw Error('Ad job belongs to a different session.');
  if(result.jobId!==(op==='ad_start'?args.requestId:args.jobId))throw Error('Ad job response belongs to a different job.');
 }while(op==='ad_status'&&(result.active===true||result.state==='running')&&now()<deadline&&polls<maxPolls);
 const next={...result,statusReads:polls,instruction:'Local job: completed confirms a matching return target, never a reward. Preserve this package/session/job. Do not restart an uncertain job.'};
 if(observe){
  if(result.active===true||result.state==='running')next.observationDeferred=true;
  else try{
   const selection={packageName:args.packageName,sessionId:args.sessionId,_ownerKey:args._ownerKey};
   const screen=await command('snapshot',selection);binding(screen,selection,'Ad result observation');
   next.observation=withLegacyLongPress(compactSnapshot(screen));
   if(includeScreenshot)try{const shot=await command('screenshot',{...selection,snapshotId:screen.snapshotId,maxDimension,quality,annotate});binding(shot,selection,'Ad result capture');if(shot.snapshotId!=null&&shot.snapshotId!==screen.snapshotId)throw Error('Ad capture snapshot mismatch.');next.screenshot=shot;}catch(error){next.screenshotError=summary(error);}
  }catch(error){next.observationError=summary(error);}
 }
 return next;
}
