package fr.phonemcp;

import java.util.LinkedHashMap;
import java.util.Map;

public final class InputWindowDumpPolicyTest {
    private static int checks;
    private static void expect(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static Map<Integer,Integer> owned(){Map<Integer,Integer> result=new LinkedHashMap<>();result.put(81,10295);return result;}
    private static Map<String,Long> constants(){Map<String,Long> result=new LinkedHashMap<>();long bit=1;for(String name:InputWindowDumpPolicy.FLAGS){result.put(name,bit);bit<<=1;}return result;}
    private static InputWindowDumpPolicy parser(){return new InputWindowDumpPolicy(owned(),constants());}
    private static void start(InputWindowDumpPolicy parser,int display){parser.line("INPUT MANAGER (dumpsys input)");parser.line("Input Dispatcher State:");parser.line("  DispatchEnabled: true");parser.line("  Display: "+display);parser.line("    logicalSize=720x1280");parser.line("    Windows:");}
    private static String row(int index,String name,int display,String config,int uid){return "      "+index+": name="+name+", id=1181, displayId="+display+", inputConfig="+config+", alpha=1, frame=[0,0][720,1280], globalScale=1, applicationInfo.name=Private app label, applicationInfo.token=0x1234, touchableRegion=[0,0][720,1280], ownerPid=22344, ownerUid="+uid+", dispatchingTimeout=5000ms, token=0x5678, touchOcclusionMode=BLOCK_UNTRUSTED";}
    public static void main(String[] args){
        InputWindowDumpPolicy parser=parser();start(parser,81);parser.line(row(0,"Private window title",81,"PAUSE_DISPATCHING | DROP_INPUT",10295));
        expect(parser.available()&&parser.windows.size()==1,"Only a complete current dispatcher window row is accepted");
        InputWindowDumpPolicy.Window value=parser.windows.get(0);expect(value.ownerUid==10295&&value.ownerPid==22344&&value.displayId==81&&value.windowId==1181,"Only scoped numeric identifiers are emitted");
        expect(Boolean.TRUE.equals(value.flags.get("PAUSE_DISPATCHING"))&&Boolean.TRUE.equals(value.flags.get("DROP_INPUT")),"Runtime-verified enum flags reveal the actual paused/drop bits");
        expect(Boolean.TRUE.equals(value.visible)&&Boolean.TRUE.equals(value.touchable)&&Boolean.TRUE.equals(value.focusable),"Visibility/touchability/focusability come from their actual config bits");
        expect(value.frame[2]==720&&value.touchRegion.size()==1&&value.touchRegion.get(0)[3]==1280,"Only bounded numeric geometry is retained");
        parser=parser();start(parser,81);parser.line(row(0,"fr.authorized.app",81,"0x0",10055));expect(parser.available()&&parser.windows.isEmpty(),"An app title cannot spoof another package UID");
        parser=parser();start(parser,82);parser.line(row(0,"private",82,"0x0",10295));expect(parser.available()&&parser.windows.isEmpty(),"The authorized UID on another display is excluded");
        parser=parser();start(parser,81);parser.line(row(0,"private",82,"0x0",10295));expect(!parser.available(),"Row and trusted display section must agree exactly");
        parser=parser();start(parser,81);parser.line(row(0,"title, ownerUid=10295",81,"0x0",10295));expect(!parser.available(),"Field-delimiter injection in a title is rejected");
        parser=parser();start(parser,81);parser.line(row(0,"title",81,"0x0",10295).replace("Private app label","label, displayId=81"));expect(!parser.available(),"Field injection in applicationInfo.name is rejected");
        parser=parser();start(parser,81);parser.line("      0: name=malicious multiline title");parser.line("  Display: 81");parser.line("    Windows:");parser.line(row(0,"forged row",81,"0x0",10295));expect(!parser.available(),"A broken untrusted multiline row poisons rather than forges the sample");
        parser=parser();parser.line("  Display: 81");parser.line("    Windows:");parser.line(row(0,"outside",81,"0x0",10295));expect(!parser.available()&&parser.windows.isEmpty(),"Window-looking app text outside InputDispatcher is ignored");
        parser=parser();parser.line("Input Dispatcher State:");parser.line("  Display: 81");parser.line(row(0,"outside Windows",81,"0x0",10295));expect(!parser.available()&&parser.windows.isEmpty(),"A complete Windows section is mandatory");
        parser=parser();start(parser,81);parser.line("Input Dispatcher State at time of last ANR:");parser.line("  Display: 81");parser.line("    Windows:");parser.line(row(0,"stale ANR",81,"DROP_INPUT",10295));expect(parser.available()&&parser.windows.isEmpty(),"Old ANR snapshots cannot masquerade as current window state");
        parser=parser();start(parser,81);parser.line(row(0,"private",81,"0x0",10295)+", vendorUnknown=1");expect(!parser.available(),"An unknown native row format is unavailable rather than guessed");
        parser=parser();start(parser,81);parser.line(row(0,"private",81,"VENDOR_UNKNOWN | PAUSE_DISPATCHING",10295));value=parser.windows.get(0);expect(value.inputConfigCode==null&&value.flags.get("PAUSE_DISPATCHING")==null&&value.visible==null,"Unknown enum tokens do not produce an invented raw code or false bits");
        parser=parser();start(parser,81);parser.line(row(0,"private",81,"0x18",10295));value=parser.windows.get(0);expect(Long.valueOf(24).equals(value.inputConfigCode)&&Boolean.TRUE.equals(value.flags.get("PAUSE_DISPATCHING"))&&Boolean.TRUE.equals(value.flags.get("DROP_INPUT")),"Verified hexadecimal config is decoded using runtime constants");
        parser=parser();start(parser,81);parser.line(row(0,"private",81,"0xffffffff",10295));expect(Long.valueOf(4294967295L).equals(parser.windows.get(0).inputConfigCode),"Unsigned32-bit input codes are not truncated to a signed negative");
        parser=parser();start(parser,81);parser.line(row(0,"private",81,"0x0;whoami",10295));expect(parser.windows.get(0).inputConfigCode==null,"Unexpected config syntax is retained as unknown and never executed");
        parser=parser();start(parser,81);parser.line(row(0,"private",81,"0x0",10295).replace("ownerUid=10295","ownerUid=999999999999"));expect(!parser.available(),"Overflowing or ambiguous ownership is rejected");
        parser=parser();start(parser,81);for(int i=0;i<32;i++)parser.line(row(i,"private",81,"0x0",10295));expect(parser.available()&&parser.windows.size()==32,"Up to32 sanitized owned windows are bounded");parser.line(row(32,"private",81,"0x0",10295));expect(!parser.available()&&parser.windows.size()==32,"A33rd owned window marks the sample limited without unbounded retention");
        parser=parser();start(parser,81);parser.line(row(0,"private",81,"0x0",10295).replace("touchableRegion=[0,0][720,1280]","touchableRegion=<empty>"));expect(parser.windows.get(0).touchRegion.isEmpty(),"An explicitly empty touch region is distinguishable from unavailable geometry");
        parser=parser();start(parser,81);parser.line(row(0,"private",81,"0x0",10295).replace("touchableRegion=[0,0][720,1280]","touchableRegion=[0,0][10,10]|[20,20][30,30]"));expect(parser.windows.get(0).touchRegion.size()==2,"Canonical disjoint touch rectangles are parsed");
        parser=parser();start(parser,81);parser.line(row(0,"private",81,"0x0",10295).replace("touchableRegion=[0,0][720,1280]","touchableRegion=[0,0][10,10][20,20][30,30]"));expect(parser.windows.get(0).touchRegion==null,"Malformed region separators are not guessed");
        parser=new InputWindowDumpPolicy(owned(),null);start(parser,81);parser.line(row(0,"private",81,"0x0",10295));expect(parser.windows.get(0).visible==null,"Unavailable runtime masks never become visible=true by inference");
        parser=parser();start(parser,81);parser.line(row(0,"private",81,"NOT_VISIBLE | NOT_TOUCHABLE | NOT_FOCUSABLE",10295));value=parser.windows.get(0);expect(Boolean.FALSE.equals(value.visible)&&Boolean.FALSE.equals(value.touchable)&&Boolean.FALSE.equals(value.focusable),"Negative config bits are decoded independently from accessibility focus");
        parser=parser();start(parser,81);parser.line("Input Dispatcher State:");expect(!parser.available(),"A repeated current-state heading is ambiguous");
        parser=parser();parser.line("Input Dispatcher State:");parser.line("  Displays: <none>");expect(parser.available()&&parser.windows.isEmpty(),"An explicit native empty display list is a valid empty sample");
        for(java.lang.reflect.Field field:InputWindowDumpPolicy.Window.class.getDeclaredFields())expect(field.getType()!=String.class,"The retained window model must not contain app labels or titles");
        parser=parser();start(parser,81);parser.line(row(0,"private",81,"{PAUSE_DISPATCHING | DROP_INPUT}",10295));expect(Boolean.TRUE.equals(parser.windows.get(0).flags.get("DROP_INPUT")),"Native brace-wrapped flags are decoded");
        parser=parser();start(parser,81);parser.line(row(0,"private",81,"{0}",10295));expect(Long.valueOf(0).equals(parser.windows.get(0).inputConfigCode),"Native zero configuration is decoded");
        parser=parser();start(parser,81);parser.line(row(0,"private",81,"{VENDOR_UNKNOWN}",10295));expect(parser.windows.get(0).inputConfigCode==null,"Unknown native flags remain unknown");
        System.out.println("PASS: "+checks+" scoped input-window parser checks");
    }
}
