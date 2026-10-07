package org.kanger;

import java.util.*;
import org.kanger.enums.UnitType;
import org.kanger.factory.TValueFactory;
import org.kanger.units.TValue;
import org.kanger.units.TVariable;

/** Shadow dirty-bucket projection. Full snapshots check, never repair it. */
public final class TValueDirtyJournal {
    private static final ThreadLocal<Session> CURRENT=new ThreadLocal<>();
    private static final class Session {
        final IdentityHashMap<Mind,State> states=new IdentityHashMap<>();
        final List<String> rows=new ArrayList<>(),errors=new ArrayList<>();
        int contexts,observations,deferred,changes,resets,touches,bucketReads,seeds,mapChanges;
    }
    private static final class State {
        final Mind mind;final int context;
        int generation,checkpoints,settlements,invocation;boolean initialized,reset,retired;
        SortedMap<Long,String> view=new TreeMap<>();
        final SortedSet<Long> dirty=new TreeSet<>();
        final Map<Long,Long> variableByValue=new HashMap<>();
        final IdentityHashMap<TValue,Long> observed=new IdentityHashMap<>();
        SortedMap<Long,Boolean> visibility=new TreeMap<>();
        State(Mind m,int c){mind=m;context=c;}
    }
    public static void begin(){if(CURRENT.get()!=null)throw new AssertionError("nested journal session");CURRENT.set(new Session());}
    private interface Work {void run(Session s,State state)throws Exception;}
    private static void safely(Mind mind,Work work){
        Session s=CURRENT.get();if(s==null||mind==null||mind.getClass()!=Mind.class)return;
        try{
            State st=s.states.get(mind);if(st==null){st=new State(mind,++s.contexts);s.states.put(mind,st);}
            if(!st.retired)work.run(s,st);
        }catch(Throwable e){s.errors.add(e.getClass().getName()+":"+e.getMessage());}
    }
    private static TValueFactory factory(Mind mind){TValueFactory f=mind.getTValues();if(f==null||f.getClass()!=TValueFactory.class)throw new AssertionError("native assigned TValueFactory required");return f;}
    private static String bucket(Mind mind,long id,Map<Long,Long> metadata)throws Exception{
        TVariable key=new TVariable(mind);key.setId(id);
        List<String> values=new ArrayList<>();Set<Long> seen=new HashSet<>();
        factory(mind).forEach(key,o->{TValue v=(TValue)o;if(!seen.add(v.getId()))throw new AssertionError("duplicate canonical enumeration ID");values.add(v.getId()+":"+v.getValueId()+":"+(v.isDeleted(mind)?1:0));if(metadata!=null){metadata.put(v.getId(),v.getTVarId());CURRENT.get().states.get(mind).observed.put(v,id);}return true;});
        return "["+String.join(",",values)+"]";
    }
    private static SortedMap<Long,String> capture(Mind mind,Map<Long,Long> metadata)throws Exception{
        SortedSet<Long> variables=new TreeSet<>();for(TValue v:factory(mind))variables.add(v.getTVarId());
        SortedMap<Long,String> view=new TreeMap<>();for(long id:variables){String b=bucket(mind,id,metadata);if(!b.equals("[]"))view.put(id,b);}return view;
    }
    private static SortedMap<Long,Boolean> visibility(Mind mind){
        SortedMap<Long,Boolean> result=new TreeMap<>();
        for(Mind m=mind;m!=null;m=(Mind)m.getNext()){
            Set<Long> restored=m.getRestored().get(UnitType.TVALUE),deleted=m.getDeleted().get(UnitType.TVALUE);
            if(restored!=null)for(long id:restored)if(!result.containsKey(id))result.put(id,false);
            if(deleted!=null)for(long id:deleted)if(!result.containsKey(id))result.put(id,true);
        }
        return result;
    }
    private static String scope(State st){return "ctx="+st.context+" mind="+st.mind.getId()+" generation="+st.generation+" invocation="+st.invocation;}
    private static String encode(SortedMap<Long,String> view){List<String> rows=new ArrayList<>();for(Map.Entry<Long,String> e:view.entrySet())rows.add(e.getKey()+"="+e.getValue());return "{"+String.join(";",rows)+"}";}
    private static void observe(Session s,State st,String reason)throws Exception{
        if(st.checkpoints!=0||st.settlements!=0){s.deferred++;return;}
        SortedMap<Long,Boolean> marks=visibility(st.mind);
        SortedMap<Long,String> next;
        if(!st.initialized||st.reset){
            st.variableByValue.clear();st.observed.clear();next=capture(st.mind,st.variableByValue);s.seeds++;
        }else{
            SortedSet<Long> ids=new TreeSet<>(marks.keySet());ids.addAll(st.visibility.keySet());
            for(long id:ids)if(!Objects.equals(marks.get(id),st.visibility.get(id))){
                Long variable=st.variableByValue.get(id);
                if(variable!=null){st.dirty.add(variable);s.mapChanges++;s.rows.add("MAP_DIRTY "+scope(st)+" value="+id+" variable="+variable);}
            }
            next=new TreeMap<>(st.view);
            for(long id:st.dirty){String b=bucket(st.mind,id,st.variableByValue);s.bucketReads++;if(b.equals("[]"))next.remove(id);else next.put(id,b);}
        }
        // A separate full native scan is solely an oracle: never copy its
        // result into the incremental projection or silently add missing keys.
        SortedMap<Long,String> authority=capture(st.mind,null);
        if(!next.equals(authority))throw new AssertionError("dirty projection mismatch ctx="+st.context+" reason="+reason+" shadow="+encode(next)+" authority="+encode(authority));
        s.observations++;
        if(!st.initialized||st.reset){
            s.rows.add((st.initialized?"RESET":"BASELINE")+" "+scope(st)+" reason="+reason+" view="+encode(next));
            if(st.initialized)s.resets++;st.initialized=true;st.reset=false;
        }else{
            SortedSet<Long> ids=new TreeSet<>(st.view.keySet());ids.addAll(next.keySet());
            for(long id:ids){String before=st.view.getOrDefault(id,"[]"),after=next.getOrDefault(id,"[]");if(!before.equals(after)){s.rows.add("CHANGE "+scope(st)+" reason="+reason+" variable="+id+" before="+before+" after="+after);s.changes++;}}
        }
        st.view=next;st.visibility=marks;st.dirty.clear();s.rows.add("VIEW "+scope(st)+" reason="+reason+" view="+encode(authority));
    }
    private static boolean dependsOn(Mind candidate,Mind owner){for(Mind m=candidate;m!=null;m=(Mind)m.getNext())if(m==owner)return true;return false;}
    public static void touch(Mind owner,TValue value,String reason){
        safely(owner,(s,source)->{
            List<State> states=new ArrayList<>(s.states.values());states.sort(Comparator.comparingInt(st->st.context));
            for(State st:states)if(!st.retired&&dependsOn(st.mind,owner)){
                st.observed.put(value,value.getTVarId());st.dirty.add(value.getTVarId());st.variableByValue.put(value.getId(),value.getTVarId());s.touches++;
                s.rows.add("TOUCH "+scope(st)+" reason="+reason+" variable="+value.getTVarId()+" value="+value.getId()+" term="+value.getValueId());
            }
        });
    }
    /** Runs after an original setter body, including its partial-failure path.
     * Identity registration prevents constructor/lookup probes from selecting
     * canonical buckets. Never consults or repairs a factory index. */
    public static void metadata(TValue value,long oldId,long oldVariable,long oldTerm,String reason){
        Session s=CURRENT.get();if(s==null)return;
        try{
            List<State> states=new ArrayList<>(s.states.values());states.sort(Comparator.comparingInt(st->st.context));
            for(State st:states)if(!st.retired&&st.observed.containsKey(value)){
                long route=st.observed.get(value);
                for(long variable:new TreeSet<>(Arrays.asList(route,oldVariable,value.getTVarId()))){
                    st.dirty.add(variable);s.touches++;
                    s.rows.add("TOUCH "+scope(st)+" reason="+reason+" variable="+variable+" value="+value.getId()+" term="+value.getValueId());
                }
                st.variableByValue.put(value.getId(),value.getTVarId());
                if(oldId!=value.getId()||oldVariable!=value.getTVarId())
                    s.errors.add("unsupported registered identity mutation reason="+reason+" old="+oldId+":"+oldVariable+" new="+value.getId()+":"+value.getTVarId());
            }
        }catch(Throwable e){s.errors.add(e.getClass().getName()+":"+e.getMessage());}
    }
    public static void promoted(Mind parent,TValueFactory child){safely(parent,(s,st)->{for(TValue value:child)touch(parent,value,"promote");});}
    public static void observe(Mind m,String reason){safely(m,(s,st)->observe(s,st,reason));}
    public static void constructed(Mind m){observe(m,"constructed");}
    public static void reset(Mind m){safely(m,(s,st)->{st.generation++;st.reset=true;st.checkpoints=0;st.dirty.clear();});}
    public static void mark(Mind m){safely(m,(s,st)->{if(!st.initialized)observe(s,st,"before-mark");st.checkpoints++;});}
    public static void complete(Mind m){safely(m,(s,st)->{if(st.checkpoints<=0)throw new AssertionError("unmatched journal checkpoint");st.checkpoints--;});}
    public static void linkStart(Mind m){safely(m,(s,st)->{observe(s,st,"before-link");st.invocation++;});}
    public static void beginSettlement(Mind m){safely(m,(s,st)->{observe(s,st,"before-settlement");st.settlements++;});}
    public static void endSettlement(Mind m){safely(m,(s,st)->{if(st.settlements<=0)throw new AssertionError("unmatched settlement");st.settlements--;observe(s,st,"after-settlement");});}
    public static void retire(Mind m){safely(m,(s,st)->{if(st.checkpoints!=0||st.settlements!=0)throw new AssertionError("retired unfinished context");st.retired=true;s.rows.add("RETIRE "+scope(st));});}
    public static int contextChangeCount(Mind mind){State st=CURRENT.get().states.get(mind);if(st==null)return 0;String prefix="CHANGE ctx="+st.context+" ";int count=0;for(String row:CURRENT.get().rows)if(row.startsWith(prefix))count++;return count;}
    public static List<String> finish(){
        Session s=CURRENT.get();if(s==null)throw new AssertionError("no journal");
        List<State> states=new ArrayList<>(s.states.values());states.sort(Comparator.comparingInt(st->st.context));for(State st:states)if(!st.retired)observe(st.mind,"session-end");CURRENT.remove();
        for(State st:states)if(st.checkpoints!=0||st.settlements!=0)s.errors.add("unsettled context "+st.context);
        if(!s.errors.isEmpty())throw new AssertionError("journal errors "+s.errors);
        s.rows.add("DIRTY_JOURNAL_OK contexts="+s.contexts+" observations="+s.observations+" deferred="+s.deferred+" changes="+s.changes+" resets="+s.resets+" touches="+s.touches+" bucketReads="+s.bucketReads+" seeds="+s.seeds+" mapChanges="+s.mapChanges);return s.rows;
    }
}
