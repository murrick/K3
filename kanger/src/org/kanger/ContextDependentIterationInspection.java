/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextDependentCutInspection.*;
import org.kanger.ContextDependentReplayInspection.Policy;
import org.kanger.ContextDependentRoundInspection.Round;
import org.kanger.ContextDependentRoundInspection.Barrier;
import org.kanger.ContextDependentRoundInspection.Discovery;
import org.kanger.ContextDependentFrontierInspection.Snapshot;

/** Bounded automatic supported-unit diagnostic. No model, full-language closure or admission authority. */
public final class ContextDependentIterationInspection {
    private ContextDependentIterationInspection(){}
    public enum Status {COLLISION,FIXED_POINT,INCOMPLETE}
    public static final class Request {
        public final Policy policy;public final int capacity,maximumRounds;
        public Request(Policy policy,int capacity,int maximumRounds){if(policy==null||capacity<1||capacity>100000||maximumRounds<1||maximumRounds>policy.steps)throw new IllegalArgumentException("Bounded automatic iteration required");this.policy=policy;this.capacity=capacity;this.maximumRounds=maximumRounds;}
    }
    public static final class Result {
        public final Status status;public final String reason;public final int completedRounds;
        public final ContextDependentRoundInspection.Result checked;
        public final Snapshot frontier;public final List<Round> history;
        private Result(Status status,String reason,int completedRounds,ContextDependentRoundInspection.Result checked,Snapshot frontier,List<Round> history){this.status=status;this.reason=reason;this.completedRounds=completedRounds;this.checked=checked;this.frontier=frontier;this.history=Collections.unmodifiableList(new ArrayList<>(history));}
        private static Result incomplete(String reason,int completed){return new Result(Status.INCOMPLETE,reason,completed,null,null,Collections.emptyList());}
    }
    private static void uninterrupted()throws InterruptedException{if(Thread.currentThread().isInterrupted())throw new InterruptedException("Dependent iteration interrupted");}
    private static ContextDependentRoundInspection.Request prefix(Request request,List<Round> history){return new ContextDependentRoundInspection.Request(request.policy,request.capacity,history);}
    private static Barrier last(ContextDependentRoundInspection.Result checked){return checked.barriers.get(checked.barriers.size()-1);}
    private static boolean collision(Barrier barrier){for(ContextDependentProductionInspection.Result cut:barrier.cuts.values())if(!cut.originalUnitRefutations.isEmpty()||!cut.finalUnitRefutations.isEmpty())return true;return false;}
    private static void current(Member x,List<Cut> cuts,Policy p)throws Exception{ContextDependentReplayInspection.current(x.actual,x.inventory,p);for(Cut cut:cuts)for(Member member:cut.members)ContextDependentReplayInspection.currentMember(member.actual,member.inventory,p);}
    public static Result inspect(Member x,List<Cut> supplied,Request request)throws Exception {
        if(x==null||!x.inventory.localX||supplied==null||supplied.contains(null)||request==null)throw new IllegalArgumentException("Actual whole bounded family required");List<Cut> cuts=new ArrayList<>(supplied);uninterrupted();current(x,cuts,request.policy);
        try {
            int completed=0;
            try {
                Map<String,List<ContextDependentRoundInspection.Step>> empty=new LinkedHashMap<>();empty.put(ContextDependentCutInspection.local(x.inventory.context),Collections.emptyList());for(Cut cut:cuts)empty.put(cut.key,Collections.emptyList());List<Round> history=new ArrayList<>();history.add(new Round(empty));
                ContextDependentRoundInspection.Result checked=ContextDependentRoundInspection.inspect(x,cuts,prefix(request,history));Barrier baseline=last(checked);uninterrupted();
                if(collision(baseline))return new Result(Status.COLLISION,"Sourced original finite-unit cut refutation",0,checked,null,history);
                Snapshot previous=ContextDependentFrontierInspection.inspect(baseline,request.policy.work,request.capacity);
                for(int pass=0;pass<request.maximumRounds;pass++) {
                    uninterrupted();
                    Discovery next=ContextDependentRoundInspection.discoverNext(x,cuts,prefix(request,history));uninterrupted();completed++;history.add(next.next);checked=next.checked;
                    // A new receiving refutation can appear even when the next-input frontier is unchanged.
                    if(collision(last(checked)))return new Result(Status.COLLISION,"Sourced receiving-round refutation before stability",completed,checked,next.frontier,history);
                    ContextDependentFrontierInspection.Comparison comparison=ContextDependentFrontierInspection.compare(previous,next.frontier,request.policy.work);uninterrupted();
                    if(comparison.removedOrWorsened!=0)return Result.incomplete("Non-monotone representative frontier",completed);
                    if(!comparison.changed)return new Result(Status.FIXED_POINT,"Unchanged supported residual-unit frontier after complete receiving qualification; admission unknown",completed,checked,next.frontier,history);
                    previous=next.frontier;
                }
                return Result.incomplete("Automatic productive-round limit",completed);
            }catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw interrupted;}
            catch(Exception failure){String message=failure.getMessage();return Result.incomplete("Inspection refused: "+(message==null?failure.getClass().getSimpleName():message),completed);}
        }finally{current(x,cuts,request.policy);}
    }
}
