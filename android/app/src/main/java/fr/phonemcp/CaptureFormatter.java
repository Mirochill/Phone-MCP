package fr.phonemcp;

import android.graphics.*;
import android.util.Base64;
import java.io.ByteArrayOutputStream;
import org.json.*;

final class CaptureFormatter {
    static JSONObject encode(Bitmap source,JSONObject args,JSONArray nodes,int originX,int originY,int display)throws Exception{
        int max=args.optInt("maxDimension",1000),quality=args.optInt("quality",75);
        if(max<320||max>1920||quality<35||quality>95)throw new IllegalArgumentException("Invalid screenshot quality or dimensions.");
        float scale=Math.min(1f,(float)max/Math.max(source.getWidth(),source.getHeight()));
        int width=Math.max(1,Math.round(source.getWidth()*scale)),height=Math.max(1,Math.round(source.getHeight()*scale));
        Bitmap resized=Bitmap.createScaledBitmap(source,width,height,true),output=resized;
        JSONArray legend=new JSONArray();
        try{
            if(args.optBoolean("annotate")){
                output=resized.copy(Bitmap.Config.ARGB_8888,true);Canvas canvas=new Canvas(output);Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
                for(int i=0;i<nodes.length()&&legend.length()<60;i++){
                    JSONObject node=nodes.getJSONObject(i);if(!node.optBoolean("visible",true)||(!node.optBoolean("clickable")&&!node.optBoolean("editable")&&!node.optBoolean("longClickable")))continue;
                    JSONArray bounds=node.optJSONArray("bounds");if(bounds==null||bounds.length()!=4)continue;
                    RectF rect=new RectF((bounds.getInt(0)-originX)*scale,(bounds.getInt(1)-originY)*scale,(bounds.getInt(2)-originX)*scale,(bounds.getInt(3)-originY)*scale);
                    if(!rect.intersect(0,0,width,height)||rect.isEmpty())continue;
                    String label=Integer.toString(legend.length()+1);paint.setColor(0xFF1267E9);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2);canvas.drawRoundRect(rect,3,3,paint);
                    float y=Math.max(18,rect.top+18),x=Math.min(width-28,Math.max(0,rect.left));paint.setStyle(Paint.Style.FILL);canvas.drawRoundRect(x,y-16,x+26,y+3,4,4,paint);paint.setColor(Color.WHITE);drawLabel(canvas,paint,label,x+4,y-13);
                    legend.put(new JSONObject().put("label",label).put("nodeId",node.getString("nodeId")).put("bounds",bounds));
                }
            }
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();output.compress(Bitmap.CompressFormat.JPEG,quality,bytes);
            return new JSONObject().put("mimeType","image/jpeg").put("data",Base64.encodeToString(bytes.toByteArray(),Base64.NO_WRAP)).put("width",width).put("height",height).put("displayId",display)
                .put("annotations",legend).put("coordinateSpace",new JSONObject().put("unit","snapshot_pixels").put("originX",originX).put("originY",originY).put("scaleX",(double)source.getWidth()/width).put("scaleY",(double)source.getHeight()/height));
        }finally{if(output!=resized)output.recycle();if(resized!=source)resized.recycle();}
    }

    // Shizuku's app_process has no application font initialization on some devices.
    // Draw numeric labels as vector segments so capture works in either runtime.
    private static void drawLabel(Canvas canvas,Paint paint,String label,float x,float y){
        final int[] masks={0x3f,0x06,0x5b,0x4f,0x66,0x6d,0x7d,0x07,0x7f,0x6f};
        final float[][] segments={{1,0,6,0},{7,1,7,5},{7,7,7,11},{1,12,6,12},{0,7,0,11},{0,1,0,5},{1,6,6,6}};
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.7f);paint.setStrokeCap(Paint.Cap.ROUND);
        for(int n=0;n<label.length();n++){
            int mask=masks[label.charAt(n)-'0'];
            for(int s=0;s<segments.length;s++)if((mask&(1<<s))!=0){float[] p=segments[s];canvas.drawLine(x+n*10+p[0],y+p[1],x+n*10+p[2],y+p[3],paint);}
        }
        paint.setStrokeCap(Paint.Cap.BUTT);paint.setStyle(Paint.Style.FILL);
    }
}
