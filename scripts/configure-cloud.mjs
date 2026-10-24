import fs from 'node:fs';
import path from 'node:path';
import {randomBytes} from 'node:crypto';
import {spawn} from 'node:child_process';
const root=path.resolve(import.meta.dirname,'..'),configPath=path.join(root,'cloudflare/wrangler.jsonc');
const secretFile=path.join(root,'work/cloud-secrets.private.json');
const [command,value,name='phone-mcp']=process.argv.slice(2);
if(command==='prepare'){
  const url=new URL(value);
  if(url.protocol!=='https:'||url.pathname!=='/'||url.username||url.password||url.search||url.hash||/your-|example\./i.test(url.hostname))throw Error('Provide your real HTTPS origin without /mcp');
  if(!/^[a-z0-9][a-z0-9-]{1,61}[a-z0-9]$/.test(name))throw Error('Invalid Worker name');
  const config=JSON.parse(fs.readFileSync(fs.existsSync(configPath)?configPath:path.join(root,'cloudflare/wrangler.example.jsonc'),'utf8'));config.name=name;config.vars.PUBLIC_URL=url.origin;
  fs.writeFileSync(configPath,JSON.stringify(config,null,2)+'\n');
  fs.mkdirSync(path.dirname(secretFile),{recursive:true});
  if(!fs.existsSync(secretFile))fs.writeFileSync(secretFile,JSON.stringify({PHONE_TOKEN:randomBytes(32).toString('base64url'),ADMIN_PASSWORD:randomBytes(32).toString('base64url')},null,2),{flag:'wx',mode:0o600});
  console.log('Cloud config prepared. Separate random secrets are in ignored work/cloud-secrets.private.json. Existing secrets were preserved. Keep this file private.');
}else if(command==='upload-secrets'){
  const secrets=JSON.parse(fs.readFileSync(secretFile,'utf8'));
  if(!/^[A-Za-z0-9_-]{32,256}$/.test(secrets.PHONE_TOKEN??'')||String(secrets.ADMIN_PASSWORD??'').length<24)throw Error('Invalid private secrets');
  const child=spawn(process.execPath,[path.join(root,'cloudflare/node_modules/wrangler/bin/wrangler.js'),'secret','bulk'],{cwd:path.join(root,'cloudflare'),stdio:['pipe','inherit','inherit'],windowsHide:true});
  child.stdin.end(JSON.stringify(secrets));
  const code=await new Promise(resolve=>child.on('exit',resolve));if(code!==0)process.exit(code??1);
}else if(command==='pair'){
  const secrets=JSON.parse(fs.readFileSync(secretFile,'utf8'));
  const child=spawn(process.execPath,[path.join(root,'cloudflare/pair.mjs')],{cwd:path.join(root,'cloudflare'),stdio:['pipe','inherit','inherit'],windowsHide:true});
  child.stdin.end(JSON.stringify({ADMIN_PASSWORD:secrets.ADMIN_PASSWORD}));
  const code=await new Promise(resolve=>child.on('exit',resolve));if(code!==0)process.exit(code??1);
}else throw Error('Usage: node scripts/configure-cloud.mjs prepare https://YOUR-WORKER.YOUR-SUBDOMAIN.workers.dev [worker-name] | upload-secrets | pair');
