package fr.phonemcp;

import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.Arrays;

/** Revalidate the exact observed target, rather than invalidating it for sibling updates. */
final class NodeIdentity {
    static boolean protectedAncestor(AccessibilityNodeInfo node){
        AccessibilityNodeInfo parent=node.getParent();
        try{for(int depth=0;parent!=null&&depth<64;depth++){
            if(parent.isPassword())return true;
            AccessibilityNodeInfo next=parent.getParent();parent.recycle();parent=next;
        }return parent!=null;}finally{if(parent!=null)parent.recycle();}
    }
    private static String value(CharSequence text){return text==null?"":text.toString();}
    static String of(AccessibilityNodeInfo node){
        return Arrays.asList(value(node.getPackageName()),node.getWindowId(),value(node.getClassName()),
            node.getViewIdResourceName(),value(node.getText()),value(node.getContentDescription()),
            node.isPassword(),node.isEnabled(),node.isEditable(),node.isClickable(),node.isLongClickable(),
            node.isScrollable(),node.isCheckable(),node.isChecked(),node.isSelected()).toString();
    }
}
