package fr.phonemcp;

import java.util.Map;

public final class AppLaunchIsolationDiagnosticsTest {
    private static int checks;
    private static void expect(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    public static void main(String[] args){
        String privateUrl="https://private.example/account?auth=secret";
        String normal="Starting: Intent { act=android.intent.action.VIEW dat="+privateUrl+" }\nStatus: ok\nLaunchState: COLD\nActivity: com.weward/.MainActivity\nTotalTime: 451\nWaitTime: 490\nComplete\n";
        Map<String,Object> receipt=AppLaunchIsolationDiagnostics.receipt(normal,0,500,false,"com.weward");
        expect("returned".equals(receipt.get("outcome")),"A returned AM receipt is classified without raw Intent text");
        expect("COLD".equals(receipt.get("launchState")),"Known launch state is kept");
        expect("com.weward/.MainActivity".equals(receipt.get("activity")),"Component metadata identifies the actual AM target");
        expect(Long.valueOf(451).equals(receipt.get("totalTimeMs")),"AM timing is retained as a number");
        expect(!receipt.toString().contains("private.example")&&!receipt.toString().contains("secret"),"Private Intent URLs never appear in a receipt");
        expect(!receipt.containsKey("raw")&&!receipt.containsKey("intent")&&!receipt.containsKey("output"),"No raw output field is exposed");
        Map<String,Object> warning=AppLaunchIsolationDiagnostics.receipt("Warning: Activity not started, intent has been delivered to currently running top-most instance.\nActivity: com.weward/.MainActivity",0,5,false,"com.weward");
        expect("delivered_to_top".equals(warning.get("warning")),"Top-only reuse is distinguishable from creation");
        Map<String,Object> malformed=AppLaunchIsolationDiagnostics.receipt("Activity: com.weward/.MainActivity?token=secret\nTotalTime: secret\nLaunchState: secret",0,2,false,"com.weward");
        expect(!malformed.containsKey("activity")&&!malformed.containsKey("totalTimeMs")&&!malformed.containsKey("launchState"),"Malformed metadata cannot inject account content");
        Map<String,Object> foreign=AppLaunchIsolationDiagnostics.receipt("Activity: com.other.private/.Main",0,2,false,"com.weward");
        expect("other_package".equals(foreign.get("activity")),"An unrelated package component is masked");
        Map<String,Object> failure=AppLaunchIsolationDiagnostics.receipt("java.lang.SecurityException: private intent data\n at com.android.system.Private(secret)",0,2,false,"com.weward");
        expect("refused".equals(failure.get("outcome"))&&!failure.toString().contains("secret"),"AM exception is classified without exposing its message");
        expect("returned".equals(AppLaunchIsolationDiagnostics.receipt("Starting: Intent { dat=https://example.com/Exception/Error: }",0,2,false,"com.weward").get("outcome")),"An Intent URL containing an error word is not a command error");
        Map<String,Object> timed=AppLaunchIsolationDiagnostics.receipt("",-1,4000,true,"com.weward");
        expect("timeout".equals(timed.get("outcome"))&&Boolean.TRUE.equals(timed.get("timedOut")),"Process timeout remains ambiguous rather than success");
        Map<String,Object> amTimeout=AppLaunchIsolationDiagnostics.receipt("Status: timeout",0,2000,false,"com.weward");
        expect("timeout".equals(amTimeout.get("outcome"))&&Boolean.TRUE.equals(amTimeout.get("timedOut")),"AM timeout with zero process exit is still uncertain");
        expect(!AppLaunchIsolationDiagnostics.safeComponent("com.weward/.MainActivity token"),"A component field cannot contain arbitrary output");
        expect(!AppLaunchIsolationDiagnostics.receipt("LaunchState: SECRET\nStatus: secret\nWaitTime: -99",0,-1,false,"com.weward").toString().contains("secret"),"Only known enums and bounded positive timings survive");
        System.out.println("PASS: "+checks+" app launch diagnostic privacy and classification checks");
    }
}
