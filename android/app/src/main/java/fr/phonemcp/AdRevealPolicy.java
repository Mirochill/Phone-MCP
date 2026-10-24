package fr.phonemcp;

/** Bounded show-controls probes, only on observed graphical ad pages without timers/warnings. */
final class AdRevealPolicy {
    static boolean badge(String text){return "google play".equals(AdPolicy.normalize(text))||"app store".equals(AdPolicy.normalize(text));}
    static boolean timer(String text,int l,int t,int r,int b,int width,int height){String n=AdPolicy.normalize(text);return n.matches("[1-9][0-9]{0,2}")&&Integer.parseInt(n)<=180&&AdPolicy.smallCorner(l,t,r,b,width,height);}
    static boolean mayReveal(boolean graphicalAd,boolean warning,boolean timer,int count,long since,long now){return graphicalAd&&!warning&&!timer&&count<2&&since>0&&now-since>=(count==0?1800:8000);}
}
