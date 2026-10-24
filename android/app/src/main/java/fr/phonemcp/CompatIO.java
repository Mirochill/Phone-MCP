package fr.phonemcp;
import java.io.*;
final class CompatIO {
    static byte[] readAtMost(InputStream input,int limit)throws IOException{
        ByteArrayOutputStream out=new ByteArrayOutputStream(Math.min(limit,8192));byte[] buffer=new byte[8192];
        while(out.size()<limit){int count=input.read(buffer,0,Math.min(buffer.length,limit-out.size()));if(count<0)break;if(count==0)continue;out.write(buffer,0,count);}return out.toByteArray();
    }
}
