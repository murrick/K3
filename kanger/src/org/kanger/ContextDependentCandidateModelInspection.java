/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextDependentCutInspection.*;
import org.kanger.ContextDependentModelInspection.Model;
import org.kanger.ContextDependentModelInspection.Function;
import org.kanger.ContextDependentModelInspection.Choice;
import org.kanger.ContextProductionCapture.Source;

/** One fresh native-append/base/proposed-model diagnostic. No supplied receipt or write capability. */
public final class ContextDependentCandidateModelInspection {
    private ContextDependentCandidateModelInspection(){}
    public enum Status {BASE_REFUTED,CANDIDATE_REFUTED,MODELLED_PENDING,INCOMPLETE}
    public static final class Request {
        public final ContextDependentCandidateInspection.Request append;
        public final Map<String,Model> baseModels,proposedOriginalModels;
        public final int points,cells,baseOriginalWork,baseFinalWork,proposedOriginalWork,proposedFinalWork;
        /** Proposed preserved-function keys use PARENT original Sources; new candidates/foreign keys stay exact. */
        public Request(ContextDependentCandidateInspection.Request append,Map<String,Model> baseModels,Map<String,Model> proposedOriginalModels,int points,int cells,int baseOriginalWork,int baseFinalWork,int proposedOriginalWork,int proposedFinalWork){
            if(append==null)throw new IllegalArgumentException("Actual append request required");ContextDependentModelInspection.Request base=new ContextDependentModelInspection.Request(append.iteration,baseModels,points,cells,baseOriginalWork,baseFinalWork),proposed=new ContextDependentModelInspection.Request(append.iteration,proposedOriginalModels,points,cells,proposedOriginalWork,proposedFinalWork);this.append=append;this.baseModels=base.models;this.proposedOriginalModels=proposed.models;this.points=points;this.cells=cells;this.baseOriginalWork=baseOriginalWork;this.baseFinalWork=baseFinalWork;this.proposedOriginalWork=proposedOriginalWork;this.proposedFinalWork=proposedFinalWork;
        }
    }
    public static final class Result {
        public final Status status;public final String phase,reason;
        public final int baseOriginalRows,baseFinalObligations,proposedOriginalRows,proposedFinalObligations;
        public final ContextDependentCandidateInspection.Result candidate;
        public final Map<String,Map<Function,Function>> reboundFunctions;
        private Result(Status status,String phase,String reason,ContextDependentCandidateInspection.Result candidate,int bo,int bf,int po,int pf,Map<String,Map<Function,Function>> rebound){this.status=status;this.phase=phase;this.reason=reason;this.candidate=candidate;baseOriginalRows=bo;baseFinalObligations=bf;proposedOriginalRows=po;proposedFinalObligations=pf;this.reboundFunctions=Collections.unmodifiableMap(new LinkedHashMap<>(rebound));}
        private static Result incomplete(String phase,String reason){return new Result(Status.INCOMPLETE,phase,reason,null,0,0,0,0,Collections.emptyMap());}
    }
    private static void need(boolean b,String why){if(!b)throw new IllegalStateException(why);}
    private static void interrupt()throws InterruptedException{if(Thread.currentThread().isInterrupted())throw new InterruptedException("Candidate/model composition interrupted");}
    private static void current(Member base,Member child,List<Cut> cuts,Request request)throws Exception{ContextDependentReplayInspection.current(base.actual,base.inventory,request.append.iteration.policy);ContextDependentReplayInspection.current(child.actual,child.inventory,request.append.iteration.policy);for(Cut cut:cuts)for(Member member:cut.members)ContextDependentReplayInspection.currentMember(member.actual,member.inventory,request.append.iteration.policy);}
    public static Result inspect(Member base,Member child,List<Cut> supplied,Request request)throws Exception{
        if(base==null||child==null||supplied==null||supplied.contains(null)||request==null)throw new IllegalArgumentException("Actual parent/child and complete foreign cuts required");List<Cut> cuts=new ArrayList<>(supplied);interrupt();current(base,child,cuts,request);
        try {String phase="native-append-and-cycles";
            try {
                ContextDependentCandidateInspection.Result candidate=ContextDependentCandidateInspection.inspect(base,child,cuts,request.append);interrupt();
                if(candidate.verdict==ContextDependentCandidateInspection.Verdict.BASE_REFUTED)return new Result(Status.BASE_REFUTED,candidate.phase,candidate.reason,candidate,0,0,0,0,Collections.emptyMap());
                if(candidate.verdict==ContextDependentCandidateInspection.Verdict.CANDIDATE_REFUTED)return new Result(Status.CANDIDATE_REFUTED,candidate.phase,candidate.reason,candidate,0,0,0,0,Collections.emptyMap());
                if(candidate.verdict!=ContextDependentCandidateInspection.Verdict.STABLE_PENDING)return Result.incomplete(phase,candidate.phase+": "+candidate.reason);
                // Internally fresh cycles only: no caller-supplied candidate/model diagnostic can skip an audit.
                phase="base-model-preflight";ContextDependentModelAudit original=new ContextDependentModelAudit(base,cuts,candidate.base,new ContextDependentModelInspection.Request(request.append.iteration,request.baseModels,request.points,request.cells,request.baseOriginalWork,request.baseFinalWork));
                phase="base-original-audit";int bo=original.original();interrupt();phase="base-actual-final-audit";int bf=original.finalModel();interrupt();
                phase="proposed-model-rebase";Runnable work=ContextGroundProductionRounds.budget(request.append.iteration.policy.work);Map<Source,Source> forward=new LinkedHashMap<>();for(Map.Entry<Source,Source> entry:candidate.originals.entrySet()){work.run();need(forward.put(entry.getValue(),entry.getKey())==null,"One-to-one actual original Source rebasing required");}
                Map<String,Model> models=new LinkedHashMap<>();Map<String,Map<Function,Function>> rebindings=new LinkedHashMap<>();int entries=0;
                for(Map.Entry<String,Model> cut:request.proposedOriginalModels.entrySet()){
                    interrupt();work.run();Model input=cut.getValue();Map<Function,Choice> functions=new LinkedHashMap<>();Map<Function,Function> renamed=new LinkedHashMap<>();
                    for(Map.Entry<Function,Choice> entry:input.functions.entrySet()){work.run();need(++entries<=request.cells,"Whole supplied function rebasing bound");Function key=entry.getKey();Source actual=forward.get(key.source);need(actual!=null,"Proposed preserved functions require exact PARENT original Sources; candidates and foreign Sources stay exact");Function next=new Function(actual,key.binder);need(functions.put(next,entry.getValue())==null,"Aliased proposed function declaration after rebasing");renamed.put(key,next);}
                    models.put(cut.getKey(),new Model(input.points,input.otherRepresentative,input.relations,functions));rebindings.put(cut.getKey(),Collections.unmodifiableMap(renamed));
                }
                phase="proposed-model-preflight";ContextDependentModelAudit proposed=new ContextDependentModelAudit(child,cuts,candidate.proposed,new ContextDependentModelInspection.Request(request.append.iteration,models,request.points,request.cells,request.proposedOriginalWork,request.proposedFinalWork));
                phase="proposed-original-audit";int po=proposed.original();interrupt();phase="proposed-actual-final-audit";int pf=proposed.finalModel();interrupt();
                return new Result(Status.MODELLED_PENDING,"historical-native-append-modelled","Actual append, fresh base/proposed cycles and all four all-value model audits checked; native product/runtime/write admission unknown",candidate,bo,bf,po,pf,rebindings);
            }catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw interrupted;}
            catch(Exception failure){return Result.incomplete(phase,failure.getMessage());}
        }finally{current(base,child,cuts,request);}
    }
}
