package fr.phonemcp;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.HashSet;
import java.util.Set;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class Settings {
    private static final String KEY_ALIAS = "phone-mcp-pairing";
    private final SharedPreferences prefs;
    private final Context context;

    Settings(Context context) { this.context=context.getApplicationContext(); prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE); }
    String endpoint() { return prefs.getString("endpoint", ""); }
    void saveConnection(String endpoint, String token) throws Exception {
        URI uri = new URI(endpoint.trim());
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null
                || !(uri.getPath().isEmpty() || "/".equals(uri.getPath())))
            throw new IllegalArgumentException("Utilisez l’origine HTTPS du serveur, sans chemin ni paramètres.");
        if(token.isEmpty()&&("https://"+uri.getRawAuthority()).equals(endpoint())&&!prefs.getString("token","").isEmpty())token=token();
        if (!token.matches("[A-Za-z0-9_-]{32,256}")) throw new IllegalArgumentException("Jeton d’appairage invalide.");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key());
        prefs.edit().putString("endpoint", "https://" + uri.getRawAuthority())
                .putString("token", Base64.encodeToString(cipher.doFinal(token.getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP))
                .putString("iv", Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP)).apply();
    }
    String token() throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.decode(prefs.getString("iv", ""), Base64.NO_WRAP)));
        return new String(cipher.doFinal(Base64.decode(prefs.getString("token", ""), Base64.NO_WRAP)), StandardCharsets.UTF_8);
    }
    private SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore"); ks.load(null);
        if (ks.containsAlias(KEY_ALIAS)) return (SecretKey) ks.getKey(KEY_ALIAS, null);
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return generator.generateKey();
    }
    // This new default intentionally replaces old opt-in grants on upgrade.
    boolean allApps() { return prefs.getBoolean("policy.v4.allApps", true); }
    boolean screenControl() { return prefs.getBoolean("policy.v4.screenControl", true); }
    void screenControl(boolean value) { prefs.edit().putBoolean("policy.v4.screenControl",value).apply(); }
    boolean parallel() { return prefs.getBoolean("parallel.enabled",false); }
    void parallel(boolean value) { prefs.edit().putBoolean("parallel.enabled",value).apply(); }
    boolean panelOff(){return prefs.getBoolean("parallel.panelOff",false);}
    void panelOff(boolean value){prefs.edit().putBoolean("parallel.panelOff",value).apply();}
    static final class ScreenChoice {final boolean keepOn;final long revision;ScreenChoice(boolean value,long version){keepOn=value;revision=version;}}
    ScreenChoice screenChoice(){synchronized(Settings.class){return new ScreenChoice(prefs.getBoolean("parallel.keepPhysicalScreenOn",false),prefs.getLong("parallel.screenChoiceRevision",0));}}
    void keepPhysicalScreenOn(boolean value){synchronized(Settings.class){long revision=Math.addExact(prefs.getLong("parallel.screenChoiceRevision",0),1);prefs.edit().putBoolean("parallel.keepPhysicalScreenOn",value).putLong("parallel.screenChoiceRevision",revision).apply();}}
    boolean filesAllowed(){return prefs.getBoolean("files.enabled",true);}
    void filesAllowed(boolean value){prefs.edit().putBoolean("files.enabled",value).apply();}
    Set<String> availablePackages() {
        android.content.Intent query=new android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_LAUNCHER);
        Set<String> apps=new HashSet<>();
        for(android.content.pm.ResolveInfo info:context.getPackageManager().queryIntentActivities(query,0)) {
            String pkg=info.activityInfo.packageName;
            if(AccessPolicy.validPackage(pkg)) apps.add(pkg);
        }
        // No stale cache: apps installed during a session are immediately usable.
        return apps;
    }
    Set<String> packages() {
        Set<String> apps=availablePackages();
        apps.removeIf(pkg -> !allowed(pkg,"read")); return apps;
    }
    boolean allowed(String pkg,String permission) {
        if(!AccessPolicy.validPackage(pkg) || !AccessPolicy.capability(permission)) return false;
        try { context.getPackageManager().getApplicationInfo(pkg,0); }
        catch(android.content.pm.PackageManager.NameNotFoundException error) { return false; }
        return AccessPolicy.allowed(pkg,permission,true,allApps(),prefs.getAll());
    }
    void grant(String pkg,String permission,boolean value) {
        if(!AccessPolicy.validPackage(pkg) || !AccessPolicy.capability(permission)) throw new IllegalArgumentException("App ou capacité invalide");
        SharedPreferences.Editor editor=prefs.edit().putBoolean(AccessPolicy.key(pkg,permission),value);
        if("read".equals(permission) && !value) for(String p:new String[]{"write","capture","notifications"}) editor.putBoolean(AccessPolicy.key(pkg,p),false);
        editor.apply();
        BridgeService.permissionsChanged();
    }
    void allApps(boolean value) {
        SharedPreferences.Editor editor=prefs.edit().putBoolean("policy.v4.allApps",value);
        for(String key:prefs.getAll().keySet()) if(key.startsWith(AccessPolicy.PREFIX)) editor.remove(key);
        editor.apply();
        BridgeService.permissionsChanged();
    }
    void forget() { prefs.edit().remove("endpoint").remove("token").remove("iv").apply(); }
}
