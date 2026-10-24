package fr.phonemcp;

import java.util.*;

public final class ControlledAudioPolicyTest {
    private static int checks;
    private static void expect(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    interface Attempt {void run()throws Exception;}
    private static void fails(Attempt attempt,String message)throws Exception{
        boolean failed=false;try{attempt.run();}catch(Exception expected){failed=true;}expect(failed,message);
    }
    static final class Fake implements ControlledAudioPolicy.Backend,ControlledAudioPolicy.Journal {
        final Map<String,Integer> uids=new HashMap<>(),modes=new HashMap<>(),overrides=new HashMap<>();
        final List<String> events=new ArrayList<>();
        List<ControlledAudioPolicy.Record> disk=new ArrayList<>();
        boolean failSave,throwAfterMute,failRestore,ignoreMute,ignoreRestore;
        Fake(){add("com.example.first",10001,0);add("com.example.second",10002,0);}
        void add(String pkg,int uid,int mode){uids.put(pkg,uid);modes.put(pkg,mode);}
        @Override public int uid(String pkg){return uids.getOrDefault(pkg,-1);}
        @Override public int packageMode(int uid,String pkg){return modes.getOrDefault(pkg,0);}
        @Override public int effectiveMode(int uid,String pkg){return overrides.getOrDefault(pkg,modes.getOrDefault(pkg,0)==3?0:modes.getOrDefault(pkg,0));}
        @Override public void setMode(int uid,String pkg,int mode)throws Exception{
            if(mode==1){
                boolean recorded=false;for(ControlledAudioPolicy.Record record:disk)if(record.pkg.equals(pkg)&&record.uid==uid)recorded=true;
                expect(recorded,"Persist the original exact mode before any mute call");
                events.add("mute:"+pkg);if(!ignoreMute)modes.put(pkg,mode);
                if(throwAfterMute){throwAfterMute=false;throw new Exception("Binder failed after applying mutation");}
            }else{
                events.add("restore:"+pkg);if(failRestore)throw new Exception("Restore unavailable");
                if(!ignoreRestore)modes.put(pkg,mode);
            }
        }
        @Override public void save(Collection<ControlledAudioPolicy.Record> records)throws Exception{
            events.add("journal");if(failSave)throw new Exception("Disk failure");disk=new ArrayList<>(records);
        }
        ControlledAudioPolicy fresh(){return new ControlledAudioPolicy(this,this,Collections.emptyList());}
        ControlledAudioPolicy recover(){return new ControlledAudioPolicy(this,this,new ArrayList<>(disk));}
    }
    public static void main(String[] args)throws Exception{
        String first="com.example.first",second="com.example.second";
        Fake f=new Fake();ControlledAudioPolicy p=f.fresh();
        p.remember(first,false);expect(f.events.isEmpty(),"An awake phone does not write app-ops or a journal");
        p.screenOff();expect(f.modes.get(first)==1,"Screen off mutes the controlled package");
        expect(f.modes.get(second)==0,"Never mute another unregistered app");
        expect(f.events.get(0).equals("journal"),"Journal precedes the first mutation");
        expect(p.owned().get(first).mode==0,"Retain the exact original mode");
        int events=f.events.size();p.screenOff();expect(f.events.size()==events,"Repeated muted observations do not rewrite settings or disk");
        expect(p.muteWrites()==1,"Only one mute write for an unchanged screen-off interval");
        p.restore();expect(f.modes.get(first)==0,"Screen on restores exactly the mode we replaced");
        expect(f.disk.isEmpty()&&p.owned().isEmpty(),"Confirmed restoration clears the durable ownership record");
        events=f.events.size();p.restore();expect(f.events.size()==events,"An idle restore does no I/O");
        expect(p.known().contains(first),"Temporary screen on does not forget an app still controlled in background");
        p.screenOff();expect(f.modes.get(first)==1,"The same known app is muted on the next off event");
        p.restore();

        f=new Fake();p=f.fresh();f.modes.put(first,2);p.remember(first,true);
        expect(f.events.isEmpty()&&p.owned().isEmpty(),"A preexisting user restriction is never owned or reset");
        p.restore();expect(f.modes.get(first)==2,"Keep an app that was already denied silent");
        f=new Fake();p=f.fresh();f.modes.put(first,3);p.remember(first,true);p.restore();
        expect(f.modes.get(first)==3,"Preserve MODE_DEFAULT rather than converting it to ALLOWED");
        f=new Fake();p=f.fresh();f.modes.put(first,4);p.remember(first,true);p.restore();
        expect(f.modes.get(first)==4,"Preserve an original foreground-only mode");

        f=new Fake();p=f.fresh();p.remember(first,true);f.modes.put(first,2);p.restore();
        expect(f.modes.get(first)==2,"An external setting change made during control must win");
        expect(f.disk.isEmpty(),"External changes release our restoration obligation");
        f=new Fake();p=f.fresh();p.remember(first,true);f.modes.put(first,0);
        ControlledAudioPolicy changed=p;
        fails(()->changed.screenOff(),"Do not overwrite a mode changed outside Phone MCP");
        expect(f.modes.get(first)==0,"An external audible override is not silently taken back");

        f=new Fake();p=f.fresh();p.remember(first,true);ControlledAudioPolicy recovered=f.recover();recovered.restore();
        expect(f.modes.get(first)==0&&f.disk.isEmpty(),"A new process recovers a killed process's original mode");
        f=new Fake();p=f.fresh();p.remember(first,true);f.uids.put(first,10999);f.modes.put(first,0);recovered=f.recover();recovered.restore();
        expect(f.modes.get(first)==0&&f.disk.isEmpty(),"Never apply a previous installation's mode to a new UID");
        f=new Fake();p=f.fresh();p.remember(first,true);f.uids.remove(first);recovered=f.recover();recovered.restore();
        expect(f.disk.isEmpty(),"A removed app cannot leave stale restoration records");

        f=new Fake();f.failSave=true;p=f.fresh();ControlledAudioPolicy diskFailure=p;
        fails(()->diskFailure.remember(first,true),"Journal failure refuses the mute before mutation");
        expect(f.modes.get(first)==0&&p.owned().isEmpty(),"Disk failure never leaves an unjournaled setting");
        f=new Fake();f.throwAfterMute=true;p=f.fresh();ControlledAudioPolicy binderFailure=p;
        fails(()->binderFailure.remember(first,true),"A post-mutation binder failure is reported");
        expect(f.modes.get(first)==0&&f.disk.isEmpty(),"Try immediate rollback even when binder threw after mutating");
        f=new Fake();f.overrides.put(first,4);p=f.fresh();ControlledAudioPolicy effectiveFailure=p;
        fails(()->effectiveFailure.remember(first,true),"An effective UID override cannot be mistaken for a successful mute");
        expect(f.modes.get(first)==0&&f.disk.isEmpty(),"Failed effective verification restores the original package mode");
        f=new Fake();f.ignoreMute=true;p=f.fresh();ControlledAudioPolicy ignoredMute=p;
        fails(()->ignoredMute.remember(first,true),"A successful return with an unchanged mode still fails closed");
        expect(f.modes.get(first)==0&&f.disk.isEmpty(),"An ignored mute call does not create lingering restoration ownership");

        f=new Fake();f.throwAfterMute=true;f.failRestore=true;p=f.fresh();ControlledAudioPolicy failedRollback=p;
        fails(()->failedRollback.remember(first,true),"A failed rollback is reported instead of declaring success");
        expect(f.modes.get(first)==1&&f.disk.size()==1,"An uncertain rollback keeps the durable recovery record");
        f.failRestore=false;recovered=f.recover();recovered.restore();
        expect(f.modes.get(first)==0&&f.disk.isEmpty(),"A later process can recover a failed rollback");
        f=new Fake();p=f.fresh();p.remember(first,true);f.ignoreRestore=true;ControlledAudioPolicy ignoredRestore=p;
        fails(()->ignoredRestore.restore(),"A restore is confirmed from package mode, not just its return code");
        expect(f.disk.size()==1&&p.owned().size()==1,"Do not erase evidence of an unconfirmed restoration");
        f.ignoreRestore=false;p.restore();expect(f.disk.isEmpty(),"Restoration can be retried after transient failure");
        f=new Fake();p=f.fresh();p.remember(first,true);f.failSave=true;ControlledAudioPolicy clearFailure=p;
        fails(()->clearFailure.restore(),"A failed journal update after undo is visible");
        expect(f.modes.get(first)==0,"Journal-clear failure does not undo the successful restoration");
        f.failSave=false;recovered=f.recover();recovered.restore();
        expect(f.modes.get(first)==0&&f.disk.isEmpty(),"Recovery safely handles a stale already-restored record");

        f=new Fake();p=f.fresh();p.remember(first,false);p.remember(second,false);f.overrides.put(first,4);ControlledAudioPolicy oneBadApp=p;
        fails(()->oneBadApp.screenOff(),"A bad app-op override is reported across a multi-app off transition");
        expect(f.modes.get(first)==0&&f.modes.get(second)==1,"A failing app does not prevent other controlled apps from becoming silent");
        p.restore();expect(f.modes.get(second)==0,"Other packages retain independent restoration ownership");
        f=new Fake();p=f.fresh();for(int i=0;i<64;i++){String pkg="com.example.app"+i;f.add(pkg,11000+i,0);p.remember(pkg,false);}
        f.add("com.example.excess",11999,0);ControlledAudioPolicy bounded=p;
        fails(()->bounded.remember("com.example.excess",false),"Bound the number of known package records");
        expect(p.known().size()==64,"Reject excess apps without growing persistent memory");
        p.remember("com.example.app0",false);expect(p.known().size()==64,"Existing app use remains allowed at the bound");
        System.out.println("PASS: "+checks+" durable audio restriction, rollback and recovery checks");
    }
}
