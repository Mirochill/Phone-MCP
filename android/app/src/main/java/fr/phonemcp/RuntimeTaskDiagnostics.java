package fr.phonemcp;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.List;

/** One bounded task-service read on explicit diagnostics, no lifecycle/input mutation. */
final class RuntimeTaskDiagnostics {
    static JSONArray sample(List<RuntimeTaskPolicy.Target> targets)throws Exception{
        JSONArray output=new JSONArray();if(targets==null||targets.isEmpty())return output;
        List<?> inventory=null;String errorClass=null;
        try{
            Class<?> api=Class.forName("android.app.IActivityTaskManager");Object service=Class.forName("android.app.ActivityTaskManager").getMethod("getService").invoke(null);
            Object raw=api.getMethod("getTasks",int.class,boolean.class,boolean.class,int.class).invoke(service,RuntimeTaskPolicy.INVENTORY_LIMIT,false,false,-1);
            if(!(raw instanceof List<?>))throw new IllegalStateException();inventory=(List<?>)raw;
        }catch(Exception unavailable){Throwable cause=unavailable;for(int depth=0;depth<8&&cause instanceof java.lang.reflect.InvocationTargetException&&cause.getCause()!=null;depth++)cause=cause.getCause();errorClass=cause.getClass().getSimpleName();}
        for(RuntimeTaskPolicy.Result value:RuntimeTaskPolicy.sample(targets,inventory,errorClass)){
            JSONObject item=new JSONObject().put("taskId",value.target.taskId).put("displayId",value.target.displayId).put("found",value.found==null?JSONObject.NULL:value.found).put("available",value.available).put("errorClass",value.errorClass==null?JSONObject.NULL:value.errorClass);
            for(java.util.Map.Entry<String,Object> field:value.fields.entrySet())item.put(field.getKey(),field.getValue()==null?JSONObject.NULL:field.getValue());output.put(item);
        }
        return output;
    }
}
