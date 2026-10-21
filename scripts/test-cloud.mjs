import fs from 'node:fs';
import path from 'node:path';
import {spawn,spawnSync} from 'node:child_process';
const root=path.resolve(import.meta.dirname,'..');
const work=path.join(root,'work');fs.mkdirSync(work,{recursive:true});
const port=Number(process.env.PHONE_MCP_TEST_PORT??8791);
if(!Number.isInteger(port)||port<1024||port>65535)throw Error('Invalid test port');
const base=`http://127.0.0.1:${port}`;
// Fresh test storage prevents rate-limit state leaking between test runs.
async function runFile(file) {
const state=fs.mkdtempSync(path.join(work,'cloud-tests-'));
const worker=spawn(process.execPath,[path.join(root,'cloudflare/node_modules/wrangler/bin/wrangler.js'),'dev','--local','--ip','127.0.0.1','--port',String(port),'--persist-to',state,'--var',`PUBLIC_URL:${base}`,'--var','ALLOW_LOCAL_HTTP:true','--var','PHONE_TOKEN:test_phone_token_00000000000000000000000','--var','ADMIN_PASSWORD:test-admin-password-000000000000'],{cwd:path.join(root,'cloudflare'),env:{...process.env,WRANGLER_SEND_METRICS:'false'},stdio:['ignore','pipe','pipe'],windowsHide:true});
let log='';for(const s of [worker.stdout,worker.stderr])s.on('data',b=>{log=(log+b).slice(-20000)});
try {
  let ready=false;
  for(let i=0;i<120;i++){
    if(worker.exitCode!==null)throw Error(`Test Worker exited: ${log}`);
    try{const r=await fetch(base+'/health',{signal:AbortSignal.timeout(500)});if(r.ok){ready=true;break}}catch{}
    await new Promise(r=>setTimeout(r,500));
  }
  if(!ready)throw Error(`Test Worker did not start: ${log}`);
  const tests=spawn(process.execPath,['--test',path.join('test',file)],{cwd:path.join(root,'cloudflare'),env:{...process.env,PHONE_MCP_CLOUD_TEST_URL:base},stdio:'inherit',windowsHide:true});
  const code=await new Promise(resolve=>tests.on('exit',resolve));
  if(code!==0)throw Error(`Cloud tests exited ${code}`);
}finally{
  if(worker.exitCode===null){
    if(process.platform==='win32')spawnSync('taskkill',['/PID',String(worker.pid),'/T','/F'],{stdio:'ignore',windowsHide:true});
    else worker.kill('SIGTERM');
  }
}
}
for(const file of fs.readdirSync(path.join(root,'cloudflare/test')).filter(f=>f.endsWith('.test.js')).sort())await runFile(file);
console.log('CLOUDFLARE_RUNTIME_VERIFIED');
