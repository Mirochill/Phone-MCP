package fr.phonemcp;

import android.app.*;
import android.content.*;
import android.os.*;
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
    private final OkHttpClient client = new OkHttpClient.Builder().pingInterval(20,TimeUnit.SECONDS).build();
    private WebSocket socket;
    private boolean busy;
    private int attempt, epoch;
    public static boolean active() { return SessionPolicy.active(instance != null,SystemClock.elapsedRealtime(),sessionEnd); }
    public static boolean unlimited() { return active() && SessionPolicy.unlimited(sessionEnd); }
    public static boolean parallelEnabled() { return active() && parallel; }
    static JSONObject backgroundState() { return instance != null && instance.background != null ? instance.background.state() : new JSONObject(); }
    static boolean canPreview(String pkg){BridgeService current=instance;if(!parallelEnabled()||current==null||!BackgroundClient.permitted()||!current.background.controls(pkg))return false;Settings settings=new Settings(current);return settings.allowed(pkg,"read")&&settings.allowed(pkg,"capture");}
    static void permissionsChanged(){BridgeService current=instance;if(current!=null&&active()&&current.background.filesReady())current.background.refresh((result,error)->{});}
    static void previewSurface(String pkg,String viewer,android.view.SurfaceControl parent,int width,int height,PhoneAccessibilityService.Reply reply){BridgeService current=instance;if(current==null||!canPreview(pkg)){reply.done(null,"Aperçu arrêté.");return;}current.background.previewSurface(pkg,viewer,parent,width,height,reply);}
    static void detachPreview(String viewer){BridgeService current=instance;if(current!=null)current.background.detachPreview(viewer);}
    static void previewFrame(String pkg,PhoneAccessibilityService.Reply reply){BridgeService current=instance;if(!canPreview(pkg)||current==null){reply.done(null,"Aperçu arrêté.");return;}current.background.previewFrame(pkg,reply);}
    static void showPreview(String pkg){
        BridgeService current=instance;if(current==null||!canPreview(pkg))return;
        if(!current.getSystemService(PowerManager.class).isInteractive()||current.getSystemService(KeyguardManager.class).isKeyguardLocked())return;
        Intent intent=new Intent(current,PreviewActivity.class).putExtra("packageName",pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        Context launcher=PhoneAccessibilityService.instance!=null?PhoneAccessibilityService.instance:current;
        try{launcher.startActivity(intent,ActivityOptions.makeBasic().setLaunchDisplayId(android.view.Display.DEFAULT_DISPLAY).toBundle());}
        catch(RuntimeException error){Audit.add("ouverture aperçu",false);}
    }
    public static boolean screenControlEnabled() { return active() && screenControl; }
    static boolean filesOperation(String op){return java.util.Arrays.asList("files_list","file_job","download_file","install_apk").contains(op);}
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
        sessionEnd = 0; screenControl = false; parallel = false; if(instance != null && instance.background != null) instance.background.close(); PhoneAccessibilityService.invalidate(); PhoneNotificationService.invalidate(); context.stopService(new Intent(context,BridgeService.class));
    }
    @Override public void onCreate() {
        super.onCreate(); instance = this; background = new BackgroundClient(this);
        wakeLock=getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"phonemcp:session");
        wakeLock.setReferenceCounted(false);
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("control","Contrôle MCP actif",NotificationManager.IMPORTANCE_LOW));
        startForeground(1,notification("Connexion en cours"));
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        if (intent != null && "STOP".equals(intent.getAction())) { stop(this); return START_NOT_STICKY; }
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
        if(wakeLock.isHeld())wakeLock.release();
        wakeLeaseAt=0;
        // Startup lease only; a failed or locked display must not retain a session-long CPU lock.
        if(parallel)wakeLock.acquire(30000L);
        main.removeCallbacksAndMessages(null); epoch++; busy = false; PhoneAccessibilityService.invalidate();
        if (socket != null) socket.cancel();
        if(parallel || (BackgroundClient.permitted()&&new Settings(this).filesAllowed())) {
            connection = "Préparation de Shizuku"; final int expected = epoch;
            background.start(sessionEnd,parallel&&intent.getBooleanExtra("panelOff",false),parallel,(result,error) -> { if(expected != epoch || !active())return; if(error != null){ android.widget.Toast.makeText(this,error,android.widget.Toast.LENGTH_LONG).show(); Audit.add("service Shizuku",false); } updateWakeLease(); });
        }else background.close();
        connect(epoch);
        main.postDelayed(this::tick,1000);
        return START_NOT_STICKY;
    }
    private void tick() {
        if (!active() || (!parallel && PhoneAccessibilityService.instance == null && PhoneNotificationService.instance == null)
                || !getSystemService(NotificationManager.class).areNotificationsEnabled()) { stop(this); return; }
        getSystemService(NotificationManager.class).notify(1,notification(status()));
        if(parallel||background.filesReady()){
            if(!BackgroundClient.permitted()){BackgroundClient.lastError=BackgroundClient.permissionStatus();background.close();updateWakeLease();}
            else background.refresh((result,error)->updateWakeLease());
        }
        main.postDelayed(this::tick,5000);
    }
    private void updateWakeLease(){
        if(!active()||!((parallel&&background.ready())||background.fileJobActive())){
            if(wakeLock.isHeld())wakeLock.release();wakeLeaseAt=0;return;
        }
        long now=SystemClock.elapsedRealtime();
        if(wakeLeaseAt==0||!wakeLock.isHeld()||now-wakeLeaseAt>=30000L){wakeLock.acquire(90000L);wakeLeaseAt=now;}
    }
    private Notification notification(String text) {
        PendingIntent open = PendingIntent.getActivity(this,1,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop = PendingIntent.getService(this,2,new Intent(this,BridgeService.class).setAction("STOP"),PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder builder=new Notification.Builder(this,"control").setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentTitle("Phone MCP · contrôle autorisé").setContentText(text).setOngoing(true).setContentIntent(open)
                .addAction(new Notification.Action.Builder(null,"Arrêter la session",stop).build());
        if(parallel){
            PendingIntent restore=PendingIntent.getService(this,3,new Intent(this,BridgeService.class).setAction("RESTORE_PANEL"),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);builder.addAction(new Notification.Action.Builder(null,"Rallumer la dalle",restore).build());
            String pkg=background.previewPackage();if(pkg!=null&&canPreview(pkg)){
                PendingIntent preview=PendingIntent.getActivity(this,4,new Intent(this,PreviewActivity.class).putExtra("packageName",pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
                builder.addAction(new Notification.Action.Builder(null,getString(R.string.preview_notification_action),preview).build());
            }
        }
        return builder.build();
    }
    private void connect(int expected) {
        if (!active() || expected != epoch) return;
        try {
            Settings settings = new Settings(this);
            String url = "wss://" + settings.endpoint().substring("https://".length()) + "/device";
            Request request = new Request.Builder().url(url).header("Authorization","Bearer " + settings.token()).build();
            socket = client.newWebSocket(request,new WebSocketListener() {
                @Override public void onOpen(WebSocket ws,Response response) {
                    main.post(() -> { if (expected != epoch || !active()) { ws.cancel(); return; } attempt = 0; connection = "Connecté au MCP"; });
                }
                @Override public void onMessage(WebSocket ws,String text) { main.post(() -> command(ws,text,expected)); }
                @Override public void onClosed(WebSocket ws,int code,String reason) { main.post(() -> reconnect(expected)); }
                @Override public void onFailure(WebSocket ws,Throwable error,Response response) {
                    main.post(() -> {
                        if (expected != epoch || !active()) return;
                        if (response != null && (response.code() == 401 || response.code() == 409)) {
                            connection = "Appairage refusé ou téléphone déjà connecté"; Audit.add("connexion",false); stop(BridgeService.this); return;
                        }
                        reconnect(expected);
                    });
                }
            });
        } catch (Exception error) { Audit.add("appairage",false); stop(this); }
    }
    private void reconnect(int expected) {
        if (!active() || expected != epoch) return;
        busy = false;
        connection = "Hors ligne · reconnexion";
        long delay = Math.min(30000,1000L << Math.min(attempt++,5));
        main.postDelayed(() -> connect(expected),delay);
    }
    private void command(WebSocket ws,String text,int expected) {
        if (expected != epoch || !active() || ws != socket || text.length() > 20000) return;
        try {
            JSONObject message = new JSONObject(text);
            String id = message.getString("id"), op = message.getString("op");
            long expires = message.getLong("expiresAt");
            if (id.length() > 80 || op.length() > 40) return;
            if (expires < System.currentTimeMillis() || expires > System.currentTimeMillis()+30000) { respond(ws,id,op,null,"Commande expirée",expected); return; }
            if (busy) { respond(ws,id,op,null,"Une commande est déjà en cours",expected); return; }
            PhoneAccessibilityService service = PhoneAccessibilityService.instance;
            boolean notificationCommand = "notifications".equals(op) || "reply_notification".equals(op);
            boolean fileCommand=filesOperation(op);
            if (notificationCommand && PhoneNotificationService.instance == null) { respond(ws,id,op,null,"Accès aux notifications désactivé",expected); return; }
            if (!notificationCommand && !fileCommand && !parallel && service == null && !"status".equals(op) && !"stop".equals(op)) { respond(ws,id,op,null,"Accessibilité désactivée",expected); return; }
            busy = true;
            PhoneAccessibilityService.Reply reply = (result,error) -> main.post(() -> {
                if (expected != epoch) return;
                busy = false;
                if (expires < System.currentTimeMillis()) { respond(ws,id,op,null,"Commande expirée",expected); return; }
                respond(ws,id,op,result,error,expected);
            });
            if("control_mode".equals(op))controlMode(message.optJSONObject("args"),reply);
            else if(fileCommand)files(op,message.optJSONObject("args"),reply);
            else if (notificationCommand) PhoneNotificationService.instance.execute(op,message.optJSONObject("args"),reply);
            else if ("status".equals(op)) {
                if(parallel||background.filesReady())background.refresh((state,error)->{try{reply.done(PhoneAccessibilityService.statusResult(this),null);}catch(Exception e){reply.done(null,e.getMessage());}});
                else reply.done(PhoneAccessibilityService.statusResult(this),null);
            }
            else if ("stop".equals(op)) { reply.done(new JSONObject().put("stopped",true),null); main.postDelayed(() -> stop(this),150); }
            else if(parallel) background.execute(op,message.optJSONObject("args"),reply);
            else service.execute(op,message.optJSONObject("args"),reply);
        } catch (Exception error) { busy = false; Audit.add("commande invalide",false); }
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
        sessionEnd = 0; screenControl = false; parallel = false; if(background != null)background.destroy(); epoch++; main.removeCallbacksAndMessages(null);
        if(wakeLock!=null&&wakeLock.isHeld())wakeLock.release();
        if (socket != null) socket.cancel(); client.dispatcher().executorService().shutdown();
        instance = null; connection = "Arrêté"; PhoneAccessibilityService.invalidate(); PhoneNotificationService.invalidate();
        stopForeground(STOP_FOREGROUND_REMOVE); super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
