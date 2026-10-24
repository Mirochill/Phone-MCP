package fr.phonemcp;
import java.util.*;
public final class JobHistoryPolicyTest{
 public static void main(String[] x){int n=0;List<JobHistoryPolicy.Record> records=new ArrayList<>();
 if(JobHistoryPolicy.evict(records)!=null)throw new AssertionError("Empty cache");n++;
 records.add(new JobHistoryPolicy.Record("active",0,true,true));records.add(new JobHistoryPolicy.Record("receiving",1,false,false));if(JobHistoryPolicy.evict(records)!=null)throw new AssertionError("Never evict active or unfinished work");n++;
 records.add(new JobHistoryPolicy.Record("recent",20,true,false));records.add(new JobHistoryPolicy.Record("old",2,true,false));if(!"old".equals(JobHistoryPolicy.evict(records)))throw new AssertionError("Evict oldest terminal metadata only");n++;
 records.add(null);records.add(new JobHistoryPolicy.Record(null,-1,true,false));if(!"old".equals(JobHistoryPolicy.evict(records)))throw new AssertionError("Invalid index row");n++;
 List<JobHistoryPolicy.Record> full=new ArrayList<>();for(int i=0;i<128;i++)full.add(new JobHistoryPolicy.Record("job"+i,i,true,false));if(!"job0".equals(JobHistoryPolicy.evict(full)))throw new AssertionError("128 completed jobs do not block the next operation");n++;
 System.out.println("PASS: "+n+" bounded job-cache and active-work preservation checks");}
}
