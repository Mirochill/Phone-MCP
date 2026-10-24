package fr.phonemcp;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Only complete, repeated running-task evidence can end an already-owned app session. */
final class TaskPresencePolicy {
    static final long CONFIRM_MISSING_MS=1500;
    enum State { UNOBSERVED, UNKNOWN, PRESENT, MISSING, ENDED, RETURNED_TO_PHONE }
    static final class Task {
        final int id,display,activities;final String basePackage,topPackage;
        Task(int id,int display,int activities,String basePackage,String topPackage){this.id=id;this.display=display;this.activities=activities;this.basePackage=basePackage;this.topPackage=topPackage;}
    }
    private final Map<String,Long> missingSince=new HashMap<>();
    State evaluate(String pkg,int ownedDisplay,Set<Integer> owned,List<Task> tasks,boolean complete,boolean rootTargetPresent,long now){
        if(pkg==null||ownedDisplay<=0||owned==null||owned.isEmpty()){missingSince.remove(pkg);return State.UNOBSERVED;}
        if(!complete||tasks==null){missingSince.remove(pkg);return State.UNKNOWN;}
        for(Task task:tasks)if(owned.contains(task.id)&&task.activities>0&&task.display==0){missingSince.remove(pkg);return State.RETURNED_TO_PHONE;}
        if(rootTargetPresent){missingSince.remove(pkg);return State.PRESENT;}
        for(Task task:tasks){
            if(task.activities<0){missingSince.remove(pkg);return State.UNKNOWN;}
            // A hidden task or a same-display replacement still owns app state; visibility and
            // virtual rendering/surface state are deliberately not part of this policy.
            if(task.activities>0&&(owned.contains(task.id)||(task.display==ownedDisplay&&(pkg.equals(task.basePackage)||pkg.equals(task.topPackage))))){missingSince.remove(pkg);return State.PRESENT;}
        }
        Long began=missingSince.get(pkg);
        if(began==null||now<began){missingSince.put(pkg,now);return State.MISSING;}
        return now-began>=CONFIRM_MISSING_MS?State.ENDED:State.MISSING;
    }
    void clear(){missingSince.clear();}
}
