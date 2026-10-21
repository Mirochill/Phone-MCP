package fr.phonemcp;

import android.annotation.SuppressLint;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.SurfaceControl;
import org.json.JSONArray;
import java.lang.reflect.Constructor;

/** A compositor mirror has no input window and never forwards events to its source. */
@SuppressLint({"PrivateApi","DiscouragedPrivateApi"})
@android.annotation.TargetApi(34)
final class PreviewMirror {
    private SurfaceControl layer;
    String viewer,pkg;
    JSONArray capturePackages;
    long deadline;
    boolean active(){return layer!=null;}
    void attach(int displayId,SurfaceControl parent,String id,String app,JSONArray captures,int width,int height)throws Exception{
        if(displayId<=0||parent==null||!parent.isValid()||width<1||height<1||width>8192||height>8192)throw new SecurityException("Surface d’aperçu invalide.");
        clear();
        Constructor<SurfaceControl> constructor=SurfaceControl.class.getDeclaredConstructor();constructor.setAccessible(true);
        SurfaceControl mirror=constructor.newInstance();
        try{
            Object wm=Class.forName("android.view.WindowManagerGlobal").getMethod("getWindowManagerService").invoke(null);
            boolean ok=(Boolean)Class.forName("android.view.IWindowManager").getMethod("mirrorDisplay",int.class,SurfaceControl.class).invoke(wm,displayId,mirror);
            if(!ok||!mirror.isValid())throw new IllegalStateException("Miroir Android indisponible.");
            float scale=Math.min((float)width/VirtualEngine.WIDTH,(float)height/VirtualEngine.HEIGHT);
            try(SurfaceControl.Transaction transaction=new SurfaceControl.Transaction()){
                // Drop input on this mirror and its mirrored children, without touching the source.
                SurfaceControl.Transaction.class.getMethod("setDropInputMode",SurfaceControl.class,int.class).invoke(transaction,mirror,1); // DropInputMode.ALL
                transaction.reparent(mirror,parent).setLayer(mirror,1).setCrop(mirror,new Rect(0,0,VirtualEngine.WIDTH,VirtualEngine.HEIGHT))
                    .setScale(mirror,scale,scale).setPosition(mirror,(width-VirtualEngine.WIDTH*scale)/2,(height-VirtualEngine.HEIGHT*scale)/2).setVisibility(mirror,true).apply();
            }
            layer=mirror;viewer=id;pkg=app;capturePackages=captures;deadline=SystemClock.elapsedRealtime()+2500;
        }catch(Exception error){mirror.release();throw error;}
    }
    boolean matches(String id){return layer!=null&&id!=null&&id.equals(viewer);}
    void visible(boolean value){if(layer!=null&&layer.isValid())try(SurfaceControl.Transaction transaction=new SurfaceControl.Transaction()){transaction.setVisibility(layer,value).apply();}}
    void clear(){
        SurfaceControl old=layer;layer=null;viewer=null;pkg=null;capturePackages=null;deadline=0;
        if(old!=null){try{if(old.isValid())try(SurfaceControl.Transaction transaction=new SurfaceControl.Transaction()){transaction.setVisibility(old,false).reparent(old,null).apply();}}finally{old.release();}}
    }
}
