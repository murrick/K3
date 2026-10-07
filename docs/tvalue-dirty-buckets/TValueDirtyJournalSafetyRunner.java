package org.kanger;
import java.util.*;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import org.kanger.units.*;
import org.kanger.interfaces.IRule;
/** Native authority gates for delayed canonical boundary records. */
public final class TValueDirtyJournalSafetyRunner {
    private static int checks;
    private static void require(boolean ok,String msg){if(!ok)throw new AssertionError(msg);checks++;}
    private static List<Long> ids(Mind m,TVariable v)throws Exception{List<Long> r=new ArrayList<>();m.getTValues().forEach(v,o->{r.add(((TValue)o).getId());return true;});return r;}
    public static void main(String[] args)throws Exception{
        TValueDirtyJournal.begin();
        try{checkpoints();publication("accept");publication("reject");publication("user-reject");publication("exception");deletionLifecycle();}
        finally{List<String> rows=TValueDirtyJournal.finish();Files.write(Paths.get(System.getProperty("journal.path")),rows,StandardCharsets.UTF_8);System.out.println(rows.get(rows.size()-1));}
        System.out.println("TVALUE_DIRTY_JOURNAL_SAFETY_OK checks="+checks);
    }
    private static void checkpoints()throws Exception{
        Mind m=new Mind(new User());
        for(String term:new String[]{"a","b","c"})require(m.compileLine("!keep("+term+");",false,null)!=null,"native retained term rule");
        Rule owner=(Rule)m.compileLine("!@x journal_owner(x);",false,null);require(owner!=null,"native owner");
        TVariable v=owner.getTree().get(0).get(0).getArguments().getTVariables(m).iterator().next();
        TValue a=m.getTValues().add(v,m.getTerms().add("a"));v.setCurrent(a);m.getTValues().dropAction();
        TValueDirtyJournal.observe(m,"baseline-add");int base=TValueDirtyJournal.contextChangeCount(m);
        require(base==1,"one baseline value bucket delta");
        m.getTValues().mark();TValue b=m.getTValues().add(v,m.getTerms().add("b"));
        m.getTValues().mark();m.getTValues().add(v,m.getTerms().add("c"));TValueDirtyJournal.observe(m,"inner-provisional");
        require(TValueDirtyJournal.contextChangeCount(m)==base,"no inner provisional publication");
        m.getTValues().commit();TValueDirtyJournal.observe(m,"inner-committed-provisional");
        require(TValueDirtyJournal.contextChangeCount(m)==base,"inner commit remains provisional to outer");
        v.setCurrent(b);m.getTValues().release();TValueDirtyJournal.observe(m,"outer-rollback");
        require(ids(m,v).equals(Collections.singletonList(a.getId())),"nested additions and enumeration rolled back");
        require(TValueDirtyJournal.contextChangeCount(m)==base,"rolled-back additions omitted from journal");
        require(v.getCurrent()==b&&!m.getTValues().isAction(),"observer does not restore transient projection or action");v.setCurrent(a);
        m.getTValues().mark();b=m.getTValues().add(v,m.getTerms().add("b"));m.getTValues().mark();TValue c=m.getTValues().add(v,m.getTerms().add("c"));m.getTValues().commit();m.getTValues().commit();
        TValueDirtyJournal.observe(m,"outer-commit");require(ids(m,v).equals(Arrays.asList(a.getId(),b.getId(),c.getId())),"native committed enumeration order");
        require(TValueDirtyJournal.contextChangeCount(m)==base+1,"one surviving bucket delta for nested commit");
        require(m.getTValues().add(v,m.getTerms().add("b"))==b,"duplicate canonical identity");TValueDirtyJournal.observe(m,"duplicate-add");
        require(TValueDirtyJournal.contextChangeCount(m)==base+1,"duplicate add emits no delta");
        b.setDeleted(true,m);TValueDirtyJournal.observe(m,"logical-delete");require(TValueDirtyJournal.contextChangeCount(m)==base+2,"logical deletion recorded");
        require(ids(m,v).contains(b.getId()),"journal does not filter deleted enumeration identities");
        b.setDeleted(true,m);TValueDirtyJournal.observe(m,"duplicate-delete");require(TValueDirtyJournal.contextChangeCount(m)==base+2,"duplicate delete emits no delta");
        require(m.getTValues().add(v,m.getTerms().add("b"))==b,"resurrection reuses canonical ID");TValueDirtyJournal.observe(m,"resurrect");
        require(TValueDirtyJournal.contextChangeCount(m)==base+3&&!b.isDeleted(m),"resurrection state change recorded");
        b.setDeleted(true,m);TValueDirtyJournal.observe(m,"before-pack");m.getTValues().pack();TValueDirtyJournal.observe(m,"after-pack");
        require(ids(m,v).equals(Arrays.asList(a.getId(),c.getId())),"physical pack removal retains authoritative order");
        require(TValueDirtyJournal.contextChangeCount(m)==base+5,"logical and physical removal distinct deltas");
        m.getTValues().clear();TValueDirtyJournal.observe(m,"factory-reset");require(m.getTValues().size()==0&&v.getCurrent()==null,"native reset remains authoritative");
        require(m.pendingTransactionCount()==0,"checkpoint fixture settled");
        System.out.println("JOURNAL_CHECKPOINT_CASE nested_rollback=omitted nested_commit=batched duplicate=omitted deleted_enumerated=true resurrection=same_id pack=removed reset=empty current_projection=untouched");
    }
    private static void publication(String mode)throws Exception{
        Mind parent=new Mind(new User());Rule owner=new Rule(parent);parent.getRules().register(owner);
        TVariable v=parent.getTVars().createTVar(owner,parent.getTerms().add("x"));
        TValue a=parent.getTValues().add(v,parent.getTerms().add("a"));TValueDirtyJournal.observe(parent,"parent-baseline");
        Mind child=new Mind(parent);require(child.compileLine("!keep(b);",false,null)!=null,"child retained term");
        if(mode.equals("accept"))require(child.compileLine("!keep(a);",false,null)!=null,"accepted child retains baseline term");
        TValue b=child.getTValues().add(v,child.getTerms().add("b"));TValueDirtyJournal.observe(child,"child-add");
        require(ids(parent,v).equals(Collections.singletonList(a.getId())),"child addition invisible in parent");
        TValueDirtyJournal.observe(parent,"parent-before-child-settle");int before=TValueDirtyJournal.contextChangeCount(parent);
        if(!mode.equals("accept")){
            if(mode.contains("reject")){Rule bad=(Rule)child.compileLine("!~keep(a);",false,null);require(bad!=null,"direct native contradictory child rule");bad.setStored(child);require(bad.isStored(),"native stored collision operand");}
            Mind sibling=new Mind(parent);Rule fact=(Rule)sibling.compileLine("!keep(a);",false,null);require(fact!=null,"sibling stored baseline fact");fact.setStored(sibling);
            require(parent.commit(sibling),"sibling publication forces non-sequenced commit path");
            require(!parent.getRules().isSequencedBy(child.getRules()),"non-sequenced native rejection/exception path reached");
            before=TValueDirtyJournal.contextChangeCount(parent);
            if(mode.equals("exception")){Field field=Mind.class.getDeclaredField("analyzer");field.setAccessible(true);field.set(parent,new FailingAnalyzer(parent));}
        }
        boolean accepted=false,failure=false;
        try{accepted=mode.equals("user-reject")?parent.commitUserTransaction(child):parent.commit(child);}catch(InjectedFailure e){failure=true;}
        require(accepted==mode.equals("accept")&&failure==mode.equals("exception"),"native settlement outcome "+mode);
        if(mode.equals("accept")){
            require(ids(parent,v).equals(Arrays.asList(a.getId(),b.getId())),"accepted child appears in parent order");
            require(TValueDirtyJournal.contextChangeCount(parent)==before+1,"accepted child produces one parent bucket delta");
        }else{
            require(ids(parent,v).equals(Collections.singletonList(a.getId())),"rejected or failed child canonical effects removed");
            require(TValueDirtyJournal.contextChangeCount(parent)==before,"rejected or failed child produces no false parent delta");
        }
        if(mode.equals("user-reject")){require(parent.pendingTransactionCount()==1,"user rejection retains live child reservation");TValueDirtyJournal.observe(child,"live-rejected-child");parent.release(child);}
        require(parent.pendingTransactionCount()==0,"parent reservation settled "+mode);
        System.out.println("JOURNAL_PUBLICATION_CASE mode="+mode+" accepted="+accepted+" failure="+failure+" parent_delta="+(mode.equals("accept")?1:0));
    }
    private static void deletionLifecycle()throws Exception{
        Mind m=new Mind(new User());
        require(m.compileLine("!keep(a);",false,null)!=null,"retain a");require(m.compileLine("!keep(b);",false,null)!=null,"retain b");
        Rule owner=(Rule)m.compileLine("!@x dirty_owner(x);",false,null);require(owner!=null,"native dirty owner");
        TVariable v=owner.getTree().get(0).get(0).getArguments().getTVariables(m).iterator().next();
        TValue a=m.getTValues().add(v,m.getTerms().add("a"));TValueDirtyJournal.observe(m,"deletion-baseline");int base=TValueDirtyJournal.contextChangeCount(m);
        m.getTValues().mark();m.getTValues().add(v,m.getTerms().add("b"));a.setDeleted(true,m);m.getTValues().release();TValueDirtyJournal.observe(m,"deletion-survives-local-release");
        require(ids(m,v).equals(Collections.singletonList(a.getId())),"local release removes provisional addition");require(a.isDeleted(m),"local release does not undo deletion map");require(TValueDirtyJournal.contextChangeCount(m)==base+1,"surviving deletion recorded after release");
        m.getTValues().mark();require(m.getTValues().add(v,m.getTerms().add("a"))==a,"checkpoint resurrection canonical ID");m.getTValues().release();TValueDirtyJournal.observe(m,"resurrection-survives-local-release");
        require(!a.isDeleted(m),"local release does not undo resurrection map");require(TValueDirtyJournal.contextChangeCount(m)==base+2,"surviving resurrection recorded after release");
        m.getDeleted().computeIfAbsent(org.kanger.enums.UnitType.TVALUE,k->new HashSet<>()).add(a.getId());TValueDirtyJournal.observe(m,"raw-deletion-alias");
        require(a.isDeleted(m),"raw alias changes native visibility");require(TValueDirtyJournal.contextChangeCount(m)==base+3,"raw alias dirty bucket selected");
        Mind child=new Mind(m);child.getRestored().computeIfAbsent(org.kanger.enums.UnitType.TVALUE,k->new HashSet<>()).add(a.getId());TValueDirtyJournal.observe(child,"raw-child-restoration-alias");
        require(!a.isDeleted(child)&&a.isDeleted(m),"child restoration overrides parent deletion");
        m.getDeleted().get(org.kanger.enums.UnitType.TVALUE).clear();TValueDirtyJournal.observe(m,"raw-parent-deletion-clear");TValueDirtyJournal.observe(child,"ancestor-alias-clear");
        require(!a.isDeleted(m)&&!a.isDeleted(child),"parent alias removal reflected in both native views");
        child.getRestored().get(org.kanger.enums.UnitType.TVALUE).clear();TValueDirtyJournal.observe(child,"raw-child-restoration-clear");require(!a.isDeleted(child),"removing redundant restoration has no semantic delta");m.release(child);require(m.pendingTransactionCount()==0,"alias fixture settled");
        // Keep a second child live to observe accepted deletion before root pack.
        Mind held=new Mind(m),published=new Mind(m);published.getDeleted().computeIfAbsent(org.kanger.enums.UnitType.TVALUE,k->new HashSet<>()).add(a.getId());TValueDirtyJournal.observe(published,"published-child-delete");
        int before=TValueDirtyJournal.contextChangeCount(m);require(m.commit(published),"accepted child visibility publication");require(a.isDeleted(m)&&ids(m,v).contains(a.getId()),"accepted deletion remains enumerated before root pack");require(TValueDirtyJournal.contextChangeCount(m)==before+1,"merged visibility map produces one parent delta");
        TValueDirtyJournal.observe(held,"held-child-observes-published-map");require(a.isDeleted(held),"live sibling observes inherited deletion");m.release(held);require(!ids(m,v).contains(a.getId()),"quiescent root pack removes accepted deleted identity");require(m.pendingTransactionCount()==0,"publication fixture settled");
        System.out.println("DIRTY_VISIBILITY_CASE deletion_release=survives resurrection_release=survives aliases=observed ancestry=observed publication=observed root_pack=removed");
    }
    private static final class InjectedFailure extends RuntimeException {}
    private static final class FailingAnalyzer extends Analyzer {
        FailingAnalyzer(Mind m){super(m);}
        @Override public boolean checkDatabase(Set<Long> rules,boolean logging){throw new InjectedFailure();}
    }
}
