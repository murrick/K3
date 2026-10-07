package org.kanger;
import java.util.*;
import org.kanger.units.*;
/** Native consequences are identical with and without the observer. */
public final class TValueMetadataNativeRunner {
    static int checks;
    static void require(boolean ok,String msg){if(!ok)throw new AssertionError(msg);checks++;}
    static boolean shadow(){return Boolean.getBoolean("metadata.shadow");}
    static void observe(Mind m,String reason){if(shadow())TValueDirtyJournal.observe(m,reason);}
    static TVariable variable(Mind m,String name)throws Exception{
        Rule r=(Rule)m.compileLine("!@x "+name+"(x);",false,null);
        return r.getTree().get(0).get(0).getArguments().getTVariables(m).iterator().next();
    }
    static List<Long> ids(Mind m,TVariable v)throws Exception{List<Long> ids=new ArrayList<>();m.getTValues().forEach(v,o->{ids.add(((TValue)o).getId());return true;});return ids;}
    public static void main(String[] args)throws Exception{
        checks=0;Mind m=new Mind(new User());
        m.compileLine("!keep(a);",false,null);m.compileLine("!keep(b);",false,null);
        TVariable v=variable(m,"metadata_owner");Term a=(Term)m.getTerms().add("a"),b=(Term)m.getTerms().add("b");
        TValue t=m.getTValues().add(v,a);v.setCurrent(t);m.getTValues().dropAction();observe(m,"metadata-baseline");
        require(m.getTValues().find(v,a)==t,"original hash lookup");
        Mind child=new Mind(m);TValue inherited=child.getTValues().get(t.getId());require(inherited==t,"ancestor canonical object shared");
        t.setValue(b);observe(m,"setValue-parent");observe(child,"setValue-child");
        require(t.getValueId()==b.getId()&&t.getValue(m)==b,"setter changes native payload");
        require(m.getTValues().get(t.getId())==t&&ids(m,v).equals(Collections.singletonList(t.getId())),"ID and ordered variable lookup preserved");
        require(m.getTValues().find(v,a)==null&&m.getTValues().find(v,b)==null,"native hash index remains stale");
        require(!m.getTValues().isAction()&&v.getCurrent()==t,"setter does not change native action/current");
        inherited.setValue(a);observe(m,"restore-through-child-alias");observe(child,"restore-through-child-alias");
        require(m.getTValues().find(v,a)==t,"original hash route usable after restoration");
        m.release(child);require(m.pendingTransactionCount()==0,"shared child settled");
        t.setPersistentReferences(b.getId(),v.getId());require(!t.isLoaded()&&t.peekBuiltinTerm()==null,"persistent references unhydrated");
        observe(m,"persistent-term-change");require(!t.isLoaded()&&t.peekBuiltinTerm()==null,"ID observer does not hydrate payload");
        require(t.getValue(m)==b&&t.isLoaded(),"native lazy hydration remains usable");
        t.setTVar(v);observe(m,"same-variable-setter");require(t.getTVar(m)==v,"same variable retained");
        m.getTValues().mark();t.setValue(a);observe(m,"checkpoint-deferred");m.getTValues().release();observe(m,"payload-survives-release");
        require(t.getValueId()==a.getId()&&m.getTValues().find(v,a)==t,"payload mutation survives native addition rollback");
        m.getTValues().mark();m.getTValues().mark();t.setValue(b);m.getTValues().commit();m.getTValues().commit();observe(m,"payload-survives-nested-commit");
        require(t.getValueId()==b.getId(),"payload survives native nested completion");
        int before=shadow()?TValueDirtyJournal.contextChangeCount(m):0;
        t.setValue(a);t.setValue(b);t.setTVar(v);observe(m,"transient-net-no-change");
        if(shadow()&&TValueDirtyJournal.contextChangeCount(m)!=before)throw new AssertionError("transient payload must not publish a delta");
        TValue probe=new TValue(v,a,m);probe.setId(t.getId());probe.setValue(b);probe.setPersistentReferences(a.getId(),v.getId());probe.setTVar(v);observe(m,"unregistered-same-ID-probe");
        require(m.getTValues().get(t.getId())==t&&t.getValueId()==b.getId(),"unregistered same-ID object leaves canonical native state");
        try{t.setValue(null);throw new AssertionError("native null setter must throw");}catch(NullPointerException expected){}
        require(t.getValueId()==b.getId()&&t.peekBuiltinTerm()==null,"native partially failed setter keeps ID and clears resident term");observe(m,"partial-failure-ID-unchanged");
        t.setValue(a);observe(m,"final-restoration");
        require(m.getTValues().find(v,a)==t&&ids(m,v).equals(Collections.singletonList(t.getId())),"native final canonical route restored");
        require(v.getMind()==m&&!m.getTValues().isAction()&&v.getCurrent()==t,"owner/action/current untouched");
        require(m.pendingTransactionCount()==0,"fixture settled");
        System.out.println("METADATA_NATIVE payload="+t.getId()+":"+t.getTVarId()+":"+t.getValueId()+" hash_after_change=both_missing restored_lookup=same_id ordered_ids="+ids(m,v)+" checkpoint_payload=survives hydration=lazy null_failure=partial reservations=0");
        System.out.println("TVALUE_METADATA_NATIVE_OK checks="+checks);
    }
}
