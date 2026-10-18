/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextUniversalFamilyInspection.Result;
import org.kanger.ContextUniversalFamilyInspection.Status;
import org.kanger.ContextUniversalFamilyAudit.Audit;

/** Whole-state applicability, exact identities and all-or-nothing independent verdict replay. */
public final class ContextUniversalFamilyAuditRunner {
    private static int checks,ids;
    private static final int WORK=64000000;
    private static void require(boolean yes,String why) {++checks;if(!yes) throw new AssertionError(why);}
    private static final class Fixture {
        final Mind mind;final Snapshot snapshot;
        Fixture(boolean x,String text) throws Exception {
            User user=new User();mind=new Mind(user);user.setCurrentMind(mind);
            if(!text.isEmpty()) require(mind.compile(text,null,false),"native fixture");
            ContextProductionInventory inventory=ContextProductionInventory.inspect(mind,new UUID(0,++ids),1,x,Collections.emptyList(),1000);
            ContextProductionNativeBinding.Result bound=ContextProductionNativeBinding.bind(mind,inventory,WORK,500000);
            require(bound.bound,"whole native binding: "+bound.reason);snapshot=bound.snapshot;
        }
        void unchanged() throws Exception {ContextProductionInventory i=snapshot.inventory;require(i.fingerprint.equals(ContextProductionInventory.inspect(mind,i.context,i.revision,i.localX,i.initialization,1000).fingerprint),"original inventory unchanged");}
        Snapshot rebind() {ContextProductionNativeBinding.Result r=ContextProductionNativeBinding.bind(mind,snapshot.inventory,WORK,500000);require(r.bound,"same-view rebind");return r.snapshot;}
    }
    private static Result inspect(Fixture x,List<Snapshot> foreign) {return ContextUniversalFamilyInspection.inspect(x.snapshot,foreign,16,16,16,20000,1000000,WORK);}
    private static Audit audit(Result r,Fixture x,List<Snapshot> foreign) {return ContextUniversalFamilyAudit.audit(r,x.snapshot,foreign,16,20000,WORK);}
    private static void refused(Audit a,String why) {require(!a.verified && a.oppositions.isEmpty(),why+": "+a.reason);}
    public static void main(String[] args) {
        try {
            System.setProperty("user.home",java.nio.file.Files.createTempDirectory("whole-family-audit-").toString());
            Fixture x=new Fixture(true,"!@x left(x)&&right(x)->p(x); !extra(1);"),a=new Fixture(false,"!left(1); !p(1);"),b=new Fixture(false,"!right(1); !~p(1);");
            List<Snapshot> foreign=Arrays.asList(a.snapshot,b.snapshot);
            Result collision=inspect(x,foreign);Audit checked=audit(collision,x,foreign);
            require(collision.status==Status.COLLISION && checked.verified && checked.verdict==collision && checked.oppositions.size()==collision.collision.oppositions.size(),"whole multi-primary state collision independently audited");
            require(audit(collision,x,Arrays.asList(b.snapshot,a.snapshot)).verified,"expected family order is irrelevant");
            Set<ContextProductionCapture.Source> universe=new HashSet<>();
            for(Snapshot s:Arrays.asList(x.snapshot,a.snapshot,b.snapshot)) s.primary.forEach(e -> universe.add(e.source));
            require(checked.oppositions.stream().allMatch(p -> universe.containsAll(p.support.sources) && p.contradiction && p.dependsOnX),"whole proof sources belong to exact original state, no invented candidate");
            Fixture safe=new Fixture(true,"!newone(1); !newtwo(2);"),empty=new Fixture(true,"");
            Result consistent=inspect(safe,foreign);
            require(consistent.status==Status.CONSISTENT && audit(consistent,safe,foreign).verified,"whole state with multiple edits needs no single-candidate staging");
            require(audit(inspect(empty,Collections.emptyList()),empty,Collections.emptyList()).verified,"empty whole state has an independently verified all-value interpretation");
            refused(ContextUniversalFamilyAudit.audit(consistent,safe.rebind(),foreign,16,20000,WORK),"same fingerprint X cannot substitute exact snapshot identity");
            refused(audit(consistent,safe,Arrays.asList(a.rebind(),b.snapshot)),"same fingerprint foreign cannot substitute exact snapshot identity");
            refused(audit(consistent,safe,Collections.singletonList(a.snapshot)),"missing expected cut");
            refused(audit(consistent,safe,Arrays.asList(a.snapshot,a.snapshot)),"repeated expected cut");
            refused(audit(consistent,x,foreign),"different whole X");
            refused(audit(null,safe,foreign),"absent verdict");
            Result stopped=ContextUniversalFamilyInspection.inspect(x.snapshot,foreign,1,16,16,20000,1000000,WORK);
            refused(audit(stopped,x,foreign),"incomplete barrier gets no verified verdict");
            refused(ContextUniversalFamilyAudit.audit(consistent,safe.snapshot,foreign,16,1,WORK),"one instance allowance across all model recertification");
            Fixture positive=new Fixture(true,"!@x @y p(x)||p(y); !@x @y q(x)||q(y);"),negative=new Fixture(false,"!@x @y ~p(x)||~p(y); !@x @y ~q(x)||~q(y);");
            Result alternatives=inspect(positive,Collections.singletonList(negative.snapshot));Audit all=audit(alternatives,positive,Collections.singletonList(negative.snapshot));
            require(all.verified && all.oppositions.size()>=2 && all.oppositions.size()==alternatives.collision.oppositions.size(),"all whole-proof alternatives retained, not only first opposition");
            refused(ContextUniversalFamilyAudit.audit(alternatives,positive.snapshot,Collections.singletonList(negative.snapshot),1,20000,WORK),"cumulative-depth stop drops every proof prefix");
            refused(ContextUniversalFamilyAudit.audit(alternatives,positive.snapshot,Collections.singletonList(negative.snapshot),16,20000,1),"single audit work budget");
            int low=0,high=WORK;
            while(high-low>1) {int mid=low+(high-low)/2;Audit r=ContextUniversalFamilyAudit.audit(alternatives,positive.snapshot,Collections.singletonList(negative.snapshot),16,20000,mid);if(r.verified) high=mid;else {require(r.oppositions.isEmpty(),"no partial evidence on any insufficient audit budget");low=mid;}}
            require(ContextUniversalFamilyAudit.audit(alternatives,positive.snapshot,Collections.singletonList(negative.snapshot),16,20000,high).verified,"minimum complete audit budget succeeds");
            refused(ContextUniversalFamilyAudit.audit(alternatives,positive.snapshot,Collections.singletonList(negative.snapshot),16,20000,high-1),"last charged operation refusal is all-or-nothing");
            boolean immutable=false;try {all.oppositions.clear();} catch(UnsupportedOperationException expected) {immutable=true;}require(immutable,"audited oppositions immutable");
            x.unchanged();a.unchanged();b.unchanged();safe.unchanged();empty.unchanged();positive.unchanged();negative.unchanged();
            System.out.println("CONTEXT_UNIVERSAL_FAMILY_AUDIT_PASS checks="+checks);System.exit(0);
        } catch(Throwable failure) {failure.printStackTrace();System.exit(1);}
    }
}
