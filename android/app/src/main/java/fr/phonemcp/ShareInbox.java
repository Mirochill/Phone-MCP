package fr.phonemcp;

import android.content.*;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.database.Cursor;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Only user-initiated Android share intents enter this bounded, expiring inbox. */
final class ShareInbox {
    private final Context context;
    ShareInbox(Context c){context=c;}
    private File folder(){File dir=new File(context.getCacheDir(),"shared-inbox");if(!dir.isDirectory()&&!dir.mkdir())throw new IllegalStateException("Shared inbox unavailable.");return dir;}
    synchronized void accept(Intent intent)throws Exception{
        clean();String action=intent.getAction();if(!Intent.ACTION_SEND.equals(action)&&!Intent.ACTION_SEND_MULTIPLE.equals(action))return;
        CharSequence text=intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
        if(text!=null){if(text.length()>60000)throw new IllegalArgumentException("Shared text is too long.");save(text.toString().getBytes(StandardCharsets.UTF_8),"Shared text.txt","text/plain");}
        ArrayList<Uri> uris=Intent.ACTION_SEND_MULTIPLE.equals(action)?intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM):new ArrayList<>();
        if(uris==null)uris=new ArrayList<>();if(Intent.ACTION_SEND.equals(action)){Uri uri=intent.getParcelableExtra(Intent.EXTRA_STREAM);if(uri!=null)uris.add(uri);}
        if(uris.size()>4)throw new IllegalArgumentException("Share up to four files at once.");
        for(Uri uri:uris){if(!"content".equals(uri.getScheme()))throw new SecurityException("Only a granted content URI may be shared.");String name="Shared file";try(Cursor c=context.getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())name=c.getString(0);}try(InputStream in=context.getContentResolver().openInputStream(uri)){if(in==null)throw new IOException("Shared file unavailable.");byte[] bytes=CompatIO.readAtMost(in,8388609);if(bytes.length>8388608)throw new IllegalArgumentException("Shared files are limited to 8 MiB.");save(bytes,name,context.getContentResolver().getType(uri));}}
    }
    private void save(byte[] data,String name,String type)throws Exception{
        File[] existing=folder().listFiles((d,n)->n.endsWith(".json"));if(existing!=null&&existing.length>=20)throw new IllegalStateException("Shared inbox is full. Remove older items on the phone.");
        String id=UUID.randomUUID().toString();try(FileOutputStream out=new FileOutputStream(new File(folder(),id+".bin"))){out.write(data);}JSONObject meta=new JSONObject().put("fileId",id).put("name",name).put("mimeType",type==null?"application/octet-stream":type).put("sizeBytes",data.length).put("expiresAt",System.currentTimeMillis()+3600000);
        try(FileOutputStream out=new FileOutputStream(new File(folder(),id+".json"))){out.write(meta.toString().getBytes(StandardCharsets.UTF_8));}
    }
    synchronized JSONObject execute(String op,JSONObject args)throws Exception{
        if(!BridgeService.active()||!new Settings(context).filesAllowed())throw new SecurityException("Start a session and enable Files first.");clean();
        if("shared_content".equals(op))return new JSONObject().put("items",items());
        String id=UUID.fromString(args.getString("fileId")).toString();JSONObject meta=meta(id);File data=new File(folder(),id+".bin");
        String mime=meta.optString("mimeType");
        if(mime.equals("image/jpeg")||mime.equals("image/png")){
            android.graphics.BitmapFactory.Options bounds=new android.graphics.BitmapFactory.Options();bounds.inJustDecodeBounds=true;android.graphics.BitmapFactory.decodeFile(data.getPath(),bounds);if(bounds.outWidth<=0||bounds.outHeight<=0||((long)bounds.outWidth*bounds.outHeight)>40000000)throw new IllegalArgumentException("Image dimensions unsupported.");
            android.graphics.BitmapFactory.Options options=new android.graphics.BitmapFactory.Options();options.inSampleSize=1;while(Math.max(bounds.outWidth,bounds.outHeight)/options.inSampleSize>1600)options.inSampleSize*=2;android.graphics.Bitmap bitmap=android.graphics.BitmapFactory.decodeFile(data.getPath(),options);if(bitmap==null)throw new IOException("Image unavailable.");try{return CaptureFormatter.encode(bitmap,new JSONObject().put("maxDimension",1200),new JSONArray(),0,0,-1).put("name",meta.optString("name"));}finally{bitmap.recycle();}
        }
        try(InputStream in=new FileInputStream(data)){return StorageAccess.textPage(in,args).put("name",meta.optString("name"));}
    }
    synchronized JSONArray items()throws Exception{clean();JSONArray items=new JSONArray();File[] files=folder().listFiles((d,n)->n.endsWith(".json"));if(files!=null)for(File file:files)items.put(new JSONObject(read(file)));return items;}
    synchronized void clear(){File[] files=folder().listFiles();if(files!=null)for(File file:files)if(file.getName().matches("[a-f0-9-]{36}\\.(json|bin)"))file.delete();}
    private static String read(File file)throws Exception{return new String(java.nio.file.Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8);}
    private JSONObject meta(String id)throws Exception{return new JSONObject(read(new File(folder(),id+".json")));}
    private void clean(){File[] files=folder().listFiles((d,n)->n.endsWith(".json"));if(files!=null)for(File file:files)try{JSONObject meta=new JSONObject(read(file));if(meta.optLong("expiresAt")<System.currentTimeMillis()){String id=file.getName().substring(0,file.getName().length()-5);new File(folder(),id+".bin").delete();file.delete();}}catch(Exception ignored){file.delete();}}
}
