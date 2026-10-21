import {validRedirectUri,matchesRedirectUri,authorizationCsp} from './redirect-uri.js';
import { randomUUID, createHash } from 'node:crypto';
import { rateLimit } from 'express-rate-limit';
import { digest, randomToken, sameSecret } from './config.js';
import { durableOAuthState } from './state.js';

const escape = value => String(value).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const scopesSupported = ['phone.read','phone.write'];
export function installOAuth(app, config) {
  const state = durableOAuthState(config.stateFile, config.publicUrl);
  const {clients, access, refresh, persist} = state;
  const pending = new Map(), codes = new Map();
  const metadata = {
    issuer: config.publicUrl,
    authorization_endpoint: `${config.publicUrl}/authorize`,
    token_endpoint: `${config.publicUrl}/token`,
    registration_endpoint: `${config.publicUrl}/register`,
    revocation_endpoint: `${config.publicUrl}/revoke`,
    response_types_supported: ['code'], grant_types_supported: ['authorization_code','refresh_token'],
    code_challenge_methods_supported: ['S256'], token_endpoint_auth_methods_supported: ['none'],
    scopes_supported: scopesSupported,
  };
  const cleanup = setInterval(() => {
    const now = Date.now();
    for (const map of [pending,codes,access,refresh,clients]) for (const [key,value] of map) if (value.expires < now) map.delete(key);
    persist();
  }, 60000).unref();
  const invalid = (res, error = 'invalid_request', status = 400) => res.status(status).json({error});
  const limited = (limit, windowMs) => rateLimit({windowMs,limit,standardHeaders:'draft-8',legacyHeaders:false});
  app.get('/.well-known/oauth-authorization-server', (_req,res) => res.json(metadata));
  app.get('/.well-known/openid-configuration', (_req,res) => res.json(metadata));
  const protectedResource = (_req,res) => res.json({
    resource: config.resource, authorization_servers:[config.publicUrl], scopes_supported:scopesSupported,
    bearer_methods_supported:['header'], resource_name:'Phone MCP',
  });
  app.get('/.well-known/oauth-protected-resource', protectedResource);
  app.get('/.well-known/oauth-protected-resource/mcp', protectedResource);
  app.post('/register',limited(20,600000), (req,res) => {
    const body = req.body;
    if (!Array.isArray(body?.redirect_uris) || !body.redirect_uris.length || body.redirect_uris.length > 5 || clients.size >= 1000
      || (body.token_endpoint_auth_method && body.token_endpoint_auth_method !== 'none')) return invalid(res,'invalid_client_metadata');
    try {
      for (const value of body.redirect_uris) {
        if (typeof value !== 'string' || value.length > 2048) throw new Error();

        if (!validRedirectUri(value)) throw new Error();
      }
      const id = randomUUID();
      const client = {client_id:id,client_name:String(body.client_name ?? 'Client MCP').slice(0,120),
        redirect_uris:body.redirect_uris,token_endpoint_auth_method:'none',grant_types:['authorization_code','refresh_token'],response_types:['code']};
      clients.set(id,{...client,expires:Date.now()+30*86400000}); persist(); res.status(201).json(client);
    } catch { invalid(res,'invalid_redirect_uri'); }
  });
  app.get('/authorize',limited(60,600000), (req,res) => {
    const q = req.query, client = clients.get(q.client_id);
    if (!client || client.expires < Date.now() || !client.redirect_uris.some(uri => matchesRedirectUri(uri,q.redirect_uri))
      || q.response_type !== 'code' || q.code_challenge_method !== 'S256' || typeof q.code_challenge !== 'string'
      || !/^[A-Za-z0-9_-]{43}$/.test(q.code_challenge) || (q.state && (typeof q.state !== 'string' || q.state.length > 2048))
      || (q.resource && q.resource !== config.resource) || pending.size >= 1000) return invalid(res);
    const scopes = String(q.scope ?? 'phone.read phone.write').split(' ').filter(Boolean);
    if (!scopes.length || scopes.some(s => !scopesSupported.includes(s))) return invalid(res,'invalid_scope');
    const nonce = randomToken();
    pending.set(digest(nonce),{clientId:q.client_id,redirectUri:q.redirect_uri,challenge:q.code_challenge,state:q.state,
      scopes,expires:Date.now()+300000});
    res.set('Content-Security-Policy',authorizationCsp(q.redirect_uri));
    res.type('html').send(`<!doctype html><html lang="fr"><meta charset="utf-8"><meta name="viewport" content="width=device-width"><title>Autoriser Phone MCP</title>
      <body><main><h1>Connecter ${escape(client.client_name)}</h1>
      <p>Autorisez ce client à ${scopes.includes('phone.write') ? 'lire et contrôler' : 'lire'} les apps sélectionnées dans Phone MCP.</p>
      <p>Destination de la connexion : <strong>${escape(new URL(q.redirect_uri).hostname)}</strong><br>${escape(q.redirect_uri)}</p>
      <p>Droits demandés : ${escape(scopes.join(', '))}. Vérifiez la destination avant de saisir le mot de passe.</p>
      <p>Le téléphone doit avoir une session active. Les apps, captures et actions restent limitées par vos choix sur le téléphone.</p>
      <form method="post" action="/approve"><input type="hidden" name="nonce" value="${nonce}">
      <label>Mot de passe du serveur (ADMIN_PASSWORD)<br><input name="password" type="password" required autocomplete="current-password"></label>
      <p><button name="decision" value="allow">Autoriser</button> <button name="decision" value="deny">Refuser</button></p></form></main></body></html>`);
  });
  app.post('/approve',limited(8,900000),(req,res) => {
    const nonce = typeof req.body?.nonce === 'string' ? req.body.nonce : '';
    const request = pending.get(digest(nonce));
    if (!request || request.expires < Date.now()) return invalid(res);
    if (req.body.decision === 'deny') {
      pending.delete(digest(nonce)); const redirect = new URL(request.redirectUri); redirect.searchParams.set('error','access_denied');
      if (request.state) redirect.searchParams.set('state',request.state); return res.redirect(303,redirect.href);
    }
    if (req.body.decision !== 'allow' || !sameSecret(req.body.password,config.adminPassword)) return invalid(res,'access_denied',401);
    pending.delete(digest(nonce)); const code = randomToken();
    codes.set(digest(code),{...request,expires:Date.now()+60000});
    const redirect = new URL(request.redirectUri); redirect.searchParams.set('code',code);
    if (request.state) redirect.searchParams.set('state',request.state); res.redirect(303,redirect.href);
  });
  function issue(grant) {
    const bearer = randomToken(), renewal = randomToken();
    access.set(digest(bearer),{...grant,expires:Date.now()+3600000});
    refresh.set(digest(renewal),{...grant,expires:Date.now()+30*86400000});
    persist();
    return {access_token:bearer,refresh_token:renewal,token_type:'Bearer',expires_in:3600,scope:grant.scopes.join(' ')};
  }
  app.post('/token',limited(60,600000),(req,res) => {
    const body = req.body ?? {}, client = clients.get(body.client_id);
    res.set('Cache-Control','no-store'); res.set('Pragma','no-cache');
    if (!client || client.expires < Date.now()) return invalid(res,'invalid_client');
    if (body.resource && body.resource !== config.resource) return invalid(res,'invalid_target');
    if (body.grant_type === 'authorization_code') {
      const hashed = digest(body.code), grant = codes.get(hashed);
      if (!grant || grant.expires < Date.now() || grant.clientId !== body.client_id || grant.redirectUri !== body.redirect_uri
        || typeof body.code_verifier !== 'string' || !/^[A-Za-z0-9._~-]{43,128}$/.test(body.code_verifier)
        || !sameSecret(createHash('sha256').update(body.code_verifier).digest('base64url'),grant.challenge)) return invalid(res,'invalid_grant');
      codes.delete(hashed); return res.json(issue({clientId:grant.clientId,scopes:grant.scopes,family:randomUUID()}));
    }
    if (body.grant_type === 'refresh_token') {
      const hashed = digest(body.refresh_token), grant = refresh.get(hashed);
      if (!grant || grant.expires < Date.now() || grant.clientId !== body.client_id) return invalid(res,'invalid_grant');
      if (body.scope && body.scope !== grant.scopes.join(' ')) return invalid(res,'invalid_scope');
      refresh.delete(hashed); return res.json(issue(grant));
    }
    return invalid(res,'unsupported_grant_type');
  });
  app.post('/revoke',limited(60,600000),(req,res) => {
    const hashed = digest(req.body?.token), grant = access.get(hashed) ?? refresh.get(hashed);
    if (grant && grant.clientId === req.body?.client_id) {
      for (const map of [access,refresh]) for (const [key,value] of map) if (value.family === grant.family) map.delete(key);
    }
    persist();
    res.status(200).end();
  });
  function authenticate(req,res,next) {
    const header = req.headers.authorization;
    const grant = typeof header === 'string' && /^Bearer [A-Za-z0-9_-]+$/.test(header) ? access.get(digest(header.slice(7))) : null;
    if (!grant || grant.expires < Date.now()) {
      res.set('WWW-Authenticate',`Bearer resource_metadata="${config.publicUrl}/.well-known/oauth-protected-resource/mcp", scope="phone.read phone.write"`);
      return invalid(res,'invalid_token',401);
    }
    req.phoneGrant = grant; next();
  }
  return {authenticate,close:() => { clearInterval(cleanup); persist(); }};
}
