package fr.phonemcp;

import android.app.*;
import android.content.*;
import android.net.Uri;
import org.json.*;

/** Content-free document cards; only the local viewer can appear on the physical display. */
final class RecentsCards {
    private static final String SCHEME="phonemcp-preview";
    private static String lastError="";
    static Intent intent(Context context,String id,String pkg){
        return new Intent(context,RecentsRegistrationActivity.class).setData(new Uri.Builder().scheme(SCHEME).authority("session").appendPath(id).build())
            .putExtra("sessionId",id).putExtra("packageName",pkg)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_NEW_DOCUMENT|Intent.FLAG_ACTIVITY_RETAIN_IN_RECENTS);
    }
    private static String id(ActivityManager.RecentTaskInfo info){
        Intent base=info.baseIntent;if(base==null||base.getComponent()==null)return null;
        String type=base.getComponent().getClassName();if(!PreviewActivity.class.getName().equals(type)&&!RecentsRegistrationActivity.class.getName().equals(type))return null;
        Uri uri=base.getData();return uri!=null&&SCHEME.equals(uri.getScheme())&&"session".equals(uri.getHost())?uri.getLastPathSegment():null;
    }
    static void sync(Context context){
        try{for(ActivityManager.AppTask task:context.getSystemService(ActivityManager.class).getAppTasks()){
            ActivityManager.RecentTaskInfo info=task.getTaskInfo();String selected=id(info);
            if(selected!=null&&!BridgeService.canPreview(selected,info.baseIntent.getStringExtra("packageName")))task.finishAndRemoveTask();
        }lastError="";}catch(RuntimeException error){lastError=error.getClass().getSimpleName();}
    }
    @SuppressWarnings("deprecation")
    static void describe(Activity activity,String label){activity.setTaskDescription(new ActivityManager.TaskDescription(activity.getString(R.string.preview_recent_title,label),null,0xFF101216));}
    static JSONObject state(Context context){JSONObject result=new JSONObject();try{int count=0;for(ActivityManager.AppTask task:context.getSystemService(ActivityManager.class).getAppTasks())if(id(task.getTaskInfo())!=null)count++;result.put("count",count).put("error",lastError);}catch(Exception ignored){}return result;}
}
