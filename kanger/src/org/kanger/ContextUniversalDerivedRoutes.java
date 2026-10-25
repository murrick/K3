/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextUniversalDerivedReplay.ProducerEvent;
import org.kanger.ContextUniversalUnitClosure.Proof;
import org.kanger.ContextWitnessMaterializationVerifier.Relation;
import org.kanger.ContextWitnessUnaryChainVerifier.Argument;
import org.kanger.ContextWitnessUnaryChainVerifier.Pattern;
import org.kanger.ContextWitnessUnaryChainVerifier.Root;

/** Local observed proof reconstruction from a sealed derived replay. No Packet or coverage authority. */
final class ContextUniversalDerivedRoutes {
    static final class Route {
        final Pattern physical;
        final Root origin;
        final Support support;
        final Proof producer;
        final ProducerEvent event;
        final List<Route> donors;
        final Route anchor,prior;
        final Relation crossing;
        private Route(Pattern physical,Root origin,Support support,Proof producer,ProducerEvent event,List<Route> donors,
                Route anchor,Route prior,Relation crossing) {
            this.physical=physical;this.origin=origin;this.support=support;this.producer=producer;this.event=event;
            this.donors=Collections.unmodifiableList(new ArrayList<>(donors));this.anchor=anchor;this.prior=prior;this.crossing=crossing;
        }
    }
    static final class Result {
        final boolean reconstructed;
        final String reason;
        final UUID basis,localBasis,replay;
        final List<Route> routes,conclusions,stored,theorems;
        final List<Relation> materializations;
        final List<String> outstandingGaps;
        private Result(ContextUniversalDerivedReplay.Result input,boolean reconstructed,String reason,List<Route> routes,List<Route> conclusions,List<Route> stored,List<Route> theorems,List<Relation> relations) {
            this.reconstructed=reconstructed;this.reason=reason;basis=input==null?null:input.basis;localBasis=input==null?null:input.localBasis;
            replay=input==null?null:input.replay;this.routes=Collections.unmodifiableList(new ArrayList<>(routes));
            this.conclusions=Collections.unmodifiableList(new ArrayList<>(conclusions));
            this.stored=Collections.unmodifiableList(new ArrayList<>(stored));
            this.theorems=Collections.unmodifiableList(new ArrayList<>(theorems));
            materializations=Collections.unmodifiableList(new ArrayList<>(relations));
            outstandingGaps=input==null?Collections.emptyList():input.outstandingGaps;
        }
    }
    private static Pattern pattern(ContextWitnessProjection.Literal literal,Runnable work) {
        List<Argument> arguments=new ArrayList<>();
        for(ContextWitnessProjection.Slot slot:literal.arguments) {
            work.run();Root root=null;
            for(ContextWitnessProjection.Node node:slot.lineage){work.run();if(node.domini)throw new IllegalStateException("Dominating derived conclusion root");}
            if(!slot.lineage.isEmpty()){ContextWitnessProjection.Node node=slot.lineage.get(slot.lineage.size()-1);root=new Root(node.term,node.ownerRule);}
            arguments.add(new Argument(root,slot.value));
        }
        return new Pattern(literal.predicate,literal.positive,arguments,true);
    }
    private static Pattern ground(Fact fact) {
        List<Argument> arguments=new ArrayList<>();for(SemanticTermSnapshot value:fact.arguments)arguments.add(new Argument(null,value));
        return new Pattern(fact.predicate,fact.positive,arguments,true);
    }
    private static boolean add(List<Route> pool,Route route,int maximumDepth,int[] capacity,Runnable work) {
        for(int i=0;i<pool.size();++i) {
            work.run();Route old=pool.get(i);
            if(Objects.equals(old.origin,route.origin)&&old.support.sources.equals(route.support.sources)&&old.physical.equivalent(route.physical,work)) {
                if(old.support.depth<=route.support.depth)return false;
                if(route.support.depth>maximumDepth)throw new IllegalStateException("Derived conclusion depth limit");
                pool.set(i,route);return true;
            }
        }
        if(route.support.depth>maximumDepth)throw new IllegalStateException("Derived conclusion depth limit");
        if(--capacity[0]<0)throw new IllegalStateException("Derived conclusion route limit");
        pool.add(route);return true;
    }
    private static Route conclusion(ProducerEvent event,Proof producer,Pattern conclusion,List<Route> donors,Route anchor,Runnable work) {
        Set<Source> sources=new LinkedHashSet<>();for(Source source:producer.support.sources){work.run();sources.add(source);}int depth=producer.support.depth;
        for(Route donor:donors){work.run();for(Source source:donor.support.sources){work.run();sources.add(source);}depth=Math.max(depth,donor.support.depth);}
        if(anchor!=null){for(Source source:anchor.support.sources){work.run();sources.add(source);}depth=Math.max(depth,anchor.support.depth);}
        // A ground unit theorem is already proved at its schema depth. An actual
        // universal instantiation or conditional application contributes one step.
        boolean theorem=event.ground!=null&&event.ground.premises.isEmpty()&&producer.clause.variables.isEmpty();
        if(!theorem){if(depth==Integer.MAX_VALUE)throw new IllegalStateException("Derived conclusion depth overflow");++depth;}
        return new Route(conclusion,conclusion.root,new Support(sources,depth),producer,event,donors,anchor,null,null);
    }
    private static boolean join(ProducerEvent event,Proof producer,Pattern conclusion,List<List<Route>> candidates,int slot,
            Root origin,List<Route> chosen,List<Route> seeds,List<Route> output,List<Route> pool,int maximumDepth,int[] capacity,Runnable work) {
        work.run();boolean changed=false;
        if(slot<candidates.size()) {
            for(Route donor:candidates.get(slot)) {
                work.run();if(origin!=null&&donor.origin!=null&&!origin.equals(donor.origin))continue;
                chosen.add(donor);changed|=join(event,producer,conclusion,candidates,slot+1,donor.origin==null?origin:donor.origin,
                    chosen,seeds,output,pool,maximumDepth,capacity,work);chosen.remove(chosen.size()-1);
            }
            return changed;
        }
        if(conclusion.root!=null&&origin!=null&&!conclusion.root.equals(origin))throw new IllegalStateException("Different derived conclusion origin");
        List<Route> anchors=new ArrayList<>();
        if(conclusion.root!=null&&origin==null) {
            for(Route seed:seeds){work.run();if(conclusion.root.equals(seed.physical.root))anchors.add(seed);}
            if(anchors.isEmpty())throw new IllegalStateException("Derived conclusion has no carried binding proof");
        }else anchors.add(null);
        for(Route anchor:anchors) {
            work.run();Route route=conclusion(event,producer,conclusion,chosen,anchor,work);
            changed|=add(output,route,maximumDepth,capacity,work);changed|=add(pool,route,maximumDepth,capacity,work);
        }
        return changed;
    }
    static Result reconstruct(ContextUniversalDerivedReplay.Result input,int maximumDepth,int maximumRoutes,int maximumWork) {
        if(maximumDepth<0||maximumDepth>64||maximumRoutes<1||maximumRoutes>65536||maximumWork<1)throw new IllegalArgumentException("Bounded derived conclusion reconstruction required");
        Runnable work=ContextGroundProductionRounds.budget(maximumWork);int[] capacity={maximumRoutes};
        try {
            if(input==null||!input.bound||input.observation==null)throw new IllegalStateException("Sealed successful derived replay required");
            Snapshot snapshot=input.observation;
            ContextWitnessMaterializationVerifier.Result correspondence=ContextWitnessMaterializationVerifier.verify(input.replay,snapshot,work);
            if(!snapshot.witnessProjections.isEmpty()||!snapshot.witnessMaterializations.isEmpty())
                if(!correspondence.verified)throw new IllegalStateException(correspondence.reason);
            List<Route> seeds=new ArrayList<>(),pool=new ArrayList<>();
            for(ContextWitnessUnaryChainVerifier.Route seed:input.seeds) {
                work.run();if(!input.replay.equals(seed.replay))throw new IllegalStateException("Different derived seed replay");
                Route route=new Route(seed.physical,seed.origin,seed.proof,null,null,Collections.emptyList(),null,null,null);
                add(seeds,route,maximumDepth,capacity,work);add(pool,route,maximumDepth,capacity,work);
            }
            List<Route> theorems=new ArrayList<>();
            for(List<Proof> group:input.bindings.values())for(Proof proof:group) {
                work.run();if(proof.clause.literals.size()!=1||!proof.clause.variables.isEmpty())continue;
                Fact fact=proof.clause.literals.get(0).fact();
                if(fact==null||!fact.positive)throw new IllegalStateException("Positive ground schema theorem required");
                Route route=new Route(ground(fact),null,proof.support,proof,null,Collections.emptyList(),null,null,null);
                if(--capacity[0]<0)throw new IllegalStateException("Derived ground theorem route limit");
                if(route.support.depth>maximumDepth)throw new IllegalStateException("Derived ground theorem depth limit");
                theorems.add(route);add(pool,route,maximumDepth,capacity,work);
            }
            Map<ProducerEvent,List<Route>> applications=new IdentityHashMap<>();
            Map<ContextWitnessProjection,ProducerEvent> witnesses=new IdentityHashMap<>();
            Map<ProducerEvent,List<Pattern>> required=new IdentityHashMap<>();Map<ProducerEvent,Pattern> conclusions=new IdentityHashMap<>();
            for(ProducerEvent event:input.events) {
                work.run();List<Pattern> premises=new ArrayList<>();Pattern conclusion;
                if(event.witness!=null) {
                    witnesses.put(event.witness,event);conclusion=pattern(event.witness.conclusion,work);
                    for(ContextWitnessProjection.Literal literal:event.witness.premises)premises.add(pattern(literal,work));
                }else{conclusion=ground(event.ground.conclusion);for(Fact fact:event.ground.premises)premises.add(ground(fact));}
                applications.put(event,new ArrayList<>());required.put(event,premises);conclusions.put(event,conclusion);
            }
            boolean changed;
            do {
                work.run();changed=false;List<Route> before=new ArrayList<>(pool);
                for(ProducerEvent event:input.events) {
                    work.run();List<List<Route>> candidates=new ArrayList<>();
                    for(Pattern premise:required.get(event)) {
                        List<Route> matching=new ArrayList<>();for(Route donor:before){work.run();if(donor.physical.equivalent(premise,work))matching.add(donor);}candidates.add(matching);
                    }
                    for(Proof producer:event.producers)changed|=join(event,producer,conclusions.get(event),candidates,0,null,new ArrayList<>(),seeds,
                        applications.get(event),pool,maximumDepth,capacity,work);
                }
                if(correspondence.verified)for(Relation relation:correspondence.relations) {
                    work.run();Pattern stored=pattern(relation.occurrence.stored,work);
                    for(ContextWitnessProjection observation:relation.applications) {
                        ProducerEvent event=witnesses.get(observation);if(event==null)throw new IllegalStateException("Unbound derived materialization producer");
                        for(Route donor:new ArrayList<>(applications.get(event))) {
                            work.run();Route route=new Route(stored,donor.origin,donor.support,null,null,Collections.emptyList(),null,donor,relation);
                            changed|=add(pool,route,maximumDepth,capacity,work);
                        }
                    }
                }
            }while(changed);
            for(List<Route> routes:applications.values()){work.run();if(routes.isEmpty())throw new IllegalStateException("Unreconstructed derived premise donors");}
            List<Route> storedRoutes=new ArrayList<>();
            if(correspondence.verified)for(Relation relation:correspondence.relations) {
                Pattern stored=pattern(relation.occurrence.stored,work);
                for(ContextWitnessProjection observation:relation.applications)for(Route donor:applications.get(witnesses.get(observation))) {
                    work.run();if(--capacity[0]<0)throw new IllegalStateException("Derived stored route limit");
                    storedRoutes.add(new Route(stored,donor.origin,donor.support,null,null,Collections.emptyList(),null,donor,relation));
                }
            }
            List<Route> allConclusions=new ArrayList<>();for(List<Route> routes:applications.values())allConclusions.addAll(routes);
            return new Result(input,true,"Observed derived conclusions and materializations reconstructed; schema gaps and family coverage remain open",pool,allConclusions,storedRoutes,theorems,
                correspondence.verified?correspondence.relations:Collections.emptyList());
        }catch(Exception failure){return new Result(input,false,failure.getMessage(),Collections.emptyList(),Collections.emptyList(),Collections.emptyList(),Collections.emptyList(),Collections.emptyList());}
    }
}
