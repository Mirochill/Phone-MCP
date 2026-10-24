package fr.phonemcp;

import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Rect;
import org.json.*;
import java.util.*;

final class TouchGestures {
    static final class Stroke {
        final int x,y,toX,toY,start,duration;
        Stroke(int x,int y,int tx,int ty,int start,int duration){this.x=x;this.y=y;toX=tx;toY=ty;this.start=start;this.duration=duration;}
    }
    static List<Stroke> strokes(String op,JSONObject args,Rect area)throws Exception{
        List<Stroke> strokes=new ArrayList<>();
        if("pinch".equals(op)){
            int x=args.getInt("centerX"),y=args.getInt("centerY"),a=args.getInt("startDistance")/2,b=args.getInt("endDistance")/2,t=args.optInt("durationMs",400);
            if(a<10||b<10||t<150||t>1000)throw new IllegalArgumentException("Invalid pinch gesture.");
            strokes.add(new Stroke(x-a,y,x-b,y,0,t));strokes.add(new Stroke(x+a,y,x+b,y,0,t));
        }else{
            boolean swipe="swipe".equals(op);int x=args.getInt(swipe?"fromX":"x"),y=args.getInt(swipe?"fromY":"y");
            int tx=swipe?args.getInt("toX"):x,ty=swipe?args.getInt("toY"):y,t=swipe?args.optInt("durationMs",350):60;
            if(t<1||t>1000||(swipe&&t<100))throw new IllegalArgumentException("Invalid gesture duration.");
            strokes.add(new Stroke(x,y,tx,ty,0,t));if("double_tap".equals(op))strokes.add(new Stroke(x,y,x,y,150,60));
        }
        for(Stroke stroke:strokes)if(!area.contains(stroke.x,stroke.y)||!area.contains(stroke.toX,stroke.toY))throw new SecurityException("Gesture exceeds the observed window.");
        return strokes;
    }
    static GestureDescription build(List<Stroke> strokes,int display){
        GestureDescription.Builder builder=new GestureDescription.Builder().setDisplayId(display);
        for(Stroke stroke:strokes){Path path=new Path();path.moveTo(stroke.x,stroke.y);if(stroke.x!=stroke.toX||stroke.y!=stroke.toY)path.lineTo(stroke.toX,stroke.toY);builder.addStroke(new GestureDescription.StrokeDescription(path,stroke.start,stroke.duration));}
        return builder.build();
    }
}
