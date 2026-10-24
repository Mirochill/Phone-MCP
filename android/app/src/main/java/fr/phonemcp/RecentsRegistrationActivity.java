package fr.phonemcp;

import android.app.Activity;
import android.content.Intent;
import android.os.*;
import android.view.*;
import org.json.*;

/** Registers an off-screen retained document; opening it locally routes to the read-only viewer. */
public final class RecentsRegistrationActivity extends Activity {
    private final Handler main=new Handler(Looper.getMainLooper());
    private long until;
    private boolean viewing,drawn;
    @Override protected void attachBaseContext(android.content.Context context){super.attachBaseContext(Locales.wrap(context));}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        // This router hosts only a label, never source pixels. Its own secure flag must
        // not contaminate transitions into the capturable read-only viewer.
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
        until=SystemClock.uptimeMillis()+1200;route();
    }
    @Override protected void onResume(){super.onResume();route();}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);route();}
    private void route(){
        int display=getDisplay()==null?-1:getDisplay().getDisplayId();String id=getIntent().getStringExtra("sessionId");
        if(display<0||id==null){finish();return;}
        JSONArray sessions=BridgeService.backgroundState().optJSONArray("sessions");
        if(sessions!=null)for(int i=0;i<sessions.length();i++){
            JSONObject entry=sessions.optJSONObject(i);if(entry==null||!id.equals(entry.optString("sessionId")))continue;
            String pkg=entry.optString("packageName");
            if(!BridgeService.canPreview(id,pkg)||display>0&&display!=entry.optInt("displayId",-1)){finishAndRemoveTask();return;}
            RecentsCards.describe(this,entry.optString("label"));
            if(display==Display.DEFAULT_DISPLAY){if(!viewing){viewing=true;startActivity(new Intent(this,PreviewActivity.class).putExtra("sessionId",id).putExtra("packageName",pkg));}finish();return;}
            // Keep one stopped local activity behind the agent app. Samsung removes never-visible
            // empty retained documents; this card owns no capture loop, timer, input or CPU lock.
            if(!drawn){drawn=true;android.widget.TextView title=new android.widget.TextView(this);title.setText(getString(R.string.preview_recent_title,entry.optString("label")));title.setTextColor(0xFFF3F4F7);title.setBackgroundColor(0xFF101216);title.setGravity(Gravity.CENTER);setContentView(title);}
            return;
        }
        if(display>0&&SystemClock.uptimeMillis()<until&&BridgeService.parallelEnabled())main.postDelayed(this::route,30);else finishAndRemoveTask();
    }
    @Override protected void onDestroy(){main.removeCallbacksAndMessages(null);super.onDestroy();}
}
