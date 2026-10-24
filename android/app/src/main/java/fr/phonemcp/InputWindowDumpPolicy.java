package fr.phonemcp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Strict current-dispatcher grammar; app labels never authorize an output row. */
final class InputWindowDumpPolicy {
    static final int MAX_WINDOWS=32;
    static final String[] FLAGS={"NOT_VISIBLE","NOT_TOUCHABLE","NOT_FOCUSABLE","PAUSE_DISPATCHING","DROP_INPUT","DROP_INPUT_IF_OBSCURED","NO_INPUT_CHANNEL","TRUSTED_OVERLAY","SLIPPERY","SPY","GLOBAL_STYLUS_BLOCKS_TOUCH"};
    private static final String INT="(-?\\d{1,10})",FLOAT="[-+]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][-+]?\\d+)?",PTR="(?:0|0x[0-9a-fA-F]+|<null>)";
    private static final String RECT="\\["+INT+","+INT+"\\]\\["+INT+","+INT+"\\]";
    private static final Pattern DISPLAY=Pattern.compile("^  Display: (\\d{1,10})$"),ROW_START=Pattern.compile("^ {6}\\d+:.*$"),REGION=Pattern.compile(RECT);
    private static final Pattern ROW=Pattern.compile("^ {6}\\d+: name=(.*?), id="+INT+", displayId="+INT+", inputConfig=(.*?), alpha="+FLOAT+", frame="+RECT+", globalScale="+FLOAT+", applicationInfo.name=(.*?), applicationInfo.token="+PTR+", touchableRegion=(.*?), ownerPid="+INT+", ownerUid="+INT+", dispatchingTimeout=\\d+ms, token="+PTR+", touchOcclusionMode=[A-Z_]+(?:, canOccludePresentation)?$");
    static final class Window {
        int windowId,displayId,ownerPid,ownerUid;int[] frame;List<int[]> touchRegion;Long inputConfigCode;final Map<String,Boolean> flags=new LinkedHashMap<>();
        Boolean visible,touchable,focusable;
    }
    private final Map<Integer,Integer> owned;private final Map<String,Long> constants;
    final List<Window> windows=new ArrayList<>();boolean dispatcherSeen,structureSeen,formatError,windowLimit;private boolean inDispatcher,inWindows;private Integer display;
    InputWindowDumpPolicy(Map<Integer,Integer> ownedDisplayUids,Map<String,Long> runtimeConstants){owned=new LinkedHashMap<>();if(ownedDisplayUids!=null)for(Map.Entry<Integer,Integer> entry:ownedDisplayUids.entrySet()){if(owned.size()>=4)break;if(entry.getKey()!=null&&entry.getValue()!=null&&entry.getKey()>0&&entry.getValue()>=0)owned.put(entry.getKey(),entry.getValue());}constants=runtimeConstants==null?new LinkedHashMap<>():new LinkedHashMap<>(runtimeConstants);}
    void line(String line){
        if(line==null)return;
        if(line.equals("Input Dispatcher State:")){if(dispatcherSeen)formatError=true;dispatcherSeen=true;inDispatcher=true;inWindows=false;display=null;return;}
        if(!inDispatcher)return;
        if(line.equals("Input Dispatcher State at time of last ANR:")){inDispatcher=false;inWindows=false;return;}
        if(!line.isEmpty()&&!Character.isWhitespace(line.charAt(0))){inDispatcher=false;inWindows=false;return;}
        Matcher dm=DISPLAY.matcher(line);if(dm.matches()){try{display=Integer.valueOf(dm.group(1));}catch(NumberFormatException invalid){formatError=true;display=null;}inWindows=false;return;}
        if(line.equals("    Windows:")||line.equals("    Windows: <none>")){if(display==null){formatError=true;return;}structureSeen=true;inWindows=line.equals("    Windows:");return;}
        if(line.equals("  Displays: <none>")){structureSeen=true;inWindows=false;return;}
        if(inWindows&&ROW_START.matcher(line).matches())parse(line);
        else if(inWindows&&!line.isEmpty()&&line.startsWith("  ")&&!line.startsWith("    "))inWindows=false;
    }
    boolean available(){return dispatcherSeen&&structureSeen&&!formatError&&!windowLimit;}
    private static boolean reserved(String text){return text.indexOf('\n')>=0||text.indexOf('\r')>=0||text.contains(", id=")||text.contains("displayId=")||text.contains("inputConfig=")||text.contains("ownerPid=")||text.contains("ownerUid=")||text.contains("applicationInfo.token=");}
    private void parse(String line){
        Matcher m=ROW.matcher(line);if(!m.matches()||line.length()>32768){formatError=true;return;}
        // The native row appends trusted fields after unescaped app labels. Any delimiter
        // injection or broken multiline row makes the entire sample unavailable.
        if(reserved(m.group(1))||reserved(m.group(9))){formatError=true;return;}
        try{
            int id=Integer.parseInt(m.group(2)),actualDisplay=Integer.parseInt(m.group(3)),pid=Integer.parseInt(m.group(11)),uid=Integer.parseInt(m.group(12));
            if(display==null||actualDisplay!=display||pid<0||uid<0){formatError=true;return;}
            Integer expectedUid=owned.get(actualDisplay);if(expectedUid==null||uid!=expectedUid)return;
            if(windows.size()>=MAX_WINDOWS){windowLimit=true;return;}
            Window window=new Window();window.windowId=id;window.displayId=actualDisplay;window.ownerPid=pid;window.ownerUid=uid;window.frame=new int[]{Integer.parseInt(m.group(5)),Integer.parseInt(m.group(6)),Integer.parseInt(m.group(7)),Integer.parseInt(m.group(8))};
            window.touchRegion=region(m.group(10));window.inputConfigCode=config(m.group(4));
            for(String flag:FLAGS){Long mask=constants.get(flag);window.flags.put(flag,window.inputConfigCode==null||mask==null?null:(window.inputConfigCode&mask)!=0);}
            window.visible=inverse(window.flags.get("NOT_VISIBLE"));window.touchable=inverse(window.flags.get("NOT_TOUCHABLE"));window.focusable=inverse(window.flags.get("NOT_FOCUSABLE"));windows.add(window);
        }catch(NumberFormatException invalid){formatError=true;}
    }
    private static Boolean inverse(Boolean value){return value==null?null:!value;}
    private Long config(String text){
        if(text==null||text.isEmpty()||text.length()>2048)return null;text=text.trim();if(text.startsWith("{")&&text.endsWith("}"))text=text.substring(1,text.length()-1).trim();if(text.equals("0")||text.equals("0x0"))return 0L;long value=0;
        for(String token:text.split(" \\| ",-1)){
            if(token.matches("0x[0-9a-fA-F]{1,8}")){value|=Long.parseLong(token.substring(2),16);continue;}
            if(!token.matches("[A-Z][A-Z0-9_]{0,63}"))return null;Long mask=constants.get(token);if(mask==null)return null;value|=mask;
        }
        return value&0xffffffffL;
    }
    private static List<int[]> region(String text){
        List<int[]> boxes=new ArrayList<>();if("<empty>".equals(text))return boxes;if(text==null||text.length()>4096)return null;
        int end=0;Matcher matcher=REGION.matcher(text);while(matcher.find()){
            if((boxes.isEmpty()?matcher.start()!=0:!(matcher.start()==end+1&&text.charAt(end)=='|'))||boxes.size()>=8)return null;
            try{boxes.add(new int[]{Integer.parseInt(matcher.group(1)),Integer.parseInt(matcher.group(2)),Integer.parseInt(matcher.group(3)),Integer.parseInt(matcher.group(4))});}catch(NumberFormatException invalid){return null;}end=matcher.end();
        }
        return end==text.length()?boxes:null;
    }
}
