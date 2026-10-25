package fr.phonemcp;

import java.util.Arrays;
public final class VisualClosePolicyTest {
    private static int checks;private static final int W=360,H=640;
    private static void expect(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    private static int[] blank(){int[] p=new int[W*H];Arrays.fill(p,0xffeeeeee);return p;}
    private static void icon(int[] p,int cx,int cy,int radius,int arm,boolean cross,boolean enclosure){
        if(enclosure)for(int y=Math.max(0,cy-radius);y<=Math.min(H-1,cy+radius);y++)for(int x=Math.max(0,cx-radius);x<=Math.min(W-1,cx+radius);x++)if((x-cx)*(x-cx)+(y-cy)*(y-cy)<=radius*radius)p[y*W+x]=0xff444444;
        for(int k=-arm;k<=arm;k++)for(int t=-1;t<=1;t++){
            int x=cx+k,y=cy+(cross?k:t);if(x>=0&&x<W&&y+t>=0&&y+t<H)p[(y+t)*W+x]=enclosure?0xffffffff:0xff111111;
            x=cx+k;y=cy+(cross?-k:t);if(x>=0&&x<W&&y+t>=0&&y+t<H)p[(y+t)*W+x]=enclosure?0xffffffff:0xff111111;
            if(!cross){x=cx+t;y=cy+k;if(x>=0&&x<W&&y>=0&&y<H)p[y*W+x]=0xffffffff;}
        }
    }
    private static int[] faint(boolean ring,boolean plus,boolean noisy){int[] p=new int[W*H];for(int i=0;i<p.length;i++){int v=35+(noisy?((i*17+i/W*13)%5)-2:0);p[i]=0xff000000|v<<16|v<<8|v;}
        int cx=316,cy=44;
        if(ring)for(int y=cy-28;y<=cy+28;y++)for(int x=cx-28;x<=cx+28;x++)if(Math.abs(Math.hypot(x-cx,y-cy)-26)<.7)p[y*W+x]=0xff2b2b2b;
        for(int k=-7;k<=7;k++)for(int t=-1;t<=1;t++){p[(cy+(plus?t:k)+t)*W+cx+k]=0xfff5f5f5;p[(cy+(plus?k:-k)+t)*W+cx+(plus?t:k)]=0xfff5f5f5;}
        return p;
    }
    public static void main(String[] args){
        expect(VisualClosePolicy.find(blank(),W,H)==null,"Blank frame has no cross");
        int[] enclosed=blank();icon(enclosed,316,44,28,14,true,true);VisualClosePolicy.Candidate c=VisualClosePolicy.find(enclosed,W,H);expect(c!=null,"A 56 px enclosed corner X has positive evidence");expect(Math.abs(c.x-316)<=2&&Math.abs(c.y-44)<=2,"Candidate follows measured glyph center");
        int[] odd=blank();icon(odd,315,45,28,14,true,true);expect(VisualClosePolicy.find(odd,W,H)!=null,"Odd-pixel glyph center is supported");
        int[] small=blank();icon(small,330,30,16,8,true,true);expect(VisualClosePolicy.find(small,W,H)!=null,"Small SDK close button is supported");
        int[] plain=blank();icon(plain,316,44,28,14,true,false);expect(VisualClosePolicy.find(plain,W,H)==null,"Plain text X without enclosing control is insufficient");
        expect(VisualClosePolicy.storeClose(plain,W,H)!=null,"Positively identified embedded store permits an actual bare X");
        int[] plus=blank();icon(plus,316,44,28,14,false,true);expect(VisualClosePolicy.find(plus,W,H)==null,"A plus is not a cross");
        expect(VisualClosePolicy.storeClose(plus,W,H)==null,"Store context still refuses a plus");
        int[] middle=blank();icon(middle,180,320,28,14,true,true);expect(VisualClosePolicy.find(middle,W,H)==null,"Never infer a center-of-ad X");
        int[] bottom=blank();icon(bottom,316,550,28,14,true,true);expect(VisualClosePolicy.find(bottom,W,H)==null,"Never infer a bottom creative X");
        int[] ambiguous=blank();icon(ambiguous,316,44,28,14,true,true);icon(ambiguous,44,44,28,14,true,true);expect(VisualClosePolicy.find(ambiguous,W,H)==null,"Two separate X controls are ambiguous");
        expect(c.same(new VisualClosePolicy.Candidate(c.x,c.y,c.r,99)),"Two frames can confirm exactly the same measured geometry");expect(!c.same(new VisualClosePolicy.Candidate(c.x+2,c.y,c.r,c.score)),"Moving cross cannot use the previous frame proof");
        expect(VisualClosePolicy.find(new int[10],W,H)==null,"Mismatched buffers rejected");
        expect(VisualClosePolicy.find(faint(true,false,false),W,H)!=null,"Strong thin X with a symmetric faint circular outline is supported");
        expect(VisualClosePolicy.find(faint(false,false,false),W,H)==null,"A bright thin text X without the faint circle is rejected");
        expect(VisualClosePolicy.find(faint(false,false,true),W,H)==null,"Low JPEG-like noise cannot manufacture a circle around an X");
        expect(VisualClosePolicy.find(faint(true,true,false),W,H)==null,"Faint circle does not turn a plus into a cross");
        System.out.println("VisualClosePolicyTest: "+checks+" checks passed");
    }
}