import 'dotenv/config';
import { createBridge } from './bridge.js';
import { resolve } from 'node:path';

try {
  const bridge = createBridge({publicUrl:process.env.PUBLIC_URL,phoneToken:process.env.PHONE_TOKEN,adminPassword:process.env.ADMIN_PASSWORD,
    stateFile:resolve('runtime/oauth.private.json'),trustProxy:process.env.TRUST_PROXY === 'true'});
  const port = Number(process.env.PORT ?? 8787), host = process.env.BIND_ADDRESS ?? '127.0.0.1';
  bridge.server.listen(port,host,() => console.log(`Phone MCP écoute sur ${host}:${port}. Publiez via votre proxy HTTPS.`));
  for (const signal of ['SIGINT','SIGTERM']) process.on(signal,async () => { await bridge.close(); process.exit(0); });
} catch (error) { console.error(error.message); process.exit(1); }
