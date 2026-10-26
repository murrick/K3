/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextUniversalFrozenClosure.Proof;
import org.kanger.ContextUniversalInputResiduals.Residual;
import org.kanger.ContextWitnessResultTransport.*;
import org.kanger.ContextWitnessUnaryChainVerifier.Root;
import org.kanger.enums.QueryPass;
import org.kanger.units.Term;

/** Native inputs and producer instances bound to a checked typed ledger; no conclusion or admission authority. */
final class ContextUniversalFrozenReplay {
    static final class ProducerEvent {
        final ContextWitnessProjection witness;
        final Application ground;
        final Residual instance;
        final List<Proof> producers;
        private ProducerEvent(ContextWitnessProjection witness,Application ground,Residual instance,List<Proof> producers) {
            this.witness=witness;this.ground=ground;this.instance=instance;this.producers=Collections.unmodifiableList(new ArrayList<>(producers));
        }
    }
    static final class Result {
        final boolean bound;
        final String reason;
        final UUID basis,localBasis,replay;
        final ContextUniversalFrozenClosure.Result theory;
        final Map<Root,Origin> carried;
        final Map<Long,List<Proof>> bindings;
        final List<Proof> imported;
        final List<ProducerEvent> events;
        final Snapshot observation;
        private Result(boolean bound,String reason,ContextUniversalFrozenClosure.Result theory,UUID replay,Map<Root,Origin> carried,
                Map<Long,List<Proof>> bindings,List<Proof> imported,List<ProducerEvent> events,Snapshot observation) {
            this.bound=bound;this.reason=reason;this.theory=theory;this.replay=replay;basis=theory==null?null:theory.basis;localBasis=theory==null?null:theory.localBasis;
            this.carried=Collections.unmodifiableMap(new LinkedHashMap<>(carried));Map<Long,List<Proof>> copy=new LinkedHashMap<>();
            for(Map.Entry<Long,List<Proof>> e:bindings.entrySet())copy.put(e.getKey(),Collections.unmodifiableList(new ArrayList<>(e.getValue())));
            this.bindings=Collections.unmodifiableMap(copy);this.imported=Collections.unmodifiableList(new ArrayList<>(imported));
            this.events=Collections.unmodifiableList(new ArrayList<>(events));this.observation=observation;
        }
    }
    private static List<Proof> producers(Map<Long,List<Proof>> bindings,long id,List<Support> labels,Runnable work) {
        List<Proof> proofs=bindings.get(id);if(proofs==null)throw new IllegalStateException("Unbound typed native producer");
        Set<Support> expected=new HashSet<>(),actual=new HashSet<>();for(Proof proof:proofs){work.run();expected.add(proof.support);}for(Support label:labels){work.run();actual.add(label);}
        if(!expected.equals(actual))throw new IllegalStateException("Different complete typed native Source/depth alternatives");return proofs;
    }
    static Result replay(ContextUniversalFrozenClosure.Result theory,int maximumDepth,int maximumWork,int maximumEvents) {
        if(maximumDepth<0||maximumDepth>64||maximumWork<1||maximumEvents<1)throw new IllegalArgumentException("Bounded frozen replay required");
        Runnable work=ContextGroundProductionRounds.budget(maximumWork);UUID namespace=null;
        try {
            ContextUniversalFrozenClosure.check(theory,maximumDepth,work);Replay restored=ContextWitnessResultTransport.restore(theory.frame.local,maximumWork);
            namespace=restored.namespace;restored.mind.setQueryPass(QueryPass.ACCEPT);Map<Root,Origin> carried=new LinkedHashMap<>();
            for(Map.Entry<Origin,Long> e:restored.witnesses.entrySet()) {
                work.run();Term term=(Term)restored.mind.getTerms().get(e.getValue());
                if(term==null||carried.put(new Root(term.getId(),term.getRuleId()),e.getKey())!=null)throw new IllegalStateException("Ambiguous frozen replay origin");
            }
            try(ContextProductionCapture capture=ContextProductionCapture.begin(restored.mind,maximumEvents)) {
                capture.observeWitnessProjections();Map<Long,List<Proof>> bindings=new LinkedHashMap<>();List<Proof> imported=new ArrayList<>();
                for(Proof proof:theory.proofs) {
                    work.run();if(proof.input!=null){imported.add(proof);continue;}
                    Input input=capture.replayTypedInput(restored.mind,proof.clause,Collections.singletonList(proof.support),carried,work);
                    if(input.source!=null||input.branches!=1||input.typedClause==null||!proof.clause.equivalent(input.typedClause,work))throw new IllegalStateException("Typed native input differs from checked proof");
                    bindings.computeIfAbsent(input.nativeRule,k->new ArrayList<>()).add(proof);
                }
                restored.mind.link(null,false);Snapshot snapshot=capture.snapshot();
                for(String gap:snapshot.gaps) {
                    work.run();if(gap.startsWith("nonordinary-primary-fact:")||gap.startsWith("cvariable-input:")) {
                        long id=Long.parseLong(gap.substring(gap.indexOf(':')+1));List<Proof> proofs=bindings.get(id);
                        if(proofs==null||proofs.stream().noneMatch(p->!p.clause.origins.isEmpty()))throw new IllegalStateException("Unauthenticated frozen native input gap");
                        if(gap.startsWith("nonordinary-primary-fact:")&&proofs.stream().noneMatch(p->p.clause.literals.size()==1))throw new IllegalStateException("Different frozen native unit gap");
                    }else if(!(gap.startsWith("nonordinary-conclusion:")||gap.startsWith("nonordinary-premise:")||gap.startsWith("nonordinary-materialization:")
                            ||gap.startsWith("compound-residual:")||gap.startsWith("unbound-waiter-composition:")))throw new IllegalStateException("Unsupported typed replay gap: "+gap);
                }
                List<ProducerEvent> events=new ArrayList<>();
                for(ContextWitnessProjection event:snapshot.witnessProjections) {
                    work.run();if(!event.conclusion.positive)throw new IllegalStateException("Positive frozen native event required");
                    for(ContextWitnessProjection.Literal premise:event.premises){work.run();if(!premise.positive)throw new IllegalStateException("Positive typed native premises required");}
                    List<Proof> proofs=producers(bindings,event.nativeRule,event.producerSupports,work);
                    Residual instance=ContextUniversalInputResiduals.event(event,carried,work);
                    for(Proof proof:proofs){work.run();if(!ContextUniversalInputResiduals.instance(proof.clause,instance,work))throw new IllegalStateException("Typed witness producer instance mismatch");}
                    events.add(new ProducerEvent(event,null,instance,proofs));
                }
                for(Application event:snapshot.applications) {
                    work.run();if(!event.conclusion.positive||event.steps!=(event.premises.isEmpty()?0:1))throw new IllegalStateException("Positive ordinary typed application required");
                    for(Fact premise:event.premises){work.run();if(!premise.positive)throw new IllegalStateException("Positive ordinary typed premises required");}
                    List<Proof> proofs=producers(bindings,event.nativeRule,event.producerSupports,work);Residual instance=ContextUniversalInputResiduals.event(event,work);
                    for(Proof proof:proofs){work.run();if(!ContextUniversalInputResiduals.instance(proof.clause,instance,work))throw new IllegalStateException("Ordinary typed producer instance mismatch");}
                    events.add(new ProducerEvent(null,event,instance,proofs));
                }
                return new Result(true,"Checked typed schemas/frozen bindings replayed; observed producers bound; donor/conclusion reconstruction and family coverage remain open",theory,namespace,carried,bindings,imported,events,snapshot);
            }
        }catch(Exception failure){return new Result(false,failure.toString(),theory,namespace,Collections.emptyMap(),Collections.emptyMap(),Collections.emptyList(),Collections.emptyList(),null);}
    }
}
