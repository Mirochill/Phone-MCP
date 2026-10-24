package fr.phonemcp;

import android.content.*;
import android.os.*;
import android.net.TrafficStats;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** On-demand counters only. No screen contents, arbitrary commands or background polling. */
final class ResourceDiagnostics {
    static JSONObject sample(Context context)throws Exception{
        long started=SystemClock.elapsedRealtime();
        BatteryManager battery=context.getSystemService(BatteryManager.class);PowerManager power=context.getSystemService(PowerManager.class);
        Intent state=context.registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        JSONObject b=new JSONObject();
        if(battery!=null){property(b,"chargeCounterUah",battery.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER));property(b,"currentNowUa",battery.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW));property(b,"currentAverageUa",battery.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE));property(b,"energyCounterNwh",battery.getLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER));}
        if(state!=null)b.put("level",state.getIntExtra(BatteryManager.EXTRA_LEVEL,-1)).put("scale",state.getIntExtra(BatteryManager.EXTRA_SCALE,-1)).put("plugged",state.getIntExtra(BatteryManager.EXTRA_PLUGGED,-1)).put("temperatureTenthsC",state.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,-1)).put("voltageMv",state.getIntExtra(BatteryManager.EXTRA_VOLTAGE,-1));
        if(state==null&&android.os.Process.myUid()==2000)try{String details=command("/system/bin/dumpsys","battery");int plugged=0;for(String line:details.split("\n")){String[] kv=line.trim().split(":",2);if(kv.length!=2)continue;String key=kv[0].trim(),value=kv[1].trim();if("true".equals(value)){if(key.equals("AC powered"))plugged|=1;if(key.equals("USB powered"))plugged|=2;if(key.equals("Wireless powered"))plugged|=4;}if(key.equals("level"))b.put("level",Integer.parseInt(value));if(key.equals("voltage"))b.put("voltageMv",Integer.parseInt(value));if(key.equals("temperature"))b.put("temperatureTenthsC",Integer.parseInt(value));}b.put("plugged",plugged);}catch(Exception ignored){}
        JSONObject out=new JSONObject().put("appVersion",BuildConfig.VERSION_NAME).put("wallTimeMs",System.currentTimeMillis()).put("elapsedRealtimeMs",started).put("uptimeMs",SystemClock.uptimeMillis()).put("pid",android.os.Process.myPid()).put("uid",android.os.Process.myUid()).put("processCpuMs",android.os.Process.getElapsedCpuTime()).put("pssKb",Debug.getPss()).put("nativeHeapBytes",Debug.getNativeHeapAllocatedSize()).put("javaHeapUsedBytes",Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory()).put("battery",b);
        if(power!=null)out.put("interactive",power.isInteractive()).put("deviceIdle",power.isDeviceIdleMode()).put("batteryOptimizationExempt",power.isIgnoringBatteryOptimizations("fr.phonemcp"));
        try{property(out,"uidRxBytes",TrafficStats.getUidRxBytes(android.os.Process.myUid()));property(out,"uidTxBytes",TrafficStats.getUidTxBytes(android.os.Process.myUid()));}catch(RuntimeException unsupported){out.put("trafficCountersAvailable",false).put("uidRxBytes",JSONObject.NULL).put("uidTxBytes",JSONObject.NULL);}
        out.put("measurementMs",SystemClock.elapsedRealtime()-started);return out;
    }
    private static void property(JSONObject target,String name,long value)throws Exception{target.put(name,value==Long.MIN_VALUE||value==Integer.MIN_VALUE||value==-1?JSONObject.NULL:value);}
    static JSONObject shellSample(Context context)throws Exception{
        JSONObject result=sample(context);JSONArray locks=new JSONArray();String power=command("/system/bin/dumpsys","power");
        for(String line:power.split("\n"))if(line.toLowerCase(Locale.ROOT).contains("phonemcp")&&line.trim().matches("^(PARTIAL|SCREEN_DIM|SCREEN_BRIGHT|FULL|DOZE|DRAW)_WAKE_LOCK.*"))locks.put(line.trim().substring(0,Math.min(500,line.trim().length())));
        result.put("phoneMcpWakeLocks",locks);
        JSONArray profiles=new JSONArray();for(String pkg:new String[]{"com.discord","com.sec.android.app.popupcalculator","com.twitter.android","com.instagram.android"})try{
            Intent intent=context.getPackageManager().getLaunchIntentForPackage(pkg);android.content.pm.ActivityInfo info=intent==null?null:context.getPackageManager().getActivityInfo(intent.getComponent(),0);
            if(info!=null)profiles.put(new JSONObject().put("packageName",pkg).put("launchMode",info.launchMode).put("taskAffinity",info.taskAffinity).put("activity",info.name));
        }catch(Exception ignored){}result.put("launchProfiles",profiles);return result;
    }
    static String command(String...args)throws Exception{
        java.lang.Process p=new ProcessBuilder(args).redirectErrorStream(true).start();
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();Thread reader=new Thread(()->{try(InputStream in=p.getInputStream()){byte[] chunk=new byte[4096];int n;while((n=in.read(chunk))!=-1){if(bytes.size()+n>512000)break;bytes.write(chunk,0,n);}}catch(IOException ignored){}},"phone-diagnostic-read");reader.start();
        if(!p.waitFor(2500,TimeUnit.MILLISECONDS)){p.destroyForcibly();reader.join(500);throw new IOException("Diagnostic timed out.");}reader.join(500);return bytes.toString(StandardCharsets.UTF_8.name());
    }
    static JSONObject probe(Context context,int ownerUid,JSONObject args)throws Exception{
        String id=FilePolicy.id(args.getString("probeId"));File root=new File("/data/local/tmp/phone-mcp-"+ownerUid),file=new File(root,"probe-"+id+".json");
        if(!root.isDirectory()||java.nio.file.Files.isSymbolicLink(root.toPath()))throw new SecurityException("Diagnostic folder unavailable.");
        if("result".equals(args.optString("action")))return file.isFile()?new JSONObject(new String(java.nio.file.Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8)):new JSONObject().put("state","not_found");
        int seconds=args.getInt("seconds");if(seconds<30||seconds>900)throw new IllegalArgumentException("Use 30 to 900 seconds.");
        if(file.isFile())return new JSONObject(new String(java.nio.file.Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8));
        File[] old=root.listFiles((dir,name)->name.startsWith("probe-")&&name.endsWith(".json"));if(old!=null&&old.length>=32)throw new IllegalStateException("Diagnostic history is full.");
        ProcessBuilder builder=new ProcessBuilder("/system/bin/app_process","/system/bin","fr.phonemcp.ResourceProbe",file.getPath(),Integer.toString(seconds),Integer.toString(ownerUid),Integer.toString(args.optInt("startDelaySeconds",0)));
        builder.environment().put("CLASSPATH",context.getApplicationInfo().sourceDir);builder.redirectErrorStream(true);builder.redirectOutput(new File(root,"probe-"+id+".log"));
        builder.start();return new JSONObject().put("state","starting").put("probeId",id).put("seconds",seconds);
    }
}
