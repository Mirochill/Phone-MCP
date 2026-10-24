package fr.phonemcp;

import android.content.*;
import android.os.*;
import okhttp3.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Updates reuse the paired HTTPS origin and the existing verified Shizuku file-job pipeline. */
final class UpdateManager {
    interface Result{void done(JSONObject value,String error);}
    static void check(Context context,Result callback){
        Context app=context.getApplicationContext();Handler main=new Handler(Looper.getMainLooper());
        new Thread(()->{OkHttpClient client=new OkHttpClient.Builder().callTimeout(15,TimeUnit.SECONDS).followRedirects(false).build();try{
            Settings settings=new Settings(app);if(settings.endpoint().isEmpty())throw new IllegalStateException(context.getString(R.string.update_pair_first));
            try(Response response=client.newCall(new Request.Builder().url(settings.endpoint()+"/updates/latest").header("Authorization","Bearer "+settings.token()).build()).execute()){
                if(!response.isSuccessful()||response.body()==null)throw new IllegalStateException(context.getString(R.string.update_check_failed));
                String body=response.body().string();if(body.length()>12000)throw new IllegalStateException("Invalid update manifest.");JSONObject value=new JSONObject(body);
                if(!"fr.phonemcp".equals(value.getString("packageName"))||value.getInt("versionCode")<1||!value.getString("sha256").matches("[a-fA-F0-9]{64}"))throw new IllegalStateException("Invalid update manifest.");
                android.net.Uri link=android.net.Uri.parse(value.getString("url")),origin=android.net.Uri.parse(settings.endpoint());
                if(!"https".equals(link.getScheme())||!origin.getAuthority().equals(link.getAuthority())||!link.getPath().startsWith("/updates/download/"))throw new SecurityException("Update must come from the paired server.");
                value.put("available",value.getInt("versionCode")>BuildConfig.VERSION_CODE);app.getSharedPreferences("settings",Context.MODE_PRIVATE).edit().putLong("update.checked",System.currentTimeMillis()).apply();main.post(()->callback.done(value,null));
            }
        }catch(Exception error){main.post(()->callback.done(null,error.getMessage()));}finally{client.dispatcher().executorService().shutdown();client.connectionPool().evictAll();}},"phone-update-check").start();
    }
    static void install(Context context,JSONObject manifest,Result callback){
        try{
            if(!manifest.optBoolean("available")||manifest.getInt("versionCode")<=BuildConfig.VERSION_CODE)throw new IllegalArgumentException(context.getString(R.string.update_current));
            if(!BridgeService.active()||!BackgroundClient.permitted())throw new IllegalStateException(context.getString(R.string.update_start_session));
            String request=UUID.randomUUID().toString();JSONObject args=new JSONObject().put("requestId",request).put("url",manifest.getString("url")).put("fileName","Phone-MCP-"+manifest.getString("versionName")+".apk").put("sha256",manifest.getString("sha256"));
            BridgeService.files("download_file",args,(job,error)->{if(error!=null){callback.done(null,error);return;}follow(context,request,manifest,callback,SystemClock.elapsedRealtime()+600000);});
        }catch(Exception error){callback.done(null,error.getMessage());}
    }
    private static void follow(Context context,String id,JSONObject manifest,Result callback,long deadline){
        if(SystemClock.elapsedRealtime()>deadline){callback.done(null,context.getString(R.string.update_check_failed));return;}
        try{BridgeService.files("file_job",new JSONObject().put("jobId",id).put("action","status"),(job,error)->{
            if(error!=null){callback.done(null,error);return;}String state=job.optString("state");
            if("completed".equals(state)){try{BridgeService.files("install_apk",new JSONObject().put("requestId",UUID.randomUUID().toString()).put("fileId",id).put("sha256",manifest.getString("sha256")).put("expectedPackage","fr.phonemcp"),callback::done);}catch(Exception e){callback.done(null,e.getMessage());}return;}
            if(Arrays.asList("failed","interrupted","cancelled").contains(state)){callback.done(null,job.optString("error",state));return;}
            new Handler(Looper.getMainLooper()).postDelayed(()->follow(context,id,manifest,callback,deadline),800);
        });}catch(Exception e){callback.done(null,e.getMessage());}
    }
}
