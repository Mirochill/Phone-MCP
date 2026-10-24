package fr.phonemcp;

import java.io.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Pure byte-stream boundary, also exercised on the JVM with real temporary files. */
final class UploadReceiver {
    static final int MAX_CHUNK_BYTES=128*1024;
    interface Check {void run()throws Exception;}
    static byte[] decode(String value){
        if(value==null||value.isEmpty()||value.length()>174764)throw new IllegalArgumentException("Bloc de transfert invalide.");
        byte[] bytes;try{bytes=Base64.getDecoder().decode(value);}catch(IllegalArgumentException e){throw new IllegalArgumentException("Encodage du bloc invalide.");}
        if(bytes.length<1||bytes.length>MAX_CHUNK_BYTES)throw new IllegalArgumentException("Bloc de transfert trop volumineux.");return bytes;
    }
    static long append(Path stage,long total,long offset,byte[] bytes)throws Exception{
        FilePolicy.limit(total);if(offset<0||bytes.length<1||bytes.length>MAX_CHUNK_BYTES||offset>total-bytes.length)throw new IllegalArgumentException("Position ou taille du bloc invalide.");
        if(Files.isSymbolicLink(stage))throw new SecurityException("Fichier de réception invalide.");
        try(FileChannel file=FileChannel.open(stage,StandardOpenOption.READ,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS)){
            long done=file.size();if(done>total||offset>done)throw new IllegalArgumentException("Bloc hors séquence. Consultez la progression avant de reprendre.");
            file.position(offset);
            if(offset<done){
                if(offset+bytes.length>done)throw new IllegalArgumentException("Bloc partiellement reçu : reprenez à la position confirmée.");
                ByteBuffer previous=ByteBuffer.allocate(bytes.length);while(previous.hasRemaining())if(file.read(previous)<0)throw new EOFException("Bloc incomplet.");
                if(!MessageDigest.isEqual(previous.array(),bytes))throw new SecurityException("Un bloc différent existe déjà à cette position.");return done;
            }
            ByteBuffer input=ByteBuffer.wrap(bytes);while(input.hasRemaining())file.write(input);return file.size();
        }
    }
    static String finish(Path source,Path part,Path target,long total,String expected,Check check)throws Exception{
        FilePolicy.limit(total);FilePolicy.hash(expected,true);if(Files.size(source)!=total||Files.isSymbolicLink(source)||Files.exists(target,LinkOption.NOFOLLOW_LINKS))throw new IOException("Transfert incomplet ou destination déjà présente.");
        MessageDigest hash=MessageDigest.getInstance("SHA-256");long count=0;
        try{
            try(InputStream in=Files.newInputStream(source,LinkOption.NOFOLLOW_LINKS);OutputStream out=Files.newOutputStream(part,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)){
                byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))!=-1){check.run();count+=n;if(count>total)throw new IOException("Taille du fichier différente.");out.write(buffer,0,n);hash.update(buffer,0,n);}
            }
            check.run();String actual=hex(hash.digest());if(count!=total||!MessageDigest.isEqual(actual.getBytes(StandardCharsets.US_ASCII),expected.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII)))throw new SecurityException("Empreinte SHA-256 différente : fichier refusé.");
            Files.move(part,target);return actual;
        }finally{Files.deleteIfExists(part);}
    }
    static String hex(byte[] bytes){StringBuilder result=new StringBuilder();for(byte b:bytes)result.append(String.format(Locale.ROOT,"%02x",b&255));return result.toString();}
}
