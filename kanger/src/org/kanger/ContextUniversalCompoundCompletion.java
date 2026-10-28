/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextProductionCapture.Source;
import org.kanger.ContextProductionCapture.Support;
import org.kanger.ContextUniversalCompoundExchange.Exchange;
import org.kanger.ContextUniversalCompoundExchange.ResidualProof;
import org.kanger.ContextUniversalFrozenConstraints.Proof;
import org.kanger.ContextUniversalFrozenConstraints.Result;
import org.kanger.ContextUniversalInputResiduals.Residual;

/** Detached original-row completion recipe; not a closure node or admission authority. */
final class ContextUniversalCompoundCompletion {
    static final class Receipt {
        final Exchange exchange;final ResidualProof residual;final Result receiver;
        final Proof producer;final List<Proof> spine,donors;
        final Map<Integer,Proof> consumed,additional;
        final List<Integer> remaining;
        final int conclusion;
        final Residual clause;final Support support;
        final boolean instantiated;
        final List<Residual> instances;
        private Receipt(Exchange exchange,ResidualProof residual,Result receiver,Plan plan,int conclusion,Map<Integer,Proof> additional,Application application) {
            this.exchange=exchange;this.residual=residual;this.receiver=receiver;producer=plan.root;
            spine=Collections.unmodifiableList(new ArrayList<>(plan.spine));consumed=Collections.unmodifiableMap(new LinkedHashMap<>(plan.consumed));remaining=Collections.unmodifiableList(new ArrayList<>(plan.remaining));
            this.conclusion=conclusion;this.additional=Collections.unmodifiableMap(new LinkedHashMap<>(additional));
            donors=Collections.unmodifiableList(new ArrayList<>(application.donors));clause=application.clause;support=application.support;instantiated=application.instantiated;instances=Collections.unmodifiableList(new ArrayList<>(application.instances));
        }
    }
    static final class Completion {
        final boolean completed;final String reason;final Receipt output;
        private Completion(boolean completed,String reason,Receipt output){this.completed=completed;this.reason=reason;this.output=output;}
    }
    private static final class Plan {
        final Proof root;final List<Proof> spine=new ArrayList<>();final Map<Integer,Proof> consumed=new LinkedHashMap<>();final List<Integer> remaining=new ArrayList<>();
        Plan(Proof root){this.root=root;for(int i=0;i<root.clause.literals.size();++i)remaining.add(i);}
    }
    private static void require(boolean b,String reason){if(!b)throw new IllegalStateException(reason);}
    /** Only an actual binary horn spine ending at an original whole-row part is in this grammar. */
    private static Plan plan(Proof p,Runnable work) {
        work.run();require(p!=null&&p.clause!=null,"Actual nonempty residual spine required");
        if(p.original!=null){require(p.clause.literals.size()>=3,"Original multi-premise producer/constraint required");return new Plan(p);}
        require(p.unit!=null&&p.horn!=null&&p.producer==null&&p.compound==null&&p.exchanged==null,"Original-row binary spine required; imported/application spines remain open");
        Plan out=plan(p.horn,work);require(p.clause.literals.size()==p.horn.clause.literals.size()-1&&out.remaining.size()==p.horn.clause.literals.size(),"Contracted residual slots need separate occurrence evidence");require(p.pivot>=0&&p.pivot<out.remaining.size(),"Actual remaining pivot required");int originalSlot=out.remaining.remove(p.pivot);require(out.consumed.put(originalSlot,p.unit)==null,"Original slot already consumed");out.spine.add(p);return out;
    }
    private static Plan frame(Exchange exchange,ResidualProof residual,Result receiver,Runnable work) {
        require(exchange!=null&&residual!=null&&receiver!=null&&receiver.closed&&receiver.compounds==exchange,"Actual checked compound receiver required");
        // Authenticate full historical input independently of the new completion fact bound.
        ContextUniversalFrozenConstraints.check(receiver,64,work);
        require(exchange.clauses.contains(residual)&&exchange.checks.containsValue(residual.owner)&&residual.owner.proofs.contains(residual.proof),"Actual owned residual receipt required");
        Plan plan=plan(residual.proof,work);require(!plan.spine.isEmpty(),"At least one actually consumed original donor required");
        require(plan.remaining.size()==residual.clause.literals.size(),"Different original remaining slots");
        return plan;
    }
    private static final class Application {
        final List<Proof> donors;final Residual clause;final Support support;final boolean instantiated;final List<Residual> instances;
        Application(List<Proof> donors,Residual clause,Support support,boolean instantiated,List<Residual> instances){this.donors=donors;this.clause=clause;this.support=support;this.instantiated=instantiated;this.instances=instances;}
    }
    private static Application apply(Plan plan,ResidualProof residual,Result receiver,int conclusion,Map<Integer,Proof> additional,int depth,Runnable work) {
        require(additional!=null&&plan.remaining.contains(conclusion),"Conclusion must be an actual surviving original slot");
        Set<Integer> expected=new LinkedHashSet<>(plan.remaining);expected.remove(conclusion);require(expected.equals(additional.keySet()),"Every remaining donor slot required exactly once");
        for(Map.Entry<Integer,Proof> e:additional.entrySet()){work.run();Proof p=e.getValue();require(p!=null&&receiver.proofs.contains(p)&&p.clause!=null&&p.clause.literals.size()==1,"Actual receiver unit proof required");}
        List<Proof> donors=new ArrayList<>();List<Residual> clauses=new ArrayList<>();Set<Source> sources=new LinkedHashSet<>(plan.root.support.sources);int grade=plan.root.support.depth;boolean symbolic=false;
        for(int slot=0;slot<plan.root.clause.literals.size();++slot)if(slot!=conclusion){work.run();Proof donor=plan.consumed.get(slot);if(donor==null)donor=additional.get(slot);require(donor!=null&&donor.clause!=null&&donor.clause.literals.size()==1,"Actual original-slot unit donor required");donors.add(donor);clauses.add(donor.clause);symbolic|=!donor.clause.variables.isEmpty();grade=Math.max(grade,donor.support.depth);for(Source source:donor.support.sources){work.run();sources.add(source);}}
        Set<Source> exact=new LinkedHashSet<>(residual.support.sources);for(Proof p:additional.values())for(Source source:p.support.sources){work.run();exact.add(source);}require(sources.equals(exact),"Original recipe must preserve every residual physical Source");
        require(grade<Integer.MAX_VALUE,"Completion grade overflow");Support support=new Support(sources,grade+1);require(support.depth<=depth,"Completion exceeds depth bound");
        ContextUniversalInputResiduals.GroundedApplication binding=symbolic?ContextUniversalInputResiduals.applyInstantiatedUnits(plan.root.clause,conclusion,clauses,work):null;
        Residual clause=symbolic?(binding==null?null:binding.conclusion):ContextUniversalInputResiduals.applySymbolicHeadFixedUnits(plan.root.clause,conclusion,clauses,work);
        require(clause!=null,"Completion requires actual fixed donor instances under one substitution");
        return new Application(donors,clause,support,symbolic,binding==null?Collections.emptyList():binding.instances);
    }
    static Completion complete(Exchange exchange,ResidualProof residual,Result receiver,int conclusion,Map<Integer,Proof> additional,int maximumDepth,int maximumWork) {
        if(maximumDepth<0||maximumDepth>64||maximumWork<1)throw new IllegalArgumentException("Bounded compound completion required");Runnable work=ContextGroundProductionRounds.budget(maximumWork);
        try{Plan plan=frame(exchange,residual,receiver,work);Application a=apply(plan,residual,receiver,conclusion,additional,maximumDepth,work);return new Completion(true,"Actual original-slot completion checked; closure integration and native minima remain open",new Receipt(exchange,residual,receiver,plan,conclusion,additional,a));}
        catch(Exception failure){return new Completion(false,failure.toString(),null);}
    }
    static void check(Receipt receipt,int maximumDepth,Runnable work) {
        require(maximumDepth>=0&&maximumDepth<=64&&receipt!=null,"Actual bounded completion receipt required");
        Plan plan=frame(receipt.exchange,receipt.residual,receipt.receiver,work);
        require(receipt.producer==plan.root&&receipt.spine.equals(plan.spine)&&receipt.consumed.equals(plan.consumed)&&receipt.remaining.equals(plan.remaining),"Different actual original producer/pivots/donors");
        Application expected=apply(plan,receipt.residual,receipt.receiver,receipt.conclusion,receipt.additional,maximumDepth,work);
        require(receipt.donors.equals(expected.donors)&&receipt.instantiated==expected.instantiated&&receipt.support.equals(expected.support)&&receipt.clause.equivalent(expected.clause,work)&&receipt.instances.size()==expected.instances.size(),"Different actual completion clause/Source/grade/recipe");
        for(int i=0;i<expected.instances.size();++i){work.run();require(receipt.instances.get(i).equivalent(expected.instances.get(i),work),"Different actual donor instance");}
    }
}
