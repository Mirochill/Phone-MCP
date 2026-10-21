package fr.phonemcp;

import java.net.URI;
import java.util.Locale;

/** Pure validation shared by the transfer and installer boundaries. */
final class FilePolicy {
    static final long MAX_BYTES=512L*1024*1024;
    static String url(String value) {
        try {
            if(value==null||value.length()>4096)throw new Exception();
            URI uri=new URI(value);
            if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||uri.getFragment()!=null)throw new Exception();
            return uri.toASCIIString();
        }catch(Exception e){throw new IllegalArgumentException("Utilisez un lien de téléchargement HTTPS direct, sans identifiants intégrés.");}
    }
    static String name(String value) {
        if(value==null||value.length()<1||value.length()>140||!value.equals(value.trim())||value.startsWith(".")
                ||value.equals("..")||value.matches(".*[\\\\/:*?\"<>|\\p{Cntrl}].*")
                ||value.toLowerCase(Locale.ROOT).endsWith(".part"))
            throw new IllegalArgumentException("Nom de fichier invalide : utilisez un nom simple, sans dossier.");
        return value;
    }
    static String id(String value) {
        if(value==null||!value.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
            throw new IllegalArgumentException("Identifiant de transfert invalide.");
        return value;
    }
    static String hash(String value,boolean required) {
        if((value==null||value.isEmpty())&&!required)return "";
        if(value==null||!value.matches("[A-Fa-f0-9]{64}"))throw new IllegalArgumentException("Une empreinte SHA-256 de 64 caractères est nécessaire.");
        return value.toLowerCase(Locale.ROOT);
    }
    static String pkg(String value) {
        if(value==null||value.length()>240||!value.matches("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+"))throw new IllegalArgumentException("Identifiant d’application invalide.");
        return value;
    }
    static long limit(long value) {
        if(value<1||value>MAX_BYTES)throw new IllegalArgumentException("La taille maximale est de 512 Mo par fichier.");return value;
    }
    static String[] installArgs(String pkg,long size,int user) {
        if(size<1||size>MAX_BYTES||user<0)throw new IllegalArgumentException("Installation invalide.");
        return new String[]{"/system/bin/pm","install","-r","--restrict-permissions","--user",Integer.toString(user),"--pkg",pkg(pkg),"-S",Long.toString(size),"--","-"};
    }
}
