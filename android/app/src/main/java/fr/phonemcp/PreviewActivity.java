package fr.phonemcp;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.*;
import android.util.Base64;
import android.view.*;
import android.widget.*;
import java.util.UUID;

/** A local image/compositor viewer. No input is forwarded to the controlled app. */
public final class PreviewActivity extends Activity implements SurfaceHolder.Callback {
    @Override protected void attachBaseContext(android.content.Context context){super.attachBaseContext(Locales.wrap(context));}
    private final Handler main=new Handler(Looper.getMainLooper());
    private SurfaceView surface;
    private ImageView image;
    private TextView status;
    private String pkg,viewer,sessionId;
    private boolean resumed,attaching,hardware,fallback;
    private int epoch;
    private boolean frameFailed;
    private boolean pendingVideoPermission;
    private String permissionSession,permissionPackage;
    private final String diagnosticId=UUID.randomUUID().toString();
    private static volatile String diagnosticOwner;
    private static volatile org.json.JSONObject localCaptureState=new org.json.JSONObject();
    static org.json.JSONObject captureState(){return localCaptureState;}
    private void publishCaptureState(){
        if(!resumed){if(diagnosticId.equals(diagnosticOwner)){localCaptureState=new org.json.JSONObject();diagnosticOwner=null;}return;}
        try{
            int flags=getWindow().getAttributes().flags;
            org.json.JSONObject next=new org.json.JSONObject().put("active",true).put("sessionId",sessionId).put("packageName",pkg)
                .put("displayId",getDisplay()==null?-1:getDisplay().getDisplayId()).put("windowSecure",(flags&WindowManager.LayoutParams.FLAG_SECURE)!=0)
                .put("keepsScreenOn",(flags&WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)!=0)
                .put("transport",hardware?"surface":fallback?"frames":"attaching").put("frameFailed",frameFailed)
                .put("frameAvailable",hardware||image!=null&&image.getDrawable()!=null);
            diagnosticOwner=diagnosticId;localCaptureState=next;
        }catch(Exception ignored){}
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        // A preview is capturable when its source permits it. Secure source layers and
        // VirtualEngine.requirePreview still enforce the original application protection.
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        // Only the user setting may keep the physical screen awake.
        if(getDisplay()==null||getDisplay().getDisplayId()!=Display.DEFAULT_DISPLAY){finish();return;}
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.BLACK);root.setFitsSystemWindows(true);
        status=new TextView(this);status.setTextColor(Color.WHITE);status.setPadding(dp(16),dp(12),dp(16),dp(12));root.addView(status);
        FrameLayout viewport=new FrameLayout(this);root.addView(viewport,new LinearLayout.LayoutParams(-1,0,1));
        surface=new SurfaceView(this);surface.setSecure(false);surface.setClickable(false);surface.setFocusable(false);surface.setContentDescription(getString(R.string.preview_description));surface.getHolder().addCallback(this);viewport.addView(surface,new FrameLayout.LayoutParams(-1,-1));
        image=new ImageView(this);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setContentDescription(getString(R.string.preview_description));image.setClickable(false);image.setFocusable(false);image.setVisibility(View.GONE);viewport.addView(image,new FrameLayout.LayoutParams(-1,-1));
        Button capture=new Button(this);capture.setText(R.string.capture_button);capture.setOnClickListener(this::captureMenu);root.addView(capture,new LinearLayout.LayoutParams(-1,-2));
        Button stop=new Button(this);stop.setText(R.string.preview_stop_control);stop.setOnClickListener(view->{stop.setEnabled(false);stopPreview();BridgeService.stopControl(sessionId,pkg,(result,error)->{if(error==null)closePreview();else{stop.setEnabled(true);status.setText(error);}});});root.addView(stop,new LinearLayout.LayoutParams(-1,-2));
        Button take=new Button(this);take.setText(R.string.preview_handoff);take.setOnClickListener(view->{take.setEnabled(false);stop.setEnabled(false);stopPreview();BridgeService.handoffControl(sessionId,pkg,(result,error)->{if(error==null)closePreview();else{status.setText(error);take.setEnabled(false);stop.setEnabled(true);}});});root.addView(take,new LinearLayout.LayoutParams(-1,-2));
        Button close=new Button(this);close.setText(R.string.preview_close);close.setOnClickListener(view->hidePreview());root.addView(close,new LinearLayout.LayoutParams(-1,-2));
        setContentView(root);select(getIntent());
        if(Build.VERSION.SDK_INT>=33)getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,this::hidePreview);
    }
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private void select(Intent intent){stopPreview();pkg=intent.getStringExtra("packageName");sessionId=intent.getStringExtra("sessionId");if(sessionId==null)sessionId=BridgeService.previewSession(pkg);if(!BridgeService.canPreview(sessionId,pkg)){closePreview();return;}setActiveStatus();}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);select(intent);if(resumed)startSurface();}
    @Override protected void onResume(){super.onResume();getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);resumed=true;publishCaptureState();if(surface!=null)surface.post(this::startSurface);}
    @Override protected void onPause(){resumed=false;publishCaptureState();stopPreview();super.onPause();}
    @Override protected void onDestroy(){resumed=false;publishCaptureState();stopPreview();super.onDestroy();}
    @Override public void surfaceCreated(SurfaceHolder holder){startSurface();}
    @Override public void surfaceChanged(SurfaceHolder holder,int format,int width,int height){stopPreview();if(resumed)startSurface();}
    @Override public void surfaceDestroyed(SurfaceHolder holder){stopPreview();}
    private void stopPreview(){epoch++;main.removeCallbacks(refresh);String old=viewer;viewer=null;attaching=false;hardware=false;fallback=false;if(old!=null)BridgeService.detachPreview(old);if(image!=null){image.setImageDrawable(null);image.setVisibility(View.GONE);}}
    private void startSurface(){
        if(!resumed||attaching||hardware||fallback||surface.getWidth()<1||surface.getHeight()<1||!surface.getHolder().getSurface().isValid())return;
        if(!BridgeService.canPreview(sessionId,pkg)){closePreview();return;}
        final int expected=epoch;final String id=UUID.randomUUID().toString();viewer=id;attaching=true;
        BridgeService.previewSurface(sessionId,pkg,id,surface.getSurfaceControl(),surface.getWidth(),surface.getHeight(),(result,error)->{
            if(!resumed||expected!=epoch){BridgeService.detachPreview(id);return;}
            attaching=false;hardware=error==null&&result!=null&&result.optBoolean("active");fallback=!hardware;
            if(fallback){image.setVisibility(View.VISIBLE);BridgeService.detachPreview(id);viewer=null;}
            if(hardware)setActiveStatus();else status.setText(R.string.preview_unavailable);publishCaptureState();main.post(refresh);
        });
    }
    private final Runnable refresh=new Runnable(){@Override public void run(){
        if(!resumed||isFinishing())return;
        if(!BridgeService.canPreview(sessionId,pkg)){stopPreview();closePreview();return;}
        final int expected=epoch;
        if(hardware){
            BridgeService.previewSurface(sessionId,pkg,viewer,null,0,0,(result,error)->{
                if(!resumed||expected!=epoch)return;
                if(error!=null||result==null||!result.optBoolean("active")){String old=viewer;viewer=null;hardware=false;fallback=true;BridgeService.detachPreview(old);image.setVisibility(View.VISIBLE);main.post(this);return;}
                if(error==null&&result!=null&&result.optBoolean("visible"))setActiveStatus();else status.setText(R.string.preview_unavailable);
                publishCaptureState();main.postDelayed(this,400);
            });
        }else if(fallback){
            BridgeService.previewFrame(sessionId,pkg,(frame,error)->{
                if(!resumed||expected!=epoch)return;
                frameFailed=error!=null||frame==null;
                if(frameFailed){image.setImageDrawable(null);status.setText(R.string.preview_unavailable);}
                else try{byte[] jpeg=Base64.decode(frame.getString("data"),Base64.NO_WRAP);Bitmap next=BitmapFactory.decodeByteArray(jpeg,0,jpeg.length);if(next==null)throw new IllegalStateException();image.setImageBitmap(next);setActiveStatus();}
                catch(Exception ignored){frameFailed=true;image.setImageDrawable(null);status.setText(R.string.preview_unavailable);}
                publishCaptureState();main.postDelayed(this,40);
            });
        }
    }};
    private void setActiveStatus(){String label=pkg;try{label=getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg,0)).toString();}catch(Exception ignored){}String agent="";org.json.JSONArray sessions=BridgeService.backgroundState().optJSONArray("sessions");if(sessions!=null)for(int i=0;i<sessions.length();i++){org.json.JSONObject entry=sessions.optJSONObject(i);if(entry!=null&&sessionId!=null&&sessionId.equals(entry.optString("sessionId")))agent=entry.optString("label");}RecentsCards.describe(this,agent.isEmpty()?label:agent);String recording=BridgeService.recordingPreview(sessionId)?" · "+getString(R.string.capture_recording,BridgeService.recordingElapsed(sessionId)/60000,(BridgeService.recordingElapsed(sessionId)/1000)%60):"";status.setText(getString(R.string.preview_active)+" · "+label+(agent.isEmpty()||agent.equals(label)?"":" · "+agent)+recording);}
    private void captureMenu(View anchor){
        if(!BridgeService.canPreview(sessionId,pkg)){closePreview();return;}
        PopupMenu menu=new PopupMenu(this,anchor);
        menu.getMenu().add(0,1,0,R.string.capture_image).setEnabled(!BridgeService.screenshotPending(sessionId));
        menu.getMenu().add(0,2,1,BridgeService.recordingPreview(sessionId)?R.string.capture_video_stop:R.string.capture_video_start);
        menu.setOnMenuItemClickListener(item->{
            if(item.getItemId()==2&&!BridgeService.recordingPreview(sessionId)&&Build.VERSION.SDK_INT>=33&&checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED){pendingVideoPermission=true;permissionSession=sessionId;permissionPackage=pkg;requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},1600);return true;}
            BridgeService.capturePreview(sessionId,pkg,item.getItemId()==2);return true;
        });menu.show();
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){
        super.onRequestPermissionsResult(request,permissions,grants);
        if(request==1600&&pendingVideoPermission){pendingVideoPermission=false;final String id=permissionSession,app=permissionPackage;permissionSession=permissionPackage=null;if(grants.length>0&&grants[0]==android.content.pm.PackageManager.PERMISSION_GRANTED)main.post(()->{if(resumed&&java.util.Objects.equals(id,sessionId)&&java.util.Objects.equals(app,pkg)&&BridgeService.canPreview(id,app))BridgeService.capturePreview(id,app,true);});}
    }
    private void hidePreview(){stopPreview();finish();}
    private void closePreview(){stopPreview();finishAndRemoveTask();}
    @Override @SuppressWarnings("deprecation") public void onBackPressed(){hidePreview();}
}
