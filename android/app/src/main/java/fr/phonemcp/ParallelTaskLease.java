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
    Set<Integer> taskIds(String pkg){Set<Integer> owned=tasks.get(pkg);return owned==null?new HashSet<>():new HashSet<>(owned);}
    boolean ownsTask(int taskId){for(Set<Integer> owned:tasks.values())if(owned.contains(taskId))return true;return false;}
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
