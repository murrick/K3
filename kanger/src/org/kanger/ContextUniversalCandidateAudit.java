/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionNativeBinding.Entry;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextUniversalFamilyInspection.Status;
import org.kanger.ContextUniversalUnitRounds.*;
import org.kanger.ContextUniversalUnitProof.Proof;

/** Independent exact-trial verdict applicability and source attribution. Never write authority. */
public final class ContextUniversalCandidateAudit {
    public static final class Attribution {
        public final Proof proof;
        public final boolean usesCandidate;
        /** Rebased original X Sources translated back; foreign and candidate Sources retained exactly. */
        public final Set<Source> originalSources;
        private Attribution(Proof proof,boolean usesCandidate,Set<Source> sources) {
            this.proof=proof; this.usesCandidate=usesCandidate;
            originalSources=Collections.unmodifiableSet(new LinkedHashSet<>(sources));
        }
    }
    public static final class Audit {
        public final boolean verified;
        public final String reason;
        public final ContextUniversalCandidate.Evaluation evaluation;
        public final List<Attribution> oppositions;
        private Audit(boolean verified,String reason,ContextUniversalCandidate.Evaluation evaluation,List<Attribution> oppositions) {
            this.verified=verified; this.reason=reason; this.evaluation=evaluation;
            this.oppositions=Collections.unmodifiableList(new ArrayList<>(oppositions));
        }
    }
    private static void source(Support support,Set<Source> universe,int depth,Runnable work) {
        work.run(); if(support.depth>depth) throw new IllegalStateException("Candidate evidence cumulative depth limit");
        for(Source s:support.sources) { work.run(); if(!universe.contains(s)) throw new IllegalStateException("Evidence Source outside exact staged family"); }
    }
    private static boolean same(Proof a,Proof b,Runnable work) {
        work.run(); return a.contradiction==b.contradiction && a.leftPivot==b.leftPivot && a.rightPivot==b.rightPivot
            && a.left.equivalent(b.left) && a.right.equivalent(b.right) && a.leftSupport.equals(b.leftSupport)
            && a.rightSupport.equals(b.rightSupport) && a.support.equals(b.support);
    }
    public static Audit audit(ContextUniversalCandidate.Evaluation evaluation,Snapshot expectedBase,List<Snapshot> expectedForeign,
            String expectedStatement,int maximumDepth,int maximumInstances,int maximumWork) {
        if(maximumDepth<0 || maximumInstances<1 || maximumWork<1) throw new IllegalArgumentException("Bounded candidate audit required");
        Runnable work=ContextGroundProductionRounds.budget(maximumWork);
        try {
            if(evaluation==null || evaluation.artifact==null || evaluation.qualification==null || evaluation.status==Status.INCOMPLETE)
                throw new IllegalStateException("Complete exact candidate verdict required");
            ContextProductionCandidate.Staged a=evaluation.artifact;
            if(a.base!=expectedBase || expectedStatement==null || !expectedStatement.equals(a.statement))
                throw new IllegalStateException("Requested candidate/base identity mismatch");
            ContextProductionFamilyAssembly.identity(a.base,true,work); ContextProductionFamilyAssembly.identity(a.staged,true,work);
            if(!a.base.inventory.context.equals(a.staged.inventory.context) || a.base.inventory.revision!=a.staged.inventory.revision
                || a.staged.primary.size()!=a.base.primary.size()+1 || a.rebased.size()!=a.base.primary.size())
                throw new IllegalStateException("Complete append-only staged inventory required");
            Map<Source,Entry> staged=new HashMap<>();
            for(Entry e:a.staged.primary) { work.run(); if(staged.put(e.source,e)!=null) throw new IllegalStateException("Repeated staged Source"); }
            Entry candidate=staged.get(a.candidate);
            if(candidate==null || !a.candidate.localX || !a.candidate.view.equals("inventory:"+a.staged.inventory.fingerprint))
                throw new IllegalStateException("Exact authenticated candidate occurrence required");
            Map<Source,Source> originals=new HashMap<>(); originals.put(a.candidate,a.candidate);
            for(Entry e:a.base.primary) {
                work.run(); Source rebuilt=a.rebased.get(e.source); Entry actual=staged.get(rebuilt);
                if(actual==null || originals.put(rebuilt,e.source)!=null) throw new IllegalStateException("Missing/aliased original rebasing");
                ContextProductionNativeBinding.sameEntry(e,actual,work);
            }
            List<Snapshot> requested=new ArrayList<>(expectedForeign); Set<Snapshot> expected=Collections.newSetFromMap(new IdentityHashMap<Snapshot,Boolean>());
            Set<UUID> contexts=new HashSet<>(); contexts.add(a.base.inventory.context);
            for(Snapshot cut:requested) {
                work.run(); ContextProductionFamilyAssembly.identity(cut,false,work);
                if(!expected.add(cut) || !contexts.add(cut.inventory.context)) throw new IllegalStateException("Repeated expected family context");
            }
            if(requested.size()!=a.foreign.size()) throw new IllegalStateException("Exact expected candidate family required");
            Set<Snapshot> seen=Collections.newSetFromMap(new IdentityHashMap<Snapshot,Boolean>());
            for(Snapshot cut:a.foreign) {
                work.run(); if(!expected.contains(cut) || !seen.add(cut)) throw new IllegalStateException("Foreign initialized snapshot identity mismatch");
                for(Entry e:cut.primary) { work.run(); if(originals.put(e.source,e.source)!=null) throw new IllegalStateException("Aliased family Source"); }
            }
            org.kanger.ContextUniversalFamilyInspection.Result verdict=evaluation.qualification;
            if(verdict.status!=evaluation.status || verdict.completedBarriers<1) throw new IllegalStateException("Candidate verdict status mismatch");
            Frontier input=verdict.status==Status.CONSISTENT ? verdict.certificate==null?null:verdict.certificate.input
                : verdict.collision==null?null:verdict.collision.input;
            if(input==null || input.x!=a.staged || input.cuts.size()!=a.foreign.size()+1) throw new IllegalStateException("Verdict is not for exact staged candidate");
            seen.clear(); seen.add(a.staged);
            for(Snapshot cut:input.cuts) {
                work.run(); if(cut==a.staged) continue;
                if(!expected.contains(cut) || !seen.add(cut)) throw new IllegalStateException("Verdict family identity mismatch");
            }
            if(seen.size()!=input.cuts.size()) throw new IllegalStateException("Incomplete verdict family");
            if(verdict.status==Status.CONSISTENT) {
                if(verdict.collision!=null) throw new IllegalStateException("Mixed candidate verdict evidence");
                List<Receipt> receipts=new ArrayList<>(); List<ContextUniversalQuotientModel.Model> models=new ArrayList<>();
                for(ContextUniversalFamilyModel.Cut cut:verdict.certificate.cuts) { work.run(); receipts.add(cut.receipt); models.add(cut.model); }
                ContextUniversalFamilyModel.Result check=ContextUniversalFamilyModel.certifyBound(input,receipts,models,maximumDepth,
                    new int[]{Math.min(maximumInstances,65536)},work);
                if(!check.certified) throw new IllegalStateException(check.reason);
                return new Audit(true,"Exact requested staged family consistency independently rechecked; no publication authority",evaluation,Collections.emptyList());
            }
            if(verdict.status!=Status.COLLISION || verdict.certificate!=null) throw new IllegalStateException("Collision-only candidate evidence required");
            Set<Source> universe=originals.keySet();
            for(ContextUniversalUnitLedger.Row row:input.products) source(row.support,universe,maximumDepth,work);
            for(Receipt receipt:verdict.collision.receipts) {
                work.run(); if(!receipt.includesCompounds || !receipt.includesFactoring) throw new IllegalStateException("Factored complete collision receipts required");
                for(ContextUniversalUnitLedger.Row row:receipt.products) source(row.support,universe,maximumDepth,work);
            }
            Barrier replay=ContextUniversalUnitRounds.mergeBound(input,verdict.collision.receipts,maximumDepth,work);
            if(replay.status!=ContextUniversalUnitRounds.Status.COLLISION || replay.oppositions.size()!=verdict.collision.oppositions.size())
                throw new IllegalStateException("Exact complete collision barrier replay failed: "+replay.reason);
            List<Attribution> result=new ArrayList<>(); List<Proof> unmatched=new ArrayList<>(replay.oppositions);
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
                Set<Source> translated=new LinkedHashSet<>();
                for(Source s:proof.support.sources) { work.run(); translated.add(originals.get(s)); }
                result.add(new Attribution(proof,proof.support.sources.contains(a.candidate),translated));
            }
            if(result.isEmpty() || !unmatched.isEmpty()) throw new IllegalStateException("Incomplete opposition attribution");
            return new Audit(true,"Exact complete family collision rechecked; candidate dependence is per whole proof, not any localX Source",evaluation,result);
        } catch(Exception failure) { return new Audit(false,failure.getMessage(),evaluation,Collections.emptyList()); }
    }
}
