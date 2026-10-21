/** Public MCP status contract, including APKs whose legacy flag meant foreground only. */
export function describeControlStatus(raw) {
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) throw new Error('Statut du téléphone invalide.');
  const mode=raw.controlMode;
  const parallel=raw.parallel && typeof raw.parallel==='object' && !Array.isArray(raw.parallel) ? raw.parallel : {};
  const active=raw.sessionActive!==false;
  const explicit=(value,fallback)=>typeof value==='boolean'?value:fallback;
  const foregroundAllowed=active && mode==='foreground' && explicit(raw.foregroundScreenControlAllowed,raw.screenControlAllowed===true);
  const parallelAllowed=active && mode==='parallel' && explicit(raw.parallelScreenControlAllowed,true);
  const validDisplay=Number.isInteger(parallel.displayId) && parallel.displayId>0;
  // APK 0.7+ verifies a trusted, own-group, always-unlocked display in Android before
  // exposing this capability. A locked primary display is not a locked virtual display.
  const independent=parallel.independentKeyguard===true;
  const parallelLocked=!independent && (parallel.keyguardLocked===true || parallel.deviceInteractive===false);
  const parallelReady=parallelAllowed && parallel.ready===true && validDisplay && !parallelLocked;
  const foregroundLocked=raw.keyguardLocked===true || raw.deviceInteractive===false;
  const foregroundReady=foregroundAllowed && !foregroundLocked
    ? explicit(raw.foregroundScreenControlReady,explicit(raw.screenControlReady,null)) : false;
  const allowed=foregroundAllowed || parallelAllowed;
  const ready=parallelAllowed ? parallelReady : foregroundAllowed ? foregroundReady : false;
  let summary,nextAction;
  if(parallelAllowed && parallelReady){
    summary='Contrôle des apps autorisé et prêt sur l’écran séparé. Les actions renvoient leur observation. Le contrôle de l’écran principal est désactivé dans ce mode.'+(independent?' Cet affichage reste indépendant du verrouillage et du bouton latéral de l’écran principal.':'');
    nextAction='use_parallel_tools';
  }else if(parallelAllowed){
    summary=parallelLocked
      ? parallel.resumeWithoutRestart===true?'L’affichage séparé est conservé, mais cette version Android partage sa veille avec l’écran principal. Rallumez et déverrouillez le téléphone ; la même session reprendra sans redémarrage.':'Contrôle des apps autorisé sur l’écran séparé, actuellement arrêté car Android est verrouillé ou en veille. Déverrouillez le téléphone puis relancez une session Écran séparé ; l’option d’extinction de Phone MCP permet de garder le contrôle dalle éteinte.'
      : 'Contrôle des apps autorisé sur l’écran séparé, mais cet affichage est indisponible. Vérifiez Shizuku et relancez une session Écran séparé.';
    nextAction=parallelLocked?(parallel.resumeWithoutRestart===true?'unlock_phone':'unlock_and_restart_parallel_session'):'restart_parallel_session_with_shizuku';
    if(!parallelLocked&&raw.controlModeSwitchAllowed===true){summary='L’affichage séparé est indisponible. phone_set_control_mode permet de redémarrer parallel ou de choisir explicitement foreground pour un jeu incompatible, sans recréer la session. Le téléphone doit être déverrouillé pour changer de mode.';nextAction='recover_parallel_or_choose_foreground';}
  }else if(foregroundAllowed){
    summary=foregroundLocked?'Contrôle autorisé sur l’écran du téléphone, actuellement verrouillé ou en veille. Déverrouillez-le pour les actions d’écran.'
      : 'Contrôle autorisé sur l’écran du téléphone. Les actions d’app utilisent cet écran ; Android vérifiera sa disponibilité à chaque commande.';
    nextAction=foregroundLocked?'unlock_phone':'use_foreground_tools';
  }else{
    summary=mode==='notifications'?'Session notifications : les actions d’écran ne sont pas autorisées. Les outils de notifications et de fichiers restent soumis à leurs accès propres.'
      : 'Aucun mode de contrôle d’écran autorisé n’est confirmé par ce statut. Vérifiez la session sur le téléphone.';
    nextAction=mode==='notifications'?'use_notifications_or_start_screen_session':'check_phone_session';
  }
  return {...raw,statusContractVersion:2,screenControlAllowed:allowed,screenControlReady:ready,
    foregroundScreenControlAllowed:foregroundAllowed,parallelScreenControlAllowed:parallelAllowed,
    activeControlDisplay:parallelAllowed?'separate':foregroundAllowed?'primary':'none',
    controlSummary:summary,nextControlAction:nextAction};
}
