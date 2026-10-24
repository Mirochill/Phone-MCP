import {Buffer} from 'node:buffer';
import {createHash} from 'node:crypto';
export const MAX_UPLOAD_BODY=1024*1024,UPLOAD_CHUNK_BYTES=128*1024;
export const uploadOwner=grant=>createHash('sha256').update(grant.family??grant.clientId??'test-legacy').digest('hex');
const uuid=/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
export function uploadSelection(url){
 const match=/^\/uploads\/([^/]+)$/.exec(url.pathname),raw=url.searchParams.get('offset');
 if(!match||!uuid.test(match[1])||!/^\d{1,9}$/.test(raw??'')||[...url.searchParams.keys()].some(k=>k!=='offset')||url.searchParams.getAll('offset').length!==1)throw Error('invalid_upload_target');
 const offset=Number(raw);if(offset>536870912)throw Error('invalid_upload_offset');return {jobId:match[1],offset};
}
export async function readUploadBody(stream,length){
 if(length!==null&&length!==undefined&&(!/^\d+$/.test(String(length))||Number(length)<1||Number(length)>MAX_UPLOAD_BODY))throw Error('upload_body_too_large');
 let size=0;const chunks=[];
 if(!stream)throw Error('empty_upload_body');
 for await(const chunk of stream){const bytes=Buffer.from(chunk);size+=bytes.length;if(size>MAX_UPLOAD_BODY)throw Error('upload_body_too_large');chunks.push(bytes);}
 if(size===0)throw Error('empty_upload_body');if(length!==null&&length!==undefined&&size!==Number(length))throw Error('incomplete_upload_body');return Buffer.concat(chunks,size);
}
export async function forwardUpload(command,grant,selection,bytes){
 if(!grant.scopes?.includes('phone.write'))throw Error('insufficient_scope');
 const _ownerKey=uploadOwner(grant);let result;
 for(let start=0;start<bytes.length;start+=UPLOAD_CHUNK_BYTES){
  const data=bytes.subarray(start,start+UPLOAD_CHUNK_BYTES);
  result=await command('file_upload_chunk',{jobId:selection.jobId,offset:selection.offset+start,data:data.toString('base64'),_ownerKey});
  if(!Number.isSafeInteger(result.bytesDone)||result.bytesDone<selection.offset+start+data.length)throw Error('invalid_upload_acknowledgement');
 }
 return {...result,acceptedBytes:bytes.length};
}
export function uploadFailure(error){const message=String(error?.message??'Upload failed').replace(/https?:\/\/\S+/g,'[link]').slice(0,500);const status=/scope/.test(message)?403:/too_large/.test(message)?413:/invalid_upload|empty_upload|incomplete_upload/.test(message)?400:502;return {status,body:{error:'upload_failed',message}};}
