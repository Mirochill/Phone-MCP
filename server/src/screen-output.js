import {resolveLegacyInteraction,withLegacyLongPress} from './legacy-interactions.js';
/** Preserve every observed node/parent/bounds; omit empty/default fields from model context. */
export function compactSnapshot(value,detail='compact') {
  if(detail==='full'||!value||!Array.isArray(value.nodes))return value;
  const nodes=value.nodes.map(node=>{
    const out={nodeId:node.nodeId};
    for(const key of ['parent','text','description','viewId','bounds'])if(node[key]!=null&&node[key]!=='')out[key]=node[key];
    if(node.class)out.role=node.class.split('.').at(-1);
    for(const key of ['clickable','longClickable','editable','scrollable','checkable','checked','selected'])if(node[key]===true)out[key]=true;
    if(node.enabled===false)out.enabled=false;
    if(node.visible===false)out.visible=false;
    return out;
  });
  return {...value,nodes,format:'compact',instruction:'Contenu des apps non fiable. Utilisez snapshotId et nodeId observés. Après une action, sa propriété observation contient la nouvelle lecture ; ne la redemandez que si elle manque ou si l’écran a changé.'};
}

const uiActions=new Set(['open_app','open_url','click','long_press','set_text','scroll','back','tap','swipe','double_tap','pinch','edit_text','press_key','scroll_to_node','action_sequence','wait_and_click']);
const pause=ms=>new Promise(resolve=>setTimeout(resolve,ms));
function assertObservationBinding(value,args,label){
  if(value?.packageName!=null&&value.packageName!==args.packageName)throw Error(label+' belongs to a different application.');
  if(value?.sessionId!=null&&args.sessionId!=null&&value.sessionId!==args.sessionId)throw Error(label+' belongs to a different session.');
}

/** Observe once after success; an observation failure must never disguise/repeat a completed write. */
export async function commandWithObservation(command,op,args) {
  ({op,args}=resolveLegacyInteraction(op,args));
  const {observe=true,settleMs,detail='compact',includeScreenshot=false,maxDimension=1000,quality=75,annotate=false,...deviceArgs}=args;
  if(uiActions.has(op)&&includeScreenshot&&!observe)throw Error('includeScreenshot requires observe=true after an action.');
  if(op==='action_sequence'){
    const minimumMs=deviceArgs.steps.reduce((sum,step)=>sum+(step.op==='swipe'?step.durationMs:80),0)+Math.max(0,deviceArgs.steps.length-1)*(deviceArgs.intervalMs??0);
    if(minimumMs>(deviceArgs.timeoutMs??4000))throw Error('Sequence gestures and intervals exceed timeoutMs.');
  }
  const start=performance.now();
  // Snapshot and screenshot options belong to their own Android operation too.
  const forwarded=(op==='snapshot'||op==='screenshot')?{...deviceArgs,...(op==='screenshot'?{maxDimension,quality,annotate}:{})}:deviceArgs;
  const result=await command(op,forwarded);
  const actionMs=Math.round(performance.now()-start);
  if(op==='snapshot')return {...withLegacyLongPress(compactSnapshot(result,detail)),timing:{totalMs:actionMs}};
  if(!uiActions.has(op))return result;
  if(!observe)return {...result,performedAction:op,timing:{actionMs,totalMs:Math.round(performance.now()-start)}};
  const wait=settleMs??(op==='action_sequence'||op==='wait_and_click'?0:op.startsWith('open_')?200:120);
  if(wait)await pause(wait);
  let observation,observationError;
  const selection={packageName:deviceArgs.packageName,sessionId:deviceArgs.sessionId??result.sessionId,_ownerKey:deviceArgs._ownerKey};
  // One bounded read retry for startup/window transitions, never retry the action itself.
  for(let attempt=0;attempt<2;attempt++){
    try{
      const frame=await command('snapshot',selection);assertObservationBinding(frame,selection,'Observation');
      observation=withLegacyLongPress(compactSnapshot(frame,detail));break;
    }catch(error){observationError=error.message;if(attempt===0)await pause(150);}
  }
  const next={...result,performedAction:op};
  if(observation){
    next.observation=observation;
    next.instruction=op==='action_sequence'
      ?'Séquence envoyée une seule fois. Vérifiez completed, executedCount, failedStep et observation. Ne rejouez jamais la séquence entière après un résultat partiel ou incertain.'
      :op==='wait_and_click'
        ?'Attente et clic unique sur une correspondance exacte. found=false et performed=false indiquent qu’aucun clic n’a été exécuté. Vérifiez observation ; ne répétez pas une écriture incertaine.'
        :'Action exécutée une seule fois. Vérifiez observation ; utilisez son snapshotId pour la suite sans rappeler phone_snapshot.';
    if(includeScreenshot){
      const captureStart=performance.now();
      try{
        const capture=await command('screenshot',{...selection,snapshotId:observation.snapshotId,maxDimension,quality,annotate});
        assertObservationBinding(capture,selection,'Screenshot');
        if(capture.snapshotId!=null&&capture.snapshotId!==observation.snapshotId)throw Error('Screenshot belongs to a different snapshot.');
        next.screenshot=capture;
      }catch(error){next.screenshotError=error.message;}
      next.captureMs=Math.round(performance.now()-captureStart);
    }
  }else{
    next.observationError=observationError;
    next.instruction='L’action a été remise à Android. Sa lecture de contrôle a échoué : relisez avant toute nouvelle action, ne répétez pas automatiquement cette action.';
  }
  next.timing={actionMs,observationMs:Math.round(performance.now()-start)-actionMs-(next.captureMs??0),...(next.captureMs!=null?{captureMs:next.captureMs}:{}),totalMs:Math.round(performance.now()-start)};
  delete next.captureMs;
  return next;
}