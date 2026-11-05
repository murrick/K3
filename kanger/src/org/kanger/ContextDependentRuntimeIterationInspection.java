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
        public final ContextDependentRuntimeModelInspection.Request preparation;
        public final boolean automatic;
        public final ContextDependentModelSearchInspection.Request receivingSearch;
        private final ContextDependentRuntimeBarrierInspection.Request initial;
        public final Projection projection;public final List<AutomaticPlan> rounds;
        public final int frontierWork,frontierCapacity;
        public Request(ContextDependentRuntimeBarrierInspection.Request first,Projection projection,List<AutomaticPlan> rounds,int frontierWork,int frontierCapacity){
            this(first,first==null?null:first.execution.preparation,projection,rounds,first==null?0:first.bridgeWork,frontierWork,frontierCapacity,false,null);
        }
        private Request(ContextDependentRuntimeBarrierInspection.Request first,ContextDependentRuntimeModelInspection.Request preparation,Projection projection,List<AutomaticPlan> rounds,int initialWork,int frontierWork,int frontierCapacity,boolean automatic,ContextDependentModelSearchInspection.Request receivingSearch){
            if(preparation==null||(!automatic&&first==null)||projection==null||rounds==null||rounds.isEmpty()||rounds.contains(null)||rounds.size()>preparation.projection.qualification.append.iteration.policy.steps||initialWork<1||frontierWork<1||frontierCapacity<1||frontierCapacity>100000)throw new IllegalArgumentException("Fresh preparation, bounded whole round schedule and frontier bounds required");
            this.receivingSearch=receivingSearch==null?null:new ContextDependentModelSearchInspection.Request(preparation.projection.qualification.append.iteration,receivingSearch.extraPoints,receivingSearch.otherRepresentative,receivingSearch.limits,projection);
            if(this.receivingSearch!=null){if(!automatic)throw new IllegalArgumentException("Constructed receiving requires automatic startup");for(AutomaticPlan round:rounds)if(!round.receivingOriginalModels.isEmpty())throw new IllegalArgumentException("Constructed receiving accepts bounds only, not supplied model maps");}
            this.first=first;this.preparation=preparation;this.automatic=automatic;this.projection=projection;this.rounds=Collections.unmodifiableList(new ArrayList<>(rounds));this.frontierWork=frontierWork;this.frontierCapacity=frontierCapacity;
            if(automatic){AutomaticPlan bounds=this.rounds.get(0);ContextDependentCandidateModelInspection.Request q=preparation.projection.qualification;initial=new ContextDependentRuntimeBarrierInspection.Request(new ContextDependentRuntimeExecution.Request(preparation,Collections.emptyList(),bounds.work,bounds.auditWork,bounds.nodes,true),q.proposedOriginalModels,initialWork,q.proposedOriginalWork,q.proposedFinalWork);}else initial=first;
        }
        /** No caller plan, donor tuple, cached frame or qualification receipt. */
        public static Request automatic(ContextDependentRuntimeModelInspection.Request preparation,Projection projection,List<AutomaticPlan> rounds,int initialWork,int frontierWork,int frontierCapacity){return new Request(null,preparation,projection,rounds,initialWork,frontierWork,frontierCapacity,true,null);}
    }
    /** Fresh construction on every internally replayed receiving state, including an empty plan. */
    public static Request constructedReceiving(ContextDependentRuntimeModelInspection.Request preparation,Projection projection,List<AutomaticPlan> rounds,int initialWork,int frontierWork,int frontierCapacity,ContextDependentModelSearchInspection.Request search){if(search==null)throw new IllegalArgumentException("Receiving model construction required");return new Request(null,preparation,projection,rounds,initialWork,frontierWork,frontierCapacity,true,search);}
    public static final class Cycle {
        public final Frame input,received;public final ContextDependentRuntimeDiscovery.Result discovery;
        public final ContextDependentRoundInspection.Result checked;public final List<Round> history;
        public final Map<String,List<Ticket>> runtimeTickets;public final List<Ticket> outputTickets;
        public final Map<String,Model> constructedModels;public final int modelAttempts;
        public final int originalRows,finalObligations;public final ContextDependentFrontierInspection.Comparison comparison;
        private Cycle(Frame input,Frame received,ContextDependentRuntimeDiscovery.Result discovery,ContextDependentRuntimeBarrierInspection.Replay replay,int originals,int finals,ContextDependentFrontierInspection.Comparison comparison,ContextDependentModelSearchInspection.Result construction){constructedModels=construction==null?Collections.emptyMap():construction.models;modelAttempts=construction==null?0:construction.tried;this.input=input;this.received=received;this.discovery=discovery;checked=replay.checked;history=replay.history;runtimeTickets=replay.runtime;outputTickets=replay.outputs;originalRows=originals;finalObligations=finals;this.comparison=comparison;}
    }
    public static final class Result {
        public final Status status;public final String phase,reason;public final UUID epoch;
        public final ContextDependentRuntimeBarrierInspection.Result first;public final ContextDependentRuntimeModelInspection.Result startup;public final List<Cycle> cycles;
        public final Frame frame;public final ContextDependentFrontierInspection.Snapshot frontier;
        public final ContextDependentRoundInspection.Result checked;public final List<Round> history;
        public final List<Opposition> oppositions;public final int completedRounds;
        private Result(Status status,String phase,String reason,ContextDependentRuntimeBarrierInspection.Result first,List<Cycle> cycles,ContextDependentFrontierInspection.Snapshot frontier,List<Opposition> oppositions){this(status,phase,reason,first,cycles,frontier,oppositions,null);}
        private Result(Status status,String phase,String reason,ContextDependentRuntimeBarrierInspection.Result first,List<Cycle> cycles,ContextDependentFrontierInspection.Snapshot frontier,List<Opposition> oppositions,ContextDependentRuntimeModelInspection.Result startup){
            this.startup=startup;this.status=status;this.phase=phase;this.reason=reason;this.first=first;this.cycles=Collections.unmodifiableList(new ArrayList<>(cycles));this.frontier=frontier;this.oppositions=Collections.unmodifiableList(new ArrayList<>(oppositions));completedRounds=cycles.size();epoch=status==Status.STABLE_PENDING?UUID.randomUUID():null;
            Cycle last=cycles.isEmpty()?null:cycles.get(cycles.size()-1);frame=last==null?null:last.received;checked=last==null?null:last.checked;history=last==null?Collections.emptyList():last.history;
        }
        private static Result incomplete(String phase,String why){return new Result(Status.INCOMPLETE,phase,why,null,Collections.emptyList(),null,Collections.emptyList());}
        private static Result refused(String phase,List<Opposition> oppositions){return new Result(Status.ROUND_REFUTED,phase,"Actual sourced receiving-family refutation before stability",null,Collections.emptyList(),null,oppositions);}
    }
    private static void need(boolean b,String why){if(!b)throw new IllegalStateException(why);}
    private static void interrupt()throws InterruptedException{ContextDependentRuntimeExecution.interrupt();}
    private static Barrier last(ContextDependentRoundInspection.Result checked){return checked.barriers.get(checked.barriers.size()-1);}
    private static void current(Member base,Member child,List<Cut> cuts,Request r)throws Exception{ContextDependentReplayInspection.Policy p=r.preparation.projection.qualification.append.iteration.policy;ContextDependentReplayInspection.current(base.actual,base.inventory,p);ContextDependentReplayInspection.current(child.actual,child.inventory,p);for(Cut cut:cuts)for(Member m:cut.members)ContextDependentReplayInspection.currentMember(m.actual,m.inventory,p);}
    private static Frame received(Member child,List<Cut> cuts,Request r,ContextDependentCandidateInspection.Result candidate,ContextDependentRoundInspection.Result checked,ContextDependentRuntimeBarrierInspection.Request models,int expected)throws Exception{
        Projection p=r.projection;ContextDependentRuntimeInspection.Request bounds=new ContextDependentRuntimeInspection.Request(r.preparation.projection.qualification,p.products,p.nodes,p.copyWork,p.auditWork);Frame frame=ContextDependentRuntimeInspection.project(child,checked,bounds);Runnable shape=ContextGroundProductionRounds.budget(p.shapeWork);
        Map<String,Model> rebased=ContextDependentRuntimeBarrierInspection.models(models,candidate,shape);ContextDependentRuntimeModelAudit audit=new ContextDependentRuntimeModelAudit(child,cuts,frame,rebased,new ContextDependentRuntimeModelInspection.Request(bounds,p.shapeWork,p.truthWork),shape);int count=audit.truth();need(count==expected,"Every actual received common/private occurrence must survive indexed projection/truth");return frame;
    }
    private static int obligations(Barrier barrier){int count=0;for(ContextDependentProductionInspection.Result cut:barrier.cuts.values())count=Math.addExact(count,Math.addExact(barrier.common.size(),cut.products.size()));return count;}
    /** Internal kernel: public entry obtains all inputs afresh; no caller-supplied receipt API. */
    static Result run(Member child,List<Cut> cuts,Request r,ContextDependentRuntimeBarrierInspection.Result first,ContextDependentCandidateInspection.Result candidate,ContextDependentRoundInspection.Result initial,List<Round> prefix)throws Exception{
        return run(child,cuts,r,first,candidate,initial,prefix,null,()->{},barriers->{},()->{});
    }
    private static Result run(Member child,List<Cut> cuts,Request r,ContextDependentRuntimeBarrierInspection.Result first,ContextDependentCandidateInspection.Result candidate,ContextDependentRoundInspection.Result initial,List<Round> prefix,ContextDependentRuntimeModelInspection.Result startup,Runnable chargeCandidate,java.util.function.IntConsumer chargeReplay,Runnable chargeReplayWork)throws Exception{
        String phase="initial-receiving";
        try{
            ContextDependentCandidateModelInspection.Request q=r.preparation.projection.qualification;ContextDependentIterationInspection.Request policy=q.append.iteration;
            List<Opposition> refusals=ContextDependentRuntimeBarrierInspection.oppositions(last(initial),candidate,ContextGroundProductionRounds.budget(r.initial.bridgeWork));if(!refusals.isEmpty())return Result.refused(phase,refusals);
            need(first==null||first.receivingFinalObligations==obligations(last(initial)),"First receiving count must match every actual raw occurrence");
            need(startup==null||startup.runtimeObligations==obligations(last(initial)),"Automatic startup count must cover every actual raw occurrence");
            phase="initial-runtime";Frame frame=received(child,cuts,r,candidate,initial,r.initial,obligations(last(initial)));
            phase="initial-frontier";ContextDependentFrontierInspection.Snapshot before=ContextDependentFrontierInspection.inspect(last(initial),r.frontierWork,r.frontierCapacity);
            ContextDependentRoundInspection.Result checked=initial;List<Round> history=prefix;List<Cycle> cycles=new ArrayList<>();
            for(AutomaticPlan plan:r.rounds){
                interrupt();phase="round-discovery";ContextDependentRuntimeExecution.Request bounds=new ContextDependentRuntimeExecution.Request(r.preparation,Collections.emptyList(),plan.work,plan.auditWork,plan.nodes,true);ContextDependentRuntimeDiscovery.Result discovery=ContextDependentRuntimeDiscovery.discover(child,cuts,frame,bounds,plan.discovery);
                ContextDependentRuntimeBarrierInspection.Request next=new ContextDependentRuntimeBarrierInspection.Request(new ContextDependentRuntimeExecution.Request(bounds.preparation,discovery.steps,plan.work,plan.auditWork,plan.nodes,true),plan.receivingOriginalModels,plan.bridgeWork,plan.originalWork,plan.finalWork);
                phase="round-bridge";
                chargeReplay.accept(Math.addExact(history.size(), 2));
                ContextDependentRuntimeBarrierInspection.Replay replay=ContextDependentRuntimeBarrierInspection.replay(child,cuts,new ContextDependentRoundInspection.Request(policy.policy,policy.capacity,history),checked,frame,discovery.steps,discovery.outputs,plan.bridgeWork,chargeReplayWork);Runnable work=ContextGroundProductionRounds.budget(plan.bridgeWork);
                phase="round-producing";refusals=ContextDependentRuntimeBarrierInspection.oppositions(replay.checked.barriers.get(replay.produced),candidate,work);if(refusals.isEmpty()){phase="round-receiving";refusals=ContextDependentRuntimeBarrierInspection.oppositions(replay.checked.barriers.get(replay.received),candidate,work);}if(!refusals.isEmpty())return Result.refused(phase,refusals);
                ContextDependentModelSearchInspection.Result construction=null;Map<String,Model> models;int points=q.points,cells=q.cells;
                if(r.receivingSearch==null){phase="round-model-rebase";models=ContextDependentRuntimeBarrierInspection.models(next,candidate,work);}else{
                    phase="round-model-construction";construction=ContextDependentModelSearchInspection.received(child,cuts,replay.checked,r.receivingSearch,chargeCandidate);
                    if(construction.status!=ContextDependentModelSearchInspection.Status.MODELLED_PENDING)return Result.incomplete(phase,construction.phase+": "+construction.reason);
                    models=construction.models;points=r.receivingSearch.limits.points;cells=r.receivingSearch.limits.cells;
                }
                phase="round-model-preflight";ContextDependentModelAudit nativeAudit=new ContextDependentModelAudit(child,cuts,replay.checked,new ContextDependentModelInspection.Request(policy,models,points,cells,plan.originalWork,plan.finalWork));phase="round-original";int originals=nativeAudit.original();phase="round-final";int finals=nativeAudit.finalModel();interrupt();
                if(construction!=null)need(construction.originalRows==originals&&construction.finalObligations==finals&&construction.runtimeObligations==finals,"Constructed and independent final receiving counts must agree");
                phase="round-runtime";Frame after;
                if(construction==null)after=received(child,cuts,r,candidate,replay.checked,next,finals);else{
                    Projection p=r.projection;after=ContextDependentRuntimeInspection.project(child,replay.checked,new ContextDependentRuntimeInspection.Request(p.products,p.nodes,p.copyWork,p.auditWork));
                    ContextDependentRuntimeModelAudit indexed=new ContextDependentRuntimeModelAudit(child,cuts,after,models,points,cells,p.products,p.nodes,p.truthWork,ContextGroundProductionRounds.budget(p.shapeWork));need(indexed.truth()==finals,"Every constructed receiving raw occurrence must pass independent indexed truth");
                }
                phase="round-frontier";ContextDependentFrontierInspection.Snapshot frontier=ContextDependentFrontierInspection.inspect(last(replay.checked),r.frontierWork,r.frontierCapacity);ContextDependentFrontierInspection.Comparison comparison=ContextDependentFrontierInspection.compare(before,frontier,r.frontierWork);need(comparison.removedOrWorsened==0,"Complete receiving proof frontier must not lose support or worsen minimum depth");
                cycles.add(new Cycle(frame,after,discovery,replay,originals,finals,comparison,construction));interrupt();
                if(!comparison.changed)return new Result(Status.STABLE_PENDING,"received-frontier-stable","Unchanged supported signed typed/whole-Source/min-depth frontier AFTER complete receiving/native/indexed audits; full-language/default admission unknown",first,cycles,frontier,Collections.emptyList(),startup);
                before=frontier;frame=after;checked=replay.checked;history=replay.history;
            }
            return Result.incomplete("round-limit","Complete round schedule exhausted before an unchanged receiving proof frontier");
        }catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw interrupted;}catch(Exception failure){interrupt();return Result.incomplete(phase,failure.getMessage());}
    }
    public static Result inspect(Member base,Member child,List<Cut> supplied,Request r)throws Exception{
        return inspect(base,child,supplied,r,()->{},barriers->{},()->{});
    }
    static Result inspect(Member base,Member child,List<Cut> supplied,Request r,Runnable chargeCandidate,java.util.function.IntConsumer chargeReplay,Runnable chargeReplayWork)throws Exception{
        if(base==null||child==null||supplied==null||supplied.contains(null)||r==null)throw new IllegalArgumentException("Actual parent/child and complete cuts required");List<Cut> cuts=new ArrayList<>(supplied);interrupt();current(base,child,cuts,r);
        try{
            if(r.automatic){
                ContextDependentRuntimeModelInspection.Result startup=ContextDependentRuntimeModelInspection.inspect(base,child,cuts,r.preparation);
                if(startup.status==ContextDependentRuntimeModelInspection.Status.INCOMPLETE)return Result.incomplete("automatic-preparation",startup.phase+": "+startup.reason);
                if(startup.status!=ContextDependentRuntimeModelInspection.Status.RUNTIME_MODELLED_PENDING)return new Result(Status.valueOf(startup.status.name()),"automatic-preparation",startup.reason,null,Collections.emptyList(),null,Collections.emptyList(),startup);
                ContextDependentCandidateInspection.Result candidate=startup.projection.qualification.candidate;
                return run(child,cuts,r,null,candidate,candidate.proposed.checked,candidate.proposed.history,startup,chargeCandidate,chargeReplay,chargeReplayWork);
            }
            ContextDependentRuntimeBarrierInspection.Result first=ContextDependentRuntimeBarrierInspection.inspect(base,child,cuts,r.first);
            if(first.status!=ContextDependentRuntimeBarrierInspection.Status.RECEIVED_PENDING){if(first.status==ContextDependentRuntimeBarrierInspection.Status.INCOMPLETE)return Result.incomplete("first-barrier",first.phase+": "+first.reason);Status status=Status.valueOf(first.status.name());return new Result(status,"first-barrier",first.reason,status==Status.BASE_REFUTED||status==Status.CANDIDATE_REFUTED?first:null,Collections.emptyList(),null,first.oppositions);}
            return run(child,cuts,r,first,first.execution.preparation.projection.qualification.candidate,first.checked,first.history);
        }finally{current(base,child,cuts,r);}
    }
}
