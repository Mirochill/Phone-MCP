package fr.phonemcp;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.SystemClock;
import android.system.Os;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import javax.net.ssl.HttpsURLConnection;

/** Typed, session-scoped file operations. No arbitrary path or shell-command endpoint. */
final class FileEngine {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService timeouts=Executors.newSingleThreadScheduledExecutor();
    private final Map<String,Job> jobs=new LinkedHashMap<>();
    private final Context context;
    private final int user;
    private final File records,downloads;
    private volatile long deadline;
    private volatile Job active;
    private static final class Job {
        final JSONObject value;
        volatile boolean cancelled;
        volatile HttpsURLConnection connection;
        volatile java.lang.Process process;
        Job(JSONObject v){value=v;}
    }
    FileEngine(Context context,int ownerUid)throws Exception {
        this.context=context;user=ownerUid/100000;
        records=new File("/data/local/tmp/phone-mcp-"+ownerUid);
        downloads=new File("/storage/emulated/"+user+"/Download/PhoneMCP");
    }
    synchronized void start(long end)throws Exception {
        if(android.os.Process.myUid()!=2000||!SessionPolicy.validDeadline(SystemClock.elapsedRealtime(),end))throw new SecurityException("Service fichiers Shizuku non autorisé.");
        ensureDirectory(records);Os.chmod(records.getPath(),0700);ensureDirectory(downloads);
        if(jobs.isEmpty()){
            File[] saved=records.listFiles((dir,name)->name.matches("[0-9a-f-]{36}\\.json"));
            if(saved!=null){Arrays.sort(saved,Comparator.comparingLong(File::lastModified).reversed());
                for(File file:Arrays.copyOf(saved,Math.min(saved.length,128)))try{
                    if(file.length()>10000||Files.isSymbolicLink(file.toPath()))continue;
                    Job job=new Job(new JSONObject(new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8)));
                    if(!terminal(job.value.optString("state")))job.value.put("state","interrupted").put("error","Opération interrompue. Vérifiez le résultat avant de recommencer.");
                    jobs.put(FilePolicy.id(job.value.getString("jobId")),job);
                }catch(Exception ignored){}
            }
        }
        // Staging copies are private, disposable APK bytes, including after a self-update kills us.
        File[] stale=records.listFiles((dir,name)->name.matches("[0-9a-f-]{36}\\.apk"));
        if(stale!=null)for(File file:stale)if(!Files.isSymbolicLink(file.toPath()))Files.deleteIfExists(file.toPath());
        deadline=end;
    }
    private static void ensureDirectory(File dir)throws Exception {
        if(Files.isSymbolicLink(dir.toPath())||(!dir.isDirectory()&&!dir.mkdirs()))throw new IOException("Dossier PhoneMCP inaccessible.");
        File current=dir;
        while(current!=null){if(Files.isSymbolicLink(current.toPath()))throw new SecurityException("Un lien symbolique remplace le dossier de transfert.");current=current.getParentFile();}
    }
    private void requireSession(){if(!SessionPolicy.validDeadline(SystemClock.elapsedRealtime(),deadline))throw new IllegalStateException("Session fichiers arrêtée ou expirée.");}
    private void check(Job job){requireSession();if(job.cancelled||Thread.currentThread().isInterrupted())throw new CancellationException("Opération annulée.");}
    synchronized JSONObject status()throws JSONException {
        return new JSONObject().put("fileServiceReady",deadline>SystemClock.elapsedRealtime()).put("fileJobActive",active!=null)
                .put("activeFileJob",active==null?JSONObject.NULL:copy(active)).put("filesFolder","Download/PhoneMCP").put("maxFileBytes",FilePolicy.MAX_BYTES);
    }
    synchronized JSONObject execute(String op,JSONObject args)throws Exception {
        requireSession();
        if("files_list".equals(op)){
            JSONArray files=new JSONArray(),recent=new JSONArray();List<Job> values=new ArrayList<>(jobs.values());
            values.sort(Comparator.comparingLong((Job j)->j.value.optLong("createdAt")).reversed());
            for(Job job:values){
                if(recent.length()<20)recent.put(copy(job));
                if("download".equals(job.value.optString("kind"))&&"completed".equals(job.value.optString("state")))try{
                    File file=file(job.value.getString("jobId"),job.value.getString("fileName"));
                    if(file.isFile()&&files.length()<100)files.put(copy(job).put("fileId",job.value.getString("jobId")).put("sizeBytes",file.length()));
                }catch(Exception ignored){}
            }
            return new JSONObject().put("files",files).put("jobs",recent).put("folder","Download/PhoneMCP");
        }
        if("file_job".equals(op)){
            Job job=jobs.get(FilePolicy.id(args.getString("jobId")));if(job==null)throw new IllegalArgumentException("Opération introuvable. Consultez phone_files_list avant de relancer.");
            if("cancel".equals(args.optString("action","status"))&&!terminal(job.value.optString("state"))){
                if("installing".equals(job.value.optString("state")))throw new IllegalStateException("L’installation a été remise à Android ; elle ne peut plus être annulée ici.");
                cancel(job);
            }
            return copy(job);
        }
        if("files_cancel_all".equals(op)){if(active!=null)cancel(active);return new JSONObject().put("cancelled",true);}
        String request=FilePolicy.id(args.getString("requestId"));
        Job existing=jobs.get(request);
        String fingerprint=fingerprint(op,args);
        if(existing!=null){if(!fingerprint.equals(existing.value.optString("requestFingerprint")))throw new IllegalArgumentException("Cet identifiant appartient à une autre opération.");return copy(existing);}
        if(active!=null)throw new IllegalStateException("Un transfert ou une installation est déjà en cours. Consultez phone_file_job.");
        if(jobs.size()>=128)throw new IllegalStateException("Historique complet (128 opérations). Les fichiers existants restent disponibles.");
        Job job;
        if("download_file".equals(op)){
            String url=FilePolicy.url(args.getString("url")),name=FilePolicy.name(args.getString("fileName"));
            String hash=FilePolicy.hash(args.optString("sha256"),false);long limit=FilePolicy.limit(args.optLong("maxBytes",FilePolicy.MAX_BYTES));
            job=new Job(new JSONObject().put("jobId",request).put("kind","download").put("fileName",name).put("state","queued").put("createdAt",System.currentTimeMillis()).put("bytesDone",0).put("requestFingerprint",fingerprint));
            begin(job,()->download(job,url,name,hash,limit));
        }else if("install_apk".equals(op)){
            String fileId=FilePolicy.id(args.getString("fileId")),hash=FilePolicy.hash(args.getString("sha256"),true),pkg=FilePolicy.pkg(args.getString("expectedPackage"));
            Job source=jobs.get(fileId);if(source==null||!"download".equals(source.value.optString("kind"))||!"completed".equals(source.value.optString("state")))throw new IllegalArgumentException("Téléchargez et vérifiez d’abord l’APK avec phone_download_file.");
            String name=source.value.getString("fileName");if(!name.toLowerCase(Locale.ROOT).endsWith(".apk"))throw new IllegalArgumentException("Le fichier doit être une APK unique ; les archives XAPK/APKS ne sont pas prises en charge.");
            job=new Job(new JSONObject().put("jobId",request).put("kind","install").put("fileId",fileId).put("fileName",name).put("packageName",pkg).put("state","queued").put("createdAt",System.currentTimeMillis()).put("requestFingerprint",fingerprint));
            begin(job,()->install(job,file(fileId,name),hash,pkg));
        }else throw new IllegalArgumentException("Commande fichiers inconnue.");
        return copy(job);
    }
    private interface Work {void run()throws Exception;}
    private synchronized void begin(Job job,Work task)throws Exception {
        jobs.put(job.value.getString("jobId"),job);active=job;save(job);
        worker.execute(()->{
            try{check(job);task.run();}
            catch(Exception e){synchronized(this){try{job.value.put("state",job.cancelled?"cancelled":"failed").put("error",safeError(e));}catch(JSONException ignored){}}}
            finally{synchronized(this){try{save(job);}catch(Exception ignored){}if(active==job)active=null;}}
        });
    }
    private void download(Job job,String source,String name,String expected,long limit)throws Exception {
        File dir=new File(downloads,job.value.getString("jobId"));ensureDirectory(dir);
        File target=file(job.value.getString("jobId"),name),part=new File(dir,".download.part");
        if(target.exists()||part.exists())throw new IOException("Le transfert existe déjà. Vérifiez son état.");
        HttpsURLConnection connection=null;
        try{
            String url=source;
            for(int hop=0;hop<=5;hop++){
                check(job);connection=(HttpsURLConnection)new URL(FilePolicy.url(url)).openConnection();job.connection=connection;
                connection.setConnectTimeout(15000);connection.setReadTimeout(30000);connection.setInstanceFollowRedirects(false);connection.setRequestProperty("Accept-Encoding","identity");
                int code=connection.getResponseCode();
                if(code>=300&&code<400){String location=connection.getHeaderField("Location");connection.disconnect();if(location==null||hop==5)throw new IOException("Trop de redirections du téléchargement.");url=new URL(new URL(url),location).toString();continue;}
                if(code!=200)throw new IOException("Téléchargement refusé (HTTP "+code+").");break;
            }
            long total=connection.getContentLengthLong();if(total>limit)throw new IOException("Fichier trop volumineux.");
            update(job,"downloading",0,total);MessageDigest digest=MessageDigest.getInstance("SHA-256");long count=0,last=0,start=SystemClock.elapsedRealtime();
            try(InputStream input=connection.getInputStream();OutputStream output=Files.newOutputStream(part.toPath(),StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)){
                byte[] data=new byte[65536];int read;
                while((read=input.read(data))!=-1){check(job);count+=read;if(count>limit||SystemClock.elapsedRealtime()-start>10*60*1000L)throw new IOException("Limite de taille ou délai de téléchargement dépassé.");output.write(data,0,read);digest.update(data,0,read);if(count-last>=262144){update(job,"downloading",count,total);last=count;}}
            }
            check(job);if(count==0||(total>=0&&count!=total))throw new IOException("Téléchargement vide ou incomplet.");String hash=hex(digest.digest());
            if(!expected.isEmpty()&&!MessageDigest.isEqual(hash.getBytes(StandardCharsets.US_ASCII),expected.getBytes(StandardCharsets.US_ASCII)))throw new SecurityException("Empreinte SHA-256 différente : fichier refusé.");
            if(!part.renameTo(target))throw new IOException("Impossible de terminer le fichier.");
            synchronized(this){job.value.put("state","completed").put("bytesDone",count).put("totalBytes",count).put("sha256",hash).put("fileId",job.value.getString("jobId")).put("folder","Download/PhoneMCP/"+job.value.getString("jobId"));addArchiveInfo(job,target);save(job);}
        }finally{job.connection=null;if(connection!=null)connection.disconnect();if(part.exists())Files.deleteIfExists(part.toPath());}
    }
    private void install(Job job,File source,String expected,String pkg)throws Exception {
        // Copy + hash to shell-private storage. The bytes installed cannot be replaced from shared Downloads.
        File staged=new File(records,job.value.getString("jobId")+".apk");
        java.lang.Process process=null;ScheduledFuture<?> watchdog=null;
        try{
            update(job,"verifying",0,source.length());MessageDigest digest=MessageDigest.getInstance("SHA-256");long bytes=0;
            try(InputStream in=Files.newInputStream(source.toPath(),LinkOption.NOFOLLOW_LINKS);OutputStream out=Files.newOutputStream(staged.toPath(),StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)){
                byte[] block=new byte[65536];int n;while((n=in.read(block))!=-1){check(job);bytes+=n;if(bytes>FilePolicy.MAX_BYTES)throw new IOException("APK trop volumineuse.");out.write(block,0,n);digest.update(block,0,n);}
            }
            Os.chmod(staged.getPath(),0600);String actual=hex(digest.digest());
            if(!MessageDigest.isEqual(actual.getBytes(StandardCharsets.US_ASCII),expected.getBytes(StandardCharsets.US_ASCII)))throw new SecurityException("L’APK ne correspond plus à l’empreinte attendue. Aucune installation effectuée.");
            PackageInfo info=context.getPackageManager().getPackageArchiveInfo(staged.getPath(),0);
            if(info==null||!pkg.equals(info.packageName))throw new SecurityException("L’APK ne correspond pas à l’application attendue.");
            check(job);synchronized(this){job.value.put("sha256",actual).put("versionCode",info.getLongVersionCode()).put("versionName",info.versionName==null?"":info.versionName);}
            update(job,"installing",bytes,bytes);
            process=new ProcessBuilder(FilePolicy.installArgs(pkg,bytes,user)).redirectErrorStream(true).start();job.process=process;
            final java.lang.Process running=process;ByteArrayOutputStream response=new ByteArrayOutputStream();
            watchdog=timeouts.schedule(()->{if(running.isAlive())running.destroyForcibly();},120,TimeUnit.SECONDS);
            Thread drain=new Thread(()->{try(InputStream in=running.getInputStream()){byte[] block=new byte[1024];int n;while((n=in.read(block))!=-1){if(response.size()<16000)response.write(block,0,Math.min(n,16000-response.size()));}}catch(IOException ignored){}},"phone-mcp-install-result");drain.start();
            // stdin is an APK byte stream, never shell text.
            try(InputStream in=Files.newInputStream(staged.toPath());OutputStream out=process.getOutputStream()){byte[] block=new byte[65536];int n;while((n=in.read(block))!=-1){check(job);out.write(block,0,n);}}
            if(!process.waitFor(120,TimeUnit.SECONDS)){process.destroyForcibly();throw new IOException("Android n’a pas confirmé l’installation. Vérifiez la version installée avant de réessayer.");}
            watchdog.cancel(false);drain.join(2000);String message=response.toString("UTF-8");
            if(process.exitValue()!=0||!message.trim().equals("Success"))throw new IOException("Installation refusée par Android : "+message.substring(0,Math.min(message.length(),1200)));
            PackageInfo installed=context.getPackageManager().getPackageInfo(pkg,0);
            if(installed.getLongVersionCode()!=info.getLongVersionCode())throw new IOException("La version installée ne correspond pas au résultat attendu.");
            synchronized(this){job.value.put("state","completed").put("installed",true).put("installedVersion",installed.versionName==null?"":installed.versionName);save(job);}
        }finally{if(watchdog!=null)watchdog.cancel(false);job.process=null;if(process!=null&&process.isAlive())process.destroyForcibly();Files.deleteIfExists(staged.toPath());}
    }
    private void addArchiveInfo(Job job,File file){try{if(file.getName().toLowerCase(Locale.ROOT).endsWith(".apk")){PackageInfo info=context.getPackageManager().getPackageArchiveInfo(file.getPath(),0);if(info!=null)job.value.put("packageName",info.packageName).put("versionName",info.versionName==null?"":info.versionName).put("versionCode",info.getLongVersionCode());}}catch(Exception ignored){}}
    private File file(String id,String name)throws Exception {
        File dir=new File(downloads,FilePolicy.id(id)),file=new File(dir,FilePolicy.name(name));
        ensureDirectory(downloads);if(Files.isSymbolicLink(dir.toPath())||Files.isSymbolicLink(file.toPath())||!file.getCanonicalPath().equals(new File(downloads.getCanonicalFile(),id+"/"+name).getPath()))throw new SecurityException("Chemin de fichier refusé.");
        return file;
    }
    private synchronized void update(Job job,String state,long done,long total)throws Exception {job.value.put("state",state).put("bytesDone",done).put("totalBytes",total);}
    private synchronized void save(Job job)throws Exception {
        File dest=new File(records,FilePolicy.id(job.value.getString("jobId"))+".json"),temp=new File(records,dest.getName()+".tmp");
        if(Files.isSymbolicLink(dest.toPath())||Files.isSymbolicLink(temp.toPath()))throw new SecurityException("Historique de transfert invalide.");
        Files.write(temp.toPath(),job.value.toString().getBytes(StandardCharsets.UTF_8));Os.chmod(temp.getPath(),0600);Files.move(temp.toPath(),dest.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
    }
    private static JSONObject copy(Job job)throws JSONException {JSONObject value=new JSONObject(job.value.toString());value.remove("requestFingerprint");return value;}
    private static String fingerprint(String op,JSONObject args)throws Exception {
        // Canonical field order avoids ambiguous duplicate request IDs after SDK retries.
        String[] fields="download_file".equals(op)?new String[]{"url","fileName","sha256","maxBytes"}:new String[]{"fileId","sha256","expectedPackage"};
        JSONArray values=new JSONArray().put(op);for(String key:fields){Object value=args.opt(key);if("maxBytes".equals(key))value=args.optLong(key,FilePolicy.MAX_BYTES);else if("sha256".equals(key))value=args.optString(key,"").toLowerCase(Locale.ROOT);values.put(value);}return hex(MessageDigest.getInstance("SHA-256").digest(values.toString().getBytes(StandardCharsets.UTF_8)));
    }
    private static String hex(byte[] bytes){StringBuilder text=new StringBuilder();for(byte b:bytes)text.append(String.format(Locale.ROOT,"%02x",b&255));return text.toString();}
    private static boolean terminal(String state){return Arrays.asList("completed","failed","cancelled","interrupted").contains(state);}
    private static String safeError(Exception e){String value=e.getMessage();if(value==null)return "Opération impossible.";value=value.replaceAll("https?://[^\\s]+","[lien]");return value.substring(0,Math.min(value.length(),1400));}
    private void cancel(Job job){job.cancelled=true;if(job.connection!=null)job.connection.disconnect();}
    synchronized void stop(){deadline=0;if(active!=null){cancel(active);if(active.process!=null)active.process.destroyForcibly();}}
    void destroy(){stop();worker.shutdownNow();timeouts.shutdownNow();}
}
