/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextUniversalUnitClosure.Proof;
import org.kanger.ContextWitnessResultTransport.*;
import org.kanger.ContextWitnessUnaryChainVerifier.Root;
import org.kanger.enums.QueryPass;
import org.kanger.units.Term;

/** Native producer binding to checked schema proofs; does not reconstruct donor proofs or export a Packet. */
final class ContextUniversalDerivedReplay {
    static final class ProducerEvent {
        final ContextWitnessProjection witness;
        final Application ground;
        final List<Proof> producers;
        final Map<Root,Origin> origins;
        private ProducerEvent(ContextWitnessProjection witness,Application ground,List<Proof> producers,Map<Root,Origin> origins) {
            this.witness=witness;this.ground=ground;this.producers=Collections.unmodifiableList(new ArrayList<>(producers));
            this.origins=Collections.unmodifiableMap(new LinkedHashMap<>(origins));
        }
    }
    static final class Result {
        final boolean bound,privateInput;
        final String reason;
        final UUID basis,localBasis,replay;
        final Cut parent,target;
        final Map<Long,List<Proof>> bindings;
        final List<ProducerEvent> events;
        final List<String> outstandingGaps;
        final Snapshot observation;
        final List<ContextWitnessUnaryChainVerifier.Route> seeds;
        private Result(boolean bound,String reason,Packet common,Packet local,UUID replay,ContextUniversalUnitClosure.Result theory,
                boolean privateInput,Map<Long,List<Proof>> bindings,List<ProducerEvent> events,List<String> gaps,
                Snapshot observation,List<ContextWitnessUnaryChainVerifier.Route> seeds) {
            this.bound=bound;this.reason=reason;basis=common==null?null:common.identity;localBasis=local==null?null:local.identity;
            this.replay=replay;parent=theory==null?null:theory.parent;target=theory==null?null:theory.target;this.privateInput=privateInput;
            Map<Long,List<Proof>> copy=new LinkedHashMap<>();for(Map.Entry<Long,List<Proof>> e:bindings.entrySet())copy.put(e.getKey(),Collections.unmodifiableList(new ArrayList<>(e.getValue())));
            this.bindings=Collections.unmodifiableMap(copy);this.events=Collections.unmodifiableList(new ArrayList<>(events));
            outstandingGaps=Collections.unmodifiableList(new ArrayList<>(gaps));
            this.observation=observation;this.seeds=Collections.unmodifiableList(new ArrayList<>(seeds));
        }
    }
    private static List<ContextWitnessUnaryChainVerifier.Route> seeds(Replay replay,Map<Root,Origin> carried,Runnable work) {
        Map<Origin,Root> roots=new HashMap<>();for(Map.Entry<Root,Origin> e:carried.entrySet())roots.put(e.getValue(),e.getKey());
        List<ContextWitnessUnaryChainVerifier.Route> seeds=new ArrayList<>();
        for(Item item:replay.seeds) {
            List<ContextWitnessUnaryChainVerifier.Argument> arguments=new ArrayList<>();
            for(ContextWitnessResultTransport.Slot slot:item.atom.arguments) {
                work.run();Root root=slot.origin==null?null:roots.get(slot.origin);
                if(slot.origin!=null&&root==null)throw new IllegalStateException("Missing derived input root proof binding");
                arguments.add(new ContextWitnessUnaryChainVerifier.Argument(root,slot.value));
            }
            ContextWitnessUnaryChainVerifier.Pattern pattern=new ContextWitnessUnaryChainVerifier.Pattern(item.atom.predicate,item.atom.positive,arguments,true);
            for(Support proof:item.proofs){work.run();seeds.add(new ContextWitnessUnaryChainVerifier.Route(replay.namespace,pattern,pattern.root,proof,null,null,null,null));}
        }
        return seeds;
    }
    private static List<Proof> producers(Map<Long,List<Proof>> bindings,long rule,List<Support> observed,Runnable work) {
        List<Proof> proofs=bindings.get(rule);if(proofs==null)throw new IllegalStateException("Native event has no exact schema proof binding");
        Set<Support> expected=new HashSet<>(),actual=new HashSet<>();
        for(Proof proof:proofs){work.run();expected.add(proof.support);}
        for(Support support:observed){work.run();actual.add(support);}
        if(!expected.equals(actual))throw new IllegalStateException("Native producer source/depth alternatives differ from checked schema ledger");
        return proofs;
    }
    private static Map<Root,Origin> origins(Replay replay,Runnable work)throws Exception {
        Map<Root,Origin> roots=new LinkedHashMap<>();
        for(Map.Entry<Origin,Long> e:replay.witnesses.entrySet()) {
            work.run();Term term=(Term)replay.mind.getTerms().get(e.getValue());
            if(term==null||roots.put(new Root(term.getId(),term.getRuleId()),e.getKey())!=null)throw new IllegalStateException("Ambiguous derived replay root");
        }
        return roots;
    }
    private static Map<Root,Origin> roots(ContextWitnessProjection observation,Map<Root,Origin> carried,Runnable work) {
        Map<Root,Origin> selected=new LinkedHashMap<>();List<ContextWitnessProjection.Literal> literals=new ArrayList<>(observation.premises);literals.add(observation.conclusion);
        for(ContextWitnessProjection.Literal literal:literals) {
            work.run();if(!literal.positive)throw new IllegalStateException("Positive derived native event required");
            for(ContextWitnessProjection.Slot slot:literal.arguments) {
                work.run();if(slot.lineage.isEmpty())continue;
                for(ContextWitnessProjection.Node node:slot.lineage){work.run();if(node.domini)throw new IllegalStateException("Dominating derived replay root");}
                ContextWitnessProjection.Node node=slot.lineage.get(slot.lineage.size()-1);Root root=new Root(node.term,node.ownerRule);Origin origin=carried.get(root);
                if(origin==null)throw new IllegalStateException("Native derived event has an unbound witness root");selected.put(root,origin);
            }
        }
        if(selected.size()!=1)throw new IllegalStateException("Single carried origin required for derived witness binding");return selected;
    }
    static Result replay(ContextUniversalUnitClosure.Result theory,Packet input,int maximumDepth,int maximumWork,int maximumEvents) {
        return replay(theory,input,input,null,maximumDepth,maximumWork,maximumEvents);
    }
    static Result replayPrivate(ContextUniversalUnitClosure.Result theory,PrivatePool pool,int maximumDepth,int maximumWork,int maximumEvents) {
        if(pool==null)throw new IllegalArgumentException("Sealed private input required");
        return replay(theory,pool.common,pool.local,pool,maximumDepth,maximumWork,maximumEvents);
    }
    private static Result replay(ContextUniversalUnitClosure.Result theory,Packet common,Packet local,PrivatePool pool,int maximumDepth,int maximumWork,int maximumEvents) {
        if(maximumDepth<0||maximumDepth>64||maximumWork<1||maximumEvents<1)throw new IllegalArgumentException("Bounded derived replay required");
        Runnable work=ContextGroundProductionRounds.budget(maximumWork);UUID namespace=null;List<String> gaps=Collections.emptyList();
        try {
            if(common==null||local==null)throw new IllegalStateException("Actual sealed replay input required");
            ContextUniversalUnitClosure.check(theory,maximumDepth,work);
            ContextWitnessResultTransport.family(common,theory.parent.equals(theory.target)?Collections.singletonList(theory.parent):Arrays.asList(theory.parent,theory.target),maximumWork);
            if(pool!=null&&(!pool.cut.equals(theory.target)||!Cut.of(pool.x).equals(theory.parent)))throw new IllegalStateException("Private input and schema frame differ");
            for(Item item:local.items){work.run();if(!item.atom.positive)throw new IllegalStateException("Positive derived replay input required");}
            Replay restored=ContextWitnessResultTransport.restore(local,maximumWork);namespace=restored.namespace;restored.mind.setQueryPass(QueryPass.ACCEPT);
            Map<Root,Origin> carried=origins(restored,work);
            try(ContextProductionCapture capture=ContextProductionCapture.begin(restored.mind,maximumEvents)) {
                capture.observeWitnessProjections();Map<Long,List<Proof>> bindings=new LinkedHashMap<>();
                for(Proof proof:theory.proofs) {
                    work.run();Input input=capture.replayDerivedInput(restored.mind,proof.clause,Collections.singletonList(proof.support));
                    if(input.source!=null||input.clause==null||input.branches!=1||!input.clause.equivalent(proof.clause))throw new IllegalStateException("Derived schema input changed or became a synthetic primary");
                    bindings.computeIfAbsent(input.nativeRule,k->new ArrayList<>()).add(proof);
                }
                restored.mind.link(null,false);Snapshot observed=capture.snapshot();gaps=observed.gaps;
                for(String gap:gaps) {
                    work.run();if(!(gap.startsWith("nonordinary-conclusion:")||gap.startsWith("nonordinary-premise:")||gap.startsWith("nonordinary-materialization:")
                            ||gap.startsWith("compound-residual:")||gap.startsWith("unbound-waiter-composition:")))throw new IllegalStateException("Unsupported derived capture gap: "+gap);
                }
                List<ProducerEvent> events=new ArrayList<>();
                for(ContextWitnessProjection event:observed.witnessProjections) {
                    work.run();List<Proof> proofs=producers(bindings,event.nativeRule,event.producerSupports,work);
                    for(Proof proof:proofs){work.run();if(!ContextWitnessPrimaryStepVerifier.producerInstance(proof.clause,event,work))throw new IllegalStateException("Derived witness producer instance mismatch");}
                    events.add(new ProducerEvent(event,null,proofs,roots(event,carried,work)));
                }
                for(Application event:observed.applications) {
                    work.run();if(!event.conclusion.positive||event.premises.size()>7||event.steps!=(event.premises.isEmpty()?0:1))throw new IllegalStateException("Outside derived ground native event scope");
                    for(Fact premise:event.premises){work.run();if(!premise.positive)throw new IllegalStateException("Positive derived ground premises required");}
                    List<Proof> proofs=producers(bindings,event.nativeRule,event.producerSupports,work);
                    for(Proof proof:proofs){work.run();if(!ContextGroundApplicationVerifier.producerInstance(proof.clause,event,work))throw new IllegalStateException("Derived ground producer instance mismatch");}
                    events.add(new ProducerEvent(null,event,proofs,Collections.emptyMap()));
                }
                return new Result(true,"All replayed schemas and observed native producer instances bound to exact proof DAG alternatives; donor proofs and family coverage still required",common,local,namespace,theory,pool!=null,bindings,events,gaps,observed,seeds(restored,carried,work));
            }
        }catch(Exception failure){return new Result(false,failure.getMessage(),common,local,namespace,theory,pool!=null,Collections.emptyMap(),Collections.emptyList(),gaps,null,Collections.emptyList());}
    }
}
