package fr.phonemcp;

import android.app.KeyguardManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.PowerManager;
import android.os.SystemClock;
import org.json.JSONObject;
import java.util.*;

/** Moves an already-owned task itself. It never launches, recreates or removes an app task. */
final class TaskHandoff {
    private static boolean flag(Object value,String name){try{return value.getClass().getField(name).getBoolean(value);}catch(Exception absent){return false;}}
    private static TaskHandoffPolicy.Task read(Object value)throws Exception{
        Class<?> type=value.getClass();ComponentName base=(ComponentName)type.getField("baseActivity").get(value),top=(ComponentName)type.getField("topActivity").get(value);
        return new TaskHandoffPolicy.Task(type.getField("taskId").getInt(value),type.getField("displayId").getInt(value),type.getField("numActivities").getInt(value),base==null?null:base.getPackageName(),top==null?null:top.getPackageName(),(int[])type.getField("childTaskIds").get(value),(String[])type.getField("childTaskNames").get(value),flag(value,"isFocused"),flag(value,"isVisible"));
    }
    private static List<TaskHandoffPolicy.Task> inventory(Class<?> api,Object manager)throws Exception{
        Object value=api.getMethod("getAllRootTaskInfos").invoke(manager);if(!(value instanceof List<?>))throw new IllegalStateException("Inventaire des fenêtres indisponible.");
        List<TaskHandoffPolicy.Task> rows=new ArrayList<>();for(Object item:(List<?>)value)rows.add(read(item));return rows;
    }
    private static void requireLocalUser(Context context){
        if(context==null||android.os.Process.myUid()!=2000||!context.getSystemService(PowerManager.class).isInteractive()||context.getSystemService(KeyguardManager.class).isKeyguardLocked())throw new SecurityException("Déverrouillez le téléphone pour reprendre cette page.");
    }
    static JSONObject move(Context context,String pkg,int sourceDisplay,Set<Integer> ownedTaskIds)throws Exception{
        requireLocalUser(context);if(sourceDisplay<=0||ownedTaskIds==null||ownedTaskIds.isEmpty())throw new SecurityException("Aucune fenêtre IA possédée à transférer.");
        Set<Integer> owned=new HashSet<>(ownedTaskIds);
        Class<?> api=Class.forName("android.app.IActivityTaskManager");Object manager=Class.forName("android.app.ActivityTaskManager").getMethod("getService").invoke(null);
        int selected=TaskHandoffPolicy.select(pkg,sourceDisplay,owned,inventory(api,manager));
        if(selected<0)throw new SecurityException("La fenêtre à reprendre est absente ou ambiguë. Le contrôle IA reste arrêté.");
        // Validate the current visible/focused selection again immediately before the only mutation.
        requireLocalUser(context);
        if(TaskHandoffPolicy.select(pkg,sourceDisplay,owned,inventory(api,manager))!=selected)throw new SecurityException("La fenêtre a changé avant sa reprise. Le contrôle IA reste arrêté.");
        api.getMethod("moveRootTaskToDisplay",int.class,int.class).invoke(manager,selected,0);
        long until=SystemClock.elapsedRealtime()+1500;
        do{
            for(TaskHandoffPolicy.Task task:inventory(api,manager))if(TaskHandoffPolicy.arrived(pkg,selected,task)){
                TaskCleanup.forgetTransferred(pkg,selected);
                return new JSONObject().put("handedOff",true).put("taskId",selected).put("sourceDisplayId",sourceDisplay).put("displayId",0).put("appRelaunched",false).put("taskPreserved",true);
            }
            Thread.sleep(25);
        }while(SystemClock.elapsedRealtime()<until);
        throw new IllegalStateException("Le transfert de cette fenêtre n’est pas confirmé. Le contrôle IA reste arrêté ; aucune application n’a été relancée.");
    }
}