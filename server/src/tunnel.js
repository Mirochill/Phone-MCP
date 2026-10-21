import { spawn } from 'node:child_process';
import { existsSync, readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { resolve } from 'node:path';
import { createInterface } from 'node:readline';
import { parse } from 'dotenv';
import { randomToken } from './config.js';
import { createBridge } from './bridge.js';

const runtime=resolve('runtime'); mkdirSync(runtime,{recursive:true});
const envFile=resolve('.env.tunnel');
const stored=existsSync(envFile)?parse(readFileSync(envFile)):{PHONE_TOKEN:randomToken(),ADMIN_PASSWORD:randomToken()};
if (!existsSync(envFile)) writeFileSync(envFile,`PHONE_TOKEN=${stored.PHONE_TOKEN}\nADMIN_PASSWORD=${stored.ADMIN_PASSWORD}\n`,{mode:0o600,flag:'wx'});
const port=Number(process.env.PORT ?? 8787);
if (!Number.isInteger(port)||port<1024||port>65535) throw new Error('PORT invalide.');
const configFile=resolve(runtime,'cloudflared.yml'); writeFileSync(configFile,'loglevel: info\n');
const bundledBinary=resolve('tools/cloudflared.exe');
const binary=process.env.CLOUDFLARED_BIN ?? (existsSync(bundledBinary)?bundledBinary:'cloudflared');
const child=spawn(binary,['tunnel','--config',configFile,'--url',`http://127.0.0.1:${port}`,'--protocol','http2','--no-autoupdate'],{stdio:['ignore','pipe','pipe'],windowsHide:true});
let bridge,starting=false,shuttingDown=false,readyTimer;
writeFileSync(resolve(runtime,'runner.json'),JSON.stringify({pid:process.pid,tunnelPid:child.pid,serverDirectory:process.cwd(),binary,startedAt:new Date().toISOString()},null,2));
async function shutdown(code=0){
 if(shuttingDown)return;shuttingDown=true;clearTimeout(readyTimer);
 child.kill();if(bridge)await bridge.close();process.exit(code);
}
async function lineReceived(line){
 const match=line.match(/https:\/\/[a-z0-9]+(?:-[a-z0-9]+){2,}\.trycloudflare\.com/);
 if(match&&!starting&&!shuttingDown){
  starting=true;
  try{
   const publicUrl=match[0];
   bridge=createBridge({publicUrl,phoneToken:stored.PHONE_TOKEN,adminPassword:stored.ADMIN_PASSWORD,stateFile:resolve(runtime,'oauth.private.json'),trustProxy:true});
   await new Promise((ok,bad)=>{bridge.server.once('error',bad);bridge.server.listen(port,'127.0.0.1',ok);});
   writeFileSync(envFile,`PUBLIC_URL=${publicUrl}\nPHONE_TOKEN=${stored.PHONE_TOKEN}\nADMIN_PASSWORD=${stored.ADMIN_PASSWORD}\n`,{mode:0o600});
   writeFileSync(resolve(runtime,'endpoint.json'),JSON.stringify({publicUrl,mcpUrl:publicUrl+'/mcp',port,temporary:true},null,2));
   console.log(`MCP ChatGPT : ${publicUrl}/mcp\nServeur local actif sur 127.0.0.1:${port}. Le PC doit rester allumé. L’adresse changera si le tunnel redémarre.`);
  }catch(error){console.error(error.message);await shutdown(1);}
 }
 if(/Registered tunnel connection/.test(line)){clearTimeout(readyTimer);console.log('Tunnel HTTPS connecté.');}
 if(/ERR/.test(line))console.error(line.replace(/token=[^\s]+/g,'token=[redacted]'));
}
for(const stream of [child.stdout,child.stderr])createInterface({input:stream}).on('line',lineReceived);
child.once('error',async error=>{console.error(error.code==='ENOENT'?'cloudflared introuvable. Lancez Demarrer-Phone-MCP.ps1.':error.message);await shutdown(1);});
child.once('exit',()=>shutdown(shuttingDown?0:1));
readyTimer=setTimeout(()=>{console.error('Le tunnel ne se connecte pas.');shutdown(1);},60000);
for(const signal of ['SIGINT','SIGTERM'])process.on(signal,()=>shutdown());
