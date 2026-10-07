package org.kanger;
import java.lang.reflect.Field;
import java.util.*;
import org.kanger.factory.TValueFactory;
import org.kanger.interfaces.internal.IStep;
import org.kanger.storage.*;
import org.kanger.units.TValue;
/** Diagnostic read-only reconstruction of exact resident native enumeration.
 * Reflection reads only: no index initialization, storage access or owner writes.
 * All unsupported boundaries fail closed before invoking their methods. */
public final class ResidentTValueRead {
    static Object field(Object object,String name)throws Exception{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    static Object nativeField(Object object,Class<?> type,String name)throws Exception{Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    static void unsupported(String why){throw new AssertionError("unsupported resident boundary: "+why);}
    static final class Layer {
        final TValueFactory factory;final List<TValue> chain;final Map<Long,TValue> byId;
        final Map<Long,List<Long>> routes;final TValueFactory parent;
        Layer(TValueFactory f,List<TValue> c,Map<Long,TValue> b,Map<Long,List<Long>> r,TValueFactory p){factory=f;chain=c;byId=b;routes=r;parent=p;}
    }
    @SuppressWarnings("unchecked")
    static Layer layer(TValueFactory factory)throws Exception{
        if(factory==null||factory.getClass()!=TValueFactory.class)unsupported("factory class");
        if(field(factory,"connection")!=null)unsupported("storage connection");
        Object cache=field(factory,"cache");if(cache==null||cache.getClass()!=Escalera.class)unsupported("cache class");
        IStep root=(IStep)field(cache,"root");List<TValue> chain=new ArrayList<>();Map<Long,TValue> byId=new HashMap<>();Map<Long,IStep> nodes=new HashMap<>();
        Set<IStep> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        for(IStep step=root;step!=null;){
            if(step.getClass()!=Step.class)unsupported("nonresident or custom step");
            if(!seen.add(step))unsupported("cycle");
            Object data=step.getData();if(data==null||data.getClass()!=TValue.class)unsupported("unresolved or custom TValue");
            TValue value=(TValue)data;long id=step.getId();
            if(id!=value.getId())unsupported("step/unit ID mismatch");
            if(byId.put(id,value)!=null)unsupported("duplicate chain ID");
            nodes.put(id,step);chain.add(value);step=step.getNext();
        }
        // Match the actual fast lookup table when native get would reuse it.
        // It may differ from the chain after layered publication; rebuilding
        // it here would silently repair native lookup semantics.
        if((Boolean)field(cache,"indexValid")&&field(cache,"indexedRoot")==root){
            Map<Long,IStep> actual=(Map<Long,IStep>)field(cache,"memoryById");
            if(!((Set<Long>)field(cache,"persistentIds")).isEmpty())unsupported("persistent cache index");
            byId.clear();
            for(Map.Entry<Long,IStep> e:actual.entrySet()){
                IStep step=e.getValue();if(step==null||step.getClass()!=Step.class)unsupported("lookup step class");
                Object data=step.getData();if(data==null||data.getClass()!=TValue.class)unsupported("unresolved or custom lookup TValue");
                TValue value=(TValue)data;if(step.getId()!=e.getKey()||value.getId()!=e.getKey())unsupported("lookup step/unit ID mismatch");
                byId.put(e.getKey(),value);
            }
        }
        Map<Long,List<Long>> routes=new HashMap<>();Object lock=field(factory,"indexLock");
        synchronized(lock){
            if((Boolean)field(factory,"indexInitialized")){
                Map<Long,LinkedHashSet<Long>> actual=(Map<Long,LinkedHashSet<Long>>)field(factory,"localByVariable");
                for(Map.Entry<Long,LinkedHashSet<Long>> e:actual.entrySet())routes.put(e.getKey(),new ArrayList<>(e.getValue()));
            }else{
                // Simulate, without executing, native lazy ensureIndex: reverse
                // newest-first chain and discard stale local acceleration data.
                for(int i=chain.size()-1;i>=0;i--){TValue v=chain.get(i);routes.computeIfAbsent(v.getTVarId(),k->new ArrayList<>()).add(v.getId());}
            }
        }
        return new Layer(factory,chain,byId,routes,(TValueFactory)field(factory,"parentIndex"));
    }
    public static List<TValue> values(TValueFactory f)throws Exception{return layer(f).chain;}
    private static void collect(TValueFactory f,long variable,LinkedHashSet<Long> ids,Set<TValueFactory> seen)throws Exception{
        if(!seen.add(f))unsupported("factory ancestry cycle");Layer l=layer(f);
        if(l.parent!=null)collect(l.parent,variable,ids,seen);
        List<Long> local=l.routes.get(variable);if(local!=null)ids.addAll(local);
    }
    public static List<TValue> bucket(Mind mind,long variable)throws Exception{
        Layer target=layer(mind.getTValues());LinkedHashSet<Long> ids=new LinkedHashSet<>();collect(target.factory,variable,ids,Collections.newSetFromMap(new IdentityHashMap<>()));
        List<TValue> result=new ArrayList<>();for(long id:ids){TValue v=target.byId.get(id);if(v!=null)result.add(v);}return result;
    }
    static String encode(Mind mind,List<TValue> values){List<String> rows=new ArrayList<>();for(TValue v:values)rows.add(v.getId()+":"+v.getValueId()+":"+(v.isDeleted(mind)?1:0));return "["+String.join(",",rows)+"]";}
    /** Independent full projection algorithm: flatten base-to-leaf routing for
     * every variable at once, then intersect with this context's resident chain.
     * Shares validated raw-state primitives, not incremental dirty state or
     * recursive single-bucket enumeration. Native-control gates check both. */
    public static SortedMap<Long,String> authority(Mind mind)throws Exception{
        Layer target=layer(mind.getTValues());List<Layer> layers=new ArrayList<>();Set<TValueFactory> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        for(Layer l=target;l!=null;l=l.parent==null?null:layer(l.parent)){if(!seen.add(l.factory))unsupported("factory ancestry cycle");layers.add(l);}
        Map<Long,LinkedHashSet<Long>> merged=new HashMap<>();
        for(int i=layers.size()-1;i>=0;i--)for(Map.Entry<Long,List<Long>> e:layers.get(i).routes.entrySet())merged.computeIfAbsent(e.getKey(),k->new LinkedHashSet<>()).addAll(e.getValue());
        SortedSet<Long> variables=new TreeSet<>();for(TValue value:target.chain)variables.add(value.getTVarId());
        SortedMap<Long,String> result=new TreeMap<>();
        for(long variable:variables){List<TValue> values=new ArrayList<>();for(long id:merged.getOrDefault(variable,new LinkedHashSet<>())){TValue v=target.byId.get(id);if(v!=null)values.add(v);}if(!values.isEmpty())result.put(variable,encode(mind,values));}
        return result;
    }
    /** Fixture fingerprint includes owner/resident references and lazy/native
     * index state. It reads only; identity hashes are compared within one JVM. */
    @SuppressWarnings("unchecked")
    public static String fingerprint(Mind mind)throws Exception{
        List<String> rows=new ArrayList<>();Set<TValueFactory> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        for(TValueFactory f=mind.getTValues();f!=null;f=(TValueFactory)field(f,"parentIndex")){
            if(!seen.add(f))unsupported("fingerprint cycle");Layer l=layer(f);Object cache=field(f,"cache");
            rows.add("factory="+System.identityHashCode(f)+" initialized="+field(f,"indexInitialized")+" action="+field(f,"action")+" routes="+new TreeMap<>((Map<Long,LinkedHashSet<Long>>)field(f,"localByVariable"))+" checkpoints="+((Stack<?>)field(f,"additionsStack")).size()+":"+((Stack<?>)field(f,"actionStack")).size());
            SortedMap<Long,Integer> memory=new TreeMap<>();for(Map.Entry<Long,IStep> e:((Map<Long,IStep>)field(cache,"memoryById")).entrySet())memory.put(e.getKey(),System.identityHashCode(e.getValue()));
            rows.add("cache="+System.identityHashCode(cache)+" root="+System.identityHashCode(field(cache,"root"))+" valid="+field(cache,"indexValid")+" indexed="+System.identityHashCode(field(cache,"indexedRoot"))+" mutation="+field(cache,"mutation")+" frames="+((Stack<?>)field(cache,"stack")).size()+" memory="+memory+" persistent="+new TreeSet<>((Set<Long>)field(cache,"persistentIds"))+" predecessor="+new TreeMap<>((Map<Long,Long>)field(cache,"predecessorById")));
            for(TValue v:l.chain)rows.add("value="+v.getId()+":"+v.getTVarId()+":"+v.getValueId()+":"+v.getMindId()+" owner="+System.identityHashCode(v.getMind())+" resident="+System.identityHashCode(field(v,"value"))+":"+System.identityHashCode(field(v,"tVar")));
        }
        return String.join("|",rows);
    }
}
