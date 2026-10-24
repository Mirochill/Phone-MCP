import test from 'node:test';
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {Buffer} from 'node:buffer';
import {uploadSelection,readUploadBody,forwardUpload,MAX_UPLOAD_BODY} from '../src/direct-upload.js';
import {Client} from '@modelcontextprotocol/sdk/client/index.js';
import {InMemoryTransport} from '@modelcontextprotocol/sdk/inMemory.js';
import {makeMcp} from '../src/mcp.js';
const jobId=randomUUID();
test('chat file input follows the documented descriptor',async t=>{
 const seen=[];
 const server=makeMcp({scopes:['phone.read','phone.write'],family:'owner'},async(op,args)=>{seen.push({op,args});return {jobId:args.requestId,state:'queued',fileName:args.fileName};},()=>({connected:true}));
 const client=new Client({name:'file-binding-test',version:'1'});
 const [a,b]=InMemoryTransport.createLinkedPair();
 await server.connect(b);await client.connect(a);
 t.after(()=>client.close());t.after(()=>server.close());
 const tool=(await client.listTools()).tools.find(t=>t.name==='phone_send_file');
 assert.deepEqual(tool._meta['openai/fileParams'],['file']);
 const schema=tool.inputSchema.properties.file;
 assert.deepEqual(schema.required,['download_url','file_id']);
 assert.deepEqual(Object.keys(schema.properties).sort(),['download_url','file_id','file_name','mime_type']);
 const result=await client.callTool({name:'phone_send_file',arguments:{requestId:jobId,file:{file_id:'file-example',download_url:'https://example.com/video',file_name:'Ma vidéo.mp4',mime_type:'video/mp4'}}});
 assert.notEqual(result.isError,true);assert.equal(seen.length,1);assert.equal(seen[0].op,'download_file');assert.equal(seen[0].args.fileName,'Ma vidéo.mp4');assert.ok(seen[0].args._ownerKey);
});
test('read-only scope refuses file send',async t=>{
 let count=0;
 const server=makeMcp({scopes:['phone.read']},async()=>{count++;return {};},()=>({connected:true}));
 const client=new Client({name:'no-write',version:'1'});
 const pair=InMemoryTransport.createLinkedPair();
 await server.connect(pair[1]);await client.connect(pair[0]);
 t.after(()=>client.close());t.after(()=>server.close());
 const r=await client.callTool({name:'phone_action',arguments:{operation:'send_file',arguments:{requestId:jobId,file:{file_id:'example',download_url:'https://example.com/a',file_name:'safe.bin'}}}});
 assert.equal(r.isError,true);assert.equal(count,0);
});
test('binary relay forwards ordered bytes with one owner',async()=>{
 const bytes=Buffer.alloc(300000);
 for(let i=0;i<bytes.length;i++)bytes[i]=i%251;
 const got=await readUploadBody(new Response(bytes).body,String(bytes.length));
 assert.deepEqual(got,bytes);
 const records=[];
 const r=await forwardUpload(async(op,args)=>{records.push({op,args});return {jobId,bytesDone:args.offset+Buffer.from(args.data,'base64').length};},{scopes:['phone.write'],family:'same-family'},{jobId,offset:17},got);
 assert.equal(records.length,3);
 assert.deepEqual(Buffer.concat(records.map(r=>Buffer.from(r.args.data,'base64'))),bytes);
 assert.deepEqual(records.map(r=>r.args.offset),[17,17+131072,17+262144]);
 assert.equal(new Set(records.map(r=>r.args._ownerKey)).size,1);
 assert.equal(r.bytesDone,bytes.length+17);
});
test('relay validation rejects bad sizes and offset selections',async()=>{
 await assert.rejects(readUploadBody(new Response(Buffer.alloc(MAX_UPLOAD_BODY+1)).body,null),/too_large/);
 await assert.rejects(readUploadBody(new Response(new Uint8Array()).body,'0'),/too_large/);
 await assert.rejects(readUploadBody(new Response(Buffer.alloc(5)).body,'6'),/incomplete/);
 for(const offset of ['-1','536870913','1.5']){
  const url=new URL('https://example.com/uploads/'+jobId);url.searchParams.set('offset',offset);
  assert.throws(()=>uploadSelection(url));
 }
 const duplicate=new URL('https://example.com/uploads/'+jobId);duplicate.searchParams.append('offset','0');duplicate.searchParams.append('offset','1');assert.throws(()=>uploadSelection(duplicate));
 let calls=0;
 await assert.rejects(forwardUpload(async()=>{calls++;return {};},{scopes:['phone.read']},{jobId,offset:0},Buffer.from('x')),/scope/);
 assert.equal(calls,0);
});
