package org.kanger;
import java.lang.reflect.Field;
import java.util.*;
import org.kanger.factory.TValueFactory;
import org.kanger.interfaces.internal.IStep;
import org.kanger.storage.*;
import org.kanger.units.TValue;
/** Diagnostic read-only reconstruction of bounded resident native enumeration (memory and current DUMB).
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
    // Quiescent diagnostic only: storage and payload writes must be excluded
    // by the caller. Native cache invalidation is outside Base.locker.
    static void validateMind(Mind m)throws Exception{
        Set<Mind> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        for(Object o=m;o!=null;o=field(o,"next")){
            if(o.getClass()!=Mind.class||!seen.add((Mind)o))unsupported("Mind class or cycle");
        }
    }
    static TValue unit(IStep step,long id)throws Exception{
        if(step==null||(step.getClass()!=Step.class&&step.getClass()!=Sapato.class))unsupported("step class");
        Object data=field(step,"data");if(data==null||data.getClass()!=TValue.class)unsupported("unresolved or custom TValue");
        TValue v=(TValue)data;if(step.getId()!=id||v.getId()!=id)unsupported("step/unit ID mismatch");return v;
    }
    @SuppressWarnings("unchecked")
    static Base activeBase(Mind mind)throws Exception{
        validateMind(mind);Object user=field(mind,"user");if(user==null||user.getClass()!=User.class)unsupported("User class");
        Object data=field(user,"data");if(data==null||data.getClass()!=DB.class)unsupported("storage module");
        Map<String,Object> bases=(Map<String,Object>)field(data,"bases"),storage=(Map<String,Object>)field(user,"storage");
        Object base=storage.get(TValueFactory.SCHEMA);
        if(base==null||base.getClass()!=Base.class||bases.get(TValueFactory.SCHEMA)!=base||field(data,"user")!=user)unsupported("active storage identity");
        return (Base)base;
    }
    @SuppressWarnings("unchecked")
    static Layer layer(TValueFactory factory)throws Exception{
        if(factory==null||factory.getClass()!=TValueFactory.class)unsupported("factory class");
        Mind mind=(Mind)field(factory,"mind");validateMind(mind);
        Object connection=field(factory,"connection");
        Object cache=field(factory,"cache");if(cache==null||cache.getClass()!=Escalera.class||field(cache,"mind")!=mind||!TValueFactory.SCHEMA.equals(field(cache,"schema")))unsupported("cache class or binding");
        IStep root=(IStep)field(cache,"root");
        boolean valid=(Boolean)field(cache,"indexValid")&&field(cache,"indexedRoot")==root;
        Set<Long> actualPersistent=(Set<Long>)field(cache,"persistentIds");
        boolean needStorage=connection!=null||(valid&&!actualPersistent.isEmpty());
        Set<IStep> probe=Collections.newSetFromMap(new IdentityHashMap<>());
        for(IStep s=root;s!=null;){
            if(s.getClass()==Sapato.class){needStorage=true;break;}
            if(s.getClass()!=Step.class||!probe.add(s))unsupported("nonresident or custom step/cycle");
            s=(IStep)field(s,"next");
        }
        Base base=null;Map<Long,IStep> resident=new HashMap<>();
        if(needStorage){
            base=activeBase(mind);
            if(connection!=null&&connection!=base)unsupported("connection generation mismatch");
            try{ResidentPersistentRead.snapshot(base);}catch(AssertionError e){unsupported(e.getMessage());}
            Map<Long,IStep> actual=(Map<Long,IStep>)field(base,"cache");
            synchronized(actual){for(Map.Entry<Long,IStep> e:actual.entrySet())resident.put(e.getKey(),e.getValue());}
            for(Map.Entry<Long,IStep> e:resident.entrySet())unit(e.getValue(),e.getKey());
        }
        List<TValue> chain=new ArrayList<>();Map<Long,TValue> byId=new HashMap<>();
        Map<Long,IStep> virtualMemory=new HashMap<>();Set<Long> virtualPersistent=new HashSet<>();
        Set<IStep> seen=Collections.newSetFromMap(new IdentityHashMap<>());Set<Long> ids=new HashSet<>();
        for(IStep step=root;step!=null;){
            if(step.getClass()!=Step.class&&step.getClass()!=Sapato.class)unsupported("nonresident or custom step");
            if(!seen.add(step)||!ids.add(step.getId()))unsupported("cycle or duplicate chain ID");
            TValue value=unit(step,step.getId());chain.add(value);
            if(step.getClass()==Sapato.class){
                IStep current=resident.get(step.getId());
                if(current==null||field(step,"base")!=base||field(current,"base")!=base)unsupported("unresolved or foreign Sapato");
                if(field(step,"data")!=field(current,"data")||!field(step,"next").equals(field(current,"next"))||!field(step,"hash").equals(field(current,"hash")))unsupported("stale Sapato anchor");
                virtualPersistent.add(step.getId());long next=(Long)field(step,"next");step=next==-1?null:resident.get(next);
                if(next!=-1&&step==null)unsupported("missing resident next");
            }else{virtualMemory.put(step.getId(),step);step=(IStep)field(step,"next");}
        }
        Map<Long,IStep> memory=valid?(Map<Long,IStep>)field(cache,"memoryById"):virtualMemory;
        Set<Long> persistent=valid?actualPersistent:virtualPersistent;
        for(Map.Entry<Long,IStep> e:memory.entrySet())byId.put(e.getKey(),unit(e.getValue(),e.getKey()));
        // Native Escalera persistent lookup, then root factory fallback. Keep
        // actual valid acceleration metadata; never rebuild or repair it.
        for(long id:persistent)if(!byId.containsKey(id)&&resident.containsKey(id))byId.put(id,unit(resident.get(id),id));
        if(connection!=null)for(Map.Entry<Long,IStep> e:resident.entrySet())if(!byId.containsKey(e.getKey()))byId.put(e.getKey(),unit(e.getValue(),e.getKey()));
        Map<Long,List<Long>> routes=new HashMap<>();Object lock=field(factory,"indexLock");
        synchronized(lock){
            if((Boolean)field(factory,"indexInitialized")){
                Map<Long,LinkedHashSet<Long>> actual=(Map<Long,LinkedHashSet<Long>>)field(factory,"localByVariable");
                for(Map.Entry<Long,LinkedHashSet<Long>> e:actual.entrySet())routes.put(e.getKey(),new ArrayList<>(e.getValue()));
            }else for(int i=chain.size()-1;i>=0;i--){TValue v=chain.get(i);routes.computeIfAbsent(v.getTVarId(),k->new ArrayList<>()).add(v.getId());}
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
        validateMind(mind);
        Layer target=layer(mind.getTValues());LinkedHashSet<Long> ids=new LinkedHashSet<>();collect(target.factory,variable,ids,Collections.newSetFromMap(new IdentityHashMap<>()));
        List<TValue> result=new ArrayList<>();for(long id:ids){TValue v=target.byId.get(id);if(v!=null)result.add(v);}return result;
    }
    static String encode(Mind mind,List<TValue> values){List<String> rows=new ArrayList<>();for(TValue v:values)rows.add(v.getId()+":"+v.getValueId()+":"+(v.isDeleted(mind)?1:0));return "["+String.join(",",rows)+"]";}
    /** Independent full projection algorithm: flatten base-to-leaf routing for
     * every variable at once, then intersect with this context's resident chain.
     * Shares validated raw-state primitives, not incremental dirty state or
     * recursive single-bucket enumeration. Native-control gates check both. */
    public static SortedMap<Long,String> authority(Mind mind)throws Exception{
        validateMind(mind);
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
        validateMind(mind);
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
