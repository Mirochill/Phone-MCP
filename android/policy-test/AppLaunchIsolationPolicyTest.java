package fr.phonemcp;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class AppLaunchIsolationPolicyTest {
    private static int checks;
    private static final String FIRST="12345678-1234-4234-8234-123456789abc",SECOND="12345678-1234-4234-8234-123456789abd";
    private static final String APP="com.weward",MAIN=APP+"/com.weward.MainActivity",PROBE=AppLaunchIsolationPolicy.PROBE_COMPONENT;
    private static void expect(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static void refuses(Runnable call,String why){checks++;try{call.run();}catch(IllegalArgumentException|SecurityException expected){return;}throw new AssertionError(why);}
    private static AppLaunchIsolationPolicy.Task task(int id,int display,int count,String intent,String root,String top,String nonce){return new AppLaunchIsolationPolicy.Task(id,display,count,intent,root,top,nonce,0,false);}
    private static String option(List<String> args,String name){int index=args.indexOf(name);return index<0?null:args.get(index+1);}
    public static void main(String[] args){
        Set<Integer> before=new HashSet<>(Arrays.asList(8,9,10));
        AppLaunchIsolationPolicy.Task fresh=task(11,72,1,MAIN,MAIN,MAIN,FIRST);
        expect(AppLaunchIsolationPolicy.requiresOwnedTask(2),"singleTask needs proven fresh task isolation");
        expect(AppLaunchIsolationPolicy.requiresOwnedTask(3),"singleInstance needs proven fresh task isolation");
        expect(!AppLaunchIsolationPolicy.requiresOwnedTask(0)&&!AppLaunchIsolationPolicy.requiresOwnedTask(1)&&!AppLaunchIsolationPolicy.requiresOwnedTask(4),"Other launch modes retain normal task semantics");
        expect(AppLaunchIsolationPolicy.mayAcceptFreshTask(fresh,before,72,APP,FIRST),"Exact nonce, new ID, true app root and intent identify a fresh owned task");
        expect(!AppLaunchIsolationPolicy.mayAcceptFreshTask(task(8,72,1,MAIN,MAIN,MAIN,FIRST),before,72,APP,FIRST),"A same-package existing task can never be accepted as new");
        expect(!AppLaunchIsolationPolicy.mayAcceptFreshTask(task(11,0,1,MAIN,MAIN,MAIN,FIRST),before,72,APP,FIRST),"A task on physical display cannot be acquired");
        expect(!AppLaunchIsolationPolicy.mayAcceptFreshTask(fresh,before,0,APP,FIRST),"Physical destination is forbidden");
        expect(!AppLaunchIsolationPolicy.mayAcceptFreshTask(fresh,before,73,APP,FIRST),"Another agent display is forbidden");
        expect(!AppLaunchIsolationPolicy.mayAcceptFreshTask(fresh,before,72,APP,SECOND),"Another command nonce cannot identify this task");
        expect(!AppLaunchIsolationPolicy.mayAcceptFreshTask(task(11,72,0,MAIN,MAIN,MAIN,FIRST),before,72,APP,FIRST),"An empty task is not a running app instance");
        expect(!AppLaunchIsolationPolicy.mayAcceptFreshTask(task(11,72,1,PROBE,MAIN,MAIN,FIRST),before,72,APP,FIRST),"Cross-UID bootstrap identity is rejected even with target activity on top");
        expect(!AppLaunchIsolationPolicy.mayAcceptFreshTask(task(11,72,1,MAIN,PROBE,MAIN,FIRST),before,72,APP,FIRST),"Actual root must also belong to target");
        expect(!AppLaunchIsolationPolicy.mayAcceptFreshTask(task(11,72,1,MAIN,MAIN,PROBE,FIRST),before,72,APP,FIRST),"An unrelated top is not a confirmed app launch");
        expect(!AppLaunchIsolationPolicy.mayAcceptFreshTask(null,before,72,APP,FIRST),"A vanished task fails closed");
        expect(!AppLaunchIsolationPolicy.mayAcceptFreshTask(fresh,null,72,APP,FIRST),"Missing before inventory fails closed");
        expect(!AppLaunchIsolationPolicy.mayAcceptFreshTask(task(11,72,1,MAIN,MAIN,MAIN,null),before,72,APP,FIRST),"A stripped nonce cannot silently reduce ownership to package name");
        expect(!AppLaunchIsolationPolicy.mayAcceptFreshTask(fresh,before,72,"com.we",FIRST),"A package prefix is not package identity");
        expect(AppLaunchIsolationPolicy.isOwnedAppTask(fresh,11,72,APP,FIRST),"Known exact ID and nonce permit navigation in owned task");
        expect(!AppLaunchIsolationPolicy.isOwnedAppTask(fresh,12,72,APP,FIRST),"Wrong task ID never reuses another instance");
        AppLaunchIsolationPolicy.Task probeA=task(12,72,1,PROBE,PROBE,PROBE,FIRST),probeB=task(13,72,1,PROBE,PROBE,PROBE,SECOND);
        expect(AppLaunchIsolationPolicy.probePair(probeA,probeB,before,72,FIRST,SECOND),"Two simultaneous fresh singleTask roots establish the OEM capability");
        expect(!AppLaunchIsolationPolicy.probePair(probeA,probeA,before,72,FIRST,SECOND),"Samsung reusing one task must refuse third-party launch");
        expect(!AppLaunchIsolationPolicy.probePair(probeA,null,before,72,FIRST,SECOND),"A first instance disappearing before second is not independence");
        expect(!AppLaunchIsolationPolicy.probePair(probeA,probeB,before,72,FIRST,FIRST),"Probe correlations must be distinct");
        expect(!AppLaunchIsolationPolicy.mayRemoveProbe(fresh,before,72,FIRST),"The probe cleanup can never delete a target app");
        expect(!AppLaunchIsolationPolicy.mayRemoveProbe(task(12,0,1,PROBE,PROBE,PROBE,FIRST),before,72,FIRST),"Probe cleanup cannot delete a physical task");
        expect(!AppLaunchIsolationPolicy.mayRemoveProbe(task(12,72,2,PROBE,PROBE,PROBE,FIRST),before,72,FIRST),"Probe cleanup refuses a task with another activity added");
        expect(!AppLaunchIsolationPolicy.mayRemoveProbe(task(8,72,1,PROBE,PROBE,PROBE,FIRST),before,72,FIRST),"Probe cleanup refuses an old ID even with matching component");
        List<String> target=AppLaunchIsolationPolicy.freshCommand(72,0,MAIN,"android.intent.action.VIEW","https://example.com/a?q=one two",Collections.singleton("android.intent.category.DEFAULT"),FIRST,true);
        expect("-2".equals(option(target,"--task")),"Fresh launch uses only the explicit non-matching task selector");
        expect("72".equals(option(target,"--display"))&&"0".equals(option(target,"--user")),"Launch remains on exact separate display and same Android user");
        int flags=Integer.parseInt(option(target,"-f"));
        expect((flags&0x00008000)==0,"No CLEAR_TASK flag may exist on any target launch");
        expect((flags&AppLaunchIsolationPolicy.NEW_TASK)!=0&&(flags&AppLaunchIsolationPolicy.MULTIPLE_TASK)!=0,"A real new task is requested");
        expect((flags&AppLaunchIsolationPolicy.EXCLUDE_FROM_RECENTS)!=0,"AI task is excluded from ordinary recents");
        expect((flags&AppLaunchIsolationPolicy.NO_USER_ACTION)!=0,"Automation does not represent a human leaving an app");
        expect(!target.contains("-S"),"No force stop of any app is allowed");
        expect(!target.contains("-i")&&!target.contains("--identifier"),"Correlation must not change Intent.filterEquals identity");
        int extra=target.indexOf("--es");expect(extra>=0&&AppLaunchIsolationPolicy.NONCE_EXTRA.equals(target.get(extra+1))&&FIRST.equals(target.get(extra+2)),"Nonce is only a typed private-prefix extra");
        expect(target.contains("https://example.com/a?q=one two"),"URL stays one ProcessBuilder argument without shell interpolation");
        List<String> probe=AppLaunchIsolationPolicy.freshCommand(72,0,PROBE,"android.intent.action.MAIN",null,null,FIRST,false);
        expect(!probe.contains("-W")&&!probe.contains("-d"),"Neutral probe needs neither a data URL nor a draw wait");
        List<String> next=AppLaunchIsolationPolicy.existingCommand(72,0,11,MAIN,"android.intent.action.VIEW","https://example.com",null,FIRST);
        expect("11".equals(option(next,"--task")),"Subsequent navigation reuses the positive owned task ID");
        expect((Integer.parseInt(option(next,"-f"))&0x00008000)==0,"Subsequent navigation never clears app state");
        refuses(()->AppLaunchIsolationPolicy.freshCommand(0,0,MAIN,"android.intent.action.MAIN",null,null,FIRST,false),"Display zero is rejected before starting am");
        refuses(()->AppLaunchIsolationPolicy.existingCommand(72,0,-2,MAIN,"android.intent.action.MAIN",null,null,FIRST),"Non-matching selector must not be accepted as an existing task");
        refuses(()->AppLaunchIsolationPolicy.freshCommand(72,-1,MAIN,"android.intent.action.MAIN",null,null,FIRST,false),"No wildcard Android user");
        refuses(()->AppLaunchIsolationPolicy.freshCommand(72,0,MAIN,"android.intent.action.MAIN",null,null,"untrusted",false),"Only canonical UUID correlation is accepted");
        refuses(()->AppLaunchIsolationPolicy.freshCommand(72,0,MAIN+";am force-stop x","android.intent.action.MAIN",null,null,FIRST,false),"Component must be explicit and well formed");
        AppLaunchIsolationPolicy.AttemptGate gate=new AppLaunchIsolationPolicy.AttemptGate();
        expect(gate.maySubmit(APP),"A new session may launch once");gate.submitted(APP);
        expect(!gate.maySubmit(APP),"An uncertain submitted launch blocks another attempt before any new work");
        expect(gate.maySubmit("com.example.other"),"Another app is not blocked by an uncertain target");
        expect(!gate.maySubmit(APP),"A diagnostic read must not clear uncertainty");gate.confirmed(APP);
        expect(gate.maySubmit(APP),"Verified launch permits following explicit navigation");gate.submitted(APP);gate.clear();
        expect(gate.maySubmit(APP),"Ending the session clears its submission gate for a new session");
        System.out.println("PASS: "+checks+" app launch isolation safety checks");
    }
}
