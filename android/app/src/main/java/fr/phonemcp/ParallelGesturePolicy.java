package fr.phonemcp;

import java.util.UUID;

/** Pure bounds and correlation policy for the app-only accessibility gesture channel. */
final class ParallelGesturePolicy {
    static final int MAX_STROKES=2, MAX_PAYLOAD=4096, MAX_DURATION_MS=2000, BIND_WAIT_MS=1500;
    static final long MAX_REQUEST_MS=6000;
    static boolean uuid(String value){try{return value!=null&&UUID.fromString(value).toString().equals(value);}catch(IllegalArgumentException bad){return false;}}
    static boolean target(String epoch,String session,String pkg,int display,int window,int width,int height,long deadline,long now){
        return uuid(epoch)&&uuid(session)&&pkg!=null&&pkg.matches("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")&&display>0&&window>=0&&width>0&&height>0&&width<=16384&&height<=16384&&deadline>now&&deadline-now<=MAX_REQUEST_MS;
    }
    static boolean stroke(int x,int y,int tx,int ty,int start,int duration,int width,int height){
        return x>=0&&y>=0&&tx>=0&&ty>=0&&x<width&&tx<width&&y<height&&ty<height&&start>=0&&start<=500&&duration>=1&&duration<=MAX_DURATION_MS&&start+duration<=2500;
    }
    static boolean resultMatches(String expectedId,int expectedDisplay,String actualId,int actualDisplay){return uuid(expectedId)&&expectedDisplay>0&&expectedId.equals(actualId)&&expectedDisplay==actualDisplay;}
}
