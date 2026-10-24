package fr.phonemcp;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** On-demand dumpsys transport. Streams lines to a privacy-filtering parser; retains no dump. */
final class BoundedDumpReader {
    interface LineSink {void line(String line);}
    static final class Result {
        volatile boolean completed,timedOut,sizeLimited,lineTooLong,readerDone;
        volatile int exitCode=-1,bytesRead;
        volatile String errorCode="";
    }
    private static final int MAX_BYTES=2*1024*1024,MAX_LINE=32768;
    private static final class Limit extends IOException {private static final long serialVersionUID=1L;}
    private static final class Counted extends FilterInputStream {
        final int limit;final Result result;
        Counted(InputStream input,int limit,Result result){super(input);this.limit=limit;this.result=result;}
        private void count(int n)throws IOException{if(n>0){result.bytesRead+=n;if(result.bytesRead>limit)throw new Limit();}}
        @Override public int read()throws IOException{int value=in.read();if(value>=0)count(1);return value;}
        @Override public int read(byte[] bytes,int offset,int length)throws IOException{
            int n=in.read(bytes,offset,Math.min(length,Math.max(1,limit-result.bytesRead+1)));count(n);return n;
        }
    }
    static void consume(InputStream input,int maxBytes,LineSink sink,Result result){
        try(Reader reader=new InputStreamReader(new Counted(input,maxBytes,result),StandardCharsets.UTF_8)){
            char[] chunk=new char[2048];StringBuilder line=new StringBuilder();int n;
            while((n=reader.read(chunk))!=-1){
                for(int i=0;i<n;i++){
                    char c=chunk[i];
                    if(c=='\n'){
                        int end=line.length();if(end>0&&line.charAt(end-1)=='\r')line.setLength(end-1);
                        String text=line.toString();line.setLength(0);
                        if(text.startsWith("*** SERVICE '")&&text.contains("DUMP TIMEOUT")){result.timedOut=true;result.errorCode="service_timeout";return;}
                        sink.line(text);
                    }else{
                        if(line.length()>=MAX_LINE){result.lineTooLong=true;result.errorCode="line_limit";return;}
                        line.append(c);
                    }
                }
            }
            if(line.length()>0)sink.line(line.toString());
        }catch(Limit oversized){result.sizeLimited=true;result.errorCode="size_limit";}
        catch(IOException failed){if(result.errorCode.isEmpty())result.errorCode="stream_unavailable";}
        catch(RuntimeException failed){result.errorCode="parser_failed";}
        finally{result.readerDone=true;}
    }
    static Result run(String service,int maxBytes,int timeoutMs,LineSink sink){
        Result result=new Result();
        if(!("display".equals(service)||"power".equals(service)||"input".equals(service))
                ||maxBytes<1||maxBytes>MAX_BYTES||timeoutMs<50||timeoutMs>1500||sink==null){
            result.errorCode="invalid_request";return result;
        }
        java.lang.Process process=null;Thread reader=null;
        try{
            // Neither the executable nor service selection comes from a shell command string.
            process=new ProcessBuilder("/system/bin/dumpsys","-t","1",service).redirectErrorStream(true).start();
            final InputStream stream=process.getInputStream();
            reader=new Thread(()->consume(stream,maxBytes,sink,result),"phone-group-diagnostic");reader.setDaemon(true);reader.start();
            if(!process.waitFor(timeoutMs,TimeUnit.MILLISECONDS)){
                result.timedOut=true;result.errorCode="deadline";
            }else result.exitCode=process.exitValue();
            if(result.timedOut||process.isAlive())process.destroyForcibly();
            reader.join(80);
            if(!result.readerDone){stream.close();reader.join(80);}
            result.completed=result.readerDone&&result.exitCode==0&&!result.timedOut&&!result.sizeLimited&&!result.lineTooLong&&result.errorCode.isEmpty();
            if(result.errorCode.isEmpty()&&!result.completed)result.errorCode=result.exitCode==0?"stream_incomplete":"command_failed";
        }catch(InterruptedException interrupted){Thread.currentThread().interrupt();result.errorCode="interrupted";}
        catch(IOException failed){if(result.errorCode.isEmpty())result.errorCode="command_unavailable";}
        finally{
            if(process!=null){
                if(process.isAlive())process.destroyForcibly();
                try{process.getInputStream().close();}catch(IOException ignored){}
                try{process.getErrorStream().close();}catch(IOException ignored){}
                try{process.getOutputStream().close();}catch(IOException ignored){}
            }
        }
        return result;
    }
}