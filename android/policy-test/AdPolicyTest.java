package fr.phonemcp;

public final class AdPolicyTest {
    private static int checks;private static void expect(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    public static void main(String[] args){
        for(String label:new String[]{"Close","CLOSE AD","Fermer","FERMER LA PUBLICITÉ","×","✕","X","Cerrar","Schließen"})expect("close".equals(AdPolicy.kind(label,"",null,false)),"Recognize explicit close: "+label);
        for(String label:new String[]{"Skip","Ignorer","Passer la publicité","Überspringen","Saltar anuncio"})expect("skip".equals(AdPolicy.kind(label,"",null,false)),"Recognize available skip: "+label);
        for(String label:new String[]{"Skip in 5","Passer dans 3 s","Fermer 4","Install","Download","Acheter","Claim reward","Continue","Done","Like","Open"})expect(AdPolicy.kind(label,"",null,false)==null,"No generic/timed/commerce/reward action: "+label);
        for(String label:new String[]{"Vous perdrez la récompense","Vous perdrez votre récompense","You will lose your reward","Losing the reward","Perdre votre récompense","Vous ne recevrez pas de récompense","Quitter la vidéo ?"})expect(AdPolicy.warning(label),"Recognize reward-loss warning: "+label);
        expect("continue_watching".equals(AdPolicy.kind("Continuer à regarder","",null,true)),"Fixture loss dialog resumes the video");
        expect("continue_watching".equals(AdPolicy.kind("Keep watching","",null,true)),"English warning resumes watching");
        expect(AdPolicy.kind("Close","",null,true)==null,"Never close a reward-loss warning");
        expect(AdPolicy.kind("Skip","",null,true)==null,"Never skip a reward-loss warning");
        expect(AdPolicy.kind("Continuer à regarder","",null,false)==null,"Continue is specific to a reward warning");
        expect("close".equals(AdPolicy.kind("","","com.sdk:id/ad_close_button",false)),"Recognize specific ad view ID");
        expect(AdPolicy.kind("","","com.app:id/delete_close",false)==null,"Do not infer arbitrary close from an ID");
        expect(AdPolicy.matches("Parcours pub terminé : 3 étapes","Parcours pub terminé","contains"),"Fixture return with counters is explicit contains match");
        expect(!AdPolicy.matches("Parcours pub terminé : 3 étapes","Parcours pub terminé","exact"),"Exact return does not silently turn into contains");
        expect(AdPolicy.matches("Récompense","recompense","exact"),"Unicode canonical accents match");
        expect(AdPolicy.smallCorner(630,20,690,80,720,1280),"Accessible X corner is small and bounded");
        expect(!AdPolicy.smallCorner(200,400,500,700,720,1280),"A large interior X is not a close control");
        expect(!AdPolicy.smallCorner(-1,20,60,80,720,1280),"No negative coordinates");
        expect(AdPolicy.parentSafe(0,1100,720,1200,720,1280),"A bounded bottom skip button is allowed");
        expect(!AdPolicy.parentSafe(0,0,720,1280,720,1280),"Do not click an entire ad as an ancestor");
        expect(AdPolicy.valid(1000,1)&&AdPolicy.valid(180000,16),"Limits inclusive");
        expect(!AdPolicy.valid(999,1)&&!AdPolicy.valid(180001,16)&&!AdPolicy.valid(3000,17),"Reject unbounded jobs");
        expect(AdPolicy.countdown("Durée réelle restante : 0.1 s"),"Fractional native fixture countdown waits");
        expect(AdPolicy.countdown("Skip in 5 seconds"),"Available-after SDK timer waits");
        expect(!AdPolicy.countdown("Durée réelle restante : 0.0 s"),"Zero countdown does not prevent available controls");
        expect(!AdPolicy.countdown("Fermeture disponible"),"Available label does not retain a timer");
        expect(!AdPolicy.matches("Any private page","   ","contains"),"Whitespace never becomes a universal return selector");
        expect(!AdPolicy.matches("Any private page","\t\n","contains"),"Normalized-empty queries never match");
        for(String op:new String[]{"tap","click","back","open_app","swipe","action_sequence","wait_and_click","set_text"})expect(!AdPolicy.externalUiAllowed(op,true),"An active job rejects queued external writes: "+op);
        for(String op:new String[]{"snapshot","screenshot","node_details","status"})expect(AdPolicy.externalUiAllowed(op,true),"Job inspection stays available: "+op);
        expect(AdPolicy.externalUiAllowed("tap",false),"Cancelling the job returns the UI lane to its session");
        expect(AdPolicy.windowSelector("63","exact")==63,"Observed positive window ID is accepted");
        expect(AdPolicy.windowSelector("2147483647","exact")==Integer.MAX_VALUE,"Maximum Android window ID is bounded");
        for(String value:new String[]{"0","-1","+1","01"," 63","63 ","1.0","*","2147483648","999999999999",""})expect(AdPolicy.windowSelector(value,"exact")==-1,"Invalid/noncanonical window ID rejected: "+value);
        expect(AdPolicy.windowSelector("63","contains")==-1,"Window IDs require exact equality");
        expect(AdPolicy.windowSelector(null,"exact")==-1,"Missing window ID rejected");
        expect(AdPolicy.nativeControl(true,25,45,101,118,720,1280),"Real HTML close button uses one native touch instead of ignored accessibility click");
        expect(!AdPolicy.nativeControl(false,25,45,101,118,720,1280),"Native widgets retain accessibility transport");
        expect(!AdPolicy.nativeControl(true,0,0,720,1280,720,1280),"Never promote a whole WebView to a touch target");
        expect(!AdPolicy.nativeControl(true,-1,45,101,118,720,1280),"HTML controls remain bounded to the owned window");
        System.out.println("AdPolicyTest: "+checks+" checks passed");
    }
}