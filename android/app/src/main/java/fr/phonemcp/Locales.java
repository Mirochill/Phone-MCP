package fr.phonemcp;

import android.content.*;
import android.content.res.Configuration;
import android.os.LocaleList;
import java.util.Locale;

final class Locales {
    static Context wrap(Context context){
        android.content.SharedPreferences prefs=context.getSharedPreferences("settings",Context.MODE_PRIVATE);
        String language=prefs.getString("ui.language","system"),theme=prefs.getString("ui.theme","system");
        Configuration configuration=new Configuration(context.getResources().getConfiguration());
        if(!"system".equals(language))configuration.setLocales(new LocaleList(Locale.forLanguageTag(language)));
        if(!"system".equals(theme))configuration.uiMode=(configuration.uiMode&~Configuration.UI_MODE_NIGHT_MASK)|("dark".equals(theme)?Configuration.UI_MODE_NIGHT_YES:Configuration.UI_MODE_NIGHT_NO);
        return context.createConfigurationContext(configuration);
    }
    static String preference(Context context,String key,String fallback){return context.getSharedPreferences("settings",Context.MODE_PRIVATE).getString("ui."+key,fallback);}
    static void setPreference(Context context,String key,String value){context.getSharedPreferences("settings",Context.MODE_PRIVATE).edit().putString("ui."+key,value).apply();}
}
