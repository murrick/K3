package org.kanger;
import java.lang.reflect.Field;
import java.util.*;
import org.kanger.storage.*;
import org.kanger.units.TValue;
/** Diagnostic only. Caller must exclude all concurrent storage and unit writes.
 * Locks do not establish that precondition: Base invalidates its cache outside
 * its storage locker. No persistent factory lookup or journal admission here. */
public final class ResidentPersistentRead {
    static Object raw(Object o,Class<?> c,String n)throws Exception {Field f=c.getDeclaredField(n);f.setAccessible(true);return f.get(o);}
    static Object raw(Object o,String n)throws Exception{return raw(o,o.getClass(),n);}
    static void reject(String why){throw new AssertionError("unsupported persistent boundary: "+why);}
    public static final class Row {
        public final long id,variable,term,mindId;
        Row(TValue v)throws Exception{id=(Long)raw(v,"id");variable=(Long)raw(v,"tVarId");term=(Long)raw(v,"valueId");mindId=(Long)raw(v,"mindId");}
        public String toString(){return id+":"+variable+":"+term+":"+mindId;}
    }
    @SuppressWarnings("unchecked")
    public static List<Row> snapshot(Object candidate)throws Exception {
        if(candidate==null||candidate.getClass()!=Base.class)reject("base class");
        Base base=(Base)candidate;Object locker=raw(base,"locker");
        synchronized(locker){
            Object data=raw(base,"data");if(data==null||data.getClass()!=Data.class||raw(data,"ras")==null)reject("closed or custom data");
            RecordedLinks.requireSettled(base);
            Object integrity=raw(base,"integrity");if(integrity==null||integrity.getClass()!=Class.forName("org.kanger.storage.IntegrityManifest"))reject("integrity class");
            Map<Long,?> entries=(Map<Long,?>)raw(integrity,"entries");Set<Long> expected=new HashSet<>(entries.keySet());
            Long root=(Long)raw(base,"rootId"),tail=(Long)raw(base,"topId");
            Map<Long,Object> cache=(Map<Long,Object>)raw(base,"cache");
            synchronized(cache){
                if(expected.isEmpty()){
                    if(root!=null||tail!=null||!cache.isEmpty())reject("empty endpoints/cache");
                    return Collections.emptyList();
                }
                if(!(Boolean)raw(base,"cacheEnabled"))reject("disabled cache");
                if((root==null)!=(tail==null))reject("partial endpoints");
                // Iteration/copy, never access-ordered cache.get(): preserve LRU.
                Map<Long,Object> resident=new HashMap<>();for(Map.Entry<Long,Object> e:cache.entrySet())resident.put(e.getKey(),e.getValue());
                if(root==null){
                    Set<Long> referenced=new HashSet<>();Long inferredTail=null;
                    for(Long key:expected){Object step=resident.get(key);if(step==null)reject("missing resident link "+key);
                        if(step.getClass()!=Sapato.class||raw(step,"base")!=base||(Long)raw(step,"id")!=key.longValue())reject("invalid resident node");
                        long next=RecordedLinks.verifiedNext(step,base,key);
                        if(next==-1){if(inferredTail!=null)reject("multiple tails");inferredTail=key;}
                        else{if(!expected.contains(next))reject("dangling saved link");referenced.add(next);}}
                    Set<Long> roots=new HashSet<>(expected);roots.removeAll(referenced);
                    if(roots.size()!=1||inferredTail==null)reject("invalid saved endpoints");
                    root=roots.iterator().next();tail=inferredTail;
                }
                Set<Long> visited=new HashSet<>();List<Row> rows=new ArrayList<>();long id=root;
                for(;;){
                    if(!visited.add(id))reject("cycle");Object step=resident.get(id);
                    if(step==null)reject("missing resident link "+id);
                    if(step.getClass()!=Sapato.class)reject("step class");
                    if(raw(step,"base")!=base)reject("foreign base");
                    if((Long)raw(step,"id")!=id)reject("cache/step ID mismatch");
                    Object unit=raw(step,"data");if(unit==null||unit.getClass()!=TValue.class)reject("unresolved or custom TValue");
                    Row row=new Row((TValue)unit);if(row.id!=id)reject("step/unit ID mismatch");rows.add(row);
                    long next=(Long)raw(step,"next");if(next==-1){if(id!=tail)reject("tail mismatch");break;}id=next;
                }
                if(!visited.equals(expected))reject("incomplete integrity universe");
                return Collections.unmodifiableList(rows);
            }
        }
    }
}
