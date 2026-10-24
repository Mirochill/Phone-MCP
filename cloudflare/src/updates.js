import {sameSecret,randomToken,digest} from '../../server/src/config.js';
import {json,fail} from './oauth.js';

/** All release assets stay behind the Worker. Short-lived tickets grant access to one APK hash. */
export async function updateRoute(request,env,storage){
  const url=new URL(request.url);
  if(!url.pathname.startsWith('/updates/'))return null;
  if(request.method!=='GET')return fail('method_not_allowed',405);
  if(url.pathname==='/updates/latest'){
    if(!sameSecret(request.headers.get('Authorization'),'Bearer '+env.PHONE_TOKEN))return fail('unauthorized',401);
    if(!env.ASSETS)return fail('release_unavailable',503);
    const response=await env.ASSETS.fetch(new Request(url.origin+'/release.json'));
    if(!response.ok)return fail('release_unavailable',503);
    const release=await response.json();
    if(release.packageName!=='fr.phonemcp'||!Number.isInteger(release.versionCode)||release.versionCode<1||!/^\d+\.\d+\.\d+$/.test(release.versionName)||!/^[a-f0-9]{64}$/.test(release.sha256)||release.sizeBytes<1||release.sizeBytes>536870912)return fail('release_invalid',503);
    const ticket=randomToken(),expires=Date.now()+900000;
    await storage.put('update-ticket:'+digest(ticket),{expires,sha256:release.sha256});
    return json({...release,url:url.origin+'/updates/download/'+ticket,expiresAt:expires},200,{'Cache-Control':'no-store'});
  }
  if(url.pathname.startsWith('/updates/download/')){
    const ticket=url.pathname.slice('/updates/download/'.length);if(!/^[A-Za-z0-9_-]{32,256}$/.test(ticket))return fail('invalid_ticket',403);
    const grant=await storage.get('update-ticket:'+digest(ticket));
    if(!grant||grant.expires<Date.now()||!env.ASSETS)return fail('expired_ticket',403);
    const manifest=await env.ASSETS.fetch(new Request(url.origin+'/release.json'));
    if(!manifest.ok||(await manifest.json()).sha256!==grant.sha256)return fail('release_changed',409);
    const asset=await env.ASSETS.fetch(new Request(url.origin+'/phone-mcp.apk'));
    if(!asset.ok)return fail('release_unavailable',503);
    const headers=new Headers(asset.headers);headers.set('Content-Type','application/vnd.android.package-archive');headers.set('Content-Disposition','attachment; filename="Phone-MCP.apk"');headers.set('Cache-Control','private, no-store');headers.set('X-Content-Type-Options','nosniff');
    return new Response(asset.body,{status:200,headers});
  }
  return fail('not_found',404);
}
