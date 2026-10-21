package fr.phonemcp;

import android.annotation.SuppressLint;
import android.os.IBinder;
import android.os.SystemClock;
import java.lang.reflect.Method;

/** Shell-only panel power. Never dismisses keyguard or injects input on display 0. */
@SuppressLint({"PrivateApi", "DiscouragedPrivateApi"})
final class PanelPower {
    private final Class<?> surface = Class.forName("android.view.SurfaceControl");
    private final Class<?> physical;
    private final Object power;
    private boolean changed;

    // Runs only in Shizuku's ADB shell process, exempt from app hidden-API restrictions.
    @SuppressLint("BlockedPrivateApi")
    PanelPower() throws Exception {
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

    void keepActive(int displayId) throws Exception {
        power.getClass().getMethod("userActivity",int.class,long.class,int.class,int.class)
                .invoke(power,displayId,SystemClock.uptimeMillis(),0,0);
    }

    void wakeVirtual(int displayId)throws Exception {
        if(displayId<=0)throw new SecurityException("Réveil de l’écran principal interdit.");
        power.getClass().getMethod("wakeUpWithDisplayId",long.class,int.class,String.class,String.class,int.class)
            .invoke(power,SystemClock.uptimeMillis(),2,"PhoneMCP:virtual","com.android.shell",displayId);
    }

    void off() throws Exception {
        // Mark first so a partial failure also attempts to restore every physical panel.
        changed = true; setPower(0);
    }

    void restore() throws Exception {
        if(changed) { setPower(2); changed = false; }
    }

    private void setPower(int mode) throws Exception {
        long[] ids = (long[])physical.getMethod("getPhysicalDisplayIds").invoke(null);
        if(ids == null || ids.length == 0)throw new IllegalStateException("Aucune dalle physique disponible.");
        Exception failure = null;
        for(long id : ids) {
            try {
                IBinder token = (IBinder)physical.getMethod("getPhysicalDisplayToken",long.class).invoke(null,id);
                if(token == null)throw new IllegalStateException("Dalle physique indisponible.");
                surface.getMethod("setDisplayPowerMode",IBinder.class,int.class).invoke(null,token,mode);
            } catch(Exception e) { failure = e; }
        }
        if(failure != null)throw failure;
    }
}
