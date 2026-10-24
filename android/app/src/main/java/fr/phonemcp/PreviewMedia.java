package fr.phonemcp;

import android.app.*;
import android.content.*;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.view.Surface;
import android.widget.Toast;
import org.json.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/** Local user-triggered media only. Each encoder is bound to one existing authorized AI session. */
final class PreviewMedia {
    private final Context context;
    private final BackgroundClient client;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Map<String,Recording> recordings=new ConcurrentHashMap<>();
    private final Set<String> images=ConcurrentHashMap.newKeySet();
    private final Map<String,PendingMedia> pendingImages=new ConcurrentHashMap<>();
    private final List<JSONObject> saved=new ArrayList<>();
    private volatile boolean closed;
    PreviewMedia(Context context,BackgroundClient client){this.context=Locales.wrap(context.getApplicationContext());this.client=client;}
    boolean recording(String id){return recordings.containsKey(id);}
    long elapsed(String id){Recording r=recordings.get(id);return r==null?0:SystemClock.elapsedRealtime()-r.started;}
    boolean imagePending(String id){return images.contains(id);}
    synchronized JSONObject state()throws Exception{
        JSONArray active=new JSONArray();for(Recording r:recordings.values())active.put(new JSONObject().put("sessionId",r.session).put("sourceDisplayId",r.sourceDisplay).put("state",r.stopping?"saving":"recording").put("elapsedMs",elapsed(r.session)).put("samples",r.samples));
        JSONArray history=new JSONArray();for(JSONObject value:saved)history.put(value);
        return new JSONObject().put("recordings",active).put("pendingImages",images.size()).put("saved",history);
    }
    private synchronized void saved(JSONObject value){saved.add(value);while(saved.size()>8)saved.remove(0);}
    void screenshot(String id,String pkg){
        if(closed||!BridgeService.canPreview(id,pkg)||!images.add(id))return;
        final PendingMedia file;
        try{file=new PendingMedia(context,false);}catch(Exception error){images.remove(id);notice(R.string.capture_failed);return;}
        pendingImages.put(id,file);
        try{client.savePreviewImage(id,pkg,file.output,(result,error)->{
            try{if(closed||error!=null||result==null||!BridgeService.canPreview(id,pkg))throw new IllegalStateException();
                Uri uri=file.publish();saved(new JSONObject().put("kind","image").put("uri",uri.toString()).put("width",result.getInt("width")).put("height",result.getInt("height")).put("sourceDisplayId",result.getInt("sourceDisplayId")).put("bytes",file.bytes()));notice(R.string.capture_image_saved);
            }catch(Exception failure){notice(R.string.capture_failed);}finally{file.close();images.remove(id);pendingImages.remove(id,file);}
        });}catch(RuntimeException stopped){file.close();images.remove(id);pendingImages.remove(id,file);notice(R.string.capture_failed);}
    }
    void start(String id,String pkg){
        if(closed||!BridgeService.canPreview(id,pkg)||recordings.containsKey(id))return;
        if(Build.VERSION.SDK_INT>=33&&context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED){notice(R.string.capture_notifications_required);return;}
        Recording r=new Recording(id,pkg);if(recordings.putIfAbsent(id,r)!=null)return;
        notification();new Thread(r,"phone-mcp-video").start();
    }
    void stop(String id){Recording r=recordings.get(id);if(r!=null)r.stopping=true;}
    void stopAll(){for(Recording r:recordings.values())r.stopping=true;}
    void close(){closed=true;stopAll();for(PendingMedia file:pendingImages.values())file.close();pendingImages.clear();images.clear();getManager().cancel(1600);}
    private void notice(int resource){main.post(()->Toast.makeText(context,resource,Toast.LENGTH_LONG).show());}
    private NotificationManager getManager(){return context.getSystemService(NotificationManager.class);}
    private void notification(){main.post(()->{
        NotificationManager manager=getManager();if(closed||recordings.isEmpty()){manager.cancel(1600);return;}
        manager.createNotificationChannel(new NotificationChannel("captures",context.getString(R.string.capture_notification),NotificationManager.IMPORTANCE_LOW));
        PendingIntent stop=PendingIntent.getService(context,1600,new Intent(context,BridgeService.class).setAction("STOP_CAPTURES"),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n=new Notification.Builder(context,"captures").setSmallIcon(android.R.drawable.presence_video_online).setContentTitle(context.getString(R.string.capture_notification)).setContentText(context.getString(R.string.capture_notification_count,recordings.size())).setOngoing(true).setOnlyAlertOnce(true).addAction(new Notification.Action.Builder(null,context.getString(R.string.capture_stop_all),stop).build()).build();try{manager.notify(1600,n);}catch(SecurityException revoked){stopAll();notice(R.string.capture_notifications_required);}
    });}
    private final class Recording implements Runnable {
        final String session,pkg,token=UUID.randomUUID().toString();
        final long started=SystemClock.elapsedRealtime();
        volatile boolean stopping;
        volatile long samples;
        volatile int sourceDisplay=-1;
        final Runnable heartbeat=new Runnable(){@Override public void run(){
            if(stopping||closed||!BridgeService.canPreview(session,pkg)){stopping=true;return;}
            client.renewRecording(session,pkg,token,(state,error)->{if(error!=null||state==null||!state.optBoolean("active")){stopping=true;return;}if(!stopping)main.postDelayed(this,750);});
        }};
        Recording(String id,String pkg){session=id;this.pkg=pkg;}
        @Override public void run(){
            MediaCodec codec=null;MediaMuxer muxer=null;Surface input=null;PendingMedia file=null;PowerManager.WakeLock wake=null;
            boolean muxerStarted=false,published=false,eosSent=false,finished=false;int track=-1;long stopAt=0,lastWake=0,lastControlCheck=0;String failure="";
            try{
                file=new PendingMedia(context,true);
                MediaFormat format=MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC,VirtualEngine.WIDTH,VirtualEngine.HEIGHT);
                format.setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);format.setInteger(MediaFormat.KEY_BIT_RATE,6000000);format.setInteger(MediaFormat.KEY_FRAME_RATE,30);format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,1);format.setLong(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER,100000);
                codec=MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);codec.configure(format,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);input=codec.createInputSurface();codec.start();
                muxer=new MediaMuxer(file.output.getFileDescriptor(),MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
                wake=context.getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"phonemcp:local-recording");wake.setReferenceCounted(false);wake.acquire(30000);lastWake=SystemClock.elapsedRealtime();
                CountDownLatch attached=new CountDownLatch(1);AtomicReference<String> error=new AtomicReference<>();AtomicReference<JSONObject> state=new AtomicReference<>();
                client.attachRecording(session,pkg,token,input,(value,problem)->{state.set(value);error.set(problem);attached.countDown();});
                if(!attached.await(8,TimeUnit.SECONDS)||error.get()!=null||state.get()==null||!state.get().optBoolean("active"))throw new IllegalStateException(error.get()==null?"Miroir vidéo non confirmé.":error.get());
                sourceDisplay=state.get().getInt("sourceDisplayId");if(sourceDisplay<=0)throw new SecurityException("Capture physique refusée.");
                main.post(heartbeat);MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
                while(!finished){
                    long now=SystemClock.elapsedRealtime();
                    if((stopping||closed)&&!eosSent){client.detachRecording(token);codec.signalEndOfInputStream();eosSent=true;stopAt=now;}
                    if(eosSent&&now-stopAt>5000)throw new IllegalStateException("Finalisation vidéo expirée.");
                    if(now-lastWake>=15000){wake.acquire(30000);lastWake=now;}
                    int index=codec.dequeueOutputBuffer(info,50000);
                    if(index==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){if(muxerStarted)throw new IllegalStateException("Format vidéo modifié.");track=muxer.addTrack(codec.getOutputFormat());muxer.start();muxerStarted=true;}
                    else if(index>=0){try{
                        ByteBuffer buffer=codec.getOutputBuffer(index);
                        if((info.flags&MediaCodec.BUFFER_FLAG_CODEC_CONFIG)!=0)info.size=0;
                        if(info.size>0){if(!muxerStarted||buffer==null)throw new IllegalStateException("Flux vidéo incomplet.");buffer.position(info.offset);buffer.limit(info.offset+info.size);muxer.writeSampleData(track,buffer,info);samples++;}
                        finished=(info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;
                    }finally{codec.releaseOutputBuffer(index,false);}}
                    if(now-lastControlCheck>=250){lastControlCheck=now;if(!BridgeService.canPreview(session,pkg))stopping=true;}
                }
                if(!muxerStarted||samples<1)throw new IllegalStateException("Vidéo vide.");
                muxer.stop();muxer.release();muxer=null;
                JSONObject details=new JSONObject().put("kind","video").put("sourceDisplayId",sourceDisplay).put("samples",samples).put("bytes",file.bytes()).put("audio",false);
                try(MediaMetadataRetriever metadata=new MediaMetadataRetriever()){metadata.setDataSource(file.output.getFileDescriptor());details.put("width",Integer.parseInt(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH))).put("height",Integer.parseInt(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT))).put("durationMs",Long.parseLong(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)));}
                if(details.getInt("width")!=VirtualEngine.WIDTH||details.getInt("height")!=VirtualEngine.HEIGHT)throw new IllegalStateException("Dimensions vidéo incorrectes.");
                Uri uri=file.publish();details.put("uri",uri.toString());saved(details);published=true;notice(R.string.capture_video_saved);
            }catch(Exception error){Throwable deepest=error;while(deepest.getCause()!=null)deepest=deepest.getCause();failure=deepest.getClass().getSimpleName()+": "+String.valueOf(deepest.getMessage());notice(R.string.capture_failed);}
            finally{
                stopping=true;main.removeCallbacks(heartbeat);client.detachRecording(token);
                if(muxer!=null){try{if(muxerStarted)muxer.stop();}catch(Exception ignored){}try{muxer.release();}catch(Exception ignored){}}
                if(codec!=null){try{codec.stop();}catch(Exception ignored){}try{codec.release();}catch(Exception ignored){}}
                if(input!=null)input.release();if(file!=null)file.close();if(wake!=null&&wake.isHeld())wake.release();
                if(!published)try{saved(new JSONObject().put("kind","video_error").put("sessionId",session).put("sourceDisplayId",sourceDisplay).put("reason",failure.substring(0,Math.min(240,failure.length()))));}catch(Exception ignored){}
                recordings.remove(session,this);notification();
            }
        }
    }
}