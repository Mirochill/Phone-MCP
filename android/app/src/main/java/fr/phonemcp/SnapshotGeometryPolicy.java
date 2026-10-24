package fr.phonemcp;

import java.util.Arrays;
import java.util.Objects;

/** Observe exact geometry twice; never round, transform or tolerate a moved target. */
final class SnapshotGeometryPolicy {
    static final long STABLE_MS=80,NEW_WINDOW_STABLE_MS=240,FRESH_LAUNCH_STABLE_MS=500,WAIT_MS=1500;
    static long requiredStableMs(boolean freshLaunch,boolean newWindow){return freshLaunch?FRESH_LAUNCH_STABLE_MS:newWindow?NEW_WINDOW_STABLE_MS:STABLE_MS;}
    static final class Frame {
        final String pkg;final int display,window,width,height;final int[] root,outer;final long topology;
        Frame(String pkg,int display,int window,int width,int height,int[] root,int[] outer){this(pkg,display,window,width,height,root,outer,0);}
        Frame(String pkg,int display,int window,int width,int height,int[] root,int[] outer,long topology){this.topology=topology;this.pkg=pkg;this.display=display;this.window=window;this.width=width;this.height=height;this.root=root==null?null:root.clone();this.outer=outer==null?null:outer.clone();}
        boolean valid(){return pkg!=null&&!pkg.isEmpty()&&display>0&&window>=0&&validBox(root)&&validBox(outer);}
        private boolean validBox(int[] b){return b!=null&&b.length==4&&WindowGeometryPolicy.bounds(b[0],b[1],b[2],b[3],width,height);}
        @Override public boolean equals(Object value){if(!(value instanceof Frame))return false;Frame f=(Frame)value;return Objects.equals(pkg,f.pkg)&&display==f.display&&window==f.window&&width==f.width&&height==f.height&&topology==f.topology&&Arrays.equals(root,f.root)&&Arrays.equals(outer,f.outer);}
        @Override public int hashCode(){return Objects.hash(pkg,display,window,width,height,topology,Arrays.hashCode(root),Arrays.hashCode(outer));}
    }
    static boolean stable(Frame first,long firstAt,Frame current,long now){return stable(first,firstAt,current,now,STABLE_MS);}
    static boolean stable(Frame first,long firstAt,Frame current,long now,long required){return required>=STABLE_MS&&required<=WAIT_MS&&first!=null&&current!=null&&first.valid()&&current.valid()&&first.equals(current)&&now>=firstAt&&now-firstAt>=required&&now-firstAt<=WAIT_MS;}
}