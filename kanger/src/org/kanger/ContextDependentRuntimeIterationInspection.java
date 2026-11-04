/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextDependentCutInspection.*;
import org.kanger.ContextDependentRuntimeContinuationInspection.AutomaticPlan;
import org.kanger.ContextDependentRuntimeContinuationInspection.Projection;
import org.kanger.ContextDependentRuntimeBarrierInspection.Opposition;
import org.kanger.ContextDependentRoundInspection.Round;
import org.kanger.ContextDependentRoundInspection.Barrier;
import org.kanger.ContextDependentRoundInspection.Ticket;
import org.kanger.ContextDependentRuntimeInspection.Frame;
import org.kanger.ContextDependentModelInspection.Model;

/** Repeated frozen runtime discovery with receiving qualification before proof-frontier stability. */
public final class ContextDependentRuntimeIterationInspection {
    private ContextDependentRuntimeIterationInspection(){}
    public enum Status {BASE_REFUTED,CANDIDATE_REFUTED,NO_MATCH,ROUND_REFUTED,STABLE_PENDING,INCOMPLETE}
    public static final class Request {
        public final ContextDependentRuntimeBarrierInspection.Request first;
        public final Projection projection;public final List<AutomaticPlan> rounds;
        public final int frontierWork,frontierCapacity;
        public Request(ContextDependentRuntimeBarrierInspection.Request first,Projection projection,List<AutomaticPlan> rounds,int frontierWork,int frontierCapacity){
            if(first==null||projection==null||rounds==null||rounds.isEmpty()||rounds.contains(null)||rounds.size()>first.execution.preparation.projection.qualification.append.iteration.policy.steps||frontierWork<1||frontierCapacity<1||frontierCapacity>100000)throw new IllegalArgumentException("Fresh first barrier, bounded whole round schedule and frontier bounds required");
            this.first=first;this.projection=projection;this.rounds=Collections.unmodifiableList(new ArrayList<>(rounds));this.frontierWork=frontierWork;this.frontierCapacity=frontierCapacity;
        }
    }
    public static final class Cycle {
        public final Frame input,received;public final ContextDependentRuntimeDiscovery.Result discovery;
        public final ContextDependentRoundInspection.Result checked;public final List<Round> history;
        public final Map<String,List<Ticket>> runtimeTickets;public final List<Ticket> outputTickets;
        public final int originalRows,finalObligations;public final ContextDependentFrontierInspection.Comparison comparison;
        private Cycle(Frame input,Frame received,ContextDependentRuntimeDiscovery.Result discovery,ContextDependentRuntimeBarrierInspection.Replay replay,int originals,int finals,ContextDependentFrontierInspection.Comparison comparison){this.input=input;this.received=received;this.discovery=discovery;checked=replay.checked;history=replay.history;runtimeTickets=replay.runtime;outputTickets=replay.outputs;originalRows=originals;finalObligations=finals;this.comparison=comparison;}
    }
    public static final class Result {
        public final Status status;public final String phase,reason;public final UUID epoch;
        public final ContextDependentRuntimeBarrierInspection.Result first;public final List<Cycle> cycles;
        public final Frame frame;public final ContextDependentFrontierInspection.Snapshot frontier;
        public final ContextDependentRoundInspection.Result checked;public final List<Round> history;
        public final List<Opposition> oppositions;public final int completedRounds;
        private Result(Status status,String phase,String reason,ContextDependentRuntimeBarrierInspection.Result first,List<Cycle> cycles,ContextDependentFrontierInspection.Snapshot frontier,List<Opposition> oppositions){
            this.status=status;this.phase=phase;this.reason=reason;this.first=first;this.cycles=Collections.unmodifiableList(new ArrayList<>(cycles));this.frontier=frontier;this.oppositions=Collections.unmodifiableList(new ArrayList<>(oppositions));completedRounds=cycles.size();epoch=status==Status.STABLE_PENDING?UUID.randomUUID():null;
            Cycle last=cycles.isEmpty()?null:cycles.get(cycles.size()-1);frame=last==null?null:last.received;checked=last==null?null:last.checked;history=last==null?Collections.emptyList():last.history;
        }
        private static Result incomplete(String phase,String why){return new Result(Status.INCOMPLETE,phase,why,null,Collections.emptyList(),null,Collections.emptyList());}
        private static Result refused(String phase,List<Opposition> oppositions){return new Result(Status.ROUND_REFUTED,phase,"Actual sourced receiving-family refutation before stability",null,Collections.emptyList(),null,oppositions);}
    }
    private static void need(boolean b,String why){if(!b)throw new IllegalStateException(why);}
    private static void interrupt()throws InterruptedException{ContextDependentRuntimeExecution.interrupt();}
    private static Barrier last(ContextDependentRoundInspection.Result checked){return checked.barriers.get(checked.barriers.size()-1);}
    private static void current(Member base,Member child,List<Cut> cuts,Request r)throws Exception{ContextDependentReplayInspection.Policy p=r.first.execution.preparation.projection.qualification.append.iteration.policy;ContextDependentReplayInspection.current(base.actual,base.inventory,p);ContextDependentReplayInspection.current(child.actual,child.inventory,p);for(Cut cut:cuts)for(Member m:cut.members)ContextDependentReplayInspection.currentMember(m.actual,m.inventory,p);}
    private static Frame received(Member child,List<Cut> cuts,Request r,ContextDependentCandidateInspection.Result candidate,ContextDependentRoundInspection.Result checked,ContextDependentRuntimeBarrierInspection.Request models,int expected)throws Exception{
        Projection p=r.projection;ContextDependentRuntimeInspection.Request bounds=new ContextDependentRuntimeInspection.Request(r.first.execution.preparation.projection.qualification,p.products,p.nodes,p.copyWork,p.auditWork);Frame frame=ContextDependentRuntimeInspection.project(child,checked,bounds);Runnable shape=ContextGroundProductionRounds.budget(p.shapeWork);
        Map<String,Model> rebased=ContextDependentRuntimeBarrierInspection.models(models,candidate,shape);ContextDependentRuntimeModelAudit audit=new ContextDependentRuntimeModelAudit(child,cuts,frame,rebased,new ContextDependentRuntimeModelInspection.Request(bounds,p.shapeWork,p.truthWork),shape);int count=audit.truth();need(count==expected,"Every actual received common/private occurrence must survive indexed projection/truth");return frame;
    }
    private static int obligations(Barrier barrier){int count=0;for(ContextDependentProductionInspection.Result cut:barrier.cuts.values())count=Math.addExact(count,Math.addExact(barrier.common.size(),cut.products.size()));return count;}
    /** Internal kernel: public entry obtains all inputs afresh; no caller-supplied receipt API. */
    static Result run(Member child,List<Cut> cuts,Request r,ContextDependentRuntimeBarrierInspection.Result first,ContextDependentCandidateInspection.Result candidate,ContextDependentRoundInspection.Result initial,List<Round> prefix)throws Exception{
        String phase="initial-receiving";
        try{
            ContextDependentCandidateModelInspection.Request q=r.first.execution.preparation.projection.qualification;ContextDependentIterationInspection.Request policy=q.append.iteration;
            List<Opposition> refusals=ContextDependentRuntimeBarrierInspection.oppositions(last(initial),candidate,ContextGroundProductionRounds.budget(r.first.bridgeWork));if(!refusals.isEmpty())return Result.refused(phase,refusals);
            need(first==null||first.receivingFinalObligations==obligations(last(initial)),"First receiving count must match every actual raw occurrence");
            phase="initial-runtime";Frame frame=received(child,cuts,r,candidate,initial,r.first,obligations(last(initial)));
            phase="initial-frontier";ContextDependentFrontierInspection.Snapshot before=ContextDependentFrontierInspection.inspect(last(initial),r.frontierWork,r.frontierCapacity);
            ContextDependentRoundInspection.Result checked=initial;List<Round> history=prefix;List<Cycle> cycles=new ArrayList<>();
            for(AutomaticPlan plan:r.rounds){
                interrupt();phase="round-discovery";ContextDependentRuntimeExecution.Request bounds=new ContextDependentRuntimeExecution.Request(r.first.execution.preparation,Collections.emptyList(),plan.work,plan.auditWork,plan.nodes,true);ContextDependentRuntimeDiscovery.Result discovery=ContextDependentRuntimeDiscovery.discover(child,cuts,frame,bounds,plan.discovery);
                ContextDependentRuntimeBarrierInspection.Request next=new ContextDependentRuntimeBarrierInspection.Request(new ContextDependentRuntimeExecution.Request(bounds.preparation,discovery.steps,plan.work,plan.auditWork,plan.nodes,true),plan.receivingOriginalModels,plan.bridgeWork,plan.originalWork,plan.finalWork);
                phase="round-bridge";ContextDependentRuntimeBarrierInspection.Replay replay=ContextDependentRuntimeBarrierInspection.replay(child,cuts,new ContextDependentRoundInspection.Request(policy.policy,policy.capacity,history),checked,frame,discovery.steps,discovery.outputs,plan.bridgeWork);Runnable work=ContextGroundProductionRounds.budget(plan.bridgeWork);
                phase="round-producing";refusals=ContextDependentRuntimeBarrierInspection.oppositions(replay.checked.barriers.get(replay.produced),candidate,work);if(refusals.isEmpty()){phase="round-receiving";refusals=ContextDependentRuntimeBarrierInspection.oppositions(replay.checked.barriers.get(replay.received),candidate,work);}if(!refusals.isEmpty())return Result.refused(phase,refusals);
                phase="round-model-rebase";Map<String,Model> models=ContextDependentRuntimeBarrierInspection.models(next,candidate,work);phase="round-model-preflight";ContextDependentModelAudit nativeAudit=new ContextDependentModelAudit(child,cuts,replay.checked,new ContextDependentModelInspection.Request(policy,models,q.points,q.cells,plan.originalWork,plan.finalWork));phase="round-original";int originals=nativeAudit.original();phase="round-final";int finals=nativeAudit.finalModel();interrupt();
                phase="round-runtime";Frame after=received(child,cuts,r,candidate,replay.checked,next,finals);
                phase="round-frontier";ContextDependentFrontierInspection.Snapshot frontier=ContextDependentFrontierInspection.inspect(last(replay.checked),r.frontierWork,r.frontierCapacity);ContextDependentFrontierInspection.Comparison comparison=ContextDependentFrontierInspection.compare(before,frontier,r.frontierWork);need(comparison.removedOrWorsened==0,"Complete receiving proof frontier must not lose support or worsen minimum depth");
                cycles.add(new Cycle(frame,after,discovery,replay,originals,finals,comparison));interrupt();
                if(!comparison.changed)return new Result(Status.STABLE_PENDING,"received-frontier-stable","Unchanged supported signed typed/whole-Source/min-depth frontier AFTER complete receiving/native/indexed audits; full-language/default admission unknown",first,cycles,frontier,Collections.emptyList());
                before=frontier;frame=after;checked=replay.checked;history=replay.history;
            }
            return Result.incomplete("round-limit","Complete round schedule exhausted before an unchanged receiving proof frontier");
        }catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw interrupted;}catch(Exception failure){interrupt();return Result.incomplete(phase,failure.getMessage());}
    }
    public static Result inspect(Member base,Member child,List<Cut> supplied,Request r)throws Exception{
        if(base==null||child==null||supplied==null||supplied.contains(null)||r==null)throw new IllegalArgumentException("Actual parent/child and complete cuts required");List<Cut> cuts=new ArrayList<>(supplied);interrupt();current(base,child,cuts,r);
        try{
            ContextDependentRuntimeBarrierInspection.Result first=ContextDependentRuntimeBarrierInspection.inspect(base,child,cuts,r.first);
            if(first.status!=ContextDependentRuntimeBarrierInspection.Status.RECEIVED_PENDING){if(first.status==ContextDependentRuntimeBarrierInspection.Status.INCOMPLETE)return Result.incomplete("first-barrier",first.phase+": "+first.reason);Status status=Status.valueOf(first.status.name());return new Result(status,"first-barrier",first.reason,status==Status.BASE_REFUTED||status==Status.CANDIDATE_REFUTED?first:null,Collections.emptyList(),null,first.oppositions);}
            return run(child,cuts,r,first,first.execution.preparation.projection.qualification.candidate,first.checked,first.history);
        }finally{current(base,child,cuts,r);}
    }
}
