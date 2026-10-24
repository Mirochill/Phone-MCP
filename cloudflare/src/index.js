import { CloudOAuth,body,json,html,fail,escape } from './oauth.js';
import { digest,randomToken,sameSecret } from '../../server/src/config.js';
import { makeMcp,WebStandardStreamableHTTPServerTransport } from '../../server/src/mcp.js';
import {CommandChannel,OperationScheduler} from '../../server/src/command-channel.js';
import {uploadSelection,readUploadBody,forwardUpload,uploadFailure} from '../../server/src/direct-upload.js';
import {updateRoute} from './updates.js';
import {VERSION as BASE_VERSION} from '../../server/src/version.js';
const VERSION=BASE_VERSION+'-cloud';
const headers={'Cache-Control':'no-store','X-Content-Type-Options':'nosniff','Referrer-Policy':'same-origin','Content-Security-Policy':"default-src 'none'; form-action 'self' https://chatgpt.com; base-uri 'none'; frame-ancestors 'none'"};
export default {
 async fetch(request,env){
  const url=new URL(request.url);
  if(!env.PUBLIC_URL || !env.PHONE_TOKEN || !env.ADMIN_PASSWORD)return json({error:'server_not_configured'},503,headers);
  if(url.origin!==env.PUBLIC_URL && !(env.ALLOW_LOCAL_HTTP==='true' && ['localhost','127.0.0.1'].includes(url.hostname)))return fail('host_denied',403);
  const origin=request.headers.get('Origin');
  if(origin && ![env.PUBLIC_URL,'https://chatgpt.com','https://chat.openai.com'].includes(origin))return fail('origin_denied',403);
  if(request.method==='OPTIONS')return new Response(null,{status:204,headers:{...headers,'Access-Control-Allow-Origin':origin??env.PUBLIC_URL,'Access-Control-Allow-Headers':'Authorization, Content-Type, MCP-Protocol-Version, MCP-Session-Id','Access-Control-Allow-Methods':'GET, POST, PUT, DELETE, OPTIONS'}});
  let result;
  if(url.pathname==='/health' && request.method==='GET')result=json({ok:true,version:VERSION,hosting:'cloudflare',requiresPC:false});
  else if(url.pathname==='/' && request.method==='GET')result=html('<h1>Phone MCP</h1><p>Le serveur fonctionne dans le cloud. Votre PC peut être éteint.</p><p>Utilisez votre lien d’appairage pour connecter le téléphone. Le contrôle reste limité aux apps, capacités et sessions que vous autorisez sur ce téléphone.</p>');
  else {try {
   // Materialize only the bounded binary route before crossing a fetch boundary.
   // A DO may reject early; forwarding the original live request stream can leave
   // the dev/runtime proxy reading it after that response has already completed.
   let routed=request;
   if(url.pathname.startsWith('/uploads/')&&request.body){
    const bytes=await readUploadBody(request.body,null);
    routed=new Request(request,{body:bytes});
   }
   result=await env.PHONE_HUB.get(env.PHONE_HUB.idFromName('personal-phone-v1')).fetch(routed);
  }catch(error){
   if(url.pathname.startsWith('/uploads/')&&/upload_body_too_large|empty_upload_body|incomplete_upload_body/.test(String(error?.message))){const failure=uploadFailure(error);result=json(failure.body,failure.status);}
   else result=fail('server_error',500);
  }}
  if(result.status===101)return result;
  const out=new Response(result.body,result);for(const [k,v] of Object.entries(headers)){if(k==='Content-Security-Policy'&&out.headers.has(k))continue;out.headers.set(k,v);}
  if(origin){out.headers.set('Access-Control-Allow-Origin',origin);out.headers.set('Vary','Origin');out.headers.set('Access-Control-Expose-Headers','WWW-Authenticate, MCP-Session-Id');}
  return out;
 }
};
export class PhoneHub {
 constructor(ctx,env){this.ctx=ctx;this.env=env;this.oauth=new CloudOAuth(ctx.storage,env);this.channel=new CommandChannel(env.ALLOW_LOCAL_HTTP==='true'?240:12000);this.scheduler=new OperationScheduler();
  ctx.blockConcurrencyWhile(async()=>{if(!await ctx.storage.getAlarm())await ctx.storage.setAlarm(Date.now()+86400000);});
 }
 sockets(){return this.ctx.getWebSockets('phone').filter(ws=>ws.readyState===1&&!ws.deserializeAttachment()?.superseded);}
 connection(){const ws=this.sockets()[0];return {connected:!!ws,connectedSince:ws?.deserializeAttachment()?.connectedSince??null,commandInProgress:this.channel.pending.size>0,serverVersion:VERSION,requiresPC:false};}
 async command(op,args,signal){return this.channel.send(this.sockets()[0],op,args,signal);}
 async fetch(request){
  try{const parsed=!new URL(request.url).pathname.startsWith('/uploads/')&&['POST','PUT','PATCH','DELETE'].includes(request.method)?await body(request):undefined;const response=await this.handle(request,parsed);if(new URL(request.url).pathname==='/mcp'){const method=['initialize','notifications/initialized','tools/list','tools/call','ping'].includes(parsed?.method)?parsed.method:'other';const tool=['phone_connection','phone_status','phone_notifications','phone_reply_notification','phone_snapshot','phone_screenshot','phone_open_app','phone_click','phone_set_text','phone_scroll','phone_back','phone_stop'].includes(parsed?.params?.name)?parsed.params.name:undefined;console.log(JSON.stringify({event:'mcp_request',method,tool,status:response.status}));}return response;}catch(error){return fail(error.message==='body_too_large'?'body_too_large':'invalid_request',error.message==='body_too_large'?413:400);}
 }
 async handle(request,parsed){
  const url=new URL(request.url),path=url.pathname,method=request.method;
  const update=await updateRoute(request,this.env,this.ctx.storage);if(update)return update;
  if(path==='/device'){
   if(request.headers.get('Origin') || !sameSecret(request.headers.get('Authorization'),'Bearer '+this.env.PHONE_TOKEN))return fail('unauthorized',401);
   if(request.headers.get('Upgrade')?.toLowerCase()!=='websocket')return fail('websocket_required',426);
   // A newly authenticated device replaces a stale transport. Commands are never replayed.
   if(this.sockets().length)this.scheduler.disconnect();
   for(const old of this.sockets()){
    old.serializeAttachment({...old.deserializeAttachment(),superseded:true});
    this.channel.disconnect(old,'Connexion du téléphone remplacée ; les commandes ne sont pas rejouées.');
    try{old.close(4001,'Device connection replaced');}catch{}
   }
   const pair=new WebSocketPair(),[client,server]=Object.values(pair);
   this.ctx.acceptWebSocket(server,['phone']);server.serializeAttachment({connectedSince:new Date().toISOString()});
   return new Response(null,{status:101,webSocket:client});
  }
  if(path==='/admin/pairing' && method==='POST'){
   if(await this.oauth.limited(request,'admin-pairing',8,60000))return fail('rate_limited',429);
   if(!sameSecret(request.headers.get('Authorization'),'Bearer '+this.env.ADMIN_PASSWORD))return fail('unauthorized',401);
   const code=randomToken(),expiresAt=Date.now()+15*60000;
   await this.ctx.storage.put('pairing',{hash:digest(code),expires:expiresAt});
   return json({endpoint:this.env.PUBLIC_URL,code,expiresAt});
  }
  if(path.startsWith('/pairing/') && method==='GET'){
   const code=path.slice('/pairing/'.length),pairing=await this.oauth.get('pairing');
   if(!pairing || !sameSecret(pairing.hash,digest(code)))return html('<h1>Appairage expiré</h1><p>Ce lien a expiré ou a déjà été utilisé.</p>',410);
   const link='phonemcp://pair?'+new URLSearchParams({server:this.env.PUBLIC_URL,code});
   return html('<!doctype html><html lang="fr"><meta name="viewport" content="width=device-width"><title>Phone MCP</title><h1>Connecter votre téléphone</h1><p>Confirmez le serveur dans Phone MCP, puis choisissez votre mode et démarrez une session.</p><p><a href="'+escape(link)+'">Ouvrir Phone MCP</a></p><p>Vous pouvez aussi copier ce lien d’appairage dans Réglages → Connexion. L’écran séparé et les installations APK utilisent Shizuku ; le contrôle de l’écran physique utilise l’accessibilité Android.</p></html>');
  }
  if(path==='/pair' && method==='POST'){
   if(await this.oauth.limited(request,'pair',8,60000))return fail('rate_limited',429);
   const b=parsed;
   return this.ctx.storage.transaction(async tx=>{
    const pairing=await this.oauth.get('pairing',tx);
    if(!pairing || !sameSecret(pairing.hash,digest(b.code)))return fail('pairing_code_invalid_or_expired',401);
    await tx.delete('pairing');return json({endpoint:this.env.PUBLIC_URL,token:this.env.PHONE_TOKEN});
   });
  }
  if(path==='/admin/status' && method==='GET'){
   if(!sameSecret(request.headers.get('Authorization'),'Bearer '+this.env.ADMIN_PASSWORD))return fail('unauthorized',401);
   return json(this.connection());
  }
  const oauth=await this.oauth.route(request,parsed);if(oauth)return oauth;
  if(path.startsWith('/uploads/')){
   const grant=await this.oauth.authenticate(request);if(!grant)return fail('invalid_token',401);
   if(!grant.scopes.includes('phone.write'))return fail('insufficient_scope',403);
   if(method!=='PUT')return json({error:'method_not_allowed'},405,{Allow:'PUT'});
   if(request.headers.get('Content-Type')?.split(';')[0]!=='application/octet-stream')return fail('binary_body_required',415);
   if(await this.oauth.limited(request,'upload',600,60000))return fail('rate_limited',429);
   try{const selected=uploadSelection(url),bytes=await readUploadBody(request.body,request.headers.get('Content-Length'));return json(await forwardUpload((op,args)=>this.command(op,args,request.signal),grant,selected,bytes));}
   catch(error){const f=uploadFailure(error);return json(f.body,f.status);}
  }
  if(path==='/mcp'){
   const grant=await this.oauth.authenticate(request);
   if(!grant)return json({error:'invalid_token'},401,{'WWW-Authenticate':`Bearer resource_metadata="${this.env.PUBLIC_URL}/.well-known/oauth-protected-resource/mcp", scope="phone.read phone.write"`});
   if(method!=='POST')return json({error:'method_not_allowed'},405,{Allow:'POST'});
   if(await this.oauth.limited(request,'mcp',600,60000))return fail('rate_limited',429);
   const parsedBody=parsed;
   const mcp=makeMcp(grant,(op,args)=>this.command(op,args,request.signal),()=>this.connection(),VERSION,this.scheduler);
   const transport=new WebStandardStreamableHTTPServerTransport({sessionIdGenerator:undefined,enableJsonResponse:true});
   await mcp.connect(transport);
   try{return await transport.handleRequest(request,{parsedBody});}finally{await transport.close();await mcp.close();}
  }
  return fail('not_found',404);
 }
 async alarm(){
  let startAfter;
  try {
   while(true){
    const entries=await this.ctx.storage.list({limit:512,...(startAfter?{startAfter}:{})});if(!entries.size)break;
    const expired=[];for(const [key,value] of entries){startAfter=key;if(value?.expires && value.expires<Date.now())expired.push(key);}
    if(expired.length)await this.ctx.storage.delete(expired);
    if(entries.size<512)break;
   }
  }finally{await this.ctx.storage.setAlarm(Date.now()+86400000);}
 }
 webSocketMessage(ws,message){
  if(typeof message!=='string'||message.length>2*1024*1024){ws.close(1009,'Message too large');return;}
  try{
   this.channel.receive(ws,JSON.parse(message));
  }catch{ws.close(1008,'Invalid response');}
 }
 webSocketClose(ws,code,reason){if(!ws.deserializeAttachment()?.superseded)this.scheduler.disconnect();this.channel.disconnect(ws);try{ws.close(code===1005||code===1006?1000:code,reason);}catch{}}
 webSocketError(ws){if(!ws.deserializeAttachment()?.superseded)this.scheduler.disconnect();this.channel.disconnect(ws);try{ws.close(1011,'Connection error');}catch{}}
}



