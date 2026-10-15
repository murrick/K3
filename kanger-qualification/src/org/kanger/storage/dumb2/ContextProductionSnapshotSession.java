/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;

import java.util.*;
import org.kanger.ContextProductionInventory;
import org.kanger.ContextProductionNativeBinding;
import org.kanger.ContextProductionFamilyAssembly;
import org.kanger.DmzCandidateSession;
import org.kanger.Mind;
import org.kanger.User;

/** Read-only owning lease over live X and every direct exact-pinned initialized view. No TRUST flattening. */
final class ContextProductionSnapshotSession implements AutoCloseable {
    static final class Binding {
        final ContextProductionNativeBinding.Snapshot x;
        final List<ContextProductionNativeBinding.Snapshot> foreign;
        private Binding(ContextProductionNativeBinding.Snapshot x,List<ContextProductionNativeBinding.Snapshot> foreign) {
            this.x = x; this.foreign = Collections.unmodifiableList(new ArrayList<>(foreign));
        }
    }
    static final class Inventory {
        final ContextProductionInventory x;
        final List<ContextProductionInventory> foreign;
        final int inspectedRows;
        private Inventory(ContextProductionInventory x,List<ContextProductionInventory> foreign,int inspectedRows) {
            this.x = x; this.foreign = Collections.unmodifiableList(new ArrayList<>(foreign)); this.inspectedRows = inspectedRows;
        }
    }
    private final DB provider;
    private final Mind source;
    private final int maximumRows;
    private final Thread owner = Thread.currentThread();
    private final RevisionRef sourceRef;
    private final List<ContextConnection> topology;
    private final Inventory inventory;
    private boolean closed;
    static ContextProductionSnapshotSession open(DB provider,Mind source,int maximumRows) throws Exception {
        synchronized(provider) { return new ContextProductionSnapshotSession(provider,source,maximumRows); }
    }
    private ContextProductionSnapshotSession(DB provider,Mind source,int maximumRows) throws Exception {
        if (maximumRows < 1 || maximumRows > 10000) throw new IllegalArgumentException("Bounded production snapshot required");
        this.provider = provider; this.source = source; this.maximumRows = maximumRows; requireRoot();
        sourceRef = new RevisionRef(provider.getContextId(),provider.getRevision());
        topology = Collections.unmodifiableList(new ArrayList<>(provider.dmzTopology()));
        Set<UUID> identities = new HashSet<>(); identities.add(sourceRef.getContextId());
        for (ContextConnection connection : topology) {
            if (connection.getTrustGroup() != null) throw new IllegalStateException("TRUST effective view requires separate production snapshot support");
            if (!identities.add(connection.getTarget().getContextId())) throw new IllegalStateException("Repeated production source Context");
        }
        inventory = capture(); requireCurrent();
    }
    private void requireRoot() throws Exception {
        if (closed || Thread.currentThread() != owner || source == null || ((User)source.getUser()).getData() != provider
                || source.getUser().getCurrentMind() != source) throw new IllegalStateException("Owning active production source session required");
        DmzCandidateSession.requireSettledPhysicalQ(source);
    }
    private Inventory capture() throws Exception {
        int remaining = maximumRows;
        ContextProductionInventory x = ContextProductionInventory.inspect(source,sourceRef.getContextId(),sourceRef.getRevision(),true,Collections.emptyList(),remaining);
        remaining -= x.rows.size(); List<ContextProductionInventory> foreign = new ArrayList<>();
        for (ContextConnection connection : topology) {
            ContextProductionInventory view = ContextProductionInventory.inspect(connection.layer(),connection.getTarget().getContextId(),
                    connection.getTarget().getRevision(),false,connection.getInitialization(),remaining);
            remaining -= view.rows.size(); foreign.add(view);
        }
        return new Inventory(x,foreign,maximumRows-remaining);
    }
    void requireCurrent() throws Exception {
        synchronized(provider) {
            requireRoot();
            if (!sourceRef.getContextId().equals(provider.getContextId()) || sourceRef.getRevision() != provider.getRevision()
                    || !topology.equals(provider.dmzTopology())) throw new IllegalStateException("Production source revision/topology changed");
            Inventory now = capture();
            if (!inventory.x.fingerprint.equals(now.x.fingerprint) || inventory.foreign.size() != now.foreign.size())
                throw new IllegalStateException("Production source effective inventory changed");
            for (int i=0;i<now.foreign.size();++i)
                if (!inventory.foreign.get(i).fingerprint.equals(now.foreign.get(i).fingerprint))
                    throw new IllegalStateException("Pinned initialized production inventory changed");
        }
    }
    Inventory inventory() throws Exception { synchronized(provider) { requireCurrent(); return inventory; } }
    Binding bind(int maximumWork,int maximumEvents) throws Exception {
        synchronized(provider) {
            requireCurrent();
            try {
                ContextProductionNativeBinding.Result x = ContextProductionNativeBinding.bind(source,inventory.x,maximumWork,maximumEvents);
                if (!x.bound) throw new IllegalStateException("X native binding incomplete: "+x.reason);
                List<ContextProductionNativeBinding.Snapshot> foreign = new ArrayList<>();
                for (int i=0;i<topology.size();++i) {
                    ContextProductionNativeBinding.Result result = ContextProductionNativeBinding.bind(topology.get(i).layer(),inventory.foreign.get(i),maximumWork,maximumEvents);
                    if (!result.bound) throw new IllegalStateException("Foreign native binding incomplete: "+result.reason);
                    foreign.add(result.snapshot);
                }
                return new Binding(x.snapshot,foreign);
            } finally { requireCurrent(); }
        }
    }
    ContextProductionFamilyAssembly.Evaluation qualify(int maximumRounds,int maximumDepth,int maximumWork,int maximumEvents) throws Exception {
        synchronized(provider) {
            requireCurrent();
            try {
                Binding bound = bind(maximumWork,maximumEvents);
                ContextProductionFamilyAssembly.Result assembly = ContextProductionFamilyAssembly.assemble(bound.x,bound.foreign,maximumWork);
                if (!assembly.assembled) throw new IllegalStateException("Effective family assembly incomplete: "+assembly.reason);
                return assembly.artifact.evaluate(maximumRounds,maximumDepth,maximumWork,maximumEvents);
            } finally { requireCurrent(); }
        }
    }
    public void close() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("Production snapshot must close on owning thread");
        closed = true; // Borrowed Minds/layers belong to DB; never close or publish them here.
    }
}
