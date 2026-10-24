package fr.phonemcp;

import android.os.Bundle;
import android.view.accessibility.AccessibilityNodeInfo;
import org.json.*;

final class NodeEditing {
    static boolean edit(AccessibilityNodeInfo node,JSONObject args)throws Exception{
        if(!node.isEditable())throw new IllegalArgumentException("This node is not editable.");
        String original=node.getText()==null?"":node.getText().toString(),text=args.optString("text"),mode=args.getString("mode"),next;
        switch(mode){
            case "clear":next="";break;
            case "replace":next=text;break;
            case "append":next=original+text;break;
            case "insert":int p=args.has("position")?args.getInt("position"):node.getTextSelectionEnd();if(p<0||p>original.length())throw new IllegalArgumentException("Invalid insertion position.");next=original.substring(0,p)+text+original.substring(p);break;
            default:throw new IllegalArgumentException("Unknown text edit mode.");
        }
        if(next.length()>4000)throw new IllegalArgumentException("Text is limited to 4000 characters.");
        Bundle data=new Bundle();data.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,next);
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,data);
    }
    static JSONObject details(AccessibilityNodeInfo node,String id)throws Exception{
        return new JSONObject().put("nodeId",id).put("text",bounded(node.getText())).put("description",bounded(node.getContentDescription()))
            .put("viewId",node.getViewIdResourceName()).put("class",bounded(node.getClassName())).put("editable",node.isEditable()).put("visible",node.isVisibleToUser())
            .put("selectionStart",node.getTextSelectionStart()).put("selectionEnd",node.getTextSelectionEnd());
    }
    private static String bounded(CharSequence value){if(value==null)return "";return value.subSequence(0,Math.min(12000,value.length())).toString();}
}
