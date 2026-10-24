package fr.phonemcp;
import java.nio.file.*;import java.security.*;import java.util.*;
public final class UploadReceiverTest {
    private static int n;private interface Task{void run()throws Exception;}
    static void ok(boolean value){n++;if(!value)throw new AssertionError("Check "+n);}
    static void refuses(Task task)throws Exception{n++;try{task.run();throw new AssertionError("Accepted "+n);}catch(IllegalArgumentException|SecurityException|java.io.IOException expected){}}
    public static void main(String[] args)throws Exception{
        Path folder=Paths.get(args[0]);Files.createDirectories(folder);Path stage=folder.resolve("bytes.part"),part=folder.resolve("public.part"),target=folder.resolve("final.bin");
        byte[] data=new byte[128*1024+17];new Random(42).nextBytes(data);String hash=UploadReceiver.hex(MessageDigest.getInstance("SHA-256").digest(data));Files.createFile(stage);
        byte[] first=Arrays.copyOf(data,128*1024),last=Arrays.copyOfRange(data,first.length,data.length);ok(Arrays.equals(first,UploadReceiver.decode(Base64.getEncoder().encodeToString(first))));
        for(String value:new String[]{"","not base64!","a".repeat(174768)})refuses(()->UploadReceiver.decode(value));
        ok(UploadReceiver.append(stage,data.length,0,first)==first.length);ok(UploadReceiver.append(stage,data.length,0,first)==first.length);
        byte[] changed=first.clone();changed[1]^=1;refuses(()->UploadReceiver.append(stage,data.length,0,changed));ok(Files.size(stage)==first.length);
        refuses(()->UploadReceiver.append(stage,data.length,first.length+1,last));refuses(()->UploadReceiver.append(stage,data.length,-1,last));refuses(()->UploadReceiver.append(stage,data.length,0,new byte[128*1024+1]));
        refuses(()->UploadReceiver.finish(stage,part,target,data.length,hash,()->{}));ok(!Files.exists(target));
        ok(UploadReceiver.append(stage,data.length,first.length,last)==data.length);
        refuses(()->UploadReceiver.finish(stage,part,target,data.length,"0".repeat(64),()->{}));ok(!Files.exists(target)&&!Files.exists(part));
        refuses(()->UploadReceiver.finish(stage,part,target,data.length,hash,()->{throw new java.io.IOException("cancelled");}));ok(!Files.exists(target)&&!Files.exists(part));
        ok(UploadReceiver.finish(stage,part,target,data.length,hash,()->{}).equals(hash));ok(Arrays.equals(Files.readAllBytes(target),data));
        refuses(()->UploadReceiver.finish(stage,part,target,data.length,hash,()->{}));ok(Arrays.equals(Files.readAllBytes(target),data));
        Files.delete(target);Files.delete(stage);Files.delete(folder);System.out.println("UploadReceiver: "+n+" integrity, replay, cancellation and no-overwrite checks PASS");
    }
}
