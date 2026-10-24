import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
import {Client} from '../server/node_modules/@modelcontextprotocol/sdk/dist/esm/client/index.js';
import {InMemoryTransport} from '../server/node_modules/@modelcontextprotocol/sdk/dist/esm/inMemory.js';
import {makeMcp} from '../server/src/mcp.js';
import {VERSION,API_VERSION} from '../server/src/version.js';
const root=path.resolve(import.meta.dirname,'..');
const command=async()=>{throw Error('Documentation generation must never execute a phone command');};
const server=makeMcp({scopes:['phone.read','phone.write'],family:'public-doc-generation'},command,()=>({connected:false,connectedSince:null,commandInProgress:false}),VERSION);
const client=new Client({name:'public-tool-documentation',version:'1'});
const [a,b]=InMemoryTransport.createLinkedPair();
try{
 await Promise.all([server.connect(a),client.connect(b)]);
 const {tools}=await client.listTools();
 const result=await client.callTool({name:'phone_catalog',arguments:{}});
 const catalog=JSON.parse(result.content.find(c=>c.type==='text').text);
 assert.equal(tools.length,59);assert.equal(catalog.operations.length,55);
 assert.equal(new Set(tools.map(t=>t.name)).size,59);
 const schemas={version:VERSION,apiVersion:API_VERSION,catalogVersion:catalog.catalogVersion,tools};
 const targets=new Map();
 targets.set('docs/tools.json',JSON.stringify(schemas,null,2)+'\n');
 let md='# Complete MCP tool reference\n\nGenerated from the real MCP `tools/list` response by `npm run docs:tools`; do not hand-edit. See [tool workflows](tools.md) for sessions, gestures, ad jobs and file transfers.\n\n';
 md+=`Release **${VERSION}** · **${tools.length} tools** · API **${API_VERSION}** · catalog **${catalog.catalogVersion}**.\n\n`;
 md+='| Tool | Access | Purpose |\n|---|---|---|\n';
 for(const tool of tools)md+=`| [\`${tool.name}\`](#${tool.name.replaceAll('_','-')}) | ${tool.annotations?.readOnlyHint?'Read':'Action'} | ${tool.description.replaceAll('|','\\|').replaceAll('\n',' ')} |\n`;
 for(const tool of tools){md+=`\n## ${tool.name.replaceAll('_','-')}\n\n\`${tool.name}\`\n\n${tool.description}\n\n`;const required=tool.inputSchema.required??[];
  md+='| Parameter | Required | Schema |\n|---|---|---|\n';
  for(const [name,schema]of Object.entries(tool.inputSchema.properties??{}))md+=`| \`${name}\` | ${required.includes(name)?'Yes':'No'} | \`${JSON.stringify(schema).replaceAll('|','\\|').replaceAll('`','')}\` |\n`;
  if(!Object.keys(tool.inputSchema.properties??{}).length)md+='| (none) | — | — |\n';
  if(tool._meta)md+='\nMCP metadata: `'+JSON.stringify(tool._meta)+'`\n';
 }
 targets.set('docs/tools-reference.md',md);
 for(const [file,content]of targets){const dest=path.join(root,file);if(process.argv.includes('--check'))assert.equal(fs.readFileSync(dest,'utf8'),content,`${file} is stale; run npm run docs:tools`);else fs.writeFileSync(dest,content);}
 console.log(`Verified ${tools.length} actual MCP tools, catalog ${catalog.catalogVersion}`);
}finally{await client.close();await server.close();}
