import React from 'react';
import {AbsoluteFill, Img, interpolate, Easing, spring, staticFile, useCurrentFrame, useVideoConfig} from 'remotion';
import {Audio} from '@remotion/media';
import '@fontsource/inter-tight/latin-500.css';
import '@fontsource/inter-tight/latin-600.css';
import '@fontsource/inter-tight/latin-700.css';
import '@fontsource/ibm-plex-mono/latin-400.css';
import './style.css';

const INK='#172127', MUTED='#66716F', GREEN='#32765F', BG='#F5F5F1';
const FONT='"Inter Tight", sans-serif', MONO='"IBM Plex Mono", monospace';
const smooth=Easing.bezier(.65,0,.25,1);
const clamp={extrapolateLeft:'clamp' as const,extrapolateRight:'clamp' as const};
const mix=(f:number,a:number,b:number,x=0,y=1)=>interpolate(f,[a,b],[x,y],{...clamp,easing:smooth});
const show=(f:number,a:number,b:number)=>mix(f,a,a+16)*(1-mix(f,b,b+16));

// Shared world coordinates. Camera keys are held around each completed action.
// There is no scene swap at the chapter boundaries. The edit map shortens finished holds.
const EDIT_T=[0,40,70,90,150,178,190,206,240,260,280,335,355,415,420,440,515,540,555,585,659];
const WORLD_T=[0,86,154,202,356,392,420,450,475,490,521,616,646,716,720,750,846,884,914,960,1079];
export const CAMERA=[
  {f:0,x:835,y:660,z:1.25},{f:86,x:835,y:660,z:1.25},
  {f:128,x:2100,y:690,z:1.05},{f:154,x:2100,y:690,z:1.05},
  {f:188,x:2660,y:690,z:1.02},{f:356,x:2660,y:690,z:1.02},
  {f:392,x:3570,y:690,z:1},{f:411,x:3570,y:690,z:1},
  {f:443,x:3940,y:690,z:2.1},{f:490,x:3940,y:690,z:2.1},
  {f:521,x:3640,y:1790,z:1.2},{f:616,x:3640,y:1790,z:1.2},
  {f:646,x:2520,y:1790,z:1.05},{f:716,x:2520,y:1790,z:1.05},
  {f:750,x:1360,y:1790,z:.98},{f:846,x:1360,y:1790,z:.98},
  {f:878,x:2660,y:690,z:1.02},{f:914,x:2660,y:690,z:1.02},
  {f:960,x:2300,y:790,z:1.1},{f:1079,x:2300,y:790,z:1.1},
];

const At:React.FC<{x:number;y:number;children:React.ReactNode;style?:React.CSSProperties}>=({x,y,children,style})=><div style={{position:'absolute',left:x,top:y,...style}}>{children}</div>;
const Text:React.FC<{children:React.ReactNode;size?:number;style?:React.CSSProperties;mono?:boolean}>=({children,size=90,style,mono=false})=><div style={{fontFamily:mono?MONO:FONT,fontSize:size,fontWeight:mono?400:600,letterSpacing:mono?'-0.04em':'-0.055em',lineHeight:1.03,whiteSpace:'nowrap',color:INK,...style}}>{children}</div>;
const Small:React.FC<{children:React.ReactNode;style?:React.CSSProperties}>=({children,style})=><Text size={28} mono style={{color:MUTED,letterSpacing:'-0.025em',...style}}>{children}</Text>;

const Phone:React.FC<{src:string;width?:number;style?:React.CSSProperties}>=({src,width=440,style})=><div style={{width,padding:10,borderRadius:40,background:'#161C21',border:'2px solid #45504F',boxShadow:'0px 16px 24px rgba(17,25,25,.12)',...style}}><Img src={staticFile(src)} style={{display:'block',width:'100%',borderRadius:29}}/></div>;

const Wire:React.FC<{d:string;start?:number;end?:number;frame:number;opacity?:number;reverse?:boolean}>=({d,start=0,end=1,frame,opacity=1,reverse=false})=>{
 const p=mix(frame,start,end);
 return <g opacity={opacity}><path d={d} fill="none" stroke="#D0D5CF" strokeWidth="3"/><path d={d} pathLength={1} fill="none" stroke={GREEN} strokeWidth="4" strokeLinecap="round" strokeDasharray="1" strokeDashoffset={reverse?-(1-p):1-p}/></g>;
};

const InputStation:React.FC<{f:number}>=({f})=>{
 const command='137 × 24';const typed=command.slice(0,Math.max(2,Math.min(command.length,Math.floor((f+2)/4))));
 const o=1-mix(f,90,126);
 return <At x={280} y={280} style={{opacity:o*(1-mix(f,924,958))}}>
   <At x={0} y={0}><Small>PHONE MCP / ANDROID 0.8.2</Small></At>
   <At x={0} y={75}><Text size={122}>Your AI.<br/>On Android.</Text></At>
   <At x={0} y={382}><Small style={{color:GREEN}}>phone_set_text</Small></At>
   <At x={0} y={438}><Text size={112} mono style={{fontWeight:400,letterSpacing:'-.06em'}}>{typed}<span style={{opacity:f<65&&(Math.floor(f/12)%2===0)?1:0,color:GREEN}}>│</span></Text></At>
   <At x={0} y={602}><Small>REPLAY OF A VERIFIED MCP RUN</Small></At>
 </At>;
};

const Hub:React.FC<{f:number}>=({f})=>{
 const end=mix(f,924,965);const initial=show(f,72,180);const p=spring({frame:f-125,fps:30,config:{damping:22,stiffness:140}});
 return <>
   <At x={1875} y={685} style={{opacity:Math.max(1-mix(f,181,203),end),transform:`scale(${1+Math.sin(Math.min(1,Math.max(0,p))*Math.PI)*.08})`}}><Img src={staticFile('phone-mcp.svg')} style={{width:240*(1-end)+162*end,height:240*(1-end)+162*end}}/></At>
   <At x={1760} y={440} style={{opacity:initial}}><Text size={82} style={{fontWeight:700}}>Phone MCP</Text></At>
   <At x={2090} y={705} style={{opacity:end}}><Text size={124} style={{fontWeight:700}}>Phone MCP</Text></At>
   <At x={1800} y={990} style={{opacity:initial}}><Small>HTTPS + OAUTH</Small></At>
   <At x={1855} y={940} style={{opacity:end}}><Text size={49} style={{fontWeight:500,letterSpacing:'-.025em'}}>Your agent. Your Android.</Text></At>
   <At x={1855} y={1040} style={{opacity:end,display:'flex',alignItems:'center',gap:17}}><Img src={staticFile('github.svg')} style={{width:38,height:38}}/><Text size={35} mono>Mirochill/Phone-MCP</Text></At>
   <At x={1855} y={1120} style={{opacity:end}}><Small>OPEN SOURCE · MIT · ANDROID APK</Small></At>
 </>;
};

const ProofStation:React.FC<{f:number}>=({f})=>{
 const returning=show(f,866,902);const o=Math.max(1-mix(f,360,387),mix(f,866,878)*(1-mix(f,930,958)));
 const n=spring({frame:f-210,fps:30,config:{damping:28,stiffness:170}});
 return <>
 <At x={2900} y={285} style={{opacity:o,transform:`perspective(1800px) rotateY(${interpolate(f,[178,238],[13,0],clamp)}deg)`}}><Phone src="live-calculator.jpg"/>
   <At x={0} y={830}><Small>REAL S24 ULTRA CAPTURE</Small></At>
 </At>
 <At x={2000} y={365} style={{opacity:show(f,202,373)}}>
   <Small style={{color:GREEN}}>OBSERVED ON THE PHONE</Small>
   <At x={0} y={65}><Text size={104}>A real<br/>Android.</Text></At>
   <At x={0} y={312}><Text size={44} mono style={{color:MUTED}}>137 × 24 =</Text></At>
   <At x={-7} y={386} style={{translate:`0px ${(1-n)*32}px`,opacity:n}}><Text size={173} style={{fontWeight:700}}>3 288</Text></At>
   <At x={0} y={600}><Small>READ → ACT → OBSERVE</Small></At>
 </At>
 <At x={2000} y={440} style={{opacity:returning}}><Small style={{color:GREEN}}>ANDROID TOOL DESTINATION</Small><At x={0} y={80}><Text size={94}>Read.<br/>Act.<br/>Observe.</Text></At><At x={0} y={418}><Text size={29} mono>phone_snapshot</Text><Text size={29} mono style={{marginTop:17}}>phone_click</Text><Text size={29} mono style={{marginTop:17}}>phone_set_text</Text></At></At>
 </>;
};

const ModeStation:React.FC<{f:number}>=({f})=>{
 const o=(1-mix(f,924,956));const focus=show(f,389,412);const close=show(f,426,494);
 return <>
   <At x={3880} y={285} style={{opacity:o}}><Phone src="android-session.png"/>
   </At>
   <At x={3020} y={365} style={{opacity:focus}}>
     <Small style={{color:GREEN}}>PARALLEL MODE</Small>
     <At x={0} y={68}><Text size={112}>Keep your<br/>screen.</Text></At>
     <At x={0} y={352}><Text size={44} style={{fontWeight:500,letterSpacing:'-.03em'}}>AI → separate display</Text></At>
     <At x={0} y={451}><Small>SHIZUKU · COMPATIBLE APPS</Small></At>
   </At>
   <At x={3880} y={1110} style={{opacity:focus}}><Small>ACTUAL SESSION INTERFACE</Small></At>
   <At x={3530} y={520} style={{opacity:close}}><Text size={63}>Separate<br/>display.</Text><Small style={{marginTop:36,fontSize:18}}>DISPLAY SCHEMATIC</Small></At>
   <At x={3790} y={670} style={{opacity:close}}><svg width="110" height="35"><path d="M 0 18 H 104 M 92 8 L 104 18 L 92 28" stroke={GREEN} strokeWidth="3" fill="none"/></svg></At>
   <At x={3530} y={726} style={{opacity:close}}>
     <svg width="320" height="211" viewBox="0 0 320 211"><rect x="10" y="12" width="80" height="140" rx="11" fill={BG} stroke={INK} strokeWidth="3"/><path d="M 42 140 H 58" stroke={INK} strokeWidth="3"/><path d={`M 92 80 H ${98+74*mix(f,448,475)}`} stroke={GREEN} strokeWidth="3"/><path d="M 156 71 L 168 80 L 156 89" fill="none" stroke={GREEN} strokeWidth="3" opacity={mix(f,460,475)}/></svg>
     <At x={14+158*mix(f,448,475)} y={-14*mix(f,448,475)} style={{opacity:mix(f,445,454)}}><Phone src="live-calculator.jpg" width={108} style={{padding:3,borderRadius:12,boxShadow:'none'}}/></At>
     <At x={0} y={155}><Small style={{fontSize:19}}>SCREEN</Small></At><At x={164} y={175}><Small style={{fontSize:19}}>AI DISPLAY</Small></At>
   </At>
 </>;
};

const FileStation:React.FC<{f:number}>=({f})=>{
 const focus=show(f,503,637);return <>
 <At x={3880} y={1390} style={{opacity:1-mix(f,924,956)}}><Phone src="android-files.png"/></At>
 <At x={3020} y={1470} style={{opacity:focus}}>
   <Small style={{color:GREEN}}>FILES + APK INSTALLATION</Small>
   <At x={0} y={68}><Text size={105}>Transfer files.<br/>Install APKs.</Text></At>
   <At x={0} y={371}><Text size={33} mono>phone_download_file</Text><Text size={33} mono style={{marginTop:22}}>phone_install_apk</Text></At>
   <At x={0} y={540}><Small>DIRECT HTTPS → ANDROID</Small></At>
 </At>
 <At x={3020} y={2118} style={{opacity:focus}}><Small style={{fontSize:23}}>ILLUSTRATED TOOL ROUTE · ORIGINAL UI</Small></At>
 <At x={3560} y={1835} style={{opacity:focus}}><svg width="420" height="240" style={{overflow:'visible'}}><path d="M 0 20 H 165 V -160 H 370" fill="none" stroke={GREEN} strokeWidth="3" strokeDasharray="600" strokeDashoffset={600*(1-mix(f,530,593))}/><path d="M 356 -170 L 370 -160 L 356 -150" fill="none" stroke={GREEN} strokeWidth="3" opacity={mix(f,580,593)}/></svg></At>
 </>;
};

const NotificationStation:React.FC<{f:number}>=({f})=>{
 const o=show(f,630,714);return <At x={1815} y={1440} style={{opacity:o}}>
   <Small style={{color:GREEN}}>NOTIFICATION ACCESS</Small>
   <At x={0} y={68}><Text size={104}>Read notifications.<br/>Reply in place.</Text></At>
   <At x={0} y={370}><Text size={34} mono>phone_notifications</Text><Text size={34} mono style={{marginTop:25}}>phone_reply_notification</Text></At>
   <At x={850} y={100}><svg width="400" height="400" viewBox="0 0 400 400"><path d="M 114 245 V 174 C 114 70 262 70 262 174 V 245 L 292 280 H 84 Z M 162 310 C 162 343 214 343 214 310" stroke={INK} fill="none" strokeWidth="10" strokeLinejoin="round"/><path d="M 55 167 C 25 214 39 307 109 339 M 309 117 C 368 168 355 242 331 270" fill="none" stroke={GREEN} strokeWidth="5" strokeDasharray="600" strokeDashoffset={600*(1-mix(f,650,677))}/><path d="M 88 315 L 109 339 L 77 341 M 355 256 L 331 270 L 326 241" stroke={GREEN} strokeWidth="5" fill="none" opacity={mix(f,667,677)}/></svg></At>
   <At x={1090} y={530}><Img src={staticFile('phone-mcp.svg')} style={{width:104,height:104}}/></At>
   <At x={1038} y={400}><svg width="210" height="140"><path d="M 20 0 V 110 H 50 M 35 98 L 50 110 L 35 122" fill="none" stroke={GREEN} strokeWidth="4" strokeDasharray="230" strokeDashoffset={230*(1-mix(f,655,680))}/><path d="M 150 118 V 8 H 119 M 133 -4 L 119 8 L 133 20" fill="none" stroke={INK} strokeWidth="4" strokeDasharray="230" strokeDashoffset={230*(1-mix(f,676,706))}/></svg></At>
   <At x={0} y={535}><Small>COMPATIBLE INLINE REPLY ACTIONS</Small></At>
 </At>;
};

const ClientStation:React.FC<{f:number}>=({f})=>{
 const o=show(f,721,916);const labels=['Chat','Work','Codex','MCP agents'];
 return <At x={610} y={1400} style={{opacity:o}}>
   <Text size={87}>One MCP connection.</Text>
   <At x={0} y={140}>
     <svg width="1550" height="640" style={{position:'absolute',overflow:'visible'}}>
       {labels.map((_,i)=>{const y=60+i*135;const start=749+i*20;const d=`M 480 ${y} C 930 ${y} 830 270 1260 270`;const packet=mix(f,start,start+23);const px=480+780*packet;const py=y+(270-y)*(3*packet*packet-2*packet*packet*packet);return <g key={i}><Wire d={d} frame={f} start={start} end={start+23}/>{f>=start&&f<start+23?<rect x={px-8} y={py-8} width="16" height="16" rx="4" fill={GREEN}/>:null}</g>;})}
     </svg>
     {labels.map((label,i)=><At key={label} x={0} y={24+i*135}><Text size={i===3?51:65} style={{fontWeight:500}}>{label}</Text></At>)}
     <At x={1270} y={202}><Img src={staticFile('phone-mcp.svg')} style={{width:130,height:130}}/></At>
     <At x={1250} y={367} style={{opacity:mix(f,803,827)}}><Text size={68}>21 tools</Text></At>
   </At>
   <At x={0} y={755}><Small>STREAMABLE HTTP + OAUTH</Small></At>
 </At>;
};

export const Film:React.FC<{offset?:number;withAudio?:boolean}>=({offset=0,withAudio=true})=>{
 const local=useCurrentFrame();const timeline=local+offset;const f=interpolate(timeline,EDIT_T,WORLD_T,clamp);const {fps}=useVideoConfig();
 const times=CAMERA.map(k=>k.f);
 let fx=interpolate(f,times,CAMERA.map(k=>k.x),{...clamp,easing:smooth});
 let fy=interpolate(f,times,CAMERA.map(k=>k.y),{...clamp,easing:smooth});
 const z=interpolate(f,times,CAMERA.map(k=>k.z),{...clamp,easing:smooth});
 const route=mix(f,846,878);const q=1-route;
 const packetX=q*q*q*2010+3*q*q*route*2520+3*q*route*route*2510+route*route*route*2890;
 const packetY=q*q*q*1810+3*q*q*route*1810+3*q*route*route*1100+route*route*route*760;
 if(f>=846&&f<=878){fx=packetX-interpolate(route,[0,1],[650,230]);fy=packetY-interpolate(route,[0,1],[20,70]);}
 const end=1-mix(f,924,958);const path1='M 1270 780 C 1550 780 1610 760 1875 760';const path2='M 2025 760 C 2400 760 2520 760 2900 760';
 return <AbsoluteFill style={{background:BG,fontFamily:FONT,overflow:'hidden'}}>
   {withAudio?<Audio src={staticFile('launch-audio.m4a')} trimBefore={offset} durationInFrames={(22*fps)-offset}/>:null}
   <div style={{position:'absolute',width:4800,height:2600,transform:`translate(${960-fx*z}px,${540-fy*z}px) scale(${z})`,transformOrigin:'0 0'}}>
     <svg width="6000" height="4000" viewBox="-600 -500 6000 4000" style={{position:'absolute',left:-600,top:-500,overflow:'visible'}}>
       <defs><pattern id="grid" width="56" height="56" patternUnits="userSpaceOnUse"><path d="M 56 0 L 0 0 0 56" fill="none" stroke="#D7DCD5" strokeWidth=".8"/></pattern></defs>
       <rect x="-600" y="-500" width="6000" height="4000" fill="url(#grid)" opacity=".65"/>
       <g opacity={end}>
         <Wire d={path1} start={90} end={127} frame={f} opacity={1-mix(f,180,206)}/><Wire d={path2} start={135} end={188} frame={f} opacity={1-mix(f,180,206)}/>
         <Wire d="M 3360 760 C 3560 760 3650 760 3880 760" start={367} end={410} frame={f} opacity={1-mix(f,411,433)}/>
         <Wire d="M 4110 1100 L 4110 1390" start={492} end={522} frame={f}/>
         <Wire d="M 3880 1860 C 3570 1860 3490 2200 3120 2200 L 2300 2200" start={610} end={652} frame={f}/>
         <Wire d="M 2100 2200 L 1640 2200 C 1470 2200 1460 2090 1460 2040" start={711} end={756} frame={f}/>
         <Wire d="M 1850 1860 C 2100 1860 2230 1670 2230 1290 C 2230 1050 1950 1030 1950 835" start={795} end={820} frame={f} opacity={1-mix(f,820,846)}/>
         <Wire d="M 2010 1810 C 2520 1810 2510 1100 2890 760" start={846} end={878} frame={f} opacity={show(f,832,918)}/>
         {f>=846&&f<882?<g transform={`translate(${packetX},${packetY})`}><rect x="-16" y="-16" width="32" height="32" rx="6" fill={GREEN}/><text x="-345" y="-35" fontSize="31" fontFamily={MONO} fill={INK}>phone_snapshot</text></g>:null}
         {[{x:3880,y:760},{x:4110,y:1390},{x:2300,y:2200},{x:1460,y:2040}].map((a,i)=><circle key={i} cx={a.x} cy={a.y} r="7" fill={BG} stroke={GREEN} strokeWidth="3"/>)}
         {f>=90&&f<188?<g transform={`translate(${interpolate(f,[90,128,135,188],[1270,1880,2020,2900],clamp)},760)`}><rect x="-16" y="-16" width="32" height="32" rx="7" fill={GREEN}/><path d="M -7 -5 L 2 -5 L 7 0 L 2 5 L -7 5" stroke={BG} strokeWidth="3" fill="none"/></g>:null}
       </g>
     </svg>
     <InputStation f={f}/><ProofStation f={f}/><ModeStation f={f}/><FileStation f={f}/><NotificationStation f={f}/><ClientStation f={f}/><Hub f={f}/>
   </div>
   <div style={{position:'absolute',left:64,top:48,display:'flex',alignItems:'center',gap:12,opacity:1-mix(f,920,950)}}><Img src={staticFile('phone-mcp.svg')} style={{width:35,height:35}}/><span style={{fontFamily:FONT,fontSize:25,fontWeight:600,letterSpacing:'-.025em'}}>Phone MCP</span></div>
 </AbsoluteFill>;
};
