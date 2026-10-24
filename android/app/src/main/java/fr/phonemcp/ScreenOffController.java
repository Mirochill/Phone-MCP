package fr.phonemcp;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.*;
import android.view.Display;
import org.json.JSONObject;

/** Event-driven physical-screen/audio ownership shared by all AI displays. */
final class ScreenOffController {
    private final Context context;
    private final Handler handler;
    private final PanelPower panel;
    private final ControlledAppAudio audio;
    private final ScreenOffPolicy policy=new ScreenOffPolicy();
    private final ActiveScreenPolicy activePolicy=new ActiveScreenPolicy();
    private final PowerManager.WakeLock awakeLease;
    private final Object awakeExpiryToken=new Object(),awakeReinforceToken=new Object();
    private boolean awakeFallback,requestedScreenOff,keepPhysicalScreenOn;
    private String fallbackReason="";
    private long activePrimaryWakes;
    private final Object reinforceToken=new Object();
    private boolean running;
    private volatile boolean screenOnPending;
    private boolean maskedPrimaryAwake,maskedWakeInProgress;
    private long maskedGroupWakes,maskedWakeBroadcasts,powerButtonRestores;
    private int lastObservedSleepReason=-1;
    private long preWakeLocalRestores;
    private long panelOffRequests,screenOnEvents,explicitRestores,directVirtualRequests,isolatedGroupWakes,refusedGroupWakes;
    private String error="";
    private JSONObject lastVirtualGroup=new JSONObject();
    private JSONObject lastPrimaryScreenEvent=new JSONObject();
    private long ignoredVirtualScreenOn,ignoredVirtualScreenOff,unverifiedPrimaryScreenEvents;

    ScreenOffController(Context context,int ownerUid,Handler handler)throws Exception {
        this.context=context;this.handler=handler;panel=new PanelPower(context);audio=new ControlledAppAudio(context,ownerUid);
        awakeLease=context.getSystemService(PowerManager.class).newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK,"phonemcp:active-app-control");
        awakeLease.setReferenceCounted(false);
    }
    synchronized void start(boolean screenOff,boolean keepOn)throws Exception {
        // The option only forces an initial blank. Leaving it unchecked never opts into a lit-screen fallback.
        running=true;requestedScreenOff=screenOff;keepPhysicalScreenOn=keepOn;awakeFallback=false;fallbackReason="";activePolicy.clear();
        if(screenOff){policy.screenOff();audio.screenOff();blank();maskedPrimaryAwake=panel.primaryInteractive();}
        else{policy.userRestore();audio.restore();}
    }
    private boolean activeScreenOwner(){return running&&activePolicy.active(SystemClock.elapsedRealtime());}
    private void releaseAwakeLease(){if(awakeLease.isHeld())awakeLease.release();}
    private void cancelAwakeCallbacks(){handler.removeCallbacksAndMessages(awakeExpiryToken);handler.removeCallbacksAndMessages(awakeReinforceToken);}

    /** Lifetime comes from real app sessions, never from the MCP transport connection. */
    synchronized void activeSessions(int count,long expiresElapsedMs)throws Exception {
        if(!running){if(count==0)return;throw new IllegalStateException("Politique écran arrêtée.");}
        boolean previouslyOwned=activeScreenOwner();long now=SystemClock.elapsedRealtime();activePolicy.update(count,expiresElapsedMs,now);
        cancelAwakeCallbacks();
        if(!activeScreenOwner()){policy.cancelMaskedWake();releaseAwakeLease();if(awakeFallback)audio.restore();awakeFallback=false;fallbackReason="";return;}
        long revision=activePolicy.revision();
        handler.postDelayed(()->expireAwakeLease(revision),awakeExpiryToken,Math.max(1,activePolicy.expires()-now));
        applyScreenChoice(previouslyOwned);
    }
    private synchronized void expireAwakeLease(long revision){
        if(activePolicy.revision()!=revision||activePolicy.active(SystemClock.elapsedRealtime()))return;
        activePolicy.clear();handler.removeCallbacksAndMessages(awakeReinforceToken);releaseAwakeLease();
        if(awakeFallback)try{audio.restore();}catch(Exception failure){error="Restauration audio en attente.";}awakeFallback=false;fallbackReason="";
    }
    synchronized void selectPhysicalScreenAwake(boolean selected)throws Exception {
        keepPhysicalScreenOn=selected;
        if(!running)return;
        boolean active=activeScreenOwner();activeSessions(active?activePolicy.count():0,active?activePolicy.expires():0);
    }
    private void applyScreenChoice(boolean previouslyOwned)throws Exception {
        if(!keepPhysicalScreenOn){
            boolean wasAwake=awakeFallback;awakeFallback=false;fallbackReason="";releaseAwakeLease();
            if(wasAwake){maskedPrimaryAwake=false;policy.userRestore();if(physicalAsleep()){policy.screenOff();audio.screenOff();}else audio.restore();}
            return;
        }
        if(!activeScreenOwner())return;
        boolean entering=!awakeFallback;awakeFallback=true;fallbackReason="local_user_setting";
        if(entering){policy.userRestore();maskedPrimaryAwake=false;cancelReinforcement();}
        awakeLease.acquire(Math.max(1,activePolicy.expires()-SystemClock.elapsedRealtime()));
        try{ensureAwake(entering||!previouslyOwned);error="";}catch(Exception failure){error="Maintien de l’écran non confirmé.";throw failure;}
    }
    private void ensureAwake(boolean restorePanel)throws Exception {
        if(!awakeFallback||!activeScreenOwner())return;
        if(!panel.primaryInteractive()){
            activePrimaryWakes++;panel.wakePrimaryForActiveControl();
            long until=SystemClock.uptimeMillis()+800;
            while(activeScreenOwner()&&!panel.primaryInteractive()&&SystemClock.uptimeMillis()<until)SystemClock.sleep(20);
            if(activeScreenOwner()&&!panel.primaryInteractive())throw new IllegalStateException("Android n’a pas rallumé l’écran physique.");
        }else if(restorePanel)panel.restore();
        // Wake may expose keyguard, but never dismisses or disables it.
        if(activeScreenOwner())audio.restore();
    }
    private void reinforceAwake(long revision){
        synchronized(this){if(!awakeFallback||!activeScreenOwner()||activePolicy.revision()!=revision)return;
            try{ensureAwake(false);}catch(Exception failure){error="Réveil de secours non confirmé.";}}
    }
    private void physicalAwakeState(boolean on,boolean userPresent)throws Exception {
        Boolean interactive=null;try{interactive=panel.primaryInteractive();}catch(Exception unavailable){}
        int event=ScreenOffPolicy.resolvePhysicalEvent(on,userPresent,interactive);
        lastPrimaryScreenEvent=new JSONObject().put("reportedOn",on).put("userPresent",userPresent)
            .put("primaryInteractive",interactive==null?JSONObject.NULL:interactive).put("accepted",event!=ScreenOffPolicy.SCREEN_EVENT_IGNORED).put("source","power_service_binder");
        if(event==ScreenOffPolicy.SCREEN_EVENT_IGNORED)return;
        if(event==ScreenOffPolicy.SCREEN_EVENT_ON){screenOnEvents++;if(activeScreenOwner())audio.restore();return;}
        if(!activeScreenOwner())return;
        audio.screenOff();ensureAwake(true);handler.removeCallbacksAndMessages(awakeReinforceToken);long revision=activePolicy.revision();
        handler.postDelayed(()->reinforceAwake(revision),awakeReinforceToken,100);
        handler.postDelayed(()->reinforceAwake(revision),awakeReinforceToken,350);
    }
    private boolean physicalAsleep(){
        Display display=context.getSystemService(DisplayManager.class).getDisplay(Display.DEFAULT_DISPLAY);
        // isInteractive() can be true because a different display is awake. Conversely a
        // SurfaceControl blank leaves this cache ON: the independent policy intent is retained.
        if(display==null||display.getState()!=Display.STATE_ON)return true;
        try {
            Object power=Class.forName("android.os.IPowerManager$Stub").getMethod("asInterface",IBinder.class)
                    .invoke(null,Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,"power"));
            return !(Boolean)power.getClass().getMethod("isDisplayInteractive",int.class).invoke(power,Display.DEFAULT_DISPLAY);
        }catch(Exception unsupported){return !context.getSystemService(PowerManager.class).isInteractive();}
    }
    synchronized boolean isOff(){return policy.isOff();}
    synchronized void beforeApp(String pkg)throws Exception {
        if(!running)throw new IllegalStateException("Protection de l’écran arrêtée.");
        if(awakeFallback){audio.remember(pkg,false);ensureAwake(false);return;}
        if(!policy.isOff()&&physicalAsleep())policy.screenOff();
        audio.remember(pkg,policy.isOff());
        if(policy.isOff())audio.screenOff();else audio.restore();
    }
    // Check before every internal wake/blank. A local POWER sleep can beat its queued
    // broadcast: re-waking group 0 first would erase the only observable OFF edge.
    private boolean restorePowerButtonBeforeWork()throws Exception {
        if(!running||awakeFallback||maskedWakeInProgress||!policy.isOff()||!maskedPrimaryAwake)return false;
        Boolean primary=panel.primaryInteractive();if(!Boolean.FALSE.equals(primary))return false;
        int reason=-1;try{reason=panel.lastSleepReason();}catch(Exception unavailable){}
        lastObservedSleepReason=reason;
        if(!ScreenOffPolicy.powerButtonRequestsRestore(true,true,primary,reason))return false;
        screenOnPending=true;
        try{preWakeLocalRestores++;powerButtonRestores++;restoreByUser();panel.wakePrimaryByUser();}
        finally{screenOnPending=false;}
        return true;
    }
    private void blank()throws Exception { panel.off();panelOffRequests++; }
    private void cancelReinforcement(){handler.removeCallbacksAndMessages(reinforceToken);}
    private void reinforce(long revision){
        synchronized(this){
            if(!running||screenOnPending||!policy.mayReinforce(revision))return;
            try{if(!restorePowerButtonBeforeWork())blank();}catch(Exception failed){error="Écran physique éteint non confirmé.";}
        }
    }
    synchronized void afterApp()throws Exception {
        if(awakeFallback){ensureAwake(false);return;}
        if(!running||screenOnPending||!policy.isOff())return;
        if(restorePowerButtonBeforeWork())return;
        blank();cancelReinforcement();long revision=policy.revision();
        // Bounded follow-ups for asynchronous window requests. No idle polling. A physical
        // SCREEN_ON/user restore invalidates both callbacks, including one already queued.
        handler.postDelayed(()->reinforce(revision),reinforceToken,16);
        handler.postDelayed(()->reinforce(revision),reinforceToken,50);
        handler.postDelayed(()->reinforce(revision),reinforceToken,100);
        handler.postDelayed(()->reinforce(revision),reinforceToken,350);
    }
    void physicalState(boolean on,boolean userPresent)throws Exception {
        synchronized(this){if(awakeFallback){physicalAwakeState(on,userPresent);return;}}
        // SCREEN_ON/OFF are GLOBAL broadcasts. Validate the primary power group before
        // publishing wake intent: a virtual wake must not cancel blanking or unmute audio.
        Boolean primaryInteractive=null;String verificationError="";
        try{primaryInteractive=panel.primaryInteractive();}
        catch(Exception unavailable){
            Throwable cause=unavailable;
            for(int depth=0;depth<8&&cause instanceof java.lang.reflect.InvocationTargetException&&cause.getCause()!=null;depth++)cause=cause.getCause();
            verificationError=cause.getClass().getSimpleName()+": "+String.valueOf(cause.getMessage());
            if(verificationError.length()>300)verificationError=verificationError.substring(0,300);
        }
        synchronized(this){
            if(awakeFallback){physicalAwakeState(on,userPresent);return;}
            if(running&&!userPresent){
                if(on&&Boolean.TRUE.equals(primaryInteractive)&&policy.consumeMaskedWake(SystemClock.uptimeMillis())){
                    maskedWakeBroadcasts++;afterApp();return;
                }
                if(!on&&Boolean.FALSE.equals(primaryInteractive)){
                    int reason=-1;try{reason=panel.lastSleepReason();}catch(Exception unavailable){}
                    lastObservedSleepReason=reason;
                    boolean localRestore=ScreenOffPolicy.powerButtonRequestsRestore(policy.isOff(),maskedPrimaryAwake,primaryInteractive,reason);
                    // A global/late virtual OFF must not discard our blank ownership.
                    if(localRestore){
                        screenOnPending=true;
                        try{powerButtonRestores++;restoreByUser();panel.wakePrimaryByUser();}
                        finally{screenOnPending=false;}
                        return;
                    }
                }
            }
        }
        int event=ScreenOffPolicy.resolvePhysicalEvent(on,userPresent,primaryInteractive);
        JSONObject observation=new JSONObject().put("reportedOn",on).put("userPresent",userPresent)
                .put("primaryInteractive",primaryInteractive==null?JSONObject.NULL:primaryInteractive)
                .put("primaryAvailable",primaryInteractive!=null).put("accepted",event!=ScreenOffPolicy.SCREEN_EVENT_IGNORED)
                .put("source","power_service_binder").put("error",verificationError);
        if(event==ScreenOffPolicy.SCREEN_EVENT_IGNORED){
            synchronized(this){
                lastPrimaryScreenEvent=observation;
                if(on)ignoredVirtualScreenOn++;else ignoredVirtualScreenOff++;
            }
            return;
        }
        boolean restore=event==ScreenOffPolicy.SCREEN_EVENT_ON;
        // A verified primary wake or local USER_PRESENT wins before waiting for a long
        // app command's monitor. No timing window can discard the owner's takeover.
        if(restore){screenOnPending=true;cancelReinforcement();}
        synchronized(this){
            try{
                if(awakeFallback){physicalAwakeState(on,userPresent);return;}
                lastPrimaryScreenEvent=observation;
                if(primaryInteractive==null)unverifiedPrimaryScreenEvents++;
                if(!running)return;
                if(!restore){cancelReinforcement();policy.screenOff();audio.screenOff();return;}
                // This is a primary interactivity observation, not a trusted HUMAN cause:
                // an application can still wake shared group0. Never claim otherwise.
                screenOnEvents++;maskedPrimaryAwake=false;policy.userRestore();audio.restore();error="";
            }finally{if(restore)screenOnPending=false;}
        }
    }
    synchronized void restoreByUser()throws Exception {
        explicitRestores++;maskedPrimaryAwake=false;policy.userRestore();cancelReinforcement();
        Exception failure=null;
        try{panel.restore();}catch(Exception failed){failure=failed;}
        try{audio.restore();}catch(Exception failed){if(failure==null)failure=failed;else failure.addSuppressed(failed);}
        if(failure!=null){error="Restauration locale non confirmée.";throw failure;}
        error="";
    }
    synchronized void prepareDisplay(Display target)throws Exception {
        if(!running)throw new IllegalStateException("Protection de l’écran arrêtée.");
        if(target==null||!target.isValid()||target.getDisplayId()<=0)
            throw new SecurityException("Affichage IA invalide.");
        if(awakeFallback){prepareAwakeDisplay(target);return;}
        restorePowerButtonBeforeWork();
        if(!policy.isOff()&&physicalAsleep()){policy.screenOff();audio.screenOff();}
        lastVirtualGroup=PanelPower.groupState(target);
        if(target.getState()==Display.STATE_ON&&panel.interactive(target.getDisplayId())){
            if(policy.isOff()&&panel.primaryInteractive())maskedPrimaryAwake=true;
            return;
        }
        Exception directFailure=null;
        try{directVirtualRequests++;panel.requestVirtualOn(target);}catch(Exception failed){directFailure=failed;}
        int group=PanelPower.groupId(target);
        if(!panel.interactive(target.getDisplayId())){
            if(ScreenOffPolicy.mayWakeDisplayGroup(target.getDisplayId(),group)){
                panel.wakeVirtual(target);isolatedGroupWakes++;
            }else if(ScreenOffPolicy.mayWakeBehindBlank(target.getDisplayId(),group,policy.isOff())){
                // Android must be interactive to deliver input. The owner's hardware panel
                // stays separately blank; this internal SCREEN_ON is not local takeover.
                blank();policy.beginMaskedWake(SystemClock.uptimeMillis());maskedWakeInProgress=true;
                try{panel.wakeBehindBlank(target);maskedGroupWakes++;
                    long wakeUntil=SystemClock.uptimeMillis()+800;
                    while(!panel.primaryInteractive()&&SystemClock.uptimeMillis()<wakeUntil)SystemClock.sleep(20);
                    maskedPrimaryAwake=panel.primaryInteractive();
                }finally{maskedWakeInProgress=false;afterApp();}
            }else{
                refusedGroupWakes++;
                throw new IllegalStateException("Réveil IA non autorisé sans protection de la dalle physique.",directFailure);
            }
        }
        long until=SystemClock.uptimeMillis()+800;
        while(!panel.interactive(target.getDisplayId())&&SystemClock.uptimeMillis()<until){
            if(policy.isOff())blank();SystemClock.sleep(20);
        }
        if(policy.isOff())afterApp();
        if(!panel.interactive(target.getDisplayId()))throw new IllegalStateException("Android n’a pas rendu l’écran IA interactif. Aucun geste n’a été envoyé.");
        if(group>0)panel.keepActive(target);
    }

    private void prepareAwakeDisplay(Display target)throws Exception {
        if(!activeScreenOwner())throw new SecurityException("Aucune session d’application active pour cet écran.");
        ensureAwake(false);lastVirtualGroup=PanelPower.groupState(target);
        if(target.getState()==Display.STATE_ON&&panel.interactive(target.getDisplayId()))return;
        directVirtualRequests++;panel.requestVirtualOn(target);
        if(!panel.interactive(target.getDisplayId()))panel.wakeForActiveControl(target);
        long until=SystemClock.uptimeMillis()+800;
        while(activeScreenOwner()&&!panel.interactive(target.getDisplayId())&&SystemClock.uptimeMillis()<until)SystemClock.sleep(20);
        if(!panel.interactive(target.getDisplayId()))throw new IllegalStateException("Écran IA non interactif. Aucun geste n’a été envoyé.");
        if(PanelPower.groupId(target)>0)panel.keepActive(target);
    }
    synchronized JSONObject state()throws Exception {
        return new JSONObject().put("mode",awakeFallback?"awake_user":"screen_off").put("physicalScreenModeOwner","phone_settings").put("keepPhysicalScreenOnSelected",keepPhysicalScreenOn).put("automaticAwakeFallback",false).put("fallbackReason",fallbackReason)
                .put("requestedScreenOff",requestedScreenOff).put("activeAppSessions",activePolicy.count())
                .put("screenAwakeRequired",awakeFallback&&activeScreenOwner()).put("screenWakeLockHeld",awakeLease.isHeld())
                .put("screenLeaseExpiresElapsedMs",activePolicy.expires()).put("activePrimaryWakes",activePrimaryWakes).put("keyguardBypass",false)
                .put("physicalPanelOff",policy.isOff()).put("audio",audio.state())
                .put("screenPolicyError",error).put("defaultGroupWakeAllowed",awakeFallback?activeScreenOwner():policy.isOff()).put("sharedGroupWakeRequiresPhysicalBlank",!awakeFallback)
                .put("screenOnPolicy",awakeFallback?"awake_while_app_sessions_active":"masked_internal_wake_else_primary_or_local_power_button")
                .put("lastPrimaryScreenEvent",lastPrimaryScreenEvent)
                .put("ignoredVirtualScreenOn",ignoredVirtualScreenOn).put("ignoredVirtualScreenOff",ignoredVirtualScreenOff)
                .put("unverifiedPrimaryScreenEvents",unverifiedPrimaryScreenEvents)
                .put("virtualWakePolicy",awakeFallback?"awake_while_app_sessions_active":"interactive_virtual_group_with_owned_physical_blank_when_shared")
                .put("lastVirtualGroup",lastVirtualGroup)
                .put("panelOffRequests",panelOffRequests).put("screenOnEvents",screenOnEvents)
                .put("explicitLocalRestores",explicitRestores).put("directVirtualPowerRequests",directVirtualRequests)
                .put("lastObservedSleepReason",lastObservedSleepReason).put("preWakeLocalRestores",preWakeLocalRestores)
                .put("maskedGroupWakes",maskedGroupWakes).put("maskedWakeBroadcasts",maskedWakeBroadcasts).put("powerButtonRestores",powerButtonRestores).put("maskedPrimaryAwake",maskedPrimaryAwake).put("isolatedGroupWakes",isolatedGroupWakes).put("refusedSharedOrUnknownGroupWakes",refusedGroupWakes);
    }
    synchronized JSONObject diagnostics()throws Exception {return state().put("audio",audio.diagnostics());}
    synchronized void close(){
        running=false;cancelReinforcement();activePolicy.clear();cancelAwakeCallbacks();releaseAwakeLease();
        // Stop/disconnect/error never turn on a screen the owner left off.
        panel.leaveOff();
        try{audio.close();}catch(Exception failed){error="Restauration audio en attente au prochain démarrage Shizuku.";}
    }
}





