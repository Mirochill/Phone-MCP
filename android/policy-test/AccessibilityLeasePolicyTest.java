package fr.phonemcp;

import java.util.*;

public final class AccessibilityLeasePolicyTest {
    private static int checks;
    private static void expect(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    interface Attempt{void run()throws Exception;}
    private static void fails(Attempt attempt,String message)throws Exception{boolean failed=false;try{attempt.run();}catch(Exception expected){failed=true;}expect(failed,message);}
    static final class Fake implements AccessibilityLeasePolicy.Backend,AccessibilityLeasePolicy.Journal {
        String value;boolean disk,failJournal,failWrite,throwAfterWrite,ignoreWrite;
        int reads,writes,journalWrites;Runnable afterJournal;final ArrayDeque<String> readChanges=new ArrayDeque<>();
        Fake(String value){this.value=value;}
        @Override public String services(){reads++;if(!readChanges.isEmpty())value=readChanges.removeFirst();return value;}
        @Override public void services(String next)throws Exception{
            expect(disk,"A durable ownership record precedes any setting change");
            writes++;if(failWrite)throw new Exception("Provider unavailable");
            if(!ignoreWrite)value=next;
            if(throwAfterWrite){throwAfterWrite=false;throw new Exception("Provider failed after applying write");}
        }
        @Override public void save(boolean added)throws Exception{
            journalWrites++;if(failJournal)throw new Exception("Journal unavailable");disk=added;
            if(added&&afterJournal!=null){Runnable callback=afterJournal;afterJournal=null;callback.run();}
        }
        AccessibilityLeasePolicy fresh(){return new AccessibilityLeasePolicy(this,this,false);}
        AccessibilityLeasePolicy recover(){return new AccessibilityLeasePolicy(this,this,disk);}
    }
    public static void main(String[] args)throws Exception{
        String ours=AccessibilityLeasePolicy.COMPONENT,full=AccessibilityLeasePolicy.FULL_COMPONENT;
        expect(AccessibilityLeasePolicy.count(null)==0,"Null setting is a valid empty list");
        expect(AccessibilityLeasePolicy.append(null).equals(ours),"Only our component is inserted into an empty setting");
        expect(AccessibilityLeasePolicy.count(full)==1,"Full and short component names identify the same service");
        expect(AccessibilityLeasePolicy.count("x"+ours)==0,"Do not confuse another component with our own");
        expect(AccessibilityLeasePolicy.removeOne("com.a/.A:"+ours+":com.b/.B").equals("com.a/.A:com.b/.B"),"Remove only our exact component");
        expect(AccessibilityLeasePolicy.removeOne(full+":"+ours).equals(full),"Retain an externally added alias when removing our addition");
        Fake f=new Fake(null);AccessibilityLeasePolicy p=f.fresh();
        p.acquire();expect(f.value.equals(ours)&&f.disk&&p.ownsAddition(),"Acquire creates a journaled temporary component");
        expect(!p.preexisting()&&p.active(),"An inserted component is temporary and active");
        int writes=f.writes,journals=f.journalWrites;p.acquire();
        expect(f.writes==writes&&f.journalWrites==journals,"Repeated acquire does not rewrite settings or journal");
        p.release();expect(f.value.isEmpty()&&!f.disk&&!p.ownsAddition(),"Release removes our addition and clears ownership");
        writes=f.writes;journals=f.journalWrites;p.release();
        expect(f.writes==writes&&f.journalWrites==journals,"Repeated release is idle with no I/O mutation");
        p.acquire();expect(f.value.equals(ours),"A new authorized control can reuse a released policy");p.release();

        for(String existing:new String[]{ours,full,"com.a/.A:"+full+":com.b/.B"}){
            f=new Fake(existing);p=f.fresh();p.acquire();p.release();
            expect(f.value.equals(existing)&&f.writes==0&&f.journalWrites==0,"A previously enabled service is never owned or disabled");
        }
        String before="com.a/.A::com.b/.B:";f=new Fake(before);p=f.fresh();p.acquire();
        expect(f.value.startsWith(before),"Preserve every existing token and its order when appending");
        f.value="com.external/.New:"+f.value+":com.last/.Last";p.release();
        expect(f.value.equals("com.external/.New:com.a/.A::com.b/.B:com.last/.Last"),"Release rereads and preserves unrelated external additions and order");
        f=new Fake("com.a/.A");p=f.fresh();p.acquire();f.value=full+":"+f.value;p.release();
        expect(f.value.equals(full+":com.a/.A"),"A duplicate added externally is not removed together with our token");

        f=new Fake("com.a/.A");Fake race=f;f.afterJournal=()->race.value="com.a/.A:com.new/.New";p=f.fresh();p.acquire();
        expect(f.value.equals("com.a/.A:com.new/.New:"+ours),"Reread after journaling preserves a concurrent unrelated change");p.release();
        f=new Fake("com.a/.A");Fake userWins=f;f.afterJournal=()->userWins.value="com.a/.A:"+full;p=f.fresh();p.acquire();
        expect(p.preexisting()&&!p.ownsAddition()&&f.writes==0&&!f.disk,"A user enabling our service before the mutation owns it");
        p.release();expect(f.value.equals("com.a/.A:"+full),"Never undo the user's concurrent activation");

        f=new Fake("com.a/.A");p=f.fresh();p.acquire();f.value="com.a/.A";p.observeExternalChange();
        expect(p.revoked()&&!p.active()&&!f.disk,"A local removal revokes the lease and its restoration claim");
        AccessibilityLeasePolicy revoked=p;
        fails(()->revoked.acquire(),"Remote use cannot silently reenable a locally disabled service");
        p.beginLocalSession();expect(!p.revoked(),"Only an explicit new local session clears the refusal");
        p.acquire();expect(f.value.equals("com.a/.A:"+ours),"A newly authorized local session can acquire again");p.release();
        f=new Fake(ours);p=f.fresh();p.acquire();f.value="";p.observeExternalChange();
        expect(p.revoked()&&f.writes==0,"A preexisting service removed by the user is also respected");

        f=new Fake("com.a/.A");p=f.fresh();p.acquire();AccessibilityLeasePolicy recovered=f.recover();recovered.release();
        expect(f.value.equals("com.a/.A")&&!f.disk,"Crash recovery removes the journaled addition only");
        f=new Fake("com.a/.A");p=f.fresh();p.acquire();f.value="com.before/.Before:"+f.value+":com.after/.After";recovered=f.recover();recovered.release();
        expect(f.value.equals("com.before/.Before:com.a/.A:com.after/.After"),"Crash recovery merges into current settings instead of restoring an old list");
        f=new Fake("com.a/.A");p=f.fresh();p.acquire();f.value="com.a/.A";recovered=f.recover();recovered.release();
        expect(f.value.equals("com.a/.A")&&!f.disk,"A missing temporary component needs only a journal cleanup");

        f=new Fake("com.a/.A");f.failJournal=true;p=f.fresh();AccessibilityLeasePolicy noJournal=p;
        fails(()->noJournal.acquire(),"A failed durable journal refuses activation before mutation");
        expect(f.writes==0&&f.value.equals("com.a/.A"),"Journal failure never changes accessibility settings");
        f=new Fake("com.a/.A");f.throwAfterWrite=true;p=f.fresh();AccessibilityLeasePolicy uncertain=p;
        fails(()->uncertain.acquire(),"A provider error after mutation is visible");
        expect(f.value.equals("com.a/.A")&&!f.disk,"Immediately undo a possibly successful failed activation");
        f=new Fake("com.a/.A");f.failWrite=true;p=f.fresh();AccessibilityLeasePolicy providerFailure=p;
        fails(()->providerFailure.acquire(),"A provider denial is explicit");
        expect(f.value.equals("com.a/.A")&&!f.disk,"A write refused before mutation leaves no temporary service");
        f=new Fake("com.a/.A");f.ignoreWrite=true;p=f.fresh();AccessibilityLeasePolicy ignored=p;
        fails(()->ignored.acquire(),"A no-effect setting write cannot be reported as acquired");
        expect(f.value.equals("com.a/.A")&&!f.disk,"Verification failure still clears unused ownership");

        f=new Fake("com.a/.A");p=f.fresh();p.acquire();f.failWrite=true;AccessibilityLeasePolicy cleanupFailure=p;
        fails(()->cleanupFailure.release(),"Failed cleanup is explicit");
        expect(f.disk&&p.ownsAddition(),"Failed cleanup retains durable and in-memory ownership");
        f.failWrite=false;recovered=f.recover();recovered.release();
        expect(!f.disk&&f.value.equals("com.a/.A"),"Later startup can recover a cleanup error");
        f=new Fake("com.a/.A");p=f.fresh();p.acquire();f.failJournal=true;AccessibilityLeasePolicy journalClearFailure=p;
        fails(()->journalClearFailure.release(),"An unpersisted cleanup remains explicit");
        expect(p.ownsAddition()&&f.disk&&f.value.equals("com.a/.A"),"Keep the recovery obligation when journal clearing fails");
        f.failJournal=false;p.release();expect(!f.disk&&!p.ownsAddition(),"Journal clearing can be retried without changing unrelated settings");

        f=new Fake("a");for(String next:new String[]{"b","c","d","e"})f.readChanges.add(next);p=f.fresh();AccessibilityLeasePolicy moving=p;
        fails(()->moving.acquire(),"Continuously changing settings are bounded instead of blindly overwritten");
        expect(f.writes==0,"No mutation is issued without a stable preflight snapshot");
        String huge="x".repeat(AccessibilityLeasePolicy.MAX_SETTING_CHARS+1);f=new Fake(huge);p=f.fresh();AccessibilityLeasePolicy oversized=p;
        fails(()->oversized.acquire(),"Oversized settings fail closed with bounded memory");
        expect(f.writes==0,"Never truncate another service list to fit our own limit");
        System.out.println("PASS: "+checks+" temporary accessibility component lease checks");
    }
}
