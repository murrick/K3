/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextDependentCutInspection.*;
import org.kanger.ContextProductionCapture.Source;
import org.kanger.ContextDependentProductionInspection.Refutation;
import org.kanger.ContextDependentIterationInspection.Status;

/** Actual native incoming append plus independent base/proposed typed diagnostics; no write authority. */
public final class ContextDependentCandidateInspection {
    private ContextDependentCandidateInspection(){}
    public enum Verdict {BASE_REFUTED,CANDIDATE_REFUTED,STABLE_PENDING,INCOMPLETE}
    public static final class Request {
        public final ContextDependentIterationInspection.Request iteration;public final List<String> statements;
        public Request(ContextDependentIterationInspection.Request iteration,List<String> statements){if(iteration==null||statements==null||statements.isEmpty()||statements.size()>iteration.policy.rows)throw new IllegalArgumentException("Exact bounded incoming batch required");List<String> copy=new ArrayList<>(statements);for(String text:copy)if(text==null||!text.startsWith("!")||text.length()>iteration.policy.bytes)throw new IllegalArgumentException("Whole exact incoming assertion origins required");this.iteration=iteration;this.statements=Collections.unmodifiableList(copy);}
    }
    public static final class Opposition {
        public final String cut;public final Refutation proof;public final Set<Source> candidates,originalSources;
        private Opposition(String cut,Refutation proof,Set<Source> candidates,Set<Source> originals){this.cut=cut;this.proof=proof;this.candidates=Collections.unmodifiableSet(new LinkedHashSet<>(candidates));originalSources=Collections.unmodifiableSet(new LinkedHashSet<>(originals));}
    }
    public static final class Result {
        public final Verdict verdict;public final String phase,reason;
        public final String baseFingerprint,proposedFingerprint;public final Set<Source> candidates;
        /** Child original Source -> physical parent original Source. New candidates map to themselves. */
        public final Map<Source,Source> originals;public final List<Opposition> oppositions;
        public final ContextDependentIterationInspection.Result base,proposed;
        private Result(Verdict verdict,String phase,String reason,Manifest manifest,List<Opposition> oppositions,ContextDependentIterationInspection.Result base,ContextDependentIterationInspection.Result proposed){this.verdict=verdict;this.phase=phase;this.reason=reason;baseFingerprint=manifest==null?null:manifest.base.inventory.fingerprint;proposedFingerprint=manifest==null?null:manifest.child.inventory.fingerprint;candidates=manifest==null?Collections.emptySet():Collections.unmodifiableSet(new LinkedHashSet<>(manifest.candidates));originals=manifest==null?Collections.emptyMap():Collections.unmodifiableMap(new LinkedHashMap<>(manifest.translation));this.oppositions=Collections.unmodifiableList(new ArrayList<>(oppositions));this.base=base;this.proposed=proposed;}
        private static Result incomplete(String phase,String reason){return new Result(Verdict.INCOMPLETE,phase,reason,null,Collections.emptyList(),null,null);}
    }
    private static void need(boolean b,String why){if(!b)throw new IllegalStateException(why);}
    private static void interrupt()throws InterruptedException{if(Thread.currentThread().isInterrupted())throw new InterruptedException("Dependent candidate inspection interrupted");}
    private static Source source(ContextProductionInventory i,long id){return new Source(i.context,i.revision,id,i.localX,"inventory:"+i.fingerprint);}
    private static final class Manifest {
        final Member base,child;final Map<Source,Source> translation=new LinkedHashMap<>();final Set<Source> candidates=new LinkedHashSet<>(),originalSources=new HashSet<>();
        Manifest(Member base,Member child,List<Cut> cuts,Request request)throws Exception{
            this.base=base;this.child=child;Runnable work=ContextGroundProductionRounds.budget(request.iteration.policy.work);ContextProductionInventory a=base.inventory,b=child.inventory;
            need(base.actual!=child.actual&&child.actual.getNext()==base.actual&&base.actual.getUser()==child.actual.getUser(),"Actual immediate native parent/child required");need(a.localX&&b.localX&&a.context.equals(b.context)&&a.revision==b.revision&&a.initialization.isEmpty()&&b.initialization.isEmpty(),"Exact same-revision local native append basis required");need(a.rows.size()==a.primary.size()&&b.rows.size()==b.primary.size(),"Whole primary-only parent and child required");
            Map<Long,ContextProductionInventory.Row> old=new LinkedHashMap<>();long maximum=-1;for(ContextProductionInventory.Row row:a.rows){work.run();old.put(row.nativeRule,row);maximum=Math.max(maximum,row.nativeRule);originalSources.add(source(a,row.nativeRule));}
            Set<Long> preserved=new HashSet<>();List<ContextProductionInventory.Row> added=new ArrayList<>();for(ContextProductionInventory.Row row:b.rows){work.run();ContextProductionInventory.Row before=old.get(row.nativeRule);Source next=source(b,row.nativeRule);if(before!=null){need(Objects.equals(before.origin,row.origin)&&Objects.equals(before.nativeText,row.nativeText)&&before.nativeDigest.equals(row.nativeDigest)&&before.generated==row.generated&&before.deleted==row.deleted&&before.query==row.query,"Actual parent original changed in native child");need(preserved.add(row.nativeRule),"Duplicate preserved native original");translation.put(next,source(a,row.nativeRule));}else{need(row.nativeRule>maximum&&!row.generated&&!row.deleted&&!row.query,"Append-only primary candidate occurrence required");added.add(row);need(candidates.add(next),"Distinct native candidate occurrence required");translation.put(next,next);}}
            need(preserved.equals(old.keySet())&&added.size()==request.statements.size(),"Whole preserved parent and exact incoming batch required");for(int i=0;i<added.size();i++){work.run();need(request.statements.get(i).equals(added.get(i).origin),"Incoming statement order/text differs from actual native append");}
            Set<UUID> members=new HashSet<>();members.add(a.context);Set<String> groups=new HashSet<>();for(Cut cut:cuts){work.run();need(groups.add(cut.key),"Split/repeated logical cut");for(Member m:cut.members){need(members.add(m.inventory.context),"Repeated physical native candidate member");for(ContextProductionInventory.Row row:m.inventory.rows){work.run();Source s=source(m.inventory,row.nativeRule);need(translation.put(s,s)==null&&originalSources.add(s),"Aliased original foreign Source");}}}
        }
    }
    private static List<Opposition> oppositions(ContextDependentIterationInspection.Result run,Manifest manifest,boolean proposed){
        ContextDependentRoundInspection.Barrier barrier=run.checked.barriers.get(run.checked.barriers.size()-1);List<Opposition> output=new ArrayList<>();for(Map.Entry<String,ContextDependentProductionInspection.Result> cut:barrier.cuts.entrySet())for(List<Refutation> list:Arrays.asList(cut.getValue().originalUnitRefutations,cut.getValue().finalUnitRefutations))for(Refutation proof:list){Set<Source> candidates=new LinkedHashSet<>(),sources=new LinkedHashSet<>();for(Source s:proof.sources){Source original=proposed?manifest.translation.get(s):s;need(original!=null,"Unmapped actual candidate proof Source");if(manifest.candidates.contains(s))candidates.add(s);else need(manifest.originalSources.contains(original),"Unknown physical original in candidate refutation");need(sources.add(original),"Aliased translated candidate proof support");}need(!proposed||!candidates.isEmpty(),"Candidate-free proposed refutation disagrees with checked fixed-point base");output.add(new Opposition(cut.getKey(),proof,candidates,sources));}need(!output.isEmpty(),"Refuted candidate result requires actual sourced refutations");return output;
    }
    private static void current(Member base,Member child,List<Cut> cuts,Request request)throws Exception{ContextDependentReplayInspection.current(base.actual,base.inventory,request.iteration.policy);ContextDependentReplayInspection.current(child.actual,child.inventory,request.iteration.policy);for(Cut cut:cuts)for(Member member:cut.members)ContextDependentReplayInspection.currentMember(member.actual,member.inventory,request.iteration.policy);}
    public static Result inspect(Member base,Member child,List<Cut> supplied,Request request)throws Exception{
        if(base==null||child==null||supplied==null||supplied.contains(null)||request==null)throw new IllegalArgumentException("Actual parent, proposed child and whole foreign cuts required");List<Cut> cuts=new ArrayList<>(supplied);interrupt();current(base,child,cuts,request);
        try {String phase="native-append-manifest";
            try {Manifest manifest=new Manifest(base,child,cuts,request);phase="base-iteration";ContextDependentIterationInspection.Result original=ContextDependentIterationInspection.inspect(base,cuts,request.iteration);interrupt();
                if(original.status==Status.COLLISION)return new Result(Verdict.BASE_REFUTED,phase,"Pre-existing sourced base refutation; incoming assertions not blamed",manifest,oppositions(original,manifest,false),original,null);
                if(original.status!=Status.FIXED_POINT)return Result.incomplete(phase,original.reason);
                phase="proposed-iteration";ContextDependentIterationInspection.Result proposed=ContextDependentIterationInspection.inspect(child,cuts,request.iteration);interrupt();
                if(proposed.status==Status.COLLISION)return new Result(Verdict.CANDIDATE_REFUTED,"candidate-causality","Actual appended candidate used by every returned proposed refutation",manifest,oppositions(proposed,manifest,true),original,proposed);
                if(proposed.status!=Status.FIXED_POINT)return Result.incomplete(phase,proposed.reason);
                return new Result(Verdict.STABLE_PENDING,"historical-pending-append","Exact native incoming append and base/proposed supported-unit cycles checked; model/native-product/write admission unknown",manifest,Collections.emptyList(),original,proposed);
            }catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw interrupted;}
            catch(Exception failure){return Result.incomplete(phase,failure.getMessage());}
        }finally{current(base,child,cuts,request);}
    }
}
