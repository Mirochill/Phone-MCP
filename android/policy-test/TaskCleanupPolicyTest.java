package fr.phonemcp;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class TaskCleanupPolicyTest {
    private static int checks;
    private static final String APP="com.test.calc",OTHER="com.other.app";
    private static TaskCleanupPolicy.Task task(int id,int display,int activities,String base,String top){return new TaskCleanupPolicy.Task(id,display,activities,base,top);}
    private static void expect(TaskCleanupPolicy.SharedState value,TaskCleanupPolicy.SharedState expected,String why){checks++;if(value!=expected)throw new AssertionError(why+": "+value);}
    private static TaskCleanupPolicy.SharedState evaluate(List<TaskCleanupPolicy.Task> tasks,boolean complete){return TaskCleanupPolicy.sharedState(APP,42,tasks,complete);}
    public static void main(String[] args){
        int original=0;
        for(int activities:new int[]{0,1,8})for(int display:new int[]{-1,0,42})for(boolean valid:new boolean[]{false,true})for(String pkg:new String[]{APP,OTHER}){
            boolean expected=activities==0&&display!=0&&(display<0||!valid)&&pkg.equals(APP);
            if(expected!=TaskCleanupPolicy.mayRemove(APP,pkg,activities,display,valid))throw new AssertionError("Task deletion guard");original++;
        }
        TaskCleanupPolicy.SharedState clear=TaskCleanupPolicy.SharedState.CLEAR,live=TaskCleanupPolicy.SharedState.OTHER_LIVE_APP_TASK,unknown=TaskCleanupPolicy.SharedState.UNKNOWN;
        expect(evaluate(Collections.emptyList(),true),clear,"A complete empty live inventory permits only the separately verified stale target");
        expect(evaluate(Collections.singletonList(task(51,0,1,APP,APP)),true),live,"Preserve a human task on the physical screen even when the target IA task is empty");
        expect(evaluate(Collections.singletonList(task(51,80,1,APP,APP)),true),live,"Preserve another agent task on another virtual screen");
        expect(evaluate(Collections.singletonList(task(51,80,8,APP,APP)),true),live,"Any positive activity count means the other app context is live");
        expect(evaluate(Collections.singletonList(task(51,80,0,APP,APP)),true),clear,"Another empty IA task need not hold cleanup forever");
        expect(evaluate(Collections.singletonList(task(51,-1,0,null,null)),true),clear,"An empty detached record cannot own a live activity");
        expect(evaluate(Collections.singletonList(task(51,0,1,OTHER,OTHER)),true),clear,"An unrelated app does not prevent package cleanup");
        expect(evaluate(Collections.singletonList(task(51,0,1,"android","android")),true),clear,"Android's core single-name package is valid inventory metadata");
        expect(evaluate(Collections.singletonList(task(51,0,1,APP+"extra",APP+"extra")),true),clear,"Package prefix is not package identity");
        expect(evaluate(Arrays.asList(task(51,80,0,APP,APP),task(52,0,1,APP,APP)),true),live,"One empty app task does not hide another live human task");
        expect(evaluate(Arrays.asList(task(51,0,1,OTHER,OTHER),task(52,81,1,APP,APP)),true),live,"A live app task remains protected alongside unrelated apps");
        expect(evaluate(Collections.singletonList(task(51,81,2,APP,OTHER)),true),live,"An app root stays protected while an external activity is on top");
        expect(evaluate(Collections.singletonList(task(51,81,2,OTHER,APP)),true),live,"An app on top of another app's task stays protected");
        expect(evaluate(Collections.singletonList(task(51,81,3,OTHER,"com.third.app")),true),unknown,"A mixed unrelated task cannot prove absence of a buried app activity");
        expect(evaluate(Collections.singletonList(task(42,80,1,APP,APP)),true),unknown,"The target reopening is a race and must prevent deletion");
        expect(evaluate(Collections.singletonList(task(42,-1,0,null,null)),true),clear,"The target can appear as an empty detached record");
        expect(evaluate(Collections.emptyList(),false),unknown,"A truncated or failed inventory can never prove safety");
        expect(evaluate(Collections.singletonList(task(51,0,1,APP,APP)),false),unknown,"An incomplete inventory remains uncertain even with a known live task");
        expect(evaluate(null,true),unknown,"Missing inventory must defer");
        expect(evaluate(Collections.singletonList(null),true),unknown,"Missing task metadata must defer");
        expect(evaluate(Arrays.asList(task(51,80,0,null,null),task(51,81,0,null,null)),true),unknown,"Duplicate IDs make inventory ambiguous");
        expect(evaluate(Collections.singletonList(task(-1,80,0,null,null)),true),unknown,"An invalid task ID makes inventory ambiguous");
        expect(evaluate(Collections.singletonList(task(51,80,-1,APP,APP)),true),unknown,"An invalid activity count makes inventory ambiguous");
        expect(evaluate(Collections.singletonList(task(51,-1,1,APP,APP)),true),unknown,"A supposedly live task with unknown display must defer");
        expect(evaluate(Collections.singletonList(task(51,0,1,null,APP)),true),unknown,"Missing true root is not complete metadata");
        expect(evaluate(Collections.singletonList(task(51,0,1,APP,null)),true),unknown,"Missing top activity is not complete metadata");
        expect(evaluate(Collections.singletonList(task(51,0,1,"com.unknown?data",OTHER)),true),unknown,"Malformed package metadata must defer");
        expect(TaskCleanupPolicy.sharedState(null,42,Collections.emptyList(),true),unknown,"An unknown owner package must defer");
        expect(TaskCleanupPolicy.sharedState(APP,-1,Collections.emptyList(),true),unknown,"An unknown target ID must defer");
        List<TaskCleanupPolicy.Task> initial=Collections.singletonList(task(51,0,1,OTHER,OTHER));
        List<TaskCleanupPolicy.Task> finalRead=Arrays.asList(task(51,0,1,OTHER,OTHER),task(52,0,1,APP,APP));
        expect(evaluate(initial,true),clear,"Initial inventory can be safe");
        expect(evaluate(finalRead,true),live,"A newly opened human task on the final read reverses the deletion decision");
        System.out.println("PASS "+original+" existing cleanup safety cases + "+checks+" shared-task inventory cases");
    }
}
