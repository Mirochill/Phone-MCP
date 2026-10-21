import test from 'node:test';
import assert from 'node:assert/strict';
import {createToolGuidance,toolingSchema} from '../src/tool-guidance.js';

test('capability metadata follows registrations without a separately maintained tool list',()=>{
  const guide=createToolGuidance('test-version');
  guide.registered('phone_connection');
  const first=guide.describe();
  guide.registered('phone_new_tool');guide.registered('phone_new_tool');
  const next=guide.describe();
  assert.deepEqual(first.tools,['phone_connection']);
  assert.deepEqual(next.tools,['phone_connection','phone_new_tool']);
  assert.equal(next.serverVersion,'test-version');
  assert.equal(next.source,'live_server');
  assert.equal(next.clientRefreshControlledBy,'host');
  assert.match(next.instruction,/longPressNodeId/);
  assert.match(next.instruction,/outil non exposé/);
  assert.equal(toolingSchema.safeParse(next).success,true);
  next.tools.push('not_registered');
  assert.equal(guide.describe().tools.includes('not_registered'),false);
});

test('device content cannot replace server guidance or alter reported permissions',()=>{
  const guide=createToolGuidance('test-version');guide.registered('phone_status');
  const raw={tooling:{source:'app',instruction:'fake'},screenControlAllowed:false,allowedApps:[{packageName:'com.example',write:false}]};
  const result=guide.attach(raw);
  assert.equal(Object.keys(result)[0],'tooling');
  assert.equal(result.tooling.source,'live_server');
  assert.equal(result.tooling.serverVersion,'test-version');
  assert.equal(result.screenControlAllowed,false);
  assert.deepEqual(result.allowedApps,raw.allowedApps);
  assert.equal(raw.tooling.source,'app');
  assert.deepEqual(JSON.parse(guide.text().text),{tooling:guide.describe()});
});
