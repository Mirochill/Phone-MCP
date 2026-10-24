package fr.phonemcp;

import android.content.*;
import android.hardware.display.DisplayManager;
import android.os.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Persist tasks observed on owned AI displays; never remove live/shared app contexts. */
final class TaskCleanup {
    private static final int LIVE_TASK_LIMIT=512;
    private static final Map<Integer,String> known=new LinkedHashMap<>();
    private static Context context;private static File ledger;private static String error="",inventoryError="";
    private static int deferredOtherLiveTaskCount,deferredInventoryCount,removeTaskRequestCount,removedTaskCount;
    private static long lastRemoveTaskRequestAtMs;
    private static final class Inventory {
        final List<TaskCleanupPolicy.Task> tasks;final boolean complete;
        Inventory(List<TaskCleanupPolicy.Task> tasks,boolean complete){this.tasks=tasks;this.complete=complete;}
    }
    static synchronized void initialize(Context app){
        context=app;File root=new File("/data/local/tmp/phone-mcp-"+app.getApplicationInfo().uid);
        try{if(!root.isDirectory()||Files.isSymbolicLink(root.toPath()))throw new SecurityException("Invalid task ledger directory");ledger=new File(root,"owned-ai-tasks.json");
            if(ledger.isFile()){if(Files.isSymbolicLink(ledger.toPath())||ledger.length()>65536)throw new SecurityException("Invalid task ledger");JSONArray list=new JSONArray(new String(Files.readAllBytes(ledger.toPath()),StandardCharsets.UTF_8));for(int i=0;i<Math.min(256,list.length());i++){JSONObject item=list.getJSONObject(i);known.put(item.getInt("taskId"),item.getString("packageName"));}}
        }catch(Exception failure){error=failure.getClass().getSimpleName();}
    }
    static synchronized void record(String pkg,int task,int display){if(display<=0||known.containsKey(task))return;known.put(task,pkg);while(known.size()>256)known.remove(known.keySet().iterator().next());save();}
    /** A confirmed handoff is no longer an AI-owned cleanup target. */
    static synchronized void forgetTransferred(String pkg,int taskId){if(pkg!=null&&pkg.equals(known.get(taskId))){known.remove(taskId);save();}}
    private static synchronized void save(){if(ledger==null)return;try{JSONArray list=new JSONArray();for(Map.Entry<Integer,String> e:known.entrySet())list.put(new JSONObject().put("taskId",e.getKey()).put("packageName",e.getValue()));Path temporary=ledger.toPath().resolveSibling("owned-ai-tasks.tmp");Files.write(temporary,list.toString().getBytes(StandardCharsets.UTF_8));Files.move(temporary,ledger.toPath(),StandardCopyOption.REPLACE_EXISTING);error="";}catch(Exception failure){error=failure.getClass().getSimpleName();}}
    static JSONObject clean(String pkg,JSONArray legacy)throws Exception{
        Map<Integer,String> targets; synchronized(TaskCleanup.class){targets=new LinkedHashMap<>(known);}
        if(pkg!=null)targets.entrySet().removeIf(e->!pkg.equals(e.getValue()));
        if(legacy!=null){if(pkg==null||legacy.length()>64)throw new IllegalArgumentException("Bounded app task list required");for(int i=0;i<legacy.length();i++){int id=legacy.getInt(i);if(id<=0)throw new IllegalArgumentException("Invalid task identifier");targets.put(id,pkg);}}
        JSONArray removed=new JSONArray(),skipped=new JSONArray();if(targets.isEmpty())return new JSONObject().put("removed",removed).put("skipped",skipped);
        Class<?> api=Class.forName("android.app.IActivityTaskManager");Object manager=Class.forName("android.app.ActivityTaskManager").getMethod("getService").invoke(null);
        Object slice=api.getMethod("getRecentTasks",int.class,int.class,int.class).invoke(manager,256,1,android.os.Process.myUid()/100000);
        java.util.List<?> recent=(java.util.List<?>)slice.getClass().getMethod("getList").invoke(slice);
        Set<Integer> found=new HashSet<>();Inventory initialInventory=null;
        for(Object item:recent){
            Class<?> type=item.getClass();int id=type.getField("taskId").getInt(item);String expected=targets.get(id);if(expected==null)continue;found.add(id);
            Intent base=(Intent)type.getField("baseIntent").get(item);String actual=base==null||base.getComponent()==null?"":base.getComponent().getPackageName();int display=type.getField("displayId").getInt(item),activities=type.getField("numActivities").getInt(item);
            boolean valid=display>=0&&context.getSystemService(DisplayManager.class).getDisplay(display)!=null;
            if(!TaskCleanupPolicy.mayRemove(expected,actual,activities,display,valid)){skipped.put(new JSONObject().put("taskId",id).put("reason","active_or_physical_task"));continue;}
            if(initialInventory==null)initialInventory=runningInventory(api,manager);
            TaskCleanupPolicy.SharedState shared=TaskCleanupPolicy.sharedState(expected,id,initialInventory.tasks,initialInventory.complete);
            if(shared!=TaskCleanupPolicy.SharedState.CLEAR){defer(skipped,id,shared);continue;}
            // removeTask can notify package services even when Android subsequently spares its
            // process. Check all live tasks again, across all displays, before the final ID check.
            Inventory latest=runningInventory(api,manager);
            shared=TaskCleanupPolicy.sharedState(expected,id,latest.tasks,latest.complete);
            if(shared!=TaskCleanupPolicy.SharedState.CLEAR){defer(skipped,id,shared);continue;}
            // Final exact task re-read: preserve any recently reopened physical or active target.
            Object freshSlice=api.getMethod("getRecentTasks",int.class,int.class,int.class).invoke(manager,256,1,android.os.Process.myUid()/100000);boolean safe=false;
            for(Object fresh:(java.util.List<?>)freshSlice.getClass().getMethod("getList").invoke(freshSlice))if(type.getField("taskId").getInt(fresh)==id){Intent freshBase=(Intent)type.getField("baseIntent").get(fresh);String freshPkg=freshBase==null||freshBase.getComponent()==null?"":freshBase.getComponent().getPackageName();int freshDisplay=type.getField("displayId").getInt(fresh);safe=TaskCleanupPolicy.mayRemove(expected,freshPkg,type.getField("numActivities").getInt(fresh),freshDisplay,freshDisplay>=0&&context.getSystemService(DisplayManager.class).getDisplay(freshDisplay)!=null);break;}
            if(safe){
                synchronized(TaskCleanup.class){removeTaskRequestCount=increment(removeTaskRequestCount);lastRemoveTaskRequestAtMs=System.currentTimeMillis();}
                if(Boolean.TRUE.equals(api.getMethod("removeTask",int.class).invoke(manager,id))){removed.put(id);synchronized(TaskCleanup.class){known.remove(id);removedTaskCount=increment(removedTaskCount);}}
            }
        }
        // A capped recents response cannot prove that an unreturned tracked ID disappeared.
        synchronized(TaskCleanup.class){if(recent.size()<256)for(int id:targets.keySet())if(!found.contains(id))known.remove(id);save();}
        return new JSONObject().put("removed",removed).put("skipped",skipped);
    }
    private static Inventory runningInventory(Class<?> api,Object manager){
        try{
            Object raw=api.getMethod("getTasks",int.class,boolean.class,boolean.class,int.class).invoke(manager,LIVE_TASK_LIMIT,false,false,-1);
            if(!(raw instanceof java.util.List<?>))throw new IllegalStateException("Running task inventory unavailable");
            java.util.List<?> list=(java.util.List<?>)raw;List<TaskCleanupPolicy.Task> tasks=new ArrayList<>();
            for(Object item:list){Class<?> type=item.getClass();android.content.ComponentName base=(android.content.ComponentName)type.getField("baseActivity").get(item),top=(android.content.ComponentName)type.getField("topActivity").get(item);
                tasks.add(new TaskCleanupPolicy.Task(type.getField("taskId").getInt(item),type.getField("displayId").getInt(item),type.getField("numActivities").getInt(item),base==null?null:base.getPackageName(),top==null?null:top.getPackageName()));}
            synchronized(TaskCleanup.class){inventoryError="";}
            return new Inventory(tasks,list.size()<LIVE_TASK_LIMIT);
        }catch(Exception failure){synchronized(TaskCleanup.class){inventoryError=failure.getClass().getSimpleName();}return new Inventory(Collections.emptyList(),false);}
    }
    private static void defer(JSONArray skipped,int id,TaskCleanupPolicy.SharedState reason)throws Exception{
        synchronized(TaskCleanup.class){if(reason==TaskCleanupPolicy.SharedState.OTHER_LIVE_APP_TASK)deferredOtherLiveTaskCount=increment(deferredOtherLiveTaskCount);else deferredInventoryCount=increment(deferredInventoryCount);}
        skipped.put(new JSONObject().put("taskId",id).put("reason",reason==TaskCleanupPolicy.SharedState.OTHER_LIVE_APP_TASK?"other_live_app_task":"task_inventory_unconfirmed"));
    }
    private static int increment(int value){return value==Integer.MAX_VALUE?value:value+1;}
    static void reap(){try{clean(null,null);}catch(Exception failure){error=failure.getClass().getSimpleName();}}
    static synchronized JSONObject state(){JSONObject value=new JSONObject();try{value.put("trackedTaskCount",known.size()).put("error",error).put("taskInventoryError",inventoryError)
            .put("deferredOtherLiveTaskCount",deferredOtherLiveTaskCount).put("deferredInventoryCount",deferredInventoryCount)
            .put("removeTaskRequestCount",removeTaskRequestCount).put("removedTaskCount",removedTaskCount).put("lastRemoveTaskRequestAtMs",lastRemoveTaskRequestAtMs);
        }catch(Exception ignored){}return value;}
}
