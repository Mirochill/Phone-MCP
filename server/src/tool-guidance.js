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
  const describe=()=>({...metadata,serverVersion,source:'live_server',tools:[...names],stableTools:['phone_catalog','phone_read','phone_action'],instruction:metadata.catalogVersion?'For speed use phone_start_app once and phone_ui_flow for known exact clicks/waits; action_sequence for observed game gestures. Ads: prefer phone_ad_start, then ad_status(waitMs=20000,observe=true,includeScreenshot=true); ad_cancel stops only that job. Timers and rewards remain app-controlled. Reuse observation; do not request another snapshot. Discover schemas with phone_catalog; cached chats use phone_read/phone_action. Verify effects, never replay uncertain writes. session_wake cannot enable physical keep-on. Legacy longPressNodeId remains supported.':instruction,clientRefreshControlledBy:'host'});
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
