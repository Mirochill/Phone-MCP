package fr.phonemcp;

import android.os.Binder;
import android.os.SystemClock;
import org.json.JSONObject;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Shell-side one-shot dispatch. A missing/late result is uncertain and is never retried. */
final class ParallelGestureChannel {
    static final class Uncertain extends IllegalStateException { Uncertain(String message,Throwable cause){super(message,cause);} }
    static void dispatch(IParallelGestureDispatcher target,String epoch,String session,String pkg,int display,int revision,JSONObject request,int appUid)throws Exception{
        if(target==null||!target.asBinder().isBinderAlive())throw new IllegalStateException("Le canal de gestes d’accessibilité est indisponible. Aucun geste n’a été envoyé.");
        String id=request.getString("requestId");long deadline=request.getLong("deadlineElapsedMs");
        if(!ParallelGesturePolicy.uuid(id)||display<=0||SystemClock.elapsedRealtime()>=deadline)throw new SecurityException("Cible ou délai du geste invalide avant envoi.");
        CompletableFuture<JSONObject> completed=new CompletableFuture<>();
        IParallelGestureResult reply=new IParallelGestureResult.Stub(){
            @Override public void finished(String actualId,int actualDisplay,boolean accepted,boolean ok,boolean uncertain,String error){
                if(Binder.getCallingUid()!=appUid||!ParallelGesturePolicy.resultMatches(id,display,actualId,actualDisplay))return;
                try{completed.complete(new JSONObject().put("accepted",accepted).put("completed",ok).put("uncertain",uncertain).put("error",error==null?"":error));}catch(Exception invalid){completed.completeExceptionally(invalid);}
            }
        };
        Exception transportFailure=null;
        try{target.dispatch(epoch,session,pkg,display,revision,request.toString(),reply);}
        catch(Exception transport){transportFailure=transport;}
        // Even a lost one-way Binder acknowledgement must allow the bounded accepted gesture
        // to finish before its display is destroyed. Never send a replacement gesture.
        long remaining=deadline-SystemClock.elapsedRealtime();
        if(remaining<=0)throw new Uncertain("Délai du geste dépassé. Relisez l’état ; le geste n’est pas répété.",null);
        final JSONObject result;
        try{result=completed.get(remaining+200,TimeUnit.MILLISECONDS);}
        catch(TimeoutException missing){throw new Uncertain("Fin du geste non confirmée. La session est arrêtée sans répéter l’action.",transportFailure==null?missing:transportFailure);}
        if(result.optBoolean("uncertain"))throw new Uncertain(result.optString("error","Fin du geste non confirmée."),null);
        if(!result.optBoolean("accepted")||!result.optBoolean("completed"))throw new IllegalStateException(result.optString("error","Geste interrompu. Relisez avant toute nouvelle action."));
    }
}
