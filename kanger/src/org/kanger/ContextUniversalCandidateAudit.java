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
            if(verdict.status!=evaluation.status) throw new IllegalStateException("Candidate verdict status mismatch");
            ContextUniversalFamilyAudit.Audit checked=ContextUniversalFamilyAudit.auditBound(verdict,a.staged,requested,maximumDepth,maximumInstances,work);
            List<Attribution> result=new ArrayList<>();
            for(Proof proof:checked.oppositions) {
                Set<Source> translated=new LinkedHashSet<>();
                for(Source source:proof.support.sources) {work.run();translated.add(originals.get(source));}
                result.add(new Attribution(proof,proof.support.sources.contains(a.candidate),translated));
            }
            return new Audit(true,"Exact candidate staging and whole-family verdict independently rechecked; no publication authority",evaluation,result);
        } catch(Exception failure) { return new Audit(false,failure.getMessage(),evaluation,Collections.emptyList()); }
    }
}
