package fr.phonemcp;

/** Geometry only: no pixel comparisons, inferred transforms or full-screen requirement. */
final class WindowGeometryPolicy {
    static final long WAIT_MS=1500;
    static boolean display(int owned,int actual,int width,int height,int logicalWidth,int logicalHeight){
        return owned>0&&actual==owned&&width>0&&height>0&&logicalWidth==width&&logicalHeight==height;
    }
    static boolean bounds(int left,int top,int right,int bottom,int width,int height){
        return width>0&&height>0&&left>=0&&top>=0&&right>left&&bottom>top&&right<=width&&bottom<=height;
    }
}
