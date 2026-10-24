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
    private final AtomicReference<CompletableFuture<Bitmap>> bitmapPending=new AtomicReference<>();
    private Image retained;
    private boolean closed;
    private Bitmap reusable;
    private ByteBuffer pixels;
    private final ByteArrayOutputStream encoded=new ByteArrayOutputStream(128000);
    byte[] next()throws Exception{
        CompletableFuture<byte[]> request=new CompletableFuture<>();
        if(!pending.compareAndSet(null,request))throw new IllegalStateException("Aperçu en cours.");
        try{return request.get(850,TimeUnit.MILLISECONDS);}
        finally{pending.compareAndSet(request,null);request.cancel(false);}
    }
    Bitmap nextBitmap()throws Exception{
        CompletableFuture<Bitmap> request=new CompletableFuture<>();
        synchronized(this){if(closed)throw new IllegalStateException("Affichage terminé.");if(retained!=null){copyPixels(retained);return reusable.copy(Bitmap.Config.ARGB_8888,false);}if(!bitmapPending.compareAndSet(null,request))throw new IllegalStateException("Capture de surface en cours.");}
        try{return request.get(850,TimeUnit.MILLISECONDS);}
        finally{bitmapPending.compareAndSet(request,null);request.cancel(false);}
    }
    // Takes ownership of one current producer buffer; no pixel copy occurs without demand.
    synchronized void accept(Image image){
        if(closed){image.close();return;}
        Image previous=retained;retained=image;if(previous!=null&&previous!=image)previous.close();
        CompletableFuture<byte[]> request=pending.getAndSet(null);
        CompletableFuture<Bitmap> bitmapRequest=bitmapPending.getAndSet(null);
        if((request==null||request.isDone())&&(bitmapRequest==null||bitmapRequest.isDone()))return;
        try{
            copyPixels(image);encoded.reset();
            if(bitmapRequest!=null&&!bitmapRequest.isDone()){Bitmap copy=reusable.copy(Bitmap.Config.ARGB_8888,false);if(!bitmapRequest.complete(copy))copy.recycle();}
            if(request==null||request.isDone())return;
            reusable.compress(Bitmap.CompressFormat.JPEG,65,encoded);
            if(encoded.size()>300000)throw new IllegalStateException("Aperçu trop volumineux.");
            request.complete(encoded.toByteArray());
        }catch(Exception error){if(request!=null)request.completeExceptionally(error);if(bitmapRequest!=null)bitmapRequest.completeExceptionally(error);}
    }
    private void copyPixels(Image image){
            Image.Plane plane=image.getPlanes()[0];
            int width=image.getWidth(),height=image.getHeight(),stride=plane.getPixelStride();
            if(stride!=4)throw new IllegalStateException("Format d’aperçu indisponible.");
            ByteBuffer buffer=plane.getBuffer();
            if(pixels==null||pixels.capacity()!=width*height*stride)pixels=ByteBuffer.allocateDirect(width*height*stride);
            pixels.clear();
            // The last Android image row need not include its trailing stride padding.
            for(int row=0;row<height;row++){ByteBuffer slice=buffer.duplicate();slice.position(row*plane.getRowStride());slice.limit(slice.position()+width*stride);pixels.put(slice);}
            if(reusable==null||reusable.getWidth()!=width||reusable.getHeight()!=height){if(reusable!=null)reusable.recycle();reusable=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);}
            pixels.rewind();reusable.copyPixelsFromBuffer(pixels);
    }
    synchronized void clearCopies(){CompletableFuture<Bitmap> bitmap=bitmapPending.getAndSet(null);if(bitmap!=null)bitmap.cancel(false);CompletableFuture<byte[]> request=pending.getAndSet(null);if(request!=null)request.cancel(false);if(reusable!=null)reusable.recycle();reusable=null;pixels=null;encoded.reset();}
    synchronized void clear(){closed=true;clearCopies();if(retained!=null)retained.close();retained=null;}
}
