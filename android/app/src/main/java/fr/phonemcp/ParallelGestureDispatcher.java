package fr.phonemcp;

import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.graphics.Point;
import android.os.Binder;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Display;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** App-side typed accessibility dispatch. Each display has its own independent in-flight gesture. */
final class ParallelGestureDispatcher extends IParallelGestureDispatcher.Stub {
    interface Guard { boolean allowed(String epoch,String session,String pkg,int display,int revision); }
    private final Context context;
    private final Guard guard;
    private final Handler main=new Handler(Looper.getMainLooper());
    private volatile String currentEpoch="";
    private final java.util.Set<String> revoked=ConcurrentHashMap.newKeySet();
    void newEpoch(String epoch){currentEpoch=epoch;revoked.clear();}
    void revokeLocally(String session){if(ParallelGesturePolicy.uuid(session))revoked.add(session);}
    @Override public void revoke(String epoch,String session,int display){if(Binder.getCallingUid()!=2000)throw new SecurityException("Révocation réservée à Shizuku.");if(display>0&&epoch!=null&&epoch.equals(currentEpoch))revokeLocally(session);}
    private final ConcurrentHashMap<Integer,Active> inFlight=new ConcurrentHashMap<>();
    private static final class Active {
        final String epoch,session,pkg,id;final int display,revision;final JSONObject request;final IParallelGestureResult result;final List<TouchGestures.Stroke> strokes;
        final long deadline,bindUntil;final AtomicBoolean done=new AtomicBoolean();boolean accepted;
        Active(String epoch,String session,String pkg,int display,int revision,JSONObject request,IParallelGestureResult result,List<TouchGestures.Stroke> strokes)throws Exception{this.epoch=epoch;this.session=session;this.pkg=pkg;this.display=display;this.revision=revision;this.request=request;this.result=result;this.strokes=strokes;id=request.getString("requestId");deadline=request.getLong("deadlineElapsedMs");bindUntil=Math.min(deadline,SystemClock.elapsedRealtime()+ParallelGesturePolicy.BIND_WAIT_MS);}
    }
    ParallelGestureDispatcher(Context context,Guard guard){this.context=context.getApplicationContext();this.guard=guard;}
    @Override public void dispatch(String epoch,String session,String pkg,int display,int revision,String payload,IParallelGestureResult result){
        if(Binder.getCallingUid()!=2000)throw new SecurityException("Canal de gestes réservé à Shizuku.");
        String requestId="";
        try{
            if(result==null||payload==null||payload.length()>ParallelGesturePolicy.MAX_PAYLOAD)throw new SecurityException("Requête de geste invalide.");
            JSONObject request=new JSONObject(payload);requestId=request.getString("requestId");int window=request.getInt("windowId"),width=request.getInt("width"),height=request.getInt("height");long deadline=request.getLong("deadlineElapsedMs");
            if(!ParallelGesturePolicy.uuid(requestId)||!ParallelGesturePolicy.target(epoch,session,pkg,display,window,width,height,deadline,SystemClock.elapsedRealtime())||revoked.contains(session)||!guard.allowed(epoch,session,pkg,display,revision))throw new SecurityException("Session, application ou affichage du geste arrêté.");
            JSONArray raw=request.getJSONArray("strokes");if(raw.length()<1||raw.length()>ParallelGesturePolicy.MAX_STROKES)throw new SecurityException("Nombre de trajectoires invalide.");
            List<TouchGestures.Stroke> strokes=new ArrayList<>();
            for(int i=0;i<raw.length();i++){JSONObject value=raw.getJSONObject(i);int x=value.getInt("x"),y=value.getInt("y"),tx=value.getInt("toX"),ty=value.getInt("toY"),start=value.getInt("start"),duration=value.getInt("duration");if(!ParallelGesturePolicy.stroke(x,y,tx,ty,start,duration,width,height))throw new SecurityException("Trajectoire hors de la fenêtre autorisée.");strokes.add(new TouchGestures.Stroke(x,y,tx,ty,start,duration));}
            Active active=new Active(epoch,session,pkg,display,revision,request,result,strokes);
            if(inFlight.putIfAbsent(display,active)!=null)throw new IllegalStateException("Un geste précédent se termine encore sur cet affichage ; aucune action n’a été envoyée.");
            main.post(()->begin(active));
        }catch(Exception rejected){try{if(result!=null)result.finished(requestId,display,false,false,false,message(rejected));}catch(Exception ignored){}}
    }
    private void begin(Active active){
        if(active.done.get())return;
        try{
            if(revoked.contains(active.session)||!guard.allowed(active.epoch,active.session,active.pkg,active.display,active.revision)||SystemClock.elapsedRealtime()>=active.deadline)throw new SecurityException("Session arrêtée avant le geste. Aucun geste n’a été envoyé.");
            PhoneAccessibilityService service=PhoneAccessibilityService.instance;
            if(service==null){if(SystemClock.elapsedRealtime()<active.bindUntil){main.postDelayed(()->begin(active),50);return;}throw new IllegalStateException("Le service de gestes n’est pas prêt. Aucun geste n’a été envoyé.");}
            Display target=context.getSystemService(DisplayManager.class).getDisplay(active.display);Point size=new Point();if(target==null||!target.isValid())throw new SecurityException("Affichage IA arrêté.");target.getRealSize(size);
            if(size.x!=active.request.getInt("width")||size.y!=active.request.getInt("height"))throw new SecurityException("Les dimensions de l’affichage ont changé.");
            service.guardParallelGesture(active.pkg,active.display,active.request.getInt("windowId"),size.x,size.y,active.request.getJSONArray("bounds"),active.request.getString("targetDigest"),active.strokes);
            int duration=0;for(TouchGestures.Stroke stroke:active.strokes)duration=Math.max(duration,stroke.start+stroke.duration);
            if(revoked.contains(active.session)||!guard.allowed(active.epoch,active.session,active.pkg,active.display,active.revision)||SystemClock.elapsedRealtime()+duration+500>=active.deadline)throw new SecurityException("Session arrêtée ou délai insuffisant avant le geste. Aucun geste n’a été envoyé.");
            GestureDescription gesture=TouchGestures.build(active.strokes,active.display);
            boolean accepted=service.dispatchGesture(gesture,new android.accessibilityservice.AccessibilityService.GestureResultCallback(){
                @Override public void onCompleted(GestureDescription value){finish(active,true,!revoked.contains(active.session)&&guard.allowed(active.epoch,active.session,active.pkg,active.display,active.revision),false,"Session arrêtée après le geste ; aucune suite n’est autorisée.");}
                @Override public void onCancelled(GestureDescription value){finish(active,true,false,false,"Geste interrompu par Android. Relisez avant toute nouvelle action.");}
            },main);
            active.accepted=accepted;
            if(!accepted)throw new IllegalStateException("Android a refusé le geste avant exécution.");
            main.postDelayed(()->finish(active,true,false,true,"Fin du geste non confirmée. Relisez l’état avant toute nouvelle action."),Math.max(1,active.deadline-SystemClock.elapsedRealtime()));
        }catch(Exception error){finish(active,active.accepted,false,false,message(error));}
    }
    private void finish(Active active,boolean accepted,boolean ok,boolean uncertain,String error){
        if(!active.done.compareAndSet(false,true))return;inFlight.remove(active.display,active);
        try{active.result.finished(active.id,active.display,accepted,ok,uncertain,ok?"":error);}catch(Exception ignored){}
    }
    private static String message(Exception error){return error.getMessage()==null?error.getClass().getSimpleName():error.getMessage();}
}
