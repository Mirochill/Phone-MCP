package fr.phonemcp;

import java.util.*;

/** Durable, per-package temporary app-op ownership; no Android or background polling. */
final class ControlledAudioPolicy {
    static final int ALLOWED=0,IGNORED=1,ERRORED=2;
    static final int MAX_APPS=64;
    interface Backend {
        int uid(String pkg)throws Exception; // -1 when uninstalled
        int packageMode(int uid,String pkg)throws Exception;
        int effectiveMode(int uid,String pkg)throws Exception;
        void setMode(int uid,String pkg,int mode)throws Exception;
    }
    interface Journal { void save(Collection<Record> records)throws Exception; }
    static final class Record {
        final String pkg;final int uid,mode;
        Record(String pkg,int uid,int mode){this.pkg=pkg;this.uid=uid;this.mode=mode;}
    }
    static final class Observation {
        final int uid,packageMode,effectiveMode;
        Observation(int uid,int packageMode,int effectiveMode){this.uid=uid;this.packageMode=packageMode;this.effectiveMode=effectiveMode;}
    }
    private final Backend backend;
    private final Journal journal;
    private final LinkedHashSet<String> known=new LinkedHashSet<>();
    private final LinkedHashMap<String,Record> owned=new LinkedHashMap<>();
    private final LinkedHashMap<String,Observation> observed=new LinkedHashMap<>();
    private long muteWrites,restoreWrites;

    ControlledAudioPolicy(Backend backend,Journal journal,Collection<Record> recovered){
        this.backend=backend;this.journal=journal;
        for(Record record:recovered){
            if(owned.put(record.pkg,record)!=null)throw new IllegalArgumentException("Entrée audio dupliquée.");
        }
        if(owned.size()>MAX_APPS)throw new IllegalArgumentException("Journal audio trop volumineux.");
    }
    synchronized void remember(String pkg,boolean screenOff)throws Exception {
        if(!known.contains(pkg)&&known.size()>=MAX_APPS)throw new IllegalStateException("Trop d’applications audio dans cette session. Redémarrez le contrôle.");
        if(backend.uid(pkg)<0)throw new IllegalArgumentException("Application audio introuvable.");
        known.add(pkg);
        if(screenOff)mute(pkg);
    }
    static boolean blocked(int mode){return mode==IGNORED||mode==ERRORED;}
    private void observe(String pkg,int uid,int packageMode)throws Exception {
        int effective=backend.effectiveMode(uid,pkg);observed.put(pkg,new Observation(uid,packageMode,effective));
        if(!blocked(effective))throw new IllegalStateException("Android ne confirme pas le silence de cette application. Action IA refusée écran éteint.");
    }
    private void mute(String pkg)throws Exception {
        int uid=backend.uid(pkg);if(uid<0)return;
        int previous=backend.packageMode(uid,pkg);
        Record existing=owned.get(pkg);
        if(existing!=null&&(existing.uid!=uid||previous!=IGNORED))
            throw new IllegalStateException("Le réglage audio a changé hors de Phone MCP. Le contrôle sonore est suspendu.");
        if(blocked(previous)){observe(pkg,uid,previous);return;}
        Record record=new Record(pkg,uid,previous);owned.put(pkg,record);
        // A successful durable save MUST precede even the first mutating binder call.
        try{persist();}catch(Exception failure){owned.remove(pkg);throw failure;}
        try{
            backend.setMode(uid,pkg,IGNORED);muteWrites++;
            int current=backend.packageMode(uid,pkg);
            if(current!=IGNORED)throw new IllegalStateException("Restriction audio non confirmée.");
            observe(pkg,uid,current);
        }catch(Exception failure){
            // A binder call can throw after it changed the mode. Try to undo our change,
            // keeping the durable journal whenever undo itself cannot be confirmed.
            try{restoreOne(pkg);persist();}catch(Exception recovery){failure.addSuppressed(recovery);}
            throw failure;
        }
    }
    synchronized void screenOff()throws Exception {
        Exception failure=null;
        for(String pkg:known)try{mute(pkg);}catch(Exception error){if(failure==null)failure=error;else failure.addSuppressed(error);}
        if(failure!=null)throw failure;
    }
    private void restoreOne(String pkg)throws Exception {
        Record record=owned.get(pkg);if(record==null)return;
        int uid=backend.uid(pkg);
        if(uid==record.uid){
            int current=backend.packageMode(uid,pkg);
            if(current==IGNORED){
                backend.setMode(uid,pkg,record.mode);restoreWrites++;
                current=backend.packageMode(uid,pkg);
                if(current!=record.mode)throw new IllegalStateException("Restauration audio non confirmée.");
            }
            // A different mode belongs to the user/another process; never overwrite it.
            observed.put(pkg,new Observation(uid,current,backend.effectiveMode(uid,pkg)));
        }
        else observed.remove(pkg);
        // Removed/reinstalled packages must never inherit a previous UID's mode.
        owned.remove(pkg);
    }
    synchronized void restore()throws Exception {
        if(owned.isEmpty())return;
        Exception failure=null;
        for(String pkg:new ArrayList<>(owned.keySet()))try{restoreOne(pkg);}catch(Exception error){if(failure==null)failure=error;else failure.addSuppressed(error);}
        try{persist();}catch(Exception error){if(failure==null)failure=error;else failure.addSuppressed(error);}
        if(failure!=null)throw failure;
    }
    private void persist()throws Exception {journal.save(new ArrayList<>(owned.values()));}
    synchronized Set<String> known(){return new LinkedHashSet<>(known);}
    synchronized Map<String,Record> owned(){return new LinkedHashMap<>(owned);}
    synchronized Map<String,Observation> observations(){return new LinkedHashMap<>(observed);}
    synchronized long muteWrites(){return muteWrites;}
    synchronized long restoreWrites(){return restoreWrites;}
    synchronized void clearKnown(){known.clear();}
}

