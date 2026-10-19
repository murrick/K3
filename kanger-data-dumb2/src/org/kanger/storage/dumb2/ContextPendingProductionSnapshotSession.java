/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.util.*;
import org.kanger.*;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.interfaces.IMind;

/** Provider-owned read-only pending-child lease. Never reserves, settles or writes. */
final class ContextPendingProductionSnapshotSession implements AutoCloseable {
    static final class Result {
        final ContextUniversalFamilyInspection.Result inspection;
        final ContextUniversalFamilyAudit.Audit audit;
        private Result(ContextUniversalFamilyInspection.Result inspection,ContextUniversalFamilyAudit.Audit audit) {this.inspection=inspection;this.audit=audit;}
    }
    enum Engine { GROUND, UNIVERSAL, WITNESS }
    /** A detached diagnostic; no later native write may reuse it as authority. */
    static final class Decision {
        final Engine engine;
        final ContextGroundClauseFamily.Evaluation ground;
        final ContextProductionFamilyAssembly.Evaluation witness;
        final Result universal;
        private Decision(Engine engine,ContextGroundClauseFamily.Evaluation ground,ContextProductionFamilyAssembly.Evaluation witness,Result universal) {
            this.engine=engine;this.ground=ground;this.witness=witness;this.universal=universal;
        }
    }
    private final DB provider;
    private final Mind parent,child;
    private Mind retained;
    private ContextProductionInventory retainedInventory;
    private final IMind active;
    private final Thread owner=Thread.currentThread();
    private final int rows;
    private final RevisionRef source;
    private final List<ContextConnection> topology;
    private final ContextProductionInventory parentInventory,childInventory;
    private final List<ContextProductionInventory> foreign;
    private boolean closed;
    static ContextPendingProductionSnapshotSession open(DB db,Mind proposed,int rows) throws Exception {
        synchronized(db) {return new ContextPendingProductionSnapshotSession(db,proposed,rows);}
    }
    private ContextPendingProductionSnapshotSession(DB db,Mind proposed,int rows) throws Exception {
        if(rows<1 || rows>10000 || proposed==null || !(proposed.getNext() instanceof Mind)) throw new IllegalArgumentException("Bounded actual pending child required");
        provider=db;child=proposed;parent=(Mind)child.getNext();this.rows=rows;active=child.getUser().getCurrentMind();
        retained=parent.requirePendingValidationChild(child);
        requireOwner();source=new RevisionRef(db.getContextId(),db.getRevision());
        topology=Collections.unmodifiableList(new ArrayList<>(db.dmzTopology()));
        Set<UUID> ids=new HashSet<>();ids.add(source.getContextId());
        for(ContextConnection c:topology) if(c.getTrustGroup()!=null || !ids.add(c.getTarget().getContextId())) throw new IllegalStateException("Distinct ordinary initialized pending cuts required");
        parentInventory=inventory(parent,rows);childInventory=inventory(child,rows);
        retainedInventory=retained==null?null:inventory(retained,rows);
        int remaining=rows-childInventory.rows.size();List<ContextProductionInventory> views=new ArrayList<>();
        for(ContextConnection c:topology) {ContextProductionInventory i=ContextProductionInventory.inspect(c.layer(),c.getTarget().getContextId(),c.getTarget().getRevision(),false,c.getInitialization(),remaining);remaining-=i.rows.size();views.add(i);}
        foreign=Collections.unmodifiableList(views);requireCurrent();
    }
    private void requireOwner() throws Exception {
        if(closed || Thread.currentThread()!=owner || provider.isClosed() || provider.isReadOnly() || ((User)child.getUser()).getData()!=provider
                || child.getUser().getCurrentMind()!=active) throw new IllegalStateException("Active owning writable pending-child lease required");
        if(parent.requirePendingValidationChild(child)!=retained) throw new IllegalStateException("Pending validation ownership scope changed");
    }
    private ContextProductionInventory inventory(Mind mind,int limit) throws Exception {
        return ContextProductionInventory.inspect(mind,source.getContextId(),source.getRevision(),true,Collections.emptyList(),limit);
    }
    void requireCurrent() throws Exception {
        synchronized(provider) {
            requireOwner();
            if(retained!=null && !retainedInventory.fingerprint.equals(inventory(retained,rows).fingerprint)) throw new IllegalStateException("Retained rollback child inventory changed");
            List<ContextConnection> current=provider.dmzTopology();
            if(!source.getContextId().equals(provider.getContextId()) || source.getRevision()!=provider.getRevision() || !topology.equals(current)) throw new IllegalStateException("Pending physical source revision/topology changed");
            if(!parentInventory.fingerprint.equals(inventory(parent,rows).fingerprint) || !childInventory.fingerprint.equals(inventory(child,rows).fingerprint)) throw new IllegalStateException("Pending parent/child inventory changed");
            for(int n=0;n<topology.size();n++) {ContextConnection c=topology.get(n);
                if(c!=current.get(n)) throw new IllegalStateException("Pending initialized connection instance replaced");
                ContextProductionInventory old=foreign.get(n);
                if(!old.fingerprint.equals(ContextProductionInventory.inspect(c.layer(),old.context,old.revision,false,c.getInitialization(),old.rows.size()).fingerprint)) throw new IllegalStateException("Pending initialized foreign inventory changed");}
        }
    }
    void requireBaseline(ContextProductionSnapshotSession.Inventory baseline,List<ContextConnection> connections) throws Exception {
        synchronized(provider) {
            requireCurrent();
            if(!parentInventory.fingerprint.equals(baseline.x.fingerprint) || foreign.size()!=baseline.foreign.size() || topology.size()!=connections.size())
                throw new IllegalStateException("Native write baseline changed before native validation");
            for(int i=0;i<foreign.size();i++) if(topology.get(i)!=connections.get(i) || !foreign.get(i).fingerprint.equals(baseline.foreign.get(i).fingerprint))
                throw new IllegalStateException("Native write initialized cut changed before native validation");
        }
    }
    ContextProductionFamilyAssembly.Evaluation inspectWitness(int rounds,int depth,int work,int events) throws Exception {
        synchronized(provider) {
            requireCurrent();
            try {
                ContextProductionNativeBinding.Result x=ContextProductionNativeBinding.bind(child,childInventory,work,events);
                if(!x.bound) throw new IllegalStateException("Pending witness X binding incomplete: "+x.reason);
                List<Snapshot> bound=new ArrayList<>();
                for(int n=0;n<topology.size();n++) {
                    ContextProductionNativeBinding.Result f=ContextProductionNativeBinding.bind(topology.get(n).layer(),foreign.get(n),work,events);
                    if(!f.bound) throw new IllegalStateException("Pending witness foreign binding incomplete: "+f.reason);bound.add(f.snapshot);
                }
                ContextProductionFamilyAssembly.Result family=ContextProductionFamilyAssembly.assemble(x.snapshot,bound,work);
                if(!family.assembled) throw new IllegalStateException("Pending witness family incomplete: "+family.reason);
                return family.artifact.evaluate(rounds,depth,work,events);
            } finally {requireCurrent();}
        }
    }
    ContextGroundClauseFamily.Evaluation inspectGround(int rounds,int local,int depth,int work,int events,int probes,int models) throws Exception {
        return inspectGround(rounds,local,depth,work,events,probes,models,ContextOrdinaryResidualRounds.LedgerLimits.DEFAULT);
    }
    ContextGroundClauseFamily.Evaluation inspectGround(int rounds,int local,int depth,int work,int events,int probes,int models,ContextOrdinaryResidualRounds.LedgerLimits ledgerLimits) throws Exception {
        if(ledgerLimits==null) throw new IllegalArgumentException("Explicit ground ledger limits required");
        synchronized(provider) {
            requireCurrent();
            try {
                ContextProductionNativeBinding.Result x=ContextProductionNativeBinding.bind(child,childInventory,work,events);
                if(!x.bound) throw new IllegalStateException("Pending ground X binding incomplete: "+x.reason);
                List<Snapshot> bound=new ArrayList<>();
                for(int n=0;n<topology.size();n++) {
                    ContextProductionNativeBinding.Result f=ContextProductionNativeBinding.bind(topology.get(n).layer(),foreign.get(n),work,events);
                    if(!f.bound) throw new IllegalStateException("Pending ground foreign binding incomplete: "+f.reason);bound.add(f.snapshot);
                }
                return ContextGroundClauseFamily.evaluate(x.snapshot,bound,rounds,local,depth,work,events,probes,models,ledgerLimits);
            } finally {requireCurrent();}
        }
    }
    Result inspect(int rounds,int local,int depth,int instances,int models,int preparation,int events,int work,int auditWork) throws Exception {
        if(rounds<1 || local<1 || depth<0 || instances<1 || models<1 || preparation<1 || events<1 || work<1 || auditWork<1) throw new IllegalArgumentException("Bounded pending inspection required");
        synchronized(provider) {
            requireCurrent();
            try {
                ContextProductionNativeBinding.Result x=ContextProductionNativeBinding.bind(child,childInventory,preparation,events);
                if(!x.bound) throw new IllegalStateException("Pending X binding incomplete: "+x.reason);
                List<Snapshot> bound=new ArrayList<>();
                for(int n=0;n<topology.size();n++) {ContextProductionNativeBinding.Result f=ContextProductionNativeBinding.bind(topology.get(n).layer(),foreign.get(n),preparation,events);
                    if(!f.bound) throw new IllegalStateException("Pending foreign binding incomplete: "+f.reason);bound.add(f.snapshot);}
                ContextUniversalFamilyInspection.Result result=ContextUniversalFamilyInspection.inspect(x.snapshot,bound,rounds,local,depth,instances,models,work);
                ContextUniversalFamilyAudit.Audit checked=ContextUniversalFamilyAudit.audit(result,x.snapshot,bound,depth,instances,auditWork);
                return new Result(result,checked);
            } finally {requireCurrent();}
        }
    }
    Decision inspectAutomatic(int rounds,int local,int depth,int instances,int models,int preparation,int events,int work,int audit,int probes) throws Exception {
        if(rounds<1 || local<1 || depth<0 || instances<1 || models<1 || preparation<1 || events<1 || work<1 || audit<1 || probes<1)
            throw new IllegalArgumentException("Bounded automatic pending qualification required");
        synchronized(provider) {
            requireCurrent();
            try {
                ContextProductionNativeBinding.Result x=ContextProductionNativeBinding.bind(child,childInventory,preparation,events);
                if(!x.bound) throw new IllegalStateException("Pending X binding incomplete: "+x.reason);
                List<Snapshot> bound=new ArrayList<>();
                for(int n=0;n<topology.size();n++) {
                    ContextProductionNativeBinding.Result f=ContextProductionNativeBinding.bind(topology.get(n).layer(),foreign.get(n),preparation,events);
                    if(!f.bound) throw new IllegalStateException("Pending foreign binding incomplete: "+f.reason);bound.add(f.snapshot);
                }
                List<Snapshot> complete=new ArrayList<>(bound);complete.add(x.snapshot);
                boolean witnesses=false,variables=false;int remaining=work;
                for(Snapshot snapshot:complete) for(ContextProductionNativeBinding.Entry entry:snapshot.primary) {
                    if(--remaining<0) throw new IllegalStateException("Pending engine selection work limit");
                    witnesses|=entry.kind==ContextProductionNativeBinding.Kind.WITNESS_BUNDLE;
                    for(ContextProductionCapture.Clause clause:entry.clauses) {
                        if(--remaining<0) throw new IllegalStateException("Pending engine selection work limit");
                        variables|=!clause.variables.isEmpty();
                    }
                }
                if(witnesses) {
                    ContextProductionFamilyAssembly.Result family=ContextProductionFamilyAssembly.assemble(x.snapshot,bound,work);
                    if(!family.assembled) throw new IllegalStateException("Pending witness family incomplete: "+family.reason);
                    return new Decision(Engine.WITNESS,null,family.artifact.evaluate(rounds,depth,work,events),null);
                }
                if(!variables) return new Decision(Engine.GROUND,ContextGroundClauseFamily.evaluate(x.snapshot,bound,rounds,local,depth,work,events,probes,models),null,null);
                ContextUniversalFamilyInspection.Result inspected=ContextUniversalFamilyInspection.inspect(x.snapshot,bound,rounds,local,depth,instances,models,work);
                ContextUniversalFamilyAudit.Audit checked=ContextUniversalFamilyAudit.audit(inspected,x.snapshot,bound,depth,instances,audit);
                return new Decision(Engine.UNIVERSAL,null,null,new Result(inspected,checked));
            } finally {requireCurrent();}
        }
    }
    @Override public void close() {synchronized(provider) {closed=true;}}
}
