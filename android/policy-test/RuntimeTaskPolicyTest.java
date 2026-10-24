package fr.phonemcp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class RuntimeTaskPolicyTest {
    private static int checks;
    public static final class Task {public int taskId,displayId,numActivities=1;public boolean isSleeping,isRunning=true,isVisible=true,isVisibleRequested=true,isFocused=true;Task(int id,int display){taskId=id;displayId=display;}}
    public static final class PartialTask {public int taskId=6947,displayId=81,numActivities=1;public boolean isRunning=true;}
    public static final class PrivateTask {private int taskId=6947;public int displayId=81;}
    private static final RuntimeTaskPolicy.Target target=new RuntimeTaskPolicy.Target(6947,81);
    private static void expect(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static RuntimeTaskPolicy.Result sample(List<?> values){return RuntimeTaskPolicy.sample(Collections.singletonList(target),values,null).get(0);}
    public static void main(String[] args){
        Task owned=new Task(6947,81);RuntimeTaskPolicy.Result value=sample(Collections.singletonList(owned));
        expect(Boolean.TRUE.equals(value.found)&&value.available,"An exact owned task/display pair is observed");
        expect(Boolean.FALSE.equals(value.fields.get("isSleeping")),"A known false is retained distinctly from unavailable");
        expect(Boolean.TRUE.equals(value.fields.get("isRunning"))&&Boolean.TRUE.equals(value.fields.get("isVisible")),"Public runtime booleans are sampled");
        expect(value.fields.size()==6&&Integer.valueOf(1).equals(value.fields.get("numActivities")),"Only the six allowlisted fields are emitted");
        expect(value.errorClass==null,"An available sample carries no invented error");
        owned.isSleeping=true;value=sample(Collections.singletonList(owned));expect(Boolean.TRUE.equals(value.fields.get("isSleeping")),"A sleeping task is reported without inference from focus or pixels");
        value=sample(Collections.singletonList(new Task(6947,0)));expect(Boolean.FALSE.equals(value.found)&&value.fields.get("isRunning")==null,"A task on the physical display cannot impersonate the owned display");
        value=sample(Collections.singletonList(new Task(6948,81)));expect(Boolean.FALSE.equals(value.found),"Another task on the same display is not selected");
        value=sample(Collections.singletonList(new Task(6947,82)));expect(Boolean.FALSE.equals(value.found),"Another agent display is excluded");
        value=sample(Collections.emptyList());expect(Boolean.FALSE.equals(value.found)&&value.available&&value.fields.get("isSleeping")==null,"Confirmed task absence is distinct from an unavailable field");
        value=sample(Collections.singletonList(new PartialTask()));expect(Boolean.TRUE.equals(value.found)&&!value.available,"Missing hidden fields do not make the whole row falsely disappear");
        expect(value.fields.get("isSleeping")==null&&Boolean.TRUE.equals(value.fields.get("isRunning")),"Available fields survive while missing fields remain null");
        expect("NoSuchFieldException".equals(value.errorClass),"Missing metadata reports only its exception class");
        value=sample(Collections.singletonList(new PrivateTask()));expect(value.found==null&&!value.available&&"NoSuchFieldException".equals(value.errorClass),"An unreadable identity cannot prove task absence");
        value=RuntimeTaskPolicy.sample(Collections.singletonList(target),null,"SecurityException").get(0);expect(value.found==null&&value.fields.get("isFocused")==null&&"SecurityException".equals(value.errorClass),"Permission failure never becomes false runtime state");
        List<Task> saturated=new ArrayList<>();for(int i=0;i<512;i++)saturated.add(new Task(10000+i,99));value=sample(saturated);expect(value.found==null&&!value.available,"A saturated inventory cannot prove an absent task");
        saturated.set(511,new Task(6947,81));value=sample(saturated);expect(Boolean.TRUE.equals(value.found)&&value.available,"An exact target found within the bound can still be reported");
        saturated.set(511,new Task(10511,99));saturated.add(new Task(6947,81));value=sample(saturated);expect(value.found==null,"Records beyond512 are never read");
        value=sample(Arrays.asList(new Task(6947,81),new Task(6947,82)));expect(value.found==null&&value.fields.get("isRunning")==null,"Ambiguous duplicate task identities fail closed");
        Task invalid=new Task(6947,81);invalid.numActivities=-1;value=sample(Collections.singletonList(invalid));expect(value.fields.get("numActivities")==null&&!value.available,"An unavailable activity count is never changed to zero");
        List<RuntimeTaskPolicy.Target> targets=Arrays.asList(new RuntimeTaskPolicy.Target(1,1),new RuntimeTaskPolicy.Target(2,2),new RuntimeTaskPolicy.Target(3,3),new RuntimeTaskPolicy.Target(4,4),new RuntimeTaskPolicy.Target(5,5));expect(RuntimeTaskPolicy.sample(targets,Collections.emptyList(),null).size()==4,"At most four owned targets are published");
        targets=Arrays.asList(new RuntimeTaskPolicy.Target(1,0),new RuntimeTaskPolicy.Target(-1,81),target,target);expect(RuntimeTaskPolicy.sample(targets,Collections.emptyList(),null).size()==1,"Invalid and duplicate targets are excluded");
        expect(RuntimeTaskPolicy.sample(Collections.emptyList(),null,null).isEmpty(),"No targets require no runtime sample");
        System.out.println("PASS: "+checks+" scoped runtime-task diagnostics checks");
    }
}
