import { Buffer } from 'node:buffer';
import {validRedirectUri,matchesRedirectUri,authorizationCsp} from '../../server/src/redirect-uri.js';
import { randomUUID } from 'node:crypto';
import { digest,randomToken,sameSecret } from '../../server/src/config.js';
export const json=(value,status=200,headers={})=>Response.json(value,{status,headers:{'Cache-Control':'no-store',...headers}});
export const html=(body,status=200)=>new Response('<!doctype html><html lang="fr"><meta charset="utf-8"><meta name="viewport" content="width=device-width"><title>Phone MCP</title><body><main>'+body+'</main></body></html>',{status,headers:{'Content-Type':'text/html; charset=utf-8','Cache-Control':'no-store'}});
export const escape=value=>String(value).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
export const fail=(error='invalid_request',status=400)=>json({error},status);
export async function body(request){
 const reader=request.body?.getReader();if(!reader)return {};
 let size=0;const chunks=[];
 while(true){const {done,value}=await reader.read();if(done)break;size+=value.byteLength;if(size>32768){await reader.cancel();throw new Error('body_too_large');}chunks.push(value);}
 const text=new TextDecoder().decode(Buffer.concat(chunks));
 return request.headers.get('content-type')?.startsWith('application/x-www-form-urlencoded')?Object.fromEntries(new URLSearchParams(text)):JSON.parse(text||'{}');
}
export class CloudOAuth {
 constructor(storage,env){this.storage=storage;this.env=env;this.origin=env.PUBLIC_URL;this.resource=env.PUBLIC_URL+'/mcp';}
 async get(key,store=this.storage){const value=await store.get(key);return value && value.expires>Date.now()?value:null;}
 async limited(request,group,limit,windowMs){
  const ip=request.headers.get('CF-Connecting-IP')??'local';const bucket=Math.floor(Date.now()/windowMs);
  const key='limit:'+group+':'+bucket+':'+digest(ip);
  return this.storage.transaction(async tx=>{const old=await tx.get(key);const count=(old?.count??0)+1;await tx.put(key,{count,expires:(bucket+2)*windowMs});return count>limit;});
 }
 metadata(){return {issuer:this.origin,authorization_endpoint:this.origin+'/authorize',token_endpoint:this.origin+'/token',registration_endpoint:this.origin+'/register',revocation_endpoint:this.origin+'/revoke',response_types_supported:['code'],grant_types_supported:['authorization_code','refresh_token'],code_challenge_methods_supported:['S256'],token_endpoint_auth_methods_supported:['none'],scopes_supported:['phone.read','phone.write']};}
 async authenticate(request){
  const header=request.headers.get('Authorization');if(!/^Bearer [A-Za-z0-9_-]{32,256}$/.test(header??''))return null;
  const grant=await this.get('access:'+digest(header.slice(7)));if(!grant)return null;
  const family=await this.get('family:'+grant.family);return family && !family.revoked?grant:null;
 }
 async issue(tx,grant){
  const access_token=randomToken(),refresh_token=randomToken(),now=Date.now();
  await tx.put('access:'+digest(access_token),{...grant,expires:now+3600000});
  await tx.put('refresh:'+digest(refresh_token),{...grant,expires:now+30*86400000});
  await tx.put('family:'+grant.family,{revoked:false,expires:now+31*86400000});
  return {access_token,refresh_token,token_type:'Bearer',expires_in:3600,scope:grant.scopes.join(' ')};
 }
 async route(request,parsed){
  const url=new URL(request.url),path=url.pathname,method=request.method;
  if(method==='GET' && path==='/.well-known/oauth-authorization-server')return json(this.metadata());
  if(method==='GET' && ['/.well-known/oauth-protected-resource','/.well-known/oauth-protected-resource/mcp'].includes(path))return json({resource:this.resource,authorization_servers:[this.origin],scopes_supported:['phone.read','phone.write'],bearer_methods_supported:['header'],resource_name:'Phone MCP'});
  if(method==='POST' && path==='/register'){
   if(await this.limited(request,'register',20,600000))return fail('rate_limited',429);
   const b=parsed;if(!Array.isArray(b.redirect_uris)||!b.redirect_uris.length||b.redirect_uris.length>5||(b.token_endpoint_auth_method&&b.token_endpoint_auth_method!=='none'))return fail('invalid_client_metadata');
   try { for(const value of b.redirect_uris){if(!validRedirectUri(value))throw Error();} }catch{return fail('invalid_redirect_uri');}
   if((await this.storage.list({prefix:'client:',limit:201})).size>=200)return fail('registration_capacity',429);
   const client={client_id:randomUUID(),client_name:String(b.client_name??'ChatGPT').slice(0,120),redirect_uris:b.redirect_uris,token_endpoint_auth_method:'none',grant_types:['authorization_code','refresh_token'],response_types:['code']};
   await this.storage.put('client:'+client.client_id,{...client,expires:Date.now()+30*86400000});return json(client,201);
  }
  if(method==='GET' && path==='/authorize'){
   if(await this.limited(request,'authorize',60,600000))return fail('rate_limited',429);
   const q=Object.fromEntries(url.searchParams),client=await this.get('client:'+q.client_id);
   if(!client||!client.redirect_uris.some(uri=>matchesRedirectUri(uri,q.redirect_uri))||q.response_type!=='code'||q.code_challenge_method!=='S256'||!/^[A-Za-z0-9_-]{43}$/.test(q.code_challenge??'')||(q.state&&q.state.length>2048)||(q.resource&&q.resource!==this.resource))return fail();
   const scopes=String(q.scope??'phone.read phone.write').split(' ').filter(Boolean);
   if(!scopes.length||scopes.some(s=>!['phone.read','phone.write'].includes(s)))return fail('invalid_scope');
   const nonce=randomToken();await this.storage.put('pending:'+digest(nonce),{clientId:q.client_id,redirectUri:q.redirect_uri,challenge:q.code_challenge,state:q.state??'',scopes,expires:Date.now()+300000});
   const page=html('<h1>Connecter '+escape(client.client_name)+'</h1><p>Droits : '+escape(scopes.join(', '))+'</p><p>Destination : '+escape(q.redirect_uri)+'</p><p>Les apps et les capacités restent limitées par vos choix sur le téléphone.</p><form method="post" action="/approve"><input type="hidden" name="nonce" value="'+nonce+'"><label>Mot de passe du serveur<br><input type="password" name="password" required autocomplete="current-password"></label><p><button name="decision" value="allow">Autoriser</button> <button name="decision" value="deny" formnovalidate>Refuser</button></p></form>');
   page.headers.set('Content-Security-Policy',authorizationCsp(q.redirect_uri));return page;
  }
  if(method==='POST' && path==='/approve'){
   if(await this.limited(request,'approve',8,900000))return fail('rate_limited',429);
   const b=parsed,key='pending:'+digest(b.nonce);const pending=await this.get(key);if(!pending)return fail();
   if(b.decision!=='deny' && (b.decision!=='allow'||!sameSecret(b.password,this.env.ADMIN_PASSWORD)))return fail('access_denied',401);
   return this.storage.transaction(async tx=>{
    const current=await this.get(key,tx);if(!current)return fail();await tx.delete(key);
    const redirect=new URL(current.redirectUri);if(current.state)redirect.searchParams.set('state',current.state);
    if(b.decision==='deny')redirect.searchParams.set('error','access_denied');
    else {const code=randomToken();await tx.put('code:'+digest(code),{...current,expires:Date.now()+60000});redirect.searchParams.set('code',code);}
    return new Response(null,{status:303,headers:{Location:redirect.href,'Cache-Control':'no-store'}});
   });
  }
  if(method==='POST' && path==='/token'){
   if(await this.limited(request,'token',60,600000))return fail('rate_limited',429);
   const b=parsed,client=await this.get('client:'+b.client_id);if(!client)return fail('invalid_client');
   if(b.resource&&b.resource!==this.resource)return fail('invalid_target');
   if(b.grant_type==='authorization_code')return this.storage.transaction(async tx=>{
    const key='code:'+digest(b.code),grant=await this.get(key,tx);
    if(!grant||grant.clientId!==b.client_id||grant.redirectUri!==b.redirect_uri||typeof b.code_verifier!=='string'||!/^[A-Za-z0-9._~-]{43,128}$/.test(b.code_verifier)||!sameSecret(Buffer.from(await crypto.subtle.digest('SHA-256',new TextEncoder().encode(b.code_verifier))).toString('base64url'),grant.challenge))return fail('invalid_grant');
    await tx.delete(key);return json(await this.issue(tx,{clientId:grant.clientId,scopes:grant.scopes,family:randomUUID()}));
   });
   if(b.grant_type==='refresh_token')return this.storage.transaction(async tx=>{
    const key='refresh:'+digest(b.refresh_token),grant=await this.get(key,tx);if(!grant||grant.clientId!==b.client_id)return fail('invalid_grant');
    const family=await this.get('family:'+grant.family,tx);if(!family||family.revoked)return fail('invalid_grant');
    if(b.scope&&b.scope!==grant.scopes.join(' '))return fail('invalid_scope');
    await tx.delete(key);return json(await this.issue(tx,{clientId:grant.clientId,scopes:grant.scopes,family:grant.family}));
   });
   return fail('unsupported_grant_type');
  }
  if(method==='POST' && path==='/revoke'){
   if(await this.limited(request,'revoke',60,600000))return fail('rate_limited',429);
   const b=parsed,hashed=digest(b.token),grant=await this.get('access:'+hashed)??await this.get('refresh:'+hashed);
   if(grant&&grant.clientId===b.client_id)await this.storage.put('family:'+grant.family,{revoked:true,expires:Date.now()+31*86400000});
   return new Response(null,{status:200});
  }
  return null;
 }
}
