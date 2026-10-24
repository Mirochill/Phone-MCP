package fr.phonemcp;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import org.json.*;
import java.io.*;
import java.nio.charset.*;
import java.util.*;

/** Document access is confined to persisted folders explicitly chosen with Android's picker. */
final class StorageAccess {
    private final Context context;
    StorageAccess(Context context){this.context=context;}
    private android.content.SharedPreferences prefs(){return context.getSharedPreferences("document-folders",Context.MODE_PRIVATE);}
    void add(Uri uri,int flags)throws Exception{
        if(!DocumentsContract.isTreeUri(uri)||!"content".equals(uri.getScheme()))throw new IllegalArgumentException("Select a document folder.");
        int permission=flags&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        if((permission&Intent.FLAG_GRANT_READ_URI_PERMISSION)==0)throw new SecurityException("Read permission was not granted.");
        context.getContentResolver().takePersistableUriPermission(uri,permission);
        for(Map.Entry<String,?> entry:prefs().getAll().entrySet())if(uri.toString().equals(entry.getValue()))return;
        prefs().edit().putString(UUID.randomUUID().toString(),uri.toString()).apply();
    }
    void remove(String id){String uri=prefs().getString(id,null);if(uri==null)return;try{context.getContentResolver().releasePersistableUriPermission(Uri.parse(uri),Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);}catch(Exception ignored){}prefs().edit().remove(id).apply();}
    JSONArray locations()throws Exception{
        JSONArray out=new JSONArray();for(String id:prefs().getAll().keySet())try{Uri tree=tree(id);JSONObject item=info(root(tree));item.remove("documentId");out.put(item.put("locationId",id));}catch(Exception ignored){}return out;
    }
    JSONObject execute(String op,JSONObject args)throws Exception{
        if(!BridgeService.active()||!new Settings(context).filesAllowed())throw new SecurityException("Start a session and enable Files first.");
        if("storage_locations".equals(op))return new JSONObject().put("locations",locations());
        Uri tree=tree(args.getString("locationId"));String path=args.optString("path");String[] parts=parts(path);
        if("write_file".equals(op)){
            if(parts.length==0)throw new IllegalArgumentException("Specify a file name.");
            Uri parent=resolve(tree,Arrays.copyOf(parts,parts.length-1)),file=find(tree,parent,parts[parts.length-1]);
            if(file!=null&&!args.optBoolean("overwrite"))throw new IllegalStateException("File exists. Set overwrite=true only for an explicitly requested replacement.");
            if(file==null)file=DocumentsContract.createDocument(context.getContentResolver(),parent,"text/plain",parts[parts.length-1]);
            if(file==null||info(file).optBoolean("directory"))throw new IllegalStateException("File could not be created.");
            byte[] bytes=args.getString("text").getBytes(StandardCharsets.UTF_8);if(bytes.length>240000)throw new IllegalArgumentException("Text is too large.");
            try(OutputStream stream=context.getContentResolver().openOutputStream(file,"wt")){if(stream==null)throw new IOException("File is not writable.");stream.write(bytes);}
            return new JSONObject().put("written",true).put("bytes",bytes.length).put("path",path);
        }
        Uri target=resolve(tree,parts);JSONObject meta=info(target);
        if("list_files".equals(op)){
            if(!meta.optBoolean("directory"))throw new IllegalArgumentException("This path is not a directory.");
            List<JSONObject> children=children(tree,target);children.sort(Comparator.comparing(v->v.optString("name"),String.CASE_INSENSITIVE_ORDER));
            int offset=args.optInt("offset"),limit=args.optInt("limit",100);if(offset<0||limit<1||limit>200)throw new IllegalArgumentException("Invalid page.");
            JSONArray page=new JSONArray();for(int i=offset;i<Math.min(children.size(),offset+limit);i++){JSONObject item=children.get(i);item.remove("documentId");page.put(item);}
            JSONObject out=new JSONObject().put("files",page).put("total",children.size()).put("path",path);if(offset+page.length()<children.size())out.put("nextOffset",offset+page.length());return out;
        }
        if(meta.optBoolean("directory"))throw new IllegalArgumentException("A file is required; directory deletion is not supported.");
        if("read_file".equals(op)){try(InputStream in=context.getContentResolver().openInputStream(target)){if(in==null)throw new IOException("File is not readable.");return textPage(in,args).put("name",meta.optString("name"));}}
        if("delete_file".equals(op)){boolean deleted=DocumentsContract.deleteDocument(context.getContentResolver(),target);if(!deleted)throw new IOException("The provider refused deletion.");return new JSONObject().put("deleted",true).put("path",path);}
        throw new IllegalArgumentException("Unknown document operation.");
    }
    static JSONObject textPage(InputStream input,JSONObject args)throws Exception{
        byte[] bytes=CompatIO.readAtMost(input,1048577);if(bytes.length>1048576)throw new IllegalArgumentException("Text files are limited to 1 MiB.");
        String text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        if(text.indexOf('\0')>=0)throw new IllegalArgumentException("This is not a UTF-8 text file.");
        String[] lines=text.split("\\r?\\n",-1);int offset=args.optInt("offset",1),limit=args.optInt("limit",100);if(offset<1||limit<1||limit>200)throw new IllegalArgumentException("Invalid line page.");
        JSONArray page=new JSONArray();int chars=0,i=offset-1;for(;i<lines.length&&page.length()<limit;i++){String value=lines[i];if(chars+value.length()>60000)break;chars+=value.length();page.put(new JSONObject().put("line",i+1).put("text",value));}
        if(i==offset-1&&i<lines.length)throw new IllegalArgumentException("A line exceeds the 60000 character page limit.");
        JSONObject out=new JSONObject().put("lines",page).put("totalLines",lines.length).put("offset",offset);if(i<lines.length)out.put("nextOffset",i+1);return out;
    }
    private Uri tree(String id)throws Exception{UUID.fromString(id);String value=prefs().getString(id,null);if(value==null)throw new SecurityException("Folder is not authorized.");Uri tree=Uri.parse(value);boolean allowed=false;for(UriPermission p:context.getContentResolver().getPersistedUriPermissions())if(p.getUri().equals(tree)&&p.isReadPermission())allowed=true;if(!allowed)throw new SecurityException("Folder access was revoked. Select it again on the phone.");return tree;}
    private static Uri root(Uri tree){return DocumentsContract.buildDocumentUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree));}
    private static String[] parts(String path){if(path.isEmpty())return new String[0];if(path.startsWith("/")||path.contains("\\")||path.length()>500)throw new IllegalArgumentException("Invalid relative path.");String[] parts=path.split("/",-1);for(String p:parts)if(p.isEmpty()||p.equals(".")||p.equals("..")||p.chars().anyMatch(c->c<32))throw new IllegalArgumentException("Invalid path segment.");return parts;}
    private Uri resolve(Uri tree,String[] parts)throws Exception{Uri at=root(tree);for(String part:parts){at=find(tree,at,part);if(at==null)throw new FileNotFoundException("Path not found.");}return at;}
    private Uri find(Uri tree,Uri parent,String name)throws Exception{for(JSONObject child:children(tree,parent))if(name.equals(child.optString("name")))return DocumentsContract.buildDocumentUriUsingTree(tree,child.getString("documentId"));return null;}
    private List<JSONObject> children(Uri tree,Uri parent)throws Exception{Uri query=DocumentsContract.buildChildDocumentsUriUsingTree(tree,DocumentsContract.getDocumentId(parent));List<JSONObject> out=new ArrayList<>();try(Cursor c=context.getContentResolver().query(query,columns(),null,null,null)){if(c==null)throw new IOException("Folder unavailable.");while(c.moveToNext()){if(out.size()>=10000)throw new IllegalStateException("Directory exceeds 10000 entries.");out.add(row(c));}}return out;}
    private JSONObject info(Uri doc)throws Exception{try(Cursor c=context.getContentResolver().query(doc,columns(),null,null,null)){if(c==null||!c.moveToFirst())throw new FileNotFoundException("Document unavailable.");return row(c);}}
    private static String[] columns(){return new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE,DocumentsContract.Document.COLUMN_SIZE,DocumentsContract.Document.COLUMN_FLAGS};}
    private static JSONObject row(Cursor c)throws Exception{return new JSONObject().put("documentId",c.getString(0)).put("name",c.getString(1)).put("mimeType",c.getString(2)).put("directory",DocumentsContract.Document.MIME_TYPE_DIR.equals(c.getString(2))).put("sizeBytes",c.isNull(3)?JSONObject.NULL:c.getLong(3)).put("writable",(c.getInt(4)&DocumentsContract.Document.FLAG_SUPPORTS_WRITE)!=0);}
}
