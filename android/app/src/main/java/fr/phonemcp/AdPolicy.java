package fr.phonemcp;

import java.text.Normalizer;
import java.util.*;

/** Bounded ad navigation rules, never reward mutation or timer bypass. */
final class AdPolicy {
    static final int MAX_ACTIONS=16;static final long TARGET_STABLE_MS=150,POLL_MS=250,VISUAL_POLL_MS=650;
    static String normalize(String value){return Normalizer.normalize(value==null?"":value,Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT).replace("ß","ss").replace('’','\'').replaceAll("\\s+"," ").trim();}
    private static final Set<String> CLOSE=new HashSet<>(Arrays.asList("x","×","✕","✖","close","close ad","close advertisement","dismiss ad","fermer","fermer la publicite","fermer l'annonce","schliessen","cerrar","cerrar anuncio","chiudi","fechar","sluiten","zamknij","закрыть","关闭","閉じる","닫기","اغلاق"));
    private static final Set<String> SKIP=new HashSet<>(Arrays.asList("ignorer","skip","skip ad","skip video","passer","passer l'annonce","passer la publicite","ignorer l'annonce","uberspringen","saltar","saltar anuncio","salta","pular","overslaan","pomin","пропустить","跳过","スキップ","건너뛰기"));
    private static final Set<String> KEEP=new HashSet<>(Arrays.asList("continue watching","keep watching","resume video","continuer a regarder","continuer la video","reprendre la video","weiter ansehen","seguir viendo","continuar assistindo","continua a guardare"));
    static String kind(String text,String description,String viewId,boolean warning){
        String a=normalize(text),b=normalize(description),id=normalize(viewId);if(KEEP.contains(a)||KEEP.contains(b))return warning?"continue_watching":null;
        if(warning)return null;
        if(CLOSE.contains(a)||CLOSE.contains(b))return "close";
        if(SKIP.contains(a)||SKIP.contains(b))return "skip";
        String suffix=id.substring(Math.max(id.lastIndexOf('/'),id.lastIndexOf(':'))+1);
        if(suffix.matches("(?:ad|ads|video|rewarded|mraid|vungle|applovin|unityads|endcard)[_.-]*(?:close|closebutton|close_button|close_btn)|(?:close|closebutton|close_button|close_btn)[_.-]*(?:ad|ads|video|endcard)"))return "close";
        if(suffix.matches("(?:ad|ads|video|rewarded)[_.-]*(?:skip|skip_button|skip_btn)"))return "skip";
        return null;
    }
    static boolean warning(String text){String n=normalize(text);return n.contains("perdrez la recompense")||n.contains("perdrez votre recompense")||n.contains("losing the reward")||n.contains("losing your reward")||n.contains("stop watching")||n.contains("quitter la video")||n.contains("lose your reward")||n.contains("lose the reward")||n.contains("won't receive")||n.contains("wont receive")||n.contains("perdre votre recompense")||n.contains("perdre la recompense")||n.contains("vous ne recevrez pas")||n.contains("reward will be lost")||n.contains("recompense sera perdue");}
    static boolean crossLabel(String value){return Arrays.asList("x","×","✕","✖").contains(normalize(value));}
    static boolean smallCorner(int l,int t,int r,int b,int width,int height){return WindowGeometryPolicy.bounds(l,t,r,b,width,height)&&r-l<=120&&b-t<=120&&((t+b)/2<height/4||(t+b)/2>height*3/4)&&((l+r)/2<width/4||(l+r)/2>3*width/4);}
    static boolean countdown(String text){String value=normalize(text);if(!(value.contains("remaining")||value.contains("restante")||value.contains("skip in")||value.contains("close in")||value.contains("fermer dans")||value.contains("passer dans")||value.contains("disponible dans")||value.contains("reward in")))return false;return value.matches(".*(?:[1-9][0-9]*|0[.,][0-9]*[1-9][0-9]*).*");}
    static int windowSelector(String query,String mode){if(query==null||!"exact".equals(mode)||!query.matches("[1-9][0-9]{0,9}"))return -1;try{long value=Long.parseLong(query);return value<=Integer.MAX_VALUE?(int)value:-1;}catch(NumberFormatException invalid){return -1;}}
    static boolean externalUiAllowed(String op,boolean active){return !active||Arrays.asList("status","snapshot","screenshot","node_details").contains(op);}
    static boolean matches(String actual,String query,String mode){if(actual==null||query==null||query.isEmpty())return false;String a=normalize(actual),q=normalize(query);if(q.isEmpty())return false;return "contains".equals(mode)?a.contains(q):a.equals(q);}
    static boolean valid(long timeout,int maxActions){return timeout>=1000&&timeout<=180000&&maxActions>=1&&maxActions<=MAX_ACTIONS;}
    static int priority(String kind){return "continue_watching".equals(kind)?3:"close".equals(kind)?2:"skip".equals(kind)?1:0;}
    static boolean nativeControl(boolean web,int l,int t,int r,int b,int width,int height){return web&&parentSafe(l,t,r,b,width,height);}
    static boolean parentSafe(int l,int t,int r,int b,int width,int height){return WindowGeometryPolicy.bounds(l,t,r,b,width,height)&&(long)(r-l)*(b-t)<=(long)width*height/3;}
}