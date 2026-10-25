package fr.phonemcp;
import java.util.*;
/** Finite forward-only stages: no loop, unreachable branch or unbounded callback chain. */
final class PlanGraphPolicy {
 static void validate(int[] stage,int[] next){
  if(stage.length<1||stage.length>32||stage.length!=next.length)throw new IllegalArgumentException("Invalid plan size");
  Set<Integer> reached=new HashSet<>();reached.add(0);Map<Integer,Integer> counts=new TreeMap<>(),depth=new TreeMap<>();depth.put(0,1);
  for(int i=0;i<stage.length;i++){int a=stage[i],b=next[i];if(a<0||a>31||b< -1||b>31||b>=0&&b<=a)throw new IllegalArgumentException("Forward-only stages required");counts.put(a,counts.getOrDefault(a,0)+1);}
  for(int a:counts.keySet()){if(!reached.contains(a)||counts.get(a)>8||depth.getOrDefault(a,99)>6)throw new IllegalArgumentException("Unreachable or oversized stage");for(int i=0;i<stage.length;i++)if(stage[i]==a&&next[i]>=0){int b=next[i];if(!counts.containsKey(b)||!reached.add(b))throw new IllegalArgumentException("Each child stage needs exactly one parent");depth.put(b,depth.get(a)+1);}}
 }
}
