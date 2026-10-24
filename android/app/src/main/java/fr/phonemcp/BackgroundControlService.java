package fr.phonemcp;

import android.content.Context;
import android.os.*;
import org.json.*;

/** Binder is handed only to this app by Shizuku. No command socket and no shell-command tool. */
public final class BackgroundControlService extends IBackgroundControl.Stub {
    private final SessionEngineManager engine;
    private final FileEngine files;
    private final int ownerUid;
    private IBinder owner;
    private final IBinder.DeathRecipient death=this::ownerGone;
    private void ownerGone(){engine.destroy();files.destroy();System.exit(0);}
    public BackgroundControlService(Context context)throws Exception{ownerUid=context.getApplicationInfo().uid;files=new FileEngine(context,ownerUid);engine=new SessionEngineManager(context);}
    private void enforceCaller(){int uid=Binder.getCallingUid();if(uid!=ownerUid && uid!=android.os.Process.myUid())throw new SecurityException("Appelant non autorisé");}
    private String wrap(java.util.concurrent.Callable<JSONObject> call){try{return new JSONObject().put("ok",true).put("result",call.call()).toString();}catch(Exception e){return error(e);}}
    static String error(Exception e){try{return new JSONObject().put("ok",false).put("error",e.getMessage()==null?e.getClass().getSimpleName():e.getMessage()).toString();}catch(Exception ignored){return "{\"ok\":false,\"error\":\"Erreur du service\"}";}}
    @Override public String start(long end,IBinder token,boolean screenOff,boolean parallelDisplay){enforceCaller();return wrap(()->{if(token==null)throw new SecurityException("Session sans propriétaire");if(owner!=null)owner.unlinkToDeath(death,0);owner=token;owner.linkToDeath(death,0);files.start(end);JSONObject result=new JSONObject().put("ready",false);if(parallelDisplay)try{result=engine.start(end,screenOff,true);}catch(Exception error){result.put("error",error.getMessage());}return merge(result);});}
    private JSONObject merge(JSONObject state)throws Exception{JSONObject f=files.status();java.util.Iterator<String> keys=f.keys();while(keys.hasNext()){String key=keys.next();state.put(key,f.get(key));}return state;}
    @Override public String setPhysicalScreenAwake(boolean keepOn,long revision){enforceCaller();return wrap(()->merge(engine.setPhysicalScreenAwake(keepOn,revision)));}
    @Override public String restorePanel(){enforceCaller();return wrap(()->merge(engine.restorePanel()));}
    @Override public String physicalScreenState(boolean on,boolean userPresent){enforceCaller();return wrap(()->merge(engine.physicalScreenState(on,userPresent)));}
    @Override public void setGestureDispatcher(IParallelGestureDispatcher dispatcher,String epoch){enforceCaller();engine.setGestureDispatcher(dispatcher,epoch);}
    @Override public void setPreviewCallback(IPreviewCallback callback){enforceCaller();engine.setPreviewCallback(callback);}
    @Override public void accessibilitySettingsChanged(){enforceCaller();engine.accessibilitySettingsChanged();}
    @Override public String attachPreview(String arguments,android.view.SurfaceControl parent){enforceCaller();return wrap(()->engine.attachPreview(previewArgs(arguments),parent));}
    @Override public String renewPreview(String arguments){enforceCaller();return wrap(()->engine.renewPreview(previewArgs(arguments)));}
    @Override public void detachPreview(String viewerId){enforceCaller();engine.detachPreview(viewerId);}
    private JSONObject previewArgs(String args)throws Exception{if(args==null||args.length()>20000)throw new IllegalArgumentException("Arguments invalides");return new JSONObject(args);}
    @Override public String previewFrame(String arguments){enforceCaller();return wrap(()->{
        if(arguments==null||arguments.length()>20000)throw new IllegalArgumentException("Arguments invalides");
        return engine.previewFrame(new JSONObject(arguments));
    });}
    @Override public String savePreviewImage(String arguments,android.os.ParcelFileDescriptor destination){enforceCaller();return wrap(()->{try(android.os.ParcelFileDescriptor output=destination){if(output==null)throw new IllegalArgumentException("Sortie absente.");return engine.savePreviewImage(previewArgs(arguments),output);}});}
    @Override public String attachRecording(String arguments,android.view.Surface destination){enforceCaller();return wrap(()->engine.attachRecording(previewArgs(arguments),destination));}
    @Override public String renewRecording(String arguments){enforceCaller();return wrap(()->engine.renewRecording(previewArgs(arguments)));}
    @Override public void detachRecording(String token){enforceCaller();engine.detachRecording(token);}
    @Override public String execute(String operation,String arguments){enforceCaller();return wrap(()->{if(arguments==null || arguments.length()>("file_upload_chunk".equals(operation)?196608:20000))throw new IllegalArgumentException("Arguments invalides");JSONObject args=new JSONObject(arguments);
        if("diagnostics".equals(operation))return ResourceDiagnostics.shellSample(engine.systemContext()).put("control",engine.diagnostics());
        if("transport_disconnected".equals(operation)){engine.disconnect(args.getInt("revision"));return new JSONObject().put("disconnected",true);}
        if("diagnostic_probe".equals(operation))return ResourceDiagnostics.probe(files.ownerContext(),ownerUid,args);
        if("control_mode".equals(operation)){
            if(owner==null||!SessionPolicy.active(true,SystemClock.elapsedRealtime(),args.getLong("deadline")))throw new SecurityException("Session de contrôle arrêtée.");
            String mode=args.getString("mode");if(!"foreground".equals(mode)&&!"parallel".equals(mode))throw new IllegalArgumentException("Mode inconnu.");
            if("parallel".equals(mode))return engine.isEnabled()?merge(engine.execute("status",args)):merge(engine.start(args.getLong("deadline"),args.optBoolean("panelOff")));
            engine.stopForModeSwitch();return merge(engine.execute("status",new JSONObject()));
        }
        if(operation.startsWith("file")||"download_file".equals(operation)||"install_apk".equals(operation))return files.execute(operation,args);JSONObject result=engine.execute(operation,args);return "status".equals(operation)?merge(result):result;});}
    @Override public void stop(){enforceCaller();files.stop();engine.stop();}
    @Override public void destroy(){enforceCaller();if(owner!=null)owner.unlinkToDeath(death,0);files.destroy();engine.destroy();System.exit(0);}
}

