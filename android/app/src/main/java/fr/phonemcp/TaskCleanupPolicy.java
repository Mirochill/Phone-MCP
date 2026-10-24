package fr.phonemcp;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class TaskCleanupPolicy {
    static boolean mayRemove(String expected,String actual,int activities,int display,boolean displayValid){
        return expected!=null&&expected.equals(actual)&&activities==0&&display!=0&&(display<0||!displayValid);
    }
    enum SharedState { CLEAR, OTHER_LIVE_APP_TASK, UNKNOWN }
    static final class Task {
        final int id,display,activities;
        final String basePackage,topPackage;
        Task(int id,int display,int activities,String basePackage,String topPackage){
            this.id=id;this.display=display;this.activities=activities;
            this.basePackage=basePackage;this.topPackage=topPackage;
        }
    }
    /** removeTask may notify package services and kill a shared process: protect other app tasks. */
    static SharedState sharedState(String pkg,int removingTask,List<Task> tasks,boolean complete){
        if(!validPackage(pkg)||removingTask<=0||tasks==null||!complete)return SharedState.UNKNOWN;
        Set<Integer> ids=new HashSet<>();boolean otherLive=false;
        for(Task task:tasks){
            if(task==null||task.id<0||task.activities<0||!ids.add(task.id))return SharedState.UNKNOWN;
            if(task.activities==0)continue;
            if(task.display<0||!validPackage(task.basePackage)||!validPackage(task.topPackage))return SharedState.UNKNOWN;
            if(task.id==removingTask)return SharedState.UNKNOWN; // Target became active again.
            boolean sameBase=pkg.equals(task.basePackage),sameTop=pkg.equals(task.topPackage);
            if(sameBase||sameTop)otherLive=true;
            // A mixed live task may contain another package between its base and top. Do not guess.
            else if(!task.basePackage.equals(task.topPackage))return SharedState.UNKNOWN;
        }
        return otherLive?SharedState.OTHER_LIVE_APP_TASK:SharedState.CLEAR;
    }
    private static boolean validPackage(String value){return value!=null&&value.length()<=255&&value.matches("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)*");}
}
