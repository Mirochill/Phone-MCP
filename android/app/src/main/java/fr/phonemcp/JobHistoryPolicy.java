package fr.phonemcp;
import java.util.*;
/** Bound the memory index only; completed disk journals keep idempotency and file access. */
final class JobHistoryPolicy {
 static final int MAX_CACHED=128;
 static final class Record{final String id;final long created;final boolean terminal,active;Record(String id,long created,boolean terminal,boolean active){this.id=id;this.created=created;this.terminal=terminal;this.active=active;}}
 static String evict(List<Record> records){Record selected=null;for(Record r:records)if(r!=null&&r.id!=null&&r.terminal&&!r.active&&(selected==null||r.created<selected.created))selected=r;return selected==null?null:selected.id;}
}
