package fr.phonemcp;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Pure policy: animations do not change a stable target's identity or display ownership. */
final class CoordinateGuard {
    static final long MAX_VISUAL_AGE_MS=30000;
    static final class Box {
        final int left,top,right,bottom;
        Box(int l,int t,int r,int b){left=l;top=t;right=r;bottom=b;}
        boolean valid(){return right>left&&bottom>top;}
        boolean contains(int x,int y){return x>=left&&x<right&&y>=top&&y<bottom;}
        boolean covers(Path p){return contains(p.x,p.y)&&contains(p.toX,p.toY);}
        boolean intersects(Path p){return Math.min(p.x,p.toX)<right&&Math.max(p.x,p.toX)>=left&&Math.min(p.y,p.toY)<bottom&&Math.max(p.y,p.toY)>=top;}
        @Override public boolean equals(Object value){if(!(value instanceof Box))return false;Box b=(Box)value;return left==b.left&&top==b.top&&right==b.right&&bottom==b.bottom;}
        @Override public int hashCode(){return Objects.hash(left,top,right,bottom);}
    }
    static final class Path {
        final int x,y,toX,toY;
        Path(int x,int y,int tx,int ty){this.x=x;this.y=y;toX=tx;toY=ty;}
    }
    static final class Element {
        final String path,layout,identity;
        final Box bounds;
        final boolean semantic,graphical;
        Element(String path,String layout,String identity,Box bounds,boolean semantic,boolean graphical){this.path=path;this.layout=layout;this.identity=identity;this.bounds=bounds;this.semantic=semantic;this.graphical=graphical;}
    }
    static final class Frame {
        final String pkg;
        final int displayId,windowId,width,height;
        final Box bounds;
        final List<Element> elements;
        final boolean complete;
        Frame(String pkg,int displayId,int windowId,int width,int height,Box bounds,List<Element> elements,boolean complete){this.pkg=pkg;this.displayId=displayId;this.windowId=windowId;this.width=width;this.height=height;this.bounds=bounds;this.elements=new ArrayList<>(elements);this.complete=complete;}
        boolean graphical(){for(Element e:elements)if(e.graphical)return true;return false;}
    }
    static boolean graphicalSurface(String className,int children,boolean hasText,boolean hasDescription,boolean editable,boolean actionable,Box bounds,long rootArea){
        if(editable||bounds==null||!bounds.valid())return false;
        String name=className==null?"":className,lower=name.toLowerCase(java.util.Locale.ROOT);
        if(lower.contains("surfaceview")||lower.contains("textureview")||lower.contains("webview")||lower.contains("unityplayer")||lower.contains("glsurface")||lower.contains("gameview"))return true;
        if(children!=0||hasText)return false;
        // Unity can expose its render host as an empty FrameLayout accessibility leaf.
        boolean emptyRenderHost=name.equals("android.widget.FrameLayout")&&!hasDescription&&!actionable;
        boolean canvasClass=emptyRenderHost||name.equals("android.view.View")||(!name.isEmpty()&&!name.startsWith("android.widget.")&&!name.startsWith("androidx.")&&!name.endsWith("Layout"));
        if(!canvasClass)return false;
        long area=(long)(bounds.right-bounds.left)*(bounds.bottom-bounds.top);
        // Descriptions are valid accessibility labels for canvas/chart views, not native actions.
        // A described non-actionable leaf still needs visual evidence when it is a substantial area.
        if(!actionable&&bounds.right-bounds.left>=96&&bounds.bottom-bounds.top>=64&&area>=Math.max(4096L,rootArea/12))return true;
        return !hasDescription&&area>=Math.max(4096L,rootArea/3);
    }
    private Frame observed,visual;
    private long visualAt=-1;
    void clear(){observed=null;visual=null;visualAt=-1;}
    void observe(Frame frame){observed=frame;visual=null;visualAt=-1;}
    boolean visual(Frame frame,long now){if(!sameFrame(observed,frame)||!observed.complete||!frame.complete)return false;visual=frame;visualAt=now;return true;}
    boolean graphical(){return observed!=null&&observed.graphical();}
    String validate(Frame current,List<Path> paths,boolean contentChanged,long now){
        if(!sameFrame(observed,current))return "La fenêtre, l’affichage ou ses dimensions ont changé. Relisez l’écran. expected="+numericFrame(observed)+" actual="+numericFrame(current);
        if(!observed.complete||!current.complete)return "La structure de la fenêtre est trop complexe pour vérifier ce geste.";
        for(Path path:paths){
            if(!observed.bounds.covers(path))return "Le geste dépasse la fenêtre observée.";
            if(!targetLayout(observed,path).equals(targetLayout(current,path)))return "La disposition de la cible ou du trajet a changé. Relisez avant le geste.";
            List<String> before=anchors(observed,path),after=anchors(current,path);
            if(!before.equals(after))return "Le texte, l’état ou la cible du geste a changé. Relisez l’écran.";
            if(!before.isEmpty())continue;
            boolean graphics=coveredByGraphics(observed,path)&&coveredByGraphics(current,path);
            if(graphics){
                if(visual==null||visualAt<0||now<visualAt||now-visualAt>MAX_VISUAL_AGE_MS||!sameFrame(visual,current)||!targetLayout(visual,path).equals(targetLayout(current,path))||!anchors(visual,path).equals(after))
                    return "Cette zone animée nécessite une capture récente liée au même snapshot avant le geste.";
            }else if(contentChanged)return "Le contenu de la zone a changé. Relisez avant le geste par coordonnées.";
        }
        return null;
    }
    static String targetDigest(Frame frame,List<Path> paths){
        if(frame==null||!frame.complete||paths==null||paths.isEmpty())return null;
        StringBuilder proof=new StringBuilder();part(proof,frame.pkg);part(proof,frame.displayId+","+frame.windowId+","+frame.width+","+frame.height);part(proof,box(frame.bounds));
        for(Path path:paths){
            part(proof,path.x+","+path.y+","+path.toX+","+path.toY);
            for(String layout:targetLayout(frame,path))part(proof,layout);
            for(String anchor:anchors(frame,path))part(proof,anchor);
        }
        try{
            byte[] digest=java.security.MessageDigest.getInstance("SHA-256").digest(proof.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));StringBuilder hex=new StringBuilder();for(byte value:digest)hex.append(String.format(java.util.Locale.ROOT,"%02x",value));return hex.toString();
        }catch(java.security.NoSuchAlgorithmException unavailable){throw new IllegalStateException(unavailable);}
    }
    private static String numericFrame(Frame frame){return frame==null?"null":"{display:"+frame.displayId+",window:"+frame.windowId+",size:["+frame.width+","+frame.height+"],bounds:"+(frame.bounds==null?"null":"["+box(frame.bounds)+"]")+"}";}
    private static String box(Box box){return box.left+","+box.top+","+box.right+","+box.bottom;}
    private static void part(StringBuilder out,String value){String text=value==null?"":value;out.append(text.length()).append(':').append(text);}
    private static boolean sameFrame(Frame a,Frame b){return a!=null&&b!=null&&a.pkg!=null&&a.pkg.equals(b.pkg)&&a.displayId==b.displayId&&a.windowId>=0&&a.windowId==b.windowId&&a.width>0&&a.height>0&&a.width==b.width&&a.height==b.height&&a.bounds.valid()&&a.bounds.equals(b.bounds);}
    private static List<String> targetLayout(Frame frame,Path path){
        List<String> result=new ArrayList<>();
        for(Element e:frame.elements)if(e.bounds.intersects(path)){
            // Structural wrappers and sibling indices are not a hit target. Preserve the
            // relative order of intersecting leaves/actions so overlapping targets cannot swap.
            boolean container=false;
            if(!e.semantic&&!e.graphical)for(Element child:frame.elements)if(child.path.startsWith(e.path+".")){container=true;break;}
            if(container)continue;
            StringBuilder entry=new StringBuilder();part(entry,e.layout);part(entry,box(e.bounds));part(entry,e.graphical?"graphic":"native");part(entry,e.semantic?"semantic":"structure");result.add(entry.toString());
        }
        return result;
    }
    private static List<String> anchors(Frame frame,Path path){
        List<String> result=new ArrayList<>();
        for(Element e:frame.elements)if(e.semantic&&!e.graphical&&e.bounds.intersects(path)){
            // A clickable full-screen wrapper is not a semantic target inside its canvas child.
            boolean graphicParent=false;for(Element g:frame.elements)if(g.graphical&&g.bounds.covers(path)&&g.path.startsWith(e.path+".")){graphicParent=true;break;}
            if(!graphicParent){StringBuilder entry=new StringBuilder();part(entry,e.layout);part(entry,box(e.bounds));part(entry,e.identity);result.add(entry.toString());}
        }
        return result;
    }
    private static boolean coveredByGraphics(Frame frame,Path path){for(Element e:frame.elements)if(e.graphical&&e.bounds.covers(path))return true;return false;}
}
