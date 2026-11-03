/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextDependentCutInspection.*;
import org.kanger.ContextDependentModelInspection.*;
import org.kanger.ContextDependentModelInspection.Function;
import org.kanger.ContextDependentRuntimeInspection.Frame;

/** Fresh native candidate, typed runtime projection and separate indexed-runtime truth audit. */
public final class ContextDependentRuntimeModelInspection {
    private ContextDependentRuntimeModelInspection(){}
    public enum Status {BASE_REFUTED,CANDIDATE_REFUTED,RUNTIME_MODELLED_PENDING,INCOMPLETE}
    public static final class Request {
        public final ContextDependentRuntimeInspection.Request projection;
        public final int shapeWork,truthWork;
        public Request(ContextDependentRuntimeInspection.Request projection,int shapeWork,int truthWork){if(projection==null||shapeWork<1||truthWork<1)throw new IllegalArgumentException("Fresh projection and separate runtime shape/truth bounds required");this.projection=projection;this.shapeWork=shapeWork;this.truthWork=truthWork;}
    }
    public static final class Result {
        public final Status status;public final String phase,reason;public final Frame frame;
        public final int runtimeObligations;
        public final ContextDependentRuntimeInspection.Result projection;
        private Result(Status status,String phase,String reason,int obligations,ContextDependentRuntimeInspection.Result projection){this.status=status;this.phase=phase;this.reason=reason;runtimeObligations=obligations;this.projection=projection;frame=projection==null?null:projection.frame;}
        private static Result incomplete(String phase,String reason){return new Result(Status.INCOMPLETE,phase,reason,0,null);}
    }
    private static void need(boolean b,String why){if(!b)throw new IllegalStateException(why);}
    private static void interrupt()throws InterruptedException{if(Thread.currentThread().isInterrupted())throw new InterruptedException("Runtime model qualification interrupted");}
    private static void current(Member base,Member child,List<Cut> cuts,Request r)throws Exception{ContextDependentReplayInspection.current(base.actual,base.inventory,r.projection.qualification.append.iteration.policy);ContextDependentReplayInspection.current(child.actual,child.inventory,r.projection.qualification.append.iteration.policy);for(Cut cut:cuts)for(Member m:cut.members)ContextDependentReplayInspection.currentMember(m.actual,m.inventory,r.projection.qualification.append.iteration.policy);}
    public static Result inspect(Member base,Member child,List<Cut> supplied,Request request)throws Exception{
        if(base==null||child==null||supplied==null||supplied.contains(null)||request==null)throw new IllegalArgumentException("Actual parent/child/cuts required");List<Cut> cuts=new ArrayList<>(supplied);interrupt();current(base,child,cuts,request);
        try{
            ContextDependentRuntimeInspection.Result projected=ContextDependentRuntimeInspection.inspect(base,child,cuts,request.projection);
            if(projected.status==ContextDependentRuntimeInspection.Status.BASE_REFUTED)return new Result(Status.BASE_REFUTED,"sourced-base-refutation",projected.reason,0,projected);
            if(projected.status==ContextDependentRuntimeInspection.Status.CANDIDATE_REFUTED)return new Result(Status.CANDIDATE_REFUTED,"sourced-candidate-refutation",projected.reason,0,projected);
            if(projected.status!=ContextDependentRuntimeInspection.Status.PROJECTED_PENDING)return Result.incomplete("projection",projected.reason);
            String phase="runtime-model-rebase";
            try{
                Runnable work=ContextGroundProductionRounds.budget(request.shapeWork);Map<String,Model> models=new LinkedHashMap<>();
                // Only internally fresh original->actual function maps; no caller frame or rebasing receipt.
                need(projected.qualification.reboundFunctions.keySet().equals(request.projection.qualification.proposedOriginalModels.keySet()),"Whole proposed rebinding cut coverage required");
                for(Map.Entry<String,Model> entry:request.projection.qualification.proposedOriginalModels.entrySet()){
                    interrupt();work.run();Map<Function,Function> mapping=projected.qualification.reboundFunctions.get(entry.getKey());Model old=entry.getValue();need(mapping.keySet().equals(old.functions.keySet()),"Exact whole supplied function rebinding required");Map<Function,Choice> functions=new LinkedHashMap<>();for(Map.Entry<Function,Choice> f:old.functions.entrySet()){work.run();need(functions.put(mapping.get(f.getKey()),f.getValue())==null,"No aliased runtime function declarations");}models.put(entry.getKey(),new Model(old.points,old.otherRepresentative,old.relations,functions));
                }
                phase="runtime-shape";ContextDependentRuntimeModelAudit audit=new ContextDependentRuntimeModelAudit(child,cuts,projected.frame,models,request,work);
                phase="runtime-truth";int count=audit.truth();need(count==projected.qualification.proposedFinalObligations,"Every qualified raw final occurrence must be checked in runtime representation");interrupt();
                return new Result(Status.RUNTIME_MODELLED_PENDING,"owned-runtime-modelled","Indexed runtime products satisfy the supplied all-value interpretation; ordinary native query/write admission unknown",count,projected);
            }catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw interrupted;}catch(Exception failure){return Result.incomplete(phase,failure.getMessage());}
        }finally{current(base,child,cuts,request);}
    }
}
