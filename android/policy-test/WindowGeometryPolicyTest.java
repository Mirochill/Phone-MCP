package fr.phonemcp;

public final class WindowGeometryPolicyTest {
    private static int checks;
    private static void expect(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static boolean bounds(int l,int t,int r,int b){return WindowGeometryPolicy.bounds(l,t,r,b,720,1280);}
    public static void main(String[] args){
        expect(WindowGeometryPolicy.display(77,77,720,1280,720,1280),"Exact owned display dimensions are accepted");
        expect(!WindowGeometryPolicy.display(77,0,720,1280,720,1280),"Physical display must never substitute for the owned display");
        expect(!WindowGeometryPolicy.display(77,78,720,1280,720,1280),"Another agent display is rejected");
        expect(!WindowGeometryPolicy.display(0,0,720,1280,720,1280),"Display zero is outside this policy");
        expect(!WindowGeometryPolicy.display(77,77,720,1280,1080,1920),"Physical-size or stale logical metadata is rejected");
        expect(!WindowGeometryPolicy.display(77,77,720,1280,1280,720),"Rotation or swapped dimensions require a fresh observation");
        expect(!WindowGeometryPolicy.display(77,77,720,1280,720,1279),"A one-pixel dimension mismatch is not silently scaled");
        expect(!WindowGeometryPolicy.display(77,77,0,1280,0,1280),"Unavailable dimensions never prove geometry");
        expect(bounds(0,0,720,1280),"A full display window is valid");
        expect(bounds(80,200,640,900),"An interior dialogue is accepted without waiting for full screen");
        expect(bounds(0,100,720,850),"A window resized for IME/insets remains valid");
        expect(!bounds(272,34,954,1246),"The real Samsung transient x>width window is rejected");
        expect(!bounds(0,0,720,1281),"A window extending past the display height is rejected");
        expect(!bounds(-1,0,719,1280),"A negative window offset cannot be normalized by guess");
        expect(!bounds(0,-1,720,1279),"A negative top offset is rejected");
        expect(!bounds(20,20,20,900),"An empty width is a transition, not a coordinate space");
        expect(!bounds(20,20,640,20),"An empty height is rejected");
        expect(!bounds(640,20,20,900),"Inverted bounds are rejected");
        expect(!bounds(0,0,Integer.MAX_VALUE,Integer.MAX_VALUE),"Absurd coordinates are not allowed to overflow a subtraction");
        boolean initial=bounds(272,34,954,1246),settled=bounds(0,0,720,1280);
        expect(!initial&&settled,"A transition can become ready from metadata only, without a fixed delay or comparing animated pixels");
        expect(!bounds(272,34,954,1246),"A later invalid frame is not authorized by a previously valid frame");
        expect(bounds(200,300,500,700),"A smaller valid window never needs an inferred full-display transform");
        System.out.println("PASS: "+checks+" display/window geometry checks");
    }
}
