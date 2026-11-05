/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextDependentCutInspection.*;
import org.kanger.ContextDependentModelInspection.Model;
import org.kanger.ContextDependentModelInspection.Function;
import org.kanger.ContextDependentModelInspection.Choice;
import org.kanger.ContextProductionCapture.Source;

/** Constructs both append interpretations, then freshly qualifies the indexed startup. Read-only. */
public final class ContextDependentConstructedStartupInspection {
    private ContextDependentConstructedStartupInspection(){}
    public enum Status {BASE_REFUTED,CANDIDATE_REFUTED,RUNTIME_MODELLED_PENDING,INCOMPLETE}
    public static final class Request {
        public final ContextDependentCandidateInspection.Request append;
        public final ContextDependentModelSearchInspection.Request search;
        public final int rebaseWork;
        public Request(ContextDependentCandidateInspection.Request append,ContextDependentModelSearchInspection.Request search,int rebaseWork){
            if(append==null||search==null||rebaseWork<1)throw new IllegalArgumentException("Actual append, bounded construction and separate rebasing work required");
            this.append=append;this.search=new ContextDependentModelSearchInspection.Request(append.iteration,search.extraPoints,search.otherRepresentative,search.limits,search.projection);this.rebaseWork=rebaseWork;
        }
    }
    public static final class Result {
        public final Status status;public final String phase,reason;
        public final ContextDependentCandidateInspection.Result candidate;
        public final ContextDependentRuntimeModelInspection.Result startup;
        /** Historical configuration, freshly rechecked if used by automatic iteration; never a receipt. */
        public final ContextDependentRuntimeModelInspection.Request preparation;
        public final int baseTried,proposedTried;
        private Result(Status status,String phase,String reason,ContextDependentCandidateInspection.Result candidate,ContextDependentRuntimeModelInspection.Result startup,ContextDependentRuntimeModelInspection.Request preparation,int baseTried,int proposedTried){this.status=status;this.phase=phase;this.reason=reason;this.candidate=candidate;this.startup=startup;this.preparation=preparation;this.baseTried=baseTried;this.proposedTried=proposedTried;}
        private static Result incomplete(String phase,String reason,int baseTried,int proposedTried){return new Result(Status.INCOMPLETE,phase,reason,null,null,null,baseTried,proposedTried);}
    }
    private static void need(boolean b,String why){if(!b)throw new IllegalStateException(why);}
    private static void current(Member base,Member child,List<Cut> cuts,Request r)throws Exception{
        ContextDependentReplayInspection.current(base.actual,base.inventory,r.append.iteration.policy);ContextDependentReplayInspection.current(child.actual,child.inventory,r.append.iteration.policy);for(Cut cut:cuts)for(Member m:cut.members)ContextDependentReplayInspection.currentMember(m.actual,m.inventory,r.append.iteration.policy);
    }
    public static Result inspect(Member base,Member child,List<Cut> supplied,Request r)throws Exception{
        return inspect(base,child,supplied,r,()->{},()->{});
    }
    static Result inspect(Member base,Member child,List<Cut> supplied,Request r,Runnable chargeCandidate,Runnable chargeAudit)throws Exception{
        if(base==null||child==null||supplied==null||supplied.contains(null)||r==null)throw new IllegalArgumentException("Actual parent/child and complete foreign cuts required");
        List<Cut> cuts=new ArrayList<>(supplied);ContextDependentRuntimeExecution.interrupt();current(base,child,cuts,r);String phase="native-append";int bt=0,pt=0;
        try{try{
            // Refutation and append identity precede every guess, including an exhausted palette.
            ContextDependentCandidateInspection.Result c=ContextDependentCandidateInspection.inspect(base,child,cuts,r.append);
            if(c.verdict==ContextDependentCandidateInspection.Verdict.BASE_REFUTED)return new Result(Status.BASE_REFUTED,c.phase,c.reason,c,null,null,0,0);
            if(c.verdict==ContextDependentCandidateInspection.Verdict.CANDIDATE_REFUTED)return new Result(Status.CANDIDATE_REFUTED,c.phase,c.reason,c,null,null,0,0);
            if(c.verdict!=ContextDependentCandidateInspection.Verdict.STABLE_PENDING)return Result.incomplete(phase,c.reason,0,0);
            phase="base-construction";ContextDependentModelSearchInspection.Result b=ContextDependentModelSearchInspection.inspect(base,cuts,r.search,chargeCandidate,chargeAudit);bt=b.tried;
            if(b.status!=ContextDependentModelSearchInspection.Status.MODELLED_PENDING)return Result.incomplete(phase,b.phase+": "+b.reason,bt,0);
            phase="proposed-construction";ContextDependentModelSearchInspection.Result p=ContextDependentModelSearchInspection.inspect(child,cuts,r.search,chargeCandidate,chargeAudit);pt=p.tried;
            if(p.status!=ContextDependentModelSearchInspection.Status.MODELLED_PENDING)return Result.incomplete(phase,p.phase+": "+p.reason,bt,pt);
            phase="parent-original-rebase";Runnable work=ContextGroundProductionRounds.budget(r.rebaseWork);Map<String,Model> originals=new LinkedHashMap<>();int entries=0;
            for(Map.Entry<String,Model> entry:p.models.entrySet()){
                ContextDependentRuntimeExecution.interrupt();work.run();Model model=entry.getValue();Map<Function,Choice> functions=new LinkedHashMap<>();
                for(Map.Entry<Function,Choice> f:model.functions.entrySet()){
                    work.run();need(++entries<=r.search.limits.cells,"Whole constructed function rebasing bound");Source source=c.originals.get(f.getKey().source);need(source!=null,"Every constructed function requires exact freshly checked original Source");
                    need(functions.put(new Function(source,f.getKey().binder),f.getValue())==null,"No aliased original function after constructed rebasing");
                }
                originals.put(entry.getKey(),new Model(model.points,model.otherRepresentative,model.relations,functions));
            }
            ContextDependentModelSearchInspection.Limits l=r.search.limits;ContextDependentRuntimeContinuationInspection.Projection q=r.search.projection;
            ContextDependentCandidateModelInspection.Request qualification=new ContextDependentCandidateModelInspection.Request(r.append,b.models,originals,l.points,l.cells,l.originalWork,l.finalWork,l.originalWork,l.finalWork);
            ContextDependentRuntimeModelInspection.Request preparation=new ContextDependentRuntimeModelInspection.Request(new ContextDependentRuntimeInspection.Request(qualification,q.products,q.nodes,q.copyWork,q.auditWork),q.shapeWork,q.truthWork);
            phase="fresh-runtime-qualification";ContextDependentRuntimeModelInspection.Result startup=ContextDependentRuntimeModelInspection.inspect(base,child,cuts,preparation,chargeAudit);ContextDependentRuntimeExecution.interrupt();
            if(startup.status!=ContextDependentRuntimeModelInspection.Status.RUNTIME_MODELLED_PENDING)return Result.incomplete(phase,startup.phase+": "+startup.reason,bt,pt);
            return new Result(Status.RUNTIME_MODELLED_PENDING,"constructed-startup-audited","Constructed base/proposed models freshly rechecked against actual append, native originals/finals and independent indexed truth; not write admission",startup.projection.qualification.candidate,startup,preparation,bt,pt);
        }catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw interrupted;}catch(Exception failure){ContextDependentRuntimeExecution.interrupt();return Result.incomplete(phase,failure.getMessage(),bt,pt);}}finally{current(base,child,cuts,r);}
    }
}
