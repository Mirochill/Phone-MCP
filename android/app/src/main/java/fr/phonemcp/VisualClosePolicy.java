package fr.phonemcp;

/** Positive X-glyph and enclosing-button pixel evidence. Never guesses a corner coordinate. */
final class VisualClosePolicy {
    static final class Candidate {final int x,y,r,score;final String kind;Candidate(int x,int y,int r,int score){this(x,y,r,score,"visual_close");}Candidate(int x,int y,int r,int score,String kind){this.x=x;this.y=y;this.r=r;this.score=score;this.kind=kind;}String key(){return kind+","+x+","+y+","+r;}boolean same(Candidate other){return other!=null&&kind.equals(other.kind)&&x==other.x&&y==other.y&&r==other.r;}}
    private static int lum(int color){return ((color>>16&255)*77+(color>>8&255)*150+(color&255)*29)>>8;}
    private static int pixel(int[] pixels,int width,int x,int y){return lum(pixels[y*width+x]);}
    private static int diagonal(int[] p,int w,int x,int y,int dx,int dy,int expected,int bg){int best=0;for(int i=-1;i<=1;i++){int v=pixel(p,w,x+dx+i,y+dy);int contrast=(v-bg)*expected;if(contrast>best)best=contrast;}return best;}
    private static final double[] RAY_X={1,.70710678,0,-.70710678,-1,-.70710678,0,.70710678};
    private static final double[] RAY_Y={0,.70710678,1,.70710678,0,-.70710678,-1,-.70710678};
    /** A faint outline still needs a narrow, co-centered radial peak on almost every ray. */
    private static boolean faintRing(int[] p,int w,int h,int x,int y,int arm){
        for(int radius=arm+8;radius<=Math.min(64,arm*3+20);radius++){
            if(x-radius-5<0||y-radius-5<0||x+radius+5>=w||y+radius+5>=h)break;
            int mask=0;
            for(int ray=0;ray<8;ray++){
                int inside=pixel(p,w,x+(int)Math.round((radius-5)*RAY_X[ray]),y+(int)Math.round((radius-5)*RAY_Y[ray]));
                int outside=pixel(p,w,x+(int)Math.round((radius+5)*RAY_X[ray]),y+(int)Math.round((radius+5)*RAY_Y[ray]));
                if(Math.abs(inside-outside)>12)continue;
                int peak=0;for(int delta=-2;delta<=2;delta++){int v=pixel(p,w,x+(int)Math.round((radius+delta)*RAY_X[ray]),y+(int)Math.round((radius+delta)*RAY_Y[ray]));peak=Math.max(peak,v-Math.max(inside,outside));}
                if(peak>=5)mask|=1<<ray;
            }
            int pairs=0;for(int ray=0;ray<4;ray++)if((mask&(1<<ray))!=0&&(mask&(1<<(ray+4)))!=0)pairs++;
            if(Integer.bitCount(mask)>=7&&pairs>=3)return true;
        }return false;
    }
    private static Candidate prove(int[] p,int w,int h,int x,int y,int r,boolean store){
        int margin=r+9;if(x-margin<0||y-margin<0||x+margin>=w||y+margin>=h)return null;
        int a=pixel(p,w,x-r-2,y),b=pixel(p,w,x+r+2,y),c=pixel(p,w,x,y-r-2),d=pixel(p,w,x,y+r+2),bg=(a+b+c+d)/4;
        if(Math.max(Math.max(a,b),Math.max(c,d))-Math.min(Math.min(a,b),Math.min(c,d))>28)return null;
        int center=pixel(p,w,x,y),sign=center>=bg?1:-1;if(Math.abs(center-bg)<75)return null;
        int hits=0,total=0,score=0;
        for(int k=3;k<=r;k++){for(int sx=-1;sx<=1;sx+=2)for(int sy=-1;sy<=1;sy+=2){int delta=diagonal(p,w,x,y,sx*k,sy*k,sign,bg);total++;if(delta>=65)hits++;score+=Math.min(255,delta);}}
        if(hits*100<total*85)return null;
        // A plus, a block, plain text or an unenclosed transparent X is insufficient proof.
        int empty=0,axis=0;for(int k=3;k<=r;k++){for(int direction=0;direction<4;direction++){int dx=direction==0?k:direction==1?-k:0,dy=direction==2?k:direction==3?-k:0;axis++;if(Math.abs(pixel(p,w,x+dx,y+dy)-bg)<45)empty++;}}
        if(empty*100<axis*75)return null;
        boolean enclosed=false;for(int q=r+8;q<=Math.min(70,r*3+16);q+=2){if(x-q<0||y-q<0||x+q>=w||y+q>=h)break;int enclosure=0;for(int direction=0;direction<4;direction++){int dx=direction==0?q:direction==1?-q:0,dy=direction==2?q:direction==3?-q:0;if(Math.abs(pixel(p,w,x+dx,y+dy)-bg)>=35)enclosure++;}if(enclosure>=3){enclosed=true;break;}}
        if(!enclosed&&score/Math.max(1,total)>=140)enclosed=faintRing(p,w,h,x,y,r);
        if(!enclosed&&!store)return null;return new Candidate(x,y,r,score/Math.max(1,total),store?"visual_store_close":"visual_close");
    }
    static Candidate find(int[] pixels,int width,int height){return find(pixels,width,height,false);}
    /** Only called for a positively identified embedded store endcard, never a generic ad. */
    static Candidate storeClose(int[] pixels,int width,int height){return find(pixels,width,height,true);}
    private static Candidate find(int[] pixels,int width,int height,boolean store){
        if(width<80||height<80||pixels==null||pixels.length!=(long)width*height)return null;
        int edge=Math.min(180,width/4),top=Math.min(220,height/4);Candidate best=null;
        for(int y=18;y<top;y+=2)for(int x=18;x<width-18;x+=2){if(x>=edge&&x<width-edge)continue;
            Candidate hit=null;for(int r=5;r<=24;r++){Candidate candidate=prove(pixels,width,height,x,y,r,store);if(candidate!=null&&(hit==null||candidate.score>hit.score))hit=candidate;}
            if(hit==null)continue;
            if(best!=null&&Math.abs(best.x-hit.x)>12)return null;
            if(best!=null&&Math.abs(best.y-hit.y)>12)return null;
            if(best==null||hit.score>best.score)best=hit;
        }return best;
    }
}