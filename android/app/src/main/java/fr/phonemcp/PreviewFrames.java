package fr.phonemcp;

import android.graphics.Bitmap;
import android.media.Image;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/** Drains the display continuously; copies a fresh frame only for a waiting local viewer. */
final class PreviewFrames {
    private final AtomicReference<CompletableFuture<byte[]>> pending=new AtomicReference<>();
    byte[] next()throws Exception{
        CompletableFuture<byte[]> request=new CompletableFuture<>();
        if(!pending.compareAndSet(null,request))throw new IllegalStateException("Aperçu en cours.");
        try{return request.get(850,TimeUnit.MILLISECONDS);}
        finally{pending.compareAndSet(request,null);request.cancel(false);}
    }
    void accept(Image image){
        CompletableFuture<byte[]> request=pending.getAndSet(null);
        if(request==null||request.isDone())return;
        Bitmap frame=null;
        try{
            Image.Plane plane=image.getPlanes()[0];
            int width=image.getWidth(),height=image.getHeight(),stride=plane.getPixelStride();
            if(stride!=4)throw new IllegalStateException("Format d’aperçu indisponible.");
            ByteBuffer buffer=plane.getBuffer(),pixels=ByteBuffer.allocate(width*height*stride);
            // The last Android image row need not include its trailing stride padding.
            for(int row=0;row<height;row++){ByteBuffer slice=buffer.duplicate();slice.position(row*plane.getRowStride());slice.limit(slice.position()+width*stride);pixels.put(slice);}
            pixels.rewind();frame=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);frame.copyPixelsFromBuffer(pixels);
            ByteArrayOutputStream encoded=new ByteArrayOutputStream();
            frame.compress(Bitmap.CompressFormat.JPEG,65,encoded);
            if(encoded.size()>300000)throw new IllegalStateException("Aperçu trop volumineux.");
            request.complete(encoded.toByteArray());
        }catch(Exception error){request.completeExceptionally(error);}
        finally{if(frame!=null)frame.recycle();}
    }
    void clear(){CompletableFuture<byte[]> request=pending.getAndSet(null);if(request!=null)request.cancel(false);}
}
