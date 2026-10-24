package fr.phonemcp;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.*;
import android.os.IBinder;
import android.os.Binder;
import android.os.Bundle;
import android.os.Build;
import android.provider.Settings;
import android.system.*;
import org.json.JSONObject;
import java.io.FileDescriptor;
import java.lang.reflect.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;

/** Shell-only component lease, acquired lazily under an already locally authorized session. */
@android.annotation.SuppressLint({"PrivateApi","DiscouragedPrivateApi"})
final class AccessibilityGestureLease {
    private final int ownerUid,user;
    private final Path root,ledger;
    private FileChannel lockChannel;
    private FileLock processLock;
    private AccessibilityLeasePolicy policy;
    private boolean cachedReady,externallyRevoked,cleanupPending;
    private long settingsChangeNotifications;
    private String lastError="",globalBefore;
    AccessibilityGestureLease(Context shellContext,int ownerUid)throws Exception {
        if(android.os.Process.myUid()!=2000||ownerUid%100000<10000)throw new SecurityException("Bail de gestes réservé au service Shizuku autorisé.");
        this.ownerUid=ownerUid;user=ownerUid/100000;
        if(shellContext.checkPermission("android.permission.WRITE_SECURE_SETTINGS",android.os.Process.myPid(),android.os.Process.myUid())!=android.content.pm.PackageManager.PERMISSION_GRANTED)
            throw new SecurityException("Android refuse l’activation temporaire des gestes. Aucun écran ne sera réveillé.");
        root=Paths.get("/data/local/tmp/phone-mcp-"+ownerUid);ledger=root.resolve("accessibility-gesture-lease.json");
        StructStat directory=Os.lstat(root.toString());
        if(!OsConstants.S_ISDIR(directory.st_mode)||directory.st_uid!=2000||(directory.st_mode&0777)!=0700)
            throw new SecurityException("Dossier privé du bail de gestes indisponible.");
        open(); // Recover a killed process; this does not acquire/enable a new service.
    }
    static Exception reflectionFailure(String operation,Throwable failure){
        Throwable cause=failure;
        for(int i=0;i<16;i++){
            Throwable target=cause instanceof InvocationTargetException?((InvocationTargetException)cause).getTargetException()
                    :cause instanceof UndeclaredThrowableException?((UndeclaredThrowableException)cause).getUndeclaredThrowable():null;
            if(target==null||target==cause)break;cause=target;
        }
        String message=cause.getMessage();
        if(message!=null)message=message.replace('\r',' ').replace('\n',' ').trim();
        String detail=cause.getClass().getSimpleName()+(message==null||message.isEmpty()?"":": "+message);
        if(detail.length()>700)detail=detail.substring(0,700)+"…";
        String text=operation+" : "+detail;
        return cause instanceof SecurityException?new SecurityException(text,cause):new IllegalStateException(text,cause);
    }
    // UserServices are shell processes, not AMS-registered app processes. The regular
    // resolver acquisition passes an IApplicationThread that AMS cannot resolve. Match
    // AOSP SettingsCmd's external-provider lifetime instead; never attachApplication.
    static Bundle callSecureSetting(boolean write,String name,String value,int user)throws Exception {
        if(android.os.Process.myUid()!=2000)throw new SecurityException("Réglages réservés au service Shizuku ADB.");
        if(user<0||user>Integer.MAX_VALUE/100000)throw new SecurityException("Utilisateur Settings non borné.");
        boolean services=Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES.equals(name);
        if(!services&&(write||!Settings.Secure.ACCESSIBILITY_ENABLED.equals(name)))
            throw new SecurityException("Réglage hors du bail de gestes.");
        if(write&&value!=null&&value.length()>AccessibilityLeasePolicy.MAX_SETTING_CHARS)
            throw new IllegalStateException("Liste d’accessibilité trop volumineuse. Aucun réglage modifié.");
        Class<?> managerApi=Class.forName("android.app.IActivityManager");
        Object manager=Class.forName("android.app.ActivityManager").getMethod("getService").invoke(null);
        Method acquire=managerApi.getMethod("getContentProviderExternal",String.class,int.class,IBinder.class,String.class);
        Method release=managerApi.getMethod("removeContentProviderExternalAsUser",String.class,IBinder.class,int.class);
        IBinder token=new Binder();boolean attempted=false;Exception failure=null;
        long identity=Binder.clearCallingIdentity();
        try{
            attempted=true;
            Object holder=acquire.invoke(manager,"settings",user,token,"PhoneMCP:gesture-lease");
            if(holder==null)throw new IllegalStateException("Fournisseur Settings externe indisponible.");
            Object provider=holder.getClass().getField("provider").get(holder);
            if(provider==null)throw new IllegalStateException("Fournisseur Settings externe non connecté.");
            Bundle args=new Bundle();args.putInt("_user",user);
            if(write)args.putString("value",value);
            String method=write?"PUT_secure":"GET_secure";
            Class<?> providerApi=Class.forName("android.content.IContentProvider");
            Bundle result;
            if(Build.VERSION.SDK_INT>=31){
                Class<?> sourceType=Class.forName("android.content.AttributionSource");
                Object source=sourceType.getConstructor(int.class,String.class,String.class)
                        .newInstance(android.os.Process.myUid(),"com.android.shell",null);
                result=(Bundle)providerApi.getMethod("call",sourceType,String.class,String.class,String.class,Bundle.class)
                        .invoke(provider,source,"settings",method,name,args);
            }else{
                // API30 predates AttributionSource; its six-argument provider protocol
                // still supplies the same fixed shell package, authority and user.
                result=(Bundle)providerApi.getMethod("call",String.class,String.class,String.class,String.class,String.class,Bundle.class)
                        .invoke(provider,"com.android.shell",null,"settings",method,name,args);
            }
            if(!write&&result==null)throw new IllegalStateException("Réponse Settings externe absente.");
            return result;
        }catch(Exception failed){failure=failed;throw failed;}
        finally{
            try{
                // Also attempt cleanup after an uncertain acquire/call return. No provider
                // reference or external handle is held while the service is idle.
                if(attempted)try{release.invoke(manager,"settings",token,user);}
                catch(Exception cleanup){if(failure!=null)failure.addSuppressed(cleanup);else throw cleanup;}
            }finally{Binder.restoreCallingIdentity(identity);}
        }
    }
    private String read(String name)throws Exception {
        try{return callSecureSetting(false,name,null,user).getString("value");}
        catch(Exception failed){throw reflectionFailure("Lecture Settings "+name,failed);}
    }
    private void writeServices(String value)throws Exception {
        try{callSecureSetting(true,Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,value,user);}
        catch(Exception failed){throw reflectionFailure("Activation temporaire du composant de gestes",failed);}
    }
    private static void requirePrivateFile(Path path)throws Exception {
        StructStat stat=Os.lstat(path.toString());
        if(!OsConstants.S_ISREG(stat.st_mode)||stat.st_uid!=2000||(stat.st_mode&0777)!=0600||stat.st_nlink!=1)
            throw new SecurityException("Journal privé du bail de gestes invalide.");
    }
    private void acquireLock()throws Exception {
        Path path=root.resolve("accessibility-gesture-lease.lock");
        try{Files.createFile(path,PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));}
        catch(FileAlreadyExistsException existing){requirePrivateFile(path);}
        requirePrivateFile(path);lockChannel=FileChannel.open(path,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);
        processLock=lockChannel.tryLock();
        if(processLock==null)throw new IllegalStateException("Un autre processus possède le bail de gestes temporaires.");
    }
    private void releaseLock(){
        try{if(processLock!=null)processLock.release();}catch(Exception ignored){}processLock=null;
        try{if(lockChannel!=null)lockChannel.close();}catch(Exception ignored){}lockChannel=null;
    }
    private boolean load()throws Exception {
        if(!Files.exists(ledger,LinkOption.NOFOLLOW_LINKS))return false;
        requirePrivateFile(ledger);String json;
        try(FileChannel channel=FileChannel.open(ledger,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)){
            long size=channel.size();if(size<2||size>4096)throw new SecurityException("Journal du bail de gestes trop volumineux.");
            ByteBuffer bytes=ByteBuffer.allocate((int)size);
            while(bytes.hasRemaining())if(channel.read(bytes)<0)throw new SecurityException("Journal du bail de gestes incomplet.");
            if(channel.size()!=size)throw new SecurityException("Journal du bail de gestes modifié pendant sa lecture.");
            bytes.flip();json=StandardCharsets.UTF_8.decode(bytes).toString();
        }
        JSONObject record=new JSONObject(json);
        if(record.getInt("version")!=1||record.getInt("ownerUid")!=ownerUid||record.getInt("userId")!=user
                ||!AccessibilityLeasePolicy.COMPONENT.equals(record.getString("component")))
            throw new SecurityException("Propriétaire du bail de gestes invalide.");
        return record.getBoolean("added");
    }
    private void save(boolean added)throws Exception {
        byte[] data=new JSONObject().put("version",1).put("ownerUid",ownerUid).put("userId",user)
                .put("component",AccessibilityLeasePolicy.COMPONENT).put("added",added).toString().getBytes(StandardCharsets.UTF_8);
        Path temp=Files.createTempFile(root,"accessibility-gesture-lease-",".tmp",PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        try{
            requirePrivateFile(temp);
            try(FileChannel channel=FileChannel.open(temp,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS)){
                ByteBuffer bytes=ByteBuffer.wrap(data);while(bytes.hasRemaining())channel.write(bytes);channel.force(true);
            }
            try{Files.move(temp,ledger,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException unsupported){Files.move(temp,ledger,StandardCopyOption.REPLACE_EXISTING);}
            FileDescriptor directory=Os.open(root.toString(),OsConstants.O_RDONLY|OsConstants.O_NOFOLLOW|OsConstants.O_CLOEXEC,0);
            try{Os.fsync(directory);}finally{Os.close(directory);}
        }finally{Files.deleteIfExists(temp);}
    }
    private void open()throws Exception {
        if(processLock!=null)return;
        try{
            acquireLock();
            policy=new AccessibilityLeasePolicy(new AccessibilityLeasePolicy.Backend(){
                @Override public String services()throws Exception{return read(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);}
                @Override public void services(String value)throws Exception{writeServices(value);}
            },this::save,load());
            policy.release();cleanupPending=false;
            globalBefore=read(Settings.Secure.ACCESSIBILITY_ENABLED);
        }catch(Exception failure){policy=null;releaseLock();throw reflectionFailure("Initialisation du bail de gestes",failure);}
    }
    // The real APK process owns the ContentObserver: ContentService also requires an
    // AMS-registered PID for observation. Only its private Binder callback invokes this.
    // The setting is still re-read before every acquire/ready, with no idle polling.
    synchronized void settingsChanged()throws Exception {
        settingsChangeNotifications++;cachedReady=false;
        if(policy==null)return;
        try{policy.observeExternalChange();externallyRevoked|=policy.revoked();}
        catch(Exception failed){
            Exception reported=reflectionFailure("Modification des réglages de gestes non confirmée",failed);
            lastError=reported.getMessage();throw reported;
        }
    }
    // Only the Manager's explicit local start calls this; no setting or in-flight gesture changes.
    synchronized void beginLocalSession(){
        externallyRevoked=false;cachedReady=false;lastError="";
        if(policy!=null)policy.beginLocalSession();
    }
    synchronized void acquire()throws Exception {
        if(externallyRevoked)throw new SecurityException("Le service de gestes a été désactivé localement. Redémarrez la session sur le téléphone.");
        open();
        try{policy.acquire();cachedReady=isBound();lastError="";}
        catch(Exception failed){
            externallyRevoked|=policy!=null&&policy.revoked();
            try{close();}catch(Exception recovery){failed.addSuppressed(recovery);}
            Exception reported=reflectionFailure("Activation des gestes non confirmée (écran physique protégé)",failed);
            lastError=reported.getMessage();throw reported;
        }
    }
    private boolean isBound()throws Exception {
        try{
        IBinder binder=(IBinder)Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,"accessibility");
        Class<?> api=Class.forName("android.view.accessibility.IAccessibilityManager");
        Object manager=Class.forName("android.view.accessibility.IAccessibilityManager$Stub").getMethod("asInterface",IBinder.class).invoke(null,binder);
        Object infos=api.getMethod("getEnabledAccessibilityServiceList",int.class,int.class).invoke(manager,AccessibilityServiceInfo.FEEDBACK_ALL_MASK,user);
        for(Object value:(List<?>)infos){
            AccessibilityServiceInfo info=(AccessibilityServiceInfo)value;
            if(AccessibilityLeasePolicy.ours(info.getId()))
                return (info.getCapabilities()&AccessibilityServiceInfo.CAPABILITY_CAN_PERFORM_GESTURES)!=0;
        }
        return false;
        }catch(Exception failed){throw reflectionFailure("Confirmation du service de gestes lié",failed);}
    }
    synchronized boolean ready()throws Exception {
        if(policy==null||!policy.active()||externallyRevoked){cachedReady=false;return false;}
        policy.observeExternalChange();externallyRevoked|=policy.revoked();
        cachedReady=policy.active()&&!externallyRevoked&&isBound();return cachedReady;
    }
    synchronized JSONObject state()throws Exception {
        return new JSONObject().put("component",AccessibilityLeasePolicy.COMPONENT).put("active",policy!=null&&policy.active())
                .put("addedBySession",policy!=null&&policy.ownsAddition()).put("preexisting",policy!=null&&policy.preexisting())
                .put("lastConfirmedBound",cachedReady).put("externallyRevoked",externallyRevoked).put("cleanupPending",cleanupPending)
                .put("settingsCallerPackage","com.android.shell").put("settingsCallerUid",android.os.Process.myUid())
                .put("settingsTransport","external_provider_binder").put("settingsProviderHeldWhileIdle",false)
                .put("shellContentObserverUsed",false).put("preCommandSettingsReadback",true).put("settingsChangeNotifications",settingsChangeNotifications)
                .put("globalAccessibilitySettingWritten",false).put("globalAccessibilityBefore",globalBefore==null?JSONObject.NULL:globalBefore)
                .put("error",lastError);
    }
    synchronized void close()throws Exception {
        Exception failure=null;
        try{if(policy!=null)policy.release();cleanupPending=false;}
        catch(Exception failed){failure=failed;cleanupPending=true;lastError="Retrait du service temporaire en attente du prochain démarrage Shizuku.";}
        cachedReady=false;
        policy=null;releaseLock();
        if(failure!=null)throw failure;
    }
}




