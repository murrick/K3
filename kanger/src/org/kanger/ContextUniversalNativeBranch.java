/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextUniversalFrozenConstraints.Proof;
import org.kanger.ContextWitnessResultTransport.*;

/** Explicit detached owning pipeline. Stable SUPPLIED scope is never full-family/write eligibility. */
final class ContextUniversalNativeBranch {
    enum Status { STABLE_SUPPLIED, REFUTED, INCOMPLETE }
    enum RejectionPhase { INITIAL, GROUPED_ROUND }
    static final class Policy {
        final ContextUniversalFrozenBranch.Limits kernel;
        final ContextUniversalGroupedLocalRounds.Limits local;
        final int rounds,work,capacity;
        Policy(ContextUniversalFrozenBranch.Limits kernel,ContextUniversalGroupedLocalRounds.Limits local,int rounds,int work,int capacity){
            if(kernel==null||local==null||rounds<1||rounds>64||work<1||capacity<1||capacity>4096)throw new IllegalArgumentException("Actual bounded native branch policy required");
            if(local.kernelRounds!=kernel.rounds||local.depth!=kernel.proofDepth||local.clauses!=kernel.clauses||local.alternatives!=kernel.alternatives)throw new IllegalArgumentException("Different local/kernel branch policy");
            this.kernel=kernel;this.local=local;this.rounds=rounds;this.work=work;this.capacity=capacity;
        }
    }
    static final class Rejection {
        final Cut cut;final RejectionPhase phase;final int round;final boolean xRefuted,foreignRefuted;final List<Proof> proofs;
        private Rejection(Cut cut,RejectionPhase phase,int round,boolean xRefuted,boolean foreignRefuted,List<Proof> proofs){
            require(cut!=null&&(xRefuted||foreignRefuted)&&proofs!=null&&!proofs.isEmpty(),"Actual sourced branch rejection required");
            for(Proof p:proofs)require(p!=null&&p.clause==null,"Only detached empty proof roots may escape rejection");
            this.cut=cut;this.phase=phase;this.round=round;this.xRefuted=xRefuted;this.foreignRefuted=foreignRefuted;this.proofs=Collections.unmodifiableList(new ArrayList<>(proofs));
        }
    }
    static final class Receipt {
        final Snapshot x;final List<Snapshot> foreign;final Policy policy;
        final ContextUniversalInitialEntryAudit.Receipt initial;
        final ContextUniversalGroupedChainAudit.Receipt chain;
        private Receipt(Snapshot x,List<Snapshot> foreign,Policy policy,ContextUniversalInitialEntryAudit.Receipt initial,ContextUniversalGroupedChainAudit.Receipt chain){this.x=x;this.foreign=Collections.unmodifiableList(new ArrayList<>(foreign));this.policy=policy;this.initial=initial;this.chain=chain;}
    }
    static final class Result {
        final Status status;final String phase,reason;final Receipt output;final Map<Cut,Rejection> refutations;
        private Result(Status status,String phase,String reason,Receipt output,Map<Cut,Rejection> refutations){this.status=status;this.phase=phase;this.reason=reason;this.output=output;this.refutations=Collections.unmodifiableMap(new LinkedHashMap<>(refutations));}
    }
    private static void require(boolean b,String why){if(!b)throw new IllegalStateException(why);}
    /** Each existing phase owns its ORIGINAL budget/session. No aggregate pipeline work bound is claimed. */
    static Result run(Snapshot x,List<Snapshot> foreign,Policy policy){
        String phase="preflight";
        try {
            require(x!=null&&foreign!=null&&foreign.size()<=15&&policy!=null,"Actual bounded supplied native branch required");
            new Policy(policy.kernel,policy.local,policy.rounds,policy.work,policy.capacity);List<Snapshot> cuts=new ArrayList<>(foreign);
            phase="initial-assembly";ContextUniversalInitialEntry.Result assembled=ContextUniversalInitialEntry.assemble(x,cuts,policy.kernel,policy.capacity);
            require(assembled.status!=ContextUniversalInitialEntry.Status.INCOMPLETE,assembled.phase+": "+assembled.reason);
            if(assembled.status==ContextUniversalInitialEntry.Status.REFUTED){Map<Cut,Rejection> evidence=new LinkedHashMap<>();for(Map.Entry<Cut,ContextUniversalInitialEntry.Rejection> e:assembled.refutations.entrySet()){ContextUniversalInitialEntry.Rejection r=e.getValue();evidence.put(e.getKey(),new Rejection(e.getKey(),RejectionPhase.INITIAL,0,r.xRefuted,r.foreignRefuted,r.proofs));}require(!evidence.isEmpty(),"Complete initial rejection inventory required");return new Result(Status.REFUTED,phase,"All supplied initial cuts checked; no initial/stable prefix",null,evidence);}
            ContextUniversalInitialEntry.Entry entry=assembled.entry;
            phase="initial-audit";ContextUniversalInitialEntryAudit.SessionInspection initial=ContextUniversalInitialEntryAudit.inspectWithValidationReuse(entry,policy.kernel,policy.work,policy.capacity);require(initial.inspection.audited,initial.inspection.phase+": "+initial.inspection.reason);require(initial.metrics.entries==0,"Initial audit scope must close");
            phase="grouped-controller";ContextUniversalGroupedRounds.SessionRun grouped=ContextUniversalGroupedRounds.runInitialWithValidationReuse(initial.inspection.output,policy.kernel,policy.local,policy.rounds,policy.work,policy.capacity);require(grouped.metrics.entries==0,"Controller scope must close");
            ContextUniversalGroupedRounds.Result trace=grouped.result;require(trace.status!=ContextUniversalGroupedRounds.Status.INCOMPLETE,trace.phase+": "+trace.reason);
            if(trace.status==ContextUniversalGroupedRounds.Status.REFUTED){Map<Cut,Rejection> evidence=new LinkedHashMap<>();for(ContextUniversalGroupedRounds.Refutation r:trace.refutations.values())evidence.put(r.cut,new Rejection(r.cut,RejectionPhase.GROUPED_ROUND,r.round,r.xRefuted,r.foreignRefuted,r.proofs));require(!evidence.isEmpty(),"Complete grouped rejection inventory required");return new Result(Status.REFUTED,phase,"All supplied cuts certified before sourced rejection; no stable prefix",null,evidence);}
            phase="chain-audit";ContextUniversalGroupedChainAudit.SessionInspection audit=ContextUniversalGroupedChainAudit.inspectWithValidationReuse(x,cuts,entry.common,entry.pools,entry.units,entry.compounds,policy.kernel,policy.local,policy.rounds,trace,policy.work,policy.capacity);require(audit.inspection.audited,audit.inspection.phase+": "+audit.inspection.reason);require(audit.metrics.entries==0,"Chain audit scope must close");
            return new Result(Status.STABLE_SUPPLIED,"stable-supplied","Initial native replay and complete supplied-cut Source/grade chain audited; full-family/admission eligibility remains open",new Receipt(x,cuts,policy,initial.inspection.output,audit.inspection.output),Collections.emptyMap());
        }catch(Exception failure){return new Result(Status.INCOMPLETE,phase,failure.toString(),null,Collections.emptyMap());}
    }
    /** Fresh independently owned initial AND chain receipt replay, bound to actual caller expectations. */
    static void check(Receipt receipt,Snapshot expectedX,List<Snapshot> expectedForeign,Policy expectedPolicy,int maximumWork,int capacity){
        require(receipt!=null&&expectedForeign!=null&&expectedPolicy!=null&&receipt.x==expectedX&&receipt.foreign.equals(expectedForeign)&&receipt.policy==expectedPolicy,"Different actual native branch request/policy");
        ContextUniversalInitialEntryAudit.Receipt initial=receipt.initial;ContextUniversalGroupedChainAudit.Receipt chain=receipt.chain;
        require(initial!=null&&chain!=null&&initial.limits==expectedPolicy.kernel&&initial.entry!=null,"Actual bound initial/chain receipts required");ContextUniversalInitialEntry.Entry e=initial.entry;
        require(e.x==expectedX&&e.foreign.equals(receipt.foreign)&&chain.x==expectedX&&chain.foreign.equals(receipt.foreign)&&chain.input==e.common&&chain.pools.equals(e.pools)&&chain.units==e.units&&chain.compounds==e.compounds,"Different actual initial-to-chain native handoff");
        require(chain.limits==expectedPolicy.kernel&&chain.local==expectedPolicy.local&&chain.maximumRounds==expectedPolicy.rounds,"Different actual owning chain policy");
        ContextUniversalInitialEntryAudit.checkWithValidationReuse(initial,maximumWork,capacity);
        ContextUniversalGroupedChainAudit.checkWithValidationReuse(chain,maximumWork,capacity);
    }
}
