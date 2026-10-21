package fr.phonemcp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class WindowPolicyTest {
    private static int checks;
    private static void expect(boolean value,String message) {
        checks++; if(!value)throw new AssertionError(message);
    }
    private static WindowPolicy.Info w(int id,String pkg,int layer,boolean active,boolean focused) {
        return new WindowPolicy.Info(id,pkg,layer,active,focused);
    }
    public static void main(String[] args) {
        String discord="com.discord",system="com.android.systemui",keyboard="com.samsung.android.honeyboard";
        WindowPolicy.Info app=w(1,discord,1,true,true);
        WindowPolicy.Info bars=w(2,system,100,false,false);
        WindowPolicy.Info ime=w(3,keyboard,200,false,true);
        List<WindowPolicy.Info> ordinary=Arrays.asList(bars,ime,app);
        expect(WindowPolicy.choose(ordinary,discord)==app,"System bars and keyboard must not block Discord");
        expect(WindowPolicy.choose(ordinary,system)==bars,"The system overlay must be addressable separately");
        expect(WindowPolicy.choose(ordinary,keyboard)==ime,"The keyboard must remain independently addressable");
        WindowPolicy.Info shade=w(4,system,300,true,true);
        WindowPolicy.Info bubble=w(5,"com.example.bubble",250,false,false);
        List<WindowPolicy.Info> overlayed=Arrays.asList(app,bars,bubble,shade,ime);
        expect(WindowPolicy.choose(overlayed,discord)==app,"An overlay must not redirect a Discord request");
        expect(WindowPolicy.choose(overlayed,system)==shade,"Choose the topmost system panel rather than the status bar");
        expect(WindowPolicy.choose(overlayed,bubble.pkg)==bubble,"Floating app windows must be selectable");
        WindowPolicy.Info dialog=w(6,discord,10,false,false);
        List<WindowPolicy.Info> dialogs=new ArrayList<>(Arrays.asList(app,dialog,bars));
        expect(WindowPolicy.choose(dialogs,discord)==dialog,"A dialog must take precedence over the app underneath");
        Collections.reverse(dialogs);
        expect(WindowPolicy.choose(dialogs,discord)==dialog,"Selection must not depend on window enumeration order");
        WindowPolicy.Info inactive=w(7,discord,10,false,false),active=w(8,discord,10,true,false),focused=w(9,discord,10,true,true);
        expect(WindowPolicy.choose(Arrays.asList(inactive,focused,active),discord)==focused,"Focus breaks equal-layer active-window ties");
        expect(WindowPolicy.choose(Collections.emptyList(),discord)==null,"No window must not produce a guessed target");
        expect(WindowPolicy.choose(ordinary,"com.missing.app")==null,"Never fall back to a different package");
        expect(WindowPolicy.choose(ordinary,null)==null,"A missing package must not pick an arbitrary window");
        expect(WindowPolicy.choose(Arrays.asList(null,w(-1,discord,999,true,true)),discord)==null,"Invalid window identifiers must be rejected");
        expect(WindowPolicy.backTargets(app.id,discord,app.id,discord),"Back is allowed for the currently active target");
        expect(!WindowPolicy.backTargets(app.id,discord,shade.id,system),"Back must not accidentally dismiss another app's overlay");
        expect(!WindowPolicy.backTargets(app.id,discord,dialog.id,discord),"A newly active same-app dialog requires a new snapshot");
        expect(!WindowPolicy.backTargets(-1,discord,-1,discord),"An unknown active window must not receive Back");
        expect(!WindowPolicy.backTargets(1,discord,1,system),"Matching identifiers never override a package mismatch");
        System.out.println("PASS: "+checks+" window-selection regression checks");
    }
}
