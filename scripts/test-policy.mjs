import fs from 'node:fs';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
const root=path.resolve(import.meta.dirname,'..');
fs.mkdirSync(path.join(root,'work'),{recursive:true});
const output=fs.mkdtempSync(path.join(root,'work/policy-'));
const main=path.join(root,'android/app/src/main/java'),tests=path.join(root,'android/policy-test');
const names=fs.readdirSync(tests).filter(n=>n.endsWith('Test.java')).sort();
let result=spawnSync('javac',['-encoding','UTF-8','-d',output,'-sourcepath',[main,tests].join(path.delimiter),...names.map(n=>path.join(tests,n))],{stdio:'inherit',windowsHide:true});
if(result.status!==0)process.exit(result.status??1);
for(const name of names){const args=['-cp',output,'fr.phonemcp.'+name.slice(0,-5)];if(name==='UploadReceiverTest.java')args.push(path.join(output,'receiver-case'));result=spawnSync('java',args,{stdio:'inherit',windowsHide:true});if(result.status!==0)process.exit(result.status??1);}
console.log(`ANDROID_POLICY_VERIFIED: ${names.length} policy families`);
