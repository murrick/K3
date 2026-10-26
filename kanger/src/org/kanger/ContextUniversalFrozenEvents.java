/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextUniversalFrozenClosure.Proof;
import org.kanger.ContextUniversalFrozenReplay.ProducerEvent;
import org.kanger.ContextWitnessResultTransport.Item;
import org.kanger.ContextUniversalInputResiduals.Residual;
import org.kanger.ContextUniversalInputResiduals.Term;
import org.kanger.ContextWitnessMaterializationVerifier.Relation;
import org.kanger.ContextWitnessResultTransport.Origin;

/** Logical proofs of observed concrete event facts; not causal native storage provenance or coverage. */
final class ContextUniversalFrozenEvents {
    static final class Binding {
        final Item input;
        final Support support;
        final Residual fact;
        private Binding(Item input,Support support,Runnable work){this.input=input;this.support=support;fact=ContextUniversalInputResiduals.fact(input,work);}
    }
    static final class Donor {
        final Residual fact;
        final Proof theorem;
        final List<Binding> anchors;
        final Map<Integer,Term> assignment;
        final Support support;
        private Donor(Residual fact,Proof theorem,List<Binding> anchors,Map<Integer,Term> assignment,Support support) {
            this.fact=fact;this.theorem=theorem;this.anchors=Collections.unmodifiableList(new ArrayList<>(anchors));
            this.assignment=assignment;this.support=support;
        }
    }
    static final class Conclusion {
        final ProducerEvent event;
        final Proof producer;
        final Residual fact;
        final List<Donor> donors;
        final List<Binding> anchors;
        final Map<Integer,Term> assignment;
        final Support support;
        private Conclusion(ProducerEvent event,Proof producer,Residual fact,List<Donor> donors,List<Binding> anchors,Map<Integer,Term> assignment,Support support) {
            this.event=event;this.producer=producer;this.fact=fact;this.donors=Collections.unmodifiableList(new ArrayList<>(donors));
            this.anchors=Collections.unmodifiableList(new ArrayList<>(anchors));this.assignment=assignment;this.support=support;
        }
    }
    static final class Stored {
        final Relation relation;
        final Conclusion prior;
        // The stored physical literal stays inside this occurrence; no global Origin alias is issued.
        private Stored(Relation relation,Conclusion prior){this.relation=relation;this.prior=prior;}
    }
    static final class Result {
        final boolean reconstructed;
        final String reason;
        final ContextUniversalFrozenUnits.Result input;
        final List<Conclusion> conclusions;
        final List<Stored> stored;
        private Result(boolean reconstructed,String reason,ContextUniversalFrozenUnits.Result input,List<Conclusion> conclusions,List<Stored> stored) {
            this.reconstructed=reconstructed;this.reason=reason;this.input=input;
            this.conclusions=Collections.unmodifiableList(new ArrayList<>(conclusions));this.stored=Collections.unmodifiableList(new ArrayList<>(stored));
        }
    }
    private static Support combine(Support producer,List<Support> parents,boolean step,Runnable work) {
        Set<Source> sources=new LinkedHashSet<>();for(Source source:producer.sources){work.run();sources.add(source);}int depth=producer.depth;
        for(Support parent:parents){work.run();depth=Math.max(depth,parent.depth);for(Source source:parent.sources){work.run();sources.add(source);}}
        if(step){if(depth==Integer.MAX_VALUE)throw new IllegalStateException("Typed native event depth overflow");++depth;}
        return new Support(sources,depth);
    }
    private interface Anchored { void accept(List<Binding> anchors); }
    private static void anchors(List<Origin> required,int slot,List<Binding> seeds,List<Binding> chosen,Anchored output,Runnable work) {
        work.run();if(slot==required.size()){output.accept(chosen);return;}
        boolean found=false;
        for(Binding seed:seeds) {
            work.run();if(!seed.fact.origins.contains(required.get(slot)))continue;found=true;
            chosen.add(seed);anchors(required,slot+1,seeds,chosen,output,work);chosen.remove(chosen.size()-1);
        }
        if(!found)throw new IllegalStateException("No actual carried input binding proof");
    }
    private static List<Origin> required(Map<Integer,Term> assignment,Set<Origin> covered,Runnable work) {
        Set<Origin> required=new LinkedHashSet<>();for(Term term:assignment.values()){work.run();if(term.origin!=null&&!covered.contains(term.origin))required.add(term.origin);}
        return new ArrayList<>(required);
    }
    private static List<Support> labels(List<Binding> anchors,Runnable work) {
        List<Support> labels=new ArrayList<>();for(Binding anchor:anchors){work.run();labels.add(anchor.support);}return labels;
    }
    private static void retainDonor(List<Donor> output,Donor next,int[] capacity,Runnable work) {
        for(int i=0;i<output.size();++i){work.run();Donor prior=output.get(i);if(prior.support.sources.equals(next.support.sources)){
            if(next.support.depth<prior.support.depth)output.set(i,next);return;}}
        if(--capacity[0]<0)throw new IllegalStateException("Typed native receipt limit");output.add(next);
    }
    private static List<Donor> donors(Residual fact,ContextUniversalFrozenUnits.Result input,List<Binding> seeds,int[] capacity,Runnable work) {
        if(!fact.variables.isEmpty()||fact.literals.size()!=1||!fact.literals.get(0).positive)throw new IllegalStateException("Concrete positive premise required");
        List<Donor> out=new ArrayList<>();
        for(Proof proof:input.input.theory.proofs) {
            work.run();if(proof.clause.literals.size()!=1)continue;
            for(Map<Integer,Term> assignment:ContextUniversalInputResiduals.assignments(proof.clause,fact,work)) {
                anchors(required(assignment,Collections.emptySet(),work),0,seeds,new ArrayList<>(),chosen->{
                    Support support=combine(proof.support,labels(chosen,work),!proof.clause.variables.isEmpty(),work);
                    retainDonor(out,new Donor(fact,proof,chosen,assignment,support),capacity,work);
                },work);
            }
        }
        if(out.isEmpty())throw new IllegalStateException("Observed premise has no checked positive unit proof");return out;
    }
    private static void retainConclusion(List<Conclusion> output,Conclusion next,int[] capacity,Runnable work) {
        for(int i=0;i<output.size();++i){work.run();Conclusion prior=output.get(i);if(prior.support.sources.equals(next.support.sources)){
            if(next.support.depth<prior.support.depth)output.set(i,next);return;}}
        if(--capacity[0]<0)throw new IllegalStateException("Typed native receipt limit");output.add(next);
    }
    private static void join(ProducerEvent event,Proof producer,Residual fact,List<List<Donor>> candidates,int slot,List<Donor> chosen,
            Map<Integer,Term> assignment,List<Binding> seeds,List<Conclusion> output,int[] capacity,Runnable work) {
        work.run();if(slot<candidates.size()) {
            for(Donor donor:candidates.get(slot)){work.run();chosen.add(donor);join(event,producer,fact,candidates,slot+1,chosen,assignment,seeds,output,capacity,work);chosen.remove(chosen.size()-1);}return;
        }
        Set<Origin> covered=new HashSet<>();for(Donor donor:chosen){work.run();covered.addAll(donor.fact.origins);}
        anchors(required(assignment,covered,work),0,seeds,new ArrayList<>(),bindings->{
            List<Support> parents=labels(bindings,work);for(Donor donor:chosen){work.run();parents.add(donor.support);}
            boolean step=!chosen.isEmpty()||!producer.clause.variables.isEmpty();
            retainConclusion(output,new Conclusion(event,producer,fact,chosen,bindings,assignment,combine(producer.support,parents,step,work)),capacity,work);
        },work);
    }
    static Result reconstruct(ContextUniversalFrozenUnits.Result input,int maximumDepth,int maximumReceipts,int maximumWork) {
        if(maximumDepth<0||maximumDepth>64||maximumReceipts<1||maximumReceipts>65536||maximumWork<1)throw new IllegalArgumentException("Bounded typed native proof reconstruction required");
        Runnable work=ContextGroundProductionRounds.budget(maximumWork);int[] capacity={maximumReceipts};
        try {
            if(input==null||!input.projected)throw new IllegalStateException("Sealed fixed unit projection required");
            ContextUniversalFrozenClosure.check(input.input.theory,maximumDepth,work);
            List<Binding> seeds=new ArrayList<>();
            for(Item item:input.input.theory.frame.local.items)for(Support support:item.proofs){work.run();seeds.add(new Binding(item,support,work));}
            List<Conclusion> conclusions=new ArrayList<>();Map<ContextWitnessProjection,List<Conclusion>> witnesses=new IdentityHashMap<>();
            for(ProducerEvent event:input.input.events) {
                work.run();Residual fact;List<Residual> premises=new ArrayList<>();
                if(event.witness!=null) {
                    fact=ContextUniversalInputResiduals.fact(event.witness.conclusion,input.input.carried,work);
                    for(ContextWitnessProjection.Literal premise:event.witness.premises){work.run();premises.add(ContextUniversalInputResiduals.fact(premise,input.input.carried,work));}
                }else {
                    fact=ContextUniversalInputResiduals.fact(event.ground.conclusion,work);
                    for(Fact premise:event.ground.premises){work.run();premises.add(ContextUniversalInputResiduals.fact(premise,work));}
                }
                List<List<Donor>> candidates=new ArrayList<>();for(Residual premise:premises){work.run();candidates.add(donors(premise,input,seeds,capacity,work));}
                List<Conclusion> routes=new ArrayList<>();
                for(Proof producer:event.producers)for(Map<Integer,Term> assignment:ContextUniversalInputResiduals.assignments(producer.clause,event.instance,work)) {
                    work.run();join(event,producer,fact,candidates,0,new ArrayList<>(),assignment,seeds,routes,capacity,work);
                }
                if(routes.isEmpty())throw new IllegalStateException("Observed event has no checked conclusion proof");
                for(Conclusion route:routes){work.run();if(route.support.depth>maximumDepth)throw new IllegalStateException("Typed native conclusion depth limit");}
                conclusions.addAll(routes);if(event.witness!=null)witnesses.put(event.witness,routes);
            }
            List<Stored> stored=new ArrayList<>();
            if(!input.input.observation.witnessMaterializations.isEmpty()) {
                ContextWitnessMaterializationVerifier.Result correspondence=ContextWitnessMaterializationVerifier.verify(input.input.replay,input.input.observation,work);
                if(!correspondence.verified)throw new IllegalStateException(correspondence.reason);
                for(Relation relation:correspondence.relations)for(ContextWitnessProjection event:relation.applications) {
                    work.run();List<Conclusion> routes=witnesses.get(event);if(routes==null)throw new IllegalStateException("Unproved materialization event");
                    for(Conclusion route:routes){work.run();if(--capacity[0]<0)throw new IllegalStateException("Typed native receipt limit");stored.add(new Stored(relation,route));}
                }
            }
            return new Result(true,"Observed typed event facts and stored occurrences proved; operational family coverage remains open",input,conclusions,stored);
        }catch(Exception failure){return new Result(false,failure.toString(),input,Collections.emptyList(),Collections.emptyList());}
    }
}
