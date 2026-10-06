package org.kanger;

import java.util.*;
import org.kanger.factory.TValueFactory;
import org.kanger.units.TValue;
import org.kanger.units.TVariable;

/** Diagnostic boundary-diff journal. Never feeds the inference scheduler. */
public final class TValueStateJournal {
    private static final ThreadLocal<Session> CURRENT=new ThreadLocal<>();
    private static final class Session {
        final IdentityHashMap<Mind,State> states=new IdentityHashMap<>();
        final List<String> rows=new ArrayList<>(),errors=new ArrayList<>();
        int contexts,observations,deferred,changes,resets;
    }
    private static final class State {
        final Mind mind;final int context;
        int generation,checkpoints,settlements,invocation;boolean initialized,reset,retired;
        SortedMap<Long,String> view=new TreeMap<>();
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
    private static SortedMap<Long,String> capture(Mind mind)throws Exception{
        TValueFactory factory=mind.getTValues();
        if(factory==null||factory.getClass()!=TValueFactory.class)throw new AssertionError("native assigned TValueFactory required");
        SortedSet<Long> variables=new TreeSet<>();
        for(TValue v:factory)variables.add(v.getTVarId());
        SortedMap<Long,String> view=new TreeMap<>();
        for(long id:variables){
            // forEach consumes only variable ID; this unregistered shell avoids
            // hydrating/changing the ownership of a canonical TVariable.
            TVariable key=new TVariable(mind);key.setId(id);
            List<String> values=new ArrayList<>();Set<Long> seen=new HashSet<>();
            factory.forEach(key,o->{TValue v=(TValue)o;if(!seen.add(v.getId()))throw new AssertionError("duplicate canonical enumeration ID");values.add(v.getId()+":"+v.getValueId()+":"+(v.isDeleted(mind)?1:0));return true;});
            if(!values.isEmpty())view.put(id,"["+String.join(",",values)+"]");
        }
        return view;
    }
    private static String scope(State st){return "ctx="+st.context+" mind="+st.mind.getId()+" generation="+st.generation+" invocation="+st.invocation;}
    private static void observe(Session s,State st,String reason)throws Exception{
        if(st.checkpoints!=0||st.settlements!=0){s.deferred++;return;}
        SortedMap<Long,String> next=capture(st.mind);s.observations++;
        if(!st.initialized||st.reset){
            String kind=st.initialized?"RESET":"BASELINE";
            s.rows.add(kind+" "+scope(st)+" reason="+reason+" view="+encode(next));
            if(st.initialized)s.resets++;st.initialized=true;st.reset=false;
        }else{
            SortedSet<Long> ids=new TreeSet<>(st.view.keySet());ids.addAll(next.keySet());
            for(long id:ids){String before=st.view.getOrDefault(id,"[]"),after=next.getOrDefault(id,"[]");if(!before.equals(after)){s.rows.add("CHANGE "+scope(st)+" reason="+reason+" variable="+id+" before="+before+" after="+after);s.changes++;}}
        }
        st.view=next;
        // Every observation includes the complete authority snapshot, allowing
        // independent replay of the preceding delta instead of trusting counts.
        s.rows.add("VIEW "+scope(st)+" reason="+reason+" view="+encode(next));
    }
    private static String encode(SortedMap<Long,String> view){List<String> rows=new ArrayList<>();for(Map.Entry<Long,String> e:view.entrySet())rows.add(e.getKey()+"="+e.getValue());return "{"+String.join(";",rows)+"}";}
    public static void observe(Mind m,String reason){safely(m,(s,st)->observe(s,st,reason));}
    public static void constructed(Mind m){observe(m,"constructed");}
    public static void reset(Mind m){safely(m,(s,st)->{st.generation++;st.reset=true;st.checkpoints=0;});}
    public static void mark(Mind m){safely(m,(s,st)->{if(!st.initialized)observe(s,st,"before-mark");st.checkpoints++;});}
    public static void complete(Mind m){safely(m,(s,st)->{if(st.checkpoints<=0)throw new AssertionError("unmatched journal checkpoint");st.checkpoints--;});}
    public static void linkStart(Mind m){safely(m,(s,st)->{observe(s,st,"before-link");st.invocation++;});}
    public static void beginSettlement(Mind m){safely(m,(s,st)->{observe(s,st,"before-settlement");st.settlements++;});}
    public static void endSettlement(Mind m){safely(m,(s,st)->{if(st.settlements<=0)throw new AssertionError("unmatched settlement");st.settlements--;observe(s,st,"after-settlement");});}
    public static void retire(Mind m){safely(m,(s,st)->{if(st.checkpoints!=0||st.settlements!=0)throw new AssertionError("retired unfinished context");st.retired=true;s.rows.add("RETIRE "+scope(st));});}
    public static int changeCount(){return CURRENT.get().changes;}
    public static int contextChangeCount(Mind mind){State st=CURRENT.get().states.get(mind);if(st==null)return 0;String prefix="CHANGE ctx="+st.context+" ";int count=0;for(String row:CURRENT.get().rows)if(row.startsWith(prefix))count++;return count;}
    public static List<String> finish(){
        Session s=CURRENT.get();if(s==null)throw new AssertionError("no journal");
        List<State> states=new ArrayList<>(s.states.values());states.sort(Comparator.comparingInt(st->st.context));
        for(State st:states)if(!st.retired)observe(st.mind,"session-end");
        CURRENT.remove();
        for(State st:states)if(st.checkpoints!=0||st.settlements!=0)s.errors.add("unsettled context "+st.context);
        if(!s.errors.isEmpty())throw new AssertionError("journal errors "+s.errors);
        s.rows.add("JOURNAL_OK contexts="+s.contexts+" observations="+s.observations+" deferred="+s.deferred+" changes="+s.changes+" resets="+s.resets);
        return s.rows;
    }
}
