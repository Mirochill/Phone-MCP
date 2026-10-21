package fr.phonemcp;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.*;
import android.net.Uri;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.net.URI;
import java.util.*;
import okhttp3.*;
import org.json.*;
import rikka.shizuku.Shizuku;

public final class MainActivity extends Activity {
    private static final int BG=0xFF0B111B,CARD=0xFF141F2D,LINE=0xFF29384A,TEXT=0xFFEDF3FA,MUTED=0xFFA4B3C5,ACCENT=0xFF84E5CE,RED=0xFFFFB2AF;
    private Settings config;
    private LinearLayout root,body,filesBody;
    private TextView sessionTitle,sessionDetail,connectionStatus,requirements;
    private Button start,stop,restore;
    private Switch panelOff;
    private Spinner duration;
    private EditText endpoint,token;
    private int tab,mode;
    private boolean filesBusy;
    private String filesFingerprint="";
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Shizuku.OnRequestPermissionResultListener shizukuPermission=(request,result)->{if(request==41){toast(result==PackageManager.PERMISSION_GRANTED?"Shizuku autorisé":"Autorisation Shizuku refusée");render();}};
    private final Runnable refresh=new Runnable(){public void run(){updateSession();if(tab==1)loadFiles();handler.postDelayed(this,2000);}};

    @Override public void onCreate(Bundle state){
        super.onCreate(state);config=new Settings(this);mode=config.parallel()?0:config.screenControl()?1:2;
        Shizuku.addRequestPermissionResultListener(shizukuPermission);render();handlePairing(getIntent());
    }
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private GradientDrawable shape(int color,int stroke,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));if(stroke!=0)d.setStroke(dp(1),stroke);return d;}
    private android.graphics.drawable.Drawable ripple(int color,int stroke,int radius){return new RippleDrawable(ColorStateList.valueOf(0x307FDBC9),shape(color,stroke,radius),null);}
    private LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private LinearLayout row(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.HORIZONTAL);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private TextView text(LinearLayout parent,String value,int size,int color,boolean bold){TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(color);v.setFontFeatureSettings("kern");v.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));v.setLineSpacing(dp(2),1);parent.addView(v,new LinearLayout.LayoutParams(-1,-2));return v;}
    private void gap(LinearLayout parent,int size){View v=new View(this);parent.addView(v,new LinearLayout.LayoutParams(1,dp(size)));}
    private LinearLayout card(LinearLayout parent){LinearLayout v=column();v.setPadding(dp(18),dp(18),dp(18),dp(18));v.setBackground(shape(CARD,LINE,22));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(16);parent.addView(v,p);return v;}
    private Button button(LinearLayout parent,String label,int fill,int color,Runnable action){Button v=new Button(this);v.setText(label);v.setAllCaps(false);v.setTextSize(15);v.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));v.setTextColor(color);v.setBackground(ripple(fill,fill==CARD?LINE:0,14));v.setMinHeight(dp(52));v.setMinimumHeight(dp(52));v.setPadding(dp(14),dp(10),dp(14),dp(10));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(10);parent.addView(v,p);v.setOnClickListener(view->action.run());return v;}
    private void section(String title,String detail){text(body,title,28,TEXT,true);if(!detail.isEmpty()){gap(body,6);text(body,detail,14,MUTED,false);}gap(body,24);}
    private EditText field(LinearLayout parent,String label,String hint,boolean secret){text(parent,label,13,MUTED,false);EditText v=new EditText(this);v.setTextColor(TEXT);v.setHintTextColor(MUTED);v.setTextSize(14);v.setHint(hint);v.setSingleLine(true);v.setInputType(InputType.TYPE_CLASS_TEXT|(secret?InputType.TYPE_TEXT_VARIATION_PASSWORD:InputType.TYPE_TEXT_VARIATION_URI));v.setBackgroundTintList(ColorStateList.valueOf(ACCENT));v.setPadding(dp(2),dp(12),dp(2),dp(12));parent.addView(v,new LinearLayout.LayoutParams(-1,-2));gap(parent,10);return v;}
    private Switch toggle(LinearLayout parent,String label,boolean checked){Switch v=new Switch(this);v.setText(label);v.setTextColor(TEXT);v.setTextSize(14);v.setPadding(0,dp(10),0,dp(10));v.setMinHeight(dp(52));v.setChecked(checked);v.setThumbTintList(ColorStateList.valueOf(ACCENT));parent.addView(v,new LinearLayout.LayoutParams(-1,-2));return v;}
    private void set(TextView view,String value){if(view!=null&&!value.contentEquals(view.getText()))view.setText(value);}

    private void render(){
        sessionTitle=sessionDetail=connectionStatus=requirements=null;start=stop=null;endpoint=token=null;filesBody=null;filesFingerprint="";
        if(tab==2)getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        getWindow().getDecorView().setSystemUiVisibility(0);
        root=column();root.setBackgroundColor(BG);root.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});setContentView(root);root.requestApplyInsets();
        LinearLayout header=row();header.setPadding(dp(24),dp(18),dp(24),dp(14));root.addView(header);
        TextView brand=new TextView(this);brand.setText("Phone MCP");brand.setTextColor(TEXT);brand.setTextSize(21);brand.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        TextView version=new TextView(this);version.setText(BuildConfig.VERSION_NAME);version.setTextSize(12);version.setTextColor(MUTED);header.addView(version);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));body=column();body.setPadding(dp(24),dp(12),dp(24),dp(24));scroll.addView(body);
        if(tab==0)renderSession();else if(tab==1)renderFiles();else renderSettings();
        if(tab==0){LinearLayout actions=column();actions.setPadding(dp(24),0,dp(24),dp(12));start=button(actions,"Démarrer la session",ACCENT,BG,this::startSession);stop=button(actions,"Arrêter la session",CARD,RED,()->{BridgeService.stop(this);updateSession();});root.addView(actions);}
        LinearLayout nav=row();nav.setPadding(dp(14),dp(8),dp(14),dp(10));nav.setBackground(shape(BG,LINE,0));String[] names={"Session","Fichiers","Réglages"};
        for(int i=0;i<names.length;i++){final int selected=i;Button item=new Button(this);item.setText(names[i]);item.setAllCaps(false);item.setTextSize(14);item.setMinHeight(dp(52));item.setTextColor(tab==i?ACCENT:MUTED);item.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));item.setBackground(ripple(tab==i?CARD:BG,0,14));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.setMargins(dp(3),0,dp(3),0);nav.addView(item,p);item.setSelected(tab==i);item.setOnClickListener(v->{tab=selected;render();});}root.addView(nav);updateSession();
    }
    private void renderSession(){
        section("Votre session","Choisissez comment l’IA utilise votre téléphone.");
        LinearLayout state=card(body);connectionStatus=text(state,"",12,ACCENT,true);gap(state,10);sessionTitle=text(state,"",25,TEXT,true);gap(state,6);sessionDetail=text(state,"",14,MUTED,false);
        LinearLayout modes=card(body);text(modes,"Mode de contrôle",17,TEXT,true);gap(modes,12);
        String[] titles={"Écran séparé","Écran du téléphone","Notifications"};String[] hints={"Utilisez une autre app en même temps · Shizuku","L’IA agit sur l’écran que vous voyez","Lire et répondre sans ouvrir les apps"};
        for(int i=0;i<titles.length;i++){final int choice=i;LinearLayout option=column();option.setPadding(dp(14),dp(13),dp(14),dp(13));option.setBackground(ripple(mode==i?0xFF1A3538:CARD,mode==i?ACCENT:LINE,14));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(8);modes.addView(option,p);text(option,(mode==i?"●  ":"○  ")+titles[i],15,mode==i?ACCENT:TEXT,true);gap(option,4);text(option,hints[i],13,MUTED,false);option.setContentDescription(titles[i]+". "+hints[i]+(mode==i?". Sélectionné":""));option.setOnClickListener(v->{mode=choice;config.parallel(mode==0);config.screenControl(mode==1);render();});}
        if(mode==0){panelOff=toggle(modes,"Éteindre l’écran physique",config.panelOff());panelOff.setOnCheckedChangeListener((v,on)->config.panelOff(on));text(modes,"Le bouton latéral verrouille votre écran. La session reste ouverte sur l’écran séparé.",12,MUTED,false);}else panelOff=null;
        gap(modes,14);text(modes,"Durée",13,MUTED,false);duration=new Spinner(this);String[] durations={"Sans limite de durée","30 minutes","1 heure","8 heures"};ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,durations);duration.setAdapter(adapter);duration.setContentDescription("Durée de la session");duration.setMinimumHeight(dp(48));modes.addView(duration);
        restore=button(body,"Rallumer l’écran physique",CARD,TEXT,()->startService(new Intent(this,BridgeService.class).setAction("RESTORE_PANEL")));
        gap(body,20);requirements=text(body,"",13,MUTED,false);button(body,"Gérer les autorisations",BG,MUTED,()->{tab=2;render();});
    }
    private void updateSession(){
        boolean active=BridgeService.active();JSONObject state=BridgeService.backgroundState();
        set(connectionStatus,active?"●  "+(BridgeService.status().startsWith("Connecté")?"CONNECTÉ":"SESSION ACTIVE"):"○  SESSION ARRÊTÉE");
        set(sessionTitle,active?"Vous gardez la main":"Prêt quand vous l’êtes");
        set(sessionDetail,active?BridgeService.status()+"\n"+(BridgeService.parallelEnabled()?"Écran séparé"+(state.optBoolean("physicalPanelOff")?" · écran physique éteint":""):BridgeService.screenControlEnabled()?"Écran du téléphone":"Notifications"):"Démarrez une session pour donner accès aux apps autorisées.");
        if(start!=null){start.setText(active?"Appliquer et redémarrer":"Démarrer la session");stop.setVisibility(active?View.VISIBLE:View.GONE);}
        if(restore!=null)restore.setVisibility(active&&BridgeService.parallelEnabled()?View.VISIBLE:View.GONE);
        String error=state.optString("error");set(requirements,"Shizuku : "+BackgroundClient.permissionStatus()+(!error.isEmpty()?"\n"+error:"")+(config.filesAllowed()?"\nFichiers et APK : "+(state.optBoolean("fileServiceReady")?"prêts":"disponibles avec Shizuku pendant une session"):""));
    }
    private void renderFiles(){
        section("Vos fichiers","Transférez et installez sans quitter vos autres apps.");
        LinearLayout intro=card(body);text(intro,"Téléchargements / PhoneMCP",16,TEXT,true);gap(intro,6);text(intro,"Lien HTTPS direct · jusqu’à 512 Mo par fichier. Une session et Shizuku actifs sont nécessaires, même pour installer une APK.",13,MUTED,false);
        button(intro,"Ajouter un fichier",ACCENT,BG,this::downloadDialog);
        button(intro,"Actualiser",CARD,TEXT,()->{filesFingerprint="";loadFiles();});
        filesBody=column();body.addView(filesBody);loadFiles();
    }
    private void loadFiles(){
        if(filesBody==null||filesBusy)return;
        if(!BridgeService.active()||!BridgeService.backgroundState().optBoolean("fileServiceReady")||!config.filesAllowed()){
            if(!"inactive".equals(filesFingerprint)){filesFingerprint="inactive";filesBody.removeAllViews();LinearLayout c=card(filesBody);text(c,"Fichiers en pause",18,TEXT,true);gap(c,8);text(c,"Démarrez Shizuku, autorisez Fichiers et APK, puis lancez une session.",14,MUTED,false);button(c,"Aller à la session",CARD,ACCENT,()->{tab=0;render();});}return;
        }
        filesBusy=true;BridgeService.files("files_list",new JSONObject(),(result,error)->{filesBusy=false;if(tab!=1||filesBody==null)return;if(error!=null){if(!error.equals(filesFingerprint)){filesFingerprint=error;filesBody.removeAllViews();text(filesBody,error,14,RED,false);}return;}String next=result.toString();if(next.equals(filesFingerprint))return;filesFingerprint=next;showFiles(result);});
    }
    private void showFiles(JSONObject result){
        filesBody.removeAllViews();JSONArray jobs=result.optJSONArray("jobs");if(jobs!=null)for(int i=0;i<jobs.length();i++){
            JSONObject job=jobs.optJSONObject(i);String state=job.optString("state");if("completed".equals(state)&&!"install".equals(job.optString("kind")))continue;
            LinearLayout c=card(filesBody);text(c,job.optString("fileName","Opération"),15,TEXT,true);gap(c,6);text(c,jobState(state)+(job.has("bytesDone")?" · "+size(job.optLong("bytesDone")):""),13,MUTED,false);
            if(job.has("error")){gap(c,6);text(c,job.optString("error"),13,RED,false);}if(Arrays.asList("downloading","queued","verifying").contains(state)){ProgressBar progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setProgressTintList(ColorStateList.valueOf(ACCENT));long total=job.optLong("totalBytes",-1);progress.setIndeterminate(total<=0);if(total>0)progress.setProgress((int)Math.min(100,100*job.optLong("bytesDone")/total));c.addView(progress);button(c,"Annuler",CARD,RED,()->fileCall("file_job",json("jobId",job.optString("jobId"),"action","cancel")));}
        }
        JSONArray files=result.optJSONArray("files");if(files==null||files.length()==0){gap(filesBody,12);text(filesBody,"Les fichiers transférés apparaîtront ici.",14,MUTED,false);return;}
        for(int i=0;i<files.length();i++){JSONObject file=files.optJSONObject(i);LinearLayout c=card(filesBody);text(c,file.optString("fileName"),16,TEXT,true);gap(c,6);text(c,size(file.optLong("sizeBytes"))+(file.has("versionName")?" · version "+file.optString("versionName"):""),13,MUTED,false);
            if(file.has("packageName"))button(c,"Installer l’APK",CARD,ACCENT,()->new AlertDialog.Builder(this).setTitle("Installer cette application ?").setMessage(file.optString("fileName")+"\n"+file.optString("packageName")+"\nVersion "+file.optString("versionName")+"\nUne version déjà installée sera mise à jour si sa signature correspond.").setNegativeButton("Annuler",null).setPositiveButton("Installer",(d,w)->fileCall("install_apk",json("requestId",UUID.randomUUID().toString(),"fileId",file.optString("fileId"),"sha256",file.optString("sha256"),"expectedPackage",file.optString("packageName")))).show());
        }
    }
    private void downloadDialog(){
        LinearLayout form=column();form.setPadding(dp(24),dp(8),dp(24),dp(8));EditText url=field(form,"Lien de téléchargement","https://…",false),name=field(form,"Nom du fichier","document.pdf ou application.apk",false),hash=field(form,"SHA-256 (facultatif)","64 caractères",false);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Ajouter un fichier").setView(form).setNegativeButton("Annuler",null).setPositiveButton("Télécharger",null).create();dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{try{JSONObject args=json("requestId",UUID.randomUUID().toString(),"url",FilePolicy.url(url.getText().toString()),"fileName",FilePolicy.name(name.getText().toString()),"sha256",FilePolicy.hash(hash.getText().toString(),false));dialog.dismiss();fileCall("download_file",args);}catch(Exception error){toast(error.getMessage());}}));dialog.show();
    }
    private void fileCall(String op,JSONObject args){BridgeService.files(op,args,(result,error)->{if(error!=null)toast(error);else{toast("install_apk".equals(op)?"Installation lancée":"download_file".equals(op)?"Téléchargement lancé":"Opération annulée");filesFingerprint="";loadFiles();}});}
    private static JSONObject json(String... pairs){JSONObject data=new JSONObject();try{for(int i=0;i<pairs.length;i+=2)data.put(pairs[i],pairs[i+1]);}catch(JSONException e){throw new IllegalArgumentException(e);}return data;}
    private static String size(long bytes){return bytes<1024?bytes+" o":bytes<1024*1024?String.format(Locale.FRANCE,"%.1f Ko",bytes/1024.0):String.format(Locale.FRANCE,"%.1f Mo",bytes/(1024.0*1024));}
    private static String jobState(String state){switch(state){case "completed":return "Installation terminée";case "queued":return "En attente";case "downloading":return "Téléchargement";case "verifying":return "Vérification de l’APK";case "installing":return "Installation par Android";case "cancelled":return "Annulé";case "interrupted":return "Interrompu";default:return "Échec";}}

    private void renderSettings(){
        section("Réglages","Les accès restent sous votre contrôle.");
        LinearLayout server=card(body);text(server,"Connexion au serveur",18,TEXT,true);gap(server,16);endpoint=field(server,"Adresse du serveur","https://mcp.votre-domaine.fr",false);endpoint.setText(config.endpoint());token=field(server,"Jeton d’appairage","Saisir uniquement pour modifier la connexion",true);
        button(server,"Enregistrer la connexion",CARD,ACCENT,()->{try{config.saveConnection(endpoint.getText().toString(),token.getText().toString());BridgeService.stop(this);token.setText("");toast("Connexion enregistrée. La session est arrêtée.");}catch(Exception error){toast(error.getMessage());}});
        LinearLayout shizuku=card(body);text(shizuku,"Shizuku",18,TEXT,true);gap(shizuku,6);text(shizuku,BackgroundClient.permissionStatus()+". À relancer après un redémarrage du téléphone.",13,MUTED,false);button(shizuku,"Ouvrir Shizuku",CARD,TEXT,this::openShizuku);button(shizuku,"Autoriser Phone MCP",CARD,ACCENT,()->{try{if(!Shizuku.pingBinder()){toast("Démarrez Shizuku par débogage sans fil.");return;}if(BackgroundClient.permitted()){toast("Shizuku est déjà autorisé.");return;}Shizuku.requestPermission(41);}catch(Exception e){toast("Ouvrez Shizuku puis réessayez.");}});
        LinearLayout permissions=card(body);text(permissions,"Autorisations",18,TEXT,true);gap(permissions,6);text(permissions,"Actives uniquement pendant une session démarrée manuellement. Les mots de passe restent masqués.",13,MUTED,false);
        Switch files=toggle(permissions,"Fichiers et installation d’APK",config.filesAllowed());files.setOnCheckedChangeListener((v,on)->{config.filesAllowed(on);if(!on)BridgeService.revokeFiles();});
        Switch all=toggle(permissions,"Toutes les apps par défaut",config.allApps());all.setOnCheckedChangeListener((v,on)->{config.allApps(on);PhoneAccessibilityService.invalidate();PhoneNotificationService.invalidate();});button(permissions,"Gérer les accès par application",CARD,TEXT,this::appPicker);
        button(permissions,"Accès à l’écran · "+(PhoneAccessibilityService.instance!=null?"actif":"à activer"),CARD,TEXT,()->startActivity(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        button(permissions,"Accès aux notifications · "+(PhoneNotificationService.instance!=null?"actif":"à activer"),CARD,TEXT,()->startActivity(new Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        LinearLayout history=card(body);text(history,"Dernière activité",16,TEXT,true);gap(history,8);text(history,Audit.latest(),13,MUTED,false);
        button(body,"Supprimer l’appairage",BG,RED,()->new AlertDialog.Builder(this).setTitle("Supprimer l’appairage ?").setMessage("La session sera arrêtée. Un nouvel appairage sera nécessaire pour reconnecter ce téléphone.").setNegativeButton("Annuler",null).setPositiveButton("Supprimer",(d,w)->{BridgeService.stop(this);config.forget();render();}).show());
    }
    private void appPicker(){
        List<ApplicationInfo> apps=new ArrayList<>(getPackageManager().getInstalledApplications(0));apps.removeIf(app->!AccessPolicy.validPackage(app.packageName));apps.sort(Comparator.comparing(app->getPackageManager().getApplicationLabel(app).toString(),String.CASE_INSENSITIVE_ORDER));
        LinearLayout content=column();content.setPadding(dp(16),0,dp(16),dp(8));EditText search=field(content,"Rechercher une application","Nom ou identifiant",false);ListView list=new ListView(this);content.addView(list,new LinearLayout.LayoutParams(-1,dp(380)));List<ApplicationInfo> visible=new ArrayList<>();ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_list_item_1);list.setAdapter(adapter);
        Runnable filter=()->{String query=search.getText().toString().toLowerCase(Locale.ROOT);visible.clear();adapter.clear();for(ApplicationInfo app:apps){String title=getPackageManager().getApplicationLabel(app).toString();if((title+" "+app.packageName).toLowerCase(Locale.ROOT).contains(query)){visible.add(app);adapter.add(title+"\n"+app.packageName);}}};filter.run();search.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int count){filter.run();}public void afterTextChanged(android.text.Editable e){}});
        list.setOnItemClickListener((p,v,pos,id)->{ApplicationInfo app=visible.get(pos);String pkg=app.packageName;String[] keys={"read","write","capture","notifications"},names={"Lecture","Actions","Captures","Notifications"};boolean[] checked=new boolean[4];for(int i=0;i<4;i++)checked[i]=config.allowed(pkg,keys[i]);AlertDialog dialog=new AlertDialog.Builder(this).setTitle(getPackageManager().getApplicationLabel(app)).setMultiChoiceItems(names,checked,(d,which,on)->{checked[which]=on;if(which==0&&!on){for(int i=1;i<4;i++){checked[i]=false;((AlertDialog)d).getListView().setItemChecked(i,false);}}else if(which>0&&on){checked[0]=true;((AlertDialog)d).getListView().setItemChecked(0,true);}}).setNegativeButton("Annuler",null).setPositiveButton("Enregistrer",(d,w)->{for(int i=0;i<4;i++)config.grant(pkg,keys[i],checked[i]);PhoneAccessibilityService.invalidate();PhoneNotificationService.invalidate();toast("Accès mis à jour");}).create();dialog.show();});new AlertDialog.Builder(this).setTitle("Accès par application").setView(content).setPositiveButton("Fermer",null).show();
    }
    private void startSession(){
        boolean parallel=mode==0,screen=mode==1;
        if(parallel&&!BackgroundClient.permitted()){toast("Démarrez et autorisez Shizuku dans Réglages.");return;}
        if(screen&&PhoneAccessibilityService.instance==null){toast("Activez l’accès à l’écran dans Réglages.");return;}
        if(mode==2&&PhoneNotificationService.instance==null){toast("Activez l’accès aux notifications dans Réglages.");return;}
        try{if(config.endpoint().isEmpty()||config.token().isEmpty()){toast("Appairez le serveur dans Réglages.");return;}}catch(Exception e){toast("Réenregistrez l’appairage.");return;}
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},1);toast("Autorisez les notifications puis démarrez la session.");return;}
        if(!getSystemService(NotificationManager.class).areNotificationsEnabled()){startActivity(new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,getPackageName()));return;}
        int[] choices={0,30,60,480};int minutes=choices[duration.getSelectedItemPosition()];boolean off=parallel&&panelOff!=null&&panelOff.isChecked();
        String message=(minutes==0?"Sans expiration automatique":"Pendant "+minutes+" minutes")+", l’IA pourra "+(parallel?"contrôler les apps autorisées sur un écran séparé":screen?"contrôler les apps autorisées sur votre écran":"lire et utiliser les notifications autorisées")+(config.filesAllowed()&&BackgroundClient.permitted()?", transférer des fichiers et installer des APK via Shizuku":"")+"."+(parallel?" L’écran séparé pourra rester contrôlable même si votre écran est verrouillé.":"")+(off?" L’écran physique sera éteint au démarrage.":"")+"\nVous pouvez arrêter à tout moment depuis la notification. La session ne reprend pas après un redémarrage.";
        new AlertDialog.Builder(this).setTitle("Démarrer cette session ?").setMessage(message).setNegativeButton("Annuler",null).setPositiveButton("Démarrer",(d,w)->{startForegroundService(new Intent(this,BridgeService.class).putExtra("minutes",minutes).putExtra("parallel",parallel).putExtra("screenControl",screen).putExtra("panelOff",off));handler.postDelayed(this::updateSession,400);}).show();
    }
    private void openShizuku(){Intent launch=getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");if(launch==null)launch=new Intent(Intent.ACTION_VIEW,Uri.parse("https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api"));startActivity(launch);}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);handlePairing(intent);}
    private void handlePairing(Intent intent){
        Uri link=intent==null?null:intent.getData();if(link==null||!"phonemcp".equals(link.getScheme())||!"pair".equals(link.getHost()))return;intent.setData(null);
        String server=link.getQueryParameter("server"),code=link.getQueryParameter("code");
        try{URI origin=new URI(server);if(!"https".equals(origin.getScheme())||origin.getHost()==null||origin.getUserInfo()!=null||origin.getQuery()!=null||origin.getFragment()!=null||!origin.getPath().isEmpty()||code==null||!code.matches("[A-Za-z0-9_-]{32,256}"))throw new Exception();}catch(Exception e){toast("Lien d’appairage invalide.");return;}
        new AlertDialog.Builder(this).setTitle("Connecter ce serveur ?").setMessage(server+"\nLa connexion ne démarre aucune session.").setNegativeButton("Annuler",null).setPositiveButton("Appairer",(d,w)->redeemPairing(server,code)).show();
    }
    private void redeemPairing(String server,String code){
        OkHttpClient http=new OkHttpClient.Builder().callTimeout(20,java.util.concurrent.TimeUnit.SECONDS).build();new Thread(()->{
            try{RequestBody request=RequestBody.create(new JSONObject().put("code",code).toString(),MediaType.get("application/json"));try(Response response=http.newCall(new Request.Builder().url(server+"/pair").post(request).build()).execute()){
                if(!response.isSuccessful()||response.body()==null)throw new Exception("Code expiré ou déjà utilisé. Régénérez l’appairage.");JSONObject result=new JSONObject(response.body().string());if(!server.equals(result.getString("endpoint")))throw new Exception("Le serveur d’appairage a changé.");BridgeService.stop(this);config.saveConnection(server,result.getString("token"));runOnUiThread(()->{toast("Serveur appairé");render();});
            }}catch(Exception e){runOnUiThread(()->toast(e.getMessage()==null?"Appairage impossible":e.getMessage()));}finally{http.dispatcher().executorService().shutdown();http.connectionPool().evictAll();}
        },"phone-mcp-pairing").start();
    }
    private void toast(String value){Toast.makeText(this,value==null?"Opération impossible":value,Toast.LENGTH_LONG).show();}
    @Override protected void onResume(){super.onResume();handler.removeCallbacks(refresh);handler.post(refresh);}
    @Override protected void onPause(){handler.removeCallbacks(refresh);super.onPause();}
    @Override protected void onDestroy(){Shizuku.removeRequestPermissionResultListener(shizukuPermission);super.onDestroy();}
}
