import path from 'node:path';
import fs from 'node:fs';
fs.mkdirSync(path.resolve(import.meta.dirname,'../android/build'),{recursive:true});
import {spawnSync} from 'node:child_process';
for(const name of ['Test-PreviewFrames.mjs','Test-PreviewRouting.mjs','Test-ScreenChoice.mjs','Test-CaptureMirror.mjs','Test-PendingMedia.mjs']){
 const result=spawnSync(process.execPath,[path.join(import.meta.dirname,name)],{stdio:'inherit',windowsHide:true});
 if(result.status!==0)process.exit(result.status??1);
}
console.log('ANDROID_DOUBLES_VERIFIED');
