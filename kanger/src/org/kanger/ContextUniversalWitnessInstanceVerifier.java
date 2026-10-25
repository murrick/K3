/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionNativeBinding.Entry;
import org.kanger.ContextWitnessResultTransport.Origin;
import org.kanger.ContextWitnessResultTransport.Replay;
import org.kanger.ContextWitnessUnaryChainVerifier.Root;
import org.kanger.units.Term;

/** Authenticates one observed universal instance; never certifies closure or mints a proof packet. */
final class ContextUniversalWitnessInstanceVerifier {
    static final class Instance {
        final UUID replay;
        final ContextWitnessProjection observation;
        final Map<Source,Entry> owners;
        final Map<Root,Origin> origins;
        private Instance(UUID replay,ContextWitnessProjection observation,Map<Source,Entry> owners,Map<Root,Origin> origins) {
            this.replay=replay;this.observation=observation;
            this.owners=Collections.unmodifiableMap(new LinkedHashMap<>(owners));
            this.origins=Collections.unmodifiableMap(new LinkedHashMap<>(origins));
        }
    }
    private static void whole(Entry owner,Input input,Runnable work) {
        if(owner.kind!=ContextProductionNativeBinding.Kind.CLAUSES||input.shapes.size()!=input.branches
                ||input.variableCount>2||input.shapes.isEmpty()||input.shapes.size()>8
                ||owner.clauses.size()!=input.shapes.size())
            throw new IllegalStateException("Incomplete universal instance owner shape");
        boolean[] used=new boolean[owner.clauses.size()];
        for(Clause actual:input.shapes) {
            boolean found=false;
            for(int i=0;i<used.length;++i){work.run();if(!used[i]&&actual.equivalent(owner.clauses.get(i))){used[i]=true;found=true;break;}}
            if(!found)throw new IllegalStateException("Universal instance changed whole owner");
        }
    }
    private static boolean universal(Clause shape,ContextWitnessProjection observation,Runnable work) {
        work.run();
        if(shape.literals.size()!=1||shape.variables.isEmpty()||!shape.literals.get(0).positive)return false;
        ContextProductionCapture.Atom atom=shape.literals.get(0);
        if(atom.arguments.size()>8)return false;
        return ContextWitnessPrimaryStepVerifier.producerInstance(shape,observation,work);
    }
    static Instance verify(Replay replay,ContextProductionCapture capture,ContextWitnessProjection observation,
            List<Entry> originals,int maximumWork) throws Exception {
        if(replay==null||capture==null||observation==null||originals==null||originals.isEmpty()||originals.size()>64||maximumWork<1)
            throw new IllegalArgumentException("Bounded native universal instance required");
        Runnable work=ContextGroundProductionRounds.budget(maximumWork);
        Snapshot snapshot=capture.snapshot(replay.mind);
        work.run();
        if(!snapshot.witnessProjections.contains(observation))throw new IllegalStateException("Universal instance outside capture");
        if(!observation.premises.isEmpty()||!observation.conclusion.positive)
            throw new IllegalStateException("Positive zero-premise universal instance required");
        Map<Source,Entry> declared=new LinkedHashMap<>();
        for(Entry owner:originals){work.run();if(owner==null||declared.put(owner.source,owner)!=null)throw new IllegalStateException("Repeated universal owner");}
        Map<Source,Entry> owners=new LinkedHashMap<>();
        for(Input input:snapshot.inputs) {
            work.run();if(input.nativeRule!=observation.nativeRule)continue;
            Entry owner=declared.get(input.source);
            if(owner==null)throw new IllegalStateException("Missing original universal owner");
            whole(owner,input,work);boolean matched=false;
            for(Clause shape:input.shapes)if(universal(shape,observation,work))matched=true;
            if(!matched)throw new IllegalStateException("Universal instance does not match original clause");
            owners.put(input.source,owner);
        }
        Set<Source> actual=new LinkedHashSet<>();
        for(Support producer:observation.producerSupports) {
            work.run();if(producer.depth!=0||producer.sources.size()!=1)throw new IllegalStateException("Universal producer is not primary");
            actual.addAll(producer.sources);
        }
        if(owners.isEmpty()||!actual.equals(owners.keySet()))throw new IllegalStateException("Incomplete universal producer alternatives");
        Map<Root,Origin> carried=new LinkedHashMap<>(),origins=new LinkedHashMap<>();
        for(Map.Entry<Origin,Long> entry:replay.witnesses.entrySet()) {
            work.run();Term term=(Term)replay.mind.getTerms().get(entry.getValue());
            if(term==null||carried.put(new Root(term.getId(),term.getRuleId()),entry.getKey())!=null)
                throw new IllegalStateException("Ambiguous carried universal origin");
        }
        for(ContextWitnessProjection.Slot slot:observation.conclusion.arguments) {
            work.run();if(slot.lineage.isEmpty())continue;
            for(ContextWitnessProjection.Node node:slot.lineage){work.run();if(node.domini)throw new IllegalStateException("Dominating universal instance origin");}
            ContextWitnessProjection.Node node=slot.lineage.get(slot.lineage.size()-1);Root root=new Root(node.term,node.ownerRule);
            Origin origin=carried.get(root);
            if(origin==null)throw new IllegalStateException("Unbound universal instance origin");
            origins.put(root,origin);
        }
        if(origins.size()!=1)throw new IllegalStateException("Single carried universal instance origin required");
        // Capture gaps and other clauses remain obligations of the owning family. This is only a local receipt.
        return new Instance(replay.namespace,observation,owners,origins);
    }
}
