package fr.phonemcp;

import org.json.JSONArray;
import org.json.JSONObject;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;

/** On-demand privacy-filtered input-window metadata; never injects or stores raw dumps. */
final class InputWindowDiagnostics {
    static JSONObject sample(Map<Integer,Integer> ownedDisplayUids)throws Exception{
        Map<Integer,Integer> owned=new LinkedHashMap<>();if(ownedDisplayUids!=null)for(Map.Entry<Integer,Integer> entry:ownedDisplayUids.entrySet()){if(owned.size()>=4)break;if(entry.getKey()!=null&&entry.getValue()!=null&&entry.getKey()>0&&entry.getValue()>=0)owned.put(entry.getKey(),entry.getValue());}
        if(owned.isEmpty())return new JSONObject().put("available",true).put("skipped",true).put("windows",new JSONArray());
        Map<String,Long> constants=new LinkedHashMap<>();String constantsError=null;
        try{for(Field field:Class.forName("android.os.InputConfig").getFields())if(field.getType()==int.class&&Modifier.isStatic(field.getModifiers())&&Modifier.isFinal(field.getModifiers())&&field.getName().matches("[A-Z][A-Z0-9_]{0,63}"))constants.put(field.getName(),field.getInt(null)&0xffffffffL);}catch(Exception unavailable){constantsError=unavailable.getClass().getSimpleName();}
        InputWindowDumpPolicy parser=new InputWindowDumpPolicy(owned,constants);BoundedDumpReader.Result read=BoundedDumpReader.run("input",2*1024*1024,1200,parser::line);
        boolean available=read.completed&&parser.available();JSONArray windows=new JSONArray();
        if(available)for(InputWindowDumpPolicy.Window window:parser.windows){
            JSONObject flags=new JSONObject();for(Map.Entry<String,Boolean> entry:window.flags.entrySet())flags.put(entry.getKey(),entry.getValue()==null?JSONObject.NULL:entry.getValue());
            JSONArray region=null;if(window.touchRegion!=null){region=new JSONArray();for(int[] rect:window.touchRegion)region.put(new JSONArray(rect));}
            windows.put(new JSONObject().put("displayId",window.displayId).put("windowId",window.windowId>=0?window.windowId:JSONObject.NULL).put("ownerUid",window.ownerUid).put("ownerPid",window.ownerPid).put("visible",window.visible==null?JSONObject.NULL:window.visible).put("touchable",window.touchable==null?JSONObject.NULL:window.touchable).put("focusable",window.focusable==null?JSONObject.NULL:window.focusable).put("inputConfigCode",window.inputConfigCode==null?JSONObject.NULL:window.inputConfigCode).put("inputFlags",flags).put("bounds",new JSONArray(window.frame)).put("touchRegion",region==null?JSONObject.NULL:region));
        }
        String error=read.errorCode;if(error.isEmpty()&&!parser.available())error=parser.windowLimit?"window_limit":"format_unrecognized";
        return new JSONObject().put("available",available).put("source","input_dispatcher_dump").put("errorCode",error.isEmpty()?JSONObject.NULL:error).put("inputConfigConstantsError",constantsError==null?JSONObject.NULL:constantsError).put("timedOut",read.timedOut).put("sizeLimited",read.sizeLimited||read.lineTooLong).put("windows",windows);
    }
}
