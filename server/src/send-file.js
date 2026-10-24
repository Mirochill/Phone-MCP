import {open,stat,readFile} from 'node:fs/promises';
import {createReadStream,readFileSync,writeFileSync,existsSync} from 'node:fs';
import {createHash,randomUUID} from 'node:crypto';
import {basename,dirname,resolve} from 'node:path';
import {fileURLToPath,pathToFileURL} from 'node:url';
import {Client} from '@modelcontextprotocol/sdk/client/index.js';
import {StreamableHTTPClientTransport} from '@modelcontextprotocol/sdk/client/streamableHttp.js';
import {parse} from 'dotenv';
const pause=ms=>new Promise(r=>setTimeout(r,ms));
export async function sendLocalFile(file,{endpoint,accessToken,requestId=randomUUID(),onProgress=()=>{},fetchImpl=fetch}={}){
 const url=new URL(endpoint);if(url.protocol!=='https:'&&!(process.env.PHONE_MCP_ALLOW_LOCAL_HTTP==='true'&&url.protocol==='http:'&&['127.0.0.1','localhost'].includes(url.hostname)))throw Error('Use the paired HTTPS server endpoint.');
 if(url.username||url.password||url.search||url.hash||url.pathname!=='/')throw Error('Use the paired server origin without credentials or a path.');
 if(!accessToken)throw Error('An existing phone.write OAuth access token is required.');
 const info=await stat(file);if(!info.isFile()||info.size<1||info.size>536870912)throw Error('Select a regular nonempty file, up to 512 MiB.');
 const hash=createHash('sha256');for await(const bytes of createReadStream(file))hash.update(bytes);const sha256=hash.digest('hex');
 const client=new Client({name:'phone-mcp-local-file-sender',version:'0.11.0'});let handle;
 const call=async(op,args)=>{const response=await client.callTool({name:'phone_action',arguments:{operation:op,arguments:args}});if(response.isError)throw Error(response.content.find(x=>x.type==='text')?.text??'Phone refused the transfer.');return JSON.parse(response.content.find(x=>x.type==='text').text);};
 try{
  await client.connect(new StreamableHTTPClientTransport(new URL('/mcp',url),{requestInit:{headers:{Authorization:'Bearer '+accessToken}}}));
  let job=await call('file_upload_begin',{requestId,fileName:basename(file),sizeBytes:info.size,sha256});onProgress({jobId:requestId,state:job.state,bytesDone:job.bytesDone??0,totalBytes:info.size});
  if(['failed','cancelled','interrupted'].includes(job.state))throw Error('This request has ended. Inspect it and use a new requestId for an intentional new transfer.');
  if(job.state==='receiving'){
   handle=await open(file,'r');let offset=job.bytesDone??0,retries=0;
   while(offset<info.size){
    const amount=Math.min(job.maxRequestBytes??1048576,1048576,info.size-offset),bytes=Buffer.allocUnsafe(amount);let done=0;
    while(done<amount){const result=await handle.read(bytes,done,amount-done,offset+done);if(!result.bytesRead)throw Error('Source file was truncated during transfer.');done+=result.bytesRead;}
    try{
     const target=new URL(job.uploadPath,url);if(target.origin!==url.origin||!/^\/uploads\/[0-9a-f-]{36}$/.test(target.pathname))throw Error('Invalid upload route returned by the phone.');target.searchParams.set('offset',String(offset));
     const response=await fetchImpl(target,{method:'PUT',headers:{Authorization:'Bearer '+accessToken,'Content-Type':'application/octet-stream'},body:bytes,signal:AbortSignal.timeout(60000)});
     if(!response.ok){const e=await response.json().catch(()=>({}));throw Error(e.message??('Upload HTTP '+response.status));}
     job=await response.json();if(!Number.isSafeInteger(job.bytesDone)||job.bytesDone<offset+amount||job.bytesDone>info.size)throw Error('Invalid byte acknowledgement.');offset=job.bytesDone;retries=0;onProgress({jobId:requestId,state:job.state,bytesDone:offset,totalBytes:info.size});
    }catch(error){
     // Inspect first: a lost HTTP response can follow a successful write on Android.
     if(++retries>3)throw error;await pause(400*retries);job=await call('file_job',{jobId:requestId});
     if(job.state!=='receiving'||!Number.isSafeInteger(job.bytesDone)||job.bytesDone>info.size)throw error;offset=job.bytesDone;
    }
   }
   await handle.close();handle=null;
   const unchanged=await stat(file);if(unchanged.size!==info.size||unchanged.mtimeMs!==info.mtimeMs)throw Error('The local source changed. The phone has not been asked to publish it.');
   job=await call('file_upload_commit',{jobId:requestId});
  }
  const deadline=Date.now()+120000;while(!['completed','failed','cancelled','interrupted'].includes(job.state)){if(Date.now()>deadline)throw Error('Verification is still running. Inspect file_job before retrying.');await pause(350);job=await call('file_job',{jobId:requestId});}
  if(job.state!=='completed'||job.sha256!==sha256||job.bytesDone!==info.size)throw Error(job.error??'The received file was not verified.');return job;
 }finally{await handle?.close();await client.close();}
}
async function main(){
 const args=process.argv.slice(2),file=args.shift();if(!file||file==='--help'){console.log('node src/send-file.js FILE [--server PAIRED_HTTPS_ORIGIN] [--auth-file PRIVATE_OAUTH_JSON] [--request-id UUID]\nServer/token can also come from PHONE_MCP_SERVER and PHONE_MCP_ACCESS_TOKEN. No file URL or public hosting.');return;}
 const options={};while(args.length){const key=args.shift(),value=args.shift();if(!['--server','--auth-file','--request-id'].includes(key)||!value)throw Error('Invalid command option.');options[key]=value;}
 const root=resolve(dirname(fileURLToPath(import.meta.url)),'..');let configured={};for(const name of ['.env','.env.tunnel']){const path=resolve(root,name);if(existsSync(path)){configured=parse(readFileSync(path));break;}}
 let endpoint=options['--server']??process.env.PHONE_MCP_SERVER??configured.PHONE_MCP_SERVER;
 const paired=resolve(root,'../cloudflare/wrangler.jsonc');if(!endpoint&&existsSync(paired)){try{endpoint=JSON.parse(readFileSync(paired,'utf8').replace(/^\uFEFF/,'')).vars?.PUBLIC_URL;}catch{}}
 endpoint??=configured.PUBLIC_URL;
 const authPath=options['--auth-file']??resolve(root,'runtime/upload-auth.private.json');
 let auth=existsSync(authPath)?JSON.parse((await readFile(authPath,'utf8')).replace(/^\uFEFF/,'')):null;
 if(auth?.serverOrigin&&auth.serverOrigin!==new URL(endpoint).origin)throw Error('This authorization belongs to another server. It was not sent.');
 if(auth?.refresh_token&&auth.createdAt+(auth.expires_in??3600)*1000<Date.now()+60000){
  const response=await fetch(new URL('/token',endpoint),{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({grant_type:'refresh_token',client_id:auth.client_id,refresh_token:auth.refresh_token})});
  if(!response.ok)throw Error('The local sender authorization must be renewed. No file was sent.');
  auth={...await response.json(),client_id:auth.client_id,createdAt:Date.now(),serverOrigin:new URL(endpoint).origin};writeFileSync(authPath,JSON.stringify(auth),{mode:0o600});
 }
 const accessToken=auth?.access_token??process.env.PHONE_MCP_ACCESS_TOKEN??configured.PHONE_MCP_ACCESS_TOKEN;
 if(!endpoint)throw Error('The paired server is missing. Set PHONE_MCP_SERVER or --server once.');const started=performance.now();
 const job=await sendLocalFile(resolve(file),{endpoint,accessToken,...(options['--request-id']?{requestId:options['--request-id']}:{}),onProgress:p=>process.stderr.write(JSON.stringify(p)+'\n')});
 console.log(JSON.stringify({jobId:job.jobId,fileName:job.fileName,state:job.state,bytes:job.bytesDone,sha256:job.sha256,folder:job.folder,elapsedMs:Math.round(performance.now()-started)}));
}
if(process.argv[1]&&import.meta.url===pathToFileURL(resolve(process.argv[1])).href)main().catch(e=>{console.error(e.message);process.exitCode=1;});
