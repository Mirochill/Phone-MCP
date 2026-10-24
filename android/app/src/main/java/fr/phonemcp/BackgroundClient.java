package fr.phonemcp;

import android.content.*;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import rikka.shizuku.Shizuku;

final class BackgroundClient {
    static volatile String lastError="";
    private final Context context;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService calls=Executors.newFixedThreadPool(6);
    private final ExecutorService control=Executors.newSingleThreadExecutor();
    private final ExecutorService screenEvents=Executors.newSingleThreadExecutor();
    private final ExecutorService previews=Executors.newSingleThreadExecutor();
    private final ExecutorService captures=Executors.newFixedThreadPool(2);
    private final AtomicBoolean settingsNotificationPending=new AtomicBoolean();
    private final AtomicBoolean settingsNotificationDirty=new AtomicBoolean();
    private volatile boolean settingsObserving,settingsObserverRegistered;
    private volatile String settingsObserverError="";
    private final ContentObserver settingsObserver=new ContentObserver(main){
        @Override public void onChange(boolean selfChange){queueAccessibilitySettingsChanged();}
        @Override public void onChange(boolean selfChange,Uri uri){queueAccessibilitySettingsChanged();}
    };
    private final Set<String> controlledPackages=ConcurrentHashMap.newKeySet();
    private volatile String latestPackage;
    private final Map<String,JSONObject> sessions=new ConcurrentHashMap<>();
    private void acceptState(JSONObject value){JSONObject previous=state;state=value;JSONArray list=value.optJSONArray("sessions");if(list!=null){sessions.clear();controlledPackages.clear();for(int i=0;i<list.length();i++){JSONObject item=list.optJSONObject(i);if(item!=null){sessions.put(item.optString("sessionId"),item);controlledPackages.add(item.optString("packageName"));}}}boolean changed=previous==null;for(String key:new String[]{"ready","sessions","endedSessions","locallyBlockedPackages","physicalPanelOff","error"})if(previous!=null&&!String.valueOf(previous.opt(key)).equals(String.valueOf(value.opt(key))))changed=true;if(changed)BridgeService.controlStateChanged();}
    private void acceptSession(JSONObject result){JSONObject item=result.optJSONObject("session");if(item!=null){sessions.put(item.optString("sessionId"),item);String target=item.optString("packageName");controlledPackages.add(target);latestPackage=target;}if(result.optBoolean("ended")){gestureDispatcher.revokeLocally(result.optString("sessionId"));sessions.remove(result.optString("sessionId"));}}
    boolean controls(String id,String pkg){if(!ready()||id==null||pkg==null)return false;JSONObject entry=sessions.get(id);return entry!=null&&"active".equals(entry.optString("state"))&&pkg.equals(entry.optString("packageName"))&&SystemClock.elapsedRealtime()<entry.optLong("leaseExpiresElapsedMs");}
    String previewSession(String pkg){String found=null;for(JSONObject value:sessions.values())if(controls(value.optString("sessionId"),pkg)){if(found!=null)return null;found=value.optString("sessionId");}return found;}
    private final IPreviewCallback previewCallback=new IPreviewCallback.Stub(){
        @Override public void onSessionsChanged(String json){main.post(()->{try{JSONObject next=new JSONObject(json);JSONObject previous=state;if(previous!=null){java.util.Iterator<String> keys=previous.keys();while(keys.hasNext()){String key=keys.next();if(!next.has(key))next.put(key,previous.get(key));}}acceptState(next);}catch(Exception ignored){}});}
    };
    private final IBinder owner=new Binder();
    private volatile IBackgroundControl remote;
    private volatile JSONObject state;
    private volatile int generation;
    private volatile int transportRevision;
    private volatile String gestureEpoch=UUID.randomUUID().toString();
    private final ParallelGestureDispatcher gestureDispatcher;
    private Shizuku.UserServiceArgs serviceArgs;
    private ServiceConnection connection;
    BackgroundClient(Context c){context=c.getApplicationContext();gestureDispatcher=new ParallelGestureDispatcher(context,(epoch,id,pkg,display,revision)->{
        JSONObject entry=sessions.get(id);
        return epoch!=null&&epoch.equals(gestureEpoch)&&revision==transportRevision&&BridgeService.active()&&BridgeService.parallelEnabled()&&ready()&&permitted()&&entry!=null&&pkg.equals(entry.optString("packageName"))&&display>0&&display==entry.optInt("displayId",-1)&&"active".equals(entry.optString("state"));
    });gestureDispatcher.newEpoch(gestureEpoch);}
    static boolean permitted(){try{return Shizuku.pingBinder()&&!Shizuku.isPreV11()&&Shizuku.getUid()==2000&&Shizuku.checkSelfPermission()==PackageManager.PERMISSION_GRANTED;}catch(Exception e){return false;}}
    static String permissionStatus(){try{
        if(!Shizuku.pingBinder())return "Shizuku arrêté ou absent";
        if(Shizuku.getUid()!=2000)return "Démarrez Shizuku avec le débogage sans fil, sans root";
        return Shizuku.checkSelfPermission()==PackageManager.PERMISSION_GRANTED?"Shizuku autorisé":"Autorisation Phone MCP à accorder dans Shizuku";
    }catch(Exception e){return "Shizuku indisponible";}}
    boolean ready(){IBackgroundControl r=remote;return r!=null&&r.asBinder().isBinderAlive()&&state!=null&&state.optBoolean("ready");}
    JSONObject state(){
        try{return new JSONObject(state==null?"{}":state.toString()).put("ready",ready()).put("error",lastError.isEmpty()&&state!=null?state.optString("error"):lastError).put("shizuku",permissionStatus()).put("settingsObserver",new JSONObject().put("registered",settingsObserverRegistered).put("observing",settingsObserving).put("error",settingsObserverError));}
        catch(JSONException e){return new JSONObject();}
    }
    boolean filesReady(){return remote!=null&&remote.asBinder().isBinderAlive()&&state!=null&&state.optBoolean("fileServiceReady");}
    boolean fileJobActive(){return state!=null&&state.optBoolean("fileJobActive");}
    boolean controls(String pkg){return pkg!=null&&controlledPackages.contains(pkg)&&ready();}
    String previewPackage(){return controls(latestPackage)?latestPackage:null;}
    // Only the APK's AMS-registered process observes Settings. The shell UserService reads Settings
    // through its existing external provider Binder, without registering its own ContentObserver.
    private synchronized void ensureSettingsObserver()throws Exception{
        if(settingsObserving&&settingsObserverRegistered)return;
        if(settingsObserverRegistered){
            try{context.getContentResolver().unregisterContentObserver(settingsObserver);settingsObserverRegistered=false;}
            catch(Exception failure){settingsObserverError=observerFailure("Retrait de l’observateur de gestes non confirmé",failure);throw new IllegalStateException(settingsObserverError,failure);}
        }
        try{
            // Mark possible registration before the call so a failed/ambiguous call is cleaned up.
            settingsObserverRegistered=true;
            context.getContentResolver().registerContentObserver(android.provider.Settings.Secure.getUriFor(android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),false,settingsObserver);
            settingsObserving=true;settingsObserverError="";
        }catch(Exception failure){
            settingsObserving=false;
            try{context.getContentResolver().unregisterContentObserver(settingsObserver);settingsObserverRegistered=false;}catch(Exception ignored){}
            settingsObserverError=observerFailure("Observation des réglages de gestes indisponible",failure);
            throw new IllegalStateException(settingsObserverError,failure);
        }
    }
    private synchronized void unregisterSettingsObserver(){
        settingsObserving=false;settingsNotificationDirty.set(false);
        if(!settingsObserverRegistered)return;
        try{context.getContentResolver().unregisterContentObserver(settingsObserver);settingsObserverRegistered=false;}
        catch(Exception failure){settingsObserverError=observerFailure("Retrait de l’observateur de gestes non confirmé",failure);}
    }
    private static String observerFailure(String detail,Exception failure){
        String type=failure.getClass().getSimpleName();return detail+" ("+type.substring(0,Math.min(64,type.length()))+").";
    }
    private void queueAccessibilitySettingsChanged(){
        final IBackgroundControl service=remote;final int expected=generation;
        if(!settingsObserving||service==null||!service.asBinder().isBinderAlive()||!BridgeService.active()||control.isShutdown())return;
        settingsNotificationDirty.set(true);
        if(!settingsNotificationPending.compareAndSet(false,true))return;
        try{control.execute(()->{
            try{
                if(expected!=generation||service!=remote||!settingsObserving||!BridgeService.active()||!service.asBinder().isBinderAlive())return;
                settingsNotificationDirty.set(false);
                service.accessibilitySettingsChanged();
                if(expected==generation&&service==remote&&settingsObserving)settingsObserverError="";
            }catch(Exception failure){if(expected==generation&&service==remote&&settingsObserving)settingsObserverError=observerFailure("Modification des réglages de gestes non transmise",failure);}
            finally{
                settingsNotificationPending.set(false);
                // A real setting event received during the Binder call gets one coalesced follow-up.
                if(settingsNotificationDirty.get())queueAccessibilitySettingsChanged();
            }
        });}catch(RejectedExecutionException stopping){settingsNotificationPending.set(false);settingsNotificationDirty.set(false);}
    }
    void start(long deadline,boolean screenOff,boolean parallelDisplay,PhoneAccessibilityService.Reply reply){
        IBackgroundControl running=remote;
        if(running!=null&&running.asBinder().isBinderAlive()&&permitted()){
            try{ensureSettingsObserver();}catch(Exception failure){String error=message(failure);close();lastError=error;reply.done(null,error);return;}
            final int expected=++generation;gestureEpoch=UUID.randomUUID().toString();gestureDispatcher.newEpoch(gestureEpoch);transportRevision=0;state=null;lastError="";controlledPackages.clear();sessions.clear();latestPackage=null;
            calls.execute(()->{try{running.stop();running.setPreviewCallback(previewCallback);running.setGestureDispatcher(gestureDispatcher,gestureEpoch);Settings.ScreenChoice choice=new Settings(context).screenChoice();unwrap(running.setPhysicalScreenAwake(choice.keepOn,choice.revision));JSONObject result=unwrap(running.start(deadline,owner,screenOff,parallelDisplay));main.post(()->{if(expected==generation){acceptState(result);reply.done(result,null);}});}catch(Exception e){main.post(()->{if(expected==generation){lastError=message(e);reply.done(null,lastError);}});}});
            return;
        }
        close();lastError="";final int expected=generation;
        if(!permitted()){lastError=permissionStatus();reply.done(null,lastError);return;}
        try{ensureSettingsObserver();}catch(Exception failure){lastError=message(failure);reply.done(null,lastError);return;}
        AtomicBoolean finished=new AtomicBoolean();
        PhoneAccessibilityService.Reply once=(result,error)->{if(expected==generation&&finished.compareAndSet(false,true))reply.done(result,error);};
        serviceArgs=new Shizuku.UserServiceArgs(new ComponentName(context,BackgroundControlService.class)).daemon(false).processNameSuffix("parallel").debuggable(false).version(BuildConfig.VERSION_CODE);
        connection=new ServiceConnection(){
            @Override public void onServiceConnected(ComponentName name,IBinder binder){
                if(BackgroundClient.this.connection!=this)return;
                IBackgroundControl candidate=IBackgroundControl.Stub.asInterface(binder);remote=candidate;
                calls.execute(()->{
                    try{candidate.setPreviewCallback(previewCallback);candidate.setGestureDispatcher(gestureDispatcher,gestureEpoch);Settings.ScreenChoice choice=new Settings(context).screenChoice();unwrap(candidate.setPhysicalScreenAwake(choice.keepOn,choice.revision));JSONObject result=unwrap(candidate.start(deadline,owner,screenOff,parallelDisplay));main.post(()->{if(expected!=generation)return;acceptState(result);once.done(result,null);});}
                    catch(Exception e){main.post(()->{if(expected!=generation)return;lastError=message(e);once.done(null,lastError);});}
                });
            }
            @Override public void onServiceDisconnected(ComponentName name){if(BackgroundClient.this.connection==this){remote=null;state=null;unregisterSettingsObserver();lastError="Le service Shizuku s’est arrêté. Relancez une session.";}}
        };
        calls.execute(()->main.post(()->{
            if(expected!=generation)return;
            try{Shizuku.bindUserService(serviceArgs,connection);}catch(Exception e){unregisterSettingsObserver();lastError=message(e);once.done(null,lastError);}
        }));
        main.postDelayed(()->{
            if(expected==generation&&finished.compareAndSet(false,true)){
                lastError="Démarrage Shizuku trop long. Ouvrez Shizuku puis relancez la session.";
                String error=lastError;close();reply.done(null,error);
            }
        },25000);
    }
    void refresh(PhoneAccessibilityService.Reply reply){
        IBackgroundControl service=remote;int expected=generation;
        if(service==null||!permitted()){if(service!=null)lastError=permissionStatus();reply.done(state(),null);return;}
        calls.execute(()->{
            try{JSONObject leases=new JSONObject().put("allowedLeasePackages",allowedLeases()).put("captureLeasePackages",captureGrants());JSONObject result=unwrap(service.execute("status",leases.toString()));main.post(()->{if(expected!=generation)return;acceptState(result);reply.done(state(),null);});}
            catch(Exception e){main.post(()->{if(expected!=generation)return;state=null;lastError=message(e);reply.done(state(),null);});}
        });
    }
    void physicalScreenState(boolean on,boolean userPresent){
        final IBackgroundControl service=remote;final int expected=generation;
        if(service==null||!permitted()||screenEvents.isShutdown())return;
        try{screenEvents.execute(()->{
            if(expected!=generation||service!=remote)return;
            try{JSONObject value=unwrap(service.physicalScreenState(on,userPresent));main.post(()->{if(expected==generation)acceptState(value);});}
            catch(Exception error){main.post(()->{if(expected==generation)lastError=message(error);});}
        });}catch(RejectedExecutionException stopping){/* The local service is already stopping. */}
    }
    void applyScreenChoice(PhoneAccessibilityService.Reply reply){
        final IBackgroundControl service=remote;final int expected=generation;
        if(service==null||!BridgeService.active()||!permitted()){reply.done(new JSONObject(),null);return;}
        control.execute(()->{try{if(expected!=generation||service!=remote)throw new IllegalStateException("Réglage enregistré. Relancez la session pour l’appliquer.");Settings.ScreenChoice choice=new Settings(context).screenChoice();JSONObject result=unwrap(service.setPhysicalScreenAwake(choice.keepOn,choice.revision));main.post(()->{if(expected==generation){acceptState(result);reply.done(result,null);}});}catch(Exception error){main.post(()->reply.done(null,"Réglage enregistré. Relancez la session pour l’appliquer."));}});
    }
    void restorePanel(PhoneAccessibilityService.Reply reply){
        IBackgroundControl service=remote;int expected=generation;
        if(service==null){reply.done(null,"Service parallèle arrêté");return;}
        calls.execute(()->{
            try{JSONObject result=unwrap(service.restorePanel());main.post(()->{if(expected!=generation)return;acceptState(result);reply.done(result,null);});}
            catch(Exception e){main.post(()->{if(expected==generation)reply.done(null,message(e));});}
        });
    }
    private JSONArray allowedLeases(){
        Settings settings=new Settings(context);JSONArray allowed=new JSONArray();
        for(String pkg:controlledPackages){if(settings.allowed(pkg,"read")&&settings.allowed(pkg,"write"))allowed.put(pkg);else controlledPackages.remove(pkg);}
        return allowed;
    }
    private JSONArray captureGrants(){Settings settings=new Settings(context);JSONArray values=new JSONArray();for(String pkg:settings.packages())if(settings.allowed(pkg,"read")&&settings.allowed(pkg,"capture"))values.put(pkg);return values;}
    private JSONObject previewArguments(String id,String pkg)throws Exception{
        Settings settings=new Settings(context);
        if(!BridgeService.parallelEnabled()||!controls(id,pkg)||!permitted()||!settings.allowed(pkg,"read")||!settings.allowed(pkg,"capture"))throw new SecurityException("Aperçu indisponible pour cette app.");
        JSONArray captures=captureGrants();
        return new JSONObject().put("packageName",pkg).put("sessionId",id).put("_local",true).put("capturePackages",captures);
    }
    void previewSurface(String id,String pkg,String viewer,android.view.SurfaceControl parent,int width,int height,PhoneAccessibilityService.Reply reply){
        final int expected=generation;final IBackgroundControl service=remote;
        try{
            JSONObject args=previewArguments(id,pkg).put("viewerId",viewer);if(parent!=null)args.put("width",width).put("height",height);
            String payload=args.toString();
            previews.execute(()->{try{
                if(expected!=generation)throw new IllegalStateException("Session arrêtée.");previewArguments(id,pkg);
                JSONObject result=unwrap(parent==null?service.renewPreview(payload):service.attachPreview(payload,parent));
                main.post(()->{if(expected!=generation||!BridgeService.canPreview(id,pkg)){detachPreview(viewer);reply.done(null,"Aperçu arrêté.");}else reply.done(result,null);});
            }catch(Exception error){main.post(()->reply.done(null,message(error)));}});
        }catch(Exception error){detachPreview(viewer);reply.done(null,message(error));}
    }
    void detachPreview(String viewer){IBackgroundControl service=remote;if(service==null||viewer==null)return;previews.execute(()->{try{service.detachPreview(viewer);}catch(Exception ignored){}});}
    void controlMode(String mode,boolean off,long end,PhoneAccessibilityService.Reply reply){
        final int expected=generation;final IBackgroundControl service=remote;
        if(service==null||!service.asBinder().isBinderAlive()||!permitted()){reply.done(null,"Service Shizuku indisponible. Relancez une session sur le téléphone.");return;}
        calls.execute(()->{try{
            if(expected!=generation||!BridgeService.active()||!permitted())throw new IllegalStateException("Session ou autorisation arrêtée.");
            JSONObject result=unwrap(service.execute("control_mode",new JSONObject().put("mode",mode).put("panelOff",off).put("deadline",end).toString()));
            main.post(()->{if(expected==generation){acceptState(result);lastError="";reply.done(result,null);}});
        }catch(Exception error){main.post(()->{if(expected==generation){lastError=message(error);reply.done(null,lastError);}});}});
    }
    void execute(String op,JSONObject args,PhoneAccessibilityService.Reply reply){
        final int wire=transportRevision;final int expected=generation;final IBackgroundControl service=remote;
        try{
            if(!BridgeService.active()||!BridgeService.parallelEnabled()||!ready()||!permitted())throw new IllegalStateException(lastError.isEmpty()?"Mode parallèle non prêt. Consultez phone_status puis relancez la session avec Shizuku.":lastError);
            if(!Arrays.asList("open_app","open_url","snapshot","screenshot","click","long_press","set_text","scroll","back","tap","swipe","double_tap","pinch","edit_text","press_key","scroll_to_node","node_details","session_create","session_list","session_keepalive","session_end","session_cancel","session_cleanup","session_resume_local","session_handoff_local","session_wake","action_sequence","wait_and_click","ad_start","ad_status","ad_cancel").contains(op))throw new IllegalArgumentException("Commande parallèle inconnue.");
            args.put("_transportRevision",wire);
            if(op.startsWith("session_")){
                if("session_create".equals(op)||"session_cleanup".equals(op)||"session_wake".equals(op)){String target=args.getString("packageName");Settings caps=new Settings(context);if(!caps.allowed(target,"read")||!caps.allowed(target,"write"))throw new SecurityException("Application not authorized for agent control.");}
                String payload=args.toString();java.util.concurrent.ExecutorService executor=("session_end".equals(op)||"session_cancel".equals(op)||"session_handoff_local".equals(op))?control:calls;
                executor.execute(()->{try{if(expected!=generation||wire!=transportRevision||!BridgeService.active())throw new SecurityException("Control stopped.");JSONObject result=unwrap(service.execute(op,payload));main.post(()->{if(expected==generation){acceptSession(result);reply.done(result,null);}});}catch(Exception e){main.post(()->reply.done(null,message(e)));}});return;
            }
            String pkg=args.getString("packageName");Settings settings=new Settings(context);
            String permission="screenshot".equals(op)?"capture":("snapshot".equals(op)||"node_details".equals(op)||"ad_status".equals(op))?"read":"write";
            if(!settings.allowed(pkg,"read")||!settings.allowed(pkg,permission)||(args.optBoolean("includeScreenshot")&&!settings.allowed(pkg,"capture")))throw new SecurityException("Application ou capacité non autorisée.");
            JSONObject forwarded=new JSONObject(args.toString());
            if("ad_start".equals(op)){if(args.optBoolean("visual")&&!settings.allowed(pkg,"capture"))throw new SecurityException("Capture non autorisée pour cet autopilote visuel.");forwarded.put("_adVisualCaptureGranted",settings.allowed(pkg,"capture")).put("capturePackages",captureGrants());}
            if("screenshot".equals(op)||args.optBoolean("includeScreenshot"))forwarded.put("capturePackages",captureGrants());
            final String payload=forwarded.toString();
            ("ad_cancel".equals(op)?control:calls).execute(()->{
                try{if(expected!=generation||wire!=transportRevision||!BridgeService.active()||!permitted())throw new IllegalStateException("Session ou autorisation arrêtée avant envoi.");JSONObject result=unwrap(service.execute(op,payload));main.post(()->{if(expected==generation){acceptSession(result);if(("open_app".equals(op)||"open_url".equals(op))&&!pkg.equals(context.getPackageName())){controlledPackages.add(pkg);latestPackage=pkg;}reply.done(result,null);}});}
                catch(Exception e){main.post(()->{if(expected==generation)reply.done(null,message(e));});}
            });
        }catch(Exception e){reply.done(null,message(e));}
    }
    void savePreviewImage(String id,String pkg,android.os.ParcelFileDescriptor output,PhoneAccessibilityService.Reply reply){
        final int expected=generation;final IBackgroundControl service=remote;
        captures.execute(()->{try{if(expected!=generation||service==null)throw new SecurityException("Session arrêtée.");JSONObject result=unwrap(service.savePreviewImage(previewArguments(id,pkg).toString(),output));if(expected!=generation||!controls(id,pkg))throw new SecurityException("Capture arrêtée.");main.post(()->reply.done(result,null));}catch(Exception error){main.post(()->reply.done(null,message(error)));}});
    }
    void attachRecording(String id,String pkg,String token,android.view.Surface output,PhoneAccessibilityService.Reply reply){
        final int expected=generation;final IBackgroundControl service=remote;
        captures.execute(()->{try{if(expected!=generation||service==null)throw new SecurityException("Session arrêtée.");JSONObject result=unwrap(service.attachRecording(previewArguments(id,pkg).put("recordingId",token).toString(),output));if(expected!=generation||!controls(id,pkg)){service.detachRecording(token);throw new SecurityException("Capture arrêtée.");}main.post(()->reply.done(result,null));}catch(Exception error){main.post(()->reply.done(null,message(error)));}});
    }
    void renewRecording(String id,String pkg,String token,PhoneAccessibilityService.Reply reply){
        final int expected=generation;final IBackgroundControl service=remote;
        captures.execute(()->{try{if(expected!=generation||service==null)throw new SecurityException("Session arrêtée.");JSONObject result=unwrap(service.renewRecording(previewArguments(id,pkg).put("recordingId",token).toString()));main.post(()->reply.done(result,null));}catch(Exception error){main.post(()->reply.done(null,message(error)));}});
    }
    void detachRecording(String token){IBackgroundControl service=remote;if(service!=null&&!captures.isShutdown())try{captures.execute(()->{try{service.detachRecording(token);}catch(Exception ignored){}});}catch(java.util.concurrent.RejectedExecutionException ignored){}}
    void previewFrame(String id,String pkg,PhoneAccessibilityService.Reply reply){
        final int expected=generation;final IBackgroundControl service=remote;
        try{String args=previewArguments(id,pkg).toString();previews.execute(()->{try{
            if(expected!=generation)throw new SecurityException("Session stopped.");previewArguments(id,pkg);
            JSONObject frame=unwrap(service.previewFrame(args));main.post(()->{if(expected!=generation||!BridgeService.canPreview(id,pkg))reply.done(null,"Preview stopped.");else reply.done(frame,null);});
        }catch(Exception e){main.post(()->reply.done(null,message(e)));}});}catch(Exception e){reply.done(null,message(e));}
    }
    void diagnostics(String op,JSONObject args,PhoneAccessibilityService.Reply reply){
        final int expected=generation;final IBackgroundControl service=remote;
        try{JSONObject app=ResourceDiagnostics.sample(context);
            if(service==null||!permitted())throw new IllegalStateException("Shizuku diagnostic unavailable.");
            calls.execute(()->{try{JSONObject result=unwrap(service.execute(op,args.toString()));main.post(()->{if(expected==generation){try{reply.done("diagnostics".equals(op)?new JSONObject().put("app",app).put("shizuku",result).put("bridge",BridgeService.resourceState()):result,null);}catch(Exception e){reply.done(null,message(e));}}});}catch(Exception e){main.post(()->reply.done(null,message(e)));}});
        }catch(Exception e){reply.done(null,message(e));}
    }
    void transportDisconnected(){int revision=++transportRevision;IBackgroundControl service=remote;if(service!=null)control.execute(()->{try{service.execute("transport_disconnected",new JSONObject().put("revision",revision).toString());}catch(Exception ignored){}});controlledPackages.clear();sessions.clear();latestPackage=null;try{if(state!=null)state.put("sessions",new JSONArray());}catch(Exception ignored){}BridgeService.controlStateChanged();}
    void localStop(String id,String pkg,PhoneAccessibilityService.Reply reply){
        try{if(id!=null){gestureDispatcher.revokeLocally(id);sessions.remove(id);}JSONObject args=new JSONObject().put("_local",true).put("packageName",pkg);if(id!=null&&!id.isEmpty())args.put("sessionId",id);execute("session_cancel",args,(result,error)->{controlledPackages.remove(pkg);if(pkg.equals(latestPackage))latestPackage=null;reply.done(result,error);});}catch(Exception e){reply.done(null,message(e));}
    }
    void localHandoff(String id,String pkg,PhoneAccessibilityService.Reply reply){
        try{
            JSONObject stopped=sessions.get(id);if(!controls(id,pkg)&&(stopped==null||!pkg.equals(stopped.optString("packageName"))||!"handoff_failed".equals(stopped.optString("state"))))throw new SecurityException("Cette session ne contrôle plus cette page.");
            // Revoke local gesture authorization immediately, before the Binder request can queue.
            gestureDispatcher.revokeLocally(id);sessions.remove(id);
            JSONObject args=new JSONObject().put("_local",true).put("sessionId",id).put("packageName",pkg);
            execute("session_handoff_local",args,(result,error)->{
                boolean other=false;for(JSONObject entry:sessions.values())if(pkg.equals(entry.optString("packageName")))other=true;
                if(!other){controlledPackages.remove(pkg);if(pkg.equals(latestPackage))latestPackage=null;}
                reply.done(result,error);
            });
        }catch(Exception e){reply.done(null,message(e));}
    }
    void localResume(String pkg,PhoneAccessibilityService.Reply reply){try{execute("session_resume_local",new JSONObject().put("_local",true).put("packageName",pkg),reply);}catch(Exception e){reply.done(null,message(e));}}
    void executeFiles(String op,JSONObject args,PhoneAccessibilityService.Reply reply){
        final int expected=generation;final IBackgroundControl service=remote;
        try{
            if(!BridgeService.active()||!filesReady()||!permitted())throw new IllegalStateException("Démarrez Shizuku puis relancez la session pour les fichiers et APK.");
            if(!Arrays.asList("files_list","file_job","download_file","install_apk","file_upload_begin","file_upload_chunk","file_upload_commit","files_cancel_all").contains(op))throw new IllegalArgumentException("Commande fichiers inconnue.");
            if(!new Settings(context).filesAllowed()&&!"files_cancel_all".equals(op))throw new SecurityException("Activez Fichiers et APK dans les réglages de Phone MCP.");
            String payload=args.toString();
            calls.execute(()->{try{
                if(expected!=generation||!BridgeService.active()||!permitted()||(!new Settings(context).filesAllowed()&&!"files_cancel_all".equals(op)))throw new SecurityException("Session ou autorisation fichiers arrêtée.");
                JSONObject result=unwrap(service.execute(op,payload));JSONObject next=unwrap(service.execute("status","{}"));
                main.post(()->{if(expected==generation){acceptState(next);reply.done(result,null);}});
            }catch(Exception e){main.post(()->{if(expected==generation)reply.done(null,message(e));});}});
        }catch(Exception e){reply.done(null,message(e));}
    }
    private JSONObject unwrap(String response)throws Exception{
        JSONObject data=new JSONObject(response);if(!data.optBoolean("ok"))throw new IllegalStateException(data.optString("error","Service Shizuku indisponible"));JSONObject result=data.getJSONObject("result");
        Settings settings=new Settings(context);JSONArray windows=result.optJSONArray("windows");if(windows!=null){JSONArray visible=new JSONArray();for(int i=0;i<windows.length();i++){JSONObject window=windows.getJSONObject(i);if(settings.allowed(window.optString("packageName"),"read"))visible.put(window);}result.put("windows",visible);}
        if(!settings.filesAllowed())result.remove("activeFileJob");return result;
    }
    private static String message(Exception e){return e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();}
    void close(){
        unregisterSettingsObserver();
        generation++;gestureEpoch=UUID.randomUUID().toString();gestureDispatcher.newEpoch(gestureEpoch);transportRevision=0;state=null;controlledPackages.clear();sessions.clear();latestPackage=null;IBackgroundControl old=remote;remote=null;
        Shizuku.UserServiceArgs oldArgs=serviceArgs;ServiceConnection oldConnection=connection;
        serviceArgs=null;connection=null;
        if(oldArgs==null||oldConnection==null)return;
        control.execute(()->{
            try{if(old!=null&&old.asBinder().isBinderAlive())old.stop();}catch(Exception ignored){}
            main.post(()->{try{Shizuku.unbindUserService(oldArgs,oldConnection,true);}catch(Exception ignored){}});
        });
    }
    void destroy(){close();calls.shutdownNow();captures.shutdownNow();previews.shutdownNow();screenEvents.shutdownNow();control.shutdown();}
}


