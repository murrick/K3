package org.kanger;
import java.util.*;
import java.util.zip.CRC32;
import org.kanger.storage.*;
import org.kanger.units.TValue;
/** Diagnostic, explicitly activated, caller-quiescent, single-thread decode provenance. */
public final class RecordedLinks {
    private static final ThreadLocal<State> ACTIVE=new ThreadLocal<>();
    private static final class State {final IdentityHashMap<Object,Witness> links=new IdentityHashMap<>();final List<String> errors=new ArrayList<>();final IdentityHashMap<Object,Gate> gates=new IdentityHashMap<>();}
    private static final class Gate {int active;boolean failed;String activeOperation="upsert",failedOperation="upsert";}
    private static final class WriteFrame {final State state;final Gate gate;final String operation,previous;WriteFrame(State s,Gate g,String op,String prior){state=s;gate=g;operation=op;previous=prior;}}
    /** Diagnostic wrapper; no native getters, I/O, or exceptions added. */
    public static Object beginUpsert(Object base){return beginMutation(base,"upsert");}
    public static Object beginMutation(Object base,String operation){
        State s=ACTIVE.get();if(s==null)return null;
        try{if(base==null||base.getClass()!=Base.class)return null;
            Gate g=s.gates.get(base);if(g==null){g=new Gate();s.gates.put(base,g);}if(g.active!=0&&!g.failed){g.failed=true;g.failedOperation=operation;}String prior=g.activeOperation;g.activeOperation=operation;g.active++;return new WriteFrame(s,g,operation,prior);
        }catch(Throwable e){s.errors.add("upsert guard begin: "+e);return null;}
    }
    /** Only diagnostic rewrites of the native clear/close flush call sites use this entry. */
    public static Object beginNativeFlush(Object base,String parent){
        State s=ACTIVE.get();if(s==null)return null;
        Gate g=s.gates.get(base);
        if(g==null||g.active!=1||!g.activeOperation.equals(parent)||!(parent.equals("clear")||parent.equals("close")))return beginMutation(base,"flush");
        g.active++;return new WriteFrame(s,g,parent,parent);
    }
    public static void endUpsert(Object token,boolean success){endMutation(token,success);}
    public static void endMutation(Object token,boolean success){
        if(token==null)return;State s=ACTIVE.get();if(s==null)return;
        try{WriteFrame frame=(WriteFrame)token;if(frame.state!=s){s.errors.add("upsert guard activation changed");return;}
            if(frame.gate.active<=0){frame.gate.failed=true;s.errors.add("unmatched upsert guard");return;}
            frame.gate.active--;frame.gate.activeOperation=frame.previous;if(!success&&!frame.gate.failed){frame.gate.failed=true;frame.gate.failedOperation=frame.operation;}
        }catch(Throwable e){s.errors.add("upsert guard end: "+e);}
    }
    static void requireSettled(Base base){
        State s=ACTIVE.get();if(s==null)return;
        Gate g=s.gates.get(base);
        if(g!=null&&g.active!=0)ResidentPersistentRead.reject("active native "+g.activeOperation);
        if(g!=null&&g.failed)ResidentPersistentRead.reject("failed native "+g.failedOperation);
        if(!s.errors.isEmpty())ResidentPersistentRead.reject("saved-link recording error: "+s.errors);
    }
    private static final class Witness {
        final Object base,entry,unit;final long id,next;final int hash;
        Witness(Object b,Object e,Object s)throws Exception{base=b;entry=e;id=(Long)raw(s,"id");next=(Long)raw(s,"next");hash=(Integer)raw(s,"hash");unit=raw(s,"data");}
    }
    static Object raw(Object o,String n)throws Exception{return ResidentPersistentRead.raw(o,n);}
    public static void enable(){if(ACTIVE.get()!=null)throw new AssertionError("nested saved-link activation");ACTIVE.set(new State());}
    public static void disable(){ACTIVE.remove();}
    /** Called only from diagnostic Data.readOne, after native successful decode. Never alters native outcome. */
    public static void decoded(Object source,Object step,byte[] storedBytes){
        State state=ACTIVE.get();if(state==null)return;
        try{
            if(source==null||source.getClass()!=Data.class||step==null||step.getClass()!=Sapato.class)return;
            Object base=raw(step,"base");if(base==null||base.getClass()!=Base.class||raw(base,"data")!=source)return;
            Object unit=raw(step,"data");if(unit==null||unit.getClass()!=TValue.class)return;
            long id=(Long)raw(step,"id");if((Long)raw(unit,"id")!=id)throw new AssertionError("decoded ID mismatch");
            Object integrity=raw(base,"integrity");if(integrity==null||integrity.getClass()!=Class.forName("org.kanger.storage.IntegrityManifest"))return;
            Object entry=((Map<?,?>)raw(integrity,"entries")).get(id);if(entry==null)return;
            CRC32 crc=new CRC32();crc.update(storedBytes);
            if((Integer)raw(entry,"length")!=storedBytes.length||(Integer)raw(entry,"crc32")!=(int)crc.getValue())throw new AssertionError("decoded integrity mismatch");
            state.links.put(step,new Witness(base,entry,step));
        }catch(Throwable e){state.errors.add(e.getClass().getName()+": "+e.getMessage());}
    }
    static long verifiedNext(Object step,Base base,long id)throws Exception{
        State state=ACTIVE.get();if(state==null)ResidentPersistentRead.reject("saved-link recording inactive");
        if(!state.errors.isEmpty())ResidentPersistentRead.reject("saved-link recording error: "+state.errors);
        Witness w=state.links.get(step);if(w==null)ResidentPersistentRead.reject("no saved-link witness");
        Object entry=((Map<?,?>)raw(raw(base,"integrity"),"entries")).get(id);
        if(w.base!=base||w.id!=id||w.entry!=entry)ResidentPersistentRead.reject("stale saved-link witness");
        if(w.unit!=raw(step,"data")||w.hash!=(Integer)raw(step,"hash"))ResidentPersistentRead.reject("resident node differs from saved witness");
        if(w.next!=(Long)raw(step,"next"))ResidentPersistentRead.reject("resident link differs from saved witness");return w.next;
    }
}
