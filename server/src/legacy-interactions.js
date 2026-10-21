// This reserved child index cannot appear in a bounded Android tree. The numeric form
// also satisfies legacy phone_click schemas, without reinterpreting ordinary node IDs.
const prefix='0.2147483647.';
const tapPrefix='0.2147483646.';
const swipePrefix='0.2147483645.';
const nodePattern=/^0(?:\.\d+)*$/;
const guide='Même avec une ancienne liste : phone_click accepte le longPressNodeId observé pour un appui long et les modèles de coordinateActions pour tap/swipe, avec le même packageName/snapshotId. Les coordonnées sont celles du snapshot, jamais celles d’une image réduite sans conversion. Le nodeId ordinaire conserve le clic simple.';

export function resolveLegacyInteraction(op,args){
  if(op!=='click'||typeof args.nodeId!=='string')return {op,args};
  for(const [marker,action,keys] of [[tapPrefix,'tap',['x','y']],[swipePrefix,'swipe',['fromX','fromY','toX','toY','durationMs']]]){
    if(!args.nodeId.startsWith(marker))continue;
    const values=args.nodeId.slice(marker.length).split('.');
    if(values.length!==keys.length||values.some(v=>!/^\d{1,5}$/.test(v)))throw new Error('Identifiant de geste invalide. Relisez phone_snapshot.');
    const coordinates=Object.fromEntries(keys.map((key,i)=>[key,Number(values[i])]));
    if(values.some((v,i)=>keys[i]==='durationMs'?Number(v)<100||Number(v)>1000:Number(v)>16383))throw new Error('Coordonnées ou durée de geste invalides.');
    const {nodeId,...rest}=args;return {op:action,args:{...rest,...coordinates}};
  }
  if(!args.nodeId.startsWith(prefix))return {op,args};
  const target=args.nodeId.slice(prefix.length);
  if(!nodePattern.test(target))throw new Error('Identifiant d’appui long invalide. Relisez phone_snapshot.');
  return {op:'long_press',args:{...args,nodeId:target}};
}

export function describeLegacyInteractions(result){
  if(!result||typeof result!=='object'||Array.isArray(result))return result;
  return {interactionGuide:guide,...result};
}

export function withLegacyLongPress(snapshot){
  if(!snapshot||!Array.isArray(snapshot.nodes))return snapshot;
  const nodes=snapshot.nodes.map(node=>{
    if(typeof node.nodeId!=='string'||!nodePattern.test(node.nodeId)||node.enabled===false
        ||node.text==='[champ protégé]'||!(node.text||node.description||node.clickable||node.longClickable))return node;
    return {...node,longPressNodeId:prefix+node.nodeId};
  });
  const coordinateActions={
    tapNodeIdTemplate:tapPrefix+'{x}.{y}',
    swipeNodeIdTemplate:swipePrefix+'{fromX}.{fromY}.{toX}.{toY}.{durationMs}',
    tool:'phone_click',coordinateSpace:'snapshot_pixels',
    instruction:'Remplacez les variables par des entiers observés dans la fenêtre lue ; durationMs entre 100 et 1000. Utilisez packageName et snapshotId de cette lecture. Le téléphone vérifie le mode, les droits, le focus et les limites de fenêtre.'
  };
  return {...describeLegacyInteractions(snapshot),coordinateActions,nodes};
}
