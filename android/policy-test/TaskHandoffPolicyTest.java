package fr.phonemcp;

import java.util.*;

public final class TaskHandoffPolicyTest {
    private static int checks;
    private static final String PKG="com.example.target";
    private static final Set<Integer> IDS=new HashSet<>(Arrays.asList(52,53));
    private static TaskHandoffPolicy.Task row(int id,int display,String base,boolean focused,boolean visible){return new TaskHandoffPolicy.Task(id,display,1,base,base,new int[]{id},new String[]{base+"/.Main"},focused,visible);}
    private static void expect(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static int select(TaskHandoffPolicy.Task... rows){return TaskHandoffPolicy.select(PKG,77,IDS,Arrays.asList(rows));}
    public static void main(String[] args){
        TaskHandoffPolicy.Task first=row(52,77,PKG,true,true),second=row(53,77,PKG,false,false);
        expect(select(first)==52,"One owned exact task can be transferred");
        expect(select(second)==53,"A unique nonfocused task is unambiguous");
        expect(select(first,second)==52,"The focused owned task wins over a cached older task");
        expect(select(row(52,77,PKG,false,false),row(53,77,PKG,false,true))==53,"A unique visible task wins");
        expect(select(row(52,77,PKG,false,false),second)<0,"Two hidden tasks require a new explicit selection");
        expect(select(first,row(53,77,PKG,true,true))<0,"Multiple focused tasks are refused");
        expect(select(row(52,77,PKG,false,true),row(53,77,PKG,false,true))<0,"Multiple visible tasks are refused");
        expect(select(first,first)<0,"Duplicate task IDs make the inventory unsafe");
        expect(select(row(52,0,PKG,true,true))<0,"A physical task must never be moved");
        expect(select(row(52,78,PKG,true,true))<0,"A task from another session display must never be moved");
        expect(select(row(99,77,PKG,true,true))<0,"Unowned tasks must never be moved");
        expect(select(row(52,77,"com.example.other",true,true))<0,"Package identity is checked independently of the task ID");
        expect(!TaskHandoffPolicy.owned(PKG,0,IDS,first),"A physical source is invalid");
        expect(!TaskHandoffPolicy.owned(PKG,77,null,first),"Missing ownership cannot authorize transfer");
        expect(!TaskHandoffPolicy.owned(PKG,77,IDS,null),"Missing current task cannot authorize transfer");
        expect(!TaskHandoffPolicy.owned(PKG,77,IDS,new TaskHandoffPolicy.Task(52,77,0,PKG,PKG,new int[0],new String[0],true,true)),"An empty task cannot preserve an app page");
        expect(!TaskHandoffPolicy.owned(PKG,77,IDS,new TaskHandoffPolicy.Task(52,77,1,PKG,PKG,null,null,true,true)),"Missing child inventory is refused");
        expect(!TaskHandoffPolicy.owned(PKG,77,IDS,new TaskHandoffPolicy.Task(52,77,1,PKG,PKG,new int[]{52},new String[0],true,true)),"Mismatched child inventory is refused");
        expect(!TaskHandoffPolicy.owned(PKG,77,IDS,new TaskHandoffPolicy.Task(52,77,1,PKG,PKG,new int[]{99},new String[]{PKG+"/.Main"},true,true)),"Root containers with an unowned child are refused");
        expect(!TaskHandoffPolicy.owned(PKG,77,IDS,new TaskHandoffPolicy.Task(52,77,1,PKG,PKG,new int[]{52},new String[]{"com.example.other/.Main"},true,true)),"Root containers with another app child are refused");
        expect(!TaskHandoffPolicy.owned(PKG,77,IDS,new TaskHandoffPolicy.Task(52,77,1,PKG,PKG,new int[]{52,52},new String[]{PKG,PKG},true,true)),"Duplicate child IDs are refused");
        expect(TaskHandoffPolicy.arrived(PKG,52,row(52,0,PKG,true,true)),"Arrival requires the exact task on display zero");
        expect(!TaskHandoffPolicy.arrived(PKG,52,first),"A move request alone is not arrival");
        expect(!TaskHandoffPolicy.arrived(PKG,52,row(53,0,PKG,true,true)),"A different physical instance cannot impersonate arrival");
        expect(!TaskHandoffPolicy.arrived(PKG,52,row(52,0,"com.example.other",true,true)),"Arrival independently confirms package identity");
        System.out.println("PASS: "+checks+" exact-task user handoff checks");
    }
}