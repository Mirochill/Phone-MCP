package fr.phonemcp;

import android.content.Context;
import android.os.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Two endpoint samples, no wakelock. Can finish even when the Phone MCP service stops. */
public final class ResourceProbe {
    public static void main(String[] args)throws Exception{
        if(android.os.Process.myUid()!=2000||args.length!=4)throw new SecurityException("Shell diagnostic only.");
        int delay=Integer.parseInt(args[3]);if(delay<0||delay>60)throw new SecurityException("Invalid diagnostic delay.");int seconds=Integer.parseInt(args[1]),uid=Integer.parseInt(args[2]);File file=new File(args[0]);
        if(seconds<30||seconds>900||!file.getCanonicalPath().matches("/data/local/tmp/phone-mcp-"+uid+"/probe-[0-9a-f-]{36}\\.json"))throw new SecurityException("Invalid diagnostic destination.");
        if(Looper.myLooper()==null)Looper.prepare();Class<?> type=Class.forName("android.app.ActivityThread");Object thread=type.getMethod("systemMain").invoke(null);Context system=(Context)type.getMethod("getSystemContext").invoke(thread);Context context=Build.VERSION.SDK_INT>=31?shellContext(system):system;
        JSONObject result=new JSONObject().put("state","running").put("version",BuildConfig.VERSION_NAME).put("requestedSeconds",seconds).put("observerPid",android.os.Process.myPid()).put("observerUsesWakeLock",false);
        try{if(delay>0){result.put("state","settling").put("startDelaySeconds",delay);save(file,result);waitElapsed(delay*1000L);}result.put("state","running");result.put("start",endpoint(context));save(file,result);waitElapsed(seconds*1000L);result.put("end",endpoint(context)).put("state","completed");}
        catch(Throwable error){result.put("state","failed").put("error",error.toString());}save(file,result);System.exit(0);
    }
    private static void waitElapsed(long duration)throws InterruptedException{long deadline=SystemClock.elapsedRealtime()+duration;while(true){long remaining=deadline-SystemClock.elapsedRealtime();if(remaining<=0)return;Thread.sleep(Math.min(1000,remaining));}}
    private static JSONObject endpoint(Context context)throws Exception{JSONObject value=ResourceDiagnostics.sample(context);try{String services=ResourceDiagnostics.command("/system/bin/dumpsys","activity","services","fr.phonemcp");value.put("bridgeServiceRunning",services.contains("fr.phonemcp/.BridgeService"));}catch(Exception ignored){}return value;}
    @android.annotation.TargetApi(31)
    private static Context shellContext(Context system){return new android.content.ContextWrapper(system){@Override public String getPackageName(){return "com.android.shell";}@Override public String getOpPackageName(){return "com.android.shell";}@Override public android.content.AttributionSource getAttributionSource(){return new android.content.AttributionSource.Builder(2000).setPackageName("com.android.shell").build();}};}
    private static void save(File file,JSONObject value)throws Exception{File temp=new File(file.getPath()+".tmp");try(FileOutputStream out=new FileOutputStream(temp)){out.write(value.toString().getBytes(StandardCharsets.UTF_8));}android.system.Os.chmod(temp.getPath(),0600);java.nio.file.Files.move(temp.toPath(),file.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING,java.nio.file.StandardCopyOption.ATOMIC_MOVE);}
}
