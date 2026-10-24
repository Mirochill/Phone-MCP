package fr.phonemcp;

import java.util.ArrayList;
import java.util.List;

/** Bounded one/two-pointer timeline, independent of Android and of app contents. */
final class NativeGesturePolicy {
    static final int DOWN=0,UP=1,MOVE=2,CANCEL=3,POINTER_DOWN=261,POINTER_UP=262;
    static final class Stroke {
        final int x,y,toX,toY,start,duration;
        Stroke(int x,int y,int tx,int ty,int start,int duration){this.x=x;this.y=y;toX=tx;toY=ty;this.start=start;this.duration=duration;}
    }
    static final class Event {
        final int at,downAt,action;final float[] xs,ys;
        Event(int at,int downAt,int action,float[] xs,float[] ys){this.at=at;this.downAt=downAt;this.action=action;this.xs=xs;this.ys=ys;}
    }
    private static void check(Stroke s,int width,int height){
        if(s==null||s.start<0||s.start>2000||s.duration<1||s.duration>2000||s.x<0||s.y<0||s.toX<0||s.toY<0||s.x>=width||s.toX>=width||s.y>=height||s.toY>=height)throw new IllegalArgumentException("Invalid gesture timeline");
    }
    static List<Event> timeline(List<Stroke> strokes,int width,int height){
        if(strokes==null||strokes.isEmpty()||strokes.size()>2||width<1||height<1)throw new IllegalArgumentException("Invalid gesture");
        for(Stroke s:strokes)check(s,width,height);
        List<Event> events=new ArrayList<>();Stroke a=strokes.get(0);
        if(a.start!=0)throw new IllegalArgumentException("Gesture must start at zero");
        if(strokes.size()==2&&strokes.get(1).start==0){
            Stroke b=strokes.get(1);if(a.duration!=b.duration)throw new IllegalArgumentException("Mismatched pointer durations");
            events.add(event(a,b,0,DOWN,1));events.add(event(a,b,0,POINTER_DOWN,2));
            for(int at=25;at<a.duration;at+=25)events.add(event(a,b,at,MOVE,2));
            events.add(event(a,b,a.duration,POINTER_UP,2));events.add(event(a,b,a.duration,UP,1));
        }else{
            int lastEnd=-1;
            for(Stroke s:strokes){
                if(s.start<=lastEnd)throw new IllegalArgumentException("Overlapping strokes");
                events.add(event(s,null,s.start,DOWN,1));
                if(s.x!=s.toX||s.y!=s.toY)for(int at=s.start+25;at<s.start+s.duration;at+=25)events.add(event(s,null,at,MOVE,1));
                events.add(event(s,null,s.start+s.duration,UP,1));lastEnd=s.start+s.duration;
            }
        }
        return events;
    }
    private static Event event(Stroke a,Stroke b,int at,int action,int count){
        float ratio=Math.max(0,Math.min(1,(float)(at-a.start)/a.duration));float[] xs=new float[count],ys=new float[count];Stroke[] values={a,b};
        for(int i=0;i<count;i++){Stroke s=values[i];xs[i]=s.x+(s.toX-s.x)*ratio;ys[i]=s.y+(s.toY-s.y)*ratio;}
        return new Event(at,a.start,action,xs,ys);
    }
}
