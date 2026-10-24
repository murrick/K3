/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextUniversalFamilyInspection.Status;
import org.kanger.ContextUniversalUnitRounds.*;
import org.kanger.ContextUniversalUnitProof.Proof;

/** Independent exact whole-family verdict audit. Detached evidence, never native write authority. */
public final class ContextUniversalFamilyAudit {
    public static final class Audit {
        public final boolean verified;
        public final String reason;
        public final ContextUniversalFamilyInspection.Result verdict;
        public final List<Proof> oppositions;
        private Audit(boolean verified,String reason,ContextUniversalFamilyInspection.Result verdict,List<Proof> oppositions) {
            this.verified=verified;this.reason=reason;this.verdict=verdict;
            this.oppositions=Collections.unmodifiableList(new ArrayList<>(oppositions));
        }
    }
    private static void source(Support support,Set<Source> universe,int depth,Runnable work) {
        work.run(); if(support.depth>depth) throw new IllegalStateException("Family evidence cumulative depth limit");
        for(Source s:support.sources) { work.run(); if(!universe.contains(s)) throw new IllegalStateException("Evidence Source outside exact whole family"); }
    }
    private static boolean same(Proof a,Proof b,Runnable work) {
        work.run(); return a.contradiction==b.contradiction && a.leftPivot==b.leftPivot && a.rightPivot==b.rightPivot
            && a.left.equivalent(b.left) && a.right.equivalent(b.right) && a.leftSupport.equals(b.leftSupport)
            && a.rightSupport.equals(b.rightSupport) && a.support.equals(b.support);
    }
    public static Audit audit(ContextUniversalFamilyInspection.Result verdict,Snapshot expectedX,List<Snapshot> expectedForeign,
            int maximumDepth,int maximumInstances,int maximumWork) {
        if(maximumDepth<0 || maximumInstances<1 || maximumWork<1) throw new IllegalArgumentException("Bounded family audit required");
        try {return auditBound(verdict,expectedX,expectedForeign,maximumDepth,maximumInstances,ContextGroundProductionRounds.budget(maximumWork));}
        catch(Exception failure) {return new Audit(false,failure.getMessage(),verdict,Collections.emptyList());}
    }
    /** Candidate staging audit shares its remaining work budget with this replay. */
    static Audit auditBound(ContextUniversalFamilyInspection.Result verdict,Snapshot expectedX,List<Snapshot> expectedForeign,
            int maximumDepth,int maximumInstances,Runnable work) {
            if(verdict==null || verdict.status==Status.INCOMPLETE || verdict.completedBarriers<1)
                throw new IllegalStateException("Complete exact whole-family verdict required");
            ContextProductionFamilyAssembly.identity(expectedX,true,work);
            List<Snapshot> requested=new ArrayList<>(expectedForeign);
            Set<Snapshot> expected=Collections.newSetFromMap(new IdentityHashMap<Snapshot,Boolean>());
            Set<UUID> contexts=new HashSet<>();contexts.add(expectedX.inventory.context);
            Set<Source> universe=new HashSet<>();
            for(org.kanger.ContextProductionNativeBinding.Entry e:expectedX.primary) {work.run();if(!universe.add(e.source)) throw new IllegalStateException("Aliased whole-family Source");}
            for(Snapshot cut:requested) {
                work.run();ContextProductionFamilyAssembly.identity(cut,false,work);
                if(!expected.add(cut)) throw new IllegalStateException("Repeated expected whole-family cut");
                ContextProductionFamilyAssembly.claimContexts(cut,contexts,work);
                for(org.kanger.ContextProductionNativeBinding.Entry e:cut.primary) {work.run();if(!universe.add(e.source)) throw new IllegalStateException("Aliased whole-family Source");}
            }
            Frontier input=verdict.status==Status.CONSISTENT ? verdict.certificate==null?null:verdict.certificate.input
                : verdict.collision==null?null:verdict.collision.input;
            if(input==null || input.x!=expectedX || input.cuts.size()!=requested.size()+1) throw new IllegalStateException("Verdict is not for exact requested whole family");
            Set<Snapshot> seen=Collections.newSetFromMap(new IdentityHashMap<Snapshot,Boolean>()); seen.add(expectedX);
            for(Snapshot cut:input.cuts) {
                work.run(); if(cut==expectedX) continue;
                if(!expected.contains(cut) || !seen.add(cut)) throw new IllegalStateException("Verdict family identity mismatch");
            }
            if(seen.size()!=input.cuts.size()) throw new IllegalStateException("Incomplete verdict family");
            if(verdict.status==Status.CONSISTENT) {
                if(verdict.collision!=null) throw new IllegalStateException("Mixed whole-family verdict evidence");
                List<Receipt> receipts=new ArrayList<>(); List<ContextUniversalQuotientModel.Model> models=new ArrayList<>();
                for(ContextUniversalFamilyModel.Cut cut:verdict.certificate.cuts) { work.run(); receipts.add(cut.receipt); models.add(cut.model); }
                ContextUniversalFamilyModel.Result check=ContextUniversalFamilyModel.certifyBound(input,receipts,models,maximumDepth,
                    new int[]{Math.min(maximumInstances,65536)},work);
                if(!check.certified) throw new IllegalStateException(check.reason);
                return new Audit(true,"Exact whole family consistency independently rechecked; no publication authority",verdict,Collections.emptyList());
            }
            if(verdict.status!=Status.COLLISION || verdict.certificate!=null) throw new IllegalStateException("Collision-only whole-family evidence required");
            for(ContextUniversalUnitLedger.Row row:input.products) source(row.support,universe,maximumDepth,work);
            for(Receipt receipt:verdict.collision.receipts) {
                work.run(); if(!receipt.includesCompounds || !receipt.includesFactoring) throw new IllegalStateException("Factored complete collision receipts required");
                for(ContextUniversalUnitLedger.Row row:receipt.products) source(row.support,universe,maximumDepth,work);
            }
            Barrier replay=ContextUniversalUnitRounds.mergeBound(input,verdict.collision.receipts,maximumDepth,work);
            if(replay.status!=ContextUniversalUnitRounds.Status.COLLISION || replay.oppositions.size()!=verdict.collision.oppositions.size())
                throw new IllegalStateException("Exact complete collision barrier replay failed: "+replay.reason);
            List<Proof> result=new ArrayList<>(); List<Proof> unmatched=new ArrayList<>(replay.oppositions);
            for(Proof proof:verdict.collision.oppositions) {
                work.run(); if(!proof.contradiction || proof.conclusion!=null || !proof.dependsOnX
                    || proof.left.literals.size()!=1 && proof.right.literals.size()!=1)
                    throw new IllegalStateException("Actual X-dependent unit opposition required");
                source(proof.leftSupport,universe,maximumDepth,work); source(proof.rightSupport,universe,maximumDepth,work); source(proof.support,universe,maximumDepth,work);
                Set<Source> union=new HashSet<>(proof.leftSupport.sources); union.addAll(proof.rightSupport.sources);
                int parentDepth=Math.max(proof.leftSupport.depth,proof.rightSupport.depth);
                if(!union.equals(proof.support.sources) || parentDepth==Integer.MAX_VALUE || parentDepth+1!=proof.support.depth
                    || proof.left.resolveWithUnits(proof.right,proof.leftPivot,proof.rightPivot,work)!=null)
                    throw new IllegalStateException("Opposition kernel/support replay mismatch");
                int match=-1; for(int i=0;i<unmatched.size();i++) if(same(proof,unmatched.get(i),work)) { match=i; break; }
                if(match<0) throw new IllegalStateException("Opposition absent from complete barrier replay"); unmatched.remove(match);
                result.add(proof);
            }
            if(result.isEmpty() || !unmatched.isEmpty()) throw new IllegalStateException("Incomplete whole-family opposition inventory");
            return new Audit(true,"Exact whole family collision independently rechecked; no publication authority",verdict,result);
    }
}
