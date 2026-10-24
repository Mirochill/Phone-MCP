package fr.phonemcp;

public final class SnapshotGeometryPolicyTest {
    private static int checks;
    private static void expect(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    private static SnapshotGeometryPolicy.Frame frame(String pkg,int display,int window,int width,int height,int[] root,int[] outer){return new SnapshotGeometryPolicy.Frame(pkg,display,window,width,height,root,outer);}
    private static SnapshotGeometryPolicy.Frame regular(){return frame("com.example",40,8,720,1280,new int[]{0,0,720,1280},new int[]{0,0,720,1280});}
    public static void main(String[] args){
        SnapshotGeometryPolicy.Frame f=regular();
        expect(f.valid(),"Normal geometry valid");
        expect(!SnapshotGeometryPolicy.stable(f,100,f,100),"One instant is not a stable observation");
        expect(!SnapshotGeometryPolicy.stable(f,100,regular(),179),"Require the complete quiet interval");
        expect(SnapshotGeometryPolicy.stable(f,100,regular(),180),"Exact repeated frame after 80 ms is stable");
        expect(!SnapshotGeometryPolicy.stable(f,100,f,99),"Clock reversal rejected");
        expect(!SnapshotGeometryPolicy.stable(f,100,f,1601),"Expired stability interval rejected");
        expect(!SnapshotGeometryPolicy.stable(null,100,f,180),"Missing first frame rejected");
        expect(!SnapshotGeometryPolicy.stable(f,100,null,180),"Missing second frame rejected");
        expect(!SnapshotGeometryPolicy.stable(f,100,frame("com.example",40,8,720,1280,new int[]{0,0,719,1280},new int[]{0,0,720,1280}),180),"Even a single root pixel changes the frame; no tolerance");
        expect(!SnapshotGeometryPolicy.stable(f,100,frame("com.example",40,8,720,1280,new int[]{0,0,720,1280},new int[]{0,1,720,1280}),180),"Window outer geometry must remain exact");
        expect(!SnapshotGeometryPolicy.stable(f,100,frame("com.other",40,8,720,1280,f.root,f.outer),180),"Package switch rejected");
        expect(!SnapshotGeometryPolicy.stable(f,100,frame("com.example",41,8,720,1280,f.root,f.outer),180),"Display switch rejected");
        expect(!SnapshotGeometryPolicy.stable(f,100,frame("com.example",40,9,720,1280,f.root,f.outer),180),"Window switch rejected");
        expect(!SnapshotGeometryPolicy.stable(f,100,frame("com.example",40,8,721,1280,f.root,f.outer),180),"Logical width switch rejected");
        expect(!frame("com.example",0,8,720,1280,f.root,f.outer).valid(),"Physical display forbidden");
        expect(!frame("com.example",40,-1,720,1280,f.root,f.outer).valid(),"Unknown window forbidden");
        expect(!frame("com.example",40,8,720,1280,new int[]{0,0,721,1280},f.outer).valid(),"Oversized root rejected");
        expect(!frame("com.example",40,8,720,1280,new int[]{0,0,0,1280},f.outer).valid(),"Empty root rejected");
        expect(!frame("com.example",40,8,720,1280,f.root,new int[]{-1,0,720,1280}).valid(),"Negative outer bound rejected");
        int[] mutable={0,0,720,1280};SnapshotGeometryPolicy.Frame copied=frame("com.example",40,8,720,1280,mutable,mutable);mutable[2]=1;expect(copied.equals(f),"Caller cannot mutate captured geometry");
        expect(copied.hashCode()==f.hashCode(),"Equal frames have equal hashes");
        expect(SnapshotGeometryPolicy.requiredStableMs(false,false)==80,"Existing windows keep their short stable interval");
        expect(SnapshotGeometryPolicy.requiredStableMs(false,true)==240,"A new window needs a longer quiet interval");
        expect(SnapshotGeometryPolicy.requiredStableMs(true,true)==500,"A fresh launch needs 500 ms, not the first short plateau");
        expect(!SnapshotGeometryPolicy.stable(f,100,f,340,500),"A 240 ms launch plateau is insufficient");
        expect(SnapshotGeometryPolicy.stable(f,100,f,600,500),"The first launch frame becomes stable after 500 ms");
        expect(!SnapshotGeometryPolicy.stable(f,100,f,339,240),"A new window cannot use an existing-window interval");
        expect(SnapshotGeometryPolicy.stable(f,100,f,340,240),"A new non-launch window becomes stable after 240 ms");
        SnapshotGeometryPolicy.Frame topology=new SnapshotGeometryPolicy.Frame("com.example",40,8,720,1280,f.root,f.outer,1);
        expect(!SnapshotGeometryPolicy.stable(f,100,topology,600,500),"A late window-state event resets stability even when bounds repeat");
        System.out.println("SnapshotGeometryPolicyTest: "+checks+" checks passed");
    }
}