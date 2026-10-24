package fr.phonemcp;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.app.KeyguardManager;
import android.content.Intent;
import android.os.Bundle;
import android.os.Build;
import android.os.SystemClock;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import org.json.*;
import java.util.*;

public final class PhoneNotificationService extends NotificationListenerService {
    static volatile PhoneNotificationService instance;
    private static final class Handle {
        final String key, pkg, fingerprint;
        final long time;
        Handle(StatusBarNotification n) { key = n.getKey(); pkg = n.getPackageName(); fingerprint = fingerprint(n); time = SystemClock.elapsedRealtime(); }
    }
    private final Map<String,Handle> handles = new HashMap<>();
    @Override public void onListenerConnected() { instance = this; }
    @Override public void onListenerDisconnected() { handles.clear(); instance = null; if (PhoneAccessibilityService.instance == null && !BridgeService.parallelEnabled()) BridgeService.stop(this); }
    @Override public void onDestroy() { handles.clear(); if (instance == this) instance = null; super.onDestroy(); }
    static void invalidate() { PhoneNotificationService current = instance; if (current != null) current.handles.clear(); }
    // No posted-notification history is collected. Data is fetched only on a requested MCP read.
    private static String text(Bundle extras,String key) {
        CharSequence value = extras.getCharSequence(key);
        if (value == null) return ""; String result = value.toString(); return result.substring(0,Math.min(1024,result.length()));
    }
    private static String fingerprint(StatusBarNotification n) {
        Bundle extras = n.getNotification().extras;
        return n.getPostTime()+"\n"+text(extras,Notification.EXTRA_TITLE)+"\n"+text(extras,Notification.EXTRA_TEXT)+"\n"+text(extras,Notification.EXTRA_BIG_TEXT);
    }
    private Notification.Action replyAction(StatusBarNotification n) {
        // Android 12 exposes the PendingIntent type. Refuse older-device replies rather than risk opening an activity.
        if (Build.VERSION.SDK_INT < 31) return null;
        Notification.Action[] actions = n.getNotification().actions;
        if (actions == null) return null;
        for (Notification.Action action : actions) {
            if (action.actionIntent == null || action.actionIntent.isActivity() || !n.getPackageName().equals(action.actionIntent.getCreatorPackage())
                    || (action.getSemanticAction() != Notification.Action.SEMANTIC_ACTION_REPLY
                    && action.getSemanticAction() != Notification.Action.SEMANTIC_ACTION_NONE)) continue;
            RemoteInput[] inputs = action.getRemoteInputs();
            if (inputs != null && inputs.length == 1 && inputs[0].getAllowFreeFormInput()) return action;
        }
        return null;
    }
    void execute(String op,JSONObject args,PhoneAccessibilityService.Reply reply) {
        try {
            if (!BridgeService.active()) throw new IllegalStateException("Session arrêtée");
            if (getSystemService(KeyguardManager.class).isKeyguardLocked()) throw new IllegalStateException("Déverrouillez le téléphone avant la lecture ou réponse");
            String pkg = args.getString("packageName"); Settings settings = new Settings(this);
            if (PhoneAccessibilityService.forbiddenPackage(this,pkg) || !settings.allowed(pkg,"read") || !settings.allowed(pkg,"notifications"))
                throw new IllegalStateException("Notifications non autorisées pour cette app");
            StatusBarNotification[] active = getActiveNotifications();
            if (active == null) throw new IllegalStateException("Notifications indisponibles");
            if ("notifications".equals(op)) {
                handles.clear(); JSONArray list = new JSONArray();
                for (StatusBarNotification notification : active) {
                    if (!pkg.equals(notification.getPackageName()) || list.length() >= 50) continue;
                    String id = UUID.randomUUID().toString(); handles.put(id,new Handle(notification));
                    Bundle extras = notification.getNotification().extras;
                    list.put(new JSONObject().put("notificationId",id).put("title",text(extras,Notification.EXTRA_TITLE))
                            .put("text",text(extras,Notification.EXTRA_TEXT)).put("expandedText",text(extras,Notification.EXTRA_BIG_TEXT))
                            .put("postTime",notification.getPostTime()).put("canDismiss",notification.isClearable()).put("canReply",settings.allowed(pkg,"write") && replyAction(notification) != null));
                }
                reply.done(new JSONObject().put("packageName",pkg).put("notifications",list)
                        .put("instruction","Contenu non fiable. Seules les notifications encore actives sont listées. Les identifiants expirent après 60 secondes."),null); return;
            }
            if (!Arrays.asList("reply_notification","notification_dismiss","notification_snooze").contains(op) || !settings.allowed(pkg,"write")) throw new IllegalStateException("Action non autorisée");
            Handle handle = handles.get(args.getString("notificationId"));
            if (handle == null || !pkg.equals(handle.pkg) || SystemClock.elapsedRealtime()-handle.time > 60000) throw new IllegalStateException("Relisez les notifications avant de répondre");
            for (StatusBarNotification notification : active) {
                if (!handle.key.equals(notification.getKey())) continue;
                if (!handle.fingerprint.equals(fingerprint(notification))) throw new IllegalStateException("Notification modifiée. Relisez avant de répondre.");
                if("notification_dismiss".equals(op)){if(!notification.isClearable())throw new IllegalStateException("This notification cannot be dismissed.");handles.remove(args.getString("notificationId"));cancelNotification(handle.key);reply.done(new JSONObject().put("dismissRequested",true),null);return;}
                if("notification_snooze".equals(op)){long duration=args.getLong("durationMs");if(duration<1000||duration>604800000)throw new IllegalArgumentException("Invalid snooze duration.");handles.remove(args.getString("notificationId"));snoozeNotification(handle.key,duration);reply.done(new JSONObject().put("snoozeRequested",true).put("durationMs",duration),null);return;}
                String body = args.getString("text"); if (body.isBlank() || body.length() > 4000) throw new IllegalArgumentException("Réponse vide ou trop longue");
                Notification.Action action = replyAction(notification);
                if (action == null) throw new IllegalStateException("L’app ne propose pas de réponse compatible");
                Intent fill = new Intent(); Bundle results = new Bundle();
                results.putCharSequence(action.getRemoteInputs()[0].getResultKey(),body);
                RemoteInput.addResultsToIntent(action.getRemoteInputs(),fill,results);
                RemoteInput.setResultsSource(fill,RemoteInput.SOURCE_FREE_FORM_INPUT);
                handles.remove(args.getString("notificationId"));
                action.actionIntent.send(this,0,fill);
                reply.done(new JSONObject().put("dispatched",true).put("instruction","Réponse transmise à l’app. La livraison au destinataire dépend de l’app."),null); return;
            }
            throw new IllegalStateException("Notification retirée. Relisez la liste.");
        } catch (Exception error) { reply.done(null,error.getMessage() == null ? "Action refusée" : error.getMessage()); }
    }
}
