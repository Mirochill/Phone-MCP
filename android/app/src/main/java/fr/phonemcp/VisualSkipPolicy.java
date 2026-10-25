package fr.phonemcp;

/** Positive filled skip triangle plus separate vertical bar, beside an observed store badge. */
final class VisualSkipPolicy {
    private static int lum(int c){return ((c>>16&255)*77+(c>>8&255)*150+(c&255)*29)>>8;}
    private static int at(int[] p,int w,int x,int y){return lum(p[y*w+x]);}
    private static boolean bright(int[] p,int w,int x,int y,int bg){return at(p,w,x,y)-bg>=75;}
    static VisualClosePolicy.Candidate beforeLabel(int[] p,int w,int h,int left,int top,int right,int bottom){
        if(p==null||p.length!=(long)w*h||left<25||right>Math.min(w/2,300)||top<12||bottom-top>60||bottom<=top||!(bottom<h/5||top>h*4/5))return null;
        VisualClosePolicy.Candidate best=null;
        for(int y=Math.max(16,top-8);y<=Math.min(h-17,bottom+8);y++)for(int x=Math.max(16,left-45);x<left-7;x++)for(int r=4;r<=9;r++){
            int base=x-r,tip=x+r-1,bar=x+r+2;if(base-3<0||bar+3>=left||y-r-3<0||y+r+3>=h)continue;
            int bg=(at(p,w,base-3,y)+at(p,w,bar+3,y)+at(p,w,x,y-r-3)+at(p,w,x,y+r+3))/4;
            if(!bright(p,w,x,y,bg)||!bright(p,w,bar,y,bg))continue;
            int hits=0,total=0;for(int dy=-r;dy<=r;dy++){total++;if(bright(p,w,bar,y+dy,bg)||bright(p,w,bar+1,y+dy,bg))hits++;}
            if(hits*100<total*85)continue;
            hits=0;total=0;for(int dx=0;dx<=2*r-2;dx++){int extent=Math.max(0,(int)Math.floor(r*(1.0-dx/(double)(2*r)))-1);for(int dy=-extent;dy<=extent;dy++){total++;if(bright(p,w,base+dx,y+dy,bg))hits++;}}
            if(hits*100<total*85)continue;
            int gap=0,samples=0;for(int dy=-r;dy<=r;dy++)if(Math.abs(dy)>2){samples++;if(at(p,w,bar-2,y+dy)-bg<45)gap++;}
            if(gap*100<samples*75)continue;
            int outside=0;for(int dy=-r;dy<=r;dy++)if(at(p,w,base-2,y+dy)-bg<45)outside++;
            if(outside*100<(2*r+1)*85)continue;
            VisualClosePolicy.Candidate found=new VisualClosePolicy.Candidate(x,y,r,200,"visual_skip");
            if(best!=null&&(Math.abs(best.x-x)>10||Math.abs(best.y-y)>10))return null;if(best==null)best=found;
        }return best;
    }

    /** Two filled right triangles + detached vertical bar inside a corner button. */
    static VisualClosePolicy.Candidate corner(int[] p,int w,int h){return corner(p,w,h,true,0,0,w,h);}
    /** A measured SDK corner container replaces the enclosing-circle evidence, never the skip glyph. */
    static VisualClosePolicy.Candidate inControl(int[] p,int w,int h,String id,int l,int t,int right,int bottom){
        if(!"base-corner-top-right".equals(id)||l<w*3/4-20||t<0||right>w||bottom>h/4||right<=l||bottom<=t||right-l>180||bottom-t>180)return null;
        return corner(p,w,h,false,l,t,right,bottom);
    }
    private static VisualClosePolicy.Candidate corner(int[] p,int w,int h,boolean requireCircle,int l,int t,int right,int bottom){
        if(p==null||p.length!=(long)w*h||w<100||h<100)return null;
        VisualClosePolicy.Candidate best=null;
        for(int y=20;y<Math.min(220,h/4);y++)for(int x=Math.max(w*3/4,40);x<w-30;x++)for(int r=5;r<=12;r++){
            int base=x-2*r,second=x,bar=x+2*r+2;
            if(base-4<0||bar+4>=w||y-r-4<0||base<l||bar+1>=right||y-r<t||y+r>=bottom)continue;
            int a=at(p,w,base-3,y),b=at(p,w,bar+4,y),c0=at(p,w,x,y-r-4),d0=at(p,w,x,y+r+4);if(Math.max(Math.max(a,b),Math.max(c0,d0))-Math.min(Math.min(a,b),Math.min(c0,d0))>28)continue;int bg=(a+b+c0+d0)/4;
            if(!bright(p,w,base,y,bg)||!bright(p,w,second,y,bg)||!bright(p,w,bar,y,bg))continue;
            int hits=0,total=0;
            for(int dy=-r+1;dy<r;dy++){total++;if(bright(p,w,bar,y+dy,bg)||bright(p,w,bar+1,y+dy,bg))hits++;}
            if(hits*100<total*90)continue;
            hits=0;total=0;
            for(int start:new int[]{base,second})for(int dx=1;dx<2*r-2;dx++){int extent=Math.max(0,(int)(r*(1-dx/(double)(2*r)))-2);for(int dy=-extent;dy<=extent;dy++){total++;if(bright(p,w,start+dx,y+dy,bg))hits++;}}
            if(total==0||hits*100<total*90)continue;
            int gaps=0;total=0;for(int dy:new int[]{-r+1,r-1})for(int dx:new int[]{r,2*r-1,3*r,4*r+1}){total++;if(at(p,w,base+dx,y+dy)-bg<45)gaps++;}
            if(gaps*100<total*75)continue;
            boolean enclosed=!requireCircle;int cx=x+r/2;if(cx<l||cx>=right||y<t||y>=bottom)continue;
            for(int q=2*r+10;!enclosed&&q<=Math.min(65,4*r+24);q+=2){if(cx-q<0||cx+q>=w||y-q<0||y+q>=h)break;int count=0;for(int[] d:new int[][]{{q,0},{-q,0},{0,q},{0,-q}})if(Math.abs(at(p,w,cx+d[0],y+d[1])-bg)>=25)count++;if(count>=3){enclosed=true;break;}}
            if(!enclosed)continue;
            VisualClosePolicy.Candidate c=new VisualClosePolicy.Candidate(cx,y,r,200,"visual_skip_corner");
            if(best!=null&&(Math.abs(best.x-c.x)>12||Math.abs(best.y-c.y)>12))return null;
            if(best==null)best=c;
        }return best;
    }
}
