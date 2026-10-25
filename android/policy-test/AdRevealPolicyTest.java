package fr.phonemcp;
import java.util.Arrays;
public final class AdRevealPolicyTest {
    static int n;static void ok(boolean b,String label){n++;if(!b)throw new AssertionError(label);}
    static int[] icon(boolean bar){int[] p=new int[720*1280];Arrays.fill(p,0xff444444);for(int x=40;x<=50;x++){int extent=(50-x)*6/10;for(int y=39-extent;y<=39+extent;y++)p[y*720+x]=0xffffffff;}if(bar)for(int y=33;y<=45;y++){p[y*720+53]=0xffffffff;p[y*720+54]=0xffffffff;}return p;}

    static int[] doubleSkip(boolean bar,boolean circle,boolean doubleArrow,int cy){int w=720,h=1280,cx=627;int[] p=new int[w*h];Arrays.fill(p,0xff222222);if(circle)for(int y=cy-35;y<=cy+35;y++)for(int x=cx-35;x<=cx+35;x++)if((x-cx)*(x-cx)+(y-cy)*(y-cy)<=35*35)p[y*w+x]=0xff777777;
        for(int start:new int[]{610,626}){if(start==610&&!doubleArrow)continue;for(int dx=0;dx<15;dx++){int dy=(int)(8*(1-dx/16.));for(int y=cy-dy;y<=cy+dy;y++)p[y*w+start+dx]=0xfffafafa;}}
        if(bar)for(int y=cy-8;y<=cy+8;y++)for(int x=642;x<=644;x++)p[y*w+x]=0xfffafafa;return p;}
    public static void main(String[] args){
        ok(VisualSkipPolicy.inControl(doubleSkip(true,false,true,139),720,1280,"base-corner-top-right",531,90,675,233)!=null,"Observed SDK container plus double glyph supports translucent controls");
        ok(VisualSkipPolicy.inControl(doubleSkip(true,false,true,139),720,1280,"install",531,90,675,233)==null,"Install container cannot substitute for skip control evidence");
        ok(VisualSkipPolicy.inControl(doubleSkip(false,false,true,139),720,1280,"base-corner-top-right",531,90,675,233)==null,"SDK container never substitutes for missing bar");
        ok(VisualSkipPolicy.inControl(doubleSkip(true,false,true,139),720,1280,"base-corner-top-right",0,0,720,1280)==null,"Full-screen creative cannot substitute for a small SDK control");
        ok(VisualSkipPolicy.corner(doubleSkip(true,true,true,139),720,1280)!=null,"Positive corner double triangle/bar enclosed button");
        ok(VisualSkipPolicy.corner(doubleSkip(false,true,true,139),720,1280)==null,"Fast-forward decoration without bar refused");
        ok(VisualSkipPolicy.corner(doubleSkip(true,false,true,139),720,1280)==null,"Unenclosed double triangle refused");
        ok(VisualSkipPolicy.corner(doubleSkip(true,true,false,139),720,1280)==null,"Play glyph is not skip");
        ok(VisualSkipPolicy.corner(doubleSkip(true,true,true,600),720,1280)==null,"Interior creative skip-like glyph refused");

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
