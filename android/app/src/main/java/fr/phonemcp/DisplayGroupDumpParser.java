package fr.phonemcp;

import java.util.*;
import java.util.regex.*;

/** Source-format parsers for AOSP16 dumps; unknown OEM structures are never guessed. */
final class DisplayGroupDumpParser {
    private static final int MAX_ENTRIES=64;
    private static final Pattern LOGICAL_COUNT=Pattern.compile("  Logical Displays: size=([0-9]+)");
    private static final Pattern GROUP_COUNT=Pattern.compile("  Display Groups: size=([0-9]+)");
    private static final Pattern DISPLAY=Pattern.compile("  Display ([0-9]+):");
    private static final Pattern GROUP=Pattern.compile("  Group ([0-9]+):");
    private static final Pattern DISPLAY_ID=Pattern.compile("    mDisplayId=([0-9]+)");
    private static final Pattern GROUP_ID=Pattern.compile("    mDisplayGroupId=([0-9]+)");
    private static final Pattern MEMBER=Pattern.compile("    Display ([0-9]+) .+");
    private static final Pattern POWER_ID=Pattern.compile("  displayGroupId=([0-9]+)");
    private static final Pattern TOUCH_FLAG=Pattern.compile(" per_display_wake_by_touch: +(true|false) \\(def:(?:true|false)\\)");
    static final class DisplayResult {
        boolean verified;String reason="format_unavailable";
        final SortedMap<Integer,Integer> groups=new TreeMap<>();
        final SortedMap<Integer,SortedSet<Integer>> members=new TreeMap<>();
    }
    static final class PowerResult {
        boolean verified;String reason="format_unavailable";Boolean perDisplayWakeByTouch;
        final SortedSet<Integer> groupIds=new TreeSet<>();
    }
    private static int number(String value){try{return Integer.parseInt(value);}catch(NumberFormatException invalid){return -1;}}
    private static int match(Pattern pattern,String line){Matcher match=pattern.matcher(line);return match.matches()?number(match.group(1)):-1;}
    static final class DisplayParser implements BoundedDumpReader.LineSink {
        private final DisplayResult result=new DisplayResult();
        private boolean header,mapper,logicalSeen,groupsSeen,closed,inside;
        private int mode,logicalCount=-1,groupCount=-1,currentDisplay=-1,currentGroup=-1;
        private Integer id,group;private String error="";
        private void fail(String reason){if(error.isEmpty())error=reason;}
        private void finishDisplay(){
            if(currentDisplay<0)return;
            if(id==null||group==null||id!=currentDisplay)fail("logical_record_incomplete");
            else if(result.groups.size()>=MAX_ENTRIES||result.groups.put(currentDisplay,group)!=null)fail("logical_record_ambiguous");
            currentDisplay=-1;id=null;group=null;
        }
        @Override public synchronized void line(String line){
            if(closed)return;
            if("DISPLAY MANAGER (dumpsys display)".equals(line)){if(header)fail("duplicate_service_header");header=true;return;}
            if("LogicalDisplayMapper:".equals(line)){
                if(mapper)fail("duplicate_mapper");mapper=true;inside=true;return;
            }
            if(!inside)return;
            if(line.isEmpty()||"---------------------".equals(line))return;
            if(line.indexOf('\t')>=0){fail("unknown_indentation");return;}
            if(!line.startsWith(" ")){finishDisplay();inside=false;return;}
            int value=match(LOGICAL_COUNT,line);
            if(value>=0){
                if(logicalSeen||groupsSeen||value>MAX_ENTRIES)fail("logical_section_ambiguous");
                logicalSeen=true;logicalCount=value;mode=1;return;
            }
            value=match(GROUP_COUNT,line);
            if(value>=0){
                finishDisplay();if(!logicalSeen||groupsSeen||value>MAX_ENTRIES)fail("group_section_ambiguous");
                groupsSeen=true;groupCount=value;mode=2;return;
            }
            if(mode==0)return; // Mapper metadata is not used as a display/group mapping.
            if(mode==1){
                value=match(DISPLAY,line);
                if(value>=0){finishDisplay();currentDisplay=value;return;}
                if(currentDisplay<0){fail("unknown_logical_format");return;}
                value=match(DISPLAY_ID,line);
                if(value>=0){if(id!=null)fail("duplicate_display_id");id=value;return;}
                value=match(GROUP_ID,line);
                if(value>=0){if(group!=null)fail("duplicate_group_id");group=value;return;}
                // Extra OEM metadata is ignored; mandatory IDs, counts and cross-membership are still checked.
                return;
            }
            if(mode==2){
                value=match(GROUP,line);
                if(value>=0){
                    currentGroup=value;
                    if(result.members.size()>=MAX_ENTRIES||result.members.put(value,new TreeSet<>())!=null)fail("group_record_ambiguous");
                    return;
                }
                value=match(MEMBER,line);
                if(value>=0){
                    SortedSet<Integer> members=result.members.get(currentGroup);
                    if(members==null||members.size()>=MAX_ENTRIES||!members.add(value))fail("member_ambiguous");
                    return;
                }
                if("  DeviceStateToLayoutMap:".equals(line)){mode=3;return;}
                fail("unknown_group_format");
            }
        }
        synchronized DisplayResult finish(boolean complete){
            closed=true;finishDisplay();
            if(!complete)fail("capture_incomplete");
            if(!header||!mapper||!logicalSeen||!groupsSeen)fail("format_unavailable");
            if(result.groups.size()!=logicalCount||result.members.size()!=groupCount)fail("section_count_mismatch");
            Set<Integer> seen=new HashSet<>();
            for(Map.Entry<Integer,SortedSet<Integer>> entry:result.members.entrySet()){
                if(entry.getValue().isEmpty())fail("empty_group");
                for(int member:entry.getValue()){
                    if(!seen.add(member)||!entry.getKey().equals(result.groups.get(member)))fail("membership_mismatch");
                }
            }
            if(seen.size()!=result.groups.size())fail("membership_incomplete");
            result.verified=error.isEmpty();result.reason=result.verified?"aosp16_mapper":error;return result;
        }
    }
    static final class PowerParser implements BoundedDumpReader.LineSink {
        private final PowerResult result=new PowerResult();
        private boolean header,section,inside,closed,flags,flagsSeen,flagSeen,flagInvalid;
        private int current=-1,fields;private String error="";
        private void fail(String reason){if(error.isEmpty())error=reason;}
        private void finishGroup(){if(current>=0&&fields!=15)fail("power_record_incomplete");current=-1;fields=0;}
        @Override public synchronized void line(String line){
            if(closed)return;
            if("POWER MANAGER (dumpsys power)".equals(line)){if(header)fail("duplicate_service_header");header=true;return;}
            if("Display Group User Activity:".equals(line)){
                finishGroup();if(section)fail("duplicate_power_groups");section=true;inside=true;flags=false;return;
            }
            if("PowerManagerFlags:".equals(line)){
                if(flagsSeen)flagInvalid=true;flagsSeen=true;flags=true;
                if(inside){finishGroup();inside=false;}return;
            }
            if(flags){
                if(line.isEmpty())return;
                if(!line.startsWith(" ")){flags=false;return;}
                Matcher flag=TOUCH_FLAG.matcher(line);
                if(flag.matches()){
                    if(flagSeen)flagInvalid=true;flagSeen=true;result.perDisplayWakeByTouch=Boolean.valueOf(flag.group(1));
                }else if(line.trim().startsWith("per_display_wake_by_touch"))flagInvalid=true;
                return;
            }
            if(!inside||line.isEmpty())return;
            if(!line.startsWith(" ")){finishGroup();inside=false;return;}
            int value=match(POWER_ID,line);
            if(value>=0){
                finishGroup();current=value;
                if(result.groupIds.size()>=MAX_ENTRIES||!result.groupIds.add(value))fail("power_group_ambiguous");return;
            }
            if(current<0){fail("unknown_power_format");return;}
            int field=line.matches("  userActivitySummary=0x[0-9a-fA-F]+")?1
                    :line.startsWith("  lastUserActivityTime=")&&line.length()>23?2
                    :line.startsWith("  lastUserActivityTimeNoChangeLights=")&&line.length()>37?4
                    :line.matches("  mWakeLockSummary=0x[0-9a-fA-F]+")?8:0;
            if(field==0)fail("unknown_power_format");
            else{if((fields&field)!=0)fail("duplicate_power_field");fields|=field;}
        }
        synchronized PowerResult finish(boolean complete){
            closed=true;finishGroup();
            if(!complete)fail("capture_incomplete");
            if(!header||!section||result.groupIds.isEmpty())fail("format_unavailable");
            if(flagInvalid||!flagsSeen||!flagSeen)result.perDisplayWakeByTouch=null;
            result.verified=error.isEmpty();result.reason=result.verified?"aosp16_power_groups":error;return result;
        }
    }
}