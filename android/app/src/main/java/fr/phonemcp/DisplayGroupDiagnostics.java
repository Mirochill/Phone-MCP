package fr.phonemcp;

import android.os.Binder;
import android.os.IBinder;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Read-only, explicit diagnostic for currently owned secondary displays. No wake authority. */
@android.annotation.SuppressLint({"PrivateApi","DiscouragedPrivateApi"})
final class DisplayGroupDiagnostics {
    private static final AtomicBoolean SAMPLING=new AtomicBoolean();
    private static final int MAX_BYTES=2*1024*1024,TIMEOUT_MS=1200;
    private static final class RuntimeInfo {
        Boolean exists,interactive,ownGroup;Integer group,flags;String errorClass="";
    }
    private static String errorClass(Throwable failure){
        for(int i=0;i<8&&failure instanceof java.lang.reflect.InvocationTargetException&&failure.getCause()!=null;i++)failure=failure.getCause();
        return failure.getClass().getSimpleName();
    }
    private static RuntimeInfo runtime(int id){
        RuntimeInfo result=new RuntimeInfo();long identity=Binder.clearCallingIdentity();
        try{
            Class<?> service=Class.forName("android.os.ServiceManager");
            IBinder displayBinder=(IBinder)service.getMethod("getService",String.class).invoke(null,"display");
            Class<?> displayApi=Class.forName("android.hardware.display.IDisplayManager");
            Object displayManager=Class.forName("android.hardware.display.IDisplayManager$Stub").getMethod("asInterface",IBinder.class).invoke(null,displayBinder);
            Object info=displayApi.getMethod("getDisplayInfo",int.class).invoke(displayManager,id);
            result.exists=info!=null;
            if(info!=null){
                Class<?> infoType=Class.forName("android.view.DisplayInfo");
                result.group=infoType.getField("displayGroupId").getInt(info);result.flags=infoType.getField("flags").getInt(info);
                try{int flag=Class.forName("android.view.Display").getField("FLAG_OWN_DISPLAY_GROUP").getInt(null);result.ownGroup=(result.flags&flag)!=0;}catch(ReflectiveOperationException unavailableFlag){}
            }
        }catch(Exception unavailable){result.errorClass=errorClass(unavailable);}
        try{
            Object powerBinder=Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,"power");
            Object power=Class.forName("android.os.IPowerManager$Stub").getMethod("asInterface",IBinder.class).invoke(null,powerBinder);
            result.interactive=(Boolean)Class.forName("android.os.IPowerManager").getMethod("isDisplayInteractive",int.class).invoke(power,id);
        }catch(Exception unavailable){if(result.errorClass.isEmpty())result.errorClass=errorClass(unavailable);}
        finally{Binder.restoreCallingIdentity(identity);}
        return result;
    }
    private static Object nullable(Object value){return value==null?JSONObject.NULL:value;}
    private static JSONObject capture(BoundedDumpReader.Result capture,String format)throws Exception {
        return new JSONObject().put("complete",capture.completed).put("timedOut",capture.timedOut)
                .put("sizeLimited",capture.sizeLimited).put("lineTooLong",capture.lineTooLong)
                .put("exitCode",capture.exitCode).put("bytesRead",capture.bytesRead)
                .put("errorCode",capture.errorCode).put("format",format);
    }
    private static JSONObject unavailable(String reason)throws Exception {
        return new JSONObject().put("status","unavailable").put("reason",reason).put("mappingVerified",false)
                .put("diagnosticOnly",true).put("snapshotAtomic",false).put("ownedDisplays",new JSONArray());
    }
    static JSONObject sample(Set<Integer> ownedDisplays)throws Exception {
        if(ownedDisplays==null)return unavailable("invalid_owned_displays");
        SortedSet<Integer> ids=new TreeSet<>();
        for(Integer id:ownedDisplays){
            if(id==null||id<=0||ids.size()>=4&&!ids.contains(id))return unavailable("invalid_owned_displays");
            ids.add(id);
        }
        if(ids.isEmpty())return unavailable("no_owned_displays");
        if(android.os.Process.myUid()!=2000)return unavailable("shell_required");
        if(!SAMPLING.compareAndSet(false,true))return unavailable("busy");
        try{
            DisplayGroupDumpParser.DisplayParser displayParser=new DisplayGroupDumpParser.DisplayParser();
            BoundedDumpReader.Result displayCapture=BoundedDumpReader.run("display",MAX_BYTES,TIMEOUT_MS,displayParser);
            DisplayGroupDumpParser.DisplayResult display=displayParser.finish(displayCapture.completed);
            DisplayGroupDumpParser.PowerParser powerParser=new DisplayGroupDumpParser.PowerParser();
            BoundedDumpReader.Result powerCapture=BoundedDumpReader.run("power",MAX_BYTES,TIMEOUT_MS,powerParser);
            DisplayGroupDumpParser.PowerResult power=powerParser.finish(powerCapture.completed);
            RuntimeInfo primary=runtime(0);JSONArray values=new JSONArray();boolean all=display.verified&&power.verified;
            for(int id:ids){
                RuntimeInfo current=runtime(id);
                Integer group=display.verified?display.groups.get(id):null;
                SortedSet<Integer> members=group==null?null:display.members.get(group);
                boolean verified=display.verified&&power.verified&&group!=null&&members!=null&&members.contains(id)
                        &&power.groupIds.contains(group)&&Boolean.TRUE.equals(current.exists);
                all&=verified;JSONArray groupMembers=new JSONArray();
                if(members!=null)for(int member:members)groupMembers.put(member);
                Object mismatch=group==null||current.group==null?JSONObject.NULL:!group.equals(current.group);
                values.put(new JSONObject().put("displayId",id).put("mappingVerified",verified)
                        .put("mapperGroupId",nullable(group)).put("groupMembers",groupMembers)
                        .put("powerGroupPresent",group==null||!power.verified?JSONObject.NULL:power.groupIds.contains(group))
                        .put("primaryInteractive",nullable(primary.interactive)).put("targetInteractive",nullable(current.interactive))
                        .put("publicInfoExists",nullable(current.exists)).put("publicGroupId",nullable(current.group))
                        .put("publicDisplayFlags",nullable(current.flags)).put("ownDisplayGroupFlag",nullable(current.ownGroup)).put("publicMapperGroupDisagree",mismatch)
                        .put("errorClass",current.errorClass));
            }
            JSONArray powerGroups=new JSONArray();if(power.verified)for(int group:power.groupIds)powerGroups.put(group);
            return new JSONObject().put("status",all?"verified":"unavailable").put("mappingVerified",all)
                    .put("diagnosticOnly",true).put("snapshotAtomic",false).put("ownedDisplays",values)
                    .put("powerGroupIds",powerGroups).put("primaryInteractive",nullable(primary.interactive))
                    .put("primaryErrorClass",primary.errorClass)
                    .put("runtimeFlags",new JSONObject().put("perDisplayWakeByTouch",nullable(power.perDisplayWakeByTouch)))
                    .put("displayCapture",capture(displayCapture,display.reason)).put("powerCapture",capture(powerCapture,power.reason));
        }finally{SAMPLING.set(false);}
    }
}