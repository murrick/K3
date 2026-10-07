package org.kanger;
import java.util.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import org.kanger.units.*;
import org.kanger.storage.ByteBuffer;
import org.kanger.exception.OutOfBufferException;
/** Executes native persistence bodies, including partial failures. */
public final class TValuePersistenceRunner {
    private static int checks;
    static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;}
    static boolean shadow(){return Boolean.getBoolean("persistence.shadow");}
    static void observe(Mind m,String reason){if(shadow())TValueDirtyJournal.observe(m,reason);}
    static Map<String,Object> map(TValue t,long owner,long term,long variable,boolean deleted){
        Map<String,Object> r=new HashMap<>();r.put("id",t.getId());r.put("mind_id",owner);r.put("deleted",deleted);r.put("value_id",term);r.put("tvar_id",variable);return r;
    }
    static ByteBuffer packet(TValue t,long owner,long term,long variable,boolean deleted){return new ByteBuffer().putLong(t.getId()).putLong(owner).putByte(deleted?1:0).putLong(term).putLong(variable);}
    public static void main(String[] args)throws Exception{
        checks=0;if(shadow())TValueDirtyJournal.begin();
        Mind m=new Mind(new User());m.compileLine("!keep(a);",false,null);m.compileLine("!keep(b);",false,null);
        TVariable v=TValueMetadataNativeRunner.variable(m,"persistent_owner");Term a=(Term)m.getTerms().add("a"),b=(Term)m.getTerms().add("b");TValue t=m.getTValues().add(v,a);
        m.getTValues().dropAction();observe(m,"persistent-baseline");
        require(t.applyMap(map(t,m.getId(),b.getId(),v.getId(),false))==t,"applyMap native return identity");
        require(!t.isLoaded()&&t.peekBuiltinTerm()==null,"map clears resident references");observe(m,"map-term-change");
        require(!t.isLoaded()&&t.peekBuiltinTerm()==null,"journal keeps references unhydrated");
        require(t.getValue(m)==b&&t.getTVar(m)==v,"map references hydrate natively");
        require(m.getTValues().find(v,a)==null&&m.getTValues().find(v,b)==null,"map does not repair native hash index");
        require(t.apply(packet(t,m.getId(),a.getId(),v.getId(),false))==t,"binary apply native return identity");observe(m,"binary-term-change");
        require(t.getValueId()==a.getId()&&t.peekBuiltinTerm()==b&&!t.isLoaded(),"binary apply keeps resident term with stale ID");
        require(t.getValue(m)==b,"binary apply does not replace nonnull resident term");
        t.setValue(a);observe(m,"restore-resident-term");
        require(m.getTValues().find(v,a)==t&&t.isLoaded(),"explicit native setter restores resident term and original lookup");
        t.applyMap(map(t,m.getId(),a.getId(),v.getId(),true));observe(m,"map-deleted-true");require(t.isDeleted(m),"map true sets native deletion");
        t.applyMap(map(t,m.getId(),a.getId(),v.getId(),false));observe(m,"map-deleted-false");require(t.isDeleted(m),"map false does not clear existing deletion");
        t.apply(packet(t,m.getId(),a.getId(),v.getId(),false));observe(m,"binary-deleted-false");require(t.isDeleted(m),"binary false does not clear deletion");
        t.setDeleted(false,m);observe(m,"explicit-resurrection");require(!t.isDeleted(m),"native explicit restoration works");
        t.apply(packet(t,m.getId(),a.getId(),v.getId(),true));observe(m,"binary-deleted-true");require(t.isDeleted(m),"binary true sets native deletion");t.setDeleted(false,m);observe(m,"second-explicit-resurrection");
        t.setValue(a);m.getTValues().mark();t.applyMap(map(t,m.getId(),b.getId(),v.getId(),false));observe(m,"persistent-checkpoint-deferred");m.getTValues().release();observe(m,"map-payload-survives-release");
        require(t.getValueId()==b.getId(),"in-place map write survives native addition rollback");
        t.setValue(a);observe(m,"before-truncated-packet");
        try{t.apply(new ByteBuffer().putLong(t.getId()).putLong(m.getId()).putByte(0).putLong(b.getId()));throw new AssertionError("truncated packet must throw");}catch(OutOfBufferException expected){}
        require(t.getValueId()==b.getId()&&t.getTVarId()==v.getId()&&t.peekBuiltinTerm()==a,"truncated apply retains partial native term write and resident term");observe(m,"truncated-apply-survives");
        t.setValue(a);observe(m,"before-invalid-map");Map<String,Object> invalid=map(t,m.getId(),b.getId(),v.getId(),false);invalid.put("tvar_id","invalid");
        try{t.applyMap(invalid);throw new AssertionError("invalid map must throw");}catch(NumberFormatException expected){}
        require(t.getValueId()==b.getId()&&t.getTVarId()==v.getId()&&t.peekBuiltinTerm()==a,"invalid map keeps partial term write before clearing refs");observe(m,"partial-map-survives");
        t.setValue(a);observe(m,"before-owner-ID-only");int before=shadow()?TValueDirtyJournal.contextChangeCount(m):0;t.setMindId(m.getId()+71);observe(m,"owner-ID-only");
        require(t.getMindId()==m.getId()+71,"owner ID setter remains native");if(shadow())require(TValueDirtyJournal.contextChangeCount(m)==before,"owner ID outside recorded projection");else require(true,"matching control assertion");
        t.setMindId(m.getId());t.setId(t.getId());observe(m,"same-ID-and-owner-restored");
        TValue probe=new TValue(m);probe.applyMap(map(t,m.getId(),b.getId(),v.getId(),false));probe.apply(packet(t,m.getId(),a.getId(),v.getId(),false));probe.setId(t.getId());observe(m,"unregistered-persistence-probe");
        require(m.getTValues().get(t.getId())==t&&t.getValueId()==a.getId(),"unregistered same-ID hydration object does not replace canonical value");
        require(TValueMetadataNativeRunner.ids(m,v).equals(Collections.singletonList(t.getId()))&&m.getTValues().find(v,a)==t,"native final ordered/hash routes preserved");
        require(!m.getTValues().isAction()&&!t.isDeleted(m)&&m.pendingTransactionCount()==0,"native final action/deletion/reservations");
        System.out.println("PERSISTENCE_NATIVE value="+t.getId()+":"+t.getTVarId()+":"+t.getValueId()+" map_hydration=lazy binary_resident=stale false_deleted=retained partial_binary=survives partial_map=survives rollback_payload=survives owner_ID=outside_projection reservations=0");
        System.out.println("TVALUE_PERSISTENCE_OK checks="+checks);
        if(shadow()){List<String> rows=TValueDirtyJournal.finish();Files.write(Paths.get(System.getProperty("journal.path")),rows,StandardCharsets.UTF_8);System.out.println(rows.get(rows.size()-1));}
    }
}
