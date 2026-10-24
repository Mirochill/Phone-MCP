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
}
