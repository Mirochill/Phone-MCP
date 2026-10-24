package fr.phonemcp;

import android.app.*;
import android.content.*;
import android.os.*;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import org.json.JSONObject;
import java.util.concurrent.TimeUnit;
import okhttp3.*;

public final class BridgeService extends Service {
    private static volatile BridgeService instance;
    private static volatile long sessionEnd;
    private static volatile String connection = "Arrêté";
    private static volatile boolean screenControl;
    private static volatile boolean parallel;
    private BackgroundClient background;
    private PowerManager.WakeLock wakeLock;
    private long wakeLeaseAt;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final OkHttpClient client = new OkHttpClient.Builder().pingInterval(60,TimeUnit.SECONDS).build();
    private WebSocket socket;
    private Network defaultNetwork;
    private boolean networkReady;
    private final Runnable retryConnection=()->connect(this.epoch);
    private final ConnectivityManager.NetworkCallback networkCallback=new ConnectivityManager.NetworkCallback(){
        @Override public void onCapabilitiesChanged(Network network,NetworkCapabilities caps){main.post(()->{
            if(!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)||!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))return;
            boolean changed=defaultNetwork!=null&&!defaultNetwork.equals(network);defaultNetwork=network;networkReady=true;
            if(!active())return;
            if(changed&&socket!=null)disconnectTransport(socket);
            main.removeCallbacks(retryConnection);if(socket==null)main.post(retryConnection);
        });}
        @Override public void onLost(Network network){main.post(()->{
            if(defaultNetwork==null||!defaultNetwork.equals(network))return;
            defaultNetwork=null;networkReady=false;main.removeCallbacks(retryConnection);
            if(!active())return;if(socket!=null)disconnectTransport(socket);
            connection="Hors ligne · reconnexion";controlStateChanged();
        });}
    };
    private final java.util.Map<String,String> executing=new java.util.HashMap<>();
    private int wireEpoch;
    private long wakeStartedAt,wakeTotalMs;
    private boolean wakeHeldBeforeCommand;
    private int attempt, epoch;
    private final java.util.concurrent.ExecutorService documents=java.util.concurrent.Executors.newSingleThreadExecutor();
    public static boolean active() { return SessionPolicy.active(instance != null,SystemClock.elapsedRealtime(),sessionEnd); }
    public static boolean connected(){return active()&&connection.startsWith("Connecté");}
    @Override protected void attachBaseContext(Context context){super.attachBaseContext(Locales.wrap(context));}
    private PreviewMedia previewMedia;
    static boolean recordingPreview(String id){BridgeService s=instance;return s!=null&&s.previewMedia!=null&&s.previewMedia.recording(id);}
    static long recordingElapsed(String id){BridgeService s=instance;return s==null||s.previewMedia==null?0:s.previewMedia.elapsed(id);}
    static boolean screenshotPending(String id){BridgeService s=instance;return s!=null&&s.previewMedia!=null&&s.previewMedia.imagePending(id);}
    static void capturePreview(String id,String pkg,boolean video){BridgeService s=instance;if(s==null||s.previewMedia==null||!canPreview(id,pkg))return;if(video){if(s.previewMedia.recording(id))s.previewMedia.stop(id);else s.previewMedia.start(id,pkg);}else s.previewMedia.screenshot(id,pkg);}
    static JSONObject resourceState()throws Exception{BridgeService s=instance;return new JSONObject().put("connected",connected()).put("active",active()).put("parallel",parallelEnabled()).put("wakeLockHeld",s!=null&&s.wakeLock!=null&&s.wakeLock.isHeld()).put("wakeHeldBeforeCommand",s!=null&&s.wakeHeldBeforeCommand).put("previewCards",s==null?new JSONObject():RecentsCards.state(s)).put("localPreview",PreviewActivity.captureState()).put("localCaptures",s==null||s.previewMedia==null?new JSONObject():s.previewMedia.state()).put("activeCommands",s==null?0:s.executing.size()).put("wakeHeldMs",s==null?0:s.wakeTotalMs+(s.wakeStartedAt==0?0:SystemClock.elapsedRealtime()-s.wakeStartedAt));}
    public static boolean unlimited() { return active() && SessionPolicy.unlimited(sessionEnd); }
    public static boolean parallelEnabled() { return active() && parallel; }
    static JSONObject backgroundState() { return instance != null && instance.background != null ? instance.background.state() : new JSONObject(); }
    static void screenChoiceChanged(PhoneAccessibilityService.Reply reply){BridgeService s=instance;if(s==null||s.background==null){reply.done(new JSONObject(),null);return;}s.background.applyScreenChoice(reply);}
    static void stopControl(String sessionId,String pkg,PhoneAccessibilityService.Reply reply){BridgeService s=instance;if(s==null){reply.done(null,"Control stopped");return;}s.background.localStop(sessionId,pkg,reply);}
    static void handoffControl(String sessionId,String pkg,PhoneAccessibilityService.Reply reply){BridgeService s=instance;if(s==null){reply.done(null,"Control stopped");return;}s.background.localHandoff(sessionId,pkg,reply);}
    static void resumeControl(String pkg,PhoneAccessibilityService.Reply reply){BridgeService s=instance;if(s==null){reply.done(null,"Start Phone MCP first");return;}s.background.localResume(pkg,reply);}
    static boolean canPreview(String id,String pkg){BridgeService current=instance;if(!parallelEnabled()||current==null||!BackgroundClient.permitted()||!current.background.controls(id,pkg))return false;Settings settings=new Settings(current);return settings.allowed(pkg,"read")&&settings.allowed(pkg,"capture");}
    static boolean canPreview(String pkg){BridgeService s=instance;return s!=null&&canPreview(s.background.previewSession(pkg),pkg);}
    static String previewSession(String pkg){return instance==null?null:instance.background.previewSession(pkg);}
    private static final java.util.Set<Runnable> uiListeners=new java.util.concurrent.CopyOnWriteArraySet<>();
    static void addUiListener(Runnable listener){uiListeners.add(listener);}
    static void removeUiListener(Runnable listener){uiListeners.remove(listener);}
    static void controlStateChanged(){BridgeService s=instance;if(s!=null)RecentsCards.sync(s);if(s!=null&&active())s.getSystemService(NotificationManager.class).notify(1,s.notification(connection));for(Runnable listener:uiListeners)listener.run();}
    static void permissionsChanged(){BridgeService current=instance;if(current!=null&&active()&&current.background.filesReady())current.background.refresh((result,error)->{});}
    static void previewSurface(String id,String pkg,String viewer,android.view.SurfaceControl parent,int width,int height,PhoneAccessibilityService.Reply reply){BridgeService current=instance;if(current==null||!canPreview(id,pkg)){reply.done(null,"Aperçu arrêté.");return;}current.background.previewSurface(id,pkg,viewer,parent,width,height,reply);}
    static void detachPreview(String viewer){BridgeService current=instance;if(current!=null)current.background.detachPreview(viewer);}
    static void previewFrame(String id,String pkg,PhoneAccessibilityService.Reply reply){BridgeService current=instance;if(!canPreview(id,pkg)||current==null){reply.done(null,"Aperçu arrêté.");return;}current.background.previewFrame(id,pkg,reply);}
    static void showPreview(String pkg){showPreview(previewSession(pkg),pkg);}
    static void showPreview(String id,String pkg){
        BridgeService current=instance;if(current==null||!canPreview(id,pkg))return;
        if(!current.getSystemService(PowerManager.class).isInteractive()||current.getSystemService(KeyguardManager.class).isKeyguardLocked())return;
        Intent intent=RecentsCards.intent(current,id,pkg);
        Context launcher=PhoneAccessibilityService.instance!=null?PhoneAccessibilityService.instance:current;
        try{launcher.startActivity(intent,ActivityOptions.makeBasic().setLaunchDisplayId(android.view.Display.DEFAULT_DISPLAY).toBundle());}
        catch(RuntimeException error){Audit.add("ouverture aperçu",false);android.widget.Toast.makeText(current,R.string.preview_unavailable,android.widget.Toast.LENGTH_LONG).show();}
    }
    public static boolean screenControlEnabled() { return active() && screenControl; }
    static boolean filesOperation(String op){return java.util.Arrays.asList("files_list","file_job","download_file","install_apk","file_upload_begin","file_upload_chunk","file_upload_commit").contains(op);}
    static void files(String op,JSONObject args,PhoneAccessibilityService.Reply reply){
        BridgeService current=instance;
        if(current==null||!active()){reply.done(null,"Démarrez une session pour accéder aux fichiers.");return;}
        current.background.executeFiles(op,args,(result,error)->{current.updateWakeLease();reply.done(result,error);});
    }
    static void revokeFiles(){BridgeService current=instance;if(current!=null&&current.background.filesReady())current.background.executeFiles("files_cancel_all",new JSONObject(),(result,error)->current.updateWakeLease());}
    public static String status() {
        return active() ? connection + (unlimited()?" · sans limite de durée":" · " + Math.max(1,(sessionEnd-SystemClock.elapsedRealtime())/60000) + " min restantes") : "Session arrêtée";
    }
    public static void stop(Context context) {
        sessionEnd = 0; screenControl = false; parallel = false; if(instance!=null&&instance.previewMedia!=null)instance.previewMedia.close(); if(instance != null && instance.background != null) instance.background.close(); PhoneAccessibilityService.invalidate(); PhoneNotificationService.invalidate(); context.stopService(new Intent(context,BridgeService.class));
    }
    private final BroadcastReceiver screenState=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){
        if(background!=null&&parallelEnabled()){
            android.view.Display physical=getSystemService(android.hardware.display.DisplayManager.class).getDisplay(android.view.Display.DEFAULT_DISPLAY);
            boolean on=!Intent.ACTION_SCREEN_OFF.equals(i.getAction())&&physical!=null&&physical.getState()==android.view.Display.STATE_ON&&getSystemService(PowerManager.class).isInteractive();
            background.physicalScreenState(on,Intent.ACTION_USER_PRESENT.equals(i.getAction()));
        }
    }};
    @Override public void onCreate() {
        super.onCreate(); instance = this; background = new BackgroundClient(this); previewMedia=new PreviewMedia(this,background);
        wakeLock=getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"phonemcp:session");
        wakeLock.setReferenceCounted(false);
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("control","Contrôle MCP actif",NotificationManager.IMPORTANCE_LOW));
        startForeground(1,notification("Connexion en cours"));
        ConnectivityManager networks=getSystemService(ConnectivityManager.class);defaultNetwork=networks.getActiveNetwork();
        NetworkCapabilities caps=networks.getNetworkCapabilities(defaultNetwork);networkReady=caps!=null&&caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        networks.registerDefaultNetworkCallback(networkCallback);
        IntentFilter screenEvents=new IntentFilter(Intent.ACTION_SCREEN_OFF);screenEvents.addAction(Intent.ACTION_SCREEN_ON);screenEvents.addAction(Intent.ACTION_USER_PRESENT);
        if(Build.VERSION.SDK_INT>=33)registerReceiver(screenState,screenEvents,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(screenState,screenEvents);
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        if (intent != null && "STOP".equals(intent.getAction())) { stop(this); return START_NOT_STICKY; }
        if(intent!=null&&"STOP_CAPTURES".equals(intent.getAction())){if(previewMedia!=null)previewMedia.stopAll();if(!active())stopSelf();return START_NOT_STICKY;}
        if(intent!=null&&"RESTORE_PANEL".equals(intent.getAction())){
            if(active()&&parallel)background.restorePanel((result,error)->{if(error!=null)android.widget.Toast.makeText(this,error,android.widget.Toast.LENGTH_LONG).show();});
            else if(!active())stopSelf();
            return START_NOT_STICKY;
        }
        if (intent == null) { stopSelf(); return START_NOT_STICKY; }
        int minutes = intent.getIntExtra("minutes",30);
        try{sessionEnd=SessionPolicy.deadline(SystemClock.elapsedRealtime(),minutes);}
        catch(IllegalArgumentException|ArithmeticException error){Audit.add("durée de session",false);stop(this);return START_NOT_STICKY;}
        screenControl = intent.getBooleanExtra("screenControl",false);
        parallel = intent.getBooleanExtra("parallel",false);
        if(parallel)screenControl=false;
        releaseWake();
        // Startup lease only; a failed or locked display must not retain a session-long CPU lock.
        if(parallel){wakeStartedAt=SystemClock.elapsedRealtime();wakeLock.acquire(10000L);}
        main.removeCallbacksAndMessages(null); epoch++;wireEpoch++;executing.clear(); PhoneAccessibilityService.invalidate();
        if (socket != null) socket.cancel();socket=null;
        if(parallel || (BackgroundClient.permitted()&&new Settings(this).filesAllowed())) {
            connection = "Préparation de Shizuku"; final int expected = epoch;
            background.start(sessionEnd,parallel&&intent.getBooleanExtra("panelOff",false),parallel,(result,error) -> { if(expected != epoch || !active())return; if(error != null){ android.widget.Toast.makeText(this,error,android.widget.Toast.LENGTH_LONG).show(); Audit.add("service Shizuku",false); } updateWakeLease(); });
        }else background.close();
        connect(epoch);
        if(!unlimited())main.postDelayed(this::tick,Math.max(1,sessionEnd-SystemClock.elapsedRealtime()));
        return START_NOT_STICKY;
    }
    private void tick(){if(!active())stop(this);}
    private void releaseWake(){if(wakeLock.isHeld())wakeLock.release();if(wakeStartedAt!=0){wakeTotalMs+=SystemClock.elapsedRealtime()-wakeStartedAt;wakeStartedAt=0;}wakeLeaseAt=0;}
    private final Runnable fileTick=()->{if(!active()||!background.fileJobActive()){updateWakeLease();return;}background.refresh((result,error)->updateWakeLease());};
    private void updateWakeLease(){
        main.removeCallbacks(fileTick);
        if(!active()||(executing.isEmpty()&&!background.fileJobActive())){releaseWake();return;}
        if(!wakeLock.isHeld()){wakeStartedAt=SystemClock.elapsedRealtime();wakeLock.acquire(15000L);}
        else if(SystemClock.elapsedRealtime()-wakeLeaseAt>=10000L)wakeLock.acquire(15000L);
        wakeLeaseAt=SystemClock.elapsedRealtime();if(background.fileJobActive())main.postDelayed(fileTick,1000);
    }
    private Notification notification(String text) {
        PendingIntent open = PendingIntent.getActivity(this,1,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop = PendingIntent.getService(this,2,new Intent(this,BridgeService.class).setAction("STOP"),PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder builder=new Notification.Builder(this,"control").setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentTitle("Phone MCP").setContentText(I18n.tr(this,parallel?"Écran séparé":"Écran du téléphone")).setOngoing(true).setContentIntent(open)
                .addAction(new Notification.Action.Builder(null,I18n.tr(this,"Arrêter"),stop).build());
        if(parallel){
            PendingIntent restore=PendingIntent.getService(this,3,new Intent(this,BridgeService.class).setAction("RESTORE_PANEL"),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);builder.addAction(new Notification.Action.Builder(null,I18n.tr(this,"Rallumer l’écran physique"),restore).build());
            String pkg=background.previewPackage();if(pkg!=null&&canPreview(pkg)){
                PendingIntent preview=PendingIntent.getActivity(this,4,RecentsCards.intent(this,previewSession(pkg),pkg),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
                builder.addAction(new Notification.Action.Builder(null,getString(R.string.preview_notification_action),preview).build());
            }
        }
        return builder.build();
    }
    private void connect(int expected) {
        if (!active() || expected != epoch || socket!=null || !networkReady) return;
        try {
            Settings settings = new Settings(this);
            String url = "wss://" + settings.endpoint().substring("https://".length()) + "/device";
            Request request = new Request.Builder().url(url).header("Authorization","Bearer " + settings.token()).build();
            socket = client.newWebSocket(request,new WebSocketListener() {
                @Override public void onOpen(WebSocket ws,Response response) {
                    main.post(() -> { if (expected != epoch || !active() || ws!=socket) { ws.cancel(); return; } attempt = 0; connection = "Connecté au MCP";controlStateChanged(); });
                }
                @Override public void onMessage(WebSocket ws,String text) { main.post(() -> command(ws,text,expected)); }
                @Override public void onClosing(WebSocket ws,int code,String reason){ws.close(code,reason);main.post(()->{if(code==4001&&expected==epoch&&ws==socket){Audit.add("connexion remplacée",false);stop(BridgeService.this);}else reconnect(ws,expected);});}
                @Override public void onClosed(WebSocket ws,int code,String reason) { main.post(() -> reconnect(ws,expected)); }
                @Override public void onFailure(WebSocket ws,Throwable error,Response response) {
                    main.post(() -> {
                        if (expected != epoch || !active() || ws!=socket) return;
                        if (response != null && response.code() == 401) {
                            connection = "Appairage refusé ou téléphone déjà connecté"; Audit.add("connexion",false); stop(BridgeService.this); return;
                        }
                        reconnect(ws,expected);
                    });
                }
            });
        } catch (Exception error) { Audit.add("appairage",false); stop(this); }
    }
    private void reconnect(WebSocket failed,int expected) {
        if (!active() || expected != epoch || failed!=socket) return;
        disconnectTransport(failed);
        connection = "Hors ligne · reconnexion";controlStateChanged();
        main.removeCallbacks(retryConnection);
        if(networkReady){long delay=Math.min(30000,1000L << Math.min(attempt++,5));main.postDelayed(retryConnection,delay);}
    }
    private void disconnectTransport(WebSocket failed){
        if(failed!=socket)return;socket=null;failed.cancel();
        wireEpoch++;executing.clear();background.transportDisconnected();updateWakeLease();
    }
    private void command(WebSocket ws,String text,int expected) {
        if (expected != epoch || !active() || ws != socket || text.length() > 196608) return;
        String cleanupId=null;
        try {
            JSONObject message = new JSONObject(text);
            String id = message.getString("id"), op = message.getString("op");
            long expires = message.getLong("expiresAt");
            if (id.length() > 80 || op.length() > 40) return;
            if(text.length()>20000&&!"file_upload_chunk".equals(op)){respond(ws,id,op,null,"Commande trop volumineuse",expected);return;}
            if (expires < System.currentTimeMillis() || expires > System.currentTimeMillis()+30000) { respond(ws,id,op,null,"Commande expirée",expected); return; }
            if(executing.size()>=16&&!java.util.Arrays.asList("session_cancel","session_end","ad_cancel","stop").contains(op)){respond(ws,id,op,null,"Device concurrency limit reached",expected);return;}
            PhoneAccessibilityService service = PhoneAccessibilityService.instance;
            boolean notificationCommand = java.util.Arrays.asList("notifications","reply_notification","notification_dismiss","notification_snooze").contains(op);
            boolean fileCommand=filesOperation(op);
            boolean documentCommand=java.util.Arrays.asList("storage_locations","list_files","read_file","write_file","delete_file","shared_content","read_shared_file").contains(op);
            if (notificationCommand && PhoneNotificationService.instance == null) { respond(ws,id,op,null,"Accès aux notifications désactivé",expected); return; }
            if (!notificationCommand && !fileCommand && !documentCommand && !parallel && service == null && !"status".equals(op) && !"stop".equals(op)) { respond(ws,id,op,null,"Accessibilité désactivée",expected); return; }
            cleanupId=id;final int wire=wireEpoch;wakeHeldBeforeCommand=wakeLock.isHeld();executing.put(id,op);updateWakeLease();
            JSONObject commandArgs=message.optJSONObject("args");if(commandArgs==null)commandArgs=new JSONObject();commandArgs.put("_expiresAt",expires);message.put("args",commandArgs);
            PhoneAccessibilityService.Reply reply = (result,error) -> main.post(() -> {
                executing.remove(id);updateWakeLease();
                if(expected!=epoch||wire!=wireEpoch||ws!=socket)return;
                if (expires < System.currentTimeMillis()) { respond(ws,id,op,null,"Commande expirée",expected); return; }
                respond(ws,id,op,result,error,expected);
            });
            if(documentCommand)documents.execute(()->{try{if(expected!=epoch||!active()||expires<System.currentTimeMillis())throw new IllegalStateException("Command expired or session stopped.");JSONObject args=message.optJSONObject("args");JSONObject result=op.contains("shared")?new ShareInbox(this).execute(op,args):new StorageAccess(this).execute(op,args);reply.done(result,null);}catch(Exception error){reply.done(null,error.getMessage());}});
            else if("diagnostics".equals(op)||"diagnostic_probe".equals(op))background.diagnostics(op,message.optJSONObject("args"),reply);
            else if("control_mode".equals(op))controlMode(message.optJSONObject("args"),reply);
            else if(fileCommand)files(op,message.optJSONObject("args"),reply);
            else if (notificationCommand) PhoneNotificationService.instance.execute(op,message.optJSONObject("args"),reply);
            else if ("status".equals(op)) {
                if(parallel||background.filesReady())background.refresh((state,error)->{try{reply.done(PhoneAccessibilityService.statusResult(this),null);}catch(Exception e){reply.done(null,e.getMessage());}});
                else reply.done(PhoneAccessibilityService.statusResult(this),null);
            }
            else if ("stop".equals(op)) { reply.done(new JSONObject().put("stopped",true),null); main.postDelayed(() -> stop(this),150); }
            else if(parallel) background.execute(op,message.optJSONObject("args"),reply);
            else service.execute(op,message.optJSONObject("args"),reply);
        } catch (Exception error) {if(cleanupId!=null){executing.remove(cleanupId);updateWakeLease();respond(ws,cleanupId,"invalid",null,"Commande invalide",expected);}Audit.add("commande invalide",false); }
    }
    private void controlMode(JSONObject args,PhoneAccessibilityService.Reply reply){
        try{
            if(!active()||(!parallel&&!screenControl))throw new SecurityException("Une session de contrôle d’écran doit déjà être autorisée. Le mode notifications ne peut pas accorder cet accès.");
            String target=args.getString("mode");boolean toParallel="parallel".equals(target);
            if(!toParallel&&!"foreground".equals(target))throw new IllegalArgumentException("Mode inconnu.");
            if(!getSystemService(PowerManager.class).isInteractive()||getSystemService(KeyguardManager.class).isKeyguardLocked())throw new SecurityException("Déverrouillez le téléphone avant de changer d’affichage.");
            if(!toParallel&&PhoneAccessibilityService.instance==null)throw new SecurityException("Activez l’accessibilité avant de choisir l’écran physique.");
            final int expected=epoch;
            PhoneAccessibilityService.Reply done=(result,error)->{
                if(expected!=epoch||!active())return;
                if(error!=null){reply.done(null,error);return;}
                if(toParallel&&!result.optBoolean("ready")){reply.done(null,"L’affichage séparé n’est pas prêt. Le mode n’a pas été changé.");return;}
                parallel=toParallel;screenControl=!toParallel;PhoneAccessibilityService.invalidate();updateWakeLease();
                Settings config=new Settings(this);config.parallel(toParallel);config.screenControl(!toParallel);
                try{reply.done(PhoneAccessibilityService.statusResult(this).put("modeChanged",true),null);}catch(Exception e){reply.done(null,e.getMessage());}
            };
            if(!background.filesReady()&&!parallel){
                if(toParallel)throw new IllegalStateException("Démarrez une session avec Shizuku avant de choisir l’écran séparé.");
                done.done(new JSONObject(),null);
            }else background.controlMode(target,toParallel&&args.optBoolean("panelOff"),sessionEnd,done);
        }catch(Exception e){reply.done(null,e.getMessage());}
    }
    private void respond(WebSocket ws,String id,String op,JSONObject result,String error,int expected) {
        if (!active() || expected != epoch) return;
        try {
            JSONObject response = new JSONObject().put("id",id).put("ok",error == null);
            if (error != null) response.put("error",error); else response.put("result",result);
            ws.send(response.toString()); Audit.add(op,error == null);
        } catch (Exception ignored) { Audit.add(op,false); }
    }
    @Override public void onDestroy() {
        try{unregisterReceiver(screenState);}catch(Exception ignored){}
        try{getSystemService(ConnectivityManager.class).unregisterNetworkCallback(networkCallback);}catch(Exception ignored){}
        sessionEnd = 0; screenControl = false; parallel = false; if(previewMedia!=null)previewMedia.close(); if(background != null)background.destroy(); epoch++; main.removeCallbacksAndMessages(null);
        if(wakeLock!=null)releaseWake();executing.clear();
        if (socket != null) socket.cancel(); client.dispatcher().executorService().shutdown();
        RecentsCards.sync(this);documents.shutdownNow();instance = null; connection = "Arrêté"; PhoneAccessibilityService.invalidate(); PhoneNotificationService.invalidate();
        stopForeground(STOP_FOREGROUND_REMOVE); super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
