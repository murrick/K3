package org.kanger;

import java.util.*;
import org.kanger.enums.UnitType;
import org.kanger.factory.TValueFactory;
import org.kanger.units.TValue;
import org.kanger.storage.*;
import org.kanger.interfaces.internal.IStep;


/** Shadow dirty-bucket projection. Full snapshots check, never repair it. */
public final class TValueDirtyJournal {
    private static final ThreadLocal<Session> CURRENT=new ThreadLocal<>();
    private static final boolean COST=Boolean.getBoolean("journal.cost.enabled");
    public static List<String> profile(){return Collections.unmodifiableList(CURRENT.get().cost);}
    private static final class Session {
        final IdentityHashMap<Mind,State> states=new IdentityHashMap<>();
        final List<String> rows=new ArrayList<>(),errors=new ArrayList<>();
        int contexts,observations,deferred,changes,resets,touches,bucketReads,seeds,mapChanges;
        UpdateFrame update;
        final List<String> cost=new ArrayList<>();
    }
    private static final class UpdateFrame {
        final Session session;final Base base;final LinkedHashMap<Long,IStep> pending=new LinkedHashMap<>();
        UpdateFrame(Session s,Base b){session=s;base=b;}
    }
    /** Bracket only a native root factory update. No snapshots or index repair. */
    public static Object beginUpdate(Mind owner,Object source){
        Session s=CURRENT.get();if(s==null)return null;
        try{
            if(owner==null||owner.getClass()!=Mind.class||!owner.isStorageUsed()||owner.getNext()!=null)return null;
            if(source==null||source.getClass()!=TValueFactory.class||source!=owner.getTValues())throw new AssertionError("update factory binding");
            if(s.update!=null)throw new AssertionError("nested native update unsupported");
            UpdateFrame frame=new UpdateFrame(s,ResidentTValueRead.activeBase(owner));s.update=frame;return frame;
        }catch(Throwable e){s.errors.add(e.getClass().getName()+":"+e.getMessage());return null;}
    }
    public static void endUpdate(Object token,boolean success){
        if(token==null)return;
        UpdateFrame frame=(UpdateFrame)token;Session s=CURRENT.get();
        if(s!=frame.session)return;
        try{
            if(s.update!=frame)throw new AssertionError("update bracket mismatch");
            s.update=null;
            if(!success){s.errors.add("unsupported incomplete native factory update");return;}
            for(IStep step:frame.pending.values())materialized(frame.base,step);
        }catch(Throwable e){s.errors.add(e.getClass().getName()+":"+e.getMessage());}
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
    private static String bucket(Mind mind,long id,Map<Long,Long> metadata,ObservationLayers frame)throws Exception{
        List<String> values=new ArrayList<>();Set<Long> seen=new HashSet<>();
        for(TValue v:frame.bucket(id)){if(!seen.add(v.getId()))throw new AssertionError("duplicate canonical enumeration ID");values.add(v.getId()+":"+v.getValueId()+":"+(v.isDeleted(mind)?1:0));if(metadata!=null){metadata.put(v.getId(),v.getTVarId());CURRENT.get().states.get(mind).observed.put(v,id);}}
        return "["+String.join(",",values)+"]";
    }
    private static SortedMap<Long,String> capture(Mind mind,Map<Long,Long> metadata,ObservationLayers frame)throws Exception{
        if(metadata==null)return ResidentTValueRead.authority(mind);
        SortedSet<Long> variables=new TreeSet<>();for(TValue v:frame.values())variables.add(v.getTVarId());
        SortedMap<Long,String> view=new TreeMap<>();for(long id:variables){String b=bucket(mind,id,metadata,frame);if(!b.equals("[]"))view.put(id,b);}return view;
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
        long costStart=COST?System.nanoTime():0;boolean costSeed=!st.initialized||st.reset;int costDirty=st.dirty.size();
        SortedMap<Long,Boolean> marks;
        SortedMap<Long,String> next;
        try(ObservationLayers frame=new ObservationLayers(st.mind)){
        marks=visibility(st.mind);
        if(!st.initialized||st.reset){
            st.variableByValue.clear();st.observed.clear();next=capture(st.mind,st.variableByValue,frame);s.seeds++;
        }else{
            SortedSet<Long> ids=new TreeSet<>(marks.keySet());ids.addAll(st.visibility.keySet());
            for(long id:ids)if(!Objects.equals(marks.get(id),st.visibility.get(id))){
                Long variable=st.variableByValue.get(id);
                if(variable!=null){st.dirty.add(variable);s.mapChanges++;s.rows.add("MAP_DIRTY "+scope(st)+" value="+id+" variable="+variable);}
            }
            next=new TreeMap<>(st.view);
            for(long id:st.dirty){String b=bucket(st.mind,id,st.variableByValue,frame);s.bucketReads++;if(b.equals("[]"))next.remove(id);else next.put(id,b);}
        }
        }
        // A separate full native scan is solely an oracle: never copy its
        // result into the incremental projection or silently add missing keys.
        long costProjected=COST?System.nanoTime():0;
        SortedMap<Long,String> authority=capture(st.mind,null,null);
        long costCaptured=COST?System.nanoTime():0;
        if(!next.equals(authority))throw new AssertionError("dirty projection mismatch ctx="+st.context+" reason="+reason+" shadow="+encode(next)+" authority="+encode(authority));
        long costChecked=COST?System.nanoTime():0;
        s.observations++;
        if(!st.initialized||st.reset){
            s.rows.add((st.initialized?"RESET":"BASELINE")+" "+scope(st)+" reason="+reason+" view="+encode(next));
            if(st.initialized)s.resets++;st.initialized=true;st.reset=false;
        }else{
            SortedSet<Long> ids=new TreeSet<>(st.view.keySet());ids.addAll(next.keySet());
            for(long id:ids){String before=st.view.getOrDefault(id,"[]"),after=next.getOrDefault(id,"[]");if(!before.equals(after)){s.rows.add("CHANGE "+scope(st)+" reason="+reason+" variable="+id+" before="+before+" after="+after);s.changes++;}}
        }
        st.view=next;st.visibility=marks;st.dirty.clear();s.rows.add("VIEW "+scope(st)+" reason="+reason+" view="+encode(authority));
        if(COST){long end=System.nanoTime();s.cost.add(reason+","+costSeed+","+costDirty+","+(costProjected-costStart)+","+(costCaptured-costProjected)+","+(costChecked-costCaptured)+","+(end-costChecked)+","+(end-costStart));}
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
    /** Post-successful native Base.get only. No snapshot, hydration, LRU
     * access, owner write, factory repair or authority registration. */
    @SuppressWarnings("unchecked")
    public static void materialized(Object candidate,IStep step){
        Session s=CURRENT.get();if(s==null||candidate==null||candidate.getClass()!=Base.class||step==null||step.getClass()!=Sapato.class)return;
        if(s.update!=null&&s.update.base==candidate){s.update.pending.put(step.getId(),step);return;}
        try{
            Object data=ResidentTValueRead.field(step,"data");if(data==null||data.getClass()!=TValue.class)return;
            TValue value=(TValue)data;long id=value.getId(),variable=value.getTVarId();
            List<State> states=new ArrayList<>(s.states.values());states.sort(Comparator.comparingInt(st->st.context));
            for(State st:states){
                if(st.retired||!st.initialized||st.reset||!st.variableByValue.containsKey(id)||st.observed.containsKey(value))continue;
                Object user=ResidentTValueRead.field(st.mind,"user");if(user==null||user.getClass()!=User.class)continue;
                Map<String,Object> storage=(Map<String,Object>)ResidentTValueRead.field(user,"storage");if(storage.get(TValueFactory.SCHEMA)!=candidate)continue;
                if(ResidentTValueRead.activeBase(st.mind)!=candidate)throw new AssertionError("materialization generation mismatch");
                if(step.getId()!=id||ResidentTValueRead.field(step,"base")!=candidate)throw new AssertionError("materialization step/unit binding mismatch");
                Map<Long,IStep> resident=(Map<Long,IStep>)ResidentTValueRead.field(candidate,"cache");boolean same=false;
                synchronized(resident){for(Map.Entry<Long,IStep> e:resident.entrySet())if(e.getKey()==id){same=e.getValue()==step;break;}}
                if(!same)throw new AssertionError("materialization not resident after native return");
                TValueFactory f=factory(st.mind);Object cache=ResidentTValueRead.field(f,"cache");
                if(cache==null||cache.getClass()!=Escalera.class)throw new AssertionError("materialization cache class");
                if(!(Boolean)ResidentTValueRead.field(cache,"indexValid")||ResidentTValueRead.field(cache,"indexedRoot")!=ResidentTValueRead.field(cache,"root"))throw new AssertionError("materialization requires valid native lookup metadata");
                Map<Long,IStep> memory=(Map<Long,IStep>)ResidentTValueRead.field(cache,"memoryById");IStep local=memory.get(id);
                if(local!=null){if((local.getClass()!=Step.class&&local.getClass()!=Sapato.class)||ResidentTValueRead.field(local,"data")!=value)continue;}
                else if(!((Set<Long>)ResidentTValueRead.field(cache,"persistentIds")).contains(id)&&ResidentTValueRead.field(f,"connection")!=candidate)continue;
                long route=st.variableByValue.get(id);if(route!=variable)throw new AssertionError("unsupported materialized variable change old="+route+" new="+variable);
                if(st.observed.containsKey(value))continue;
                // Stop treating an evicted instance as canonical in this
                // context; aliases in other contexts are checked separately.
                Iterator<TValue> old=st.observed.keySet().iterator();while(old.hasNext())if(old.next().getId()==id)old.remove();
                st.observed.put(value,route);st.dirty.add(route);s.touches++;
                s.rows.add("TOUCH "+scope(st)+" reason=materialize variable="+route+" value="+id+" term="+value.getValueId());
            }
        }catch(Throwable e){s.errors.add(e.getClass().getName()+":"+e.getMessage());}
    }
    public static void promoted(Mind parent,TValueFactory child){safely(parent,(s,st)->{for(TValue value:ResidentTValueRead.values(child))touch(parent,value,"promote");});}
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
