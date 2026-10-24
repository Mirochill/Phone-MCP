package fr.phonemcp;

import org.json.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/** Pages refer to one device observation, never to a silently refreshed tree. */
final class SnapshotPager {
    private JSONObject current;
    private JSONObject selection;
    void clear(){current=null;}
    JSONArray nodes(){return current==null?new JSONArray():current.optJSONArray("nodes");}
    JSONObject capture(JSONObject value,JSONObject args)throws Exception{
        current=value;selection=new JSONObject(args.toString());
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(value.getJSONArray("nodes").toString().getBytes(StandardCharsets.UTF_8));
        StringBuilder hash=new StringBuilder();for(byte b:digest)hash.append(String.format(Locale.ROOT,"%02x",b));
        current.put("contentFingerprint",hash.toString());return page(args);
    }
    JSONObject page(JSONObject args)throws Exception{
        if(current==null)throw new IllegalStateException("Snapshot expired. Read a fresh snapshot.");
        String cursor=args.optString("cursor"),snapshot=current.getString("snapshotId");int offset=0;
        if(!cursor.isEmpty()){
            int dot=cursor.lastIndexOf('.');
            if(dot<0||!snapshot.equals(cursor.substring(0,dot)))throw new IllegalStateException("Cursor belongs to an expired snapshot.");
            try{offset=Integer.parseInt(cursor.substring(dot+1));}catch(NumberFormatException e){throw new IllegalArgumentException("Invalid cursor.");}
        }
        int limit=args.optInt("limit",200);if(limit<20||limit>350)throw new IllegalArgumentException("Page size must be between 20 and 350.");
        JSONObject options=cursor.isEmpty()?args:selection;
        JSONArray source=current.getJSONArray("nodes"),matches=new JSONArray();String query=options.optString("query").toLowerCase(Locale.ROOT),by=options.optString("by","any");
        for(int i=0;i<source.length();i++){
            JSONObject node=source.getJSONObject(i);
            if(!options.optBoolean("includeOffscreen")&&!node.optBoolean("visible",true))continue;
            if(!query.isEmpty()){
                String value;
                if("any".equals(by))value=node.optString("text")+"\n"+node.optString("description")+"\n"+node.optString("viewId")+"\n"+node.optString("class");
                else if("role".equals(by)){value=node.optString("class");value=value.substring(value.lastIndexOf('.')+1);}
                else value=node.optString(by);
                value=value.toLowerCase(Locale.ROOT);
                boolean exact="exact".equals(options.optString("match"));
                if(exact&&"any".equals(by)){boolean found=false;for(String field:new String[]{"text","description","viewId","class"})if(query.equals(node.optString(field).toLowerCase(Locale.ROOT)))found=true;if(!found)continue;}
                else if(exact?!value.equals(query):!value.contains(query))continue;
            }
            matches.put(node);
        }
        if(offset<0||offset>matches.length())throw new IllegalArgumentException("Cursor is outside this snapshot.");
        JSONArray page=new JSONArray();for(int i=offset;i<Math.min(offset+limit,matches.length());i++)page.put(matches.get(i));
        JSONObject result=new JSONObject(current.toString()).put("nodes",page).put("offset",offset).put("totalNodes",matches.length()).put("pageSize",limit);
        if(offset+page.length()<matches.length())result.put("nextCursor",snapshot+"."+(offset+page.length()));
        return result;
    }
}
