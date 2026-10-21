import express from 'express';
import { createServer } from 'node:http';
import { randomUUID } from 'node:crypto';
import { WebSocketServer, WebSocket } from 'ws';
import { rateLimit } from 'express-rate-limit';
import { StreamableHTTPServerTransport } from '@modelcontextprotocol/sdk/server/streamableHttp.js';
import { validateConfig, sameSecret, randomToken } from './config.js';
import { installOAuth } from './oauth.js';
import { makeMcp } from './mcp.js';


export function createBridge(inputConfig) {
  const config = validateConfig(inputConfig), app = express();
  app.disable('x-powered-by');
  // Enable only when exactly one trusted local reverse proxy is in front of this process.
  if (config.trustProxy === true) app.set('trust proxy','loopback');
  app.use((req,res,next) => {
    res.set('Cache-Control','no-store'); res.set('X-Content-Type-Options','nosniff'); res.set('Referrer-Policy','no-referrer');
    res.set('Content-Security-Policy',"default-src 'none'; form-action 'self'; base-uri 'none'; frame-ancestors 'none'");
    const origin = req.headers.origin;
    const allowed = [config.publicUrl,'https://chatgpt.com','https://chat.openai.com'];
    if (origin && !allowed.includes(origin)) return res.status(403).json({error:'origin_denied'});
    if (origin) {
      res.set('Access-Control-Allow-Origin',origin); res.set('Vary','Origin');
      res.set('Access-Control-Allow-Headers','Authorization, Content-Type, MCP-Protocol-Version, MCP-Session-Id');
      res.set('Access-Control-Allow-Methods','GET, POST, DELETE, OPTIONS');
      res.set('Access-Control-Expose-Headers','WWW-Authenticate, MCP-Session-Id');
    }
    if (req.method === 'OPTIONS') return res.status(204).end(); next();
  });
  app.use(express.json({limit:'32kb'})); app.use(express.urlencoded({extended:false,limit:'12kb'}));
  const oauth = installOAuth(app,config);
  app.get('/health',(_req,res) => res.json({ok:true,version:'0.2.0'}));
  let pairingUsed = false, pairingCode = config.pairingCode;
  let pairingExpires = Date.now() + 15*60000;
  app.post('/admin/pairing',rateLimit({windowMs:60000,limit:8,standardHeaders:'draft-8',legacyHeaders:false}),(req,res) => {
    if (!sameSecret(req.headers.authorization, 'Bearer '+config.adminPassword)) return res.status(401).json({error:'unauthorized'});
    pairingCode = randomToken(); pairingUsed = false; pairingExpires = Date.now()+15*60000;
    res.json({endpoint:config.publicUrl,code:pairingCode,expiresAt:pairingExpires});
  });
  app.get('/pairing/:code',(req,res) => {
    if (!pairingCode || pairingUsed || Date.now() > pairingExpires || !sameSecret(req.params.code,pairingCode))
      return res.status(410).type('text').send('Lien expiré ou déjà utilisé. Régénérez l’appairage sur le PC.');
    const link='phonemcp://pair?'+new URLSearchParams({server:config.publicUrl,code:pairingCode});
    res.type('html').send('<!doctype html><html lang="fr"><meta charset="utf-8"><meta name="viewport" content="width=device-width"><title>Phone MCP</title><h1>Appairer Phone MCP</h1><p>Installez l’APK Phone MCP, puis ouvrez le lien ci-dessous.</p><p><a href="'+link.replaceAll('&','&amp;')+'">Ouvrir Phone MCP et appairer ce serveur</a></p><p>Aucune session ne démarrera automatiquement.</p></html>');
  });
  app.post('/pair',rateLimit({windowMs:60000,limit:8,standardHeaders:'draft-8',legacyHeaders:false}),(req,res) => {
    if (!pairingCode || pairingUsed || Date.now() > pairingExpires || !sameSecret(req.body?.code,pairingCode))
      return res.status(401).json({error:'pairing_code_invalid_or_expired'});
    pairingUsed = true;
    res.json({endpoint:config.publicUrl,token:config.phoneToken});
  });
  const server = createServer(app);
  const wss = new WebSocketServer({noServer:true,maxPayload:2*1024*1024,perMessageDeflate:false});
  let phone = null, phoneAlive = false, inFlight = null, connectedSince = null;
  server.on('upgrade',(req,socket,head) => {
    const authorization = req.headers.authorization;
    if (req.url !== '/device' || req.headers.origin || !sameSecret(authorization,`Bearer ${config.phoneToken}`)) {
      socket.end('HTTP/1.1 401 Unauthorized\r\nConnection: close\r\n\r\n'); return;
    }
    if (phone) { socket.end('HTTP/1.1 409 Conflict\r\nConnection: close\r\n\r\n'); return; }
    wss.handleUpgrade(req,socket,head,ws => { phone = ws; wss.emit('connection',ws); });
  });
  wss.on('connection',ws => {
    connectedSince = new Date().toISOString();
    phoneAlive = true; ws.on('pong',() => { phoneAlive = true; });
    ws.on('error',() => {});
    ws.on('message',data => {
      try {
        const response = JSON.parse(data.toString());
        if (!inFlight || response.id !== inFlight.id) return;
        const waiting = inFlight; inFlight = null; clearTimeout(waiting.timer);
        if (response.ok === true && response.result && typeof response.result === 'object') waiting.resolve(response.result);
        else waiting.reject(new Error(typeof response.error === 'string' ? response.error.slice(0,300) : 'Le téléphone a refusé la commande.'));
      } catch { ws.close(1008,'Invalid response'); }
    });
    ws.on('close',() => {
      if (phone === ws) { phone = null; if (inFlight) { clearTimeout(inFlight.timer); inFlight.reject(new Error('Téléphone déconnecté.')); inFlight = null; } }
    });
  });
  const heartbeat = setInterval(() => {
    if (!phone) return;
    if (!phoneAlive) { phone.terminate(); return; }
    phoneAlive = false; phone.ping();
  },25000).unref();
  function command(op,args) {
    if (!phone || phone.readyState !== WebSocket.OPEN) return Promise.reject(new Error('Téléphone hors ligne. Démarrez une session dans Phone MCP.'));
    if (inFlight) return Promise.reject(new Error('Une commande est déjà en cours. Réessayez après sa réponse.'));
    return new Promise((resolve,reject) => {
      const id = randomUUID();
      const timer = setTimeout(() => {
        if (inFlight?.id === id) inFlight = null;
        // Never retry an action automatically: it may already have happened.
        reject(new Error('Délai dépassé. L’action a peut-être été effectuée : relisez l’écran avant de réessayer.'));
      },config.commandTimeoutMs ?? 12000);
      inFlight = {id,timer,resolve,reject};
      phone.send(JSON.stringify({id,op,args,expiresAt:Date.now()+(config.commandTimeoutMs ?? 12000)}),error => {
        if (error && inFlight?.id === id) { clearTimeout(timer); inFlight = null; reject(new Error('Envoi impossible.')); }
      });
    });
  }
  app.post('/mcp',oauth.authenticate,rateLimit({windowMs:60000,limit:120,standardHeaders:'draft-8',legacyHeaders:false}),async (req,res,next) => {
    const mcp = makeMcp(req.phoneGrant,command,() => ({connected:phone?.readyState === WebSocket.OPEN,connectedSince:phone ? connectedSince : null,commandInProgress:!!inFlight,serverVersion:'0.2.0'}));
    const transport = new StreamableHTTPServerTransport({sessionIdGenerator:undefined,enableJsonResponse:true});
    res.on('close',() => { transport.close().catch(() => {}); mcp.close().catch(() => {}); });
    try { await mcp.connect(transport); await transport.handleRequest(req,res,req.body); }
    catch (error) { if (!res.headersSent) next(error); }
  });
  app.get('/mcp',oauth.authenticate,(_req,res) => res.status(405).set('Allow','POST').end());
  app.delete('/mcp',oauth.authenticate,(_req,res) => res.status(405).set('Allow','POST').end());
  app.use((error,_req,res,_next) => res.status(error.status ?? 500).json({error:'request_failed'}));
  async function close() {
    clearInterval(heartbeat); oauth.close();
    if (inFlight) { clearTimeout(inFlight.timer); inFlight.reject(new Error('Serveur arrêté.')); inFlight = null; }
    for (const ws of wss.clients) ws.terminate();
    await new Promise(resolve => wss.close(resolve));
    if (server.listening) await new Promise(resolve => server.close(resolve));
  }
  return {app,server,close};
}
