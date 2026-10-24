package fr.phonemcp;

/** A temporary lease for our one accessibility component, preserving other settings verbatim. */
final class AccessibilityLeasePolicy {
    static final String COMPONENT="fr.phonemcp/.PhoneAccessibilityService";
    static final String FULL_COMPONENT="fr.phonemcp/fr.phonemcp.PhoneAccessibilityService";
    static final int MAX_SETTING_CHARS=65536;
    interface Backend {
        String services()throws Exception;
        void services(String value)throws Exception;
    }
    interface Journal {void save(boolean added)throws Exception;}
    private final Backend backend;
    private final Journal journal;
    private boolean owned,active,preexisting,revoked;
    AccessibilityLeasePolicy(Backend backend,Journal journal,boolean recoveredAdded){this.backend=backend;this.journal=journal;owned=recoveredAdded;}
    private static String checked(String setting){
        if(setting!=null&&setting.length()>MAX_SETTING_CHARS)throw new IllegalStateException("Liste d’accessibilité trop volumineuse. Aucun réglage modifié.");
        return setting;
    }
    static boolean ours(String token){return COMPONENT.equals(token)||FULL_COMPONENT.equals(token);}
    static int count(String setting){
        checked(setting);if(setting==null||setting.isEmpty())return 0;
        int count=0;for(String token:setting.split(":",-1))if(ours(token))count++;return count;
    }
    static String append(String setting){
        checked(setting);if(count(setting)>0)return setting;
        String result=setting==null||setting.isEmpty()?COMPONENT:setting+(setting.endsWith(":")?"":":")+COMPONENT;
        return checked(result);
    }
    static String removeOne(String setting){
        checked(setting);if(count(setting)==0)return setting;
        String[] tokens=setting.split(":",-1);int index=-1;
        // Prefer the exact short component we appended. A concurrently added alias/duplicate
        // remains, rather than treating every equivalent occurrence as our own addition.
        for(int i=0;i<tokens.length;i++)if(COMPONENT.equals(tokens[i]))index=i;
        if(index<0)for(int i=0;i<tokens.length;i++)if(ours(tokens[i]))index=i;
        StringBuilder result=new StringBuilder();boolean first=true;
        for(int i=0;i<tokens.length;i++)if(i!=index){if(!first)result.append(':');result.append(tokens[i]);first=false;}
        return result.toString();
    }
    private String current()throws Exception{return checked(backend.services());}
    private String stable()throws Exception{
        String value=current();
        for(int i=0;i<3;i++){String newer=current();if(java.util.Objects.equals(value,newer))return value;value=newer;}
        throw new IllegalStateException("Les réglages d’accessibilité changent actuellement. Aucun remplacement de liste effectué.");
    }
    synchronized void acquire()throws Exception {
        if(revoked)throw new SecurityException("Le service de gestes a été désactivé localement. Redémarrez la session sur le téléphone pour l’autoriser de nouveau.");
        String value=stable();
        if(owned){
            if(count(value)>0){active=true;return;}
            active=false;revoked=true;journal.save(false);owned=false;
            throw new SecurityException("Le service de gestes a été désactivé localement.");
        }
        if(count(value)>0){active=true;preexisting=true;return;}
        // Persist before the first setting mutation, including the uncertain binder-return case.
        journal.save(true);owned=true;preexisting=false;
        try{
            String latest=stable();
            if(count(latest)>0){journal.save(false);owned=false;preexisting=true;active=true;return;}
            backend.services(append(latest));
            if(count(current())==0)throw new IllegalStateException("Android n’a pas confirmé l’activation du service de gestes.");
            active=true;
        }catch(Exception failure){
            try{release();}catch(Exception recovery){failure.addSuppressed(recovery);}
            throw failure;
        }
    }
    synchronized void observeExternalChange()throws Exception {
        if(!active&&!owned)return;
        if(count(current())==0){
            active=false;revoked=true;
            if(owned){journal.save(false);owned=false;}
        }
    }
    synchronized void release()throws Exception {
        active=false;
        if(!owned)return; // A previously enabled/user-owned service is never disabled by us.
        String value=stable();
        if(count(value)>0){
            String desired=removeOne(value);backend.services(desired);
            String after=current();
            if(count(after)>=count(value))throw new IllegalStateException("Retrait du service temporaire non confirmé. Le journal de récupération est conservé.");
        }
        journal.save(false);owned=false;
    }
    synchronized void beginLocalSession(){revoked=false;}
    synchronized boolean ownsAddition(){return owned;}
    synchronized boolean active(){return active;}
    synchronized boolean preexisting(){return preexisting;}
    synchronized boolean revoked(){return revoked;}
}



