package fr.phonemcp;

import java.util.*;

/** Pure ownership and ambiguity checks for a local, exact-task handoff. */
final class TaskHandoffPolicy {
    static final class Task {
        final int id,display,activities;final String base,top;final int[] children;final String[] childNames;final boolean focused,visible;
        Task(int id,int display,int activities,String base,String top,int[] children,String[] names,boolean focused,boolean visible){this.id=id;this.display=display;this.activities=activities;this.base=base;this.top=top;this.children=children;this.childNames=names;this.focused=focused;this.visible=visible;}
    }
    static boolean owned(String pkg,int source,Set<Integer> ids,Task task){
        if(pkg==null||pkg.isEmpty()||source<=0||ids==null||task==null||task.id<0||!ids.contains(task.id)||task.display!=source||task.activities<1)return false;
        if(!pkg.equals(task.base))return false;
        if(task.children==null||task.childNames==null||task.children.length!=task.childNames.length)return false;
        Set<Integer> seen=new HashSet<>();
        for(int i=0;i<task.children.length;i++){
            int id=task.children[i];String name=task.childNames[i];
            if(id<0||!ids.contains(id)||!seen.add(id)||name==null||!(name.equals(pkg)||name.startsWith(pkg+"/")))return false;
        }
        return true;
    }
    static int select(String pkg,int source,Set<Integer> ids,List<Task> tasks){
        if(tasks==null)return -1;
        List<Task> candidates=new ArrayList<>();Set<Integer> unique=new HashSet<>();
        for(Task task:tasks){if(task==null||!unique.add(task.id))return -1;if(owned(pkg,source,ids,task))candidates.add(task);}
        if(candidates.isEmpty())return -1;
        int focused=only(candidates,true);if(focused>=0)return focused;if(focused==-2)return -1;
        int visible=only(candidates,false);if(visible>=0)return visible;if(visible==-2)return -1;
        return candidates.size()==1?candidates.get(0).id:-1;
    }
    private static int only(List<Task> tasks,boolean focus){int id=-1;for(Task task:tasks)if(focus?task.focused:task.visible){if(id>=0)return -2;id=task.id;}return id;}
    static boolean arrived(String pkg,int id,Task task){return task!=null&&task.id==id&&task.display==0&&task.activities>0&&pkg!=null&&pkg.equals(task.base);}
}