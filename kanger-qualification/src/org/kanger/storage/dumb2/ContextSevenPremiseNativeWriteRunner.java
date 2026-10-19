/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.nio.file.*;
import java.util.*;
import org.kanger.*;
import org.kanger.ContextOrdinaryResidualRounds.LedgerLimits;

/** Explicit seven-premise authoring reaches actual native settlement and durable source-only reopen. */
public final class ContextSevenPremiseNativeWriteRunner {
    static int checks;static final int WORK=64000000;
    static void require(boolean v,String why){++checks;if(!v)throw new AssertionError(why);}
    static class Fixture extends ContextPendingWitnessWriteRunner.Fixture{Fixture(Path d,String n)throws Exception{super(d,n);}}
    static String rule(){return ContextNativeClauseWidthRunner.rule(7,false);}
    static void create(Path d,String n,String text)throws Exception{try(Fixture f=new Fixture(d,n)){require(f.root.compile(text,null,false),"private fixture "+n);}}
    static void prepare(Fixture f,boolean reverse,String other)throws Exception{
        require(f.root.compile(ContextNativeClauseWidthRunner.base(7),null,false),"six X premises");
        f.db.connectContext(reverse?other:"A");f.db.connectContext(reverse?"A":other);
        f.db.saveConnections(f.root);
    }
    static Set<String> origins(ContextProductionInventory inventory){Set<String> result=new TreeSet<>();for(ContextProductionInventory.Row row:inventory.primary)result.add(row.origin);return result;}
    static ContextProductionInventory inventory(Fixture f)throws Exception{try(ContextProductionSnapshotSession lease=ContextProductionSnapshotSession.open(f.db,f.root,1000)){return lease.inventory().x;}}
    static List<RevisionRef> pins(Fixture f){List<RevisionRef> result=new ArrayList<>();for(ContextConnection c:f.db.dmzTopology())result.add(c.getTarget());return result;}
    static DB.GroundProductionWriteResult write(Fixture f,LedgerLimits limits)throws Exception{return f.db.writeGroundProductionCandidate(f.root,rule(),1000,16,16,16,WORK,500000,100000,1000000,false,limits);}
    public static void main(String[] args){try{
        Path dir=Files.createTempDirectory("seven-native-write-");System.setProperty("user.home",dir.toString());create(dir,"A","!q(1);");create(dir,"Safe","!safe(1);");
        for(boolean reverse:new boolean[]{false,true}){
            String name="Accept"+reverse;Set<String> committed;long after;List<RevisionRef> pinned;
            try(Fixture f=new Fixture(dir,name)){
                prepare(f,reverse,"Safe");long before=f.db.getRevision();Set<String> initial=origins(inventory(f));pinned=pins(f);require(initial.size()==6&&pinned.size()==2,"only six initial local source rules");
                LedgerLimits limits=new LedgerLimits(256,1024);DB.GroundProductionWriteResult result=write(f,limits);
                require(result.status==DB.ProductionWriteStatus.PUBLISHED,"actual explicit native write accepted: "+result.status+" / "+result.trial.reason);
                require(result.before.getRevision()==before&&result.after.getRevision()==f.db.getRevision()&&result.after.getRevision()>before&&result.before.getContextId().equals(result.after.getContextId()),"actual physical settlement references and advancing revision");
                ContextGroundClauseFamily.Evaluation e=result.trial.qualification;
                require(e.status==ContextGroundClauseFamily.Status.GROUND_FIXED_POINT&&e.rounds==2&&e.certificate.frontier.ledgerLimits==limits&&e.certificate.frontier.products.size()==127&&e.certificate.models.size()==3,"complete explicit pretrial retains policy, recipes and separate models");
                require(e.certificate.frontier.products.stream().anyMatch(p->p.clause.literals.size()==1&&p.clause.literals.get(0).positive&&p.clause.literals.get(0).predicate.equals("out")&&p.proof.depth==1&&p.proof.sources.size()==8&&p.proof.sources.stream().filter(s->s.localX).count()==7&&p.proof.sources.stream().filter(s->!s.localX).map(s->s.context).distinct().count()==1),"out keeps seven X sources and one private donor at depth1");
                ContextProductionInventory inv=inventory(f);committed=origins(inv);after=f.db.getRevision();
                require(pins(f).equals(pinned),"publication preserves exact foreign pins and traversal order");
                require(inv.primary.size()==7&&committed.size()==7&&committed.containsAll(initial),"only the whole new source rule is added to X");
                require(inv.primary.stream().noneMatch(r->r.origin.equals("!q(1);")||r.origin.equals("!safe(1);")||r.origin.equals("!out(1);")),"private facts and derived product never become X primary rows");
            }
            try(Fixture f=new Fixture(dir,name)){
                ContextProductionInventory inv=inventory(f);require(f.db.getRevision()>=after&&inv.primary.size()==7&&origins(inv).equals(committed)&&pins(f).equals(pinned),"reopened durable X contains exactly the committed source theory");
                require(inv.primary.stream().noneMatch(r->r.origin.equals("!q(1);")||r.origin.equals("!safe(1);")||r.origin.equals("!out(1);")),"reopen cannot materialize foreign/private product as primary");
            }
        }
        System.out.println("CONTEXT_SEVEN_PREMISE_NATIVE_WRITE_PASS checks="+checks);System.exit(0);
    }catch(Throwable e){e.printStackTrace();System.exit(1);}}
}
