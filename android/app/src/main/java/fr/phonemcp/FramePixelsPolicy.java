package fr.phonemcp;
/** A fully black/transparent capture is suspicious, never proof that an app rendered successfully. */
final class FramePixelsPolicy {
    static boolean blank(int[] pixels,int width,int height){
        if(pixels==null||width<1||height<1||(long)width*height!=pixels.length)throw new IllegalArgumentException("Invalid frame dimensions");
        for(int color:pixels)if((color>>16&255)>3||(color>>8&255)>3||(color&255)>3)return false;
        return true;
    }
}
