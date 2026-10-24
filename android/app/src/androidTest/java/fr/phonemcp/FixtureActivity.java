package fr.phonemcp;
import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.text.InputType;
import android.widget.*;
import android.view.*;

public final class FixtureActivity extends Activity {
    int taps,pointers;EditText input;TextView pointerStatus;
    @Override public void onCreate(Bundle state){super.onCreate(state);getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(20,60,20,10);ScrollView scroll=new ScrollView(this);scroll.addView(content);setContentView(scroll);
        input=new EditText(this);input.setSingleLine(true);input.setText("seed");input.setContentDescription("Fixture input");content.addView(input,new LinearLayout.LayoutParams(-1,140));
        Button tap=new Button(this);tap.setAllCaps(false);tap.setText("Tap count: 0");tap.setOnClickListener(v->tap.setText("Tap count: "+(++taps)));content.addView(tap,new LinearLayout.LayoutParams(-1,140));
        pointerStatus=new TextView(this);pointerStatus.setText("Pointers: 0");content.addView(pointerStatus);
        TextView gestures=new TextView(this);gestures.setText("Gesture area");gestures.setBackgroundColor(0xFFCDDFF0);gestures.setOnTouchListener((v,event)->{pointers=Math.max(pointers,event.getPointerCount());pointerStatus.setText("Pointers: "+pointers);return true;});content.addView(gestures,new LinearLayout.LayoutParams(-1,250));
        Button protect=new Button(this);protect.setAllCaps(false);protect.setText("Protect input");protect.setOnClickListener(v->{input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);input.setText("private-fixture-password");});content.addView(protect,new LinearLayout.LayoutParams(-1,120));
        for(int i=0;i<450;i++){TextView item=new TextView(this);item.setText("Fixture row "+i);item.setPadding(10,12,10,12);content.addView(item,new LinearLayout.LayoutParams(-1,70));}
    }
}
