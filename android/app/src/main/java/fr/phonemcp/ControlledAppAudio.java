package fr.phonemcp;

import android.app.AppOpsManager;
import android.content.Context;
import android.media.AudioManager;
import android.os.IBinder;
import android.system.*;
import org.json.*;
import java.io.FileDescriptor;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;

/** Shell-only, per-package silence; never changes stream volume, ringer or DND. */
@android.annotation.SuppressLint({"PrivateApi","DiscouragedPrivateApi"})
final class ControlledAppAudio {
    private final Context context;
    private final AppOpsManager appOps;
    private final int op,user,ownerUid,defaultMode;
    private final Path root,ledger;
    private final ControlledAudioPolicy policy;
    private FileChannel lockChannel;
    private FileLock processLock;
    private String lastError="";

    ControlledAppAudio(Context context,int ownerUid)throws Exception {
        if(android.os.Process.myUid()!=2000)throw new SecurityException("Service audio Shizuku requis.");
        this.context=context;this.ownerUid=ownerUid;user=ownerUid/100000;
        appOps=context.getSystemService(AppOpsManager.class);
        op=AppOpsManager.class.getField("OP_PLAY_AUDIO").getInt(null);
        defaultMode=(Integer)AppOpsManager.class.getMethod("opToDefaultMode",int.class).invoke(null,op);
        root=Paths.get("/data/local/tmp/phone-mcp-"+ownerUid);ledger=root.resolve("screen-off-audio.json");
        StructStat directory=Os.lstat(root.toString());
        if(!OsConstants.S_ISDIR(directory.st_mode)||directory.st_uid!=2000||(directory.st_mode&0777)!=0700)
            throw new SecurityException("Dossier audio privé indisponible.");
        try {
            acquireLock();
            policy=new ControlledAudioPolicy(new ControlledAudioPolicy.Backend(){
                @Override public int uid(String pkg)throws Exception{return packageUid(pkg);}
                @Override public int packageMode(int uid,String pkg)throws Exception{return readPackageMode(uid,pkg);}
                @Override public int effectiveMode(int uid,String pkg)throws Exception{return readEffectiveMode(uid,pkg);}
                @Override public void setMode(int uid,String pkg,int mode)throws Exception{
                    AppOpsManager.class.getMethod("setMode",int.class,int.class,String.class,int.class).invoke(appOps,op,uid,pkg,mode);
                }
            },this::save,load());
            // A killed Shizuku process leaves its durable journal. Recover before accepting
            // new work. If recovery fails, retain it and refuse this new control process.
            policy.restore();
        }catch(Exception failure){releaseLock();throw failure;}
    }
    private static void requirePrivateFile(Path path)throws Exception {
        StructStat stat=Os.lstat(path.toString());
        if(!OsConstants.S_ISREG(stat.st_mode)||stat.st_uid!=2000||(stat.st_mode&0777)!=0600||stat.st_nlink!=1)
            throw new SecurityException("Journal audio privé invalide.");
    }
    private void acquireLock()throws Exception {
        Path lock=root.resolve("screen-off-audio.lock");
        try{Files.createFile(lock,PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));}
        catch(FileAlreadyExistsException existing){requirePrivateFile(lock);}
        requirePrivateFile(lock);
        lockChannel=FileChannel.open(lock,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);
        processLock=lockChannel.tryLock();
        if(processLock==null)throw new IllegalStateException("Un autre contrôle possède les réglages audio temporaires.");
    }
    private void releaseLock(){
        try{if(processLock!=null)processLock.release();}catch(Exception ignored){}processLock=null;
        try{if(lockChannel!=null)lockChannel.close();}catch(Exception ignored){}lockChannel=null;
    }
    private List<ControlledAudioPolicy.Record> load()throws Exception {
        List<ControlledAudioPolicy.Record> result=new ArrayList<>();
        if(!Files.exists(ledger,LinkOption.NOFOLLOW_LINKS))return result;
        requirePrivateFile(ledger);
        String json;
        try(FileChannel channel=FileChannel.open(ledger,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)){
            long size=channel.size();if(size<2||size>65536)throw new SecurityException("Journal audio trop volumineux.");
            ByteBuffer buffer=ByteBuffer.allocate((int)size);
            while(buffer.hasRemaining())if(channel.read(buffer)<0)throw new SecurityException("Journal audio incomplet.");
            if(channel.size()!=size)throw new SecurityException("Journal audio modifié pendant sa lecture.");
            buffer.flip();json=StandardCharsets.UTF_8.decode(buffer).toString();
        }
        JSONArray records;
        if(json.trim().startsWith("["))records=new JSONArray(json); // Unpublished prototype journal.
        else{
            JSONObject wrapper=new JSONObject(json);
            if(wrapper.getInt("version")!=1||wrapper.getInt("ownerUid")!=ownerUid)throw new SecurityException("Propriétaire du journal audio invalide.");
            records=wrapper.getJSONArray("entries");
        }
        if(records.length()>ControlledAudioPolicy.MAX_APPS)throw new SecurityException("Journal audio trop volumineux.");
        for(int i=0;i<records.length();i++){
            JSONObject record=records.getJSONObject(i);String pkg=record.getString("packageName");int uid=record.getInt("uid"),mode=record.getInt("mode");
            if(!AccessPolicy.validPackage(pkg)||uid/100000!=user||uid%100000<10000||mode<0||mode>4||mode==AppOpsManager.MODE_IGNORED)
                throw new SecurityException("Entrée du journal audio invalide.");
            result.add(new ControlledAudioPolicy.Record(pkg,uid,mode));
        }
        return result;
    }
    private int packageUid(String pkg)throws Exception {
        if(!AccessPolicy.validPackage(pkg))throw new SecurityException("Application audio invalide.");
        int uid;
        try{uid=context.getPackageManager().getApplicationInfo(pkg,0).uid;}
        catch(android.content.pm.PackageManager.NameNotFoundException removed){return -1;}
        if(uid/100000!=user||uid%100000<10000)throw new SecurityException("Application audio système ou profil différent refusé.");
        return uid;
    }
    private int readPackageMode(int uid,String pkg)throws Exception {
        Object entries=AppOpsManager.class.getMethod("getOpsForPackage",int.class,String.class,int[].class).invoke(appOps,uid,pkg,new int[]{op});
        if(entries!=null)for(Object entry:(List<?>)entries){
            Object ops=entry.getClass().getMethod("getOps").invoke(entry);
            for(Object value:(List<?>)ops)if((Integer)value.getClass().getMethod("getOp").invoke(value)==op)
                return (Integer)value.getClass().getMethod("getMode").invoke(value);
        }
        return defaultMode;
    }
    private int readEffectiveMode(int uid,String pkg)throws Exception {
        // Read the binder directly rather than an eventually updated app-op cache. UID or
        // manufacturer overrides must never make a failed package restriction look successful.
        IBinder binder=(IBinder)Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,"appops");
        Class<?> api=Class.forName("com.android.internal.app.IAppOpsService");
        Object service=Class.forName("com.android.internal.app.IAppOpsService$Stub").getMethod("asInterface",IBinder.class).invoke(null,binder);
        try{return (Integer)api.getMethod("checkOperationRaw",int.class,int.class,String.class,String.class).invoke(service,op,uid,pkg,null);}
        catch(NoSuchMethodException older){return (Integer)api.getMethod("checkOperationRaw",int.class,int.class,String.class).invoke(service,op,uid,pkg);}
    }
    synchronized void remember(String pkg,boolean screenOff)throws Exception {
        try{policy.remember(pkg,screenOff);lastError="";}catch(Exception failed){lastError="Protection audio non confirmée.";throw failed;}
    }
    synchronized void screenOff()throws Exception {
        try{policy.screenOff();lastError="";}catch(Exception failed){lastError="Silence des apps IA non confirmé.";throw failed;}
    }
    synchronized void restore()throws Exception {
        try{policy.restore();lastError="";}catch(Exception failed){lastError="Restauration audio en attente.";throw failed;}
    }
    private void save(Collection<ControlledAudioPolicy.Record> entries)throws Exception {
        JSONArray records=new JSONArray();
        for(ControlledAudioPolicy.Record item:entries)records.put(new JSONObject().put("packageName",item.pkg).put("uid",item.uid).put("mode",item.mode));
        byte[] data=new JSONObject().put("version",1).put("ownerUid",ownerUid).put("entries",records).toString().getBytes(StandardCharsets.UTF_8);
        Path temporary=Files.createTempFile(root,"screen-off-audio-",".tmp",PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        try{
            requirePrivateFile(temporary);
            try(FileChannel channel=FileChannel.open(temporary,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS)){
                ByteBuffer bytes=ByteBuffer.wrap(data);while(bytes.hasRemaining())channel.write(bytes);channel.force(true);
            }
            try{Files.move(temporary,ledger,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException unsupported){Files.move(temporary,ledger,StandardCopyOption.REPLACE_EXISTING);}
            // Persist the rename before a mutating app-op call, not just the file contents.
            FileDescriptor directory=Os.open(root.toString(),OsConstants.O_RDONLY|OsConstants.O_NOFOLLOW|OsConstants.O_CLOEXEC,0);
            try{Os.fsync(directory);}finally{Os.close(directory);}
        }finally{Files.deleteIfExists(temporary);}
    }
    synchronized JSONObject state()throws Exception {
        JSONArray apps=new JSONArray();Map<String,ControlledAudioPolicy.Record> owned=policy.owned();
        Map<String,ControlledAudioPolicy.Observation> observed=policy.observations();
        for(String pkg:policy.known()){
            JSONObject app=new JSONObject().put("packageName",pkg).put("restrictionOwned",owned.containsKey(pkg));
            ControlledAudioPolicy.Record record=owned.get(pkg);if(record!=null)app.put("originalPackageMode",record.mode);
            ControlledAudioPolicy.Observation value=observed.get(pkg);
            if(value!=null)app.put("uid",value.uid).put("lastVerifiedPackageMode",value.packageMode).put("lastVerifiedEffectiveMode",value.effectiveMode);
            apps.put(app);
        }
        return new JSONObject().put("scope","controlled_apps").put("sharedAcrossPackageInstances",true)
                .put("knownApps",policy.known().size()).put("temporaryAudioRestrictions",owned.size()).put("apps",apps)
                .put("verification","per_action_and_screen_transition").put("muteWrites",policy.muteWrites())
                .put("restoreWrites",policy.restoreWrites()).put("error",lastError);
    }
    // Explicit diagnostic only. Routine status calls above never enumerate global playback or
    // query binder modes again, and there is no registered polling/player callback at idle.
    synchronized JSONObject diagnostics()throws Exception {
        JSONObject result=state();JSONArray apps=new JSONArray(),players=new JSONArray();Set<Integer> knownUids=new HashSet<>();
        for(String pkg:policy.known()){
            int uid=packageUid(pkg);if(uid<0)continue;knownUids.add(uid);
            apps.put(new JSONObject().put("packageName",pkg).put("uid",uid).put("packageMode",readPackageMode(uid,pkg)).put("effectiveMode",readEffectiveMode(uid,pkg)));
        }
        result.put("currentApps",apps).put("players",players).put("playerDiagnosticsAvailable",false).put("mutedByPlayAudioBit",1<<3);
        try{
            Class<?> configuration=Class.forName("android.media.AudioPlaybackConfiguration");
            Method client=configuration.getMethod("getClientUid"),muted=configuration.getMethod("getMutedBy"),state=configuration.getMethod("getPlayerState"),id=configuration.getMethod("getPlayerInterfaceId");
            result.put("playerMuteApiAvailable",true);
            boolean privileged=context.checkPermission("android.permission.MODIFY_AUDIO_ROUTING",android.os.Process.myPid(),android.os.Process.myUid())==android.content.pm.PackageManager.PERMISSION_GRANTED;
            result.put("playerDiagnosticsPrivileged",privileged);
            if(!privileged){
                result.put("playerDiagnosticsError","Android ne donne pas au service l’accès aux lecteurs audio non anonymisés.");
                return result;
            }
            AudioManager audio=context.getSystemService(AudioManager.class);int count=0;
            for(Object player:audio.getActivePlaybackConfigurations()){
                int uid=(Integer)client.invoke(player);if(!knownUids.contains(uid))continue;
                if(++count>64){result.put("playersTruncated",true);break;}
                int mask=(Integer)muted.invoke(player);
                players.put(new JSONObject().put("uid",uid).put("playerId",id.invoke(player)).put("state",state.invoke(player)).put("mutedBy",mask).put("mutedByPlayAudio",(mask&(1<<3))!=0));
            }
            result.put("playerDiagnosticsAvailable",true);
        }catch(Exception unavailable){result.put("playerDiagnosticsError","Indicateurs audio natifs indisponibles sur cette version Android.");}
        return result;
    }
    synchronized void close()throws Exception {
        try{restore();}finally{policy.clearKnown();releaseLock();}
    }
}


