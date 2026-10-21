package fr.phonemcp;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

/** Only a task observed on our own display may ever be returned to it. */
final class ParallelTaskLease {
    private final Map<String,Set<Integer>> tasks=new HashMap<>();
    void observe(String pkg,int taskId,int actualDisplay,int ownedDisplay){
        if(pkg!=null&&!pkg.isEmpty()&&taskId>=0&&ownedDisplay>0&&actualDisplay==ownedDisplay)tasks.computeIfAbsent(pkg,key->new HashSet<>()).add(taskId);
    }
    boolean owns(String pkg){return tasks.containsKey(pkg);}
    void retain(Set<String> packages){tasks.keySet().retainAll(packages);}
    boolean reclaim(String pkg,int taskId,int actualDisplay,int ownedDisplay,String[] children){
        if(ownedDisplay<=0||actualDisplay!=0||!tasks.containsKey(pkg)||!tasks.get(pkg).contains(taskId))return false;
        if(children==null)return false;
        for(String child:children)if(child==null||!(child.equals(pkg)||child.startsWith(pkg+"/")))return false;
        return true;
    }
    void clear(){tasks.clear();}
}
