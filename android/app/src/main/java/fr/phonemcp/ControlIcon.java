package fr.phonemcp;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Small, consistent vector icons; no font-glyph or network dependency. */
final class ControlIcon extends View {
    private final String kind;private final Paint pen=new Paint(Paint.ANTI_ALIAS_FLAG);
    ControlIcon(Context context,String kind,int color){super(context);this.kind=kind;pen.setColor(color);pen.setStyle(Paint.Style.STROKE);pen.setStrokeWidth(1.8f);pen.setStrokeCap(Paint.Cap.ROUND);pen.setStrokeJoin(Paint.Join.ROUND);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);canvas.save();float unit=Math.min(getWidth(),getHeight())/32f;canvas.translate((getWidth()-24*unit)/2,(getHeight()-24*unit)/2);canvas.scale(unit,unit);
        switch(kind){
            case "phone":canvas.drawRoundRect(6,2,18,22,3,3,pen);canvas.drawLine(10,5,14,5,pen);canvas.drawLine(10,19,14,19,pen);break;
            case "split":canvas.drawRoundRect(2,4,22,20,3,3,pen);canvas.drawLine(12,4,12,20,pen);canvas.drawLine(5,9,9,9,pen);canvas.drawLine(15,15,19,15,pen);break;
            case "files":Path file=new Path();file.moveTo(5,2);file.lineTo(14,2);file.lineTo(20,8);file.lineTo(20,22);file.lineTo(5,22);file.close();canvas.drawPath(file,pen);canvas.drawLine(14,2,14,8,pen);canvas.drawLine(14,8,20,8,pen);canvas.drawLine(9,13,16,13,pen);canvas.drawLine(9,17,15,17,pen);break;
            case "notification":canvas.drawArc(6,3,18,17,180,180,false,pen);canvas.drawLine(6,9,6,16,pen);canvas.drawLine(18,9,18,16,pen);canvas.drawLine(4,18,20,18,pen);canvas.drawArc(9,18,15,23,0,180,false,pen);break;
            case "settings":canvas.drawLine(3,6,21,6,pen);canvas.drawLine(3,12,21,12,pen);canvas.drawLine(3,18,21,18,pen);canvas.drawCircle(8,6,2.5f,pen);canvas.drawCircle(16,12,2.5f,pen);canvas.drawCircle(10,18,2.5f,pen);break;
            default:canvas.drawRoundRect(2,3,22,21,4,4,pen);canvas.drawCircle(8,9,1.5f,pen);canvas.drawLine(13,9,18,9,pen);canvas.drawLine(6,15,18,15,pen);
        }canvas.restore();}
}
