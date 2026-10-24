package fr.phonemcp;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Read-only scoped runtime fields; an unavailable value is never guessed false. */
final class RuntimeTaskPolicy {
    static final int MAX_TARGETS=4,INVENTORY_LIMIT=512;
    private static final String[] BOOLEANS={"isSleeping","isRunning","isVisible","isVisibleRequested","isFocused"};
    static final class Target {
        final int taskId,displayId;
        Target(int taskId,int displayId){this.taskId=taskId;this.displayId=displayId;}
    }
    static final class Result {
        final Target target;final Map<String,Object> fields=new LinkedHashMap<>();
        Boolean found;boolean available;String errorClass;
        Result(Target target){this.target=target;for(String name:BOOLEANS)fields.put(name,null);fields.put("numActivities",null);}
        void unavailable(String error){found=null;available=false;errorClass=error;for(String name:fields.keySet())fields.put(name,null);}
    }
    static List<Result> sample(List<Target> targets,List<?> inventory,String retrievalError){
        List<Result> results=new ArrayList<>();Set<Integer> targetIds=new HashSet<>();
        if(targets!=null)for(Target target:targets){if(results.size()>=MAX_TARGETS)break;if(target==null||target.taskId<0||target.displayId<=0||!targetIds.add(target.taskId))continue;results.add(new Result(target));}
        if(results.isEmpty())return results;
        if(retrievalError!=null||inventory==null){for(Result result:results)result.unavailable(retrievalError==null?"IllegalStateException":retrievalError);return results;}
        boolean complete=inventory.size()<INVENTORY_LIMIT;String inventoryError=null;Set<Integer> taskIds=new HashSet<>();Set<Integer> duplicates=new HashSet<>();
        for(int i=0;i<Math.min(INVENTORY_LIMIT,inventory.size());i++){
            Object task=inventory.get(i);int taskId,displayId;
            try{taskId=task.getClass().getField("taskId").getInt(task);displayId=task.getClass().getField("displayId").getInt(task);if(taskId<0||displayId<0)throw new IllegalStateException();}
            catch(Exception error){complete=false;if(inventoryError==null)inventoryError=error.getClass().getSimpleName();continue;}
            if(!taskIds.add(taskId)){complete=false;duplicates.add(taskId);}
            for(Result result:results)if(result.target.taskId==taskId&&result.target.displayId==displayId&&result.found==null){
                result.found=true;result.available=true;
                for(String name:BOOLEANS)try{result.fields.put(name,task.getClass().getField(name).getBoolean(task));}catch(Exception error){result.available=false;if(result.errorClass==null)result.errorClass=error.getClass().getSimpleName();}
                try{int count=task.getClass().getField("numActivities").getInt(task);if(count<0)throw new IllegalStateException();result.fields.put("numActivities",count);}catch(Exception error){result.available=false;if(result.errorClass==null)result.errorClass=error.getClass().getSimpleName();}
            }
        }
        for(Result result:results){
            if(duplicates.contains(result.target.taskId)){result.unavailable("IllegalStateException");continue;}
            if(result.found==null){if(complete){result.found=false;result.available=true;}else result.unavailable(inventoryError==null?"IllegalStateException":inventoryError);}
        }
        return results;
    }
}
