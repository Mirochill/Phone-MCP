package fr.phonemcp;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;
import android.view.WindowManager;

/** Neutral singleTask self-check: never launches another app, has no input or physical UI. */
public final class AppLaunchIsolationActivity extends Activity {
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Runnable expire=this::finish;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE
                |WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        setTurnScreenOn(false);setShowWhenLocked(false);
        int display=getDisplay()==null?-1:getDisplay().getDisplayId();
        String nonce=getIntent().getStringExtra(AppLaunchIsolationPolicy.NONCE_EXTRA);
        if(display<=Display.DEFAULT_DISPLAY||!AppLaunchIsolationPolicy.canonicalNonce(nonce)||!isTaskRoot()){
            finish();return;
        }
        main.postDelayed(expire,10000);
    }
    @Override protected void onDestroy(){main.removeCallbacksAndMessages(null);super.onDestroy();}
}
