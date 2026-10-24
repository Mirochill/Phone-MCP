package fr.phonemcp;

import android.content.Context;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.os.SystemClock;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceControl;
import java.lang.reflect.Constructor;

/** Explicit source-layer clone on an OWN_CONTENT_ONLY encoder display: never auto-mirror display 0. */
@android.annotation.SuppressLint({"PrivateApi","DiscouragedPrivateApi"})
@android.annotation.TargetApi(34)
final class CaptureMirror {
    private VirtualDisplay output;
    private SurfaceControl layer;
    private SurfaceControl outputRoot;
    private String token;
    private long expires;
    int sourceDisplay=-1,outputDisplay=-1,sourceLayerStack=-1,outputLayerStack=-1;
    int outputState(){return output==null?-1:output.getDisplay().getState();}
    boolean active(){return output!=null&&layer!=null;}
    boolean matches(String value){return active()&&value!=null&&value.equals(token);}
    boolean expired(){return active()&&SystemClock.elapsedRealtime()>=expires;}
    void renew(){expires=SystemClock.elapsedRealtime()+4000;}
    void attach(Context context,int source,Surface sink,String id,int width,int height,int density)throws Exception{
        if(source<=Display.DEFAULT_DISPLAY||sink==null||!sink.isValid()||id==null||id.length()>80||width<1||height<1)throw new SecurityException("Source de capture IA invalide.");
        if(active())throw new IllegalStateException("Un enregistrement est déjà actif pour cette session.");
        try{
            Constructor<DisplayManager> ctor=DisplayManager.class.getDeclaredConstructor(Context.class);ctor.setAccessible(true);
            DisplayManager manager=ctor.newInstance(context);
            Display sourceInfo=manager.getDisplay(source);
            if(sourceInfo==null||!sourceInfo.isValid())throw new SecurityException("Affichage IA terminé.");
            // No AUTO_MIRROR, PUBLIC or SECURE flag. If the source disappears, this output stays
            // empty; Android must never select the physical screen as a replacement source.
            output=manager.createVirtualDisplay("Phone MCP · capture IA",width,height,density,sink,DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY);
            if(output==null)throw new IllegalStateException("Sortie vidéo indisponible.");
            outputDisplay=output.getDisplay().getDisplayId();
            int sourceStack=(Integer)Display.class.getMethod("getLayerStack").invoke(sourceInfo);
            int targetStack=(Integer)Display.class.getMethod("getLayerStack").invoke(output.getDisplay());
            if(outputDisplay<=0||outputDisplay==source||targetStack<=0||sourceStack==targetStack)throw new SecurityException("Isolation de la sortie vidéo non confirmée.");
            Constructor<SurfaceControl> sc=SurfaceControl.class.getDeclaredConstructor();sc.setAccessible(true);layer=sc.newInstance();
            Object wm=Class.forName("android.view.WindowManagerGlobal").getMethod("getWindowManagerService").invoke(null);
            boolean mirrored=(Boolean)Class.forName("android.view.IWindowManager").getMethod("mirrorDisplay",int.class,SurfaceControl.class).invoke(wm,source,layer);
            if(!mirrored||!layer.isValid())throw new IllegalStateException("Miroir vidéo indisponible.");
            SurfaceControl.Builder rootBuilder=new SurfaceControl.Builder().setName("Phone MCP · encode root").setHidden(true);
            SurfaceControl.Builder.class.getMethod("setContainerLayer").invoke(rootBuilder);
            outputRoot=rootBuilder.build();
            sourceLayerStack=sourceStack;outputLayerStack=targetStack;
            try(SurfaceControl.Transaction tx=new SurfaceControl.Transaction()){
                // A mirrored layer starts offscreen and cannot become a display root by itself.
                // Bind it under a real, initially hidden container belonging only to this output.
                tx.reparent(layer,outputRoot);
                SurfaceControl.Transaction.class.getMethod("setDropInputMode",SurfaceControl.class,int.class).invoke(tx,layer,1);
                SurfaceControl.Transaction.class.getMethod("setLayerStack",SurfaceControl.class,int.class).invoke(tx,outputRoot,targetStack);
                tx.setLayer(outputRoot,1).setVisibility(outputRoot,true);
                tx.setLayer(layer,1).setCrop(layer,new Rect(0,0,width,height)).setPosition(layer,0,0).setVisibility(layer,true).apply();
            }
            sourceDisplay=source;token=id;renew();
        }catch(Exception error){clear();throw error;}
    }
    void clear(){
        SurfaceControl old=layer;layer=null;token=null;expires=0;
        if(old!=null){try{if(old.isValid())try(SurfaceControl.Transaction tx=new SurfaceControl.Transaction()){tx.setVisibility(old,false).reparent(old,null).apply();}}finally{old.release();}}
        SurfaceControl root=outputRoot;outputRoot=null;
        if(root!=null){try{if(root.isValid())try(SurfaceControl.Transaction tx=new SurfaceControl.Transaction()){tx.setVisibility(root,false).apply();}}finally{root.release();}}
        VirtualDisplay previous=output;output=null;if(previous!=null)previous.release();sourceDisplay=outputDisplay=-1;
    }
}