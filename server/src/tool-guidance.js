import { z } from 'zod';

const instruction='Catalogue actuel du serveur. Les droits réels restent ceux de phone_status. Appui long : phone_long_press ou phone_click avec longPressNodeId. Toucher/glisser : phone_tap/phone_swipe ou phone_click avec les modèles coordinateActions du snapshot si ces outils manquent dans votre cache. Même packageName/snapshotId ; pixels du snapshot, convertir les images réduites selon coordinateSpace. Un nodeId ordinaire reste un clic simple. APK 0.8+ : gestes aussi en foreground, sans passage au mode séparé. phone_set_control_mode choisit explicitement le mode ou répare un affichage arrêté. Réutilisez observation. Si un autre outil manque, utilisez la redécouverte supportée par le client ; sinon actualisez sa connexion. N’inventez pas d’appel à un outil non exposé.';

export const toolingSchema=z.object({
  apiVersion:z.number().optional(),catalogVersion:z.string().optional(),stableTools:z.array(z.string()).optional(),
  serverVersion:z.string(),
  source:z.literal('live_server'),
  tools:z.array(z.string()),
  instruction:z.string(),
  clientRefreshControlledBy:z.literal('host')
});

/** Metadata only: no phone command, background polling, cached UI, or change of permissions. */
export function createToolGuidance(serverVersion,metadata={}){
  const names=[];
  const describe=()=>({...metadata,serverVersion,source:'live_server',tools:[...names],stableTools:['phone_catalog','phone_read','phone_action'],instruction:metadata.catalogVersion?'Use phone_catalog + phone_read/phone_action. Ads: prefer phone_ad_start with observed returnTargets, then ad_status; ad_cancel stops only that job. App timers are respected; hidden controls can be revealed with bounded neutral taps. Reuse observation; action_sequence batches observed gestures. Black frames are not visual evidence. User handoff allows a fresh session, never old-session replay. Verify app effects, never replay uncertain writes. session_wake only recovers the AI display under local screen settings; it cannot enable a lit physical screen. Legacy longPressNodeId/coordinateActions work.':instruction,clientRefreshControlledBy:'host'});
  const attach=result=>{
    // Device/application data must never replace the server's capability guide.
    const {tooling:ignored,...payload}=result&&typeof result==='object'&&!Array.isArray(result)?result:{result};
    return {tooling:describe(),...payload};
  };
  return {
    registered:name=>{if(!names.includes(name))names.push(name);},
    describe,
    attach,
    text:()=>({type:'text',text:JSON.stringify({tooling:describe()})})
  };
}
