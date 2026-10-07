package org.kanger;
import java.util.*;
import java.util.zip.CRC32;
import org.kanger.storage.*;
import org.kanger.units.TValue;
/** Diagnostic, explicitly activated, caller-quiescent, single-thread decode provenance. */
public final class RecordedLinks {
    private static final ThreadLocal<State> ACTIVE=new ThreadLocal<>();
    private static final class State {final IdentityHashMap<Object,Witness> links=new IdentityHashMap<>();final List<String> errors=new ArrayList<>();}
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
