package fr.phonemcp;
import java.util.*;
public final class NativeGesturePolicyTest {
    static int checks;static void expect(boolean condition,String name){checks++;if(!condition)throw new AssertionError(name);}
    static NativeGesturePolicy.Stroke s(int x,int y,int tx,int ty,int start,int duration){return new NativeGesturePolicy.Stroke(x,y,tx,ty,start,duration);}
    static List<NativeGesturePolicy.Event> t(NativeGesturePolicy.Stroke... strokes){return NativeGesturePolicy.timeline(Arrays.asList(strokes),720,1280);}
    static void bad(NativeGesturePolicy.Stroke... strokes){try{t(strokes);throw new AssertionError("Accepted invalid gesture");}catch(IllegalArgumentException expected){checks++;}}
    public static void main(String[] args){
        var tap=t(s(20,30,20,30,0,60));expect(tap.size()==2,"A tap sends exactly DOWN/UP");expect(tap.get(0).action==0&&tap.get(1).action==1&&tap.get(1).at==60,"Tap ordering and duration");
        var dbl=t(s(20,30,20,30,0,60),s(20,30,20,30,150,60));expect(dbl.size()==4&&dbl.get(2).downAt==150&&dbl.get(3).at==210,"Two taps have distinct downTime with a real gap");
        var swipe=t(s(10,20,110,220,0,100));expect(swipe.size()==5,"Swipe has bounded samples");expect(swipe.get(2).xs[0]==60&&swipe.get(2).ys[0]==120,"Interpolation");expect(swipe.get(4).xs[0]==110&&swipe.get(4).ys[0]==220,"Exact endpoint");
        var pinch=t(s(100,200,50,200,0,100),s(300,200,350,200,0,100));expect(pinch.get(0).xs.length==1&&pinch.get(1).xs.length==2,"Pinch begins one then two pointers");expect(pinch.get(1).action==261,"Pointer index is encoded");expect(pinch.get(pinch.size()-2).action==262&&pinch.get(pinch.size()-1).action==1,"Pinch ends second pointer then first");expect(pinch.get(pinch.size()-2).xs[1]==350,"Second pointer endpoint");
        for(var events:List.of(tap,dbl,swipe,pinch)){int previous=-1;for(var e:events){expect(e.at>=previous&&e.downAt<=e.at,"Monotonic times");expect(e.xs.length==e.ys.length&&e.xs.length>=1&&e.xs.length<=2,"Bounded pointer count");previous=e.at;}}
        bad();bad(s(-1,0,1,1,0,60));bad(s(0,0,720,0,0,60));bad(s(0,0,1,1280,0,60));bad(s(0,0,1,1,0,0));bad(s(0,0,1,1,0,2001));bad(s(0,0,1,1,1,60));bad(s(0,0,1,1,0,100),s(0,0,1,1,50,60));bad(s(0,0,1,1,0,100),s(0,0,1,1,0,200));bad(s(0,0,1,1,0,50),s(0,0,1,1,100,50),s(0,0,1,1,200,50));
        expect(t(s(0,0,719,1279,0,2000)).size()<=82,"Maximum duration remains bounded");expect(t(s(0,0,0,0,0,1800)).size()==2,"Long press does not poll");
        System.out.println("PASS: "+checks+" synchronous gesture timeline checks");
    }
}
