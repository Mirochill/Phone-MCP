package fr.phonemcp;

import android.content.*;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/** Only completed, nonempty captures are published to the user's media library. */
final class PendingMedia implements AutoCloseable {
    final Uri uri;
    final ParcelFileDescriptor output;
    private final ContentResolver resolver;
    private boolean published,closed;
    PendingMedia(Context context,boolean video)throws Exception{
        resolver=context.getContentResolver();
        String name="PhoneMCP_"+LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))+"_"+UUID.randomUUID().toString().substring(0,6)+(video?".mp4":".png");
        ContentValues values=new ContentValues();values.put(MediaStore.MediaColumns.DISPLAY_NAME,name);values.put(MediaStore.MediaColumns.MIME_TYPE,video?"video/mp4":"image/png");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH,(video?Environment.DIRECTORY_MOVIES:Environment.DIRECTORY_PICTURES)+"/PhoneMCP");values.put(MediaStore.MediaColumns.IS_PENDING,1);
        Uri collection=video?MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY):MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        uri=resolver.insert(collection,values);if(uri==null)throw new IOException("Création du fichier impossible.");
        ParcelFileDescriptor handle=null;
        try{handle=resolver.openFileDescriptor(uri,"rw");if(handle==null)throw new IOException("Sortie média absente.");output=handle;}
        catch(Exception error){if(handle!=null)handle.close();resolver.delete(uri,null,null);throw error;}
    }
    long bytes(){return output.getStatSize();}
    synchronized Uri publish()throws Exception{
        if(closed)throw new IOException("Capture fermée.");if(published)return uri;
        if(bytes()<=0)throw new IOException("Capture vide.");
        ContentValues values=new ContentValues();values.put(MediaStore.MediaColumns.IS_PENDING,0);
        if(resolver.update(uri,values,null,null)!=1)throw new IOException("Enregistrement du média non confirmé.");published=true;return uri;
    }
    @Override public synchronized void close(){if(closed)return;closed=true;try{output.close();}catch(Exception ignored){}if(!published)try{resolver.delete(uri,null,null);}catch(Exception ignored){}}
}