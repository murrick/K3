/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextProductionCapture.Source;
import org.kanger.ContextProductionCapture.Support;

/** Sealed native pre-settlement refusal evidence; scoped ready is NOT full native admission. */
public final class ContextNativePendingGuard {
    private ContextNativePendingGuard(){}
    public enum Status { SCOPED_READY, REFUTED, INCOMPLETE }
    public static final class Evidence {
        private final Snapshot x;private final List<Snapshot> foreign;
        private final ContextUniversalNativeBranch.Policy policy;
        /** Retains the actual authenticated FLATTENED proof DAGs, not just scalar cause summaries. */
        private final ContextUniversalNativeBranch.Result nativeResult;
        private final List<ContextNativeBranchInspection.Opposition> oppositions;
        private Evidence(Snapshot x,List<Snapshot> foreign,ContextUniversalNativeBranch.Policy policy,ContextUniversalNativeBranch.Result result){this.x=x;this.foreign=Collections.unmodifiableList(new ArrayList<>(foreign));this.policy=policy;nativeResult=result;List<ContextNativeBranchInspection.Opposition> out=new ArrayList<>();for(ContextUniversalNativeBranch.Rejection r:result.refutations.values())for(ContextUniversalFrozenConstraints.Proof proof:r.proofs)out.add(new ContextNativeBranchInspection.Opposition(r,proof));oppositions=Collections.unmodifiableList(out);}
        public List<ContextNativeBranchInspection.Opposition> getOppositions(){return oppositions;}
        /** Fresh same-request native replay; immutable detached graph precondition, no global memo. */
        public void check(int maximumWork,int capacity){verify(x,foreign,policy,nativeResult,maximumWork,capacity);}
    }
    public static final class Result {
        public final Status status;public final String phase,reason;public final Evidence evidence;
        private Result(Status status,String phase,String reason,Evidence evidence){this.status=status;this.phase=phase;this.reason=reason;this.evidence=evidence;}
    }
    private static void require(boolean b,String why){if(!b)throw new IllegalStateException(why);}
    private static Map<Set<Source>,Integer> supports(ContextUniversalNativeBranch.Rejection rejection,Runnable work){
        Map<Set<Source>,Integer> result=new HashMap<>();for(ContextUniversalFrozenConstraints.Proof p:rejection.proofs){work.run();require(p!=null&&p.clause==null&&p.support!=null&&!p.support.sources.isEmpty(),"Actual detached empty proof required");Support support=p.support;Set<Source> key=new HashSet<>();for(Source source:support.sources){work.run();key.add(source);}require(result.put(key,support.depth)==null,"Duplicate exact-Source rejection root");}require(!result.isEmpty(),"Nonempty native rejection inventory required");return result;
    }
    private static void verify(Snapshot x,List<Snapshot> foreign,ContextUniversalNativeBranch.Policy policy,ContextUniversalNativeBranch.Result original,int maximumWork,int capacity){
        if(maximumWork<1||capacity<1||capacity>4096)throw new IllegalArgumentException("Bounded independent native refusal replay required");Runnable work=ContextGroundProductionRounds.budget(maximumWork);work.run();
        require(x!=null&&foreign!=null&&policy!=null&&original!=null&&original.status==ContextUniversalNativeBranch.Status.REFUTED&&original.output==null&&!original.refutations.isEmpty(),"Actual native sourced refusal required");
        Map<ContextWitnessResultTransport.Cut,Map<Set<Source>,Integer>> roots=new LinkedHashMap<>();for(Map.Entry<ContextWitnessResultTransport.Cut,ContextUniversalNativeBranch.Rejection> e:original.refutations.entrySet()){work.run();require(e.getKey().equals(e.getValue().cut),"Different native rejection cut");roots.put(e.getKey(),supports(e.getValue(),work));}
        // The original operation's budgets remain intact; this is a NEW invocation, never its memo.
        ContextUniversalNativeBranch.Policy replayPolicy=new ContextUniversalNativeBranch.Policy(policy.kernel,policy.local,policy.rounds,policy.work,capacity);
        ContextUniversalNativeBranch.Result replay=ContextUniversalNativeBranch.run(x,foreign,replayPolicy);
        require(replay.status==ContextUniversalNativeBranch.Status.REFUTED&&replay.output==null&&replay.refutations.keySet().equals(original.refutations.keySet()),"Independent native refusal replay incomplete/different");
        for(Map.Entry<ContextWitnessResultTransport.Cut,ContextUniversalNativeBranch.Rejection> e:replay.refutations.entrySet()){work.run();ContextUniversalNativeBranch.Rejection a=original.refutations.get(e.getKey()),b=e.getValue();require(a.phase==b.phase&&a.round==b.round&&a.xRefuted==b.xRefuted&&a.foreignRefuted==b.foreignRefuted&&roots.get(e.getKey()).equals(supports(b,work)),"Different full native refusal phase/flags/exact Source minima");}
    }
    /** An owning provider must check currentness around this call and still run its full family gate. */
    public static Result inspect(Snapshot x,List<Snapshot> foreign,ContextNativeCandidateInspection.Limits limits){
        String phase="native-branch";
        try {
            require(x!=null&&foreign!=null&&limits!=null,"Actual native pending request required");List<Snapshot> cuts=new ArrayList<>(foreign);ContextUniversalNativeBranch.Policy policy=limits.branchPolicy();ContextUniversalNativeBranch.Result result=ContextUniversalNativeBranch.run(x,cuts,policy);
            if(result.status==ContextUniversalNativeBranch.Status.INCOMPLETE)return new Result(Status.INCOMPLETE,phase,result.phase+": "+result.reason,null);
            if(result.status==ContextUniversalNativeBranch.Status.REFUTED){phase="refutation-audit";verify(x,cuts,policy,result,limits.audit,limits.capacity);return new Result(Status.REFUTED,phase,"Native refusal independently replayed; no write permission",new Evidence(x,cuts,policy,result));}
            phase="stable-audit";ContextUniversalNativeBranch.check(result.output,x,cuts,policy,limits.audit,limits.capacity);
            return new Result(Status.SCOPED_READY,"scoped-ready","Native supplied scope checked; mandatory full-family eligibility remains",null);
        }catch(Exception failure){return new Result(Status.INCOMPLETE,phase,failure.toString(),null);}
    }
}
