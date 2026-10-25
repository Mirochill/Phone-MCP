import {randomUUID} from 'node:crypto';

/** Correlates every response by connection and UUID; writes are never replayed. */
export class CommandChannel {
  pending=new Map();
  constructor(timeoutMs=12000){this.timeoutMs=timeoutMs;}
  send(ws,op,args,signal){
    if(!ws||ws.readyState!==1)return Promise.reject(Error('Téléphone hors ligne. Démarrez une session dans Phone MCP.'));
    if(signal?.aborted)return Promise.reject(Error('Commande annulée avant envoi.'));
    if(this.pending.size>=(['session_cancel','session_end','stop','ad_cancel','plan_cancel'].includes(op)?20:16))return Promise.reject(Error('Limite de commandes simultanées atteinte.'));
    return new Promise((resolve,reject)=>{
      const budget=(op==='ad_status'||op==='plan_status')?Math.max(this.timeoutMs,Math.min(9000,Math.max(0,Number(args.waitMs)||0)+1000)):this.timeoutMs;
      const id=randomUUID(),expiresAt=Date.now()+budget;
      const finish=(error,value)=>{
        if(!this.pending.delete(id))return;
        clearTimeout(timer);signal?.removeEventListener('abort',abort);
        error?reject(error):resolve(value);
      };
      const abort=()=>finish(Error('Requête annulée. Une action remise à Android peut avoir eu lieu ; relisez avant de réessayer.'));
      const timer=setTimeout(()=>finish(Error('Délai dépassé. Une action remise à Android peut avoir eu lieu ; relisez avant de réessayer.')),budget);
      this.pending.set(id,{ws,finish});signal?.addEventListener('abort',abort,{once:true});
      try{ws.send(JSON.stringify({id,op,args,expiresAt}));}catch{finish(Error('Envoi impossible. Le téléphone doit se reconnecter.'));}
    });
  }
  receive(ws,response){
    const active=this.pending.get(response.id);if(!active||active.ws!==ws)return;
    if(response.ok===true&&response.result&&typeof response.result==='object')active.finish(null,response.result);
    else active.finish(Error(typeof response.error==='string'?response.error.slice(0,500):'Le téléphone a refusé la commande.'));
  }
  disconnect(ws,message='Téléphone déconnecté.'){
    for(const active of this.pending.values())if(!ws||active.ws===ws)active.finish(Error(message));
  }
}

/** Serialize a complete action + observation only within its own session. */
export class OperationScheduler {
  lanes=new Map();ended=new Map();transportRevision=0;timingHistory=new Map();
  // Content-free, owner-scoped diagnostics: bounded RAM only, no polling/storage.
  recordTiming(owner,event){
    const {operation,startedAt,completedAt,totalMs,deviceMs,deviceCommands,queueMs,success}=event;
    const recent=this.recentTimings(owner,completedAt);
    recent.push({operation,startedAt,completedAt,totalMs,deviceMs,deviceCommands,queueMs,success});
    this.timingHistory.delete(owner);this.timingHistory.set(owner,recent.slice(-48));
    while(this.timingHistory.size>32)this.timingHistory.delete(this.timingHistory.keys().next().value);
  }
  recentTimings(owner,now=Date.now()){
    return (this.timingHistory.get(owner)??[]).filter(e=>e.completedAt>=now-300000).map(e=>({...e}));
  }
  disconnect(){this.transportRevision++;}
  key(args){return args._ownerKey+'|'+(args.sessionId??('app:'+args.packageName));}
  run(op,args,work){
    const key=this.key(args),terminal=op==='session_end'||op==='session_cancel';
    if(terminal){
      this.ended.set(key,Date.now());while(this.ended.size>256)this.ended.delete(this.ended.keys().next().value);
      return work(command=>command());
    }
    const scoped=args.sessionId||args.packageName;
    if(!scoped||['notifications','reply_notification','notification_dismiss','notification_snooze'].includes(op))return work(command=>command());
    if(args.sessionId&&this.ended.has(key))return Promise.reject(Error('Session arrêtée. Créez une nouvelle session autorisée.'));
    // Long-poll reads and cancellation must stay responsive even while a native wait
    // occupies this session's command lane. Cancelling an ad never ends the session.
    if(op==='ad_status'||op==='ad_cancel'||op==='plan_status'||op==='plan_list'||op==='plan_cancel'){
      const transport=this.transportRevision;
      const guard=command=>{
        if(transport!==this.transportRevision)throw Error('Transport déconnecté ; commande annulée.');
        if(args.sessionId&&this.ended.has(key))throw Error('Session arrêtée ; commande annulée.');
        return command();
      };
      return Promise.resolve().then(()=>guard(()=>work(guard)));
    }
    const lane=this.lanes.get(key)??{tail:Promise.resolve(),count:0};
    if(lane.count>=24)return Promise.reject(Error('File de commandes de cette session pleine.'));
    this.lanes.set(key,lane);lane.count++;
    const queuedAt=Date.now(),transport=this.transportRevision,guard=command=>{
      if(transport!==this.transportRevision)throw Error("Transport déconnecté ; commande en attente annulée.");
      if(args.sessionId&&this.ended.has(key))throw Error('Session arrêtée ; commande annulée.');
      if(Date.now()-queuedAt>30000)throw Error('Commande expirée avant exécution.');
      return command();
    };
    const result=lane.tail.then(()=>guard(()=>work(guard)));
    lane.tail=result.catch(()=>{}).finally(()=>{lane.count--;if(!lane.count)this.lanes.delete(key);});
    return result;
  }
}
