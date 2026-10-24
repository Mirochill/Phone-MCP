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
    private int BG,CARD,LINE,TEXT,MUTED,ACCENT,RED;
    private boolean dark;
    private JSONObject updateManifest;
    private Settings config;
    private LinearLayout root,body,filesBody,agentsBody;
    private TextView sessionTitle,sessionDetail,connectionStatus,requirements;
    private Button start,stop,restore;
    private Switch panelOff;
    private Spinner duration;
    private EditText endpoint,token;
    private int tab,mode;
    private boolean filesBusy;
    private String filesFingerprint="",agentsFingerprint="";
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Shizuku.OnRequestPermissionResultListener shizukuPermission=(request,result)->{if(request==41){toast(result==PackageManager.PERMISSION_GRANTED?t("Shizuku autorisé"):t("Autorisation Shizuku refusée"));render();}};
    private final Runnable refresh=new Runnable(){public void run(){updateSession();if(tab==1)loadFiles();if(BridgeService.active()&&!BridgeService.backgroundState().optBoolean("physicalPanelOff"))handler.postDelayed(this,tab==1?2000:60000);}};
    private final Runnable serviceStateChanged=()->{handler.removeCallbacks(refresh);handler.post(refresh);};

    @Override public void onCreate(Bundle state){
        super.onCreate(state);config=new Settings(this);applyPalette();mode=config.parallel()?0:config.screenControl()?1:2;
        Shizuku.addRequestPermissionResultListener(shizukuPermission);render();handlePairing(getIntent());handleShare(getIntent());
    }
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private GradientDrawable shape(int color,int stroke,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));if(stroke!=0)d.setStroke(dp(1),stroke);return d;}
    private android.graphics.drawable.Drawable ripple(int color,int stroke,int radius){return new RippleDrawable(ColorStateList.valueOf(0x307FDBC9),shape(color,stroke,radius),null);}
    private LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private LinearLayout row(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.HORIZONTAL);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private TextView text(LinearLayout parent,String value,int size,int color,boolean bold){TextView v=new TextView(this);v.setText(t(value));v.setTextSize(size);v.setTextColor(color);v.setFontFeatureSettings("kern");v.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));v.setLineSpacing(dp(2),1);parent.addView(v,new LinearLayout.LayoutParams(-1,-2));return v;}
    private void gap(LinearLayout parent,int size){View v=new View(this);parent.addView(v,new LinearLayout.LayoutParams(1,dp(size)));}
    private LinearLayout card(LinearLayout parent){LinearLayout v=column();v.setPadding(dp(18),dp(18),dp(18),dp(18));v.setBackground(shape(CARD,0,18));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(14);parent.addView(v,p);return v;}
    private Button button(LinearLayout parent,String label,int fill,int color,Runnable action){Button v=new Button(this);v.setText(t(label));v.setAllCaps(false);v.setTextSize(15);v.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));v.setTextColor(color);v.setBackground(ripple(fill,fill==CARD?LINE:0,14));v.setMinHeight(dp(52));v.setMinimumHeight(dp(52));v.setPadding(dp(14),dp(10),dp(14),dp(10));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(10);parent.addView(v,p);v.setOnClickListener(view->action.run());return v;}
    private void section(String title,String detail){text(body,title,27,TEXT,true);if(!detail.isEmpty()){gap(body,6);text(body,detail,14,MUTED,false);}gap(body,24);}
    private EditText field(LinearLayout parent,String label,String hint,boolean secret){text(parent,label,13,MUTED,false);EditText v=new EditText(this);v.setTextColor(TEXT);v.setHintTextColor(MUTED);v.setTextSize(14);v.setHint(t(hint));v.setSingleLine(true);v.setInputType(InputType.TYPE_CLASS_TEXT|(secret?InputType.TYPE_TEXT_VARIATION_PASSWORD:InputType.TYPE_TEXT_VARIATION_URI));v.setBackgroundTintList(ColorStateList.valueOf(ACCENT));v.setPadding(dp(2),dp(12),dp(2),dp(12));parent.addView(v,new LinearLayout.LayoutParams(-1,-2));gap(parent,10);return v;}
    private Switch toggle(LinearLayout parent,String label,boolean checked){Switch v=new Switch(this);v.setText(t(label));v.setTextColor(TEXT);v.setTextSize(14);v.setPadding(0,dp(10),0,dp(10));v.setMinHeight(dp(52));v.setChecked(checked);v.setThumbTintList(ColorStateList.valueOf(ACCENT));parent.addView(v,new LinearLayout.LayoutParams(-1,-2));return v;}
    private void set(TextView view,String value){if(view!=null&&!value.contentEquals(view.getText()))view.setText(value);}

    @Override protected void attachBaseContext(Context context){super.attachBaseContext(Locales.wrap(context));}
    private String t(String value){return I18n.tr(this,value);}
    private void applyPalette(){
        dark=(getResources().getConfiguration().uiMode&android.content.res.Configuration.UI_MODE_NIGHT_MASK)==android.content.res.Configuration.UI_MODE_NIGHT_YES;
        BG=dark?0xFF101216:0xFFF3F4F7;CARD=dark?0xFF1A1D23:0xFFFFFFFF;LINE=dark?0xFF30343D:0xFFE1E4EB;TEXT=dark?0xFFF3F4F7:0xFF171B24;MUTED=dark?0xFFA0A7B5:0xFF626C7E;ACCENT=dark?0xFF98B8FF:0xFF245BD9;RED=dark?0xFFFFA6A4:0xFFBB323A;
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(CARD);
    }
    private void render(){
        sessionTitle=sessionDetail=connectionStatus=requirements=null;start=stop=restore=null;endpoint=token=null;filesBody=null;filesFingerprint="";agentsBody=null;agentsFingerprint="";
        if(tab==2)getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        getWindow().getDecorView().setSystemUiVisibility(dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        root=column();root.setBackgroundColor(BG);root.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});setContentView(root);root.requestApplyInsets();
        LinearLayout header=row();header.setPadding(dp(22),dp(18),dp(22),dp(12));root.addView(header);
        ControlIcon logo=new ControlIcon(this,"phone",ACCENT);logo.setBackground(shape(CARD,0,12));header.addView(logo,new LinearLayout.LayoutParams(dp(38),dp(38)));
        TextView brand=new TextView(this);brand.setText("Phone MCP");brand.setTextColor(TEXT);brand.setTextSize(19);brand.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));LinearLayout.LayoutParams brandParams=new LinearLayout.LayoutParams(0,-2,1);brandParams.leftMargin=dp(11);header.addView(brand,brandParams);
        TextView version=new TextView(this);version.setText(BuildConfig.VERSION_NAME);version.setTextSize(12);version.setTextColor(MUTED);version.setPadding(dp(9),dp(5),dp(9),dp(5));version.setBackground(shape(CARD,0,8));header.addView(version);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);scroll.setVerticalScrollBarEnabled(false);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));body=column();body.setPadding(dp(22),dp(15),dp(22),dp(18));scroll.addView(body);
        if(tab==0)renderSession();else if(tab==1)renderFiles();else renderSettings();
        if(tab==0){LinearLayout actions=row();actions.setPadding(dp(22),dp(8),dp(22),dp(14));start=button(actions,t("Démarrer la session"),ACCENT,dark?BG:Color.WHITE,this::startSession);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(0,dp(54),1);start.setLayoutParams(sp);stop=button(actions,t("Arrêter"),CARD,RED,()->{BridgeService.stop(this);updateSession();});LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(dp(94),dp(54));ep.leftMargin=dp(10);stop.setLayoutParams(ep);root.addView(actions);}
        LinearLayout nav=row();nav.setPadding(dp(12),dp(8),dp(12),dp(5));nav.setBackgroundColor(CARD);String[] names={t("Session"),t("Fichiers"),t("Réglages")},icons={"session","files","settings"};
        for(int i=0;i<names.length;i++){final int selected=i;LinearLayout item=column();item.setGravity(Gravity.CENTER);item.setPadding(0,dp(6),0,dp(6));int color=tab==i?ACCENT:MUTED;ControlIcon icon=new ControlIcon(this,icons[i],color);item.addView(icon,new LinearLayout.LayoutParams(dp(25),dp(25)));TextView label=new TextView(this);label.setText(t(names[i]));label.setTextSize(11);label.setGravity(Gravity.CENTER);label.setTextColor(color);label.setPadding(0,dp(5),0,0);item.addView(label);item.setBackground(ripple(tab==i?(dark?0xFF242E43:0xFFEBF0FC):CARD,0,13));item.setContentDescription(t(names[i]));item.setFocusable(true);item.setClickable(true);item.setSelected(tab==i);LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(0,dp(64),1);np.setMargins(dp(3),0,dp(3),0);nav.addView(item,np);item.setOnClickListener(v->{tab=selected;render();});}root.addView(nav);updateSession();
    }

    private void renderSession(){
        section(t("Contrôle"),t("Choisissez un mode pour cette session."));
        LinearLayout state=card(body);connectionStatus=text(state,"",11,ACCENT,true);gap(state,10);sessionTitle=text(state,"",25,TEXT,true);gap(state,5);sessionDetail=text(state,"",13,MUTED,false);
        LinearLayout modes=card(body);text(modes,t("Mode de contrôle"),15,TEXT,true);gap(modes,14);
        String[] titles={t("Écran séparé"),t("Écran du téléphone"),t("Notifications")},hints={t("L’IA travaille pendant que vous utilisez le téléphone."),t("Contrôle de l’écran que vous voyez."),t("Lire et répondre sans ouvrir les apps.")},icons={"split","phone","notification"};
        for(int i=0;i<titles.length;i++){final int choice=i;LinearLayout option=row();option.setPadding(dp(12),dp(13),dp(12),dp(13));option.setBackground(ripple(mode==i?(dark?0xFF242F46:0xFFEEF3FF):CARD,mode==i?ACCENT:LINE,12));LinearLayout.LayoutParams op=new LinearLayout.LayoutParams(-1,-2);op.bottomMargin=dp(9);modes.addView(option,op);ControlIcon icon=new ControlIcon(this,icons[i],mode==i?ACCENT:MUTED);option.addView(icon,new LinearLayout.LayoutParams(dp(25),dp(25)));LinearLayout copy=column();LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-2,1);cp.leftMargin=dp(12);option.addView(copy,cp);text(copy,titles[i],14,TEXT,true);gap(copy,3);text(copy,hints[i],12,MUTED,false);TextView radio=new TextView(this);radio.setText(mode==i?"●":"○");radio.setTextColor(mode==i?ACCENT:MUTED);radio.setTextSize(19);option.addView(radio);option.setContentDescription(t(titles[i])+". "+t(hints[i]));option.setFocusable(true);option.setSelected(mode==i);option.setOnClickListener(v->{mode=choice;config.parallel(mode==0);config.screenControl(mode==1);render();});}
        if(mode==0){panelOff=toggle(modes,t("Éteindre l’écran au démarrage"),config.panelOff());panelOff.setOnCheckedChangeListener((v,on)->config.panelOff(on));}else panelOff=null;
        gap(modes,6);text(modes,t("Durée"),12,MUTED,false);duration=new Spinner(this);String[] values={t("Sans limite"),t("30 minutes"),t("1 heure"),t("8 heures")};ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,values);duration.setAdapter(adapter);duration.setSelection(getPreferences(MODE_PRIVATE).getInt("duration",0));duration.setMinimumHeight(dp(48));duration.setContentDescription(t("Durée de la session"));modes.addView(duration);duration.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){getPreferences(MODE_PRIVATE).edit().putInt("duration",pos).apply();}public void onNothingSelected(android.widget.AdapterView<?> p){}});
        restore=button(body,t("Rallumer l’écran physique"),CARD,TEXT,()->startService(new Intent(this,BridgeService.class).setAction("RESTORE_PANEL")));
        if(config.endpoint().isEmpty()||(mode==0&&!BackgroundClient.permitted())||(mode==1&&PhoneAccessibilityService.instance==null)||(mode==2&&PhoneNotificationService.instance==null)){
            gap(body,14);LinearLayout setup=card(body);text(setup,t("Préparer la connexion"),16,TEXT,true);gap(setup,6);
            if(config.endpoint().isEmpty())setting(setup,t("Connecter le serveur"),t("Coller le lien d’appairage"),this::pairingDialog);
            if(mode==0&&!BackgroundClient.permitted())setting(setup,t("Activer Shizuku"),BackgroundClient.permissionStatus(),()->{tab=2;render();});
            if(mode==1&&PhoneAccessibilityService.instance==null)setting(setup,t("Autoriser le contrôle de l’écran"),t("Ouvrir les réglages Android"),()->startActivity(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)));
            if(mode==2&&PhoneNotificationService.instance==null)setting(setup,t("Autoriser les notifications"),t("Ouvrir les réglages Android"),()->startActivity(new Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        }
        agentsBody=column();body.addView(agentsBody);
        gap(body,8);requirements=text(body,"",12,MUTED,false);
    }

    private void updateSession(){
        boolean active=BridgeService.active();JSONObject state=BridgeService.backgroundState();
        set(connectionStatus,t(active?(BridgeService.connected()?t("CONNECTÉ"):t("CONNEXION EN COURS")):t("SESSION ARRÊTÉE")));
        set(sessionTitle,t(active?t("Session en cours"):t("Prêt à démarrer")));
        String current=BridgeService.parallelEnabled()?t("Écran séparé"):BridgeService.screenControlEnabled()?t("Écran du téléphone"):t("Notifications");
        JSONObject screenPolicy=state.optJSONObject("screenPolicy");boolean screenHeld=screenPolicy!=null&&screenPolicy.optBoolean("screenAwakeRequired");
        set(sessionDetail,active?t(current)+(screenHeld?" · "+t("Écran maintenu allumé"):state.optBoolean("physicalPanelOff")?" · "+t("Écran physique éteint"):""):t("L’accès commence lorsque vous démarrez la session."));
        if(start!=null){start.setText(t(active?t("Appliquer et redémarrer"):t("Démarrer la session")));stop.setVisibility(active?View.VISIBLE:View.GONE);}
        if(restore!=null)restore.setVisibility(active&&BridgeService.parallelEnabled()?View.VISIBLE:View.GONE);
        set(requirements,state.optString("error"));
        updateAgents(state);
    }

    private void updateAgents(JSONObject state){
        if(agentsBody==null)return;JSONArray list=state.optJSONArray("sessions"),blocked=state.optJSONArray("locallyBlockedPackages");
        String fingerprint=String.valueOf(list)+String.valueOf(blocked)+BridgeService.active();if(fingerprint.equals(agentsFingerprint))return;agentsFingerprint=fingerprint;agentsBody.removeAllViews();
        if(!BridgeService.parallelEnabled()||(list==null||list.length()==0)&&(blocked==null||blocked.length()==0))return;
        LinearLayout box=card(agentsBody);text(box,getString(R.string.agent_sessions),16,TEXT,true);
        if(list!=null)for(int i=0;i<list.length();i++){JSONObject item=list.optJSONObject(i);if(item==null)continue;String id=item.optString("sessionId"),pkg=item.optString("packageName"),label=item.optString("label",pkg);gap(box,12);text(box,label,14,TEXT,true);if(!label.equals(pkg))text(box,pkg,11,MUTED,false);
            if("exclusive_activity_instance".equals(item.optString("navigationIsolation")))text(box,getString(R.string.agent_exclusive),12,MUTED,false);
            if(BridgeService.canPreview(id,pkg))button(box,getString(R.string.preview_notification_action),CARD,ACCENT,()->BridgeService.showPreview(id,pkg));
            button(box,getString(R.string.preview_stop_control),CARD,RED,()->BridgeService.stopControl(id,pkg,(result,error)->{if(error!=null)toast(error);updateSession();}));
        }
        if(blocked!=null)for(int i=0;i<blocked.length();i++){String pkg=blocked.optString(i);gap(box,12);text(box,pkg,13,TEXT,true);text(box,getString(R.string.agent_locally_stopped),12,MUTED,false);button(box,getString(R.string.agent_allow_resume),CARD,ACCENT,()->BridgeService.resumeControl(pkg,(result,error)->{if(error!=null)toast(error);updateSession();}));}
    }

    private void renderFiles(){
        section(t("Vos fichiers"),t("Transférez et installez sans quitter vos autres apps."));
        LinearLayout intro=card(body);text(intro,t("Téléchargements / PhoneMCP"),16,TEXT,true);gap(intro,6);text(intro,t("Recevez les fichiers envoyés depuis le chat ou le PC, jusqu’à 512 Mo. Une session et Shizuku actifs sont nécessaires."),13,MUTED,false);
        button(intro,t("Télécharger par lien"),ACCENT,BG,this::downloadDialog);
        button(intro,t("Actualiser"),CARD,TEXT,()->{filesFingerprint="";loadFiles();});
        documentCards();filesBody=column();body.addView(filesBody);loadFiles();
    }
    private void loadFiles(){
        if(filesBody==null||filesBusy)return;
        if(!BridgeService.active()||!BridgeService.backgroundState().optBoolean("fileServiceReady")||!config.filesAllowed()){
            if(!"inactive".equals(filesFingerprint)){filesFingerprint="inactive";filesBody.removeAllViews();LinearLayout c=card(filesBody);text(c,t("Fichiers en pause"),18,TEXT,true);gap(c,8);text(c,t("Démarrez Shizuku, autorisez Fichiers et APK, puis lancez une session."),14,MUTED,false);button(c,t("Aller à la session"),CARD,ACCENT,()->{tab=0;render();});}return;
        }
        filesBusy=true;BridgeService.files("files_list",new JSONObject(),(result,error)->{filesBusy=false;if(tab!=1||filesBody==null)return;if(error!=null){if(!error.equals(filesFingerprint)){filesFingerprint=error;filesBody.removeAllViews();text(filesBody,error,14,RED,false);}return;}String next=result.toString();if(next.equals(filesFingerprint))return;filesFingerprint=next;showFiles(result);});
    }
    private void showFiles(JSONObject result){
        filesBody.removeAllViews();JSONArray jobs=result.optJSONArray("jobs");if(jobs!=null)for(int i=0;i<jobs.length();i++){
            JSONObject job=jobs.optJSONObject(i);String state=job.optString("state");if("completed".equals(state)&&!"install".equals(job.optString("kind")))continue;
            LinearLayout c=card(filesBody);text(c,job.optString("fileName",t("Opération")),15,TEXT,true);gap(c,6);text(c,jobState(state)+(job.has("bytesDone")?" · "+size(job.optLong("bytesDone")):""),13,MUTED,false);
            if(job.has("error")){gap(c,6);text(c,job.optString("error"),13,RED,false);}if(Arrays.asList("receiving","downloading","queued","verifying").contains(state)){ProgressBar progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setProgressTintList(ColorStateList.valueOf(ACCENT));long total=job.optLong("totalBytes",-1);progress.setIndeterminate(total<=0);if(total>0)progress.setProgress((int)Math.min(100,100*job.optLong("bytesDone")/total));c.addView(progress);button(c,t("Annuler"),CARD,RED,()->fileCall("file_job",json("jobId",job.optString("jobId"),"action","cancel")));}
        }
        JSONArray files=result.optJSONArray("files");if(files==null||files.length()==0){gap(filesBody,12);text(filesBody,t("Les fichiers transférés apparaîtront ici."),14,MUTED,false);return;}
        for(int i=0;i<files.length();i++){JSONObject file=files.optJSONObject(i);LinearLayout c=card(filesBody);text(c,file.optString("fileName"),16,TEXT,true);gap(c,6);text(c,size(file.optLong("sizeBytes"))+(file.has("versionName")?t(" · version ")+file.optString("versionName"):""),13,MUTED,false);
            if(file.has("packageName"))button(c,t("Installer l’APK"),CARD,ACCENT,()->new AlertDialog.Builder(this).setTitle(t("Installer cette application ?")).setMessage(file.optString("fileName")+"\n"+file.optString("packageName")+t("\nVersion ")+file.optString("versionName")+t("\nUne version déjà installée sera mise à jour si sa signature correspond.")).setNegativeButton(t("Annuler"),null).setPositiveButton(t("Installer"),(d,w)->fileCall("install_apk",json("requestId",UUID.randomUUID().toString(),"fileId",file.optString("fileId"),"sha256",file.optString("sha256"),"expectedPackage",file.optString("packageName")))).show());
        }
    }
    private void downloadDialog(){
        LinearLayout form=column();form.setPadding(dp(24),dp(8),dp(24),dp(8));EditText url=field(form,t("Lien de téléchargement"),"https://…",false),name=field(form,t("Nom du fichier"),"document.pdf ou application.apk",false),hash=field(form,t("SHA-256 (facultatif)"),t("64 caractères"),false);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(t("Ajouter un fichier")).setView(form).setNegativeButton(t("Annuler"),null).setPositiveButton(t("Télécharger"),null).create();dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{try{JSONObject args=json("requestId",UUID.randomUUID().toString(),"url",FilePolicy.url(url.getText().toString()),"fileName",FilePolicy.name(name.getText().toString()),"sha256",FilePolicy.hash(hash.getText().toString(),false));dialog.dismiss();fileCall("download_file",args);}catch(Exception error){toast(error.getMessage());}}));dialog.show();
    }
    private void fileCall(String op,JSONObject args){BridgeService.files(op,args,(result,error)->{if(error!=null)toast(error);else{toast("install_apk".equals(op)?t("Installation lancée"):"download_file".equals(op)?t("Téléchargement lancé"):t("Opération annulée"));filesFingerprint="";loadFiles();}});}
    private static JSONObject json(String... pairs){JSONObject data=new JSONObject();try{for(int i=0;i<pairs.length;i+=2)data.put(pairs[i],pairs[i+1]);}catch(JSONException e){throw new IllegalArgumentException(e);}return data;}
    private String size(long bytes){return bytes<1024?bytes+t(" o"):bytes<1024*1024?String.format(Locale.FRANCE,t("%.1f Ko"),bytes/1024.0):String.format(Locale.FRANCE,t("%.1f Mo"),bytes/(1024.0*1024));}
    private String jobState(String state){switch(state){case "completed":return t("Installation terminée");case "queued":return t("En attente");case "receiving":return t("Réception du fichier");case "downloading":return t("Téléchargement");case "verifying":return t("Vérification du fichier");case "installing":return t("Installation par Android");case "cancelled":return t("Annulé");case "interrupted":return t("Interrompu");default:return t("Échec");}}

    private void renderSettings(){
        section(t("Réglages"),"");
        LinearLayout server=card(body);text(server,t("Connexion"),15,TEXT,true);gap(server,5);String host=config.endpoint().isEmpty()?t("Aucun serveur connecté"):Uri.parse(config.endpoint()).getHost();setting(server,t("Serveur MCP"),host,this::pairingDialog);
        if(!config.endpoint().isEmpty())setting(server,t("Copier l’URL MCP"),config.endpoint()+"/mcp",()->{((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Phone MCP",config.endpoint()+"/mcp"));toast("URL copiée");});
        setting(server,t("Connexion manuelle"),t("Adresse et jeton"),this::manualConnection);
        LinearLayout appearance=card(body);text(appearance,t("Préférences"),15,TEXT,true);gap(appearance,5);
        setting(appearance,t("Langue"),languageName(),this::chooseLanguage);String theme=Locales.preference(this,"theme","system");setting(appearance,t("Apparence"),theme.equals("dark")?t("Sombre"):theme.equals("light")?t("Claire"):t("Selon le système"),this::chooseTheme);
        LinearLayout screenOptions=card(body);text(screenOptions,t("Écran pendant le contrôle"),15,TEXT,true);gap(screenOptions,5);
        Switch keepOn=toggle(screenOptions,t("Maintenir l’écran physique allumé"),config.screenChoice().keepOn);
        text(screenOptions,t("Allume et maintient l’écran allumé tant qu’une app est contrôlée. Désactivé, l’IA ne peut pas l’imposer."),12,MUTED,false);
        keepOn.setOnCheckedChangeListener((v,on)->{config.keepPhysicalScreenOn(on);BridgeService.screenChoiceChanged((result,error)->{if(error!=null)toast(error);});});
        LinearLayout permissions=card(body);text(permissions,t("Accès autorisés"),15,TEXT,true);gap(permissions,5);
        Switch files=toggle(permissions,t("Fichiers et installation d’APK"),config.filesAllowed());files.setOnCheckedChangeListener((v,on)->{config.filesAllowed(on);if(!on)BridgeService.revokeFiles();});
        Switch all=toggle(permissions,t("Toutes les apps par défaut"),config.allApps());all.setOnCheckedChangeListener((v,on)->{config.allApps(on);PhoneAccessibilityService.invalidate();PhoneNotificationService.invalidate();});
        setting(permissions,t("Accès par application"),t("Lecture, actions, images et notifications"),this::appPicker);
        setting(permissions,t("Contrôle de l’écran"),PhoneAccessibilityService.instance!=null?t("Activé"):t("À activer"),()->startActivity(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        setting(permissions,t("Accès aux notifications"),PhoneNotificationService.instance!=null?t("Activé"):t("À activer"),()->startActivity(new Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        LinearLayout shizuku=card(body);text(shizuku,"Shizuku",15,TEXT,true);gap(shizuku,5);setting(shizuku,t("Service Shizuku"),BackgroundClient.permissionStatus(),this::openShizuku);
        setting(shizuku,t("Autoriser Phone MCP"),t("Nécessaire pour l’écran séparé et les APK"),()->{try{if(!Shizuku.pingBinder()){openShizuku();return;}if(BackgroundClient.permitted()){toast("Shizuku est déjà autorisé.");return;}Shizuku.requestPermission(41);}catch(Exception e){toast("Ouvrez Shizuku puis réessayez.");}});
        text(shizuku,t("À relancer après un redémarrage du téléphone."),12,MUTED,false);
        LinearLayout updates=card(body);text(updates,t("Mises à jour"),15,TEXT,true);gap(updates,5);setting(updates,t("Version installée"),BuildConfig.VERSION_NAME,this::checkUpdates);
        Switch checks=toggle(updates,t("Vérifier automatiquement"),getSharedPreferences("settings",MODE_PRIVATE).getBoolean("update.autoCheck",true));checks.setOnCheckedChangeListener((v,on)->getSharedPreferences("settings",MODE_PRIVATE).edit().putBoolean("update.autoCheck",on).apply());
        LinearLayout history=card(body);text(history,t("Dernière activité"),15,TEXT,true);gap(history,9);text(history,Audit.latest(),12,MUTED,false);
        button(body,t("Supprimer l’appairage"),BG,RED,()->new AlertDialog.Builder(this).setTitle(t("Supprimer l’appairage ?")).setMessage(t("La session sera arrêtée. Un nouvel appairage sera nécessaire pour reconnecter ce téléphone.")).setNegativeButton(t("Annuler"),null).setPositiveButton(t("Supprimer"),(d,w)->{BridgeService.stop(this);config.forget();render();}).show());
    }
    private void setting(LinearLayout parent,String title,String detail,Runnable action){
        LinearLayout item=row();item.setPadding(0,dp(13),0,dp(13));LinearLayout copy=column();item.addView(copy,new LinearLayout.LayoutParams(0,-2,1));text(copy,title,14,TEXT,false);if(detail!=null&&!detail.isEmpty()){gap(copy,3);text(copy,detail,12,MUTED,false);}TextView arrow=new TextView(this);arrow.setText("›");arrow.setTextSize(25);arrow.setTextColor(MUTED);arrow.setPadding(dp(12),0,0,0);item.addView(arrow);parent.addView(item);item.setBackground(ripple(CARD,0,8));item.setFocusable(true);item.setContentDescription(t(title)+". "+t(detail==null?"":detail));item.setOnClickListener(v->action.run());
    }
    private String languageName(){String code=Locales.preference(this,"language","system");switch(code){case "fr":return "Français";case "en":return "English";case "es":return "Español";case "de":return "Deutsch";default:return t("Selon le système");}}
    private void chooseLanguage(){String[] codes={"system","fr","en","es","de"},labels={t("Selon le système"),"Français","English","Español","Deutsch"};int index=Arrays.asList(codes).indexOf(Locales.preference(this,"language","system"));new AlertDialog.Builder(this).setTitle(t("Langue")).setSingleChoiceItems(labels,index,(d,which)->{Locales.setPreference(this,"language",codes[which]);d.dismiss();recreate();}).setNegativeButton(t("Annuler"),null).show();}
    private void chooseTheme(){String[] codes={"system","light","dark"},labels={t("Selon le système"),t("Claire"),t("Sombre")};new AlertDialog.Builder(this).setTitle(t("Apparence")).setSingleChoiceItems(labels,Arrays.asList(codes).indexOf(Locales.preference(this,"theme","system")),(d,which)->{Locales.setPreference(this,"theme",codes[which]);d.dismiss();recreate();}).setNegativeButton(t("Annuler"),null).show();}
    private void pairingDialog(){LinearLayout form=column();form.setPadding(dp(24),dp(12),dp(24),dp(12));EditText link=field(form,t("Lien d’appairage"),"phonemcp://pair…",false);new AlertDialog.Builder(this).setTitle(t("Connecter le serveur")).setView(form).setNegativeButton(t("Annuler"),null).setPositiveButton(t("Continuer"),(d,w)->{try{Uri uri=Uri.parse(link.getText().toString().trim());if("https".equals(uri.getScheme())&&uri.getPath()!=null&&uri.getPath().startsWith("/pairing/"))uri=Uri.parse("phonemcp://pair?server="+Uri.encode("https://"+uri.getAuthority())+"&code="+Uri.encode(uri.getLastPathSegment()));if(!"phonemcp".equals(uri.getScheme()))throw new IllegalArgumentException();handlePairing(new Intent(Intent.ACTION_VIEW,uri));}catch(Exception e){toast("Lien d’appairage invalide.");}}).show();}
    private void manualConnection(){LinearLayout form=column();form.setPadding(dp(24),dp(10),dp(24),dp(10));EditText address=field(form,t("Adresse du serveur"),"https://…",false),secret=field(form,t("Jeton d’appairage"),t("Saisir uniquement pour modifier la connexion"),true);address.setText(config.endpoint());AlertDialog dialog=new AlertDialog.Builder(this).setTitle(t("Connexion manuelle")).setView(form).setNegativeButton(t("Annuler"),null).setPositiveButton(t("Enregistrer"),null).create();dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{try{config.saveConnection(address.getText().toString(),secret.getText().toString());BridgeService.stop(this);dialog.dismiss();toast("Connexion enregistrée. La session est arrêtée.");render();}catch(Exception e){toast(e.getMessage());}}));dialog.show();dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);}
    private void chooseFolder(){Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION|Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);startActivityForResult(intent,101);}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==101&&result==RESULT_OK&&data!=null&&data.getData()!=null)try{new StorageAccess(this).add(data.getData(),data.getFlags());render();}catch(Exception error){toast(error.getMessage());}}
    private void documentCards(){
        LinearLayout folders=card(body);text(folders,t("Dossiers autorisés"),16,TEXT,true);gap(folders,6);try{JSONArray locations=new StorageAccess(this).locations();for(int i=0;i<locations.length();i++){JSONObject item=locations.getJSONObject(i);setting(folders,item.optString("name"),t("Retirer l’accès"),()->new AlertDialog.Builder(this).setTitle(t("Retirer ce dossier ?")).setNegativeButton(t("Annuler"),null).setPositiveButton(t("Retirer"),(d,w)->{new StorageAccess(this).remove(item.optString("locationId"));render();}).show());}}catch(Exception e){text(folders,e.getMessage(),12,RED,false);}button(folders,t("Ajouter un dossier"),CARD,ACCENT,this::chooseFolder);
        LinearLayout shared=card(body);text(shared,t("Partagés avec Phone MCP"),16,TEXT,true);gap(shared,6);try{JSONArray items=new ShareInbox(this).items();if(items.length()==0)text(shared,t("Utilisez Partager dans une autre app pour ajouter un texte, une image ou un fichier."),13,MUTED,false);else{for(int i=0;i<items.length();i++)text(shared,items.getJSONObject(i).optString("name"),13,TEXT,false);button(shared,t("Vider les fichiers partagés"),CARD,RED,()->{new ShareInbox(this).clear();render();});}}catch(Exception error){text(shared,error.getMessage(),12,RED,false);}
    }
    private void handleShare(Intent intent){if(intent==null||(!Intent.ACTION_SEND.equals(intent.getAction())&&!Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction())))return;new Thread(()->{try{new ShareInbox(this).accept(intent);runOnUiThread(()->{tab=1;render();toast("Contenu ajouté");});}catch(Exception error){runOnUiThread(()->toast(error.getMessage()));}},"phone-share-import").start();}
    private void checkUpdates(){toast("Recherche de mises à jour…");UpdateManager.check(this,(manifest,error)->{if(isFinishing()||isDestroyed())return;if(error!=null){toast(error);return;}if(!manifest.optBoolean("available")){toast("L’application est à jour.");return;}updateManifest=manifest;new AlertDialog.Builder(this).setTitle(t("Mise à jour disponible")+" · "+manifest.optString("versionName")).setMessage(t("L’installation conserve vos réglages et arrête la session. Rouvrez ensuite Phone MCP pour reprendre.")).setNegativeButton(t("Plus tard"),null).setPositiveButton(t("Installer"),(d,w)->{toast("Téléchargement de la mise à jour…");UpdateManager.install(this,manifest,(job,failure)->{if(failure!=null)toast(failure);else toast("Installation remise à Android.");});}).show();});}

    private void appPicker(){
        List<ApplicationInfo> apps=new ArrayList<>(getPackageManager().getInstalledApplications(0));apps.removeIf(app->!AccessPolicy.validPackage(app.packageName));apps.sort(Comparator.comparing(app->getPackageManager().getApplicationLabel(app).toString(),String.CASE_INSENSITIVE_ORDER));
        LinearLayout content=column();content.setPadding(dp(16),0,dp(16),dp(8));EditText search=field(content,t("Rechercher une application"),t("Nom ou identifiant"),false);ListView list=new ListView(this);content.addView(list,new LinearLayout.LayoutParams(-1,dp(380)));List<ApplicationInfo> visible=new ArrayList<>();ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_list_item_1);list.setAdapter(adapter);
        Runnable filter=()->{String query=search.getText().toString().toLowerCase(Locale.ROOT);visible.clear();adapter.clear();for(ApplicationInfo app:apps){String title=getPackageManager().getApplicationLabel(app).toString();if((title+" "+app.packageName).toLowerCase(Locale.ROOT).contains(query)){visible.add(app);adapter.add(title+"\n"+app.packageName);}}};filter.run();search.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int count){filter.run();}public void afterTextChanged(android.text.Editable e){}});
        list.setOnItemClickListener((p,v,pos,id)->{ApplicationInfo app=visible.get(pos);String pkg=app.packageName;String[] keys={"read","write","capture","notifications"},names={t("Lecture"),t("Actions"),t("Captures"),t("Notifications")};boolean[] checked=new boolean[4];for(int i=0;i<4;i++)checked[i]=config.allowed(pkg,keys[i]);AlertDialog dialog=new AlertDialog.Builder(this).setTitle(getPackageManager().getApplicationLabel(app)).setMultiChoiceItems(names,checked,(d,which,on)->{checked[which]=on;if(which==0&&!on){for(int i=1;i<4;i++){checked[i]=false;((AlertDialog)d).getListView().setItemChecked(i,false);}}else if(which>0&&on){checked[0]=true;((AlertDialog)d).getListView().setItemChecked(0,true);}}).setNegativeButton(t("Annuler"),null).setPositiveButton(t("Enregistrer"),(d,w)->{for(int i=0;i<4;i++)config.grant(pkg,keys[i],checked[i]);PhoneAccessibilityService.invalidate();PhoneNotificationService.invalidate();toast("Accès mis à jour");}).create();dialog.show();});new AlertDialog.Builder(this).setTitle(t("Accès par application")).setView(content).setPositiveButton(t("Fermer"),null).show();
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
        String message=(minutes==0?t("Sans expiration automatique"):t("Pendant ")+minutes+t(" minutes"))+t(", l’IA pourra ")+(parallel?t("contrôler les apps autorisées sur un écran séparé"):screen?t("contrôler les apps autorisées sur votre écran"):t("lire et utiliser les notifications autorisées"))+(config.filesAllowed()&&BackgroundClient.permitted()?t(", transférer des fichiers et installer des APK via Shizuku"):"")+"."+(parallel?t(" L’écran séparé pourra rester contrôlable même si votre écran est verrouillé."):"")+(off?t(" L’écran physique sera éteint au démarrage."):"")+(parallel&&config.screenChoice().keepOn?t(" L’écran physique sera maintenu allumé tant qu’une app est contrôlée."):"")+t("\nVous pouvez arrêter à tout moment depuis la notification. La session ne reprend pas après un redémarrage.");
        new AlertDialog.Builder(this).setTitle(t("Démarrer cette session ?")).setMessage(message).setNegativeButton(t("Annuler"),null).setPositiveButton(t("Démarrer"),(d,w)->{startForegroundService(new Intent(this,BridgeService.class).putExtra("minutes",minutes).putExtra("parallel",parallel).putExtra("screenControl",screen).putExtra("panelOff",off));handler.postDelayed(this::updateSession,400);}).show();
    }
    private void openShizuku(){Intent launch=getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");if(launch==null)launch=new Intent(Intent.ACTION_VIEW,Uri.parse("https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api"));startActivity(launch);}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);handlePairing(intent);handleShare(intent);}
    private void handlePairing(Intent intent){
        Uri link=intent==null?null:intent.getData();if(link==null||!"phonemcp".equals(link.getScheme())||!"pair".equals(link.getHost()))return;intent.setData(null);
        String server=link.getQueryParameter("server"),code=link.getQueryParameter("code");
        try{URI origin=new URI(server);if(!"https".equals(origin.getScheme())||origin.getHost()==null||origin.getUserInfo()!=null||origin.getQuery()!=null||origin.getFragment()!=null||!origin.getPath().isEmpty()||code==null||!code.matches("[A-Za-z0-9_-]{32,256}"))throw new Exception();}catch(Exception e){toast("Lien d’appairage invalide.");return;}
        new AlertDialog.Builder(this).setTitle(t("Connecter ce serveur ?")).setMessage(server+t("\nLa connexion ne démarre aucune session.")).setNegativeButton(t("Annuler"),null).setPositiveButton(t("Appairer"),(d,w)->redeemPairing(server,code)).show();
    }
    private void redeemPairing(String server,String code){
        OkHttpClient http=new OkHttpClient.Builder().callTimeout(20,java.util.concurrent.TimeUnit.SECONDS).build();new Thread(()->{
            try{RequestBody request=RequestBody.create(new JSONObject().put("code",code).toString(),MediaType.get("application/json"));try(Response response=http.newCall(new Request.Builder().url(server+"/pair").post(request).build()).execute()){
                if(!response.isSuccessful()||response.body()==null)throw new Exception(t("Code expiré ou déjà utilisé. Régénérez l’appairage."));JSONObject result=new JSONObject(response.body().string());if(!server.equals(result.getString("endpoint")))throw new Exception(t("Le serveur d’appairage a changé."));BridgeService.stop(this);config.saveConnection(server,result.getString("token"));runOnUiThread(()->{toast("Serveur appairé");render();});
            }}catch(Exception e){runOnUiThread(()->toast(e.getMessage()==null?t("Appairage impossible"):e.getMessage()));}finally{http.dispatcher().executorService().shutdown();http.connectionPool().evictAll();}
        },"phone-mcp-pairing").start();
    }
    private void toast(String value){Toast.makeText(this,t(value==null?"Opération impossible":value),Toast.LENGTH_LONG).show();}
    @Override protected void onResume(){super.onResume();BridgeService.addUiListener(serviceStateChanged);handler.removeCallbacks(refresh);handler.post(refresh);if(config!=null&&!config.endpoint().isEmpty()&&getSharedPreferences("settings",MODE_PRIVATE).getBoolean("update.autoCheck",true)&&System.currentTimeMillis()-getSharedPreferences("settings",MODE_PRIVATE).getLong("update.checked",0)>21600000)UpdateManager.check(this,(value,error)->{if(value!=null&&value.optBoolean("available")&&!isDestroyed())checkUpdates();});}
    @Override protected void onPause(){BridgeService.removeUiListener(serviceStateChanged);handler.removeCallbacks(refresh);super.onPause();}
    @Override protected void onDestroy(){Shizuku.removeRequestPermissionResultListener(shizukuPermission);super.onDestroy();}
}
