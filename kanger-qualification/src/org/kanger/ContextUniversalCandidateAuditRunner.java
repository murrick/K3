/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextProductionCapture.Source;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextUniversalFamilyInspection.Status;
import org.kanger.ContextUniversalCandidateAudit.Audit;

/** Exact candidate applicability and candidate-independent/mixed whole-proof attribution. */
public final class ContextUniversalCandidateAuditRunner {
    private static int checks,ids;
    private static final int WORK = 64000000;
    private static void require(boolean yes,String why) { ++checks; if (!yes) throw new AssertionError(why); }
    private static final class Fixture {
        final Mind mind; final Snapshot snapshot;
        Fixture(boolean x,String text) throws Exception {
            User u = new User(); mind = new Mind(u); u.setCurrentMind(mind);
            if (!text.isEmpty()) require(mind.compile(text,null,false),"native fixture");
            ContextProductionInventory i = ContextProductionInventory.inspect(mind,new UUID(0,++ids),1,x,Collections.emptyList(),1000);
            ContextProductionNativeBinding.Result b = ContextProductionNativeBinding.bind(mind,i,WORK,500000);
            require(b.bound,"native binding"); snapshot = b.snapshot;
        }
        void unchanged() throws Exception {
            ContextProductionInventory i = snapshot.inventory;
            require(i.fingerprint.equals(ContextProductionInventory.inspect(mind,i.context,i.revision,i.localX,i.initialization,1000).fingerprint),"caller native inventory unchanged");
        }
    }
    private static ContextUniversalCandidate.Evaluation qualify(Fixture x,List<Snapshot> foreign,String text) {
        return ContextUniversalCandidate.qualify(x.snapshot,foreign,text,1000,8,16,16,20000,1000000,WORK,500000,WORK);
    }
    private static void incomplete(ContextUniversalCandidate.Evaluation e,String why) {
        require(e.status==Status.INCOMPLETE && (e.qualification==null || e.qualification.certificate==null && e.qualification.collision==null),why+": "+e.reason);
    }
    private static Audit audit(ContextUniversalCandidate.Evaluation e,Fixture x,List<Snapshot> foreign,String statement) {
        return ContextUniversalCandidateAudit.audit(e,x.snapshot,foreign,statement,16,20000,WORK);
    }
    private static void refused(Audit a,String why) { require(!a.verified && a.oppositions.isEmpty(),why+": "+a.reason); }
    public static void main(String[] args) {
        try {
            System.setProperty("user.home",java.nio.file.Files.createTempDirectory("universal-candidate-audit-").toString());
            Fixture empty=new Fixture(true,""),p=new Fixture(false,"!@x p(x);"),nq=new Fixture(false,"!~q(9);");
            String rule="!@x p(x)->q(x);";
            ContextUniversalCandidate.Evaluation clash=qualify(empty,Arrays.asList(p.snapshot,nq.snapshot),rule);
            Audit cause=audit(clash,empty,Arrays.asList(p.snapshot,nq.snapshot),rule);
            require(clash.status==Status.COLLISION && cause.verified && !cause.oppositions.isEmpty(),"candidate collision independently checked: "+cause.reason);
            require(cause.oppositions.stream().allMatch(a -> a.usesCandidate && a.originalSources.contains(clash.artifact.candidate)),"actual candidate Source appears in every new-rule opposition");
            require(cause.oppositions.stream().allMatch(a -> a.originalSources.equals(a.proof.support.sources)),"foreign/candidate-only proof needs no original-X translation");
            Fixture old=new Fixture(true,rule); String unrelated="!@x unrelated(x);";
            ContextUniversalCandidate.Evaluation oldCollision=qualify(old,Arrays.asList(p.snapshot,nq.snapshot),unrelated);
            Audit preexisting=audit(oldCollision,old,Arrays.asList(p.snapshot,nq.snapshot),unrelated);
            require(oldCollision.status==Status.COLLISION && preexisting.verified && !preexisting.oppositions.isEmpty(),"concrete unrelated-candidate counterexample rechecked");
            require(preexisting.oppositions.stream().noneMatch(a -> a.usesCandidate),"family COLLISION does not prove the unrelated candidate false");
            Source original=old.snapshot.primary.get(0).source,rebuilt=oldCollision.artifact.rebased.get(original);
            Set<Source> expected=new HashSet<>(Arrays.asList(original,p.snapshot.primary.get(0).source,nq.snapshot.primary.get(0).source));
            require(preexisting.oppositions.stream().allMatch(a -> a.originalSources.equals(expected) && !a.originalSources.contains(rebuilt) && a.proof.support.sources.contains(rebuilt)),"whole original source sets translated without mutating original staged proofs");
            Fixture baseFactor=new Fixture(true,"!@x @y p(x)||p(y);"),negative=new Fixture(false,"!@x @y ~p(x)||~p(y); !@x @y ~q(x)||~q(y);");
            String candidateFactor="!@x @y q(x)||q(y);";
            ContextUniversalCandidate.Evaluation mixed=qualify(baseFactor,Collections.singletonList(negative.snapshot),candidateFactor);
            Audit both=audit(mixed,baseFactor,Collections.singletonList(negative.snapshot),candidateFactor);
            require(both.verified && both.oppositions.stream().anyMatch(a -> a.usesCandidate) && both.oppositions.stream().anyMatch(a -> !a.usesCandidate),"mixed candidate and old-base oppositions remain distinct whole proofs: "+both.reason);
            require(both.oppositions.size()==mixed.qualification.collision.oppositions.size(),"no alternative opposition dropped by attribution");
            require(both.oppositions.stream().allMatch(a -> a.usesCandidate==a.proof.support.sources.contains(mixed.artifact.candidate)),"localX source is never substituted for exact candidate dependence");
            Fixture anchor=new Fixture(true,"!anchor(1);"); String safe="!@x p(x)->r(x);";
            ContextUniversalCandidate.Evaluation ok=qualify(anchor,Arrays.asList(p.snapshot,nq.snapshot),safe);
            Audit good=audit(ok,anchor,Arrays.asList(p.snapshot,nq.snapshot),safe);
            require(ok.status==Status.CONSISTENT && good.verified && good.oppositions.isEmpty() && good.evaluation==ok,"exact complete model certificate independently rechecked, no publication capability");
            require(audit(ok,anchor,Arrays.asList(nq.snapshot,p.snapshot),safe).verified,"independent expected family order does not change identity matching");
            refused(audit(ok,anchor,Arrays.asList(p.snapshot,nq.snapshot),"!@x p(x)->other(x);"),"requested assertion mismatch");
            Fixture other=new Fixture(true,"!anchor(1);"); refused(audit(ok,other,Arrays.asList(p.snapshot,nq.snapshot),safe),"same semantic base cannot substitute exact identity");
            ContextProductionNativeBinding.Result rebind=ContextProductionNativeBinding.bind(p.mind,p.snapshot.inventory,WORK,500000);
            require(rebind.bound && rebind.snapshot!=p.snapshot && rebind.snapshot.inventory.fingerprint.equals(p.snapshot.inventory.fingerprint),"semantically identical historical foreign rebind fixture");
            refused(audit(ok,anchor,Arrays.asList(rebind.snapshot,nq.snapshot),safe),"foreign identity must match, not merely context/revision/fingerprint");
            refused(audit(ok,anchor,Collections.singletonList(p.snapshot),safe),"missing expected cut");
            refused(audit(ok,anchor,Arrays.asList(p.snapshot,p.snapshot),safe),"repeated expected context");
            refused(audit(null,anchor,Collections.emptyList(),safe),"missing evaluation");
            ContextUniversalCandidate.Evaluation incomplete=ContextUniversalCandidate.qualify(anchor.snapshot,Collections.singletonList(p.snapshot),safe,1000,1,16,16,20000,1000000,WORK,500000,WORK);
            refused(audit(incomplete,anchor,Collections.singletonList(p.snapshot),safe),"incomplete common rounds cannot gain an applicability verdict");
            refused(ContextUniversalCandidateAudit.audit(ok,anchor.snapshot,Arrays.asList(p.snapshot,nq.snapshot),safe,16,1,WORK),"certificate model rechecks share instance allowance");
            refused(ContextUniversalCandidateAudit.audit(mixed,baseFactor.snapshot,Collections.singletonList(negative.snapshot),candidateFactor,1,20000,WORK),"late cumulative depth refusal exposes no attribution prefix");
            refused(ContextUniversalCandidateAudit.audit(mixed,baseFactor.snapshot,Collections.singletonList(negative.snapshot),candidateFactor,16,20000,1),"one shared audit work allowance");
            int low=0,high=WORK;
            while(high-low>1) {
                int mid=low+(high-low)/2;
                Audit bounded=ContextUniversalCandidateAudit.audit(mixed,baseFactor.snapshot,Collections.singletonList(negative.snapshot),candidateFactor,16,20000,mid);
                if(bounded.verified) high=mid;
                else { require(bounded.oppositions.isEmpty(),"no attribution prefix at any insufficient work allowance"); low=mid; }
            }
            require(ContextUniversalCandidateAudit.audit(mixed,baseFactor.snapshot,Collections.singletonList(negative.snapshot),candidateFactor,16,20000,high).verified,"minimal complete audit work allowance succeeds");
            refused(ContextUniversalCandidateAudit.audit(mixed,baseFactor.snapshot,Collections.singletonList(negative.snapshot),candidateFactor,16,20000,high-1),"last charged operation failure discards earlier successful whole-proof attributions");
            boolean immutable=false; try { both.oppositions.clear(); } catch(UnsupportedOperationException expectedFailure) { immutable=true; } require(immutable,"whole-proof attribution list immutable");
            immutable=false; try { both.oppositions.get(0).originalSources.clear(); } catch(UnsupportedOperationException expectedFailure) { immutable=true; } require(immutable,"translated source sets immutable");
            empty.unchanged(); p.unchanged(); nq.unchanged(); old.unchanged(); baseFactor.unchanged(); negative.unchanged(); anchor.unchanged();
            System.out.println("CONTEXT_UNIVERSAL_CANDIDATE_AUDIT_PASS checks="+checks); System.exit(0);
        } catch(Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
}
