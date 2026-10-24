package fr.phonemcp;

import android.content.Context;
import org.json.*;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

final class I18n {
    private static volatile JSONObject translations;
    static String tr(Context context,String source){
        if(source==null)return "";
        String language=context.getResources().getConfiguration().getLocales().get(0).getLanguage();if("fr".equals(language))return source;
        try{
            if(translations==null)synchronized(I18n.class){if(translations==null)try(InputStream in=context.getAssets().open("ui-translations.json")){translations=new JSONObject(new String(CompatIO.readAtMost(in,200000),StandardCharsets.UTF_8));}}
            JSONArray values=translations.optJSONArray(source);if(values==null)return source;
            return values.optString("es".equals(language)?1:"de".equals(language)?2:0,source);
        }catch(Exception ignored){return source;}
    }
}
