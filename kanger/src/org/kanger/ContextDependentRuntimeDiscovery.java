/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextDependentCutInspection.Member;
import org.kanger.ContextDependentCutInspection.Cut;
import org.kanger.ContextDependentCutInspection.Ref;
import org.kanger.ContextDependentRuntimeExecution.*;
import static org.kanger.ContextDependentRuntimeExecution.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.units.Rule;

/** Bounded exhaustive frozen-input ordinary residual discovery. No same-pass output donation. */
public final class ContextDependentRuntimeDiscovery {
    private ContextDependentRuntimeDiscovery(){}
    public static final class Limits {
        public final int candidates,outputs,scanWork;
        public Limits(int candidates,int outputs,int scanWork){if(candidates<1||outputs<1||scanWork<1)throw new IllegalArgumentException("Separate positive tuple/output/scan bounds required");this.candidates=candidates;this.outputs=outputs;this.scanWork=scanWork;}
    }
    public static final class Result {
        public final List<Step> steps;public final List<Output> outputs;public final int candidates,noMatches;
        private Result(List<Step> steps,List<Output> outputs,int candidates,int noMatches){this.steps=Collections.unmodifiableList(new ArrayList<>(steps));this.outputs=Collections.unmodifiableList(new ArrayList<>(outputs));this.candidates=candidates;this.noMatches=noMatches;}
    }
    private static final class Original {final Ref ref;final Clause clause;Original(Ref ref,Clause clause){this.ref=ref;this.clause=clause;}}
    private static final class Available {
        final Donor donor;final String predicate;final boolean positive;final int rank;
        Available(Donor donor,String predicate,boolean positive,int rank){this.donor=donor;this.predicate=predicate;this.positive=positive;this.rank=rank;}
    }
    /** Only an internally fresh, already shape/truth-audited frame reaches this entry. */
    static Result discover(Member child,List<Cut> cuts,ContextDependentRuntimeInspection.Frame frame,Request bounds,Limits limits)throws Exception{
        Runnable scan=ContextGroundProductionRounds.budget(limits.scanWork);Engine engine=new Engine(child,cuts,frame,bounds);List<Step> steps=new ArrayList<>();List<Output> outputs=new ArrayList<>();int candidates=0,noMatches=0;
        for(String cut:frame.cuts.keySet()){
            interrupt();scan.run();List<Member> members=new ArrayList<>();members.add(child);boolean known=cut.equals(ContextDependentCutInspection.local(child.inventory.context));for(Cut c:cuts)if(c.key.equals(cut)){known=true;members.addAll(c.members);}need(known,"Exact frozen discovery cut required");
            List<Original> rules=new ArrayList<>();List<Available> available=new ArrayList<>();List<ContextDependentRuntimeInspection.Product> pool=frame.cuts.get(cut);
            for(int i=0;i<pool.size();i++){scan.run();ContextDependentRuntimeInspection.Product p=pool.get(i);available.add(new Available(Donor.runtime(i),p.predicate,p.positive,p.arguments.size()));}
            // Observe every actual primary row. Unsupported rows refuse the entire discovery; no silent omission.
            for(Member member:members)for(ContextProductionInventory.Row row:member.inventory.primary){interrupt();scan.run();Ref ref=new Ref(member.inventory.context,row.nativeRule);Rule rule=(Rule)member.actual.getRules().get(row.nativeRule);need(rule!=null&&row.origin.equals(rule.getOrigin()),"Exact actual discovery original required");if(NativeDependentProductionRows.unit(rule)){NativeUnit unit=unit(child,cuts,cut,ref,scan);available.add(new Available(Donor.original(ref),unit.predicate(),unit.positive(),unit.arity()));}else{Clause clause=NativeDependentProductionRows.ordinary(rule,member.actual,scan);if(clause.literals.size()==1){Atom atom=clause.literals.get(0);available.add(new Available(Donor.original(ref),atom.predicate,atom.positive,atom.arguments.size()));}else rules.add(new Original(ref,clause));}}
            for(Original original:rules)for(int head=0;head<original.clause.literals.size();head++){
                interrupt();scan.run();List<List<Donor>> choices=new ArrayList<>();boolean possible=true;
                for(int literal=0;literal<original.clause.literals.size();literal++)if(literal!=head){scan.run();Atom pivot=original.clause.literals.get(literal);List<Donor> row=new ArrayList<>();for(Available a:available){scan.run();if(a.positive!=pivot.positive&&a.predicate.equals(pivot.predicate)&&a.rank==pivot.arguments.size())row.add(a.donor);}choices.add(row);if(row.isEmpty())possible=false;}
                if(!possible)continue;int[] selected=new int[choices.size()];boolean more=true;
                while(more){interrupt();scan.run();need(candidates<limits.candidates,"Whole frozen discovery candidate-tuple bound");candidates++;List<Donor> donors=new ArrayList<>();for(int i=0;i<selected.length;i++){scan.run();donors.add(choices.get(i).get(selected[i]));}Step step=new Step(cut,original.ref,head,donors);
                    try{Output output=engine.step(step,Collections.emptyList());need(outputs.size()<limits.outputs&&outputs.size()<bounds.preparation.projection.qualification.append.iteration.policy.steps,"Whole frozen discovery output bound");steps.add(step);outputs.add(output);}catch(NoMatch missing){noMatches++;}
                    more=false;for(int i=selected.length-1;i>=0;i--){scan.run();if(++selected[i]<choices.get(i).size()){more=true;break;}selected[i]=0;}
                }
            }
        }
        Request audit=new Request(bounds.preparation,steps,bounds.work,bounds.auditWork,bounds.nodes,true);new ContextDependentRuntimeExecutionAudit(child,cuts,frame,audit).check(outputs);interrupt();return new Result(steps,outputs,candidates,noMatches);
    }
}
