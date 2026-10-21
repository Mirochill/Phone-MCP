import test from 'node:test';
import assert from 'node:assert/strict';
import {describeControlStatus} from '../src/control-status.js';

test('legacy false foreground flag does not disable ready parallel control',()=>{
  const raw={controlMode:'parallel',screenControlAllowed:false,parallel:{ready:true,displayId:14,physicalPanelOff:true,deviceInteractive:true,keyguardLocked:false},allowedApps:[{packageName:'com.discord',read:true,write:false}]};
  const value=describeControlStatus(raw);
  assert.equal(value.screenControlAllowed,true);assert.equal(value.screenControlReady,true);
  assert.equal(value.foregroundScreenControlAllowed,false);assert.equal(value.parallelScreenControlAllowed,true);
  assert.equal(value.activeControlDisplay,'separate');assert.equal(value.nextControlAction,'use_parallel_tools');
  assert.deepEqual(value.allowedApps,raw.allowedApps);assert.equal(value.allowedApps[0].write,false);
  assert.equal(raw.screenControlAllowed,false);assert.deepEqual(value.parallel,raw.parallel);
});

test('parallel permission and availability remain distinct on lock and service failure',()=>{
  for(const parallel of [{ready:false,displayId:-1,error:'service stopped'},{ready:true,displayId:0},{ready:true,displayId:14,keyguardLocked:true},{ready:true,displayId:14,deviceInteractive:false},{ready:'true',displayId:14},{ready:true,displayId:'14'},{ready:true,displayId:Infinity}]){
    const v=describeControlStatus({controlMode:'parallel',screenControlAllowed:false,parallel});assert.equal(v.screenControlAllowed,true);assert.equal(v.screenControlReady,false);assert.equal(v.foregroundScreenControlAllowed,false);assert.notEqual(v.nextControlAction,'use_parallel_tools');
  }
  assert.equal(describeControlStatus({controlMode:'parallel',parallel:{keyguardLocked:true}}).nextControlAction,'unlock_and_restart_parallel_session');
});

test('explicit revocation and inactive sessions never gain screen access',()=>{
  for(const extra of [{parallelScreenControlAllowed:false},{sessionActive:false}]){
    const v=describeControlStatus({controlMode:'parallel',screenControlAllowed:false,parallel:{ready:true,displayId:14},...extra});assert.equal(v.screenControlAllowed,false);assert.equal(v.screenControlReady,false);
  }
  const v=describeControlStatus({controlMode:'notifications',screenControlAllowed:true,parallel:{ready:true,displayId:14},filesReady:true});assert.equal(v.screenControlAllowed,false);assert.equal(v.activeControlDisplay,'none');assert.equal(v.filesReady,true);
});

test('foreground preserves explicit availability and distinguishes unknown legacy readiness',()=>{
  const base={controlMode:'foreground',screenControlAllowed:true};
  assert.equal(describeControlStatus(base).screenControlReady,null);
  assert.equal(describeControlStatus({...base,screenControlReady:true}).screenControlReady,true);
  assert.equal(describeControlStatus({...base,keyguardLocked:true}).screenControlReady,false);
  assert.equal(describeControlStatus({...base,foregroundScreenControlAllowed:false}).screenControlAllowed,false);
  assert.equal(describeControlStatus({screenControlAllowed:true}).screenControlAllowed,false);
});

test('status payload must be an object',()=>{for(const bad of [null,undefined,[],false,'status'])assert.throws(()=>describeControlStatus(bad));});

test('only verified independent displays stay usable behind the physical keyguard',()=>{
  const raw={controlMode:'parallel',parallel:{ready:true,displayId:17,independentKeyguard:true,keyguardLocked:true,deviceInteractive:false}};
  assert.equal(describeControlStatus(raw).screenControlReady,true);
  assert.equal(describeControlStatus(raw).nextControlAction,'use_parallel_tools');
  for(const extra of [{ready:false},{displayId:0},{independentKeyguard:false},{independentKeyguard:'true'}])assert.equal(describeControlStatus({...raw,parallel:{...raw.parallel,...extra}}).screenControlReady,false);
});
