/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextWitnessResultTransport.Origin;
import org.kanger.ContextWitnessResultTransport.Item;
import org.kanger.ContextWitnessResultTransport.Packet;
import org.kanger.ContextWitnessResultTransport.Replay;
import org.kanger.ContextWitnessResultTransport.Slot;
import org.kanger.ContextWitnessUnaryChainVerifier.*;
import org.kanger.enums.QueryPass;
import org.kanger.units.Term;

/** One fresh productive witness pass, seeded by a sealed proof ledger. */
final class ContextWitnessSeedReplay implements AutoCloseable {
    final UUID namespace;
    final UUID inputIdentity;
    final Packet input;
    final List<Item> seeds;
    final Map<Origin,Root> bindings;
    private final List<Item> localSeeds;
    private final boolean privateSeeds;
    private final Replay replay;
    private final ContextWitnessResultTransport.Cut target;
    private final ContextProductionCapture capture;
    private boolean linked, closed;
    private String nativeFailure;
    private ContextWitnessSeedReplay(Replay replay,Packet input,int maximumEvents,ContextWitnessResultTransport.Cut target,boolean privateSeeds) throws Exception {
        this.privateSeeds=privateSeeds;localSeeds=replay.seeds;
        this.target=target;this.replay = replay; this.input = input; this.inputIdentity = input.identity; namespace = replay.namespace; seeds = input.items;
        Map<Origin,Root> roots = new LinkedHashMap<>();
        for (Map.Entry<Origin,Long> entry : replay.witnesses.entrySet()) {
            Term term = (Term)replay.mind.getTerms().get(entry.getValue());
            roots.put(entry.getKey(),new Root(term.getId(),term.getRuleId()));
        }
        bindings = Collections.unmodifiableMap(roots);
        replay.mind.setQueryPass(QueryPass.ACCEPT);
        capture = ContextProductionCapture.begin(replay.mind,maximumEvents);
        capture.observeWitnessProjections();
    }
    static ContextWitnessSeedReplay open(Packet packet,int maximumWork,int maximumEvents) throws Exception {
        return new ContextWitnessSeedReplay(ContextWitnessResultTransport.restore(packet,maximumWork),packet,maximumEvents,null,false);
    }
    static ContextWitnessSeedReplay open(Packet packet,ContextWitnessResultTransport.Cut target,int maximumWork,int maximumEvents)throws Exception{
        if(target==null)throw new IllegalArgumentException("Exact replay target required");
        return new ContextWitnessSeedReplay(ContextWitnessResultTransport.restore(packet,maximumWork),packet,maximumEvents,target,false);
    }
    static ContextWitnessSeedReplay openPrivate(ContextWitnessResultTransport.PrivatePool pool,int maximumWork,int maximumEvents)throws Exception {
        if(pool==null)throw new IllegalArgumentException("Sealed private pool required");
        return new ContextWitnessSeedReplay(ContextWitnessResultTransport.restore(pool.local,maximumWork),pool.common,maximumEvents,pool.cut,true);
    }
    boolean hasPrivateSeeds(){return privateSeeds;}
    void requireTarget(ContextWitnessResultTransport.Cut expected){
        if(target!=null&&!target.equals(expected))throw new IllegalStateException("Witness replay has a different sealed target cut");
    }
    void rule(Source source,String statement) throws Exception {
        if (closed || linked) throw new IllegalStateException("Witness pass input is sealed");
        try {
            if(source!=null&&!source.localX&&target!=null&&!target.contains(ContextWitnessResultTransport.Cut.of(source)))
                throw new IllegalStateException("Foreign producer outside sealed witness replay members");
            capture.replay(replay.mind,source,statement);
        }
        catch (Exception failure) { linked = true; nativeFailure = "Witness input failed: "+failure.getMessage(); throw failure; }
    }
    private static boolean sameCut(Source a,Source b) {
        return a.context.equals(b.context) && a.revision == b.revision && a.view.equals(b.view);
    }
    private Source parentX(Runnable work) {
        Source parent = null;
        for (Item item : seeds) for (Support proof : item.proofs) {
            boolean usesX = false;
            for (Source source : proof.sources) {
                work.run(); if (!source.localX) continue;
                usesX = true;
                if (parent == null) parent = source;
                else if (!sameCut(parent,source)) throw new IllegalStateException("Incompatible carried X cut");
            }
            if (!usesX) throw new IllegalStateException("Foreign-only carried proof");
        }
        if (parent == null) throw new IllegalStateException("Missing carried X cut");
        return parent;
    }
    Result inspect(int maximumDepth,int maximumWork) {
        if (maximumDepth < 0 || maximumWork < 1) throw new IllegalArgumentException("Bounded seed replay verification required");
        int[] remaining = {maximumWork}; Runnable work = () -> { if (--remaining[0] < 0) throw new IllegalStateException("Witness seed replay work limit"); };
        try {
            Snapshot snapshot=settle(work);
            ContextWitnessPrimaryStepVerifier.Scope scope = ContextWitnessPrimaryStepVerifier.scope(
                    Collections.emptyList(),snapshot,work,true,8,target);
            List<Route> imported=imported(work);
            return ContextWitnessUnaryChainVerifier.close(namespace,snapshot,scope,imported,maximumDepth,7,work,true);
        } catch (IllegalStateException failure) { return new Result(false,failure.getMessage(),Collections.emptyList()); }
    }
    private Snapshot settle(Runnable work) {
        if (closed) throw new IllegalStateException("Witness seed replay closed");
        if (nativeFailure != null) throw new IllegalStateException(nativeFailure);
        if (!linked) {
            linked = true;
            try { replay.mind.link(null,false); }
            catch (Exception failure) {
                nativeFailure = "Native witness pass failed: "+failure.getMessage();
                throw new IllegalStateException(nativeFailure);
            }
        }
        Snapshot snapshot = capture.snapshot(); Source parent = parentX(work);
        for (Input input : snapshot.inputs) {
            work.run(); Source source = input.source;
            if (source == null || source.localX && !sameCut(parent,source)
                || !source.localX && parent.context.equals(source.context))
                throw new IllegalStateException("New producer does not match carried X cut");
        }
        return snapshot;
    }
    private List<Route> imported(Runnable work) {
        List<Route> imported = new ArrayList<>();
        for (Item item : localSeeds) {
            List<Argument> arguments = new ArrayList<>();
            for (Slot slot : item.atom.arguments) {
                work.run(); Root root = slot.origin == null ? null : bindings.get(slot.origin);
                if (slot.origin != null && root == null) throw new IllegalStateException("Missing carried witness binding");
                arguments.add(new Argument(root,slot.value));
            }
            Pattern pattern = new Pattern(item.atom.predicate,item.atom.positive,arguments,true);
            for (Support proof : item.proofs) {
                work.run(); imported.add(new Route(namespace,pattern,pattern.root,proof,null,null,null,null));
            }
        }
        return imported;
    }
    /** Observed proof routes only, deliberately incompatible with family export/coverage entrypoints. */
    static final class ObservedUniversals {
        final UUID basis,replay;
        final boolean reconstructed;
        final String reason;
        final List<Route> routes;
        final List<String> outstandingGaps;
        private ObservedUniversals(UUID basis,UUID replay,boolean reconstructed,String reason,List<Route> routes,List<String> gaps) {
            this.basis=basis;this.replay=replay;this.reconstructed=reconstructed;this.reason=reason;
            this.routes=Collections.unmodifiableList(new ArrayList<>(routes));
            outstandingGaps=Collections.unmodifiableList(new ArrayList<>(gaps));
        }
    }
    ObservedUniversals inspectObservedUniversals(List<ContextProductionNativeBinding.Entry> originals,int maximumDepth,int maximumWork) {
        if(maximumDepth<0||maximumWork<1||originals==null||originals.isEmpty()||originals.size()>64)
            throw new IllegalArgumentException("Bounded observed universal replay required");
        Runnable work=ContextGroundProductionRounds.budget(maximumWork);List<String> gaps=Collections.emptyList();
        try {
            Snapshot snapshot=settle(work);gaps=snapshot.gaps;
            Map<Source,ContextProductionNativeBinding.Entry> declared=new LinkedHashMap<>();
            for(ContextProductionNativeBinding.Entry original:originals){work.run();if(declared.put(original.source,original)!=null)throw new IllegalStateException("Repeated original replay owner");}
            for(Input input:snapshot.inputs) {
                work.run();ContextProductionNativeBinding.Entry owner=declared.get(input.source);
                if(owner==null)throw new IllegalStateException("Missing whole native replay owner");
                ContextUniversalWitnessInstanceVerifier.whole(owner,input,work);
            }
            ContextWitnessPrimaryStepVerifier.Scope scope=ContextWitnessPrimaryStepVerifier.observedUniversalScope(snapshot,work,target);
            Map<ContextWitnessProjection,ContextUniversalWitnessInstanceVerifier.Instance> instances=
                ContextUniversalWitnessInstanceVerifier.observed(replay,capture,originals,work);
            if(instances.isEmpty())throw new IllegalStateException("No observed universal witness instances");
            Result proof=ContextWitnessUnaryChainVerifier.close(namespace,snapshot,scope,imported(work),maximumDepth,7,work,true,instances);
            return new ObservedUniversals(inputIdentity,namespace,proof.reconstructed,proof.reason,proof.routes,gaps);
        }catch(Exception failure){return new ObservedUniversals(inputIdentity,namespace,false,failure.getMessage(),Collections.emptyList(),gaps);}
    }
    Set<Source> producerSources(Runnable work) {
        Set<Source> sources = new LinkedHashSet<>();
        for (Input declaration : producerInputs(work)) { work.run(); sources.add(declaration.source); }
        return Collections.unmodifiableSet(sources);
    }
    List<Input> producerInputs(Runnable work) {
        if (closed || !linked || nativeFailure != null) throw new IllegalStateException("Unsettled witness pass");
        List<Input> inputs = new ArrayList<>();
        for (Input declaration : capture.snapshot().inputs) { work.run(); inputs.add(declaration); }
        return Collections.unmodifiableList(inputs);
    }
    public void close() {
        if (!closed) { capture.close(); closed = true; }
    }
}
