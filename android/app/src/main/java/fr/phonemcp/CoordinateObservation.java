package fr.phonemcp;

import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Bounded fresh geometry/target observation; never examines a protected subtree. */
final class CoordinateObservation {
    static CoordinateGuard.Frame frame(AccessibilityNodeInfo root,int display,int width,int height){
        Rect area=new Rect();root.getBoundsInScreen(area);List<CoordinateGuard.Element> nodes=new ArrayList<>();int[] budget={0,0};
        collect(root,"0",nodes,budget,0,Math.max(1L,(long)area.width()*area.height()));
        return new CoordinateGuard.Frame(value(root.getPackageName()),display,root.getWindowId(),width,height,box(area),nodes,budget[1]==0);
    }
    static List<CoordinateGuard.Path> paths(List<TouchGestures.Stroke> strokes){List<CoordinateGuard.Path> result=new ArrayList<>();for(TouchGestures.Stroke stroke:strokes)result.add(new CoordinateGuard.Path(stroke.x,stroke.y,stroke.toX,stroke.toY));return result;}
    private static void collect(AccessibilityNodeInfo node,String path,List<CoordinateGuard.Element> out,int[] budget,int depth,long rootArea){
        if(node==null)return;if(++budget[0]>1000||depth>50){budget[1]=1;return;}if(node.isPassword()){budget[1]=1;return;}
        if(node.isVisibleToUser()){
            Rect bounds=new Rect();node.getBoundsInScreen(bounds);String name=value(node.getClassName());
            boolean actionable=node.isClickable()||node.isLongClickable()||node.isScrollable()||node.isCheckable();
            int visibleChildren=node.getChildCount();
            if(name.equals("android.widget.FrameLayout")&&!actionable&&value(node.getText()).isEmpty()&&value(node.getContentDescription()).isEmpty()){visibleChildren=0;for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo child=child(node,i);try{if(child!=null&&child.isVisibleToUser())visibleChildren++;}finally{if(child!=null)child.recycle();}}}
            boolean graphical=CoordinateGuard.graphicalSurface(name,visibleChildren,!value(node.getText()).isEmpty(),!value(node.getContentDescription()).isEmpty(),node.isEditable(),actionable,box(bounds),rootArea);
            boolean semantic=node.isEditable()||node.isClickable()||node.isLongClickable()||node.isScrollable()||node.isCheckable()||!value(node.getText()).isEmpty()||!value(node.getContentDescription()).isEmpty();
            String layout=Arrays.asList(name,node.getViewIdResourceName(),node.isEnabled(),node.isEditable(),node.isClickable(),node.isLongClickable(),node.isScrollable(),node.isCheckable()).toString();
            out.add(new CoordinateGuard.Element(path,layout,NodeIdentity.of(node),box(bounds),semantic,graphical));
        }
        for(int i=0;i<node.getChildCount()&&budget[1]==0;i++){AccessibilityNodeInfo child=child(node,i);try{collect(child,path+"."+i,out,budget,depth+1,rootArea);}finally{if(child!=null)child.recycle();}}
    }
    private static AccessibilityNodeInfo child(AccessibilityNodeInfo node,int index){return android.os.Build.VERSION.SDK_INT>=33?node.getChild(index,AccessibilityNodeInfo.FLAG_PREFETCH_ANCESTORS|AccessibilityNodeInfo.FLAG_PREFETCH_DESCENDANTS_DEPTH_FIRST|AccessibilityNodeInfo.FLAG_PREFETCH_UNINTERRUPTIBLE):node.getChild(index);}
    private static CoordinateGuard.Box box(Rect r){return new CoordinateGuard.Box(r.left,r.top,r.right,r.bottom);}
    private static String value(CharSequence value){return value==null?"":value.toString();}
}
