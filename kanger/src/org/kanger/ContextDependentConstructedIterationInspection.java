/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextDependentCutInspection.*;

/** Fresh constructed startup and receiving models; frozen discovery and audited frontier stability. */
public final class ContextDependentConstructedIterationInspection {
    private ContextDependentConstructedIterationInspection(){}
    public enum Status {BASE_REFUTED,CANDIDATE_REFUTED,NO_MATCH,ROUND_REFUTED,STABLE_PENDING,INCOMPLETE}
    public static final class RoundLimits {
        public final ContextDependentRuntimeDiscovery.Limits discovery;
        public final int work,auditWork,nodes,bridgeWork,originalWork,finalWork;
        public RoundLimits(ContextDependentRuntimeDiscovery.Limits discovery,int work,int auditWork,int nodes,int bridgeWork,int originalWork,int finalWork){
            ContextDependentRuntimeContinuationInspection.AutomaticPlan p=new ContextDependentRuntimeContinuationInspection.AutomaticPlan(discovery,Collections.emptyMap(),work,auditWork,nodes,bridgeWork,originalWork,finalWork);
            this.discovery=p.discovery;this.work=work;this.auditWork=auditWork;this.nodes=nodes;this.bridgeWork=bridgeWork;this.originalWork=originalWork;this.finalWork=finalWork;
        }
        private ContextDependentRuntimeContinuationInspection.AutomaticPlan plan(){return new ContextDependentRuntimeContinuationInspection.AutomaticPlan(discovery,Collections.emptyMap(),work,auditWork,nodes,bridgeWork,originalWork,finalWork);}
    }
    public static final class Request {
        public final ContextDependentConstructedStartupInspection.Request startup;
        public final ContextDependentModelSearchInspection.Request receivingSearch;
        public final List<RoundLimits> rounds;public final int initialWork,frontierWork,frontierCapacity;
        public Request(ContextDependentConstructedStartupInspection.Request startup,ContextDependentModelSearchInspection.Request receivingSearch,List<RoundLimits> rounds,int initialWork,int frontierWork,int frontierCapacity){
            if(startup==null||receivingSearch==null||rounds==null||rounds.isEmpty()||rounds.contains(null)||rounds.size()>startup.append.iteration.policy.steps||initialWork<1||frontierWork<1||frontierCapacity<1||frontierCapacity>100000)throw new IllegalArgumentException("Constructed startup/receiving search and complete bounded schedule required");
            this.startup=startup;this.receivingSearch=new ContextDependentModelSearchInspection.Request(startup.append.iteration,receivingSearch.extraPoints,receivingSearch.otherRepresentative,receivingSearch.limits,receivingSearch.projection);this.rounds=Collections.unmodifiableList(new ArrayList<>(rounds));this.initialWork=initialWork;this.frontierWork=frontierWork;this.frontierCapacity=frontierCapacity;
        }
    }
    public static final class Result {
        public final Status status;public final String phase,reason;public final UUID epoch;
        public final ContextDependentConstructedStartupInspection.Result startup;
        public final ContextDependentRuntimeIterationInspection.Result iteration;
        private Result(Status status,String phase,String reason,ContextDependentConstructedStartupInspection.Result startup,ContextDependentRuntimeIterationInspection.Result iteration){this.status=status;this.phase=phase;this.reason=reason;this.startup=startup;this.iteration=iteration;epoch=status==Status.STABLE_PENDING?iteration.epoch:null;}
        private static Result incomplete(String phase,String why){return new Result(Status.INCOMPLETE,phase,why,null,null);}
    }
    private static void current(Member base,Member child,List<Cut> cuts,Request r)throws Exception{
        ContextDependentReplayInspection.Policy p=r.startup.append.iteration.policy;ContextDependentReplayInspection.current(base.actual,base.inventory,p);ContextDependentReplayInspection.current(child.actual,child.inventory,p);for(Cut cut:cuts)for(Member m:cut.members)ContextDependentReplayInspection.currentMember(m.actual,m.inventory,p);
    }
    public static Result inspect(Member base,Member child,List<Cut> supplied,Request r)throws Exception{
        if(base==null||child==null||supplied==null||supplied.contains(null)||r==null)throw new IllegalArgumentException("Actual parent/child and complete cuts required");List<Cut> cuts=new ArrayList<>(supplied);ContextDependentRuntimeExecution.interrupt();current(base,child,cuts,r);String phase="constructed-startup";
        try{try{
            ContextDependentConstructedStartupInspection.Result startup=ContextDependentConstructedStartupInspection.inspect(base,child,cuts,r.startup);
            if(startup.status==ContextDependentConstructedStartupInspection.Status.INCOMPLETE)return Result.incomplete(phase,startup.phase+": "+startup.reason);
            if(startup.status!=ContextDependentConstructedStartupInspection.Status.RUNTIME_MODELLED_PENDING)return new Result(Status.valueOf(startup.status.name()),phase,startup.reason,startup,null);
            List<ContextDependentRuntimeContinuationInspection.AutomaticPlan> schedule=new ArrayList<>();for(RoundLimits bounds:r.rounds)schedule.add(bounds.plan());
            phase="constructed-iteration";ContextDependentRuntimeIterationInspection.Request request=ContextDependentRuntimeIterationInspection.constructedReceiving(startup.preparation,r.receivingSearch.projection,schedule,r.initialWork,r.frontierWork,r.frontierCapacity,r.receivingSearch);
            ContextDependentRuntimeIterationInspection.Result iteration=ContextDependentRuntimeIterationInspection.inspect(base,child,cuts,request);ContextDependentRuntimeExecution.interrupt();
            if(iteration.status==ContextDependentRuntimeIterationInspection.Status.INCOMPLETE)return Result.incomplete(iteration.phase,iteration.reason);
            Status status=Status.valueOf(iteration.status.name());return new Result(status,iteration.phase,iteration.reason,status==Status.STABLE_PENDING?startup:null,iteration);
        }catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw interrupted;}catch(Exception failure){ContextDependentRuntimeExecution.interrupt();return Result.incomplete(phase,failure.getMessage());}}finally{current(base,child,cuts,r);}
    }
}
