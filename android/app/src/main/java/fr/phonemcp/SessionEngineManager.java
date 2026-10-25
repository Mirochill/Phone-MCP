package fr.phonemcp;

import android.content.*;
import android.os.*;
import android.app.KeyguardManager;
import android.view.SurfaceControl;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

/** One owned display/snapshot lane per agent; a shared Android automation registration. */
@android.annotation.SuppressLint("NewApi")
final class SessionEngineManager {
    private static final int MAX_SESSIONS=4;
    private final Context ownerContext;
    private volatile Context context;
    private final ConcurrentHashMap<String,Entry> sessions=new ConcurrentHashMap<>();
    private final Map<String,String> legacy=new HashMap<>();
    private final LinkedHashMap<String,JSONObject> terminal=new LinkedHashMap<>();
    private final Set<String> locallyBlocked=new HashSet<>();
    private final ScreenAwakePreference screenPreference=new ScreenAwakePreference();
    private final HandlerThread timersThread=new HandlerThread("phone-mcp-session-lifecycle");
    private final Handler timers;
    private final ExecutorService cleanup=Executors.newSingleThreadExecutor();
    private volatile long deadline;
    private volatile int transportRevision;
    private volatile boolean enabled,requestedPanelOff;
    private volatile ScreenOffController screen;
    private IPreviewCallback callback;
    private volatile IParallelGestureDispatcher gestureDispatcher;
    private volatile String gestureEpoch="";
    private AccessibilityGestureLease gestureLease;
    private String gestureLeaseError="";
    private String gestureSettingsObserverError="";
    private volatile boolean gestureLeaseLifetime;
    private final java.util.concurrent.atomic.AtomicInteger displaysDraining=new java.util.concurrent.atomic.AtomicInteger();

    private static final class Entry {
        final String id,owner,pkg,label;final int leaseSeconds;boolean keepRendering;volatile int launchMode;final VirtualEngine engine;
        volatile long expires;volatile boolean active=true,handoffInProgress,handoffFailed,endAfterHandoff;volatile String deferredEndReason="ended";volatile boolean deferredEndLocal;
        Entry(String id,String owner,String pkg,String label,int leaseSeconds,VirtualEngine engine,long expires){this.id=id;this.owner=owner;this.pkg=pkg;this.label=label;this.leaseSeconds=leaseSeconds;this.engine=engine;this.expires=expires;}
        JSONObject describe()throws Exception{return new JSONObject().put("sessionId",id).put("packageName",pkg).put("label",label).put("state",active?"active":handoffInProgress?"handoff_pending":handoffFailed?"handoff_failed":"ended").put("leaseExpiresElapsedMs",expires).put("displayId",engine.displayIdentifier()).put("navigationIsolation",launchMode==2||launchMode==3?AppLaunchIsolation.strategy():"separate_activity_instances").put("accountDataShared",true).put("processShared",true).put("taskIsolationVerified",engine.cachedIsolationVerified(pkg)).put("ownedTaskId",engine.cachedOwnedTaskId(pkg)).put("rendering",engine.rendering()).put("keepRendering",keepRendering).put("adJob",engine.adSummary()).put("planJob",engine.cachedPlanState()).put("lastWindowRecovery",engine.cachedWindowRecovery()).put("lastCapture",engine.cachedCaptureState());}
    }
    SessionEngineManager(Context ownerContext){this.ownerContext=ownerContext;TaskCleanup.initialize(ownerContext);TaskCleanup.reap();timersThread.start();timers=new Handler(timersThread.getLooper());}
    private Context shell()throws Exception{
        if(context!=null)return context;
        synchronized(this){if(context==null){Class<?> type=Class.forName("android.app.ActivityThread");Object thread=type.getMethod("currentActivityThread").invoke(null);if(thread==null)thread=type.getMethod("systemMain").invoke(null);Context system=(Context)type.getMethod("getSystemContext").invoke(thread);context=new ContextWrapper(system){@Override public String getPackageName(){return "com.android.shell";}@Override public String getOpPackageName(){return "com.android.shell";}@Override public Context getApplicationContext(){return this;}@Override public AttributionSource getAttributionSource(){return new AttributionSource.Builder(2000).setPackageName("com.android.shell").build();}@Override public int getDeviceId(){return 0;}};}return context;}
    }
    boolean isEnabled(){return enabled&&SystemClock.elapsedRealtime()<deadline;}
    Context systemContext(){return context==null?ownerContext:context;}
    JSONObject start(long end,boolean screenOff)throws Exception{return start(end,screenOff,false);}
    synchronized JSONObject start(long end,boolean screenOff,boolean localStart)throws Exception{
        stopForModeSwitch();TaskCleanup.initialize(ownerContext);TaskCleanup.reap();if(Build.VERSION.SDK_INT<34||android.os.Process.myUid()!=2000)throw new SecurityException("Separate sessions require Android 14+ and Shizuku shell.");
        Context c=shell();if(!c.getSystemService(PowerManager.class).isInteractive()||c.getSystemService(KeyguardManager.class).isKeyguardLocked())throw new SecurityException("Unlock the phone before starting control.");
        if(!SessionPolicy.validDeadline(SystemClock.elapsedRealtime(),end))throw new SecurityException("Invalid control deadline.");
        locallyBlocked.clear();terminal.clear();transportRevision=0;deadline=end;requestedPanelOff=screenOff;
        if(gestureLease==null)try{gestureLease=new AccessibilityGestureLease(c,ownerContext.getApplicationInfo().uid);}catch(Exception unavailable){gestureLeaseError=String.valueOf(unavailable.getMessage());}
        if(gestureLease!=null&&localStart)gestureLease.beginLocalSession();
        gestureLeaseLifetime=true;
        screen=new ScreenOffController(c,ownerContext.getApplicationInfo().uid,timers);
        try{screen.start(screenOff,screenPreference.keepOn());enabled=true;scheduleExpiry();return status();}
        catch(Exception failure){stop();throw failure;}
    }
    synchronized JSONObject setPhysicalScreenAwake(boolean keepOn,long revision)throws Exception{screenPreference.select(keepOn,revision);if(screen!=null)screen.selectPhysicalScreenAwake(screenPreference.keepOn());for(Entry e:sessions.values())e.engine.physicalScreenPolicyChanged();changed();return status().put("screenPreferenceRevision",screenPreference.revision());}
    private void requireEnabled(){if(!enabled||SystemClock.elapsedRealtime()>=deadline)throw new IllegalStateException("Control stopped or expired.");}
    private static String owner(JSONObject args){return args.optString("_ownerKey","legacy");}
    private synchronized Entry create(JSONObject args,boolean oldClient)throws Exception{
        requireEnabled();requireTransport(args);String pkg=args.getString("packageName");if(!AccessPolicy.validPackage(pkg)||PhoneAccessibilityService.forbiddenPackage(shell(),pkg))throw new SecurityException("Application refused.");
        String owner=owner(args),id=oldClient?UUID.randomUUID().toString():FilePolicy.id(args.getString("requestId"));
        Entry existing=sessions.get(id);if(existing!=null){check(existing,args);return existing;}
        if(terminal.containsKey(id))throw new SecurityException("Session identifier already ended; do not repeat creation.");
        if(locallyBlocked.contains(pkg))throw new SecurityException("Control was stopped on the phone. Resume this application locally before creating a new session.");
        if(sessions.size()>=MAX_SESSIONS)throw new IllegalStateException("Maximum four independent sessions. End an unused session first.");
        Intent intent=shell().getPackageManager().getLaunchIntentForPackage(pkg);if(intent==null||intent.getComponent()==null)throw new IllegalArgumentException("Application is not launchable.");
        int mode=shell().getPackageManager().getActivityInfo(intent.getComponent(),0).launchMode;
        // Single-task apps receive a nonce-verified fresh task on their own display at launch.
        int ttl=args.optInt("leaseSeconds",300);if(ttl<30||ttl>900)throw new IllegalArgumentException("Session lease must be 30 to 900 seconds.");
        ScreenOffController control=screen;if(control==null)throw new IllegalStateException("Protection écran indisponible.");
        control.beforeApp(pkg);
        VirtualEngine engine=new VirtualEngine();engine.setPowerDelegate(new VirtualEngine.PowerDelegate(){
            @Override public void wake(int displayId)throws Exception{wakeDisplay(displayId);}
            @Override public boolean physicalOff(){ScreenOffController value=screen;return value!=null&&value.isOff();}
        });
        Entry entry=new Entry(id,owner,pkg,args.optString("label",pkg),ttl,engine,Math.min(deadline,SystemClock.elapsedRealtime()+ttl*1000L));
        engine.setGestureDelegate((target,display,request)->dispatchGesture(entry,target,display,request));
        engine.setControlLostListener(()->timers.post(()->{synchronized(SessionEngineManager.this){if(entry.active&&!entry.handoffInProgress&&!entry.handoffFailed)end(entry,engine.controlLostReason(),false);}}));
        try{engine.start(deadline,false);entry.launchMode=mode;entry.keepRendering=args.optBoolean("keepRendering");engine.setKeepRendering(entry.keepRendering);sessions.put(id,entry);syncPowerOwnership();if(oldClient)legacy.put(owner+"|"+pkg,id);scheduleExpiry();changed();try{engine.registerPreviewCard(id,pkg);}catch(Exception ignored){}return entry;}
        catch(Exception error){if(sessions.containsKey(id))end(entry,"creation_failed",false);else engine.destroy();throw error;}
        finally{control.afterApp();}
    }
    private void requireTransport(JSONObject args){if(!args.optBoolean("_local")&&args.optInt("_transportRevision",0)!=transportRevision)throw new SecurityException("Transport disconnected; stale command refused.");}
    private void check(Entry entry,JSONObject args)throws Exception{
        requireEnabled();requireTransport(args);if(args.optLong("_expiresAt",Long.MAX_VALUE)<System.currentTimeMillis())throw new SecurityException("Command expired before execution.");if(!entry.active||SystemClock.elapsedRealtime()>=entry.expires)throw new SecurityException("Control session ended or lease expired.");
        if(!args.optBoolean("_local")&&!entry.owner.equals(owner(args)))throw new SecurityException("Session belongs to another OAuth authorization.");
        if(args.has("packageName")&&!entry.pkg.equals(args.getString("packageName")))throw new SecurityException("Application does not match this session.");
    }
    private Entry select(JSONObject args,boolean mayCreate)throws Exception{
        String id=args.optString("sessionId");if(id.isEmpty())synchronized(this){id=legacy.get(owner(args)+"|"+args.optString("packageName"));}
        Entry result=id==null?null:sessions.get(id);
        if(result==null&&args.optBoolean("_local")&&args.has("packageName")){for(Entry candidate:sessions.values())if(candidate.pkg.equals(args.optString("packageName"))){if(result!=null)throw new IllegalArgumentException("Choose a session identifier.");result=candidate;}}
        if(result==null&&mayCreate&&args.optString("sessionId").isEmpty())result=create(args,true);
        if(result==null)throw new SecurityException("Session is not active. Create a session, or open the authorized app first. Ended sessions cannot resume implicitly.");
        check(result,args);return result;
    }
    private synchronized void touch(Entry entry)throws Exception{checkActiveEntry(entry);entry.expires=Math.min(deadline,SystemClock.elapsedRealtime()+entry.leaseSeconds*1000L);syncPowerOwnership();scheduleExpiry();}
    private void checkActiveEntry(Entry entry){if(!entry.active||sessions.get(entry.id)!=entry||SystemClock.elapsedRealtime()>=entry.expires)throw new SecurityException("Control session stopped or expired.");}
    private synchronized void syncPowerOwnership()throws Exception{
        ScreenOffController current=screen;if(current==null)return;long now=SystemClock.elapsedRealtime(),latest=0;int count=0;
        if(enabled&&now<deadline)for(Entry value:sessions.values())if(value.active&&now<value.expires){count++;latest=Math.max(latest,Math.min(deadline,value.expires));}
        current.activeSessions(count,latest);
    }
    private void syncPowerAfterStop(){try{syncPowerOwnership();}catch(Exception failure){gestureLeaseError="Mise à jour de la veille en attente de récupération.";}}
    private synchronized Entry localStoppedSelection(JSONObject args)throws Exception{
        if(!args.optBoolean("_local")||!args.has("sessionId")||!args.has("packageName"))throw new SecurityException("Choose the exact session locally.");
        Entry entry=sessions.get(args.getString("sessionId"));if(entry==null||!entry.pkg.equals(args.getString("packageName")))throw new SecurityException("Cette page n’est plus contrôlée par cette session.");
        return entry;
    }
    private JSONObject handoffLocal(JSONObject args)throws Exception{
        final Entry entry;
        synchronized(this){
            requireEnabled();entry=localStoppedSelection(args);
            if(entry.handoffInProgress)throw new IllegalStateException("La reprise de cette page est déjà en cours.");
            if(!entry.active&&!entry.handoffFailed)throw new SecurityException("Cette session est déjà terminée.");
            entry.active=false;entry.handoffInProgress=true;entry.handoffFailed=false;locallyBlocked.add(entry.pkg);
            IParallelGestureDispatcher dispatcher=gestureDispatcher;if(dispatcher!=null)try{dispatcher.revoke(gestureEpoch,entry.id,entry.engine.displayIdentifier());}catch(Exception ignored){}
            entry.engine.revokeForHandoff();syncPowerAfterStop();changed();
        }
        try{
            JSONObject result=entry.engine.handoffToUser(entry.pkg);
            synchronized(this){entry.handoffInProgress=false;entry.handoffFailed=false;if(!entry.deferredEndLocal)locallyBlocked.remove(entry.pkg);end(entry,"handed_to_user",entry.deferredEndLocal);}
            return result.put("ended",true).put("sessionId",entry.id);
        }catch(Exception failure){
            synchronized(this){entry.handoffInProgress=false;entry.handoffFailed=true;
                if(entry.endAfterHandoff)end(entry,entry.deferredEndReason,entry.deferredEndLocal);else{syncPowerAfterStop();changed();}}
            throw failure;
        }
    }
    JSONObject execute(String op,JSONObject args)throws Exception{
        if("status".equals(op)){JSONArray capture=args.optJSONArray("captureLeasePackages");if(capture!=null){Set<String> captures=new HashSet<>();for(int i=0;i<capture.length();i++)captures.add(capture.optString(i));for(Entry e:sessions.values())if(e.engine.adVisualActive()&&!captures.contains(e.pkg))e.engine.cancelAdWork("capture_permission_revoked");for(Entry e:sessions.values())if(e.engine.planActive()&&!captures.contains(e.pkg))e.engine.cancelPlanWork("capture_permission_revoked");}JSONArray allowed=args.optJSONArray("allowedLeasePackages");if(allowed!=null){Set<String> keep=new HashSet<>();for(int i=0;i<allowed.length();i++)keep.add(allowed.optString(i));for(Entry e:sessions.values())if(!keep.contains(e.pkg))end(e,"permission_revoked",false);}return status();}
        if("session_list".equals(op))return list(owner(args));
        if("session_cleanup".equals(op)){requireEnabled();requireTransport(args);String pkg=args.getString("packageName");if(!AccessPolicy.validPackage(pkg)||PhoneAccessibilityService.forbiddenPackage(shell(),pkg))throw new SecurityException("Application refused");return TaskCleanup.clean(pkg,args.optJSONArray("taskIds"));}
        if("session_create".equals(op)){Entry e=create(args,false);return new JSONObject().put("created",true).put("session",e.describe());}
        if("session_resume_local".equals(op)){if(!args.optBoolean("_local"))throw new SecurityException("Resume must be requested on the phone.");synchronized(this){locallyBlocked.remove(args.getString("packageName"));}changed();return status();}
        if("session_handoff_local".equals(op))return handoffLocal(args);
        if("session_cancel".equals(op)&&args.optBoolean("_local")){Entry stopped=localStoppedSelection(args);end(stopped,"stopped_on_phone",true);return new JSONObject().put("ended",true).put("sessionId",stopped.id);}
        Entry entry=select(args,"open_app".equals(op)||"open_url".equals(op));
        if("session_wake".equals(op)){
            check(entry,args);String reason=args.optString("reason","app_requires_screen");
            if(!Arrays.asList("noninteractive","black_render","app_requires_screen").contains(reason))throw new IllegalArgumentException("Unknown screen fallback reason.");
            touch(entry);ScreenOffController control=screen;if(control==null)throw new IllegalStateException("Politique écran indisponible.");control.beforeApp(entry.pkg);
            try{entry.engine.execute("recover_display",args);check(entry,args);changed();return status().put("sessionId",entry.id).put("session",entry.describe()).put("woken",true).put("physicalScreenModeChanged",false).put("physicalScreenModeOwner","phone_settings");}finally{control.afterApp();}
        }
        if("session_end".equals(op)||"session_cancel".equals(op)){end(entry,args.optBoolean("_local")?"stopped_on_phone":"ended",args.optBoolean("_local"));return new JSONObject().put("ended",true).put("sessionId",entry.id);}
        if("session_keepalive".equals(op)){touch(entry);changed();return new JSONObject().put("session",entry.describe());}
        if("plan_list".equals(op)){check(entry,args);return entry.engine.listPlans(args).put("sessionId",entry.id);}
        if("plan_status".equals(op)){check(entry,args);return entry.engine.planStatus(args).put("sessionId",entry.id);}
        if("plan_cancel".equals(op)){check(entry,args);return entry.engine.cancelPlan(args).put("sessionId",entry.id);}
        if("plan_start".equals(op)){check(entry,args);touch(entry);ScreenOffController control=screen;if(control==null)throw new IllegalStateException("Protection écran indisponible.");control.beforeApp(entry.pkg);try{return entry.engine.startPlan(new JSONObject(args.toString()).put("_planLeaseExpiresElapsedMs",entry.expires)).put("sessionId",entry.id);}finally{control.afterApp();}}
        if("ad_status".equals(op)){check(entry,args);return entry.engine.adStatus(args).put("sessionId",entry.id);}
        if("ad_cancel".equals(op)){check(entry,args);return entry.engine.cancelAd(args).put("sessionId",entry.id);}
        if("ad_start".equals(op)){check(entry,args);touch(entry);ScreenOffController control=screen;if(control==null)throw new IllegalStateException("Protection écran indisponible.");control.beforeApp(entry.pkg);try{JSONObject selection=new JSONObject(args.toString()).put("_adLeaseExpiresElapsedMs",entry.expires);return entry.engine.startAd(selection).put("sessionId",entry.id);}finally{control.afterApp();}}
        check(entry,args);touch(entry);
        ScreenOffController control=screen;if(control==null)throw new IllegalStateException("Protection écran indisponible.");
        control.beforeApp(entry.pkg);
        try{JSONObject value=entry.engine.execute(op,args);check(entry,args);return value.put("sessionId",entry.id).put("session",entry.describe());}
        catch(Exception error){if(entry.active&&!entry.handoffInProgress&&!entry.handoffFailed&&entry.engine.controlLost())end(entry,entry.engine.controlLostReason(),false);if(!entry.active)throw new SecurityException("Session stopped; no new actions are permitted.");throw error;}
        finally{control.afterApp();}
    }
    synchronized void setGestureDispatcher(IParallelGestureDispatcher callback,String epoch){
        if(callback==null||!ParallelGesturePolicy.uuid(epoch))throw new SecurityException("Canal de gestes invalide.");gestureDispatcher=callback;gestureEpoch=epoch;
    }
    synchronized void accessibilitySettingsChanged(){
        // Observation is notification only. Never construct/acquire/re-enable a lease here.
        if(gestureLease==null)return;
        try{gestureLease.settingsChanged();gestureSettingsObserverError="";}
        catch(Exception failure){String type=failure.getClass().getSimpleName();gestureSettingsObserverError="Modification des réglages de gestes non confirmée ("+type.substring(0,Math.min(64,type.length()))+").";}
    }
    private void dispatchGesture(Entry entry,String pkg,int display,JSONObject request)throws Exception{
        IParallelGestureDispatcher target;String epoch;int revision;
        synchronized(this){
            requireEnabled();if(!entry.active||SystemClock.elapsedRealtime()>=entry.expires||!entry.pkg.equals(pkg)||entry.engine.displayIdentifier()!=display||display<=0)throw new SecurityException("Session de geste arrêtée.");
            if(request.optBoolean("_nativeAuthorizationOnly")){if(gestureLease!=null){gestureLease.settingsChanged();if(gestureLease.state().optBoolean("externallyRevoked"))throw new SecurityException("Gestes désactivés localement. Reprenez le contrôle depuis le téléphone.");}return;}
            if(gestureLease==null)throw new IllegalStateException("Le service de gestes temporaire est indisponible : "+gestureLeaseError);
            gestureLease.acquire();
            if(!entry.active||Thread.currentThread().isInterrupted()||SystemClock.elapsedRealtime()>=entry.expires||SystemClock.elapsedRealtime()>=request.getLong("deadlineElapsedMs"))throw new SecurityException("Session arrêtée ou commande expirée avant l’envoi du geste.");
            target=gestureDispatcher;epoch=gestureEpoch;revision=transportRevision;
        }
        // Waiting for a per-display result never holds the manager/global session lock.
        ParallelGestureChannel.dispatch(target,epoch,entry.id,pkg,display,revision,request,ownerContext.getApplicationInfo().uid);
        synchronized(this){if(gestureLease!=null)try{gestureLease.ready();}catch(Exception unknown){gestureLeaseError="État du service de gestes non confirmé.";}}
    }
    private void releaseGestureLeaseWhenUnused(){
        cleanup.execute(()->{synchronized(SessionEngineManager.this){if(gestureLease!=null&&displaysDraining.get()==0&&(!gestureLeaseLifetime||(enabled&&sessions.isEmpty())))try{gestureLease.close();}catch(Exception error){gestureLeaseError="Retrait du service de gestes en attente de récupération.";}}});
    }
    private synchronized JSONObject list(String owner)throws Exception{
        JSONArray active=new JSONArray(),ended=new JSONArray(),blocked=new JSONArray();for(Entry entry:sessions.values())if(owner==null||entry.owner.equals(owner))active.put(entry.describe());
        for(JSONObject value:terminal.values())if(owner==null||owner.equals(value.optString("_ownerKey"))){JSONObject copy=new JSONObject(value.toString());copy.remove("_ownerKey");ended.put(copy);}
        for(String pkg:locallyBlocked)blocked.put(pkg);return new JSONObject().put("sessions",active).put("endedSessions",ended).put("locallyBlockedPackages",blocked).put("maxSessions",MAX_SESSIONS);
    }
    private synchronized JSONObject status()throws Exception{
        JSONObject value=list(null);int display=-1;for(Entry entry:sessions.values()){display=entry.engine.displayIdentifier();break;}
        return value.put("conditionalPlansVersion",2).put("maxActivePlansPerSession",4).put("ready",enabled&&SystemClock.elapsedRealtime()<deadline).put("displayId",display).put("mode","parallel").put("independentKeyguard",true).put("physicalPanelOff",screen!=null&&screen.isOff()).put("screenPolicy",screen==null?new JSONObject():screen.state()).put("displaysDraining",displaysDraining.get()).put("gestureService",(gestureLease==null?new JSONObject().put("error",gestureLeaseError):gestureLease.state()).put("settingsObserverError",gestureSettingsObserverError)).put("requestedPanelOff",requestedPanelOff).put("independentPowerGroup",false).put("resumeWithoutRestart",true).put("readOnlyPreview",true).put("previewTransport","frames").put("deviceInteractive",context!=null&&context.getSystemService(PowerManager.class).isInteractive()).put("keyguardLocked",context!=null&&context.getSystemService(KeyguardManager.class).isKeyguardLocked()).put("windows",new JSONArray()).put("error","");
    }
    private synchronized void end(Entry entry,String reason,boolean local){
        if(sessions.get(entry.id)!=entry)return;
        if(entry.handoffInProgress){entry.endAfterHandoff=true;entry.deferredEndReason=reason;entry.deferredEndLocal|=local;if(local)locallyBlocked.add(entry.pkg);return;}
        entry.active=false;entry.handoffFailed=false;IParallelGestureDispatcher dispatcher=gestureDispatcher;if(dispatcher!=null)try{dispatcher.revoke(gestureEpoch,entry.id,entry.engine.displayIdentifier());}catch(Exception ignored){}entry.engine.cancel();sessions.remove(entry.id,entry);syncPowerAfterStop();legacy.values().removeIf(id->id.equals(entry.id));if(local)locallyBlocked.add(entry.pkg);
        try{JSONObject record=entry.describe().put("state",reason).put("_ownerKey",entry.owner);terminal.put(entry.id,record);while(terminal.size()>128)terminal.remove(terminal.keySet().iterator().next());}catch(Exception ignored){}
        displaysDraining.incrementAndGet();
        cleanup.execute(()->{try{entry.engine.destroy();TaskCleanup.reap();try{Thread.sleep(200);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}TaskCleanup.reap();}finally{displaysDraining.decrementAndGet();}});releaseGestureLeaseWhenUnused();scheduleExpiry();changed();
    }
    private final Runnable expire=()->{long now=SystemClock.elapsedRealtime();for(Entry entry:sessions.values())if(now>=entry.expires||now>=deadline)end(entry,"expired",false);if(now>=deadline){enabled=false;gestureLeaseLifetime=false;if(screen!=null)screen.close();releaseGestureLeaseWhenUnused();changed();}scheduleExpiry();};
    private synchronized void scheduleExpiry(){timers.removeCallbacks(expire);if(!enabled)return;long next=deadline;for(Entry entry:sessions.values())next=Math.min(next,entry.expires);if(next!=Long.MAX_VALUE)timers.postDelayed(expire,Math.max(1,next-SystemClock.elapsedRealtime()));}
    private synchronized void wakeDisplay(int displayId)throws Exception{
        requireEnabled();if(displayId<=0)throw new SecurityException("Physical display wake refused.");
        boolean owned=false;for(Entry entry:sessions.values())if(entry.active&&entry.engine.displayIdentifier()==displayId){owned=true;break;}
        if(!owned||screen==null)throw new SecurityException("Affichage IA non possédé par une session active.");
        screen.prepareDisplay(shell().getSystemService(android.hardware.display.DisplayManager.class).getDisplay(displayId));
    }
    synchronized JSONObject restorePanel()throws Exception{if(screen!=null)screen.restoreByUser();for(Entry e:sessions.values())e.engine.physicalScreenPolicyChanged();return status();}
    JSONObject physicalScreenState(boolean on,boolean userPresent)throws Exception{
        // Apply local screen intent before waiting for a long app/session operation.
        ScreenOffController current=screen;if(current!=null)current.physicalState(on,userPresent);for(Entry e:sessions.values())e.engine.physicalScreenPolicyChanged();
        changed();return status();
    }
    synchronized void setPreviewCallback(IPreviewCallback callback){this.callback=callback;}
    private void changed(){IPreviewCallback target=callback;if(target!=null)try{target.onSessionsChanged(status().toString());}catch(Exception ignored){}}
    private synchronized Set<String> diagnosticPackages(){
        LinkedHashSet<String> packages=new LinkedHashSet<>();for(Entry entry:sessions.values())if(AccessPolicy.validPackage(entry.pkg)){packages.add(entry.pkg);if(packages.size()>=4)return packages;}
        List<JSONObject> ended=new ArrayList<>(terminal.values());for(int i=ended.size()-1;i>=0&&packages.size()<4;i--){String pkg=ended.get(i).optString("packageName");if(AccessPolicy.validPackage(pkg))packages.add(pkg);}return packages;
    }

    private JSONObject runtimeDiagnostics()throws Exception{
        List<RuntimeTaskPolicy.Target> targets=new ArrayList<>();
        Set<Integer> displays=new LinkedHashSet<>();Map<Integer,Integer> displayUids=new LinkedHashMap<>();
        for(Entry entry:new ArrayList<>(sessions.values())){
            if(!entry.active||!entry.engine.cachedIsolationVerified(entry.pkg))continue;
            int display=entry.engine.displayIdentifier(),task=entry.engine.cachedOwnedTaskId(entry.pkg);
            if(display<=0||task<0||displays.size()>=MAX_SESSIONS)continue;
            displays.add(display);targets.add(new RuntimeTaskPolicy.Target(task,display));
            try{displayUids.put(display,shell().getPackageManager().getApplicationInfo(entry.pkg,0).uid);}catch(android.content.pm.PackageManager.NameNotFoundException unavailable){}
        }
        return new JSONObject().put("snapshotAtomic",false).put("runtimeTasks",RuntimeTaskDiagnostics.sample(targets))
            .put("displayGroups",DisplayGroupDiagnostics.sample(displays)).put("inputWindows",InputWindowDiagnostics.sample(displayUids));
    }

    JSONObject diagnostics()throws Exception{JSONArray exits=ApplicationExitDiagnostics.sample(shell(),diagnosticPackages());JSONArray values=new JSONArray();for(Entry entry:sessions.values()){JSONObject item=entry.describe();try{item.put("display",entry.engine.execute("status",new JSONObject()));}catch(Exception e){item.put("error",e.toString());}values.put(item);}JSONArray tasks=new JSONArray();try{Class<?> api=Class.forName("android.app.IActivityTaskManager");Object manager=Class.forName("android.app.ActivityTaskManager").getMethod("getService").invoke(null);for(Object root:(java.util.List<?>)api.getMethod("getAllRootTaskInfos").invoke(manager)){Class<?> type=root.getClass();android.content.ComponentName top=(android.content.ComponentName)type.getField("topActivity").get(root);JSONObject item=new JSONObject().put("taskId",type.getField("taskId").getInt(root)).put("displayId",type.getField("displayId").getInt(root)).put("packageName",top==null?"":top.getPackageName());try{item.put("visible",type.getField("isVisible").getBoolean(root));}catch(Exception ignored){}try{Intent base=(Intent)type.getField("baseIntent").get(root);item.put("excludedFromRecents",base!=null&&(base.getFlags()&Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)!=0);}catch(Exception ignored){}tasks.put(item);}}catch(Exception ignored){}JSONArray recents=new JSONArray();try{Class<?> api=Class.forName("android.app.IActivityTaskManager");Object manager=Class.forName("android.app.ActivityTaskManager").getMethod("getService").invoke(null);Object slice=api.getMethod("getRecentTasks",int.class,int.class,int.class).invoke(manager,64,1,android.os.Process.myUid()/100000);for(Object item:(java.util.List<?>)slice.getClass().getMethod("getList").invoke(slice)){Class<?> type=item.getClass();Intent base=(Intent)type.getField("baseIntent").get(item);String pkg=base==null||base.getComponent()==null?"":base.getComponent().getPackageName();boolean ours="fr.phonemcp".equals(pkg);for(Entry entry:sessions.values())if(entry.pkg.equals(pkg))ours=true;if(!ours)continue;recents.put(new JSONObject().put("taskId",type.getField("taskId").getInt(item)).put("displayId",type.getField("displayId").getInt(item)).put("packageName",pkg).put("excludedFromRecents",base!=null&&(base.getFlags()&Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)!=0));}}catch(Exception ignored){}return runtimeDiagnostics().put("recentApplicationExits",exits).put("sessions",values).put("tasks",tasks).put("recentTasks",recents).put("taskCleanup",TaskCleanup.state()).put("screenPolicy",screen==null?new JSONObject():screen.diagnostics());}
    JSONObject savePreviewImage(JSONObject args,android.os.ParcelFileDescriptor destination)throws Exception{Entry e=select(args,false);return e.engine.savePreviewImage(args,destination).put("sessionId",e.id);}
    JSONObject attachRecording(JSONObject args,android.view.Surface destination)throws Exception{Entry e=select(args,false);return e.engine.attachRecording(args,destination);}
    JSONObject renewRecording(JSONObject args)throws Exception{Entry e=select(args,false);return e.engine.renewRecording(args);}
    void detachRecording(String token){for(Entry e:sessions.values())e.engine.detachRecording(token);}
    JSONObject previewFrame(JSONObject args)throws Exception{Entry entry=select(args,false);return entry.engine.previewFrame(args).put("sessionId",entry.id);}
    JSONObject attachPreview(JSONObject args,SurfaceControl parent)throws Exception{Entry entry=select(args,false);return entry.engine.attachPreview(args,parent);}
    JSONObject renewPreview(JSONObject args)throws Exception{Entry entry=select(args,false);return entry.engine.renewPreview(args);}
    void detachPreview(String viewer){for(Entry entry:sessions.values())entry.engine.detachPreview(viewer);}
    synchronized void disconnect(int revision){transportRevision=Math.max(transportRevision,revision);for(Entry entry:new ArrayList<>(sessions.values()))end(entry,"disconnected",false);}
    synchronized void stopForModeSwitch(){stopDisplays(false);}
    synchronized void stop(){stopDisplays(true);}
    private void stopDisplays(boolean endTransport){if(endTransport)gestureLeaseLifetime=false;enabled=false;deadline=0;timers.removeCallbacks(expire);for(Entry entry:new ArrayList<>(sessions.values()))end(entry,"stopped",false);legacy.clear();if(screen!=null)screen.close();screen=null;requestedPanelOff=false;releaseGestureLeaseWhenUnused();}
    void destroy(){stop();cleanup.shutdown();try{cleanup.awaitTermination(ParallelGesturePolicy.MAX_REQUEST_MS+2000,TimeUnit.MILLISECONDS);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}TaskCleanup.reap();timersThread.quitSafely();}
}

