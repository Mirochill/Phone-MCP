package fr.phonemcp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Fresh-task selection is enabled only after an off-screen two-instance singleTask self-check. */
final class AppLaunchIsolationPolicy {
    static final String PROBE_COMPONENT="fr.phonemcp/fr.phonemcp.AppLaunchIsolationActivity";
    static final String NONCE_EXTRA="fr.phonemcp.launchNonce";
    static final String STRATEGY="forced_owned_task";
    // Android's INVALID_TASK_ID is -1. -2 can never identify an existing positive task. AOSP keeps
    // this explicit non-matching selection through resolveReusableTask; the neutral probe verifies
    // that the installed OEM implementation really creates two new tasks instead of reusing one.
    static final int UNMATCHED_TASK_ID=-2;
    static final int NEW_TASK=0x10000000, MULTIPLE_TASK=0x08000000,
            EXCLUDE_FROM_RECENTS=0x00800000, NO_USER_ACTION=0x00040000;
    static final int NEW_FLAGS=NEW_TASK|MULTIPLE_TASK|EXCLUDE_FROM_RECENTS|NO_USER_ACTION;
    static final class Task {
        final int id,display,activities,flags;
        final boolean visible;
        final String intentComponent,baseComponent,topComponent,nonce;
        Task(int id,int display,int activities,String intentComponent,String baseComponent,
                String topComponent,String nonce,int flags,boolean visible){
            this.id=id;this.display=display;this.activities=activities;this.flags=flags;this.visible=visible;
            this.intentComponent=intentComponent;this.baseComponent=baseComponent;
            this.topComponent=topComponent;this.nonce=nonce;
        }
    }
    static boolean requiresOwnedTask(int launchMode){return launchMode==2||launchMode==3;}
    static boolean canonicalNonce(String value){
        try{return value!=null&&UUID.fromString(value).toString().equals(value);}catch(IllegalArgumentException error){return false;}
    }
    static boolean packageComponent(String component,String pkg){
        return component!=null&&pkg!=null&&component.startsWith(pkg+"/")&&component.length()>pkg.length()+1;
    }
    static boolean isOwnedAppTask(Task task,int ownedId,int display,String pkg,String nonce){
        return task!=null&&ownedId>0&&task.id==ownedId&&display>0&&task.display==display
                &&task.activities>0&&canonicalNonce(nonce)&&nonce.equals(task.nonce)
                &&packageComponent(task.intentComponent,pkg)&&packageComponent(task.baseComponent,pkg)
                &&packageComponent(task.topComponent,pkg);
    }
    static boolean mayAcceptFreshTask(Task task,Set<Integer> before,int display,String pkg,String nonce){
        return task!=null&&before!=null&&!before.contains(task.id)
                &&isOwnedAppTask(task,task.id,display,pkg,nonce);
    }
    static boolean mayRemoveProbe(Task task,Set<Integer> before,int display,String nonce){
        return mayAcceptFreshTask(task,before,display,"fr.phonemcp",nonce)&&task.activities==1
                &&PROBE_COMPONENT.equals(task.intentComponent)&&PROBE_COMPONENT.equals(task.baseComponent)
                &&PROBE_COMPONENT.equals(task.topComponent);
    }
    static boolean probePair(Task first,Task second,Set<Integer> before,int display,String firstNonce,String secondNonce){
        return first!=null&&second!=null&&first.id!=second.id&&canonicalNonce(firstNonce)&&canonicalNonce(secondNonce)&&!firstNonce.equals(secondNonce)
                &&mayRemoveProbe(first,before,display,firstNonce)&&mayRemoveProbe(second,before,display,secondNonce);
    }
    static List<String> freshCommand(int display,int user,String component,String action,String data,
            Set<String> categories,String nonce,boolean waitForDraw){
        return command(display,user,UNMATCHED_TASK_ID,component,action,data,categories,nonce,waitForDraw);
    }
    static List<String> existingCommand(int display,int user,int ownedTask,String component,String action,
            String data,Set<String> categories,String nonce){
        if(ownedTask<=0)throw new SecurityException("Only an exact owned positive task may be reused");
        return command(display,user,ownedTask,component,action,data,categories,nonce,true);
    }
    private static List<String> command(int display,int user,int task,String component,String action,
            String data,Set<String> categories,String nonce,boolean waitForDraw){
        if(display<=0)throw new SecurityException("A separate display is required");
        if(user<0||component==null||!component.matches("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+/[A-Za-z0-9_.$]+")
                ||action==null||action.isEmpty()||!canonicalNonce(nonce))throw new IllegalArgumentException("Invalid isolated launch");
        List<String> out=new ArrayList<>();Collections.addAll(out,"/system/bin/am","start");if(waitForDraw)out.add("-W");
        Collections.addAll(out,"--user",Integer.toString(user),"--display",Integer.toString(display),
                "--task",Integer.toString(task),"-n",component,"-a",action,"-f",Integer.toString(NEW_FLAGS),
                "--es",NONCE_EXTRA,nonce);
        if(data!=null){out.add("-d");out.add(data);}
        if(categories!=null)for(String category:categories){out.add("-c");out.add(category);}
        return out;
    }
    /** An unconfirmed submitted launch must never be replayed by a repeated open_app. */
    static final class AttemptGate {
        private final java.util.Set<String> pending=new java.util.HashSet<>();
        boolean maySubmit(String pkg){return !pending.contains(pkg);}
        void submitted(String pkg){pending.add(pkg);}
        void confirmed(String pkg){pending.remove(pkg);}
        void clear(){pending.clear();}
    }
    private AppLaunchIsolationPolicy(){}
}


