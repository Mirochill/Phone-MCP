package fr.phonemcp;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class CoordinateGuardTest {
    private static int checks;
    private static void expect(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    private static final CoordinateGuard.Box screen=new CoordinateGuard.Box(0,0,720,1280);
    private static CoordinateGuard.Element e(String path,String layout,String text,CoordinateGuard.Box box,boolean semantic,boolean graphic){return new CoordinateGuard.Element(path,layout,text,box,semantic,graphic);}
    private static CoordinateGuard.Frame frame(int display,int window,int width,CoordinateGuard.Element...nodes){return new CoordinateGuard.Frame("com.example.game",display,window,width,1280,screen,Arrays.asList(nodes),true);}
    private static CoordinateGuard.Element canvas(){return e("0.0","SurfaceView","",screen,false,true);}
    private static List<CoordinateGuard.Path> tap(int x,int y){return Collections.singletonList(new CoordinateGuard.Path(x,y,x,y));}
    private static boolean allowed(CoordinateGuard guard,CoordinateGuard.Frame frame,List<CoordinateGuard.Path> path,boolean changed,long now){return guard.validate(frame,path,changed,now)==null;}
    public static void main(String[] args){
        CoordinateGuard guard=new CoordinateGuard();CoordinateGuard.Frame game=frame(40,7,720,canvas());
        guard.observe(game);
        expect(!allowed(guard,game,tap(300,600),true,1000),"An animated canvas needs a matching visual capture");
        expect(!allowed(guard,game,tap(300,600),false,1000),"A canvas never relies solely on missing accessibility events");
        expect(guard.visual(game,1000),"A matching capture arms visual gestures");
        expect(allowed(guard,game,tap(300,600),true,1200),"Frame animation alone does not invalidate a captured canvas");
        expect(allowed(guard,game,Collections.singletonList(new CoordinateGuard.Path(200,500,500,900)),true,1300),"Canvas swipes use the same bounded observation");
        expect(!allowed(guard,game,tap(300,600),true,31001),"Visual captures expire");
        expect(!allowed(guard,game,tap(300,600),true,999),"A future-dated visual capture is rejected");
        expect(!allowed(guard,frame(41,7,720,canvas()),tap(300,600),true,1200),"Cross-session display confusion is rejected");
        expect(!allowed(guard,frame(40,8,720,canvas()),tap(300,600),true,1200),"A new same-app window needs a fresh observation");
        expect(!allowed(guard,frame(40,7,800,canvas()),tap(300,600),true,1200),"Resized or rotated display dimensions are rejected");
        expect(!allowed(guard,game,tap(720,600),true,1200),"Out of bounds gestures are rejected");
        CoordinateGuard.Frame otherApp=new CoordinateGuard.Frame("com.example.other",40,7,720,1280,screen,Collections.singletonList(canvas()),true);
        expect(!allowed(guard,otherApp,tap(300,600),true,1200),"Window identity cannot override a package mismatch");
        expect(!guard.visual(otherApp,1200),"A screenshot of another app cannot arm the original app");
        CoordinateGuard.Element close=e("0.1","Button:close","Close",new CoordinateGuard.Box(600,20,700,120),true,false);
        CoordinateGuard.Element timer=e("0.2","TextView:timer","10",new CoordinateGuard.Box(200,40,400,100),true,false);
        CoordinateGuard.Element timer9=e("0.2","TextView:timer","9",timer.bounds,true,false);
        CoordinateGuard.Frame ad=frame(40,7,720,canvas(),close,timer),animated=frame(40,7,720,canvas(),close,timer9);
        guard.observe(ad);
        expect(allowed(guard,animated,tap(650,60),true,1000),"An unchanged close button is usable while an unrelated timer changes");
        expect(!allowed(guard,animated,tap(300,60),true,1000),"Coordinates cannot click a target whose text changed");
        CoordinateGuard.Element buy=e("0.1","Button:close","Buy",close.bounds,true,false);
        expect(!allowed(guard,frame(40,7,720,canvas(),buy,timer),tap(650,60),true,1000),"A button label change remains protected");
        CoordinateGuard.Element moved=e("0.1","Button:close","Close",new CoordinateGuard.Box(500,20,600,120),true,false);
        expect(!allowed(guard,frame(40,7,720,canvas(),moved,timer),tap(650,60),false,1000),"Geometry changes are detected even without an event");
        CoordinateGuard.Element disabled=e("0.1","Button:close:disabled","Close",close.bounds,true,false);
        expect(!allowed(guard,frame(40,7,720,canvas(),disabled,timer),tap(650,60),false,1000),"Disabled/replaced targets cannot reuse coordinates");
        CoordinateGuard.Element overlay=e("0.3","Dialog","Confirm",screen,true,false);
        expect(!allowed(guard,frame(40,7,720,canvas(),close,timer,overlay),tap(650,60),true,1000),"New dialog structure invalidates the old target");
        CoordinateGuard.Frame blank=frame(40,7,720,e("0","Layout","",screen,false,false));guard.observe(blank);
        expect(!allowed(guard,blank,tap(300,600),true,1000),"Ordinary unanchored screens keep content-change protection");
        expect(allowed(guard,blank,tap(300,600),false,1000),"The existing stable ordinary coordinate path remains available");
        CoordinateGuard.Element wrapper=e("0","Clickable wrapper","",screen,true,false);CoordinateGuard.Frame wrappedGame=frame(40,7,720,wrapper,canvas());guard.observe(wrappedGame);
        expect(!allowed(guard,wrappedGame,tap(300,600),true,1000),"Clickable canvas wrappers do not bypass visual observation");
        guard.visual(wrappedGame,1000);expect(allowed(guard,wrappedGame,tap(300,600),true,1200),"A captured wrapped canvas remains usable");
        guard.clear();expect(!allowed(guard,wrappedGame,tap(300,600),false,1200),"Consumed/cancelled observations cannot be replayed");
        guard.observe(game);guard.visual(game,1000);CoordinateGuard.Frame incomplete=new CoordinateGuard.Frame("com.example.game",40,7,720,1280,screen,Collections.singletonList(canvas()),false);
        expect(!allowed(guard,incomplete,tap(300,600),true,1200),"Partial or protected trees remain fail-closed");
        CoordinateGuard.Box describedCanvas=new CoordinateGuard.Box(21,169,699,421);
        expect(CoordinateGuard.graphicalSurface("android.view.View",0,false,true,false,false,describedCanvas,720L*1280),"The real described 678x252 canvas is a visual area despite occupying less than one third");
        expect(!CoordinateGuard.graphicalSurface("android.widget.TextView",0,false,true,false,false,describedCanvas,720L*1280),"A native described label is not reclassified as a canvas");
        expect(!CoordinateGuard.graphicalSurface("android.view.View",0,false,true,false,false,new CoordinateGuard.Box(20,20,60,60),720L*1280),"Small described decorative icons remain semantic");
        expect(!CoordinateGuard.graphicalSurface("android.view.View",0,false,true,false,true,describedCanvas,720L*1280),"An actionable described generic button keeps semantic target validation");
        expect(!CoordinateGuard.graphicalSurface("android.view.View",0,true,true,false,false,describedCanvas,720L*1280),"Ordinary text-bearing nodes remain semantic");
        expect(!CoordinateGuard.graphicalSurface("android.view.View",1,false,true,false,false,describedCanvas,720L*1280),"Ordinary described containers with children are not opaque canvas leaves");
        expect(!CoordinateGuard.graphicalSurface("android.webkit.WebView",0,false,false,true,false,screen,720L*1280),"An editable WebView cannot bypass editable target checks");
        expect(CoordinateGuard.graphicalSurface("android.view.SurfaceView",0,false,true,false,false,new CoordinateGuard.Box(0,0,80,50),720L*1280),"A known SurfaceView remains explicitly graphical");
        String closeDigest=CoordinateGuard.targetDigest(ad,tap(650,60));
        expect(closeDigest!=null&&closeDigest.length()==64,"The IPC proof is a fixed SHA-256 digest, not full app text");
        expect(closeDigest.equals(CoordinateGuard.targetDigest(animated,tap(650,60))),"A timer outside the trajectory may change while accessibility binds");
        expect(!closeDigest.equals(CoordinateGuard.targetDigest(frame(40,7,720,canvas(),moved,timer),tap(650,60))),"A target moved during accessibility binding is refused");
        expect(!closeDigest.equals(CoordinateGuard.targetDigest(frame(40,7,720,canvas(),buy,timer),tap(650,60))),"A target renamed during binding is refused even at the same position");
        CoordinateGuard.Element newTarget=e("0.4","Button:new","New target",new CoordinateGuard.Box(260,500,360,700),true,false);
        expect(!CoordinateGuard.targetDigest(game,tap(300,600)).equals(CoordinateGuard.targetDigest(frame(40,7,720,canvas(),newTarget),tap(300,600))),"A new target entering the trajectory changes the binding-time proof");
        CoordinateGuard.Element animatedPixels=e("0.0","SurfaceView","different pixels or canvas description",screen,false,true);
        expect(CoordinateGuard.targetDigest(game,tap(300,600)).equals(CoordinateGuard.targetDigest(frame(40,7,720,animatedPixels),tap(300,600))),"Graphical pixel animation does not invalidate the target digest");
        expect(!closeDigest.equals(CoordinateGuard.targetDigest(frame(41,7,720,canvas(),close,timer),tap(650,60))),"The proof is also bound to the precise agent display");
        expect(CoordinateGuard.targetDigest(incomplete,tap(300,600))==null,"A partial or protected frame cannot produce a usable proof");
        CoordinateGuard.Element wideScore=e("0.2","TextView:timer","999",new CoordinateGuard.Box(150,20,470,140),true,false);
        CoordinateGuard.Element extraScore=e("0.5","TextView:new-score","Score",new CoordinateGuard.Box(5,160,150,200),true,false);
        CoordinateGuard.Frame movedElsewhere=frame(40,7,720,canvas(),close,wideScore,extraScore);
        guard.observe(ad);guard.visual(ad,1000);
        expect(allowed(guard,movedElsewhere,tap(650,60),true,1200),"An off-target layout resize/addition cannot invalidate the unchanged close button");
        expect(allowed(guard,movedElsewhere,tap(300,600),true,1200),"A canvas trajectory ignores off-target HUD layout changes");
        expect(guard.visual(movedElsewhere,1300),"Off-target layout changes during capture do not disarm visual evidence");
        expect(allowed(guard,movedElsewhere,tap(300,600),true,1400),"The captured trajectory remains valid after unrelated layout updates");
        expect(CoordinateGuard.targetDigest(ad,tap(650,60)).equals(CoordinateGuard.targetDigest(movedElsewhere,tap(650,60))),"Pre-dispatch proof is limited to the hit area");
        CoordinateGuard.Element reindexedClose=e("0.8","Button:close","Close",close.bounds,true,false);
        CoordinateGuard.Frame reindexed=frame(40,7,720,canvas(),extraScore,reindexedClose,timer);
        expect(allowed(guard,reindexed,tap(650,60),true,1500),"Off-target sibling insertion may renumber the same coordinate target");
        expect(CoordinateGuard.targetDigest(ad,tap(650,60)).equals(CoordinateGuard.targetDigest(reindexed,tap(650,60))),"Coordinate proof does not depend on the sibling index");
        CoordinateGuard.Element intruder=e("0.6","Button:new","Buy",close.bounds,true,false);
        expect(!allowed(guard,frame(40,7,720,canvas(),close,timer,intruder),tap(650,60),true,1500),"A new semantic overlay inside the hit area is rejected");
        expect(!allowed(guard,frame(40,7,720,canvas(),intruder,close,timer),tap(650,60),true,1500),"An intersecting overlay cannot hide behind traversal order");
        CoordinateGuard.Frame overlap1=frame(40,7,720,canvas(),close,intruder),overlap2=frame(40,7,720,canvas(),intruder,close);
        expect(!CoordinateGuard.targetDigest(overlap1,tap(650,60)).equals(CoordinateGuard.targetDigest(overlap2,tap(650,60))),"Reordering overlapping semantic targets changes the proof");
        CoordinateGuard.Element crossedScore=e("0.2","TextView:timer","999",close.bounds,true,false);
        expect(!allowed(guard,frame(40,7,720,canvas(),close,crossedScore),tap(650,60),true,1500),"A formerly unrelated element entering the trajectory invalidates it");
        CoordinateGuard.Element container2=e("0","Layout children=2","",screen,false,false),container3=e("0","Layout children=3","",screen,false,false);
        CoordinateGuard.Frame withContainer=frame(40,7,720,container2,canvas(),close),containerUpdated=frame(40,7,720,container3,canvas(),close,extraScore);
        guard.observe(withContainer);expect(allowed(guard,containerUpdated,tap(650,60),true,1600),"Structural wrapper child count is not the coordinate target identity");
        CoordinateGuard.Element changedCanvas=e("0.0","SurfaceView","",new CoordinateGuard.Box(0,0,650,1200),false,true);
        guard.observe(game);guard.visual(game,1000);
        expect(!allowed(guard,frame(40,7,720,changedCanvas),tap(300,600),true,1500),"The actual canvas geometry still cannot move or resize");
        expect(!allowed(guard,frame(40,7,720,canvas(),e("0.3","View","",new CoordinateGuard.Box(200,500,400,700),false,false)),tap(300,600),true,1500),"A new opaque leaf entering a graphical trajectory is rejected");
        expect(!guard.visual(incomplete,1600),"A protected or incomplete capture never arms a gesture");
        expect(CoordinateGuard.graphicalSurface("android.widget.FrameLayout",0,false,false,false,false,screen,720L*1280),"An empty full-size Unity render host requires fresh visual evidence");
        expect(!CoordinateGuard.graphicalSurface("android.widget.FrameLayout",1,false,false,false,false,screen,720L*1280),"An ordinary populated layout is not a graphical leaf");
        expect(!CoordinateGuard.graphicalSurface("android.widget.FrameLayout",0,false,false,false,true,screen,720L*1280),"An actionable frame is not an empty render host");
        expect(!CoordinateGuard.graphicalSurface("android.widget.FrameLayout",0,true,false,false,false,screen,720L*1280),"A textual frame is not a render host");
        expect(!CoordinateGuard.graphicalSurface("android.widget.FrameLayout",0,false,false,true,false,screen,720L*1280),"An editable frame remains protected");
        System.out.println("PASS: "+checks+" coordinate target and animated-window regression checks");
    }
}
