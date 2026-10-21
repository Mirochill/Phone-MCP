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
    private final Handler main=new Handler(Looper.getMainLooper());
    private SurfaceView surface;
    private ImageView image;
    private TextView status;
    private String pkg,viewer;
    private boolean resumed,attaching,hardware,fallback;
    private int epoch;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE|WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if(getDisplay()==null||getDisplay().getDisplayId()!=Display.DEFAULT_DISPLAY){finish();return;}
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.BLACK);root.setFitsSystemWindows(true);
        status=new TextView(this);status.setTextColor(Color.WHITE);status.setPadding(dp(16),dp(12),dp(16),dp(12));root.addView(status);
        FrameLayout viewport=new FrameLayout(this);root.addView(viewport,new LinearLayout.LayoutParams(-1,0,1));
        surface=new SurfaceView(this);surface.setSecure(true);surface.setClickable(false);surface.setFocusable(false);surface.setContentDescription(getString(R.string.preview_description));surface.getHolder().addCallback(this);viewport.addView(surface,new FrameLayout.LayoutParams(-1,-1));
        image=new ImageView(this);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setContentDescription(getString(R.string.preview_description));image.setClickable(false);image.setFocusable(false);image.setVisibility(View.GONE);viewport.addView(image,new FrameLayout.LayoutParams(-1,-1));
        Button close=new Button(this);close.setText(R.string.preview_close);close.setOnClickListener(view->closePreview());root.addView(close,new LinearLayout.LayoutParams(-1,-2));
        setContentView(root);select(getIntent());
        if(Build.VERSION.SDK_INT>=33)getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,this::closePreview);
    }
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private void select(Intent intent){stopPreview();pkg=intent.getStringExtra("packageName");status.setText(BridgeService.canPreview(pkg)?R.string.preview_active:R.string.preview_stopped);}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);select(intent);if(resumed)startSurface();}
    @Override protected void onResume(){super.onResume();resumed=true;if(surface!=null)surface.post(this::startSurface);}
    @Override protected void onPause(){resumed=false;stopPreview();super.onPause();}
    @Override protected void onDestroy(){resumed=false;stopPreview();super.onDestroy();}
    @Override public void surfaceCreated(SurfaceHolder holder){startSurface();}
    @Override public void surfaceChanged(SurfaceHolder holder,int format,int width,int height){stopPreview();if(resumed)startSurface();}
    @Override public void surfaceDestroyed(SurfaceHolder holder){stopPreview();}
    private void stopPreview(){epoch++;main.removeCallbacks(refresh);String old=viewer;viewer=null;attaching=false;hardware=false;fallback=false;if(old!=null)BridgeService.detachPreview(old);if(image!=null){image.setImageDrawable(null);image.setVisibility(View.GONE);}}
    private void startSurface(){
        if(!resumed||attaching||hardware||fallback||surface.getWidth()<1||surface.getHeight()<1||!surface.getHolder().getSurface().isValid())return;
        if(!BridgeService.canPreview(pkg)){status.setText(R.string.preview_stopped);return;}
        final int expected=epoch;final String id=UUID.randomUUID().toString();viewer=id;attaching=true;
        BridgeService.previewSurface(pkg,id,surface.getSurfaceControl(),surface.getWidth(),surface.getHeight(),(result,error)->{
            if(!resumed||expected!=epoch){BridgeService.detachPreview(id);return;}
            attaching=false;hardware=error==null&&result!=null&&result.optBoolean("active");fallback=!hardware;
            if(fallback){image.setVisibility(View.VISIBLE);BridgeService.detachPreview(id);viewer=null;}
            status.setText(hardware?R.string.preview_active:R.string.preview_unavailable);main.post(refresh);
        });
    }
    private final Runnable refresh=new Runnable(){@Override public void run(){
        if(!resumed||isFinishing())return;
        if(!BridgeService.canPreview(pkg)){stopPreview();status.setText(R.string.preview_stopped);return;}
        final int expected=epoch;
        if(hardware){
            BridgeService.previewSurface(pkg,viewer,null,0,0,(result,error)->{
                if(!resumed||expected!=epoch)return;
                status.setText(error==null&&result!=null&&result.optBoolean("visible")?R.string.preview_active:R.string.preview_unavailable);
                main.postDelayed(this,400);
            });
        }else if(fallback){
            BridgeService.previewFrame(pkg,(frame,error)->{
                if(!resumed||expected!=epoch)return;
                if(error!=null||frame==null){image.setImageDrawable(null);status.setText(R.string.preview_unavailable);}
                else try{byte[] jpeg=Base64.decode(frame.getString("data"),Base64.NO_WRAP);Bitmap next=BitmapFactory.decodeByteArray(jpeg,0,jpeg.length);if(next==null)throw new IllegalStateException();image.setImageBitmap(next);status.setText(R.string.preview_active);}
                catch(Exception ignored){image.setImageDrawable(null);status.setText(R.string.preview_unavailable);}
                main.postDelayed(this,40);
            });
        }
    }};
    private void closePreview(){startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));finishAndRemoveTask();}
    @Override @SuppressWarnings("deprecation") public void onBackPressed(){closePreview();}
}
