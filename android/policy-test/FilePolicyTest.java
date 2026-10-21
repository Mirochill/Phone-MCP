package fr.phonemcp;
import java.util.*;
public final class FilePolicyTest {
    static int count;
    static void ok(boolean value){count++;if(!value)throw new AssertionError("Check "+count);}
    static void refuses(Runnable action){count++;try{action.run();throw new AssertionError("Accepted check "+count);}catch(IllegalArgumentException expected){}}
    public static void main(String[] args){
        ok(FilePolicy.url("https://example.com/file?token=abc").equals("https://example.com/file?token=abc"));
        for(String value:new String[]{"http://example.com","file:///etc/passwd","https://user:pass@example.com/","https://example.com/#x","https:///bad","https://example.com/\n",null})refuses(()->FilePolicy.url(value));
        for(String value:new String[]{"image.png","application.apk","Mon fichier.pdf","étude 2026.txt"})ok(FilePolicy.name(value).equals(value));
        for(String value:new String[]{"../app.apk","..",".download.part","/a","a/b","a\\b","a:b"," bad.txt","bad.txt ","x\n.txt","","a".repeat(141),null})refuses(()->FilePolicy.name(value));
        String id="a8d56602-b21e-4485-8d71-e245ddc6a451";ok(FilePolicy.id(id).equals(id));
        for(String value:new String[]{"../../","a",null})refuses(()->FilePolicy.id(value));
        ok(FilePolicy.hash("A".repeat(64),true).equals("a".repeat(64)));ok(FilePolicy.hash("",false).isEmpty());
        refuses(()->FilePolicy.hash("",true));refuses(()->FilePolicy.hash("g".repeat(64),true));
        ok(FilePolicy.limit(FilePolicy.MAX_BYTES)==536870912);refuses(()->FilePolicy.limit(0));refuses(()->FilePolicy.limit(FilePolicy.MAX_BYTES+1));
        String[] command=FilePolicy.installArgs("org.example.app",1024,0);ok(command[0].equals("/system/bin/pm"));ok(Arrays.asList(command).contains("--pkg"));ok(Arrays.asList(command).contains("--restrict-permissions"));ok(!Arrays.asList(command).contains("-g"));ok(!Arrays.asList(command).contains("-d"));ok(command[command.length-1].equals("-"));
        refuses(()->FilePolicy.installArgs("org.example.app; reboot",1024,0));refuses(()->FilePolicy.installArgs("org.example.app",0,0));refuses(()->FilePolicy.installArgs("org.example.app",1024,-1));
        System.out.println("FilePolicy: "+count+" checks PASS");
    }
}
