import fs from 'node:fs';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
const root=path.resolve(import.meta.dirname,'..');
const output=path.join(root,'work','policy-classes');fs.mkdirSync(output,{recursive:true});
const names=['AccessPolicy','FilePolicy','SessionPolicy','WindowPolicy','ParallelTaskLease'];
const files=names.flatMap(n=>[path.join(root,'android/app/src/main/java/fr/phonemcp',n+'.java'),path.join(root,'android/policy-test',n+'Test.java')]);
let r=spawnSync('javac',['-encoding','UTF-8','-d',output,...files],{stdio:'inherit'});if(r.status!==0)process.exit(r.status??1);
for(const name of names){r=spawnSync('java',['-cp',output,'fr.phonemcp.'+name+'Test'],{stdio:'inherit'});if(r.status!==0)process.exit(r.status??1);}
console.log('ANDROID_POLICY_VERIFIED');
