package fr.phonemcp;
import java.util.Arrays;
public final class AdRevealPolicyTest {
    static int n;static void ok(boolean b,String label){n++;if(!b)throw new AssertionError(label);}
    static int[] icon(boolean bar){int[] p=new int[720*1280];Arrays.fill(p,0xff444444);for(int x=40;x<=50;x++){int extent=(50-x)*6/10;for(int y=39-extent;y<=39+extent;y++)p[y*720+x]=0xffffffff;}if(bar)for(int y=33;y<=45;y++){p[y*720+53]=0xffffffff;p[y*720+54]=0xffffffff;}return p;}
    public static void main(String[] args){
        ok(VisualSkipPolicy.beforeLabel(icon(true),720,1280,66,35,178,45)!=null,"Measured skip triangle and bar beside observed badge");
        ok(VisualSkipPolicy.beforeLabel(icon(false),720,1280,66,35,178,45)==null,"Brand/play triangle without skip bar refused");
        ok(VisualSkipPolicy.beforeLabel(new int[4],720,1280,66,35,178,45)==null,"Wrong capture dimensions refused");
        ok(VisualSkipPolicy.beforeLabel(icon(true),720,1280,66,500,178,510)==null,"Interior creative badge refused");
        ok(AdRevealPolicy.badge("Google Play"),"Observed store badge known");ok(!AdRevealPolicy.badge("Installer"),"Install CTA never a skip badge");
        ok(AdRevealPolicy.timer("9",35,1200,70,1240,720,1280),"Observed bottom corner end-card timer waits");
        ok(!AdRevealPolicy.timer("9",200,500,240,540,720,1280),"Game score is not a corner timer");
        ok(!AdRevealPolicy.timer("0",35,1200,70,1240,720,1280),"Zero timer permits next controls");
        ok(AdRevealPolicy.mayReveal(true,false,false,0,1000,2800),"Bounded first reveal");
        ok(!AdRevealPolicy.mayReveal(true,false,true,0,1000,9000),"Never reveal across observed timer");
        ok(!AdRevealPolicy.mayReveal(true,true,false,0,1000,9000),"Reward warning retains normal handling");
        ok(!AdRevealPolicy.mayReveal(true,false,false,2,1000,9000),"No unbounded repeated taps");
        ok(!AdRevealPolicy.mayReveal(false,false,false,0,1000,9000),"No probes outside observed ad page");
        ok(!AdRevealPolicy.mayReveal(true,false,false,1,1000,8999),"Second reveal waits for fresh no-effect observations");
        ok(AdRevealPolicy.mayReveal(true,false,false,1,1000,9000),"One bounded delayed reveal retry");
        System.out.println("PASS: "+n+" ad reveal/skip/timer checks");
    }
}
