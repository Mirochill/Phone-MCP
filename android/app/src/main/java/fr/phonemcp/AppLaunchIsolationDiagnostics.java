package fr.phonemcp;

import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded AM receipts. Raw output may contain private Intent data and must never be retained. */
final class AppLaunchIsolationDiagnostics {
    static Map<String,Object> receipt(String raw,int exit,long elapsed,boolean timedOut,String pkg){
        Map<String,Object> out=new LinkedHashMap<>();out.put("exitCode",exit);
        out.put("elapsedMs",Math.max(0,elapsed));out.put("timedOut",timedOut);
        String input=raw==null?"":raw.substring(0,Math.min(raw.length(),8192));
        boolean refused=false;
        for(String line:input.split("\\r?\\n")){String value=line.trim();
            if(value.startsWith("Error:")||value.startsWith("Exception occurred while executing")
                    ||value.matches("[A-Za-z0-9_.$]*Exception:.*"))refused=true;
        }
        out.put("outcome",timedOut?"timeout":exit!=0||refused?"refused":"returned");
        for(String line:input.split("\\r?\\n")){
            int separator=line.indexOf(':');if(separator<0)continue;
            String key=line.substring(0,separator).trim(),value=line.substring(separator+1).trim();
            if("Status".equals(key)&&("ok".equals(value)||"timeout".equals(value)))out.put("status",value);
            else if("LaunchState".equals(key)&&value.matches("COLD|WARM|HOT|UNKNOWN"))out.put("launchState",value);
            else if("Activity".equals(key)&&safeComponent(value))out.put("activity",value.startsWith(pkg+"/")||value.startsWith("fr.phonemcp/")?value:"other_package");
            else if(("TotalTime".equals(key)||"WaitTime".equals(key)||"ThisTime".equals(key))&&value.matches("[0-9]{1,8}"))
                out.put(Character.toLowerCase(key.charAt(0))+key.substring(1)+"Ms",Long.parseLong(value));
        }
        if(input.contains("top-most instance"))out.put("warning","delivered_to_top");
        else if(input.contains("task has been brought to the front"))out.put("warning","task_to_front");
        else if(input.contains("current activity is being kept"))out.put("warning","switch_canceled");
        if("timeout".equals(out.get("status"))){out.put("outcome","timeout");out.put("timedOut",true);}
        return out;
    }
    static boolean safeComponent(String value){return value!=null&&value.length()<=256&&value.matches("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+/[A-Za-z0-9_.$]+");}
    private AppLaunchIsolationDiagnostics(){}
}

