import {z} from 'zod';

const pkg=z.string().min(3).max(240).regex(/^(?:android|[A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)+)$/);
const id=z.string().uuid();
const session={sessionId:id.optional()};
const action={packageName:pkg,snapshotId:id,...session};
const node={...action,nodeId:z.string().min(1).max(260).regex(/^0(?:\.\d+)*$/)};
const point=z.number().int().min(0).max(16383);
const text=z.string().max(4000);
// Leave room for the command envelope in Android's 20k-character and HTTP's 32 KiB limits.
const fileText=z.string().max(16000).refine(value=>{
  const encoded=JSON.stringify(value);
  return encoded.length<=16000&&new TextEncoder().encode(encoded).length<=24000;
},'File text must fit 16,000 JSON characters and 24,000 UTF-8 bytes.');
const https=z.string().max(4096).url().refine(value=>{try{const u=new URL(value);return u.protocol==='https:'&&!u.username&&!u.password&&!u.hash;}catch{return false;}},'A direct HTTPS URL is required.');
const hash=z.string().regex(/^[a-fA-F0-9]{64}$/);
const path=z.string().max(500).refine(v=>!v.startsWith('/')&&!v.includes('\\')&&!v.split('/').some(p=>p==='.'||p==='..')&&!/[\x00-\x1f]/.test(v),'Use a relative path inside an authorized folder.');
const screenshot={maxDimension:z.number().int().min(320).max(1920).default(1000),quality:z.number().int().min(35).max(95).default(75),annotate:z.boolean().default(false),snapshotId:id.optional()};
const observation={observe:z.boolean().default(true),settleMs:z.number().int().min(0).max(800).optional(),detail:z.enum(['compact','full']).default('compact'),includeScreenshot:z.boolean().default(false),maxDimension:screenshot.maxDimension,quality:screenshot.quality,annotate:screenshot.annotate};
const sequenceStep=z.discriminatedUnion('op',[
  z.object({op:z.literal('tap'),x:point,y:point}).strict(),
  z.object({op:z.literal('swipe'),fromX:point,fromY:point,toX:point,toY:point,durationMs:z.number().int().min(100).max(1000).default(150)}).strict()
]);
const query={packageName:pkg,query:z.string().min(1).max(250),by:z.enum(['text','description','viewId','role','any']).default('any'),match:z.enum(['contains','exact']).default('contains'),includeOffscreen:z.boolean().default(false)};

// One registry is used for native tools, the stable gateways, validation and the live catalog.
export function operationDefinitions(){
  const out=[];
  const add=(name,op,description,args={},write=false,ui=false)=>out.push({name,op,description,write,ui,schema:z.object({...args,...(args.packageName&&(!op.includes('notification'))&&!args.sessionId?session:{}),...(ui?observation:{})}).strict()});
  add('phone_status','status','Session, permissions, displays, Android and installed app version.');
  add('phone_diagnostics','diagnostics','On-demand battery, CPU, memory and Phone MCP wakelock counters. Does not read app contents.');
  add('phone_diagnostic_probe','diagnostic_probe','Run or retrieve a bounded two-sample battery observation without a wakelock. It can outlive a stopped Phone MCP service.',{probeId:id,action:z.enum(['start','result']),seconds:z.number().int().min(30).max(900).default(180),startDelaySeconds:z.number().int().min(0).max(60).default(0)},true);
  add('phone_session_create','session_create','Create a separately routed app session. requestId is an idempotent UUID; keep the returned sessionId. Does not launch the app. The physical-screen policy is selected only in local Phone MCP settings. keepRendering=true preserves visual background work during pauses at a higher idle cost. After a successful user handoff, create a new requestId/session; the former session stays terminated. Explicit local Stop still requires local resumption.',{requestId:id,packageName:pkg,label:z.string().min(1).max(80).optional(),leaseSeconds:z.number().int().min(30).max(900).default(300),keepRendering:z.boolean().default(false)},true);
  add('phone_session_wake','session_wake','Recover only the existing AI display under the screen policy selected locally in Phone MCP settings. This tool cannot enable the physical-screen-on mode or change the local setting. It does not relaunch the app or bypass keyguard. If off-screen recovery fails, report it instead of choosing a lit-screen fallback.',{packageName:pkg,sessionId:id,reason:z.enum(['noninteractive','black_render','app_requires_screen']).default('app_requires_screen')},true);
  add('phone_session_cleanup','session_cleanup','Remove empty abandoned task records for an authorized app. Active and physical tasks are always preserved. Optional taskIds migrate identified legacy AI tasks; no live app is closed.',{packageName:pkg,taskIds:z.array(z.number().int().min(1).max(2147483647)).max(64).optional()},true);
  add('phone_session_list','session_list','List this authorization’s active and ended app sessions, their displays, isolation and lease state.');
  add('phone_session_keepalive','session_keepalive','Extend a legitimate thinking pause for an active session. Does not restart an ended or locally stopped session.',{sessionId:id},true);
  add('phone_session_end','session_end','Finish this app session and release its display and preview. A different app session continues.',{sessionId:id},true);
  add('phone_session_cancel','session_cancel','Interrupt only this app session and reject its queued commands. A locally stopped app requires local resumption.',{sessionId:id},true);
  add('phone_notifications','notifications','Read current notifications for an authorized app without opening it.',{packageName:pkg});
  add('phone_reply_notification','reply_notification','Send the requested reply through a freshly observed notification.',{packageName:pkg,notificationId:id,text:z.string().min(1).max(4000)},true);
  add('phone_snapshot','snapshot','Read an authorized window. Reuse an action observation. A cursor reads more nodes from the same unchanged snapshot.',{packageName:pkg,windowId:z.number().int().nonnegative().optional(),cursor:z.string().max(100).optional(),limit:z.number().int().min(20).max(350).default(200),includeOffscreen:z.boolean().default(false),detail:z.enum(['compact','full']).default('compact'),includeScreenshot:z.boolean().default(false),maxDimension:screenshot.maxDimension,quality:screenshot.quality,annotate:screenshot.annotate});
  add('phone_screenshot','screenshot','Capture the authorized window. For black or incomplete images try captureSource=surface: a fresh frame from the owned non-secure virtual display, without changing app state or physical-screen power. Optional labels map through coordinateSpace.',{packageName:pkg,captureSource:z.enum(['auto','surface']).default('auto'),...screenshot});
  add('phone_open_app','open_app','Open an authorized app on the active control display.',{packageName:pkg},true,true);
  add('phone_open_url','open_url','Open an HTTPS URL in the chosen authorized app on the active display.',{packageName:pkg,url:https},true,true);
  add('phone_click','click','Click an observed node. Legacy longPressNodeId and coordinateActions remain supported.',node,true,true);
  add('phone_long_press','long_press','Long press an observed node, for example to open its context menu.',node,true,true);
  add('phone_set_text','set_text','Replace an observed editable field, excluding protected fields.',{...node,text},true,true);
  add('phone_scroll','scroll','Scroll an observed scrollable node.',{...node,direction:z.enum(['forward','backward'])},true,true);
  add('phone_back','back','Go back within the observed authorized window.',action,true,true);
  add('phone_tap','tap','Tap a freshly observed target. Canvas/game areas require a recent screenshot tied to the same snapshot; unrelated animation does not invalidate a stable target.',{...action,x:point,y:point},true,true);
  add('phone_swipe','swipe','Swipe within the freshly observed window and stable geometry. For canvas/game areas, first capture the same snapshot.',{...action,fromX:point,fromY:point,toX:point,toY:point,durationMs:z.number().int().min(100).max(1000).default(350)},true,true);
  add('phone_action_sequence','action_sequence','Execute up to 8 already observed taps/swipes locally in one bounded command, with one final observation. Prefer one swipe for a drag or two taps for source/destination. Every step keeps the original session, app, window and target guards. Stops at the first failure; inspect executedCount and failedStep, never replay the whole sequence. Does not bypass app timers, protected content or reward conditions.',{...action,steps:z.array(sequenceStep).min(1).max(8),intervalMs:z.number().int().min(0).max(250).default(0),timeoutMs:z.number().int().min(500).max(8000).default(4000)},true,true);
  add('phone_ad_start','ad_start','Start one bounded local ad-closing job on an existing app session. The phone waits and closes available controls across successive ad windows without model round trips. Supply observed app returnTargets; for a graphical game, save its host windowId before the ad, then use by=windowId/exact only after another ad window opens. Available close/skip controls are preferred. Hidden ad controls can be revealed with bounded neutral taps; visual skip icons need positive pixel evidence beside an observed badge. It never skips observed timers or claims rewards; follow ad_status, or ad_cancel. Reuse requestId only for the same job.',{packageName:pkg,sessionId:id,requestId:id,returnTargets:z.array(z.object({query:z.string().min(1).max(250).refine(value=>value.trim().length>0,'A return target cannot be blank'),by:z.enum(['text','description','viewId','windowId']).default('text'),match:z.enum(['exact','contains']).default('exact')}).strict().refine(t=>t.by!=='windowId'||(t.match==='exact'&&/^[1-9][0-9]{0,9}$/.test(t.query)&&Number(t.query)<=2147483647),'windowId requires an exact positive Android window ID')).min(1).max(6),timeoutMs:z.number().int().min(1000).max(180000).default(90000),maxActions:z.number().int().min(1).max(16).default(8),visual:z.boolean().default(true)},true);
  add('phone_ad_status','ad_status','Read a local ad job with a bounded long poll. completed confirms an observed returnTarget, not a reward. Keep the exact packageName, sessionId and jobId. Read-only: never clicks or starts another job.',{packageName:pkg,sessionId:id,jobId:id,waitMs:z.number().int().min(0).max(8000).default(0)});
  add('phone_ad_cancel','ad_cancel','Cancel only this local ad job immediately. It does not end or resume its app session. Actions already delivered may have taken effect; inspect the returned state before continuing.',{packageName:pkg,sessionId:id,jobId:id},true);
  add('phone_set_control_mode','control_mode','Explicitly select foreground or parallel, preserving the existing session and permissions.',{mode:z.enum(['foreground','parallel']),panelOff:z.boolean().default(false)},true);
  add('phone_files_list','files_list','List Phone MCP transfers and recent installation jobs. Requires Shizuku.');
  add('phone_send_file','send_file','Send a user-selected or generated chat file to the phone. Pass the host file object; no URL needs to be created, copied or published by the user. Follow file_job until completed.',{requestId:id,file:z.object({download_url:https,file_id:z.string().min(1).max(512),mime_type:z.string().max(200).optional(),file_name:z.string().max(500).optional()}).strict(),fileName:z.string().min(1).max(140).optional(),sha256:hash.optional(),maxBytes:z.number().int().min(1).max(536870912).default(536870912)},true);
  out[out.length-1].fileParams=['file'];
  add('phone_upload_begin','file_upload_begin','Prepare a direct binary transfer from a local client, without hosting the file. Reuse requestId only for the same file. Return jobId, offset and an authenticated relative upload path.',{requestId:id,fileName:z.string().min(1).max(140).refine(v=>v===v.trim()&&!v.startsWith('.')&&!/[\\/:*?"<>|\x00-\x1f\x7f]/.test(v)&&!v.toLowerCase().endsWith('.part')),sizeBytes:z.number().int().min(1).max(536870912),sha256:hash},true);
  add('phone_upload_commit','file_upload_commit','Finish a fully received binary transfer; SHA-256 and exact size must match before the file becomes completed. Follow file_job; incomplete or mismatched bytes are never published as completed.',{jobId:id},true);
  add('phone_download_file','download_file','Download an HTTPS file, up to 512 MiB. Follow its job; reuse requestId after an uncertain result.',{requestId:id,url:https,fileName:z.string().min(1).max(140).refine(v=>v===v.trim()&&!v.startsWith('.')&&!/[\\/:*?"<>|\x00-\x1f\x7f]/.test(v)&&!v.toLowerCase().endsWith('.part')),sha256:hash.optional(),maxBytes:z.number().int().min(1).max(536870912).optional()},true);
  add('phone_file_job','file_job','Inspect a transfer/install, or cancel a transfer. Only completed confirms success.',{jobId:id,action:z.enum(['status','cancel']).default('status')},true);
  add('phone_install_apk','install_apk','Install the specified transferred APK via Shizuku, checking its SHA-256 and package. Follow the job.',{requestId:id,fileId:id,sha256:hash,expectedPackage:pkg.refine(v=>v!=='android')},true);
  add('phone_stop','stop','Stop the phone session. Restarting requires a local action.',{},true);
  add('phone_find_nodes','find_nodes','Find matching accessible elements on the active display, including later pages.',query);
  add('phone_wait_for','wait_for','Wait for a matching element, returning found=false on timeout. Never repeats an action.',{...query,timeoutMs:z.number().int().min(100).max(8000).default(5000)});
  add('phone_wait_and_click','wait_and_click','Wait locally up to 8 seconds for one exact visible, enabled, unprotected accessible target, then click once and return its final observation. Timeout returns found=false and performed=false. Ambiguous matches are refused; no coordinate guessing, no timer skipping or action retries.',{packageName:pkg,query:z.string().min(1).max(250),by:z.enum(['text','description','viewId']).default('text'),match:z.literal('exact').default('exact'),timeoutMs:z.number().int().min(100).max(8000).default(5000)},true,true);
  add('phone_wait_for_idle','wait_for_idle','Wait until the observed accessibility content is stable; returns idle=false on timeout.',{packageName:pkg,timeoutMs:z.number().int().min(500).max(8000).default(5000),stableMs:z.number().int().min(250).max(1500).default(600)});
  add('phone_node_details','node_details','Read the full text and state of one freshly observed, unprotected node.',node);
  add('phone_double_tap','double_tap','Double tap inside the freshly observed window and stable target; canvas areas require a recent screenshot.',{...action,x:point,y:point},true,true);
  add('phone_pinch','pinch','Pinch or zoom inside the freshly observed window and stable target; canvas areas require a recent screenshot.',{...action,centerX:point,centerY:point,startDistance:z.number().int().min(20).max(1500),endDistance:z.number().int().min(20).max(1500),durationMs:z.number().int().min(150).max(1000).default(400)},true,true);
  add('phone_edit_text','edit_text','Append, insert, clear or replace an observed non-protected editable field.',{...node,mode:z.enum(['append','insert','clear','replace']),text:text.default(''),position:z.number().int().min(0).max(4000).optional()},true,true);
  add('phone_press_key','press_key','Submit the observed editable field using its Android IME Enter action.',{...node,key:z.literal('enter').default('enter')},true,true);
  add('phone_scroll_to_node','scroll_to_node','Ask Android to bring an observed node into view. Find with includeOffscreen first.',node,true,true);
  add('phone_notification_dismiss','notification_dismiss','Dismiss a freshly observed clearable notification.',{packageName:pkg,notificationId:id},true);
  add('phone_notification_snooze','notification_snooze','Snooze a freshly observed notification.',{packageName:pkg,notificationId:id,durationMs:z.number().int().min(1000).max(604800000)},true);
  add('phone_storage_locations','storage_locations','List folders explicitly selected on the phone. Works without Shizuku.');
  add('phone_list_files','list_files','List a path inside an explicitly selected folder.',{locationId:id,path:path.default(''),offset:z.number().int().min(0).max(10000).default(0),limit:z.number().int().min(1).max(200).default(100)});
  add('phone_read_file','read_file','Read a UTF-8 text file inside a selected folder, with line pagination.',{locationId:id,path,offset:z.number().int().min(1).max(100000).default(1),limit:z.number().int().min(1).max(200).default(100)});
  add('phone_write_file','write_file','Create or explicitly replace a UTF-8 file in a selected folder. Text is bounded to 16,000 JSON characters and 24,000 UTF-8 bytes. Overwrite requires overwrite=true.',{locationId:id,path,text:fileText,overwrite:z.boolean().default(false)},true);
  add('phone_delete_file','delete_file','Delete the requested file inside a selected folder. Directories are refused.',{locationId:id,path},true);
  add('phone_shared_content','shared_content','List content deliberately shared to Phone MCP through Android Share.');
  add('phone_read_shared_file','read_shared_file','Read a shared text file or image. Does not access other app files.',{fileId:id,offset:z.number().int().min(1).max(100000).default(1),limit:z.number().int().min(1).max(200).default(100)});
  return out;
}
