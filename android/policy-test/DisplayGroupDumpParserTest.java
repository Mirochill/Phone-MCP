package fr.phonemcp;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class DisplayGroupDumpParserTest {
    private static int checks;
    private static void expect(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    // Exact section/indentation grammar emitted by the AOSP16 dumpLocked methods;
    // names are synthetic. This is not presented as a Samsung device dump.
    private static final String DISPLAY="DISPLAY MANAGER (dumpsys display)\n\nLogicalDisplayMapper:\n---------------------\n  mSingleDisplayDemoMode=false\n  mInteractive=true\n\n  Logical Displays: size=2\n  Display 0:\n    mDisplayId=0\n    mPrimaryDisplayDevice=PERSONAL_PRIMARY_NAME\n    mBaseDisplayInfo=DisplayInfo{displayGroupId 0}\n    mDisplayGroupId=0\n    mDisplayGroupName=PERSONAL_GROUP\n\n  Display 79:\n    mDisplayId=79\n    mPrimaryDisplayDevice=PERSONAL_APP_NAME\n    mBaseDisplayInfo=DisplayInfo{displayGroupId 0, ownerUid 12345}\n    mDisplayGroupId=3\n    mDisplayGroupName=PERSONAL_GROUP\n\n  Display Groups: size=2\n  Group 0:\n    Display 0 DisplayDevice{PERSONAL_PRIMARY_NAME, uniqueId=PRIVATE}\n\n  Group 3:\n    Display 79 DisplayDevice{PERSONAL_APP_NAME, uniqueId=PRIVATE}\n\n  DeviceStateToLayoutMap:\n  -----------------------\n    Registered Layouts:\nCallbacks: size=0\n";
    private static final String POWER="POWER MANAGER (dumpsys power)\n\nPower Manager State:\n  mWakefulness=Awake\n  unrelatedPrivateWakeLock=PERSONAL\n\nDisplay Group User Activity:\n  displayGroupId=0\n  userActivitySummary=0x0\n  lastUserActivityTime=1000 (1 sec ago)\n  lastUserActivityTimeNoChangeLights=900 (2 sec ago)\n  mWakeLockSummary=0x0\n  displayGroupId=3\n  userActivitySummary=0x1\n  lastUserActivityTime=2000 (0 sec ago)\n  lastUserActivityTimeNoChangeLights=1900 (0 sec ago)\n  mWakeLockSummary=0x1\nNotifier:\n  personal wake lock details discarded\nPowerManagerFlags:\n per_display_wake_by_touch:            true (def:false)\n improve_wakelock_latency:             false (def:false)\n";
    private static DisplayGroupDumpParser.DisplayResult display(String text,boolean complete){DisplayGroupDumpParser.DisplayParser parser=new DisplayGroupDumpParser.DisplayParser();for(String line:text.split("\n",-1))parser.line(line);return parser.finish(complete);}
    private static DisplayGroupDumpParser.PowerResult power(String text,boolean complete){DisplayGroupDumpParser.PowerParser parser=new DisplayGroupDumpParser.PowerParser();for(String line:text.split("\n",-1))parser.line(line);return parser.finish(complete);}
    private static BoundedDumpReader.Result consume(String text,int bytes,List<String> values){BoundedDumpReader.Result result=new BoundedDumpReader.Result();BoundedDumpReader.consume(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)),bytes,values::add,result);return result;}
    public static void main(String[] args){
        DisplayGroupDumpParser.DisplayResult d=display(DISPLAY,true);
        expect(d.verified,"AOSP16 mapper sections and counts are recognized");
        expect(d.groups.get(79)==3&&d.groups.get(0)==0,"Use the internal mDisplayGroupId, not embedded public DisplayInfo text");
        expect(d.members.get(3).equals(new TreeSet<>(Arrays.asList(79))),"Associate group members only inside the exact group section");
        expect(!d.groups.toString().contains("PERSONAL")&&!d.members.toString().contains("PRIVATE"),"Parsed records contain only numeric IDs, never names or unique IDs");
        String shared=DISPLAY.replace("    mDisplayGroupId=3","    mDisplayGroupId=0").replace("  Display Groups: size=2","  Display Groups: size=1").replace("\n  Group 3:\n", "\n");
        d=display(shared,true);expect(d.verified&&d.members.get(0).equals(new TreeSet<>(Arrays.asList(0,79))),"A verified shared physical group is represented honestly");
        expect(!display(DISPLAY,false).verified,"A truncated capture cannot verify an otherwise complete-looking mapper");
        expect(!display(DISPLAY.replace("LogicalDisplayMapper:","SamsungLogicalMapper:"),true).verified,"Unknown Samsung section names fail closed");
        expect(!display(DISPLAY.replace("  Display Groups:","  DisplayGroups:"),true).verified,"Unknown Samsung group layout is not guessed");
        expect(!display(DISPLAY.replace("    mDisplayGroupId=3","      mDisplayGroupId=3"),true).verified,"Nested labels cannot supply the mapping field");
        expect(!display(DISPLAY.replace("    mDisplayId=79","    mDisplayId=78"),true).verified,"The block ID must match mDisplayId");
        expect(!display(DISPLAY.replace("    mDisplayGroupId=3","    mDisplayGroupId=3\n    mDisplayGroupId=0"),true).verified,"Duplicate or conflicting mapping fields fail closed");
        expect(!display(DISPLAY.replace("    mDisplayGroupId=3","    mDisplayGroupId=-1"),true).verified,"An unassigned or malformed internal group cannot verify");
        expect(!display(DISPLAY.replace("  Logical Displays: size=2","  Logical Displays: size=3"),true).verified,"A missing logical record cannot pass the declared count");
        expect(!display(DISPLAY.replace("  Display Groups: size=2","  Display Groups: size=3"),true).verified,"A missing group record cannot pass the declared count");
        expect(!display(DISPLAY.replace("    Display 79 DisplayDevice", "    Display 78 DisplayDevice"),true).verified,"Group membership must agree with actual logical records");
        expect(!display(DISPLAY.replace("    Display 79 DisplayDevice", "    Display 0 DisplayDevice"),true).verified,"A display cannot belong to two groups");
        expect(!display(DISPLAY.replace("  Group 3:","  Group 0:"),true).verified,"Duplicate group identities are ambiguous");
        expect(!display(DISPLAY.replace("  Logical Displays: size=2","  Logical Displays: size=65"),true).verified,"Large record counts fail closed");
        expect(!display(DISPLAY.replace("mDisplayId=79","mDisplayId=2147483648"),true).verified,"Integer overflow cannot authorize a mapping");
        expect(!display(DISPLAY+"LogicalDisplayMapper:\n",true).verified,"An injected second mapper header is rejected");
        expect(display(DISPLAY.replace("PERSONAL_APP_NAME","label Group 0: mDisplayGroupId=0"),true).groups.get(79)==3,"Mapping-looking app labels on an opaque field are ignored");
        DisplayGroupDumpParser.DisplayParser finished=new DisplayGroupDumpParser.DisplayParser();for(String line:DISPLAY.split("\n"))finished.line(line);d=finished.finish(true);finished.line("LogicalDisplayMapper:");expect(d.verified&&d.groups.get(79)==3,"Late reader callbacks cannot change a finalized result");
        DisplayGroupDumpParser.PowerResult p=power(POWER,true);
        expect(p.verified&&p.groupIds.equals(new TreeSet<>(Arrays.asList(0,3))),"AOSP16 PMS display-group section identifies real mPowerGroups IDs");
        expect(Boolean.TRUE.equals(p.perDisplayWakeByTouch),"Read the runtime flag value, not its default");
        expect(!power(POWER,false).verified,"An incomplete PMS capture cannot verify its group list");
        expect(!power(POWER.replace("Display Group User Activity:","Power Groups:"),true).verified,"An unknown Samsung PMS structure is unavailable rather than guessed");
        expect(!power(POWER.replace("  displayGroupId=3","    displayGroupId=3"),true).verified,"A nested label cannot create a power group");
        expect(!power(POWER.replace("  displayGroupId=3","  displayGroupId=0"),true).verified,"Duplicate PMS group IDs fail closed");
        expect(!power(POWER.replace("  mWakeLockSummary=0x1\n",""),true).verified,"Incomplete PMS records fail closed");
        expect(!power(POWER+"Display Group User Activity:\n",true).verified,"Multiple power-group sections are ambiguous");
        expect(power(POWER.replace("true (def:false)","false (def:true)"),true).perDisplayWakeByTouch==Boolean.FALSE,"Runtime false is distinct from an unavailable flag");
        expect(power(POWER.replace(" per_display_wake_by_touch:"," samsung_touch_flag:"),true).perDisplayWakeByTouch==null,"An unknown runtime flag is returned unavailable");
        expect(power(POWER+" per_display_wake_by_touch: false (def:false)\n",true).perDisplayWakeByTouch==null,"Repeated runtime flags cannot choose a convenient value");
        List<String> lines=new ArrayList<>();BoundedDumpReader.Result r=consume("one\r\ntwo\n",100,lines);
        expect(r.readerDone&&r.errorCode.isEmpty()&&lines.equals(Arrays.asList("one","two")),"Streaming accepts LF/CRLF and sends only complete lines to a parser");
        lines.clear();r=consume("é\n",3,lines);expect(!r.sizeLimited&&lines.equals(Arrays.asList("é")),"The byte cap respects UTF8 decoding");
        lines.clear();r=consume("é\n",2,lines);expect(r.sizeLimited&&r.errorCode.equals("size_limit"),"Byte limits fail closed before a partial UTF8 dump can verify");
        lines.clear();r=consume("x".repeat(32769),50000,lines);expect(r.lineTooLong&&lines.isEmpty(),"A single large private line cannot grow without a bound");
        lines.clear();r=consume("*** SERVICE 'display' DUMP TIMEOUT (1000ms) EXPIRED ***\n",200,lines);expect(r.timedOut&&lines.isEmpty(),"A dumpsys timeout marker cannot be accepted as a successful snapshot");
        r=new BoundedDumpReader.Result();BoundedDumpReader.consume(new ByteArrayInputStream("secret\n".getBytes(StandardCharsets.UTF_8)),100,line->{throw new IllegalStateException("PRIVATE_CONTENT");},r);expect(r.errorCode.equals("parser_failed")&&!r.errorCode.contains("PRIVATE"),"Parser exceptions expose only a constant error code");
        expect(BoundedDumpReader.run("power; anything",100,100,line->{}).errorCode.equals("invalid_request"),"Arbitrary command/service text is refused before process creation");
        expect(BoundedDumpReader.run("display",0,100,line->{}).errorCode.equals("invalid_request"),"A nonpositive byte cap is refused");
        expect(BoundedDumpReader.run("power",3*1024*1024,100,line->{}).errorCode.equals("invalid_request"),"Oversized dump budgets are refused");
        expect(BoundedDumpReader.run("input",100,2000,line->{}).errorCode.equals("invalid_request"),"Long dump deadlines are refused");
        expect(BoundedDumpReader.run(null,100,100,line->{}).errorCode.equals("invalid_request"),"Null service names cannot create a process");
        System.out.println("PASS: "+checks+" display-group parser/privacy/bounds checks");
    }
}