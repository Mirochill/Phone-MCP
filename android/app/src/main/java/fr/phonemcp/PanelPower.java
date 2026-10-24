package fr.phonemcp;

import android.annotation.SuppressLint;
import android.os.IBinder;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.SystemClock;
import android.view.Display;
import java.lang.reflect.Method;

/** Shell-only panel power. Never dismisses keyguard or injects input on display 0. */
@SuppressLint({"PrivateApi", "DiscouragedPrivateApi"})
final class PanelPower {
    private final Class<?> surface = Class.forName("android.view.SurfaceControl");
    private final Class<?> physical;
    private final Object power;
    private final Context context;

    // Runs only in Shizuku's ADB shell process, exempt from app hidden-API restrictions.
    @SuppressLint("BlockedPrivateApi")
    PanelPower(Context context) throws Exception {
        this.context=context;
        if(android.os.Process.myUid()!=2000)throw new SecurityException("Alimentation réservée au service Shizuku ADB.");
        Class<?> physicalClass;
        try { surface.getMethod("getPhysicalDisplayIds"); physicalClass = surface; }
        catch (NoSuchMethodException moved) {
            Class<?> factory = Class.forName("com.android.internal.os.ClassLoaderFactory");
            Method create = factory.getDeclaredMethod("createClassLoader",String.class,String.class,String.class,ClassLoader.class,int.class,boolean.class,String.class);
            ClassLoader loader = (ClassLoader)create.invoke(null,System.getenv("SYSTEMSERVERCLASSPATH"),null,null,ClassLoader.getSystemClassLoader(),0,true,null);
            physicalClass = loader.loadClass("com.android.server.display.DisplayControl");
            Method load = Runtime.class.getDeclaredMethod("loadLibrary0",Class.class,String.class);
            load.setAccessible(true); load.invoke(Runtime.getRuntime(),physicalClass,"android_servers");
        }
        physical = physicalClass;
        IBinder binder = (IBinder)Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,"power");
        power = Class.forName("android.os.IPowerManager$Stub").getMethod("asInterface",IBinder.class).invoke(null,binder);
    }

    void keepActive(Display display) throws Exception {
        int displayId=display==null?-1:display.getDisplayId();
        if(!ScreenOffPolicy.mayWakeDisplayGroup(displayId,groupId(display)))
            throw new SecurityException("Activité d’un groupe physique ou inconnu interdite.");
        power.getClass().getMethod("userActivity",int.class,long.class,int.class,int.class)
                .invoke(power,displayId,SystemClock.uptimeMillis(),0,1); // NO_CHANGE_LIGHTS
    }

    void wakeVirtual(Display display)throws Exception {
        int displayId=display==null?-1:display.getDisplayId();
        if(!ScreenOffPolicy.mayWakeDisplayGroup(displayId,groupId(display)))
            throw new SecurityException("Réveil d’un groupe partagé avec l’écran physique interdit.");
        power.getClass().getMethod("wakeUpWithDisplayId",long.class,int.class,String.class,String.class,int.class)
            .invoke(power,SystemClock.uptimeMillis(),2,"PhoneMCP:virtual","com.android.shell",displayId);
    }

    boolean interactive(int displayId)throws Exception {
        return Boolean.TRUE.equals(Class.forName("android.os.IPowerManager").getMethod("isDisplayInteractive",int.class).invoke(power,displayId));
    }
    int lastSleepReason()throws Exception {
        return (Integer)Class.forName("android.os.IPowerManager").getMethod("getLastSleepReason").invoke(power);
    }
    void wakeBehindBlank(Display display)throws Exception {
        if(display==null||!display.isValid()||!ScreenOffPolicy.mayWakeBehindBlank(display.getDisplayId(),groupId(display),true))
            throw new SecurityException("Réveil masqué réservé à un écran IA valide du groupe partagé.");
        off();
        power.getClass().getMethod("wakeUpWithDisplayId",long.class,int.class,String.class,String.class,int.class)
            .invoke(power,SystemClock.uptimeMillis(),2,"PhoneMCP:blanked-virtual","com.android.shell",display.getDisplayId());
        off();
    }
    // Only a verified local POWER-button sleep while our panel was already blank can
    // request this reversal. It restores the lock screen, never bypasses the keyguard.
    void wakePrimaryByUser()throws Exception {
        power.getClass().getMethod("wakeUpWithDisplayId",long.class,int.class,String.class,String.class,int.class)
            .invoke(power,SystemClock.uptimeMillis(),1,"PhoneMCP:local-power-button","com.android.shell",Display.DEFAULT_DISPLAY);
        restore();
    }
    // Requested explicitly by the local active-control policy. Wake only; never
    // dismiss or disable keyguard, change its credentials, or inject physical input.
    void wakePrimaryForActiveControl()throws Exception {
        power.getClass().getMethod("wakeUpWithDisplayId",long.class,int.class,String.class,String.class,int.class)
            .invoke(power,SystemClock.uptimeMillis(),2,"PhoneMCP:active-app-session","com.android.shell",Display.DEFAULT_DISPLAY);
        restore();
    }
    void wakeForActiveControl(Display display)throws Exception {
        if(display==null||!display.isValid()||display.getDisplayId()<=0)throw new SecurityException("Affichage IA invalide.");
        power.getClass().getMethod("wakeUpWithDisplayId",long.class,int.class,String.class,String.class,int.class)
            .invoke(power,SystemClock.uptimeMillis(),2,"PhoneMCP:active-virtual","com.android.shell",display.getDisplayId());
    }
    static int groupId(Display display)throws Exception {
        if(display==null||!display.isValid()||display.getDisplayId()<=0)return -1;
        // Display.getDisplayInfo() goes through DisplayManagerGlobal's cache. A power-group
        // authorization must use the service's current answer, including after group assignment.
        IBinder binder=(IBinder)Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,"display");
        Class<?> api=Class.forName("android.hardware.display.IDisplayManager");
        Object manager=Class.forName("android.hardware.display.IDisplayManager$Stub").getMethod("asInterface",IBinder.class).invoke(null,binder);
        Object info=api.getMethod("getDisplayInfo",int.class).invoke(manager,display.getDisplayId());
        if(info==null)return -1;
        return Class.forName("android.view.DisplayInfo").getField("displayGroupId").getInt(info);
    }
    static int cachedGroupId(Display display)throws Exception {
        if(display==null||display.getDisplayId()<=0)return -1;
        Class<?> type=Class.forName("android.view.DisplayInfo");Object info=type.getConstructor().newInstance();
        if(!Boolean.TRUE.equals(Display.class.getMethod("getDisplayInfo",type).invoke(display,info)))return -1;
        return type.getField("displayGroupId").getInt(info);
    }
    static org.json.JSONObject groupState(Display display)throws Exception {
        org.json.JSONObject result=new org.json.JSONObject().put("displayId",display==null?-1:display.getDisplayId()).put("identitySource","display_service_binder");
        if(display==null)return result.put("directGroupId",-1).put("cachedGroupId",-1);
        int direct=-1,cached=-1;
        try{direct=groupId(display);}catch(Exception unavailable){result.put("directGroupError",unavailable.getClass().getSimpleName());}
        try{cached=cachedGroupId(display);}catch(Exception unavailable){result.put("cachedGroupError",unavailable.getClass().getSimpleName());}
        int ownFlag=0;try{ownFlag=Display.class.getField("FLAG_OWN_DISPLAY_GROUP").getInt(null);}catch(NoSuchFieldException unavailable){}
        return result.put("directGroupId",direct).put("cachedGroupId",cached).put("displayFlags",display.getFlags())
                .put("ownDisplayGroupFlag",ownFlag==0?org.json.JSONObject.NULL:(display.getFlags()&ownFlag)!=0).put("independentGroupVerified",direct>0)
                .put("groupCacheDisagrees",direct!=cached);
    }

    // Change only the owned virtual display, never its entire power group.
    boolean requestVirtualOn(Display display)throws Exception {
        int id=display==null?-1:display.getDisplayId();
        if(id<=0||!display.isValid())throw new SecurityException("Alimentation de l’écran physique interdite.");
        IBinder binder=(IBinder)Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,"display");
        Class<?> api=Class.forName("android.hardware.display.IDisplayManager");
        Object manager=Class.forName("android.hardware.display.IDisplayManager$Stub").getMethod("asInterface",IBinder.class).invoke(null,binder);
        try { return Boolean.TRUE.equals(api.getMethod("requestDisplayPower",int.class,int.class).invoke(manager,id,Display.STATE_ON)); }
        catch(NoSuchMethodException older) {
            try { return Boolean.TRUE.equals(api.getMethod("requestDisplayPower",int.class,boolean.class).invoke(manager,id,true)); }
            catch(NoSuchMethodException unsupported) { return false; }
        }
    }

    // Read the primary power GROUP from the service, never the global interactivity or
    // SurfaceControl's separate panel-blank state. This read cannot wake either display.
    boolean primaryInteractive()throws Exception {
        Object value=Class.forName("android.os.IPowerManager").getMethod("isDisplayInteractive",int.class)
                .invoke(power,Display.DEFAULT_DISPLAY);
        if(!(value instanceof Boolean))throw new IllegalStateException("État interactif principal indisponible.");
        return (Boolean)value;
    }

    void leaveOff() { /* Teardown is never a request to wake the owner. */ }

    void off() throws Exception { setPower(0); }

    // Only the explicit local button calls this. It must work even after a previous stop.
    void restore() throws Exception { setPower(2); }

    private void setPower(int mode) throws Exception {
        long[] ids=(long[])physical.getMethod("getPhysicalDisplayIds").invoke(null);
        Long primaryId=null;
        try{
            Display primary=context.getSystemService(DisplayManager.class).getDisplay(Display.DEFAULT_DISPLAY);
            if(primary!=null&&primary.isValid()){
                Class<?> type=Class.forName("android.view.DisplayInfo");Object info=type.getConstructor().newInstance();
                if(Boolean.TRUE.equals(Display.class.getMethod("getDisplayInfo",type).invoke(primary,info))){
                    Object address=type.getField("address").get(info);
                    if(address!=null)primaryId=(Long)address.getClass().getMethod("getPhysicalDisplayId").invoke(address);
                }
            }
        }catch(ReflectiveOperationException unsupported){ /* A single physical panel remains unambiguous. */ }
        // Never blank another physical output (e.g. an attached Dex/HDMI display).
        long id=ScreenOffPolicy.physicalPanel(ids,primaryId);
        IBinder token=(IBinder)physical.getMethod("getPhysicalDisplayToken",long.class).invoke(null,id);
        if(token==null)throw new IllegalStateException("Dalle physique indisponible.");
        surface.getMethod("setDisplayPowerMode",IBinder.class,int.class).invoke(null,token,mode);
    }
}


