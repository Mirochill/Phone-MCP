package fr.phonemcp;

import java.util.HashMap;
import java.util.Map;

/** Run with the JDK, without Android or additional dependencies. */
public final class AccessPolicyTest {
    private static int checks;
    private static void expect(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        Map<String,Object> grants = new HashMap<>();
        String[] apps = {"com.android.settings", "com.android.vending", "com.android.systemui",
                "com.google.android.permissioncontroller", "com.android.packageinstaller",
                "com.sec.android.app.launcher", "fr.phonemcp", "com.example.newapp", "android"};
        String[] capabilities = {"read", "write", "capture", "notifications"};
        for (String app : apps) for (String capability : capabilities) {
            expect(AccessPolicy.allowed(app,capability,true,true,grants), app + " / " + capability + " must default to allowed");
            expect(!AccessPolicy.allowed(app,capability,true,false,grants), "Disabled default must deny " + app);
        }
        String app = "com.android.settings";
        grants.put("grant.v3." + app + ".read", false);
        expect(AccessPolicy.allowed(app,"read",true,true,grants), "Legacy opt-in grants must not override the requested new default");
        grants.put(AccessPolicy.key(app,"write"),false);
        expect(!AccessPolicy.allowed(app,"write",true,true,grants), "An explicit action revocation must be honored");
        expect(AccessPolicy.allowed(app,"read",true,true,grants), "Revoking actions must keep reading available");
        expect(AccessPolicy.allowed(app,"capture",true,true,grants), "Revoking actions must keep captures available");
        grants.put(AccessPolicy.key(app,"read"),false);
        for (String capability : capabilities)
            expect(!AccessPolicy.allowed(app,capability,true,true,grants), "Revoking reading must block all capabilities");
        grants.clear();
        grants.put(AccessPolicy.key(app,"read"),true);
        grants.put(AccessPolicy.key(app,"write"),true);
        expect(AccessPolicy.allowed(app,"write",true,false,grants), "Individual grants must work when the default is disabled");
        expect(!AccessPolicy.allowed(app,"capture",true,false,grants), "Ungranted capabilities must remain disabled");
        expect(!AccessPolicy.allowed(app,"read",false,true,grants), "A missing or removed app must be denied");
        expect(AccessPolicy.allowed("com.example.justinstalled","write",true,true,grants), "A newly installed app needs no individual grant");
        expect(!AccessPolicy.allowed(app,"shell",true,true,grants), "Unknown capabilities must be denied");
        expect(!AccessPolicy.allowed(app,null,true,true,grants), "Null capabilities must be denied");
        for (String bad : new String[]{null,"","com..example","../app","com.example\n","x".repeat(241)})
            expect(!AccessPolicy.allowed(bad,"read",true,true,grants), "Malformed package names must be denied");
        grants.put(AccessPolicy.key(app,"read"),"true");
        expect(!AccessPolicy.allowed(app,"read",true,true,grants), "Invalid stored values must fail closed");
        System.out.println("PASS: " + checks + " permission-policy checks");
    }
}
