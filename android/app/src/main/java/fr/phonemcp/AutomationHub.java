package fr.phonemcp;

import android.app.UiAutomation;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.graphics.*;
import android.os.*;
import android.view.accessibility.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

/** Android permits one automation registration. Displays keep independent state above it. */
final class AutomationHub {
    interface Listener { void changed(int type,int windowId);default void topologyChanged(){} }
    private static final Map<Integer,Listener> listeners=new HashMap<>();
    private static HandlerThread thread;
    private static UiAutomation automation;
    private static Object connection;
    private static int captures;
    private static final Map<Integer,UiAutomation> captureViews=new HashMap<>();
    @android.annotation.TargetApi(34)
    static synchronized UiAutomation acquire(int displayId,Listener listener)throws Exception{
        if(Build.VERSION.SDK_INT<34||displayId<=0||listeners.containsKey(displayId))throw new SecurityException("Invalid automation display.");
        if(automation==null){
            thread=new HandlerThread("phone-mcp-automation");thread.start();
            Class<?> connectionClass=Class.forName("android.app.IUiAutomationConnection");connection=Class.forName("android.app.UiAutomationConnection").getConstructor().newInstance();
            Constructor<UiAutomation> ctor=UiAutomation.class.getDeclaredConstructor(int.class,Looper.class,connectionClass);ctor.setAccessible(true);automation=ctor.newInstance(displayId,thread.getLooper(),connection);
            try{UiAutomation.class.getMethod("connect",int.class).invoke(automation,1);AccessibilityServiceInfo info=automation.getServiceInfo();info.flags|=AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS|AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS|AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;automation.setServiceInfo(info);
                automation.setOnAccessibilityEventListener(event->{Listener target;List<Listener> topology=null;int type=event.getEventType();synchronized(AutomationHub.class){target=listeners.get(event.getDisplayId());if(type==AccessibilityEvent.TYPE_WINDOWS_CHANGED||type==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)topology=new ArrayList<>(listeners.values());}if(target!=null)target.changed(type,event.getWindowId());if(topology!=null)for(Listener changedListener:topology)changedListener.topologyChanged();});
            }catch(Exception failure){close();throw failure;}
        }
        Class<?> connectionClass=Class.forName("android.app.IUiAutomationConnection");Constructor<UiAutomation> ctor=UiAutomation.class.getDeclaredConstructor(int.class,Looper.class,connectionClass);ctor.setAccessible(true);
        // A facade only supplies its immutable display ID to Android's public screenshot method.
        // It is never connected: there is exactly one accessibility registration.
        captureViews.put(displayId,ctor.newInstance(displayId,thread.getLooper(),connection));listeners.put(displayId,listener);return automation;
    }
    static synchronized void release(int displayId){listeners.remove(displayId);captureViews.remove(displayId);if(listeners.isEmpty()&&captures==0)close();}
    private static void close(){if(automation!=null)try{UiAutomation.class.getMethod("disconnect").invoke(automation);}catch(Exception ignored){}automation=null;connection=null;if(thread!=null)thread.quitSafely();thread=null;}
    static Bitmap capture(int displayId,int width,int height)throws Exception{
        UiAutomation view;synchronized(AutomationHub.class){view=captureViews.get(displayId);if(displayId<=0||!listeners.containsKey(displayId)||view==null)throw new SecurityException("Capture display is no longer owned.");captures++;}
        try{Bitmap image=view.takeScreenshot();if(image==null)throw new IllegalStateException("Display capture unavailable.");if(image.getWidth()!=width||image.getHeight()!=height){image.recycle();throw new SecurityException("Capture dimensions do not match the owned display.");}return image;}
        finally{synchronized(AutomationHub.class){captures--;if(listeners.isEmpty()&&captures==0)close();}}
    }
}
