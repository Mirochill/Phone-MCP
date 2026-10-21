package fr.phonemcp;

import android.content.*;
import android.content.pm.PackageManager;
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
    private final ExecutorService calls=Executors.newSingleThreadExecutor();
    private final ExecutorService previews=Executors.newSingleThreadExecutor();
    private final Set<String> controlledPackages=ConcurrentHashMap.newKeySet();
    private volatile String latestPackage;
    private final IPreviewCallback previewCallback=new IPreviewCallback.Stub(){
        @Override public void onPreviewRequested(String pkg){main.post(()->{if(controlledPackages.contains(pkg))BridgeService.showPreview(pkg);});}
    };
    private final IBinder owner=new Binder();
    private volatile IBackgroundControl remote;
    private volatile JSONObject state;
    private volatile int generation;
    private Shizuku.UserServiceArgs serviceArgs;
    private ServiceConnection connection;
    BackgroundClient(Context c){context=c.getApplicationContext();}
    static boolean permitted(){try{return Shizuku.pingBinder()&&!Shizuku.isPreV11()&&Shizuku.getUid()==2000&&Shizuku.checkSelfPermission()==PackageManager.PERMISSION_GRANTED;}catch(Exception e){return false;}}
    static String permissionStatus(){try{
        if(!Shizuku.pingBinder())return "Shizuku arrêté ou absent";
        if(Shizuku.getUid()!=2000)return "Démarrez Shizuku avec le débogage sans fil, sans root";
        return Shizuku.checkSelfPermission()==PackageManager.PERMISSION_GRANTED?"Shizuku autorisé":"Autorisation Phone MCP à accorder dans Shizuku";
    }catch(Exception e){return "Shizuku indisponible";}}
    boolean ready(){IBackgroundControl r=remote;return r!=null&&r.asBinder().isBinderAlive()&&state!=null&&state.optBoolean("ready");}
    JSONObject state(){
        try{return new JSONObject(state==null?"{}":state.toString()).put("ready",ready()).put("error",lastError.isEmpty()&&state!=null?state.optString("error"):lastError).put("shizuku",permissionStatus());}
        catch(JSONException e){return new JSONObject();}
    }
    boolean filesReady(){return remote!=null&&remote.asBinder().isBinderAlive()&&state!=null&&state.optBoolean("fileServiceReady");}
    boolean fileJobActive(){return state!=null&&state.optBoolean("fileJobActive");}
    boolean controls(String pkg){return pkg!=null&&controlledPackages.contains(pkg)&&ready();}
    String previewPackage(){return controls(latestPackage)?latestPackage:null;}
    void start(long deadline,boolean screenOff,boolean parallelDisplay,PhoneAccessibilityService.Reply reply){
        IBackgroundControl running=remote;
        if(running!=null&&running.asBinder().isBinderAlive()&&permitted()){
            final int expected=++generation;state=null;lastError="";controlledPackages.clear();latestPackage=null;
            calls.execute(()->{try{running.stop();running.setPreviewCallback(previewCallback);JSONObject result=unwrap(running.start(deadline,owner,screenOff,parallelDisplay));main.post(()->{if(expected==generation){state=result;reply.done(result,null);}});}catch(Exception e){main.post(()->{if(expected==generation){lastError=message(e);reply.done(null,lastError);}});}});
            return;
        }
        close();lastError="";final int expected=generation;
        if(!permitted()){lastError=permissionStatus();reply.done(null,lastError);return;}
        AtomicBoolean finished=new AtomicBoolean();
        PhoneAccessibilityService.Reply once=(result,error)->{if(expected==generation&&finished.compareAndSet(false,true))reply.done(result,error);};
        serviceArgs=new Shizuku.UserServiceArgs(new ComponentName(context,BackgroundControlService.class)).daemon(false).processNameSuffix("parallel").debuggable(false).version(18);
        connection=new ServiceConnection(){
            @Override public void onServiceConnected(ComponentName name,IBinder binder){
                if(BackgroundClient.this.connection!=this)return;
                IBackgroundControl candidate=IBackgroundControl.Stub.asInterface(binder);remote=candidate;
                calls.execute(()->{
                    try{candidate.setPreviewCallback(previewCallback);JSONObject result=unwrap(candidate.start(deadline,owner,screenOff,parallelDisplay));main.post(()->{if(expected!=generation)return;state=result;once.done(result,null);});}
                    catch(Exception e){main.post(()->{if(expected!=generation)return;lastError=message(e);once.done(null,lastError);});}
                });
            }
            @Override public void onServiceDisconnected(ComponentName name){if(BackgroundClient.this.connection==this){remote=null;state=null;lastError="Le service Shizuku s’est arrêté. Relancez une session.";}}
        };
        calls.execute(()->main.post(()->{
            if(expected!=generation)return;
            try{Shizuku.bindUserService(serviceArgs,connection);}catch(Exception e){lastError=message(e);once.done(null,lastError);}
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
            try{JSONObject leases=new JSONObject().put("allowedLeasePackages",allowedLeases());JSONObject result=unwrap(service.execute("status",leases.toString()));main.post(()->{if(expected!=generation)return;state=result;reply.done(state(),null);});}
            catch(Exception e){main.post(()->{if(expected!=generation)return;state=null;lastError=message(e);reply.done(state(),null);});}
        });
    }
    void restorePanel(PhoneAccessibilityService.Reply reply){
        IBackgroundControl service=remote;int expected=generation;
        if(service==null){reply.done(null,"Service parallèle arrêté");return;}
        calls.execute(()->{
            try{JSONObject result=unwrap(service.restorePanel());main.post(()->{if(expected!=generation)return;state=result;reply.done(result,null);});}
            catch(Exception e){main.post(()->{if(expected==generation)reply.done(null,message(e));});}
        });
    }
    private JSONArray allowedLeases(){
        Settings settings=new Settings(context);JSONArray allowed=new JSONArray();
        for(String pkg:controlledPackages){if(settings.allowed(pkg,"read")&&settings.allowed(pkg,"write"))allowed.put(pkg);else controlledPackages.remove(pkg);}
        return allowed;
    }
    private JSONObject previewArguments(String pkg)throws Exception{
        Settings settings=new Settings(context);
        if(!BridgeService.parallelEnabled()||!controls(pkg)||!permitted()||!settings.allowed(pkg,"read")||!settings.allowed(pkg,"capture"))throw new SecurityException("Aperçu indisponible pour cette app.");
        JSONArray captures=new JSONArray().put(pkg),windows=state==null?null:state.optJSONArray("windows");
        if(windows!=null)for(int i=0;i<windows.length();i++){String visible=windows.getJSONObject(i).optString("packageName");if(settings.allowed(visible,"read")&&settings.allowed(visible,"capture"))captures.put(visible);}
        return new JSONObject().put("packageName",pkg).put("capturePackages",captures);
    }
    void previewSurface(String pkg,String viewer,android.view.SurfaceControl parent,int width,int height,PhoneAccessibilityService.Reply reply){
        final int expected=generation;final IBackgroundControl service=remote;
        try{
            JSONObject args=previewArguments(pkg).put("viewerId",viewer);if(parent!=null)args.put("width",width).put("height",height);
            String payload=args.toString();
            previews.execute(()->{try{
                if(expected!=generation)throw new IllegalStateException("Session arrêtée.");previewArguments(pkg);
                JSONObject result=unwrap(parent==null?service.renewPreview(payload):service.attachPreview(payload,parent));
                main.post(()->{if(expected!=generation||!BridgeService.canPreview(pkg)){detachPreview(viewer);reply.done(null,"Aperçu arrêté.");}else reply.done(result,null);});
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
            main.post(()->{if(expected==generation){state=result;lastError="";controlledPackages.clear();latestPackage=null;reply.done(result,null);}});
        }catch(Exception error){main.post(()->{if(expected==generation){lastError=message(error);reply.done(null,lastError);}});}});
    }
    void execute(String op,JSONObject args,PhoneAccessibilityService.Reply reply){
        final int expected=generation;final IBackgroundControl service=remote;
        try{
            if(!BridgeService.active()||!BridgeService.parallelEnabled()||!ready()||!permitted())throw new IllegalStateException(lastError.isEmpty()?"Mode parallèle non prêt. Consultez phone_status puis relancez la session avec Shizuku.":lastError);
            if(!Arrays.asList("open_app","open_url","snapshot","screenshot","click","long_press","set_text","scroll","back","tap","swipe").contains(op))throw new IllegalArgumentException("Commande parallèle inconnue.");
            String pkg=args.getString("packageName");Settings settings=new Settings(context);
            String permission="screenshot".equals(op)?"capture":"snapshot".equals(op)?"read":"write";
            if(!settings.allowed(pkg,"read")||!settings.allowed(pkg,permission))throw new SecurityException("Application ou capacité non autorisée.");
            JSONObject forwarded=new JSONObject(args.toString());
            if("screenshot".equals(op)){JSONArray permitted=new JSONArray().put(pkg),windows=state==null?null:state.optJSONArray("windows");if(windows!=null)for(int i=0;i<windows.length();i++){String visible=windows.getJSONObject(i).optString("packageName");if(settings.allowed(visible,"read")&&settings.allowed(visible,"capture"))permitted.put(visible);}forwarded.put("capturePackages",permitted);}
            final String payload=forwarded.toString();
            calls.execute(()->{
                try{if(expected!=generation||!BridgeService.active()||!permitted())throw new IllegalStateException("Session ou autorisation arrêtée avant envoi.");JSONObject result=unwrap(service.execute(op,payload));main.post(()->{if(expected==generation){if(("open_app".equals(op)||"open_url".equals(op))&&!pkg.equals(context.getPackageName())){controlledPackages.add(pkg);latestPackage=pkg;}reply.done(result,null);}});}
                catch(Exception e){main.post(()->{if(expected==generation)reply.done(null,message(e));});}
            });
        }catch(Exception e){reply.done(null,message(e));}
    }
    void previewFrame(String pkg,PhoneAccessibilityService.Reply reply){
        final int expected=generation;final IBackgroundControl service=remote;
        try{
            Settings settings=new Settings(context);
            if(!BridgeService.parallelEnabled()||!controls(pkg)||!permitted()||!settings.allowed(pkg,"read")||!settings.allowed(pkg,"capture"))throw new SecurityException("Aperçu indisponible pour cette app.");
            JSONArray captures=new JSONArray().put(pkg),windows=state==null?null:state.optJSONArray("windows");
            if(windows!=null)for(int i=0;i<windows.length();i++){String visible=windows.getJSONObject(i).optString("packageName");if(settings.allowed(visible,"read")&&settings.allowed(visible,"capture"))captures.put(visible);}
            String args=new JSONObject().put("packageName",pkg).put("capturePackages",captures).toString();
            previews.execute(()->{try{
                if(expected!=generation||!BridgeService.parallelEnabled()||!controls(pkg)||!permitted())throw new SecurityException("Session arrêtée.");
                JSONObject frame=unwrap(service.previewFrame(args));
                main.post(()->{Settings current=new Settings(context);if(expected!=generation||!BridgeService.parallelEnabled()||!controls(pkg)||!permitted()||!current.allowed(pkg,"read")||!current.allowed(pkg,"capture"))reply.done(null,"Aperçu arrêté.");else reply.done(frame,null);});
            }catch(Exception error){main.post(()->reply.done(null,message(error)));}});
        }catch(Exception error){reply.done(null,message(error));}
    }
    void executeFiles(String op,JSONObject args,PhoneAccessibilityService.Reply reply){
        final int expected=generation;final IBackgroundControl service=remote;
        try{
            if(!BridgeService.active()||!filesReady()||!permitted())throw new IllegalStateException("Démarrez Shizuku puis relancez la session pour les fichiers et APK.");
            if(!Arrays.asList("files_list","file_job","download_file","install_apk","files_cancel_all").contains(op))throw new IllegalArgumentException("Commande fichiers inconnue.");
            if(!new Settings(context).filesAllowed()&&!"files_cancel_all".equals(op))throw new SecurityException("Activez Fichiers et APK dans les réglages de Phone MCP.");
            String payload=args.toString();
            calls.execute(()->{try{
                if(expected!=generation||!BridgeService.active()||!permitted()||(!new Settings(context).filesAllowed()&&!"files_cancel_all".equals(op)))throw new SecurityException("Session ou autorisation fichiers arrêtée.");
                JSONObject result=unwrap(service.execute(op,payload));JSONObject next=unwrap(service.execute("status","{}"));
                main.post(()->{if(expected==generation){state=next;reply.done(result,null);}});
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
        generation++;state=null;controlledPackages.clear();latestPackage=null;IBackgroundControl old=remote;remote=null;
        Shizuku.UserServiceArgs oldArgs=serviceArgs;ServiceConnection oldConnection=connection;
        serviceArgs=null;connection=null;
        if(oldArgs==null||oldConnection==null)return;
        calls.execute(()->{
            try{if(old!=null&&old.asBinder().isBinderAlive())old.stop();}catch(Exception ignored){}
            main.post(()->{try{Shizuku.unbindUserService(oldArgs,oldConnection,true);}catch(Exception ignored){}});
        });
    }
    void destroy(){close();calls.shutdown();previews.shutdownNow();}
}
