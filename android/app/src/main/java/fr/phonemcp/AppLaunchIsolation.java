package fr.phonemcp;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Process;
import android.os.SystemClock;
import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Shell-only fresh-task launch. The installed OEM is first checked using two neutral singleTask
 * Phone MCP instances on the AI display. No cross-UID bootstrap, CLEAR_TASK or human-task takeover.
 * Raw AM/Intent output and nonce values are never retained in the diagnostic returned to agents.
 */
@android.annotation.TargetApi(34)
@SuppressLint({"PrivateApi","DiscouragedPrivateApi"})
final class AppLaunchIsolation {
    interface Check { void run() throws Exception; }
    private static final Object PROBE_LOCK=new Object();
    private static boolean probeVerified,probeRefused;
    private static JSONObject proof=new JSONObject();
    private static final int TASK_LIMIT=4,TRANSITION_LIMIT=6;
    private static final class OwnedTask {
        final int id;final String nonce;
        OwnedTask(int id,String nonce){this.id=id;this.nonce=nonce;}
    }
    private final Map<String,OwnedTask> ownedTasks=new HashMap<>();
    private final AppLaunchIsolationPolicy.AttemptGate attempts=new AppLaunchIsolationPolicy.AttemptGate();
    private volatile JSONObject lastLaunch=new JSONObject();
    void clear(){ownedTasks.clear();attempts.clear();}
    int ownedTaskId(String pkg){OwnedTask owned=ownedTasks.get(pkg);return owned==null?-1:owned.id;}
    static boolean required(int launchMode){return AppLaunchIsolationPolicy.requiresOwnedTask(launchMode);}
    static String strategy(){return AppLaunchIsolationPolicy.STRATEGY;}
    JSONObject diagnostics(){try{return new JSONObject(lastLaunch.toString());}catch(Exception ignored){return new JSONObject();}}

    int launch(Intent target,int display,Check check)throws Exception{
        if(Process.myUid()!=2000||display<=0)throw new SecurityException("An off-screen Shizuku shell launch is required");
        ComponentName component=target==null?null:target.getComponent();
        if(component==null)throw new SecurityException("An explicit app activity is required");
        String pkg=component.getPackageName();check.run();
        if(!attempts.maySubmit(pkg)){
            lastLaunch.put("repeatBlocked",true).put("blockedPackage",pkg);
            throw new IllegalStateException("Une ouverture précédente de cette app est restée incertaine. Relisez son état ou terminez cette session puis créez-en explicitement une nouvelle avant de réessayer.");
        }
        JSONObject report=new JSONObject().put("requestedDisplayId",display).put("strategy",strategy())
                .put("target",targetProfile(component)).put("state","starting");lastLaunch=report;
        Trace trace=new Trace(pkg,display,report);
        Set<Integer> before=new HashSet<>();String nonce=null;boolean submitted=false;int observed=-1;
        try{
            ensureFreshTaskSupport(display,check,trace);
            check.run();List<AppLaunchIsolationPolicy.Task> prior=snapshot();
            for(AppLaunchIsolationPolicy.Task task:prior)before.add(task.id);
            report.put("tasksBefore",diagnosticTasks(prior,pkg,display));trace.capture(prior);
            OwnedTask owned=ownedTasks.get(pkg);
            if(owned!=null){
                report.put("reservedTaskId",owned.id).put("reusingOwnedTask",true);
                if(!AppLaunchIsolationPolicy.isOwnedAppTask(find(prior,owned.id),owned.id,display,pkg,owned.nonce))
                    throw new IllegalStateException("La tâche de cette session n’est plus sur son écran séparé. Aucun lancement de remplacement n’a été effectué.");
                check.run();report.put("phase","launch_existing");attempts.submitted(pkg);submitted=true;nonce=owned.nonce;
                start(AppLaunchIsolationPolicy.existingCommand(display,Process.myUid()/100000,owned.id,
                        component.flattenToString(),target.getAction(),target.getDataString(),target.getCategories(),nonce),
                        pkg,"targetReceipt",trace,check);
                observed=awaitTask(before,display,pkg,nonce,owned.id,false,check,trace);
            }else{
                nonce=UUID.randomUUID().toString();check.run();report.put("phase","launch_fresh").put("reusingOwnedTask",false);
                attempts.submitted(pkg);submitted=true;
                start(AppLaunchIsolationPolicy.freshCommand(display,Process.myUid()/100000,
                        component.flattenToString(),target.getAction(),target.getDataString(),target.getCategories(),nonce,true),
                        pkg,"targetReceipt",trace,check);
                observed=awaitTask(before,display,pkg,nonce,-1,false,check,trace);
            }
            ownedTasks.put(pkg,new OwnedTask(observed,nonce));TaskCleanup.record(pkg,observed,display);
            attempts.confirmed(pkg);report.put("state","confirmed").put("ownedTaskId",observed).put("correlationConfirmed",true);return observed;
        }catch(Exception error){report.put("state",submitted?"unconfirmed":"not_submitted");throw error;}
        finally{
            // An uncertain return must not lose a verified fresh resource from the cleanup ledger.
            // This records only a matching nonce + new ID + true target identity on this display.
            if(submitted&&observed<0&&nonce!=null)try{
                for(AppLaunchIsolationPolicy.Task task:snapshot())if(AppLaunchIsolationPolicy.mayAcceptFreshTask(task,before,display,pkg,nonce)){
                    ownedTasks.put(pkg,new OwnedTask(task.id,nonce));TaskCleanup.record(pkg,task.id,display);break;
                }
            }catch(Exception ignored){}
            finishDiagnostic(report,pkg,display,trace);
        }
    }
    private static void ensureFreshTaskSupport(int display,Check check,Trace trace)throws Exception{
        synchronized(PROBE_LOCK){
            if(probeVerified){trace.report.put("autoCheck",new JSONObject(proof.toString()).put("cached",true));return;}
            if(probeRefused){trace.report.put("autoCheck",new JSONObject(proof.toString()).put("cached",true));
                throw new IllegalStateException("Android n’a pas validé la création indépendante d’instances. Aucune application tierce n’a été relancée ni déplacée.");}
            check.run();ComponentName component=ComponentName.unflattenFromString(AppLaunchIsolationPolicy.PROBE_COMPONENT);
            JSONObject local=new JSONObject().put("cached",false).put("state","checking");trace.report.put("autoCheck",local);
            ActivityInfo info=activityInfo(component);
            if(info==null||info.launchMode!=2){local.put("state","refused").put("reason","probe_is_not_singleTask");proof=local;probeRefused=true;
                throw new IllegalStateException("La sonde d’isolation doit être déclarée singleTask avant le contrôle d’apps déjà ouvertes.");}
            List<AppLaunchIsolationPolicy.Task> prior=snapshot();Set<Integer> before=new HashSet<>();
            for(AppLaunchIsolationPolicy.Task task:prior){before.add(task.id);
                if(AppLaunchIsolationPolicy.PROBE_COMPONENT.equals(task.intentComponent)||AppLaunchIsolationPolicy.PROBE_COMPONENT.equals(task.baseComponent)){
                    local.put("state","refused").put("reason","preexisting_neutral_probe");
                    throw new IllegalStateException("Une sonde neutre précédente n’est pas encore terminée. Aucune application tierce n’a été lancée.");
                }
            }
            String firstNonce=UUID.randomUUID().toString(),secondNonce=UUID.randomUUID().toString();int first=-1,second=-1;boolean passed=false;
            try{
                trace.report.put("phase","verify_oem_first_instance");
                start(AppLaunchIsolationPolicy.freshCommand(display,Process.myUid()/100000,AppLaunchIsolationPolicy.PROBE_COMPONENT,
                        Intent.ACTION_MAIN,null,null,firstNonce,false),"fr.phonemcp","firstProbeReceipt",trace,check);
                first=awaitTask(before,display,"fr.phonemcp",firstNonce,-1,true,check,trace);
                trace.report.put("phase","verify_oem_second_instance");
                start(AppLaunchIsolationPolicy.freshCommand(display,Process.myUid()/100000,AppLaunchIsolationPolicy.PROBE_COMPONENT,
                        Intent.ACTION_MAIN,null,null,secondNonce,false),"fr.phonemcp","secondProbeReceipt",trace,check);
                second=awaitTask(before,display,"fr.phonemcp",secondNonce,-1,true,check,trace);
                List<AppLaunchIsolationPolicy.Task> now=snapshot();
                if(!AppLaunchIsolationPolicy.probePair(find(now,first),find(now,second),before,display,firstNonce,secondNonce))
                    throw new IllegalStateException("Les deux instances de la sonde ne sont pas restées indépendantes.");
                local.put("firstTaskId",first).put("secondTaskId",second).put("verifiedDisplayId",display).put("correlationConfirmed",true);
                passed=true;
            }catch(InterruptedException cancelled){throw cancelled;}
            catch(Exception failure){local.put("state","refused").put("reason","fresh_singleTask_identity_not_confirmed");proof=local;probeRefused=true;throw failure;}
            finally{
                boolean cleanFirst=removeProbe(first,before,display,firstNonce),cleanSecond=removeProbe(second,before,display,secondNonce);
                local.put("probeTasksCleaned",cleanFirst&&cleanSecond);
                if(passed&&(!cleanFirst||!cleanSecond)){passed=false;local.put("state","refused").put("reason","neutral_probe_cleanup_not_confirmed");}
            }
            if(!passed){proof=local;probeRefused=true;throw new IllegalStateException("Le nettoyage des sondes neutres n’a pas été confirmé. Aucune application tierce n’a été lancée.");}
            local.put("state","passed").put("verifiedAtElapsedMs",SystemClock.elapsedRealtime());proof=local;probeVerified=true;
        }
    }
    private static boolean removeProbe(int id,Set<Integer> before,int display,String nonce){
        try{
            List<AppLaunchIsolationPolicy.Task> values=snapshot();
            if(id<=0){for(AppLaunchIsolationPolicy.Task task:values)if(AppLaunchIsolationPolicy.mayRemoveProbe(task,before,display,nonce)){id=task.id;break;}}
            if(id<=0)return true;
            AppLaunchIsolationPolicy.Task fresh=find(snapshot(),id);
            if(fresh==null)return true;
            if(!AppLaunchIsolationPolicy.mayRemoveProbe(fresh,before,display,nonce))return false;
            Class<?> api=Class.forName("android.app.IActivityTaskManager");
            return Boolean.TRUE.equals(api.getMethod("removeTask",int.class).invoke(service(),id));
        }catch(Exception ignored){return false;}
    }
    private static int awaitTask(Set<Integer> before,int display,String pkg,String nonce,int existing,
            boolean probe,Check check,Trace trace)throws Exception{
        long until=SystemClock.elapsedRealtime()+1500;
        do{
            check.run();List<AppLaunchIsolationPolicy.Task> values=snapshot();trace.capture(values);
            int match=-1;
            for(AppLaunchIsolationPolicy.Task task:values){
                boolean valid=existing>0?AppLaunchIsolationPolicy.isOwnedAppTask(task,existing,display,pkg,nonce)
                        :probe?AppLaunchIsolationPolicy.mayRemoveProbe(task,before,display,nonce)
                        :AppLaunchIsolationPolicy.mayAcceptFreshTask(task,before,display,pkg,nonce);
                if(valid){if(match>0&&match!=task.id)throw new SecurityException("La corrélation de la tâche n’est pas unique.");match=task.id;}
            }
            if(match>0)return match;
            Thread.sleep(40);
        }while(SystemClock.elapsedRealtime()<until);
        throw new IllegalStateException("L’ouverture indépendante n’a pas été confirmée sur une nouvelle tâche de l’écran séparé. Relisez l’état ; aucun lancement n’est répété automatiquement.");
    }
    private static void start(List<String> command,String pkg,String receiptKey,Trace trace,Check check)throws Exception{
        long began=SystemClock.elapsedRealtime();java.lang.Process child=new ProcessBuilder(command).redirectErrorStream(true).start();
        try{
            while(!child.waitFor(100,TimeUnit.MILLISECONDS)){
                check.run();trace.sample();
                if(SystemClock.elapsedRealtime()-began>=4000){
                    child.destroyForcibly();trace.report.put(receiptKey,new JSONObject(AppLaunchIsolationDiagnostics.receipt("",-1,SystemClock.elapsedRealtime()-began,true,pkg)));
                    throw new IllegalStateException("Le résultat de l’ouverture isolée est incertain. Relisez l’état sans répéter automatiquement cette ouverture.");
                }
            }
            String output=new String(child.getInputStream().readNBytes(8192),StandardCharsets.UTF_8);
            Map<String,Object> receipt=AppLaunchIsolationDiagnostics.receipt(output,child.exitValue(),SystemClock.elapsedRealtime()-began,false,pkg);
            trace.report.put(receiptKey,new JSONObject(receipt));trace.sample();
            if(!"returned".equals(receipt.get("outcome")))throw new IllegalStateException("Android a refusé l’ouverture de l’activité dans la tâche isolée.");
        }catch(InterruptedException cancelled){child.destroyForcibly();Thread.currentThread().interrupt();throw cancelled;}
        catch(Exception error){if(child.isAlive())child.destroyForcibly();throw error;}
    }
    private static ActivityInfo activityInfo(ComponentName component)throws Exception{
        Object pm=Class.forName("android.app.AppGlobals").getMethod("getPackageManager").invoke(null);
        return (ActivityInfo)Class.forName("android.content.pm.IPackageManager").getMethod("getActivityInfo",ComponentName.class,long.class,int.class)
                .invoke(pm,component,0L,Process.myUid()/100000);
    }
    private static JSONObject targetProfile(ComponentName component)throws Exception{
        JSONObject out=new JSONObject().put("component",component.flattenToString());
        try{ActivityInfo info=activityInfo(component);
            if(info!=null){out.put("name",bounded(info.name)).put("targetActivity",info.targetActivity==null?JSONObject.NULL:bounded(info.targetActivity))
                    .put("launchMode",info.launchMode).put("documentLaunchMode",info.documentLaunchMode)
                    .put("taskAffinity",info.taskAffinity==null?JSONObject.NULL:bounded(info.taskAffinity)).put("exported",info.exported).put("enabled",info.enabled);
                try{out.put("resizeMode",ActivityInfo.class.getField("resizeMode").getInt(info));}catch(Exception ignored){}}
        }catch(Exception error){out.put("profileError",error.getClass().getSimpleName());}return out;
    }
    private static final class Trace {
        final String pkg;final int display;final JSONObject report;final long began=SystemClock.elapsedRealtime();
        final JSONArray changes=new JSONArray();String previous="";int samples,dropped;
        Trace(String pkg,int display,JSONObject report){this.pkg=pkg;this.display=display;this.report=report;}
        void sample(){try{capture(snapshot());}catch(Exception ignored){}}
        void capture(List<AppLaunchIsolationPolicy.Task> values)throws Exception{
            samples++;JSONArray state=diagnosticTasks(values,pkg,display);String signature=state.toString();
            if(!previous.equals(signature)){previous=signature;
                if(changes.length()<TRANSITION_LIMIT)changes.put(new JSONObject().put("elapsedMs",SystemClock.elapsedRealtime()-began).put("tasks",state));else dropped++;}
        }
    }
    private static void finishDiagnostic(JSONObject report,String pkg,int display,Trace trace){
        try{List<AppLaunchIsolationPolicy.Task> values=snapshot();trace.capture(values);report.put("tasksAfter",diagnosticTasks(values,pkg,display))
                .put("taskTransitions",trace.changes).put("taskSamples",trace.samples).put("droppedTransitions",trace.dropped)
                .put("elapsedMs",SystemClock.elapsedRealtime()-trace.began).put("diagnosticTaskLimit",TASK_LIMIT);}
        catch(Exception error){try{report.put("diagnosticError",error.getClass().getSimpleName());}catch(Exception ignored){}}
    }
    private static JSONArray diagnosticTasks(List<AppLaunchIsolationPolicy.Task> values,String pkg,int display)throws Exception{
        JSONArray out=new JSONArray();
        for(AppLaunchIsolationPolicy.Task task:values){
            boolean target=AppLaunchIsolationPolicy.packageComponent(task.intentComponent,pkg)||AppLaunchIsolationPolicy.packageComponent(task.baseComponent,pkg)
                    ||AppLaunchIsolationPolicy.packageComponent(task.topComponent,pkg);
            boolean neutral=task.display==display&&(AppLaunchIsolationPolicy.PROBE_COMPONENT.equals(task.intentComponent)||AppLaunchIsolationPolicy.PROBE_COMPONENT.equals(task.baseComponent));
            if(!target&&!neutral)continue;if(out.length()>=TASK_LIMIT)break;
            out.put(new JSONObject().put("taskId",task.id).put("displayId",task.display).put("activities",task.activities)
                    .put("baseIntentActivity",task.intentComponent==null?JSONObject.NULL:bounded(task.intentComponent))
                    .put("baseActivity",task.baseComponent==null?JSONObject.NULL:bounded(task.baseComponent))
                    .put("topActivity",task.topComponent==null?JSONObject.NULL:bounded(task.topComponent))
                    .put("baseFlags",task.flags).put("visible",task.visible).put("correlationExtraPresent",AppLaunchIsolationPolicy.canonicalNonce(task.nonce)));
        }return out;
    }
    private static String bounded(String value){return value==null?"":value.substring(0,Math.min(value.length(),160));}
    private static Object service()throws Exception{return Class.forName("android.app.ActivityTaskManager").getMethod("getService").invoke(null);}
    private static List<AppLaunchIsolationPolicy.Task> snapshot()throws Exception{
        Class<?> api=Class.forName("android.app.IActivityTaskManager");
        Object raw=api.getMethod("getTasks",int.class,boolean.class,boolean.class,int.class).invoke(service(),512,false,true,-1);
        List<AppLaunchIsolationPolicy.Task> out=new ArrayList<>();
        for(Object value:(List<?>)raw){
            Class<?> type=value.getClass();Intent intent=(Intent)type.getField("baseIntent").get(value);
            ComponentName base=(ComponentName)type.getField("baseActivity").get(value),top=(ComponentName)type.getField("topActivity").get(value);
            ComponentName intentComponent=intent==null?null:intent.getComponent();
            boolean visible=false;try{visible=type.getField("isVisible").getBoolean(value);}catch(Exception ignored){}
            String nonce=null;try{nonce=intent==null?null:intent.getStringExtra(AppLaunchIsolationPolicy.NONCE_EXTRA);}catch(RuntimeException ignored){}
            out.add(new AppLaunchIsolationPolicy.Task(type.getField("taskId").getInt(value),type.getField("displayId").getInt(value),type.getField("numActivities").getInt(value),
                    intentComponent==null?null:intentComponent.flattenToString(),base==null?null:base.flattenToString(),top==null?null:top.flattenToString(),nonce,
                    intent==null?0:intent.getFlags(),visible));
        }return out;
    }
    private static AppLaunchIsolationPolicy.Task find(List<AppLaunchIsolationPolicy.Task> tasks,int id){for(AppLaunchIsolationPolicy.Task task:tasks)if(task.id==id)return task;return null;}
}

