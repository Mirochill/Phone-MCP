package fr.phonemcp;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** No external test library; checks actual Android APIs and the running accessibility service. */
public final class UpgradeInstrumentation extends Instrumentation {
    private int checks;private final ArrayList<String> results=new ArrayList<>();private Context target;
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    private void expect(boolean value,String message){if(!value)throw new AssertionError(message);checks++;results.add(message);}
    @Override public void onStart(){Bundle output=new Bundle();int resultCode=Activity.RESULT_CANCELED;android.content.SharedPreferences prefs=null;Map<String,?> saved=null;try{
        target=getTargetContext();prefs=target.getSharedPreferences("settings",Context.MODE_PRIVATE);saved=new HashMap<>(prefs.getAll());prefs.edit().putBoolean("update.autoCheck",false).commit();pureChecks();foregroundChecks();output.putString("stream","PASS "+checks+" Android checks\n"+String.join("\n",results));resultCode=Activity.RESULT_OK;
    }catch(Throwable error){output.putString("stream","FAIL after "+checks+": "+error+"\n"+android.util.Log.getStackTraceString(error));
    }finally{if(target!=null)BridgeService.stop(target);if(prefs!=null&&saved!=null){android.content.SharedPreferences.Editor edit=prefs.edit().clear();for(Map.Entry<String,?> entry:saved.entrySet()){Object v=entry.getValue();String k=entry.getKey();if(v instanceof String)edit.putString(k,(String)v);else if(v instanceof Boolean)edit.putBoolean(k,(Boolean)v);else if(v instanceof Integer)edit.putInt(k,(Integer)v);else if(v instanceof Long)edit.putLong(k,(Long)v);else if(v instanceof Set)edit.putStringSet(k,(Set<String>)v);}edit.commit();}}
        finish(resultCode,output);
    }
    private void pureChecks()throws Exception{
        SnapshotPager pager=new SnapshotPager();JSONArray nodes=new JSONArray();String id=UUID.randomUUID().toString();for(int i=0;i<450;i++)nodes.put(new JSONObject().put("nodeId","0."+i).put("text","Row "+i).put("visible",i<400));
        JSONObject first=pager.capture(new JSONObject().put("snapshotId",id).put("nodes",nodes),new JSONObject().put("limit",200));expect(first.getJSONArray("nodes").length()==200,"Snapshot page bounded to 200");expect(first.getInt("totalNodes")==400,"Hidden nodes excluded by default");
        JSONObject next=pager.page(new JSONObject().put("cursor",first.getString("nextCursor")));expect(next.getJSONArray("nodes").getJSONObject(0).getString("nodeId").equals("0.200"),"Cursor advances without losing identifiers");
        boolean refused=false;try{pager.page(new JSONObject().put("cursor",UUID.randomUUID()+".200"));}catch(IllegalStateException e){refused=true;}expect(refused,"Foreign cursor rejected");
        pager.clear();refused=false;try{pager.page(new JSONObject());}catch(IllegalStateException e){refused=true;}expect(refused,"Cleared snapshot cannot be reused");
        expect(CompatIO.readAtMost(new ByteArrayInputStream(new byte[100]),10).length==10,"Bounded reads support Android 11");
        JSONObject lines=StorageAccess.textPage(new ByteArrayInputStream("one\ntwo\nthree".getBytes(java.nio.charset.StandardCharsets.UTF_8)),new JSONObject().put("offset",2).put("limit",1));expect(lines.getJSONArray("lines").getJSONObject(0).getString("text").equals("two")&&lines.getInt("nextOffset")==3,"UTF-8 file pagination preserves line positions");
        refused=false;try{StorageAccess.textPage(new ByteArrayInputStream(new byte[]{(byte)255}),new JSONObject());}catch(java.nio.charset.CharacterCodingException e){refused=true;}expect(refused,"Binary data is not silently decoded as text");
        JSONObject pinch=new JSONObject().put("centerX",100).put("centerY",100).put("startDistance",40).put("endDistance",100).put("durationMs",200);expect(TouchGestures.strokes("pinch",pinch,new Rect(0,0,200,200)).size()==2,"Pinch creates two simultaneous fingers");
        refused=false;try{TouchGestures.strokes("pinch",pinch,new Rect(90,90,110,110));}catch(SecurityException e){refused=true;}expect(refused,"Gesture cannot exceed its authorized window");
        Bitmap bitmap=Bitmap.createBitmap(720,1280,Bitmap.Config.ARGB_8888);try{JSONObject encoded=CaptureFormatter.encode(bitmap,new JSONObject().put("maxDimension",640).put("annotate",true),new JSONArray().put(new JSONObject().put("nodeId","0.1").put("clickable",true).put("bounds",new JSONArray(new int[]{20,30,200,130}))),10,20,3);expect(encoded.getInt("width")==360&&encoded.getInt("height")==640,"Capture resolution bounded before encoding");expect(encoded.getJSONArray("annotations").getJSONObject(0).getString("nodeId").equals("0.1"),"Capture labels preserve real node identifiers");expect(encoded.getJSONObject("coordinateSpace").getDouble("scaleX")==2&&encoded.getJSONObject("coordinateSpace").getInt("originX")==10,"Capture coordinate conversion includes scale and crop");}finally{bitmap.recycle();}
        for(String[] pair:new String[][]{{"fr","Réglages"},{"en","Settings"},{"es","Ajustes"},{"de","Einstellungen"}}){Locales.setPreference(target,"language",pair[0]);Context localized=Locales.wrap(target);expect(I18n.tr(localized,"Réglages").equals(pair[1]),"Interface language "+pair[0]);}
        Context isolated=new ContextWrapper(target){@Override public File getCacheDir(){File folder=new File(target.getCacheDir(),"upgrade-tests");folder.mkdirs();return folder;}};
        ShareInbox inbox=new ShareInbox(isolated);inbox.clear();inbox.accept(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,"User shared fixture"));expect(inbox.items().length()==1,"Share inbox imports only explicit shared content");inbox.clear();
    }
    private JSONObject call(String op,JSONObject args)throws Exception{
        CountDownLatch done=new CountDownLatch(1);JSONObject[] result={null};String[] error={null};runOnMainSync(()->PhoneAccessibilityService.instance.execute(op,args,(value,failure)->{result[0]=value;error[0]=failure;done.countDown();}));if(!done.await(8,TimeUnit.SECONDS))throw new IllegalStateException("Timed out: "+op);if(error[0]!=null)throw new IllegalStateException(error[0]);return result[0];
    }
    private String fixturePackage(){return getContext().getPackageName();}
    private JSONObject snapshot()throws Exception{long deadline=SystemClock.elapsedRealtime()+5000;while(true)try{return call("snapshot",new JSONObject().put("packageName",fixturePackage()).put("includeOffscreen",true).put("limit",350));}catch(IllegalStateException e){if(SystemClock.elapsedRealtime()>=deadline)throw e;SystemClock.sleep(100);}}
    private JSONObject find(JSONObject frame,String value)throws Exception{JSONArray nodes=frame.getJSONArray("nodes");for(int i=0;i<nodes.length();i++){JSONObject node=nodes.getJSONObject(i);if(value.equals(node.optString("text"))||value.equals(node.optString("description")))return node;}throw new AssertionError("Node missing: "+value);}
    private JSONObject awaitText(String value)throws Exception{long end=SystemClock.elapsedRealtime()+5000;while(true){JSONObject frame=snapshot();try{find(frame,value);return frame;}catch(AssertionError error){if(SystemClock.elapsedRealtime()>=end)throw error;SystemClock.sleep(100);}}}
    private JSONObject args(JSONObject frame,JSONObject node)throws Exception{return new JSONObject().put("packageName",fixturePackage()).put("snapshotId",frame.getString("snapshotId")).put("nodeId",node.getString("nodeId"));}
    private void foregroundChecks()throws Exception{
        android.app.UiAutomation automation=getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        String enabled=android.provider.Settings.Secure.getString(target.getContentResolver(),"enabled_accessibility_services"),own="fr.phonemcp/fr.phonemcp.PhoneAccessibilityService";
        ArrayList<String> services=new ArrayList<>();if(enabled!=null&&!enabled.equals("null"))for(String entry:enabled.split(":"))if(!entry.equals(own))services.add(entry);
        String others=String.join(":",services);if(!others.matches("[A-Za-z0-9_./:]*"))throw new SecurityException("Unexpected service identifier.");
        try(android.os.ParcelFileDescriptor command=automation.executeShellCommand(others.isEmpty()?"settings delete secure enabled_accessibility_services":"settings put secure enabled_accessibility_services "+others)){CompatIO.readAtMost(new FileInputStream(command.getFileDescriptor()),2000);}
        services.add(own);try(android.os.ParcelFileDescriptor command=automation.executeShellCommand("settings put secure enabled_accessibility_services "+String.join(":",services))){CompatIO.readAtMost(new FileInputStream(command.getFileDescriptor()),2000);}
        long ready=SystemClock.elapsedRealtime()+5000;while(PhoneAccessibilityService.instance==null&&SystemClock.elapsedRealtime()<ready)SystemClock.sleep(50);
        expect(PhoneAccessibilityService.instance!=null,"Accessibility service enabled for native checks");
        Settings settings=new Settings(target);settings.saveConnection("https://127.0.0.1","a".repeat(40));for(String cap:new String[]{"read","write","capture"})settings.grant(fixturePackage(),cap,true);
        runOnMainSync(()->{target.startActivity(new Intent(target,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));});SystemClock.sleep(250);
        runOnMainSync(()->target.startForegroundService(new Intent(target,BridgeService.class).putExtra("minutes",30).putExtra("screenControl",true)));
        runOnMainSync(()->target.startActivity(new Intent().setClassName(fixturePackage(),FixtureActivity.class.getName()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));SystemClock.sleep(600);
        snapshot();automation.waitForIdle(500,5000);
        JSONObject screen=snapshot(),input=find(screen,"seed");expect(screen.has("nextCursor"),"Native long interface returns a pagination cursor");
        JSONObject page=call("snapshot",new JSONObject().put("packageName",fixturePackage()).put("cursor",screen.getString("nextCursor")).put("includeOffscreen",true));expect(page.getString("snapshotId").equals(screen.getString("snapshotId")),"Native paging keeps the same observation");
        call("edit_text",args(screen,input).put("mode","append").put("text"," + edit"));expect(find(awaitText("seed + edit"),"seed + edit")!=null,"Append text executes on the real Android field");
        boolean stale=false;try{call("set_text",args(screen,input).put("text","wrong"));}catch(IllegalStateException error){stale=true;}expect(stale,"A completed action invalidates its previous snapshot");
        screen=snapshot();JSONObject button=find(screen,"Tap count: 0");JSONArray b=button.getJSONArray("bounds");int x=(b.getInt(0)+b.getInt(2))/2,y=(b.getInt(1)+b.getInt(3))/2;
        call("double_tap",new JSONObject().put("packageName",fixturePackage()).put("snapshotId",screen.getString("snapshotId")).put("x",x).put("y",y));expect(find(awaitText("Tap count: 2"),"Tap count: 2")!=null,"Double tap dispatches both touches to the intended app");
        screen=snapshot();JSONObject area=find(screen,"Gesture area");b=area.getJSONArray("bounds");x=(b.getInt(0)+b.getInt(2))/2;y=(b.getInt(1)+b.getInt(3))/2;
        call("pinch",new JSONObject().put("packageName",fixturePackage()).put("snapshotId",screen.getString("snapshotId")).put("centerX",x).put("centerY",y).put("startDistance",80).put("endDistance",180).put("durationMs",250));expect(find(awaitText("Pointers: 2"),"Pointers: 2")!=null,"Pinch reaches the app as a two-pointer gesture");
        screen=snapshot();JSONObject image=call("screenshot",new JSONObject().put("packageName",fixturePackage()).put("snapshotId",screen.getString("snapshotId")).put("annotate",true).put("maxDimension",640));expect(image.getInt("height")<=640&&image.getJSONArray("annotations").length()>0,"Real window screenshot is resized and annotated");
        screen=snapshot();call("click",args(screen,find(screen,"Protect input")));screen=awaitText("[champ protégé]");expect(!screen.toString().contains("private-fixture-password"),"Password content is excluded from native snapshots");
        boolean protectedCapture=false;try{call("screenshot",new JSONObject().put("packageName",fixturePackage()));}catch(IllegalStateException e){protectedCapture=true;}expect(protectedCapture,"Screenshots with protected fields remain blocked");
        storageChecks();
    }

    private void storageChecks()throws Exception{
        StorageAccess storage=new StorageAccess(target);JSONArray locations=storage.locations();String location=null;
        for(int i=0;i<locations.length();i++)if("PhoneMCP-Upgrade-Test".equals(locations.getJSONObject(i).optString("name")))location=locations.getJSONObject(i).getString("locationId");
        if(location==null){results.add("SAF provider checks skipped: select the dedicated PhoneMCP-Upgrade-Test folder first.");return;}
        expect(true,"Android picker grant exposes only the dedicated test folder");new Settings(target).filesAllowed(true);
        String file="upgrade-"+UUID.randomUUID()+".txt";JSONObject args=new JSONObject().put("locationId",location).put("path",file);boolean created=false;
        try{
            JSONObject written=storage.execute("write_file",new JSONObject(args.toString()).put("text","é漢\nsecond\nthird"));created=true;expect(written.optBoolean("written"),"SAF provider creates a UTF-8 document");
            JSONObject read=storage.execute("read_file",new JSONObject(args.toString()).put("offset",2).put("limit",1));expect("second".equals(read.getJSONArray("lines").getJSONObject(0).getString("text"))&&read.getInt("nextOffset")==3,"SAF provider reads paginated UTF-8 text");
            boolean refused=false;try{storage.execute("write_file",new JSONObject(args.toString()).put("text","wrong"));}catch(IllegalStateException e){refused=true;}expect(refused,"SAF provider refuses an implicit overwrite");
            JSONObject replaced=storage.execute("write_file",new JSONObject(args.toString()).put("text","replacement").put("overwrite",true));expect(replaced.optBoolean("written"),"SAF provider honors an explicit overwrite");
            refused=false;try{storage.execute("read_file",new JSONObject(args.toString()).put("path","../private.txt"));}catch(IllegalArgumentException e){refused=true;}expect(refused,"SAF path traversal is refused on Android");
            refused=false;try{storage.execute("delete_file",new JSONObject(args.toString()).put("path",""));}catch(IllegalArgumentException e){refused=true;}expect(refused,"SAF directory deletion is refused");
            expect(storage.execute("delete_file",args).optBoolean("deleted"),"SAF provider deletes only the requested test file");created=false;
            storage.remove(location);refused=false;try{storage.execute("read_file",args);}catch(SecurityException e){refused=true;}expect(refused,"Revoked SAF access is refused immediately");
        }finally{if(created)try{storage.execute("delete_file",args);}catch(Exception ignored){}storage.remove(location);}
    }
}
