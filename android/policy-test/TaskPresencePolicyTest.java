package fr.phonemcp;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class TaskPresencePolicyTest {
    private static int checks;
    private static final String pkg="com.example.app";
    private static final Set<Integer> owned=new HashSet<>(Collections.singletonList(6936));
    private static final List<TaskPresencePolicy.Task> empty=Collections.emptyList();
    private static void expect(boolean value,String text){checks++;if(!value)throw new AssertionError(text);}
    private static TaskPresencePolicy.Task task(int id,int display,int activities,String base,String top){return new TaskPresencePolicy.Task(id,display,activities,base,top);}
    private static TaskPresencePolicy.State check(TaskPresencePolicy policy,List<TaskPresencePolicy.Task> tasks,boolean complete,boolean root,long now){return policy.evaluate(pkg,72,owned,tasks,complete,root,now);}
    public static void main(String[] args){
        TaskPresencePolicy policy=new TaskPresencePolicy();
        expect(policy.evaluate(pkg,72,Collections.emptySet(),empty,true,false,1000)==TaskPresencePolicy.State.UNOBSERVED,"A session before its first confirmed app task must never auto-end");
        expect(check(policy,empty,true,false,1000)==TaskPresencePolicy.State.MISSING,"One empty running-task inventory is insufficient");
        expect(check(policy,empty,true,false,2499)==TaskPresencePolicy.State.MISSING,"Transient task recreation gets the full confirmation interval");
        expect(check(policy,empty,true,false,2500)==TaskPresencePolicy.State.ENDED,"Repeated complete absence can end an already-owned app task");
        policy.clear();List<TaskPresencePolicy.Task> alive=Collections.singletonList(task(6936,72,1,pkg,pkg));
        expect(check(policy,alive,true,false,1000)==TaskPresencePolicy.State.PRESENT,"A suspended or nonvisible task with an activity is still alive");
        expect(check(policy,empty,true,false,1100)==TaskPresencePolicy.State.MISSING,"New absence starts a fresh grace interval");
        expect(check(policy,alive,true,false,2000)==TaskPresencePolicy.State.PRESENT,"A recovered task cancels pending auto-end");
        expect(check(policy,empty,true,false,2600)==TaskPresencePolicy.State.MISSING,"A later absence cannot reuse an older missing timestamp");
        expect(check(policy,Collections.singletonList(task(6936,72,0,pkg,null)),true,false,4100)==TaskPresencePolicy.State.ENDED,"An empty owned task with zero activities counts as ended after confirmation");
        policy.clear();List<TaskPresencePolicy.Task> replacement=Collections.singletonList(task(7000,72,1,pkg,"com.example.ad"));
        expect(check(policy,replacement,true,false,1000)==TaskPresencePolicy.State.PRESENT,"An internal same-display replacement/base task preserves app state");
        expect(check(policy,Collections.singletonList(task(7001,72,1,"com.example.container",pkg)),true,false,1000)==TaskPresencePolicy.State.PRESENT,"A matching top activity on the owned display preserves the session");
        expect(check(policy,empty,true,true,1000)==TaskPresencePolicy.State.PRESENT,"Conflicting root-task evidence prevents a false end");
        expect(check(policy,Collections.singletonList(task(6936,0,1,pkg,pkg)),true,false,1000)==TaskPresencePolicy.State.RETURNED_TO_PHONE,"A user takeover gets its distinct reason and is never reclaimed");
        policy.clear();List<TaskPresencePolicy.Task> humanOnly=Collections.singletonList(task(6918,0,1,pkg,pkg));
        expect(check(policy,humanOnly,true,false,1000)==TaskPresencePolicy.State.MISSING,"An unrelated human task cannot impersonate the owned AI task");
        expect(check(policy,humanOnly,true,false,2500)==TaskPresencePolicy.State.ENDED,"The AI session ends while preserving a separate physical task");
        policy.clear();List<TaskPresencePolicy.Task> otherAgent=Collections.singletonList(task(6937,73,1,pkg,pkg));
        expect(check(policy,otherAgent,true,false,1000)==TaskPresencePolicy.State.MISSING,"Another agent's app state does not mask this session's missing task");
        expect(check(policy,otherAgent,true,false,2500)==TaskPresencePolicy.State.ENDED,"Only the missing session is ended when another agent remains alive");
        policy.clear();check(policy,empty,true,false,1000);
        expect(check(policy,empty,false,false,2400)==TaskPresencePolicy.State.UNKNOWN,"Failed, saturated or incomplete inventories never prove task death");
        expect(check(policy,empty,true,false,2500)==TaskPresencePolicy.State.MISSING,"An API error resets missing-time confirmation");
        expect(check(policy,Collections.singletonList(task(9000,1,-1,null,null)),true,false,4100)==TaskPresencePolicy.State.UNKNOWN,"Unavailable activity counts are not guessed to be zero");
        expect(check(policy,Collections.singletonList(task(6936,99,1,pkg,pkg)),true,false,4200)==TaskPresencePolicy.State.PRESENT,"An owned task moved to an unknown display is not falsely called dead");
        policy.clear();check(policy,empty,true,false,5000);
        expect(check(policy,empty,true,false,4000)==TaskPresencePolicy.State.MISSING,"Clock discontinuity resets the grace interval");
        expect(policy.evaluate(pkg,0,owned,empty,true,false,6000)==TaskPresencePolicy.State.UNOBSERVED,"Display zero is outside this app-task ownership policy");
        System.out.println("PASS: "+checks+" conservative app-task lifetime checks");
    }
}
