package fr.phonemcp;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.List;
import java.util.Set;

/** On-demand scoped process-exit codes and counters only; never opens descriptions or traces. */
@android.annotation.TargetApi(30)
final class ApplicationExitDiagnostics {
    static JSONArray sample(Context shell,Set<String> packages)throws Exception{
        JSONArray result=new JSONArray();ActivityManager manager=shell.getSystemService(ActivityManager.class);
        if(packages==null)return result;
        int count=0;
        for(String pkg:packages){
            if(count>=4)break;if(!AccessPolicy.validPackage(pkg))continue;count++;
            JSONObject item=new JSONObject().put("packageName",pkg);JSONArray exits=new JSONArray();
            try{
                if(manager==null)throw new IllegalStateException();
                List<ApplicationExitInfo> history=manager.getHistoricalProcessExitReasons(pkg,0,3);
                for(int i=0;history!=null&&i<Math.min(3,history.size());i++){
                    ApplicationExitInfo value=history.get(i);if(value==null)continue;
                    exits.put(new JSONObject().put("reason",value.getReason()).put("status",value.getStatus()).put("timestamp",value.getTimestamp()).put("pssKb",value.getPss()).put("rssKb",value.getRss()).put("importance",value.getImportance()));
                }
                item.put("available",true);
            }catch(Exception unavailable){item.put("available",false).put("errorCode",unavailable.getClass().getSimpleName());}
            result.put(item.put("exits",exits));
        }
        return result;
    }
}
